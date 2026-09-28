package com.example.codingagent.infrastructure.ai;

import com.example.codingagent.application.PromptFactory;
import com.example.codingagent.application.port.AgentLoop;
import com.example.codingagent.application.port.ExecutionJournal;
import com.example.codingagent.application.port.JournalEvent;
import com.example.codingagent.domain.LoopOutcome;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SpringAiAgentLoop implements AgentLoop {

    private static final Logger log = LoggerFactory.getLogger(SpringAiAgentLoop.class);
    private static final int MAX_NUDGES = 2;

    private final ChatModel chatModel;
    private final CodingTools tools;
    private final ExecutionJournal journal;
    private final int maxIterations;
    private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

    public SpringAiAgentLoop(ChatModel chatModel, CodingTools tools, ExecutionJournal journal, int maxIterations) {
        this.chatModel = chatModel;
        this.tools = tools;
        this.journal = journal;
        this.maxIterations = maxIterations;
    }

    @Override
    public LoopOutcome run(String systemPrompt, String taskPrompt) {
        ToolCallingChatOptions options = this.toolCallingOptions();
        Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(taskPrompt)), options);
        int iteration = 0;
        int nudges = 0;

        try {
            while (iteration < this.maxIterations) {
                iteration++;
                log.info("Iteração {} de {}", iteration, this.maxIterations);

                ChatResponse response = this.chatModel.call(prompt);
                this.recordModelTurn(iteration, response);

                if (response.hasToolCalls()) {
                    ToolExecutionResult executed = this.toolCallingManager.executeToolCalls(prompt, response);
                    if (this.tools.finishSignal().isPresent()) {
                        return LoopOutcome.finished(iteration, this.tools.finishSignal().get());
                    }
                    prompt = new Prompt(executed.conversationHistory(), options);
                } else if (nudges < MAX_NUDGES) {
                    nudges++;
                    prompt = this.withNudge(prompt, response, options);
                } else {
                    return LoopOutcome.stoppedWithoutTools(iteration);
                }
            }
            return LoopOutcome.iterationLimitReached(iteration);
        } catch (RuntimeException e) {
            log.error("Falha no laço do agente", e);
            this.journal.record(JournalEvent.ERROR, Map.of("message", String.valueOf(e.getMessage())));
            return LoopOutcome.failed(iteration, e.getMessage());
        }
    }

    private ToolCallingChatOptions toolCallingOptions() {
        return ToolCallingChatOptions.builder()
                .toolCallbacks(ToolCallbacks.from(this.tools))
                .internalToolExecutionEnabled(false)
                .build();
    }

    private Prompt withNudge(Prompt prompt, ChatResponse response, ToolCallingChatOptions options) {
        List<Message> messages = new ArrayList<>(prompt.getInstructions());
        AssistantMessage output = this.outputOf(response);
        if (output != null) {
            messages.add(output);
        }
        messages.add(new UserMessage(PromptFactory.NUDGE_NO_TOOL_CALL));
        return new Prompt(messages, options);
    }

    private void recordModelTurn(int iteration, ChatResponse response) {
        AssistantMessage output = this.outputOf(response);
        this.journal.record(JournalEvent.MODEL_TURN, Map.of(
                "iteration", iteration,
                "text", output == null || output.getText() == null ? "" : output.getText(),
                "toolCalls", output == null ? 0 : output.getToolCalls().size()));
    }

    private AssistantMessage outputOf(ChatResponse response) {
        return response.getResult() == null ? null : response.getResult().getOutput();
    }
}
