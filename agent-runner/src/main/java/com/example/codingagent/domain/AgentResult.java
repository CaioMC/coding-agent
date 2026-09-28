package com.example.codingagent.domain;

import java.util.List;

public record AgentResult(
        String requestId,
        Integer issueNumber,
        AgentStatus status,
        String summary,
        int iterations,
        int toolCalls,
        List<String> commandsRun,
        Verification verification,
        String model) {

    public static AgentResult of(AgentTask task, AgentOutcome outcome, LoopOutcome loop,
                                 Verification verification, ToolUsage usage, String model) {
        return new AgentResult(
                task.requestId(),
                task.issueNumber(),
                outcome.status(),
                outcome.summary(),
                loop.iterations(),
                usage.calls(),
                usage.commandsRun(),
                verification,
                model);
    }
}
