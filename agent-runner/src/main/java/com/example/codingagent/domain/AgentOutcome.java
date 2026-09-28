package com.example.codingagent.domain;

public record AgentOutcome(AgentStatus status, String summary) {

    public static AgentOutcome decide(LoopOutcome loop, Verification verification, int maxIterations) {
        return switch (loop.end()) {
            case ERROR -> new AgentOutcome(AgentStatus.ERROR,
                    "Falha ao executar o agente: " + loop.errorMessage());
            case ITERATION_LIMIT -> new AgentOutcome(AgentStatus.BUDGET_EXCEEDED,
                    "O agente atingiu o limite de " + maxIterations + " iterações sem concluir.");
            case NO_TOOL_CALLS -> new AgentOutcome(AgentStatus.INCOMPLETE,
                    "O agente parou de usar ferramentas sem chamar finish.");
            case FINISHED -> fromFinishSignal(loop.finishSignal(), verification);
        };
    }

    private static AgentOutcome fromFinishSignal(FinishSignal signal, Verification verification) {
        if (!signal.done()) {
            return new AgentOutcome(AgentStatus.INCOMPLETE, signal.summary());
        }
        if (verification.failed()) {
            return new AgentOutcome(AgentStatus.VERIFICATION_FAILED, signal.summary());
        }
        return new AgentOutcome(AgentStatus.COMPLETED, signal.summary());
    }
}
