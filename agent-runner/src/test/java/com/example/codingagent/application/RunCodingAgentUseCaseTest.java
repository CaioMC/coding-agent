package com.example.codingagent.application;

import com.example.codingagent.application.port.CommandExecutor;
import com.example.codingagent.domain.AgentResult;
import com.example.codingagent.domain.AgentStatus;
import com.example.codingagent.domain.AgentTask;
import com.example.codingagent.domain.CommandResult;
import com.example.codingagent.domain.FinishSignal;
import com.example.codingagent.domain.LoopOutcome;
import com.example.codingagent.domain.ToolUsage;
import com.example.codingagent.infrastructure.workspace.LocalWorkspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RunCodingAgentUseCaseTest {

    private static final AgentTask TASK = new AgentTask("req-1", 7, null, "Adicionar log", "corpo", List.of("- compila"));

    @TempDir
    Path dir;

    private final List<AgentResult> written = new ArrayList<>();
    private final List<String> systemPrompts = new ArrayList<>();
    private int verifyExitCode;
    private LocalWorkspace workspace;
    private CommandExecutor executor;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(this.dir.resolve(".agent"));
        Files.writeString(this.dir.resolve(".agent/verify.sh"), "mvn -o -q verify");
        Files.writeString(this.dir.resolve("AGENTS.md"), "Use sempre mvn -o.");
        this.workspace = new LocalWorkspace(this.dir);
        this.executor = new CommandExecutor() {
            @Override
            public CommandResult run(String command, Duration timeout) {
                return new CommandResult(RunCodingAgentUseCaseTest.this.verifyExitCode, "saida", false, 5);
            }

            @Override
            public String describe() {
                return "executor de teste";
            }
        };
    }

    @Test
    void gravaResultadoConcluidoQuandoVerificacaoPassa() {
        AgentResult result = this.useCaseReturning(LoopOutcome.finished(2, FinishSignal.from("done", "pronto"))).execute();

        assertThat(result.status()).isEqualTo(AgentStatus.COMPLETED);
        assertThat(result.verification().command()).isEqualTo("bash .agent/verify.sh");
        assertThat(result.iterations()).isEqualTo(2);
        assertThat(this.written).containsExactly(result);
    }

    @Test
    void verificacaoRodaMesmoQuandoOLacoFalha() {
        this.verifyExitCode = 1;

        AgentResult result = this.useCaseReturning(LoopOutcome.failed(1, "timeout")).execute();

        assertThat(result.status()).isEqualTo(AgentStatus.ERROR);
        assertThat(result.verification().executed()).isTrue();
    }

    @Test
    void promptDeSistemaIncluiOAgentsMd() {
        this.useCaseReturning(LoopOutcome.stoppedWithoutTools(3)).execute();

        assertThat(this.systemPrompts.get(0)).contains("Use sempre mvn -o.").contains("executor de teste");
    }

    private RunCodingAgentUseCase useCaseReturning(LoopOutcome loopOutcome) {
        VerificationRunner verificationRunner = new VerificationRunner(this.workspace, this.executor,
                (event, data) -> { }, "", Duration.ofSeconds(60));
        return new RunCodingAgentUseCase(
                () -> TASK,
                new PromptFactory(this.workspace, this.executor, 30),
                (systemPrompt, taskPrompt) -> {
                    this.systemPrompts.add(systemPrompt);
                    return loopOutcome;
                },
                verificationRunner,
                this.written::add,
                new ToolUsage(90),
                30,
                "qwen3:4b");
    }
}
