package com.empresa.copilotlogistico.codingagent.pipeline;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Dispara e acompanha o workflow no GitHub Actions.
 *
 * O endpoint de dispatch não devolve o id da execução, então o workflow usa
 * "run-name: coding-agent {request_id}" e aqui procuramos a execução por esse título.
 * Token: GitHub App ou PAT fine-grained com "Actions: read and write" no repositório do agente.
 */
public class GitHubActionsClient implements CodingPipelineClient {

    private final String apiBase;
    private final String agentRepo;
    private final String workflowFile;
    private final String ref;
    private final String artifactName;
    private final HttpSupport http;

    public GitHubActionsClient(String apiBase, String agentRepo, String workflowFile, String ref,
                               String artifactName, Supplier<String> token) {
        this.apiBase = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
        this.agentRepo = agentRepo;
        this.workflowFile = workflowFile;
        this.ref = ref;
        this.artifactName = artifactName;
        this.http = new HttpSupport(() -> "Bearer " + token.get(),
                Map.of("X-GitHub-Api-Version", "2022-11-28"));
    }

    @Override
    public PipelineRun start(Map<String, String> parameters) {
        // Nomes dos inputs do workflow usam snake_case.
        Map<String, String> inputs = Map.of(
                "request_id", parameters.get("requestId"),
                "target_repository", parameters.get("targetRepository"),
                "base_branch", parameters.get("baseBranch"),
                "work_branch", parameters.get("workBranch"),
                "task_b64", parameters.get("taskB64"));
        String url = apiBase + "/repos/" + agentRepo + "/actions/workflows/" + workflowFile + "/dispatches";
        http.json("POST", url, Map.of("ref", ref, "inputs", inputs));
        return findRun(new PipelineRun(parameters.get("requestId"), null, null));
    }

    @Override
    public PipelineState state(PipelineRun run) {
        PipelineRun resolved = run.runId() == null ? findRun(run) : run;
        if (resolved.runId() == null) {
            return new PipelineState(PipelineState.Phase.QUEUED, resolved);
        }
        JsonNode node = http.json("GET", apiBase + "/repos/" + agentRepo + "/actions/runs/" + resolved.runId(), null);
        String status = node.path("status").asText("");
        String conclusion = node.path("conclusion").asText("");
        PipelineState.Phase phase;
        if ("completed".equals(status)) {
            phase = switch (conclusion) {
                case "success" -> PipelineState.Phase.SUCCEEDED;
                case "cancelled" -> PipelineState.Phase.CANCELED;
                default -> PipelineState.Phase.FAILED;
            };
        } else if ("in_progress".equals(status)) {
            phase = PipelineState.Phase.RUNNING;
        } else {
            phase = PipelineState.Phase.QUEUED;
        }
        return new PipelineState(phase, resolved.withRun(resolved.runId(), node.path("html_url").asText(resolved.webUrl())));
    }

    @Override
    public Optional<Map<String, byte[]>> downloadResult(PipelineRun run) {
        if (run.runId() == null) {
            return Optional.empty();
        }
        JsonNode list = http.json("GET", apiBase + "/repos/" + agentRepo + "/actions/runs/" + run.runId()
                + "/artifacts?name=" + artifactName, null);
        for (JsonNode artifact : list.path("artifacts")) {
            if (artifactName.equals(artifact.path("name").asText()) && !artifact.path("expired").asBoolean(false)) {
                return Optional.of(HttpSupport.unzip(http.download(artifact.path("archive_download_url").asText())));
            }
        }
        return Optional.empty();
    }

    private PipelineRun findRun(PipelineRun run) {
        String expectedTitle = "coding-agent " + run.requestId();
        JsonNode runs = http.json("GET", apiBase + "/repos/" + agentRepo + "/actions/workflows/" + workflowFile
                + "/runs?event=workflow_dispatch&per_page=30", null);
        for (JsonNode r : runs.path("workflow_runs")) {
            if (expectedTitle.equals(r.path("display_title").asText())) {
                return run.withRun(r.path("id").asText(), r.path("html_url").asText(null));
            }
        }
        return run;
    }
}
