package com.example.codingagent.infrastructure.workspace;

import com.example.codingagent.application.port.Workspace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class LocalWorkspace implements Workspace {

    private static final long MAX_READ_BYTES = 512 * 1024;

    private final SafePathResolver paths;
    private final FileTreeLister lister;
    private final CodeSearcher searcher;

    public LocalWorkspace(Path root) {
        this.paths = new SafePathResolver(root);
        this.lister = new FileTreeLister(this.paths);
        this.searcher = new CodeSearcher(this.paths, MAX_READ_BYTES);
    }

    @Override
    public Path root() {
        return this.paths.root();
    }

    @Override
    public List<String> listFiles(String relativeDir, int maxDepth, int maxEntries) {
        return this.lister.list(relativeDir, maxDepth, maxEntries);
    }

    @Override
    public String readFile(String relativePath, Integer startLine, Integer endLine) {
        Path file = this.existingFile(relativePath);
        this.ensureReadableSize(file);
        List<String> lines = this.readLines(file);
        int from = startLine == null || startLine < 1 ? 1 : startLine;
        int to = endLine == null || endLine < 1 ? lines.size() : Math.min(endLine, lines.size());

        StringBuilder numbered = new StringBuilder();
        for (int line = from; line <= to; line++) {
            numbered.append(line).append('\t').append(lines.get(line - 1)).append('\n');
        }
        if (numbered.isEmpty()) {
            return "(arquivo vazio ou intervalo sem linhas; total de linhas: " + lines.size() + ")";
        }
        return numbered.toString();
    }

    @Override
    public Optional<String> readIfExists(String relativePath) {
        Path file = this.paths.resolve(relativePath);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean isFile(String relativePath) {
        return Files.isRegularFile(this.paths.resolve(relativePath));
    }

    @Override
    public void writeFile(String relativePath, String content) {
        Path file = this.paths.resolve(relativePath);
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, content == null ? "" : content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void replaceInFile(String relativePath, String oldText, String newText) {
        if (oldText == null || oldText.isEmpty()) {
            throw new IllegalArgumentException("oldText não pode ser vazio");
        }
        Path file = this.existingFile(relativePath);
        String content = this.readString(file);
        int first = content.indexOf(oldText);
        if (first < 0) {
            throw new IllegalArgumentException("Trecho não encontrado em " + relativePath
                    + ". Leia o arquivo de novo e copie o trecho exatamente, incluindo espaços.");
        }
        if (content.indexOf(oldText, first + 1) >= 0) {
            throw new IllegalArgumentException("Trecho aparece mais de uma vez em " + relativePath
                    + ". Inclua mais linhas de contexto para torná-lo único.");
        }
        String updated = content.substring(0, first) + (newText == null ? "" : newText)
                + content.substring(first + oldText.length());
        this.writeFile(relativePath, updated);
    }

    @Override
    public List<String> search(String regex, String pathFilter, int maxResults) {
        return this.searcher.search(regex, pathFilter, maxResults);
    }

    private Path existingFile(String relativePath) {
        Path file = this.paths.resolve(relativePath);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Arquivo não encontrado: " + relativePath);
        }
        return file;
    }

    private void ensureReadableSize(Path file) {
        try {
            long size = Files.size(file);
            if (size > MAX_READ_BYTES) {
                throw new IllegalArgumentException("Arquivo grande demais para leitura integral ("
                        + size + " bytes). Use startLine/endLine ou search_code.");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String readString(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
