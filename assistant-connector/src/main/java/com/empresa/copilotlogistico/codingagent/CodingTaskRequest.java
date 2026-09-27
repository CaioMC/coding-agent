package com.empresa.copilotlogistico.codingagent;

import java.util.List;

/** O que o assistente sabe sobre a tarefa quando o usuário pede a codificação. */
public record CodingTaskRequest(
        String issueKey,
        String title,
        String description,
        List<String> acceptanceCriteria,
        String extraInstructions,
        String repository,
        String baseBranch) {
}
