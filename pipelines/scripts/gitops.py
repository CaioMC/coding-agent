#!/usr/bin/env python3
"""
Operações de Git e de pull request feitas PELA PIPELINE, fora do alcance do modelo.

O agente (agent-runner) só edita arquivos e roda comandos no container. Quem tem o token
de escrita é este script, em passos separados da pipeline:

  decode-task  grava task.json a partir do parâmetro em base64
  clone        clona o repositório alvo sem deixar o token salvo no .git/config
  publish      faz commit, push da branch de trabalho e abre o PR em rascunho (draft)

Funciona com Azure Repos (GIT_PROVIDER=azure) e GitHub (GIT_PROVIDER=github).
Só usa a biblioteca padrão do Python, disponível nos agentes hospedados.
"""
import base64
import json
import os
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request


def env(name, default=None, required=False):
    value = os.environ.get(name, default)
    if required and not value:
        sys.exit(f"Variável de ambiente obrigatória ausente: {name}")
    return value


def log(msg):
    print(f"[gitops] {msg}", flush=True)


# ---------------------------------------------------------------- autenticação

def git_auth_header(provider, token):
    """Cabeçalho HTTP usado só no comando git atual (não fica gravado no repositório)."""
    if provider == "azure":
        return f"AUTHORIZATION: bearer {token}"
    basic = base64.b64encode(f"x-access-token:{token}".encode()).decode()
    return f"AUTHORIZATION: basic {basic}"


def git(args, cwd=None, auth_header=None, capture=False):
    cmd = ["git"]
    if auth_header:
        # "-c" antes do subcomando vale só para esta execução.
        cmd += ["-c", f"http.extraheader={auth_header}"]
    cmd += args
    shown = " ".join(a if "AUTHORIZATION" not in a else "http.extraheader=***" for a in cmd)
    log(f"$ {shown}")
    result = subprocess.run(cmd, cwd=cwd, text=True, capture_output=capture)
    if result.returncode != 0:
        if capture:
            sys.stderr.write(result.stdout + result.stderr)
        sys.exit(f"git falhou com código {result.returncode}")
    return result.stdout if capture else ""


# ---------------------------------------------------------------- decode-task

def cmd_decode_task():
    raw = base64.b64decode(env("TASK_B64", required=True)).decode("utf-8")
    task = json.loads(raw)
    if not task.get("title"):
        sys.exit("A tarefa precisa de 'title'")
    task.setdefault("requestId", env("REQUEST_ID", ""))
    out = env("TASK_FILE", required=True)
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(task, f, ensure_ascii=False, indent=2)
    log(f"Tarefa {task.get('issueKey')} gravada em {out}")


# ---------------------------------------------------------------- clone

def cmd_clone():
    provider = env("GIT_PROVIDER", "azure")
    url = env("TARGET_REPO_URL", required=True)
    base = env("BASE_BRANCH", "main")
    workspace = env("WORKSPACE_DIR", required=True)
    token = env("GIT_TOKEN")
    header = git_auth_header(provider, token) if token else None
    git(["clone", "--branch", base, "--depth", "50", url, workspace], auth_header=header)
    log(f"Repositório clonado em {workspace} (branch {base})")


# ---------------------------------------------------------------- publish

def http_json(method, url, token, provider, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    req.add_header("Accept", "application/json")
    if provider == "github":
        req.add_header("Authorization", f"Bearer {token}")
        req.add_header("X-GitHub-Api-Version", "2022-11-28")
    else:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            payload = resp.read().decode() or "{}"
            return json.loads(payload)
    except urllib.error.HTTPError as e:
        detail = e.read().decode(errors="replace")
        sys.exit(f"Erro HTTP {e.code} em {method} {url}: {detail[:800]}")


def load_result(output_dir):
    path = os.path.join(output_dir, "result.json")
    if not os.path.exists(path):
        return path, {"status": "ERROR", "summary": "O agent-runner não gerou result.json."}
    with open(path, encoding="utf-8") as f:
        return path, json.load(f)


def save_result(path, result):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)


def pr_body(task, result, run_url):
    status = result.get("status", "UNKNOWN")
    verification = result.get("verification") or {}
    if verification.get("executed"):
        ok = verification.get("exitCode") == 0
        ver = f"`{verification.get('command')}` terminou com código {verification.get('exitCode')}" + (
            " ✅" if ok else " ❌")
    else:
        ver = "não executada (" + str(verification.get("command") or "sem comando") + ")"
    warning = ""
    if status != "COMPLETED":
        warning = (f"\n> ⚠️ O agente terminou com status **{status}**. "
                   "Revise com atenção antes de marcar como pronto.\n")
    commands = result.get("commandsRun") or []
    cmd_list = "\n".join(f"- `{c}`" for c in commands[-15:]) or "- (nenhum)"
    return f"""## {task.get('issueKey', '')} {task.get('title', '')}
{warning}
### Resumo do agente
{result.get('summary') or '(sem resumo)'}

### Verificação independente
{ver}

### Execução
- Status: `{status}`
- Iterações: {result.get('iterations')} | chamadas de ferramenta: {result.get('toolCalls')}
- Modelo: {result.get('model') or 'n/d'}
- Log completo: {run_url or 'n/d'} (artefato `agent-result`)

<details><summary>Últimos comandos executados</summary>

{cmd_list}
</details>

_PR aberto automaticamente pelo Copilot Logístico. Requer aprovação humana._
"""


def create_pr(provider, token, task, result, branch, base, run_url):
    title_prefix = "" if result.get("status") == "COMPLETED" else "[WIP] "
    title = f"{title_prefix}{task.get('issueKey', '')}: {task.get('title', '')}".strip()
    body = pr_body(task, result, run_url)

    if provider == "azure":
        org_url = env("ADO_ORG_URL", required=True).rstrip("/")
        project = env("ADO_PROJECT", required=True)
        repo = env("TARGET_REPO", required=True)
        api = f"{org_url}/{urllib.parse.quote(project)}/_apis/git/repositories/{urllib.parse.quote(repo)}/pullrequests?api-version=7.1"
        pr = http_json("POST", api, token, provider, {
            "sourceRefName": f"refs/heads/{branch}",
            "targetRefName": f"refs/heads/{base}",
            "title": title[:400],
            "description": body[:3900],  # limite de 4000 caracteres da descrição no Azure DevOps
            "isDraft": True,
        })
        pr_id = pr.get("pullRequestId")
        url = f"{org_url}/{urllib.parse.quote(project)}/_git/{urllib.parse.quote(repo)}/pullrequest/{pr_id}"
        return pr_id, url

    api_base = env("GITHUB_API_URL", "https://api.github.com").rstrip("/")
    repo = env("TARGET_REPO", required=True)  # owner/repo
    pr = http_json("POST", f"{api_base}/repos/{repo}/pulls", token, provider, {
        "title": title[:250],
        "head": branch,
        "base": base,
        "body": body,
        "draft": True,
    })
    return pr.get("number"), pr.get("html_url")


def cmd_publish():
    provider = env("GIT_PROVIDER", "azure")
    workspace = env("WORKSPACE_DIR", required=True)
    output_dir = env("OUTPUT_DIR", required=True)
    branch = env("WORK_BRANCH", required=True)
    base = env("BASE_BRANCH", "main")
    token = env("GIT_TOKEN", required=True)
    run_url = env("RUN_URL", "")
    open_on_failure = env("OPEN_PR_ON_FAILURE", "true").lower() == "true"

    with open(env("TASK_FILE", required=True), encoding="utf-8") as f:
        task = json.load(f)
    result_path, result = load_result(output_dir)
    status = result.get("status")

    # Rede de segurança caso o repositório não ignore saídas de build no .gitignore.
    git(["add", "-A", "--", ".",
         ":(exclude,glob)**/target/**", ":(exclude,glob)**/build/**",
         ":(exclude,glob)**/node_modules/**", ":(exclude,glob)**/.gradle/**"], cwd=workspace)
    changed = git(["diff", "--cached", "--name-status"], cwd=workspace, capture=True).strip()
    if not changed:
        log("Nenhuma alteração para publicar.")
        result["publish"] = {"state": "NO_CHANGES"}
        save_result(result_path, result)
        return

    patch = git(["diff", "--cached"], cwd=workspace, capture=True)
    with open(os.path.join(output_dir, "changes.patch"), "w", encoding="utf-8") as f:
        f.write(patch)
    # "M\tarquivo" ou, em renomeações, "R100\tantigo\tnovo": o último campo é o caminho atual.
    changed_files = [line.split("\t")[-1] for line in changed.splitlines()]
    result["changedFiles"] = changed_files

    if status == "ERROR" or (status != "COMPLETED" and not open_on_failure):
        log(f"Status {status}: alterações salvas em changes.patch, sem PR.")
        result["publish"] = {"state": "SKIPPED", "reason": f"status {status}"}
        save_result(result_path, result)
        return

    message = f"{task.get('issueKey', '')}: {task.get('title', '')}\n\n{(result.get('summary') or '')[:2000]}"
    git(["-c", "user.name=" + env("GIT_AUTHOR_NAME", "Copilot Logistico"),
         "-c", "user.email=" + env("GIT_AUTHOR_EMAIL", "copilot-logistico@empresa.local"),
         "commit", "-q", "-m", message], cwd=workspace)
    git(["push", "origin", f"HEAD:refs/heads/{branch}"], cwd=workspace,
        auth_header=git_auth_header(provider, token))

    pr_id, pr_url = create_pr(provider, token, task, result, branch, base, run_url)
    log(f"PR em rascunho criado: {pr_url}")
    result["publish"] = {"state": "PR_CREATED", "branch": branch, "prId": pr_id, "prUrl": pr_url}
    save_result(result_path, result)


if __name__ == "__main__":
    commands = {"decode-task": cmd_decode_task, "clone": cmd_clone, "publish": cmd_publish}
    if len(sys.argv) != 2 or sys.argv[1] not in commands:
        sys.exit("uso: gitops.py [decode-task|clone|publish]")
    commands[sys.argv[1]]()
