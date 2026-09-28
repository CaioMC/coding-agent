from __future__ import annotations

import os

from agent_result import AgentResult
from console import log
from environment import Environment
from git_repository import GitRepository
from github_api import GitHubApi
from pull_request import PullRequestDraft
from task import Task

MAX_COMMIT_SUMMARY_CHARS = 2000
DEFAULT_AUTHOR_NAME = "coding-agent[bot]"
DEFAULT_AUTHOR_EMAIL = "41898282+github-actions[bot]@users.noreply.github.com"


class PublishCommand:

    def __init__(self, env: Environment, api: GitHubApi, repository: GitRepository):
        self._env = env
        self._api = api
        self._repository = repository
        self._output_dir = env.require("OUTPUT_DIR")
        self._branch = env.require("WORK_BRANCH")
        self._base = env.get("BASE_BRANCH", "main")
        self._token = env.require("GH_TOKEN")
        self._run_url = env.get("RUN_URL")
        self._open_pr_on_failure = env.flag("OPEN_PR_ON_FAILURE", default=True)

    def run(self) -> None:
        task = Task.load(self._env.require("TASK_FILE"))
        result = AgentResult.load(self._output_dir)

        self._repository.stage_all_changes()
        changed_files = self._repository.staged_files()
        if not changed_files:
            self._report_no_changes(task, result)
            return

        self._save_patch()
        result.record_changed_files(changed_files)

        if not self._should_open_pull_request(result):
            self._report_skipped(task, result)
            return

        self._commit_and_push(task, result)
        self._open_pull_request(task, result)

    def _should_open_pull_request(self, result: AgentResult) -> bool:
        if result.is_error():
            return False
        return result.is_completed() or self._open_pr_on_failure

    def _save_patch(self) -> None:
        with open(os.path.join(self._output_dir, "changes.patch"), "w", encoding="utf-8") as file:
            file.write(self._repository.staged_diff())

    def _commit_and_push(self, task: Task, result: AgentResult) -> None:
        message = f"#{task.issue_number}: {task.title}\n\n{result.summary[:MAX_COMMIT_SUMMARY_CHARS]}"
        self._repository.commit(
            message,
            author_name=self._env.get("GIT_AUTHOR_NAME", DEFAULT_AUTHOR_NAME),
            author_email=self._env.get("GIT_AUTHOR_EMAIL", DEFAULT_AUTHOR_EMAIL),
        )
        self._repository.push(self._branch, self._token)

    def _open_pull_request(self, task: Task, result: AgentResult) -> None:
        draft = PullRequestDraft(task, result, self._run_url)
        pull_request = self._api.create_draft_pull_request(draft.title(), self._branch, self._base, draft.body())
        pr_url = pull_request.get("html_url")
        log(f"PR em rascunho criado: {pr_url}")

        result.record_publication(state="PR_CREATED", branch=self._branch,
                                  prNumber=pull_request.get("number"), prUrl=pr_url)
        self._api.comment_on_issue(task.issue_number,
                                   f"🤖 Abri o PR em rascunho {pr_url} (status do agente: `{result.status}`).\n\n"
                                   f"Verificação: {result.describe_verification()}")

    def _report_no_changes(self, task: Task, result: AgentResult) -> None:
        log("Nenhuma alteração para publicar.")
        result.record_publication(state="NO_CHANGES")
        self._api.comment_on_issue(task.issue_number,
                                   f"🤖 O agente terminou (`{result.status}`) sem alterar arquivos.\n\n"
                                   f"{result.summary}\n\nExecução: {self._run_url}")

    def _report_skipped(self, task: Task, result: AgentResult) -> None:
        log(f"Status {result.status}: alterações salvas em changes.patch, sem PR.")
        result.record_publication(state="SKIPPED", reason=f"status {result.status}")
        self._api.comment_on_issue(task.issue_number,
                                   f"🤖 O agente terminou com `{result.status}` e não abriu PR. "
                                   f"O diff está no artefato `agent-result`: {self._run_url}")
