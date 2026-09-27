package com.empresa.copilotlogistico.agent.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * Acesso a arquivos restrito à pasta do repositório clonado.
 *
 * Toda operação passa por {@link #resolve(String)}, que impede sair da raiz
 * (ex.: "../../etc/passwd") e bloqueia a pasta .git, para que o modelo não
 * altere histórico, remotes ou credenciais.
 */
public class Workspace {

    private static final Set<String> IGNORED_DIRS = Set.of(
            ".git", "node_modules", "target", "build", "dist", ".gradle", ".idea", ".venv", "__pycache__");

    private static final long MAX_READ_BYTES = 512 * 1024;

    private final Path root;

    public Workspace(Path root) {
        try {
            this.root = root.toAbsolutePath().normalize().toRealPath();
        } catch (IOException e) {
            throw new UncheckedIOException("Workspace não encontrado: " + root, e);
        }
    }

    public Path root() {
        return root;
    }

    /** Converte um caminho relativo vindo do modelo em um caminho seguro dentro da raiz. */
    public Path resolve(String relativePath) {
        String cleaned = relativePath == null || relativePath.isBlank() ? "." : relativePath.trim();
        if (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        Path candidate = root.resolve(cleaned).normalize();
        if (!candidate.startsWith(root)) {
            throw new SecurityException("Caminho fora do workspace: " + relativePath);
        }
        // Se já existe, resolve links simbólicos para impedir fuga via symlink.
        if (Files.exists(candidate)) {
            try {
                Path real = candidate.toRealPath();
                if (!real.startsWith(root)) {
                    throw new SecurityException("Link simbólico aponta para fora do workspace: " + relativePath);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        Path rel = root.relativize(candidate);
        if (rel.getNameCount() > 0 && rel.getName(0).toString().equals(".git")) {
            throw new SecurityException("Acesso à pasta .git não é permitido");
        }
        return candidate;
    }

    public String relative(Path absolute) {
        return root.relativize(absolute).toString().replace('\\', '/');
    }

    public List<String> listFiles(String relativeDir, int maxDepth, int maxEntries) {
        Path dir = resolve(relativeDir);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Não é um diretório: " + relativeDir);
        }
        List<String> out = new ArrayList<>();
        try {
            Files.walkFileTree(dir, Set.of(), Math.max(1, maxDepth), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                    if (!d.equals(dir) && IGNORED_DIRS.contains(d.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (!d.equals(dir)) {
                        out.add(relative(d) + "/");
                    }
                    return out.size() >= maxEntries ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) {
                    if (attrs.isDirectory()) {
                        // Diretórios no limite de profundidade chegam aqui.
                        if (!IGNORED_DIRS.contains(f.getFileName().toString())) {
                            out.add(relative(f) + "/");
                        }
                    } else {
                        out.add(relative(f));
                    }
                    return out.size() >= maxEntries ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        out.sort(String::compareTo);
        return out;
    }

    /** Lê um arquivo com números de linha, opcionalmente num intervalo (1-based, inclusivo). */
    public String readFile(String relativePath, Integer startLine, Integer endLine) {
        Path file = resolve(relativePath);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Arquivo não encontrado: " + relativePath);
        }
        try {
            if (Files.size(file) > MAX_READ_BYTES) {
                throw new IllegalArgumentException("Arquivo grande demais para leitura integral ("
                        + Files.size(file) + " bytes). Use startLine/endLine ou search_code.");
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            int from = startLine == null || startLine < 1 ? 1 : startLine;
            int to = endLine == null || endLine < 1 ? lines.size() : Math.min(endLine, lines.size());
            StringBuilder sb = new StringBuilder();
            for (int i = from; i <= to; i++) {
                sb.append(i).append('\t').append(lines.get(i - 1)).append('\n');
            }
            if (sb.length() == 0) {
                sb.append("(arquivo vazio ou intervalo sem linhas; total de linhas: ").append(lines.size()).append(")");
            }
            return sb.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void writeFile(String relativePath, String content) {
        Path file = resolve(relativePath);
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, content == null ? "" : content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Substitui um trecho exato. Exige que o trecho apareça exatamente uma vez,
     * o que evita edições ambíguas (uma das lições do SWE-agent sobre ferramentas de edição).
     */
    public void replaceInFile(String relativePath, String oldText, String newText) {
        if (oldText == null || oldText.isEmpty()) {
            throw new IllegalArgumentException("oldText não pode ser vazio");
        }
        Path file = resolve(relativePath);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Arquivo não encontrado: " + relativePath);
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
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
            Files.writeString(file, updated, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Busca por regex em arquivos de texto. Retorna "caminho:linha: conteúdo". */
    public List<String> search(String regex, String pathFilter, int maxResults) {
        Pattern pattern;
        try {
            pattern = Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            pattern = Pattern.compile(Pattern.quote(regex));
        }
        String filter = pathFilter == null ? "" : pathFilter.trim();
        List<String> results = new ArrayList<>();
        Pattern finalPattern = pattern;
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> !isInIgnoredDir(p))
                    .filter(p -> filter.isEmpty() || relative(p).contains(filter))
                    .sorted()
                    .forEach(p -> {
                        if (results.size() >= maxResults) {
                            return;
                        }
                        try {
                            if (Files.size(p) > MAX_READ_BYTES) {
                                return;
                            }
                            List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                            for (int i = 0; i < lines.size() && results.size() < maxResults; i++) {
                                Matcher m = finalPattern.matcher(lines.get(i));
                                if (m.find()) {
                                    String line = lines.get(i).strip();
                                    if (line.length() > 200) {
                                        line = line.substring(0, 200) + "...";
                                    }
                                    results.add(relative(p) + ":" + (i + 1) + ": " + line);
                                }
                            }
                        } catch (IOException | UncheckedIOException e) {
                            // Arquivo binário ou com encoding inválido: ignora.
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return results;
    }

    private boolean isInIgnoredDir(Path p) {
        Path rel = root.relativize(p);
        for (Path part : rel) {
            if (IGNORED_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }
}
