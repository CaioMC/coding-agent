package com.example.codingagent.domain;

import java.util.List;

public record AgentTask(
        String requestId,
        Integer issueNumber,
        String issueUrl,
        String title,
        String description,
        List<String> acceptanceCriteria) {

    public AgentTask {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("A tarefa precisa de um título");
        }
        acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
    }

    public boolean hasDescription() {
        return this.description != null && !this.description.isBlank();
    }

    public boolean hasAcceptanceCriteria() {
        return !this.acceptanceCriteria.isEmpty();
    }
}
