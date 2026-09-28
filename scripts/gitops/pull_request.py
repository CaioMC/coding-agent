from __future__ import annotations

from agent_result import AgentResult
from task import Task

MAX_TITLE_CHARS = 250
MAX_LISTED_COMMANDS = 15


class PullRequestDraft:

    def __init__(self, task: Task, result: AgentResult, run_url: str):
        self._task = task
        self._result = result
        self._run_url = run_url or "n/d"

    def title(self) -> str:
        prefix = "" if self._result.is_completed() else "[WIP] "
        return f"{prefix}{self._task.title}"[:MAX_TITLE_CHARS]

    def body(self) -> str:
        return f"""Closes #{self._task.issue_number}
{self._warning()}
### Resumo do agente
{self._result.summary or '(sem resumo)'}

### Verificação independente
{self._result.describe_verification()}

### Execução
- Status: `{self._result.status}`
- Iterações: {self._result.iterations} | chamadas de ferramenta: {self._result.tool_calls}
- Modelo: {self._result.model}
- Log completo: {self._run_url} (artefato `agent-result`)

<details><summary>Últimos comandos executados</summary>

{self._command_list()}
</details>

_PR aberto automaticamente pelo agente de codificação. Requer aprovação humana._
"""

    def _warning(self) -> str:
        if self._result.is_completed():
            return ""
        return (f"\n> ⚠️ O agente terminou com status **{self._result.status}**. "
                "Revise com atenção antes de marcar como pronto.\n")

    def _command_list(self) -> str:
        commands = self._result.commands_run[-MAX_LISTED_COMMANDS:]
        return "\n".join(f"- `{command}`" for command in commands) or "- (nenhum)"
