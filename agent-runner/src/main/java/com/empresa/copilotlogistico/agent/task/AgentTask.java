package com.empresa.copilotlogistico.agent.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A tarefa enviada pelo assistente (Copilot Logístico) para a pipeline.
 * Chega como JSON, em base64, no parâmetro "taskB64" da pipeline.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentTask(
        String requestId,
        String issueKey,
        String title,
        String description,
        List<String> acceptanceCriteria,
        String extraInstructions) {

    public String toPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Tarefa ").append(issueKey == null ? "" : issueKey).append('\n');
        sb.append("## Título\n").append(title).append("\n\n");
        if (description != null && !description.isBlank()) {
            sb.append("## Descrição\n").append(description).append("\n\n");
        }
        if (acceptanceCriteria != null && !acceptanceCriteria.isEmpty()) {
            sb.append("## Critérios de aceite\n");
            acceptanceCriteria.forEach(c -> sb.append("- ").append(c).append('\n'));
            sb.append('\n');
        }
        if (extraInstructions != null && !extraInstructions.isBlank()) {
            sb.append("## Instruções adicionais\n").append(extraInstructions).append('\n');
        }
        return sb.toString();
    }
}
