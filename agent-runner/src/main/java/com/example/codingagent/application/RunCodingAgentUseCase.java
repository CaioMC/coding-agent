package com.example.codingagent.application;

import com.example.codingagent.application.port.AgentLoop;
import com.example.codingagent.application.port.ResultWriter;
import com.example.codingagent.application.port.TaskReader;
import com.example.codingagent.domain.AgentOutcome;
import com.example.codingagent.domain.AgentResult;
import com.example.codingagent.domain.AgentTask;
import com.example.codingagent.domain.LoopOutcome;
import com.example.codingagent.domain.ToolUsage;
import com.example.codingagent.domain.Verification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RunCodingAgentUseCase {

    private static final Logger log = LoggerFactory.getLogger(RunCodingAgentUseCase.class);

    private final TaskReader taskReader;
    private final PromptFactory promptFactory;
    private final AgentLoop agentLoop;
    private final VerificationRunner verificationRunner;
    private final ResultWriter resultWriter;
    private final ToolUsage toolUsage;
    private final int maxIterations;
    private final String modelLabel;

    public RunCodingAgentUseCase(TaskReader taskReader, PromptFactory promptFactory, AgentLoop agentLoop,
                                 VerificationRunner verificationRunner, ResultWriter resultWriter,
                                 ToolUsage toolUsage, int maxIterations, String modelLabel) {
        this.taskReader = taskReader;
        this.promptFactory = promptFactory;
        this.agentLoop = agentLoop;
        this.verificationRunner = verificationRunner;
        this.resultWriter = resultWriter;
        this.toolUsage = toolUsage;
        this.maxIterations = maxIterations;
        this.modelLabel = modelLabel;
    }

    public AgentResult execute() {
        AgentTask task = this.taskReader.read();
        log.info("Issue #{} ({}): {}", task.issueNumber(), task.requestId(), task.title());

        LoopOutcome loop = this.agentLoop.run(this.promptFactory.systemPrompt(), this.promptFactory.taskPrompt(task));
        Verification verification = this.verificationRunner.run();
        AgentOutcome outcome = AgentOutcome.decide(loop, verification, this.maxIterations);

        AgentResult result = AgentResult.of(task, outcome, loop, verification, this.toolUsage, this.modelLabel);
        this.resultWriter.write(result);

        log.info("Status: {} | iterações: {} | ferramentas: {}", result.status(), result.iterations(), result.toolCalls());
        log.info("Resumo: {}", result.summary());
        return result;
    }
}
