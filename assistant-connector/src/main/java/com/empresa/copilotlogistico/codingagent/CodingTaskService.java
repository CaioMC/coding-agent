package com.empresa.copilotlogistico.codingagent;

import com.empresa.copilotlogistico.codingagent.pipeline.CodingPipelineClient;
import com.empresa.copilotlogistico.codingagent.pipeline.PipelineRun;
import com.empresa.copilotlogistico.codingagent.pipeline.PipelineState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orquestra o pedido de codificação do lado do assistente:
 * gera o id e o nome da branch, dispara a pipeline, acompanha e interpreta o resultado.
 *
 * O registro das tarefas está em memória para a POC. Em produção, troque por uma tabela
 * (requestId, usuário, issue, runId, status), para sobreviver a reinícios e permitir auditoria.
 */
public class CodingTaskService {

    private static final int PATCH_PREVIEW_CHARS = 3000;

    private final CodingPipelineClient pipeline;
    private final String defaultBaseBranch;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Tracked> tasks = new ConcurrentHashMap<>();

    private record Tracked(CodingTaskRequest request, String workBranch, PipelineRun run) {
    }

    public CodingTaskService(CodingPipelineClient pipeline, String defaultBaseBranch) {
        this.pipeline = pipeline;
        this.defaultBaseBranch = defaultBaseBranch;
    }

    public CodingTaskStatus start(CodingTaskRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new IllegalArgumentException("Título da tarefa é obrigatório");
        }
        if (request.repository() == null || request.repository().isBlank()) {
            throw new IllegalArgumentException("Repositório alvo é obrigatório");
        }
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        String issue = request.issueKey() == null || request.issueKey().isBlank() ? "TASK" : request.issueKey();
        String workBranch = "agent/" + issue.replaceAll("[^A-Za-z0-9._-]", "-") + "-" + requestId;
        String base = request.baseBranch() == null || request.baseBranch().isBlank() ? defaultBaseBranch : request.baseBranch();

        Map<String, Object> task = new LinkedHashMap<>();
        task.put("requestId", requestId);
        task.put("issueKey", issue);
        task.put("title", request.title());
        task.put("description", request.description());
        task.put("acceptanceCriteria", request.acceptanceCriteria() == null ? List.of() : request.acceptanceCriteria());
        task.put("extraInstructions", request.extraInstructions());

        Map<String, String> params = new LinkedHashMap<>();
        params.put("requestId", requestId);
        params.put("targetRepository", request.repository());
        params.put("baseBranch", base);
        params.put("workBranch", workBranch);
        params.put("taskB64", encode(task));

        PipelineRun run = pipeline.start(params);
        tasks.put(requestId, new Tracked(request, workBranch, run));
        return new CodingTaskStatus(requestId, issue, workBranch, PipelineState.Phase.QUEUED, run.webUrl(),
                null, null, null, List.of(), null, null);
    }

    public CodingTaskStatus status(String requestId) {
        Tracked tracked = tasks.get(requestId);
        if (tracked == null) {
            throw new IllegalArgumentException("Solicitação não encontrada: " + requestId);
        }
        PipelineState state = pipeline.state(tracked.run());
        tasks.put(requestId, new Tracked(tracked.request(), tracked.workBranch(), state.run()));
        String issue = tracked.request().issueKey();

        if (!state.phase().finished()) {
            return new CodingTaskStatus(requestId, issue, tracked.workBranch(), state.phase(), state.run().webUrl(),
                    null, null, null, List.of(), null, null);
        }

        Optional<Map<String, byte[]>> artifact = pipeline.downloadResult(state.run());
        if (artifact.isEmpty() || !artifact.get().containsKey("result.json")) {
            return new CodingTaskStatus(requestId, issue, tracked.workBranch(), state.phase(), state.run().webUrl(),
                    "SEM_RESULTADO", "A pipeline terminou sem publicar result.json. Veja o log da execução.",
                    null, List.of(), null, null);
        }
        Map<String, byte[]> files = artifact.get();
        JsonNode result = readJson(files.get("result.json"));
        JsonNode verification = result.path("verification");
        String verificationText = verification.path("executed").asBoolean(false)
                ? verification.path("command").asText() + " -> código " + verification.path("exitCode").asText()
                : "não executada";
        List<String> changed = new ArrayList<>();
        result.path("changedFiles").forEach(n -> changed.add(n.asText()));
        String patch = files.containsKey("changes.patch")
                ? new String(files.get("changes.patch"), StandardCharsets.UTF_8) : null;
        if (patch != null && patch.length() > PATCH_PREVIEW_CHARS) {
            patch = patch.substring(0, PATCH_PREVIEW_CHARS) + "\n... (diff completo no PR)";
        }
        return new CodingTaskStatus(requestId, issue, tracked.workBranch(), state.phase(), state.run().webUrl(),
                result.path("status").asText(null), result.path("summary").asText(null),
                result.path("publish").path("prUrl").asText(null), changed, verificationText, patch);
    }

    private String encode(Map<String, Object> task) {
        try {
            return Base64.getEncoder().encodeToString(mapper.writeValueAsBytes(task));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private JsonNode readJson(byte[] bytes) {
        try {
            return mapper.readTree(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
