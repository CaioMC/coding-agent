package com.example.codingagent.infrastructure.executor;

import com.example.codingagent.application.port.CommandExecutor;
import com.example.codingagent.domain.CommandResult;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class LocalCommandExecutor implements CommandExecutor {

    private final Path workDir;
    private final ProcessRunner processRunner;

    public LocalCommandExecutor(Path workDir, int maxOutputChars) {
        this.workDir = workDir;
        this.processRunner = new ProcessRunner(maxOutputChars);
    }

    @Override
    public CommandResult run(String command, Duration timeout) {
        return this.processRunner.run(List.of("bash", "-lc", command), this.workDir, Map.of("CI", "true"), timeout);
    }

    @Override
    public String describe() {
        return "Shell local (bash) no diretório " + this.workDir;
    }
}
