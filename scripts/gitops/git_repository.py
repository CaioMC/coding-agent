from __future__ import annotations

import base64
import subprocess

from console import log

BUILD_OUTPUT_EXCLUDES = [
    ":(exclude,glob)**/target/**",
    ":(exclude,glob)**/build/**",
    ":(exclude,glob)**/node_modules/**",
    ":(exclude,glob)**/.gradle/**",
]


class GitCommandError(Exception):
    pass


class GitRepository:

    def __init__(self, path: str):
        self._path = path

    def stage_all_changes(self) -> None:
        self._run(["add", "-A", "--", ".", *BUILD_OUTPUT_EXCLUDES])

    def staged_files(self) -> list[str]:
        name_status = self._run(["diff", "--cached", "--name-status"], capture=True).strip()
        # "M\tarquivo" ou, em renomeações, "R100\tantigo\tnovo": o último campo é o caminho atual.
        return [line.split("\t")[-1] for line in name_status.splitlines()]

    def staged_diff(self) -> str:
        return self._run(["diff", "--cached"], capture=True)

    def commit(self, message: str, author_name: str, author_email: str) -> None:
        self._run(["-c", f"user.name={author_name}", "-c", f"user.email={author_email}",
                   "commit", "-q", "-m", message])

    def push(self, branch: str, token: str) -> None:
        self._run(["push", "origin", f"HEAD:refs/heads/{branch}"], auth_header=self._auth_header(token))

    @staticmethod
    def _auth_header(token: str) -> str:
        credentials = base64.b64encode(f"x-access-token:{token}".encode()).decode()
        return f"AUTHORIZATION: basic {credentials}"

    def _run(self, args: list[str], auth_header: str | None = None, capture: bool = False) -> str:
        command = ["git"]
        if auth_header:
            # Vale só para esta execução: o token nunca fica gravado no .git/config.
            command += ["-c", f"http.extraheader={auth_header}"]
        command += args
        log("$ " + " ".join("http.extraheader=***" if "AUTHORIZATION" in part else part for part in command))

        result = subprocess.run(command, cwd=self._path, text=True, capture_output=capture)
        if result.returncode != 0:
            output = (result.stdout or "") + (result.stderr or "") if capture else ""
            raise GitCommandError(f"{output}git falhou com código {result.returncode}")
        return result.stdout if capture else ""
