package com.empresa.copilotlogistico.agent.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Executa um processo com tempo limite e captura a saída combinada, truncada no final. */
final class ProcessRunner {

    private ProcessRunner() {
    }

    static CommandResult run(List<String> argv, Path workDir, Map<String, String> extraEnv,
                             Duration timeout, int maxOutputChars) {
        long start = System.nanoTime();
        ProcessBuilder pb = new ProcessBuilder(argv).redirectErrorStream(true);
        if (workDir != null) {
            pb.directory(workDir.toFile());
        }
        if (extraEnv != null) {
            pb.environment().putAll(extraEnv);
        }
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao iniciar: " + argv, e);
        }
        TailBuffer buffer = new TailBuffer(maxOutputChars);
        Thread reader = new Thread(() -> pump(process.getInputStream(), buffer), "cmd-output");
        reader.setDaemon(true);
        reader.start();

        boolean finished;
        try {
            finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            reader.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            finished = false;
        }
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        int exit = finished ? process.exitValue() : 124;
        return new CommandResult(exit, buffer.toString(), !finished, durationMs);
    }

    private static void pump(InputStream in, TailBuffer buffer) {
        byte[] chunk = new byte[8192];
        try (in) {
            int n;
            while ((n = in.read(chunk)) != -1) {
                buffer.append(new String(chunk, 0, n, StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
            // Processo encerrado.
        }
    }

    /** Guarda só o final da saída: em builds e testes, o erro relevante costuma estar no fim. */
    static final class TailBuffer {
        private final int max;
        private final StringBuilder sb = new StringBuilder();
        private long dropped;

        TailBuffer(int max) {
            this.max = max;
        }

        synchronized void append(String s) {
            sb.append(s);
            if (sb.length() > max) {
                int excess = sb.length() - max;
                sb.delete(0, excess);
                dropped += excess;
            }
        }

        @Override
        public synchronized String toString() {
            if (dropped == 0) {
                return sb.toString();
            }
            return "[... " + dropped + " caracteres iniciais omitidos ...]\n" + sb;
        }
    }
}
