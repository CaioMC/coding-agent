package com.example.codingagent.application;

import com.example.codingagent.application.port.CommandExecutor;
import com.example.codingagent.application.port.ExecutionJournal;
import com.example.codingagent.application.port.JournalEvent;
import com.example.codingagent.application.port.Workspace;
import com.example.codingagent.domain.CommandResult;
import com.example.codingagent.domain.Verification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public class VerificationRunner {

    private static final Logger log = LoggerFactory.getLogger(VerificationRunner.class);

    private static final String VERIFY_SCRIPT = ".agent/verify.sh";
    private static final int MAX_OUTPUT_TAIL_CHARS = 3000;

    private final Workspace workspace;
    private final CommandExecutor executor;
    private final ExecutionJournal journal;
    private final String configuredCommand;
    private final Duration timeout;

    public VerificationRunner(Workspace workspace, CommandExecutor executor, ExecutionJournal journal,
                              String configuredCommand, Duration timeout) {
        this.workspace = workspace;
        this.executor = executor;
        this.journal = journal;
        this.configuredCommand = configuredCommand;
        this.timeout = timeout;
    }

    public Verification run() {
        return this.resolveCommand()
                .map(this::execute)
                .orElseGet(() -> Verification.skipped("nenhum comando de verificação configurado"));
    }

    private Optional<String> resolveCommand() {
        if (this.configuredCommand != null && !this.configuredCommand.isBlank()) {
            return Optional.of(this.configuredCommand);
        }
        if (this.workspace.isFile(VERIFY_SCRIPT)) {
            return Optional.of("bash " + VERIFY_SCRIPT);
        }
        return Optional.empty();
    }

    private Verification execute(String command) {
        log.info("Rodando verificação: {}", command);
        CommandResult result = this.executor.run(command, this.timeout);
        this.journal.record(JournalEvent.VERIFICATION, Map.of(
                "command", command,
                "exitCode", result.exitCode(),
                "output", result.output()));
        return Verification.executed(command, result.exitCode(), result.outputTail(MAX_OUTPUT_TAIL_CHARS));
    }
}
