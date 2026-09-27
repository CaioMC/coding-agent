package com.example.codingagent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Resultado gravado em result.json. O workflow completa com prUrl/branch depois do push,
 * e o assistente lê esse arquivo para responder ao usuário.
 *
 * status:
 *  - COMPLETED:  o agente terminou e a verificação passou (ou não havia verificação)
 *  - VERIFICATION_FAILED: o agente terminou, mas a verificação independente falhou
 *  - INCOMPLETE: o agente declarou que não conseguiu concluir
 *  - BUDGET_EXCEEDED: atingiu o limite de iterações ou de chamadas de ferramenta
 *  - ERROR: falha inesperada (ex.: modelo indisponível)
 */
public record AgentResult(
        String requestId,
        Integer issueNumber,
        String status,
        String summary,
        int iterations,
        int toolCalls,
        List<String> commandsRun,
        Verification verification,
        String model) {

    public record Verification(String command, boolean executed, Integer exitCode, String outputTail) {
        public static Verification skipped(String reason) {
            return new Verification(reason, false, null, null);
        }
    }

    public void writeTo(Path file) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(file.toFile(), this);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
