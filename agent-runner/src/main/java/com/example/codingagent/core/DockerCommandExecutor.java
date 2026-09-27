package com.example.codingagent.core;

import java.time.Duration;
import java.util.List;

/**
 * Executa cada comando dentro do container do projeto, preparado pelo workflow
 * (script prepare-sandbox.sh). O repositório está montado em /workspace, então as
 * edições feitas pelo agente no host aparecem no container e vice-versa.
 *
 * O container roda sem rede depois do preparo; o agent-runner, que fala com o modelo,
 * fica fora dele. Assim o código executado não alcança nem o modelo nem a internet.
 */
public class DockerCommandExecutor implements CommandExecutor {

    private final String containerName;
    private final String containerWorkdir;
    private final int maxOutputChars;

    public DockerCommandExecutor(String containerName, String containerWorkdir, int maxOutputChars) {
        this.containerName = containerName;
        this.containerWorkdir = containerWorkdir;
        this.maxOutputChars = maxOutputChars;
    }

    @Override
    public CommandResult run(String command, Duration timeout) {
        long seconds = Math.max(1, timeout.toSeconds());
        // O "timeout" roda DENTRO do container: matar só o cliente docker não interromperia o processo lá dentro.
        List<String> argv = List.of(
                "docker", "exec",
                "-w", containerWorkdir,
                "-e", "CI=true",
                containerName,
                "timeout", "-k", "10", String.valueOf(seconds),
                "bash", "-lc", command);
        // Folga de 20 s para o próprio timeout do container agir primeiro.
        return ProcessRunner.run(argv, null, null, timeout.plusSeconds(20), maxOutputChars);
    }

    @Override
    public String describe() {
        return "Container Docker '" + containerName + "' com o repositório em " + containerWorkdir
                + " (sem acesso à rede; dependências já instaladas pelo preparo)";
    }
}
