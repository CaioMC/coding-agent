package com.empresa.copilotlogistico.codingagent.pipeline;

/** Estado da execução, normalizado entre Azure Pipelines e GitHub Actions. */
public record PipelineState(Phase phase, PipelineRun run) {

    public enum Phase {
        QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELED;

        public boolean finished() {
            return this == SUCCEEDED || this == FAILED || this == CANCELED;
        }
    }
}
