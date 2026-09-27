package com.example.codingagent.core;

/**
 * Resultado de um comando executado no ambiente do projeto.
 * É isto que o modelo "observa": código de saída e as últimas linhas da saída.
 */
public record CommandResult(int exitCode, String output, boolean timedOut, long durationMs) {

    /** Formato enviado ao modelo como resposta da ferramenta run_command. */
    public String toObservation() {
        StringBuilder sb = new StringBuilder();
        sb.append("exit_code=").append(exitCode);
        if (timedOut) {
            sb.append(" (TEMPO ESGOTADO: o comando foi interrompido)");
        }
        sb.append(" duracao_ms=").append(durationMs).append('\n');
        sb.append(output == null || output.isBlank() ? "(sem saída)" : output);
        return sb.toString();
    }
}
