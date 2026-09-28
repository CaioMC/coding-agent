"""Operações de GitHub feitas pelo workflow, fora do alcance do modelo.

Uso: python3 scripts/gitops [fetch-issue|publish]
"""
from __future__ import annotations

import sys

from environment import Environment, MissingVariableError
from fetch_issue import FetchIssueCommand
from git_repository import GitCommandError, GitRepository
from github_api import GitHubApi, GitHubApiError
from publish import PublishCommand


def build_fetch_issue(env: Environment) -> FetchIssueCommand:
    return FetchIssueCommand(env, GitHubApi.from_environment(env))


def build_publish(env: Environment) -> PublishCommand:
    return PublishCommand(env, GitHubApi.from_environment(env), GitRepository(env.require("WORKSPACE_DIR")))


COMMANDS = {
    "fetch-issue": build_fetch_issue,
    "publish": build_publish,
}


def main(argv: list[str]) -> int:
    if len(argv) != 2 or argv[1] not in COMMANDS:
        print("uso: gitops [fetch-issue|publish]", file=sys.stderr)
        return 1
    try:
        COMMANDS[argv[1]](Environment()).run()
        return 0
    except (MissingVariableError, GitHubApiError, GitCommandError) as error:
        print(error, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
