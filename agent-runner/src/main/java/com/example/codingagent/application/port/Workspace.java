package com.example.codingagent.application.port;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface Workspace {

    Path root();

    List<String> listFiles(String relativeDir, int maxDepth, int maxEntries);

    String readFile(String relativePath, Integer startLine, Integer endLine);

    Optional<String> readIfExists(String relativePath);

    boolean isFile(String relativePath);

    void writeFile(String relativePath, String content);

    void replaceInFile(String relativePath, String oldText, String newText);

    List<String> search(String regex, String pathFilter, int maxResults);
}
