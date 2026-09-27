package com.empresa.copilotlogistico.codingagent.pipeline;

/**
 * Referência a uma execução de pipeline.
 * No GitHub o id só é conhecido depois que a execução aparece na API, por isso pode ser null no início.
 */
public record PipelineRun(String requestId, String runId, String webUrl) {

    public PipelineRun withRun(String newRunId, String newWebUrl) {
        return new PipelineRun(requestId, newRunId, newWebUrl);
    }
}
