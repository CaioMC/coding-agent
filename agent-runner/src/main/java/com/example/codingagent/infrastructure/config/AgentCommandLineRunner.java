package com.example.codingagent.infrastructure.config;

import com.example.codingagent.application.RunCodingAgentUseCase;
import com.example.codingagent.application.port.CommandExecutor;
import com.example.codingagent.application.port.Workspace;
import com.example.codingagent.domain.AgentResult;
import com.example.codingagent.domain.AgentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;

public class AgentCommandLineRunner implements CommandLineRunner, ExitCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(AgentCommandLineRunner.class);

    private final RunCodingAgentUseCase useCase;
    private final Workspace workspace;
    private final CommandExecutor executor;
    private int exitCode;

    public AgentCommandLineRunner(RunCodingAgentUseCase useCase, Workspace workspace, CommandExecutor executor) {
        this.useCase = useCase;
        this.workspace = workspace;
        this.executor = executor;
    }

    @Override
    public void run(String... args) {
        log.info("Workspace {} usando {}", this.workspace.root(), this.executor.describe());
        AgentResult result = this.useCase.execute();
        this.exitCode = result.status() == AgentStatus.ERROR ? 1 : 0;
    }

    @Override
    public int getExitCode() {
        return this.exitCode;
    }
}
