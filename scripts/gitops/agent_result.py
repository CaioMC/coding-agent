from __future__ import annotations

import json
import os

COMPLETED = "COMPLETED"
ERROR = "ERROR"


class AgentResult:

    def __init__(self, path: str, data: dict):
        self._path = path
        self._data = data

    @classmethod
    def load(cls, output_dir: str) -> AgentResult:
        path = os.path.join(output_dir, "result.json")
        if not os.path.exists(path):
            return cls(path, {"status": ERROR, "summary": "O agent-runner não gerou result.json."})
        with open(path, encoding="utf-8") as file:
            return cls(path, json.load(file))

    @property
    def status(self) -> str:
        return self._data.get("status", "UNKNOWN")

    @property
    def summary(self) -> str:
        return self._data.get("summary") or ""

    @property
    def iterations(self):
        return self._data.get("iterations")

    @property
    def tool_calls(self):
        return self._data.get("toolCalls")

    @property
    def model(self) -> str:
        return self._data.get("model") or "n/d"

    @property
    def commands_run(self) -> list[str]:
        return self._data.get("commandsRun") or []

    def is_completed(self) -> bool:
        return self.status == COMPLETED

    def is_error(self) -> bool:
        return self.status == ERROR

    def describe_verification(self) -> str:
        verification = self._data.get("verification") or {}
        if not verification.get("executed"):
            return f"não executada ({verification.get('command') or 'sem comando'})"
        exit_code = verification.get("exitCode")
        icon = " ✅" if exit_code == 0 else " ❌"
        return f"`{verification.get('command')}` terminou com código {exit_code}{icon}"

    def record_changed_files(self, files: list[str]) -> None:
        self._data["changedFiles"] = files

    def record_publication(self, **publication) -> None:
        self._data["publish"] = publication
        self.save()

    def save(self) -> None:
        os.makedirs(os.path.dirname(self._path), exist_ok=True)
        with open(self._path, "w", encoding="utf-8") as file:
            json.dump(self._data, file, ensure_ascii=False, indent=2)
