package com.empresa.copilotlogistico.agent.task;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TaskLoader {

    private TaskLoader() {
    }

    public static AgentTask load(Path taskFile) {
        try {
            AgentTask task = new ObjectMapper().readValue(Files.readString(taskFile), AgentTask.class);
            if (task.title() == null || task.title().isBlank()) {
                throw new IllegalArgumentException("A tarefa precisa de um título");
            }
            return task;
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler a tarefa em " + taskFile, e);
        }
    }
}
