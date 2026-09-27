package com.example.codingagent.core;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Diário da execução: cada chamada de ferramenta vira uma linha JSON em journal.jsonl.
 * Serve para auditoria (anexado ao PR como artefato) e para controlar o orçamento
 * de chamadas de ferramenta.
 */
public class ExecutionJournal {

    private static final int MAX_LOGGED_CHARS = 4000;

    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper();
    private final int maxToolCalls;
    private final List<String> commandsRun = Collections.synchronizedList(new ArrayList<>());
    private int toolCalls;

    public ExecutionJournal(Path file, int maxToolCalls) {
        this.file = file;
        this.maxToolCalls = maxToolCalls;
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Registra o início de uma chamada e diz se ainda há orçamento. */
    public synchronized boolean startToolCall() {
        toolCalls++;
        return toolCalls <= maxToolCalls;
    }

    public synchronized int toolCalls() {
        return toolCalls;
    }

    public int maxToolCalls() {
        return maxToolCalls;
    }

    public void recordCommand(String command) {
        commandsRun.add(command);
    }

    public List<String> commandsRun() {
        return List.copyOf(commandsRun);
    }

    public void log(String type, Map<String, ?> data) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("ts", Instant.now().toString());
        entry.put("type", type);
        data.forEach((k, v) -> entry.put(k, truncate(v)));
        try {
            String line = mapper.writeValueAsString(entry) + "\n";
            synchronized (this) {
                Files.writeString(file, line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Object truncate(Object value) {
        if (value instanceof String s && s.length() > MAX_LOGGED_CHARS) {
            return s.substring(0, MAX_LOGGED_CHARS) + "...[truncado]";
        }
        return value;
    }
}
