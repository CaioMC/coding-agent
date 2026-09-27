package com.empresa.copilotlogistico.codingagent;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

/**
 * Ferramentas que o SEU assistente (Copilot Logístico) passa a oferecer ao modelo.
 * Registre com: chatClientBuilder.defaultTools(codingAgentTools)
 *
 * O modelo do assistente não escreve código aqui: ele só dispara e acompanha a pipeline.
 * Quem codifica é o agent-runner, dentro da pipeline.
 */
public class CodingAgentTools {

    private final CodingTaskService service;

    public CodingAgentTools(CodingTaskService service) {
        this.service = service;
    }

    @Tool(name = "iniciar_codificacao", description = """
            Dispara o agente de codificação numa pipeline para implementar uma tarefa já definida
            (normalmente uma issue do Jira). O agente cria uma branch, implementa, roda os testes e abre
            um pull request em rascunho para aprovação humana. Leva de alguns minutos a uma hora.
            Só chame depois que o usuário confirmar a tarefa e o repositório.
            Devolve um requestId para acompanhar com consultar_codificacao.""")
    public String startCodingTask(
            @ToolParam(description = "Chave da issue no Jira, ex.: LOG-123") String issueKey,
            @ToolParam(description = "Título curto da tarefa") String title,
            @ToolParam(description = "Descrição detalhada do que implementar") String description,
            @ToolParam(description = "Critérios de aceite, um por item", required = false) List<String> acceptanceCriteria,
            @ToolParam(description = "Nome do repositório alvo (Azure Repos) ou owner/repo (GitHub)") String repository,
            @ToolParam(description = "Branch base; padrão main", required = false) String baseBranch,
            @ToolParam(description = "Orientações extras de implementação", required = false) String extraInstructions) {
        try {
            CodingTaskStatus status = service.start(new CodingTaskRequest(issueKey, title, description,
                    acceptanceCriteria, extraInstructions, repository, baseBranch));
            return "Codificação iniciada. requestId=" + status.requestId()
                    + ", branch=" + status.workBranch()
                    + (status.runUrl() == null ? "" : ", execução: " + status.runUrl())
                    + ". Informe ao usuário e consulte o andamento com consultar_codificacao.";
        } catch (RuntimeException e) {
            return "Não foi possível iniciar a codificação: " + e.getMessage();
        }
    }

    @Tool(name = "consultar_codificacao", description = """
            Consulta o andamento de uma codificação iniciada com iniciar_codificacao. Quando terminar,
            devolve o link do pull request, os arquivos alterados, o resultado dos testes e o resumo do agente.""")
    public String getCodingTaskStatus(
            @ToolParam(description = "requestId devolvido por iniciar_codificacao") String requestId) {
        try {
            return service.status(requestId).toToolAnswer();
        } catch (RuntimeException e) {
            return "Não foi possível consultar: " + e.getMessage();
        }
    }
}
