package com.example.codingagent.infrastructure.persistence;

import com.example.codingagent.application.port.ExecutionJournal;
import com.example.codingagent.application.port.JournalEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class JsonlExecutionJournal implements ExecutionJournal {

    private static final int MAX_VALUE_CHARS = 4000;

    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper();

    public JsonlExecutionJournal(Path file) {
        this.file = file;
        this.startEmpty();
    }

    @Override
    public synchronized void record(JournalEvent event, Map<String, ?> data) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("ts", Instant.now().toString());
        entry.put("type", event.type());
        data.forEach((key, value) -> entry.put(key, this.truncate(value)));
        try {
            String line = this.mapper.writeValueAsString(entry) + "\n";
            Files.writeString(this.file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void startEmpty() {
        try {
            if (this.file.getParent() != null) {
                Files.createDirectories(this.file.getParent());
            }
            Files.deleteIfExists(this.file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Object truncate(Object value) {
        if (value instanceof String text && text.length() > MAX_VALUE_CHARS) {
            return text.substring(0, MAX_VALUE_CHARS) + "...[truncado]";
        }
        return value;
    }
}
