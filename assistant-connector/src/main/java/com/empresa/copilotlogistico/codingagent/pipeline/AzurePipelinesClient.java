package com.empresa.copilotlogistico.codingagent.pipeline;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Dispara e acompanha a pipeline no Azure Pipelines pela REST API (versão 7.1).
 *
 * Autenticação:
 *  - POC: PAT de uma conta de serviço, com escopos Build (Read & Execute).
 *  - Produção: token do Entra ID (service principal ou managed identity), via {@link #bearer(Supplier)}.
 */
public class AzurePipelinesClient implements CodingPipelineClient {

    private static final String API = "api-version=7.1";

    private final String orgUrl;
    private final String project;
    private final int pipelineId;
    private final String pipelineRef;
    private final String artifactName;
    private final HttpSupport http;

    public AzurePipelinesClient(String orgUrl, String project, int pipelineId, String pipelineRef,
                                String artifactName, Supplier<String> authorizationHeader) {
        this.orgUrl = orgUrl.endsWith("/") ? orgUrl.substring(0, orgUrl.length() - 1) : orgUrl;
        this.project = URLEncoder.encode(project, StandardCharsets.UTF_8).replace("+", "%20");
        this.pipelineId = pipelineId;
        this.pipelineRef = pipelineRef;
        this.artifactName = artifactName;
        this.http = new HttpSupport(authorizationHeader, null);
    }

    /** Cabeçalho Basic para PAT. */
    public static Supplier<String> pat(String pat) {
        String encoded = Base64.getEncoder().encodeToString((":" + pat).getBytes(StandardCharsets.UTF_8));
        return () -> "Basic " + encoded;
    }

    /** Cabeçalho Bearer para token do Entra ID (renovado a cada chamada pelo supplier). */
    public static Supplier<String> bearer(Supplier<String> tokenSupplier) {
        return () -> "Bearer " + tokenSupplier.get();
    }

    @Override
    public PipelineRun start(Map<String, String> parameters) {
        String url = orgUrl + "/" + project + "/_apis/pipelines/" + pipelineId + "/runs?" + API;
        Map<String, Object> body = Map.of(
                "resources", Map.of("repositories", Map.of("self", Map.of("refName", "refs/heads/" + pipelineRef))),
                "templateParameters", parameters);
        JsonNode run = http.json("POST", url, body);
        return new PipelineRun(parameters.get("requestId"), run.path("id").asText(),
                run.path("_links").path("web").path("href").asText(null));
    }

    @Override
    public PipelineState state(PipelineRun run) {
        String url = orgUrl + "/" + project + "/_apis/pipelines/" + pipelineId + "/runs/" + run.runId() + "?" + API;
        JsonNode node = http.json("GET", url, null);
        String state = node.path("state").asText("");
        String result = node.path("result").asText("");
        PipelineState.Phase phase;
        if ("completed".equals(state)) {
            phase = switch (result) {
                case "succeeded" -> PipelineState.Phase.SUCCEEDED;
                case "canceled" -> PipelineState.Phase.CANCELED;
                default -> PipelineState.Phase.FAILED;
            };
        } else if ("inProgress".equals(state) || "canceling".equals(state)) {
            phase = PipelineState.Phase.RUNNING;
        } else {
            phase = PipelineState.Phase.QUEUED;
        }
        String web = node.path("_links").path("web").path("href").asText(run.webUrl());
        return new PipelineState(phase, run.withRun(run.runId(), web));
    }

    @Override
    public Optional<Map<String, byte[]>> downloadResult(PipelineRun run) {
        // No Azure, o id da execução da pipeline é o mesmo id do build.
        String url = orgUrl + "/" + project + "/_apis/build/builds/" + run.runId()
                + "/artifacts?artifactName=" + artifactName + "&" + API;
        HttpResponse<byte[]> resp = http.send("GET", url, null, true);
        if (resp.statusCode() == 404) {
            return Optional.empty();
        }
        HttpSupport.ensureOk(resp, "GET", url);
        try {
            String downloadUrl = http.mapper.readTree(resp.body()).path("resource").path("downloadUrl").asText(null);
            if (downloadUrl == null) {
                return Optional.empty();
            }
            return Optional.of(HttpSupport.unzip(http.download(downloadUrl)));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
