package com.example.codingagent.infrastructure.persistence;

import com.example.codingagent.application.port.TaskReader;
import com.example.codingagent.domain.AgentTask;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JsonTaskReader implements TaskReader {

    private final Path taskFile;
    private final ObjectMapper mapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public JsonTaskReader(Path taskFile) {
        this.taskFile = taskFile;
    }

    @Override
    public AgentTask read() {
        try {
            return this.mapper.readValue(Files.readString(this.taskFile), AgentTask.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler a tarefa em " + this.taskFile, e);
        }
    }
}
