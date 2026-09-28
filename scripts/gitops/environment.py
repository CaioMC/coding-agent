from __future__ import annotations

import os
from collections.abc import Mapping


class MissingVariableError(Exception):
    pass


class Environment:

    def __init__(self, values: Mapping[str, str] | None = None):
        self._values = os.environ if values is None else values

    def get(self, name: str, default: str = "") -> str:
        return self._values.get(name) or default

    def require(self, name: str) -> str:
        value = self._values.get(name)
        if not value:
            raise MissingVariableError(f"Variável de ambiente obrigatória ausente: {name}")
        return value

    def flag(self, name: str, default: bool) -> bool:
        return self.get(name, str(default)).lower() == "true"
