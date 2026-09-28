package com.example.codingagent.infrastructure.ai;

import com.example.codingagent.application.port.CommandExecutor;
import com.example.codingagent.application.port.ExecutionJournal;
import com.example.codingagent.application.port.JournalEvent;
import com.example.codingagent.domain.CommandPolicy;
import com.example.codingagent.domain.CommandResult;
import com.example.codingagent.domain.CommandTimeouts;
import com.example.codingagent.domain.LoopEnd;
import com.example.codingagent.domain.LoopOutcome;
import com.example.codingagent.domain.ToolUsage;
import com.example.codingagent.infrastructure.workspace.LocalWorkspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiAgentLoopTest {

    private static final int MAX_ITERATIONS = 5;

    @TempDir
    Path dir;

    private final List<String> executedCommands = new ArrayList<>();
    private final List<JournalEvent> journalEvents = new ArrayList<>();
    private ToolUsage usage;
    private CodingTools tools;

    @BeforeEach
    void setUp() {
        CommandExecutor executor = new CommandExecutor() {
            @Override
            public CommandResult run(String command, Duration timeout) {
                SpringAiAgentLoopTest.this.executedCommands.add(command);
                return new CommandResult(0, "BUILD SUCCESS", false, 10);
            }

            @Override
            public String describe() {
                return "executor de teste";
            }
        };
        ExecutionJournal journal = (event, data) -> this.journalEvents.add(event);
        CommandTimeouts timeouts = new CommandTimeouts(Duration.ofSeconds(300), Duration.ofSeconds(1200));
        this.usage = new ToolUsage(10);
        this.tools = new CodingTools(new LocalWorkspace(this.dir), executor, new CommandPolicy(), timeouts, this.usage, journal);
    }

    @Test
    void terminaQuandoOModeloChamaFinish() {
        LoopOutcome outcome = this.loopWith(
                this.toolCall("run_command", "{\"command\":\"mvn -o -q test\"}"),
                this.toolCall("finish", "{\"status\":\"done\",\"summary\":\"pronto\"}")
        ).run("sistema", "tarefa");

        assertThat(outcome.end()).isEqualTo(LoopEnd.FINISHED);
        assertThat(outcome.iterations()).isEqualTo(2);
        assertThat(outcome.finishSignal().done()).isTrue();
        assertThat(this.executedCommands).containsExactly("mvn -o -q test");
    }

    @Test
    void comandoBloqueadoNaoChegaAoExecutor() {
        this.loopWith(
                this.toolCall("run_command", "{\"command\":\"git push origin main\"}"),
                this.toolCall("finish", "{\"status\":\"done\",\"summary\":\"pronto\"}")
        ).run("sistema", "tarefa");

        assertThat(this.executedCommands).isEmpty();
        assertThat(this.usage.calls()).isEqualTo(1);
    }

    @Test
    void desisteDepoisDeDuasRespostasSemFerramentas() {
        LoopOutcome outcome = this.loopWith(
                this.text("vou pensar"),
                this.text("ainda pensando"),
                this.text("acho que terminei")
        ).run("sistema", "tarefa");

        assertThat(outcome.end()).isEqualTo(LoopEnd.NO_TOOL_CALLS);
        assertThat(outcome.iterations()).isEqualTo(3);
    }

    @Test
    void lembreteSemFerramentaRetomaOTrabalho() {
        LoopOutcome outcome = this.loopWith(
                this.text("vou pensar"),
                this.toolCall("finish", "{\"status\":\"done\",\"summary\":\"pronto\"}")
        ).run("sistema", "tarefa");

        assertThat(outcome.end()).isEqualTo(LoopEnd.FINISHED);
    }

    @Test
    void paraNoLimiteDeIteracoes() {
        ChatResponse[] responses = new ChatResponse[MAX_ITERATIONS];
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            responses[i] = this.toolCall("list_files", "{\"path\":\".\"}");
        }

        LoopOutcome outcome = this.loopWith(responses).run("sistema", "tarefa");

        assertThat(outcome.end()).isEqualTo(LoopEnd.ITERATION_LIMIT);
        assertThat(outcome.iterations()).isEqualTo(MAX_ITERATIONS);
    }

    @Test
    void falhaDoModeloViraErro() {
        ChatModel failingModel = prompt -> {
            throw new IllegalStateException("Ollama fora do ar");
        };

        LoopOutcome outcome = new SpringAiAgentLoop(failingModel, this.tools, (event, data) -> this.journalEvents.add(event), MAX_ITERATIONS)
                .run("sistema", "tarefa");

        assertThat(outcome.end()).isEqualTo(LoopEnd.ERROR);
        assertThat(outcome.errorMessage()).isEqualTo("Ollama fora do ar");
        assertThat(this.journalEvents).contains(JournalEvent.ERROR);
    }

    private SpringAiAgentLoop loopWith(ChatResponse... responses) {
        Deque<ChatResponse> script = new ArrayDeque<>(List.of(responses));
        ChatModel scriptedModel = (Prompt prompt) -> script.removeFirst();
        return new SpringAiAgentLoop(scriptedModel, this.tools, (event, data) -> this.journalEvents.add(event), MAX_ITERATIONS);
    }

    private ChatResponse toolCall(String name, String arguments) {
        AssistantMessage message = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-" + name, "function", name, arguments)))
                .properties(Map.of())
                .build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    private ChatResponse text(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}
