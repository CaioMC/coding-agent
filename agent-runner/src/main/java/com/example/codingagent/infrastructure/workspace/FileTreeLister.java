package com.example.codingagent.infrastructure.workspace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class FileTreeLister {

    private final SafePathResolver paths;

    FileTreeLister(SafePathResolver paths) {
        this.paths = paths;
    }

    List<String> list(String relativeDir, int maxDepth, int maxEntries) {
        Path start = this.paths.resolve(relativeDir);
        if (!Files.isDirectory(start)) {
            throw new IllegalArgumentException("Não é um diretório: " + relativeDir);
        }
        List<String> entries = new ArrayList<>();
        try {
            Files.walkFileTree(start, Set.of(), Math.max(1, maxDepth), new Collector(start, entries, maxEntries));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        entries.sort(String::compareTo);
        return entries;
    }

    private final class Collector extends SimpleFileVisitor<Path> {

        private final Path start;
        private final List<String> entries;
        private final int maxEntries;

        private Collector(Path start, List<String> entries, int maxEntries) {
            this.start = start;
            this.entries = entries;
            this.maxEntries = maxEntries;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
            if (directory.equals(this.start)) {
                return FileVisitResult.CONTINUE;
            }
            if (IgnoredDirectories.isIgnored(directory)) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            this.addDirectory(directory);
            return this.continueOrStop();
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
            if (!attributes.isDirectory()) {
                this.entries.add(FileTreeLister.this.paths.relative(file));
            } else if (!IgnoredDirectories.isIgnored(file)) {
                this.addDirectory(file);
            }
            return this.continueOrStop();
        }

        private void addDirectory(Path directory) {
            this.entries.add(FileTreeLister.this.paths.relative(directory) + "/");
        }

        private FileVisitResult continueOrStop() {
            return this.entries.size() >= this.maxEntries ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
        }
    }
}
