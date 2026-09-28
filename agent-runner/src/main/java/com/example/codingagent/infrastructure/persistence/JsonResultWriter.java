package com.example.codingagent.infrastructure.persistence;

import com.example.codingagent.application.port.ResultWriter;
import com.example.codingagent.domain.AgentResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JsonResultWriter implements ResultWriter {

    private final Path resultFile;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public JsonResultWriter(Path resultFile) {
        this.resultFile = resultFile;
    }

    @Override
    public void write(AgentResult result) {
        try {
            if (this.resultFile.getParent() != null) {
                Files.createDirectories(this.resultFile.getParent());
            }
            this.mapper.writeValue(this.resultFile.toFile(), result);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível gravar " + this.resultFile, e);
        }
    }
}
