package com.empresa.copilotlogistico.agent.loop;

import com.empresa.copilotlogistico.agent.config.AgentProperties;
import com.empresa.copilotlogistico.agent.core.AgentResult;
import com.empresa.copilotlogistico.agent.core.CommandExecutor;
import com.empresa.copilotlogistico.agent.core.CommandResult;
import com.empresa.copilotlogistico.agent.core.ExecutionJournal;
import com.empresa.copilotlogistico.agent.core.Workspace;
import com.empresa.copilotlogistico.agent.task.AgentTask;
import com.empresa.copilotlogistico.agent.tools.CodingTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * O laço do agente: pensar, agir, observar.
 *
 * Usamos a execução de ferramentas controlada pelo usuário do Spring AI
 * (internalToolExecutionEnabled = false). Assim NÓS rodamos o laço, e não o framework:
 * dá para contar iterações, impor limite, registrar cada passo e decidir quando parar.
 *
 * A cada volta:
 *   1. o modelo recebe o histórico e responde pedindo ferramentas (ex.: run_command "mvn -o test");
 *   2. o ToolCallingManager executa as ferramentas e anexa o resultado ao histórico;
 *   3. o modelo "observa" a saída (exit_code, erros de compilação, testes) na próxima chamada.
 */
public class CodingAgent {

    private static final Logger log = LoggerFactory.getLogger(CodingAgent.class);
    private static final int MAX_NUDGES = 2;

    private final ChatModel chatModel;
    private final Workspace workspace;
    private final CommandExecutor executor;
    private final ExecutionJournal journal;
    private final CodingTools tools;
    private final AgentProperties props;

    public CodingAgent(ChatModel chatModel, Workspace workspace, CommandExecutor executor,
                       ExecutionJournal journal, CodingTools tools, AgentProperties props) {
        this.chatModel = chatModel;
        this.workspace = workspace;
        this.executor = executor;
        this.journal = journal;
        this.tools = tools;
        this.props = props;
    }

    public AgentResult run(AgentTask task) {
        ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .toolCallbacks(ToolCallbacks.from(tools))
                .internalToolExecutionEnabled(false)
                .build();

        String system = Prompts.system(executor.describe(), readAgentsMd(), props.getMaxIterations());
        List<Message> history = new ArrayList<>(List.of(new SystemMessage(system), new UserMessage(task.toPrompt())));
        Prompt prompt = new Prompt(history, options);

        int iteration = 0;
        int nudges = 0;
        String endReason;
        try {
            while (true) {
                iteration++;
                if (iteration > props.getMaxIterations()) {
                    endReason = "budget";
                    break;
                }
                log.info("Iteração {} de {}", iteration, props.getMaxIterations());
                ChatResponse response = chatModel.call(prompt);
                AssistantMessage output = response.getResult() == null ? null : response.getResult().getOutput();
                journal.log("model_turn", Map.of("iteration", iteration,
                        "text", output == null || output.getText() == null ? "" : output.getText(),
                        "toolCalls", output == null ? 0 : output.getToolCalls().size()));

                if (response.hasToolCalls()) {
                    ToolExecutionResult executed = toolCallingManager.executeToolCalls(prompt, response);
                    if (tools.finishSignal() != null || executed.returnDirect()) {
                        endReason = "finish";
                        break;
                    }
                    prompt = new Prompt(executed.conversationHistory(), options);
                    continue;
                }

                // O modelo respondeu só com texto. Modelos menores fazem isso com frequência:
                // lembramos de usar as ferramentas algumas vezes antes de desistir.
                if (nudges >= MAX_NUDGES) {
                    endReason = "no_tool_calls";
                    break;
                }
                nudges++;
                List<Message> next = new ArrayList<>(prompt.getInstructions());
                if (output != null) {
                    next.add(output);
                }
                next.add(new UserMessage(Prompts.NUDGE_NO_TOOL_CALL));
                prompt = new Prompt(next, options);
            }
        } catch (RuntimeException e) {
            log.error("Falha no laço do agente", e);
            journal.log("error", Map.of("message", String.valueOf(e.getMessage())));
            return buildResult(task, "ERROR", "Falha ao executar o agente: " + e.getMessage(),
                    iteration, runVerification());
        }

        AgentResult.Verification verification = runVerification();
        CodingTools.FinishSignal finish = tools.finishSignal();
        String status;
        String summary;
        if (finish == null) {
            status = "budget".equals(endReason) ? "BUDGET_EXCEEDED" : "INCOMPLETE";
            summary = "budget".equals(endReason)
                    ? "O agente atingiu o limite de " + props.getMaxIterations() + " iterações sem concluir."
                    : "O agente parou de usar ferramentas sem chamar finish.";
        } else if (!"done".equals(finish.status())) {
            status = "INCOMPLETE";
            summary = finish.summary();
        } else if (verification.executed() && verification.exitCode() != null && verification.exitCode() != 0) {
            status = "VERIFICATION_FAILED";
            summary = finish.summary();
        } else {
            status = "COMPLETED";
            summary = finish.summary();
        }
        return buildResult(task, status, summary, Math.min(iteration, props.getMaxIterations()), verification);
    }

    /**
     * Verificação independente do modelo: roda .agent/verify.sh (ou o comando configurado)
     * depois do laço. O resultado vai para o PR, então o revisor não depende do que o modelo disse.
     */
    private AgentResult.Verification runVerification() {
        String command = props.getVerifyCommand();
        if ((command == null || command.isBlank()) && Files.isRegularFile(workspace.root().resolve(".agent/verify.sh"))) {
            command = "bash .agent/verify.sh";
        }
        if (command == null || command.isBlank()) {
            return AgentResult.Verification.skipped("nenhum comando de verificação configurado");
        }
        log.info("Rodando verificação: {}", command);
        CommandResult r = executor.run(command, Duration.ofSeconds(props.getVerifyTimeoutSeconds()));
        journal.log("verification", Map.of("command", command, "exitCode", r.exitCode(), "output", r.output()));
        String tail = r.output() == null ? "" : r.output();
        if (tail.length() > 3000) {
            tail = tail.substring(tail.length() - 3000);
        }
        return new AgentResult.Verification(command, true, r.exitCode(), tail);
    }

    private AgentResult buildResult(AgentTask task, String status, String summary, int iterations,
                                    AgentResult.Verification verification) {
        return new AgentResult(task.requestId(), task.issueKey(), status, summary, iterations,
                journal.toolCalls(), journal.commandsRun(), verification, props.getModelLabel());
    }

    private String readAgentsMd() {
        Path file = workspace.root().resolve("AGENTS.md");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            String content = Files.readString(file);
            return content.length() > 8000 ? content.substring(0, 8000) + "\n...[truncado]" : content;
        } catch (IOException e) {
            return null;
        }
    }
}
