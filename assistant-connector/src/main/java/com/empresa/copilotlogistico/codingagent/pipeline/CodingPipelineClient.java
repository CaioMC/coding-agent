package com.empresa.copilotlogistico.codingagent.pipeline;

import java.util.Map;
import java.util.Optional;

/**
 * Contrato entre o assistente e a pipeline de codificação.
 * Há uma implementação para Azure Pipelines e outra para GitHub Actions; o resto do
 * assistente não sabe qual está em uso.
 */
public interface CodingPipelineClient {

    /**
     * Dispara a pipeline.
     *
     * @param parameters requestId, targetRepository, baseBranch, workBranch, taskB64
     */
    PipelineRun start(Map<String, String> parameters);

    PipelineState state(PipelineRun run);

    /**
     * Baixa o artefato "agent-result" (result.json, changes.patch, journal.jsonl).
     * Vazio se a execução ainda não publicou o artefato.
     */
    Optional<Map<String, byte[]>> downloadResult(PipelineRun run);
}
