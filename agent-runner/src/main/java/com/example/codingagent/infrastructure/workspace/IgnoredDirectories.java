package com.example.codingagent.infrastructure.workspace;

import java.nio.file.Path;
import java.util.Set;

final class IgnoredDirectories {

    private static final Set<String> NAMES = Set.of(
            ".git", "node_modules", "target", "build", "dist", ".gradle", ".idea", ".venv", "__pycache__");

    private IgnoredDirectories() {
    }

    static boolean isIgnored(Path directory) {
        Path name = directory.getFileName();
        return name != null && NAMES.contains(name.toString());
    }

    static boolean containsIgnoredSegment(Path relativePath) {
        for (Path segment : relativePath) {
            if (NAMES.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }
}
