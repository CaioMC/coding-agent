package com.example.codingagent.core;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Executa comandos direto no sistema onde o agent-runner está.
 * Útil para desenvolvimento local e testes. No workflow, prefira {@link DockerCommandExecutor}.
 */
public class LocalCommandExecutor implements CommandExecutor {

    private final Workspace workspace;
    private final int maxOutputChars;

    public LocalCommandExecutor(Workspace workspace, int maxOutputChars) {
        this.workspace = workspace;
        this.maxOutputChars = maxOutputChars;
    }

    @Override
    public CommandResult run(String command, Duration timeout) {
        return ProcessRunner.run(List.of("bash", "-lc", command), workspace.root(),
                Map.of("CI", "true"), timeout, maxOutputChars);
    }

    @Override
    public String describe() {
        return "Shell local (bash) no diretório " + workspace.root();
    }
}
