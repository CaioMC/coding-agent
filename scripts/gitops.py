#!/usr/bin/env python3
"""
Operações de GitHub feitas PELO WORKFLOW, fora do alcance do modelo.

O agente (agent-runner) só edita arquivos e roda comandos no container. Quem tem o
GITHUB_TOKEN com permissão de escrita é este script, em passos separados do workflow:

  fetch-issue  lê a issue (a "tarefa"), grava task.json e comenta na issue que começou
  publish      faz commit, push da branch de trabalho, abre o PR em rascunho
               ligado à issue ("Closes #N") e comenta o resultado na issue

Só usa a biblioteca padrão do Python, disponível nos runners do GitHub.

Variáveis comuns (o GitHub Actions já define as duas primeiras):
  GITHUB_REPOSITORY  owner/repo onde o workflow está rodando
  GITHUB_API_URL     https://api.github.com
  GH_TOKEN           token com contents, pull-requests e issues (normalmente o GITHUB_TOKEN)
"""
import base64
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request


def env(name, default=None, required=False):
    value = os.environ.get(name, default)
    if required and not value:
        sys.exit(f"Variável de ambiente obrigatória ausente: {name}")
    return value


def log(msg):
    print(f"[gitops] {msg}", flush=True)


# ---------------------------------------------------------------- API do GitHub

def api(method, path, body=None):
    base = env("GITHUB_API_URL", "https://api.github.com").rstrip("/")
    url = f"{base}{path}"
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("X-GitHub-Api-Version", "2022-11-28")
    req.add_header("Authorization", f"Bearer {env('GH_TOKEN', required=True)}")
    if data is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            payload = resp.read().decode() or "{}"
            return json.loads(payload)
    except urllib.error.HTTPError as e:
        detail = e.read().decode(errors="replace")
        sys.exit(f"Erro HTTP {e.code} em {method} {path}: {detail[:800]}")


def comment_on_issue(repo, issue_number, text):
    api("POST", f"/repos/{repo}/issues/{issue_number}/comments", {"body": text})


# ---------------------------------------------------------------- git

def git_auth_header(token):
    """Cabeçalho usado só no comando git atual; nunca fica gravado em .git/config."""
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


# ---------------------------------------------------------------- fetch-issue

CHECKBOX = re.compile(r"^\s*[-*]\s*\[[ xX]\]\s*(.+)$")


def parse_acceptance_criteria(body):
    """Linhas no formato '- [ ] algo' do corpo da issue viram critérios de aceite."""
    return [m.group(1).strip() for line in (body or "").splitlines() if (m := CHECKBOX.match(line))]


def cmd_fetch_issue():
    repo = env("GITHUB_REPOSITORY", required=True)
    number = int(env("ISSUE_NUMBER", required=True))
    issue = api("GET", f"/repos/{repo}/issues/{number}")
    body = issue.get("body") or ""
    task = {
        "requestId": env("REQUEST_ID", ""),
        "issueNumber": number,
        "issueUrl": issue.get("html_url"),
        "title": issue.get("title"),
        "description": body,
        "acceptanceCriteria": parse_acceptance_criteria(body),
    }
    out = env("TASK_FILE", required=True)
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(task, f, ensure_ascii=False, indent=2)
    log(f"Issue #{number} gravada em {out} ({len(task['acceptanceCriteria'])} critérios de aceite)")

    run_url = env("RUN_URL", "")
    comment_on_issue(repo, number,
                     f"🤖 O agente de codificação começou a trabalhar nesta issue.\n\nAcompanhe a execução: {run_url}")


# ---------------------------------------------------------------- publish

def load_result(output_dir):
    path = os.path.join(output_dir, "result.json")
    if not os.path.exists(path):
        return path, {"status": "ERROR", "summary": "O agent-runner não gerou result.json."}
    with open(path, encoding="utf-8") as f:
        return path, json.load(f)


def save_result(path, result):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)


def describe_verification(result):
    verification = result.get("verification") or {}
    if verification.get("executed"):
        ok = verification.get("exitCode") == 0
        return (f"`{verification.get('command')}` terminou com código {verification.get('exitCode')}"
                + (" ✅" if ok else " ❌"))
    return "não executada (" + str(verification.get("command") or "sem comando") + ")"


def pr_body(task, result, run_url):
    status = result.get("status", "UNKNOWN")
    warning = ""
    if status != "COMPLETED":
        warning = (f"\n> ⚠️ O agente terminou com status **{status}**. "
                   "Revise com atenção antes de marcar como pronto.\n")
    commands = result.get("commandsRun") or []
    cmd_list = "\n".join(f"- `{c}`" for c in commands[-15:]) or "- (nenhum)"
    return f"""Closes #{task.get('issueNumber')}
{warning}
### Resumo do agente
{result.get('summary') or '(sem resumo)'}

### Verificação independente
{describe_verification(result)}

### Execução
- Status: `{status}`
- Iterações: {result.get('iterations')} | chamadas de ferramenta: {result.get('toolCalls')}
- Modelo: {result.get('model') or 'n/d'}
- Log completo: {run_url or 'n/d'} (artefato `agent-result`)

<details><summary>Últimos comandos executados</summary>

{cmd_list}
</details>

_PR aberto automaticamente pelo agente de codificação. Requer aprovação humana._
"""


def cmd_publish():
    repo = env("GITHUB_REPOSITORY", required=True)
    workspace = env("WORKSPACE_DIR", required=True)
    output_dir = env("OUTPUT_DIR", required=True)
    branch = env("WORK_BRANCH", required=True)
    base = env("BASE_BRANCH", "main")
    token = env("GH_TOKEN", required=True)
    run_url = env("RUN_URL", "")
    open_on_failure = env("OPEN_PR_ON_FAILURE", "true").lower() == "true"

    with open(env("TASK_FILE", required=True), encoding="utf-8") as f:
        task = json.load(f)
    issue_number = task.get("issueNumber")
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
        comment_on_issue(repo, issue_number,
                         f"🤖 O agente terminou (`{status}`) sem alterar arquivos.\n\n"
                         f"{result.get('summary') or ''}\n\nExecução: {run_url}")
        return

    patch = git(["diff", "--cached"], cwd=workspace, capture=True)
    with open(os.path.join(output_dir, "changes.patch"), "w", encoding="utf-8") as f:
        f.write(patch)
    # "M\tarquivo" ou, em renomeações, "R100\tantigo\tnovo": o último campo é o caminho atual.
    result["changedFiles"] = [line.split("\t")[-1] for line in changed.splitlines()]

    if status == "ERROR" or (status != "COMPLETED" and not open_on_failure):
        log(f"Status {status}: alterações salvas em changes.patch, sem PR.")
        result["publish"] = {"state": "SKIPPED", "reason": f"status {status}"}
        save_result(result_path, result)
        comment_on_issue(repo, issue_number,
                         f"🤖 O agente terminou com `{status}` e não abriu PR. "
                         f"O diff está no artefato `agent-result`: {run_url}")
        return

    message = f"#{issue_number}: {task.get('title', '')}\n\n{(result.get('summary') or '')[:2000]}"
    git(["-c", "user.name=" + env("GIT_AUTHOR_NAME", "coding-agent[bot]"),
         "-c", "user.email=" + env("GIT_AUTHOR_EMAIL", "41898282+github-actions[bot]@users.noreply.github.com"),
         "commit", "-q", "-m", message], cwd=workspace)
    git(["push", "origin", f"HEAD:refs/heads/{branch}"], cwd=workspace, auth_header=git_auth_header(token))

    prefix = "" if status == "COMPLETED" else "[WIP] "
    pr = api("POST", f"/repos/{repo}/pulls", {
        "title": f"{prefix}{task.get('title', '')}"[:250],
        "head": branch,
        "base": base,
        "body": pr_body(task, result, run_url),
        "draft": True,
    })
    pr_url = pr.get("html_url")
    log(f"PR em rascunho criado: {pr_url}")
    result["publish"] = {"state": "PR_CREATED", "branch": branch, "prNumber": pr.get("number"), "prUrl": pr_url}
    save_result(result_path, result)
    comment_on_issue(repo, issue_number,
                     f"🤖 Abri o PR em rascunho {pr_url} (status do agente: `{status}`).\n\n"
                     f"Verificação: {describe_verification(result)}")


if __name__ == "__main__":
    commands = {"fetch-issue": cmd_fetch_issue, "publish": cmd_publish}
    if len(sys.argv) != 2 or sys.argv[1] not in commands:
        sys.exit("uso: gitops.py [fetch-issue|publish]")
    commands[sys.argv[1]]()
