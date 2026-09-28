package com.example.codingagent.infrastructure.workspace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class SafePathResolver {

    private static final String GIT_DIRECTORY = ".git";

    private final Path root;

    SafePathResolver(Path root) {
        try {
            this.root = root.toAbsolutePath().normalize().toRealPath();
        } catch (IOException e) {
            throw new UncheckedIOException("Workspace não encontrado: " + root, e);
        }
    }

    Path root() {
        return this.root;
    }

    Path resolve(String relativePath) {
        Path candidate = this.root.resolve(this.clean(relativePath)).normalize();
        this.ensureInsideRoot(candidate, relativePath);
        this.ensureNoSymlinkEscape(candidate, relativePath);
        this.ensureOutsideGitDirectory(candidate);
        return candidate;
    }

    String relative(Path absolute) {
        return this.root.relativize(absolute).toString().replace('\\', '/');
    }

    Path relativePath(Path absolute) {
        return this.root.relativize(absolute);
    }

    private String clean(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return ".";
        }
        String trimmed = relativePath.trim();
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }

    private void ensureInsideRoot(Path candidate, String requested) {
        if (!candidate.startsWith(this.root)) {
            throw new SecurityException("Caminho fora do workspace: " + requested);
        }
    }

    private void ensureNoSymlinkEscape(Path candidate, String requested) {
        if (!Files.exists(candidate)) {
            return;
        }
        try {
            if (!candidate.toRealPath().startsWith(this.root)) {
                throw new SecurityException("Link simbólico aponta para fora do workspace: " + requested);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void ensureOutsideGitDirectory(Path candidate) {
        Path relative = this.root.relativize(candidate);
        if (relative.getNameCount() > 0 && relative.getName(0).toString().equals(GIT_DIRECTORY)) {
            throw new SecurityException("Acesso à pasta .git não é permitido");
        }
    }
}
