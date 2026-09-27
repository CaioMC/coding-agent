package com.empresa.copilotlogistico.codingagent;

import com.empresa.copilotlogistico.codingagent.pipeline.PipelineState;

import java.util.List;

/** O que o assistente mostra ao usuário sobre uma tarefa de codificação. */
public record CodingTaskStatus(
        String requestId,
        String issueKey,
        String workBranch,
        PipelineState.Phase phase,
        String runUrl,
        String agentStatus,
        String summary,
        String prUrl,
        List<String> changedFiles,
        String verification,
        String patchPreview) {

    /** Texto enviado ao modelo do assistente como resposta da ferramenta. */
    public String toToolAnswer() {
        StringBuilder sb = new StringBuilder();
        sb.append("Solicitação ").append(requestId).append(" (").append(issueKey).append(")\n");
        sb.append("Pipeline: ").append(phase).append(runUrl == null ? "" : " | " + runUrl).append('\n');
        if (!phase.finished()) {
            sb.append("Ainda em execução. Consulte de novo em alguns minutos.");
            return sb.toString();
        }
        if (agentStatus != null) {
            sb.append("Status do agente: ").append(agentStatus).append('\n');
        }
        if (prUrl != null) {
            sb.append("Pull request (rascunho, aguardando aprovação humana): ").append(prUrl).append('\n');
        } else {
            sb.append("Nenhum PR foi aberto.\n");
        }
        sb.append("Branch: ").append(workBranch).append('\n');
        if (verification != null) {
            sb.append("Verificação: ").append(verification).append('\n');
        }
        if (changedFiles != null && !changedFiles.isEmpty()) {
            sb.append("Arquivos alterados:\n");
            changedFiles.forEach(f -> sb.append("- ").append(f).append('\n'));
        }
        if (summary != null && !summary.isBlank()) {
            sb.append("Resumo do agente:\n").append(summary).append('\n');
        }
        if (patchPreview != null && !patchPreview.isBlank()) {
            sb.append("Prévia do diff:\n").append(patchPreview).append('\n');
        }
        return sb.toString();
    }
}
