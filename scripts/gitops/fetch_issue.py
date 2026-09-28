from __future__ import annotations

from console import log
from environment import Environment
from github_api import GitHubApi
from task import Task

STARTED_COMMENT = "🤖 O agente de codificação começou a trabalhar nesta issue.\n\nAcompanhe a execução: {run_url}"


class FetchIssueCommand:

    def __init__(self, env: Environment, api: GitHubApi):
        self._env = env
        self._api = api

    def run(self) -> None:
        number = int(self._env.require("ISSUE_NUMBER"))
        task_file = self._env.require("TASK_FILE")

        issue = self._api.get_issue(number)
        task = Task.from_issue(issue, number, self._env.get("REQUEST_ID"))
        task.save(task_file)
        log(f"Issue #{number} gravada em {task_file} ({len(task.acceptance_criteria)} critérios de aceite)")

        self._api.comment_on_issue(number, STARTED_COMMENT.format(run_url=self._env.get("RUN_URL")))
