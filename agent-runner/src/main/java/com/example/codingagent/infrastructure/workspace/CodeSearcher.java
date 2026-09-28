package com.example.codingagent.infrastructure.workspace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

final class CodeSearcher {

    private static final int MAX_LINE_CHARS = 200;

    private final SafePathResolver paths;
    private final long maxFileBytes;

    CodeSearcher(SafePathResolver paths, long maxFileBytes) {
        this.paths = paths;
        this.maxFileBytes = maxFileBytes;
    }

    List<String> search(String regex, String pathFilter, int maxResults) {
        Pattern pattern = this.compile(regex);
        String filter = pathFilter == null ? "" : pathFilter.trim();
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(this.paths.root())) {
            files.filter(Files::isRegularFile)
                    .filter(file -> !IgnoredDirectories.containsIgnoredSegment(this.paths.relativePath(file)))
                    .filter(file -> filter.isEmpty() || this.paths.relative(file).contains(filter))
                    .sorted()
                    .forEach(file -> this.collectHits(file, pattern, hits, maxResults));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return hits;
    }

    private Pattern compile(String regex) {
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            return Pattern.compile(Pattern.quote(regex));
        }
    }

    private void collectHits(Path file, Pattern pattern, List<String> hits, int maxResults) {
        if (hits.size() >= maxResults) {
            return;
        }
        try {
            if (Files.size(file) > this.maxFileBytes) {
                return;
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int index = 0; index < lines.size() && hits.size() < maxResults; index++) {
                if (pattern.matcher(lines.get(index)).find()) {
                    hits.add(this.paths.relative(file) + ":" + (index + 1) + ": " + this.shorten(lines.get(index)));
                }
            }
        } catch (IOException | UncheckedIOException binaryOrInvalidEncoding) {
        }
    }

    private String shorten(String line) {
        String stripped = line.strip();
        return stripped.length() > MAX_LINE_CHARS ? stripped.substring(0, MAX_LINE_CHARS) + "..." : stripped;
    }
}
