package com.example.codingagent.infrastructure.executor;

import com.example.codingagent.application.port.CommandExecutor;
import com.example.codingagent.domain.CommandResult;

import java.time.Duration;
import java.util.List;

public class DockerCommandExecutor implements CommandExecutor {

    private static final Duration CONTAINER_TIMEOUT_GRACE = Duration.ofSeconds(20);

    private final String containerName;
    private final String containerWorkdir;
    private final ProcessRunner processRunner;

    public DockerCommandExecutor(String containerName, String containerWorkdir, int maxOutputChars) {
        this.containerName = containerName;
        this.containerWorkdir = containerWorkdir;
        this.processRunner = new ProcessRunner(maxOutputChars);
    }

    @Override
    public CommandResult run(String command, Duration timeout) {
        // O timeout precisa rodar dentro do container: matar o cliente docker não mata o processo lá dentro.
        long seconds = Math.max(1, timeout.toSeconds());
        List<String> argv = List.of(
                "docker", "exec",
                "-w", this.containerWorkdir,
                "-e", "CI=true",
                this.containerName,
                "timeout", "-k", "10", String.valueOf(seconds),
                "bash", "-lc", command);
        return this.processRunner.run(argv, null, null, timeout.plus(CONTAINER_TIMEOUT_GRACE));
    }

    @Override
    public String describe() {
        return "Container Docker '" + this.containerName + "' com o repositório em " + this.containerWorkdir
                + " (sem acesso à rede; dependências já instaladas pelo preparo)";
    }
}
