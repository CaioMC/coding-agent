package com.example.codingagent.infrastructure.executor;

import com.example.codingagent.domain.CommandResult;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class ProcessRunner {

    private static final int TIMED_OUT_EXIT_CODE = 124;
    private static final int READ_CHUNK_BYTES = 8192;

    private final int maxOutputChars;

    ProcessRunner(int maxOutputChars) {
        this.maxOutputChars = maxOutputChars;
    }

    CommandResult run(List<String> argv, Path workDir, Map<String, String> extraEnv, Duration timeout) {
        long start = System.nanoTime();
        Process process = this.start(argv, workDir, extraEnv);
        OutputTail output = new OutputTail(this.maxOutputChars);
        Thread reader = this.startReader(process, output);

        boolean finished = this.awaitOrKill(process, reader, timeout);

        long durationMs = (System.nanoTime() - start) / 1_000_000;
        int exitCode = finished ? process.exitValue() : TIMED_OUT_EXIT_CODE;
        return new CommandResult(exitCode, output.toString(), !finished, durationMs);
    }

    private Process start(List<String> argv, Path workDir, Map<String, String> extraEnv) {
        ProcessBuilder builder = new ProcessBuilder(argv).redirectErrorStream(true);
        if (workDir != null) {
            builder.directory(workDir.toFile());
        }
        if (extraEnv != null) {
            builder.environment().putAll(extraEnv);
        }
        try {
            return builder.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao iniciar: " + argv, e);
        }
    }

    private Thread startReader(Process process, OutputTail output) {
        Thread reader = new Thread(() -> this.pump(process.getInputStream(), output), "cmd-output");
        reader.setDaemon(true);
        reader.start();
        return reader;
    }

    private boolean awaitOrKill(Process process, Thread reader, Duration timeout) {
        try {
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            reader.join(2000);
            return finished;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return false;
        }
    }

    private void pump(InputStream in, OutputTail output) {
        byte[] chunk = new byte[READ_CHUNK_BYTES];
        try (in) {
            int read;
            while ((read = in.read(chunk)) != -1) {
                output.append(new String(chunk, 0, read, StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
        }
    }
}
