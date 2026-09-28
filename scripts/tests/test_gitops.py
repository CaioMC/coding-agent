import json
import os
import subprocess
import sys
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

GITOPS_DIR = Path(__file__).resolve().parent.parent / "gitops"
sys.path.insert(0, str(GITOPS_DIR))

from task import parse_acceptance_criteria  # noqa: E402


class FakeGitHub(BaseHTTPRequestHandler):
    requests: list = []
    pull_request_status = 201

    def do_GET(self):
        self._record(None)
        self._reply(200, {"title": "Adicionar log", "body": "Contexto\n- [ ] compila\n- [x] testa",
                          "html_url": "https://github.com/o/r/issues/7"})

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        self._record(body)
        if self.path.endswith("/pulls"):
            self._reply(self.pull_request_status, {"number": 12, "html_url": "https://github.com/o/r/pull/12",
                                                   "message": "GitHub Actions is not permitted"})
        else:
            self._reply(201, {})

    def _record(self, body):
        FakeGitHub.requests.append((self.command, self.path, body))

    def _reply(self, status, payload):
        data = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *args):
        pass


class GitOpsTestCase(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.server = HTTPServer(("127.0.0.1", 0), FakeGitHub)
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()

    def setUp(self):
        FakeGitHub.requests = []
        FakeGitHub.pull_request_status = 201
        self.tmp = Path(tempfile.mkdtemp())
        self.remote = self.tmp / "remote.git"
        self.workspace = self.tmp / "workspace"
        self.output = self.tmp / "output"
        self.task_file = self.tmp / "task.json"
        self._git("init", "-q", "--bare", str(self.remote), cwd=self.tmp)
        self._git("clone", "-q", str(self.remote), str(self.workspace), cwd=self.tmp)
        (self.workspace / "App.java").write_text("class App {}\n")
        self._git("-c", "user.name=t", "-c", "user.email=t@t", "add", ".", cwd=self.workspace)
        self._git("-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "-m", "init", cwd=self.workspace)
        self._git("push", "-q", "origin", "HEAD", cwd=self.workspace)

    def run_gitops(self, command):
        env = {
            **os.environ,
            "GITHUB_REPOSITORY": "o/r",
            "GITHUB_API_URL": f"http://127.0.0.1:{self.server.server_port}",
            "GH_TOKEN": "token-falso",
            "ISSUE_NUMBER": "7",
            "REQUEST_ID": "req-1",
            "TASK_FILE": str(self.task_file),
            "OUTPUT_DIR": str(self.output),
            "WORKSPACE_DIR": str(self.workspace),
            "WORK_BRANCH": "agent/issue-7",
            "RUN_URL": "https://run",
        }
        return subprocess.run([sys.executable, str(GITOPS_DIR), command], env=env, text=True, capture_output=True)

    def write_result(self, status):
        self.output.mkdir(parents=True, exist_ok=True)
        (self.output / "result.json").write_text(json.dumps({
            "status": status, "summary": "resumo", "iterations": 3, "toolCalls": 5,
            "commandsRun": ["mvn -o -q test"], "model": "qwen3:4b",
            "verification": {"command": "bash .agent/verify.sh", "executed": True, "exitCode": 0},
        }))

    def result(self):
        return json.loads((self.output / "result.json").read_text())

    def posts_to(self, suffix):
        return [body for method, path, body in FakeGitHub.requests if method == "POST" and path.endswith(suffix)]

    def remote_branches(self):
        return self._git("branch", "--list", cwd=self.remote).stdout

    def _git(self, *args, cwd):
        return subprocess.run(["git", *args], cwd=cwd, text=True, capture_output=True, check=True)


class FetchIssueTest(GitOpsTestCase):

    def test_grava_task_json_e_comenta_na_issue(self):
        completed = self.run_gitops("fetch-issue")

        self.assertEqual(completed.returncode, 0, completed.stderr)
        task = json.loads(self.task_file.read_text())
        self.assertEqual(task["title"], "Adicionar log")
        self.assertEqual(task["acceptanceCriteria"], ["compila", "testa"])
        self.assertEqual(len(self.posts_to("/issues/7/comments")), 1)


class PublishTest(GitOpsTestCase):

    def setUp(self):
        super().setUp()
        self.run_gitops("fetch-issue")
        FakeGitHub.requests = []

    def test_abre_pr_em_rascunho_quando_concluido(self):
        self.write_result("COMPLETED")
        (self.workspace / "App.java").write_text("class App { int x; }\n")

        completed = self.run_gitops("publish")

        self.assertEqual(completed.returncode, 0, completed.stderr)
        pull_request = self.posts_to("/pulls")[0]
        self.assertTrue(pull_request["draft"])
        self.assertEqual(pull_request["title"], "Adicionar log")
        self.assertIn("Closes #7", pull_request["body"])
        self.assertEqual(self.result()["publish"]["state"], "PR_CREATED")
        self.assertIn("agent/issue-7", self.remote_branches())
        self.assertNotIn("token-falso", completed.stdout)

    def test_prefixo_wip_quando_verificacao_falha(self):
        self.write_result("VERIFICATION_FAILED")
        (self.workspace / "App.java").write_text("class App { int x; }\n")

        self.run_gitops("publish")

        self.assertTrue(self.posts_to("/pulls")[0]["title"].startswith("[WIP] "))

    def test_sem_alteracoes_apenas_comenta(self):
        self.write_result("COMPLETED")

        completed = self.run_gitops("publish")

        self.assertEqual(completed.returncode, 0, completed.stderr)
        self.assertEqual(self.posts_to("/pulls"), [])
        self.assertEqual(self.result()["publish"]["state"], "NO_CHANGES")

    def test_status_error_guarda_patch_e_nao_abre_pr(self):
        self.write_result("ERROR")
        (self.workspace / "App.java").write_text("class App { int x; }\n")

        self.run_gitops("publish")

        self.assertEqual(self.posts_to("/pulls"), [])
        self.assertEqual(self.result()["publish"]["state"], "SKIPPED")
        self.assertIn("int x", (self.output / "changes.patch").read_text())
        self.assertNotIn("agent/issue-7", self.remote_branches())

    def test_ignora_saidas_de_build(self):
        self.write_result("COMPLETED")
        (self.workspace / "target").mkdir()
        (self.workspace / "target" / "App.class").write_text("binario")
        (self.workspace / "Novo.java").write_text("class Novo {}\n")

        self.run_gitops("publish")

        self.assertEqual(self.result()["changedFiles"], ["Novo.java"])

    def test_falha_ao_criar_pr_encerra_com_erro(self):
        FakeGitHub.pull_request_status = 403
        self.write_result("COMPLETED")
        (self.workspace / "App.java").write_text("class App { int x; }\n")

        completed = self.run_gitops("publish")

        self.assertEqual(completed.returncode, 1)
        self.assertIn("Erro HTTP 403", completed.stderr)


class AcceptanceCriteriaTest(unittest.TestCase):

    def test_extrai_checkboxes(self):
        body = "texto\n- [ ] um\n* [x] dois\n-[ ] tres\nnão é critério"
        self.assertEqual(parse_acceptance_criteria(body), ["um", "dois", "tres"])


if __name__ == "__main__":
    unittest.main()
