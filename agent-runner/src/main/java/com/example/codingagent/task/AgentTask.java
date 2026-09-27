package com.example.codingagent.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A tarefa que o agente vai implementar.
 *
 * Nasce de uma issue do GitHub: o assistente abre a issue, o workflow lê a issue
 * (scripts/gitops.py fetch-issue) e grava este JSON em task.json.
 * As linhas "- [ ] ..." do corpo da issue viram critérios de aceite.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentTask(
        String requestId,
        Integer issueNumber,
        String issueUrl,
        String title,
        String description,
        List<String> acceptanceCriteria) {

    public String toPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Tarefa: issue #").append(issueNumber == null ? "?" : issueNumber).append('\n');
        sb.append("## Título\n").append(title).append("\n\n");
        if (description != null && !description.isBlank()) {
            sb.append("## Descrição (corpo da issue)\n").append(description).append("\n\n");
        }
        if (acceptanceCriteria != null && !acceptanceCriteria.isEmpty()) {
            sb.append("## Critérios de aceite\n");
            acceptanceCriteria.forEach(c -> sb.append("- ").append(c).append('\n'));
        }
        return sb.toString();
    }
}
