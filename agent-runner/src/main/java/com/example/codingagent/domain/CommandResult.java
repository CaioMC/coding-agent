package com.example.codingagent.domain;

public record CommandResult(int exitCode, String output, boolean timedOut, long durationMs) {

    public String toObservation() {
        StringBuilder observation = new StringBuilder("exit_code=").append(this.exitCode);
        if (this.timedOut) {
            observation.append(" (TEMPO ESGOTADO: o comando foi interrompido)");
        }
        observation.append(" duracao_ms=").append(this.durationMs).append('\n');
        observation.append(this.output == null || this.output.isBlank() ? "(sem saída)" : this.output);
        return observation.toString();
    }

    public String outputTail(int maxChars) {
        if (this.output == null) {
            return "";
        }
        return this.output.length() > maxChars ? this.output.substring(this.output.length() - maxChars) : this.output;
    }
}
