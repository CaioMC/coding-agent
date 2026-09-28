from __future__ import annotations

import json
import os
import re
from dataclasses import dataclass, field

CHECKBOX = re.compile(r"^\s*[-*]\s*\[[ xX]\]\s*(.+)$")


def parse_acceptance_criteria(body: str | None) -> list[str]:
    return [match.group(1).strip() for line in (body or "").splitlines() if (match := CHECKBOX.match(line))]


@dataclass
class Task:
    issue_number: int
    title: str
    description: str = ""
    request_id: str = ""
    issue_url: str | None = None
    acceptance_criteria: list[str] = field(default_factory=list)

    @classmethod
    def from_issue(cls, issue: dict, number: int, request_id: str) -> Task:
        body = issue.get("body") or ""
        return cls(
            issue_number=number,
            title=issue.get("title") or "",
            description=body,
            request_id=request_id,
            issue_url=issue.get("html_url"),
            acceptance_criteria=parse_acceptance_criteria(body),
        )

    @classmethod
    def load(cls, path: str) -> Task:
        with open(path, encoding="utf-8") as file:
            data = json.load(file)
        return cls(
            issue_number=data.get("issueNumber"),
            title=data.get("title") or "",
            description=data.get("description") or "",
            request_id=data.get("requestId") or "",
            issue_url=data.get("issueUrl"),
            acceptance_criteria=data.get("acceptanceCriteria") or [],
        )

    def save(self, path: str) -> None:
        os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
        with open(path, "w", encoding="utf-8") as file:
            json.dump(self.to_json(), file, ensure_ascii=False, indent=2)

    def to_json(self) -> dict:
        return {
            "requestId": self.request_id,
            "issueNumber": self.issue_number,
            "issueUrl": self.issue_url,
            "title": self.title,
            "description": self.description,
            "acceptanceCriteria": self.acceptance_criteria,
        }
