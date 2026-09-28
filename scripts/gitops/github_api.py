from __future__ import annotations

import json
import urllib.error
import urllib.request

from environment import Environment


class GitHubApiError(Exception):

    def __init__(self, method: str, path: str, status: int, detail: str):
        super().__init__(f"Erro HTTP {status} em {method} {path}: {detail[:800]}")
        self.status = status


class GitHubApi:

    TIMEOUT_SECONDS = 60

    def __init__(self, base_url: str, token: str, repository: str):
        self._base_url = base_url.rstrip("/")
        self._token = token
        self._repository = repository

    @classmethod
    def from_environment(cls, env: Environment) -> GitHubApi:
        return cls(
            base_url=env.get("GITHUB_API_URL", "https://api.github.com"),
            token=env.require("GH_TOKEN"),
            repository=env.require("GITHUB_REPOSITORY"),
        )

    def get_issue(self, number: int) -> dict:
        return self._request("GET", f"/repos/{self._repository}/issues/{number}")

    def comment_on_issue(self, number: int, text: str) -> None:
        self._request("POST", f"/repos/{self._repository}/issues/{number}/comments", {"body": text})

    def create_draft_pull_request(self, title: str, head: str, base: str, body: str) -> dict:
        return self._request("POST", f"/repos/{self._repository}/pulls", {
            "title": title,
            "head": head,
            "base": base,
            "body": body,
            "draft": True,
        })

    def _request(self, method: str, path: str, body: dict | None = None) -> dict:
        request = self._build_request(method, path, body)
        try:
            with urllib.request.urlopen(request, timeout=self.TIMEOUT_SECONDS) as response:
                return json.loads(response.read().decode() or "{}")
        except urllib.error.HTTPError as error:
            raise GitHubApiError(method, path, error.code, error.read().decode(errors="replace")) from error

    def _build_request(self, method: str, path: str, body: dict | None) -> urllib.request.Request:
        data = json.dumps(body).encode() if body is not None else None
        request = urllib.request.Request(f"{self._base_url}{path}", data=data, method=method)
        request.add_header("Accept", "application/vnd.github+json")
        request.add_header("X-GitHub-Api-Version", "2022-11-28")
        request.add_header("Authorization", f"Bearer {self._token}")
        if data is not None:
            request.add_header("Content-Type", "application/json")
        return request
