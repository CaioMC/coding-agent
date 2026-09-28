package com.example.codingagent.domain;

public record LoopOutcome(LoopEnd end, int iterations, FinishSignal finishSignal, String errorMessage) {

    public static LoopOutcome finished(int iterations, FinishSignal finishSignal) {
        return new LoopOutcome(LoopEnd.FINISHED, iterations, finishSignal, null);
    }

    public static LoopOutcome iterationLimitReached(int iterations) {
        return new LoopOutcome(LoopEnd.ITERATION_LIMIT, iterations, null, null);
    }

    public static LoopOutcome stoppedWithoutTools(int iterations) {
        return new LoopOutcome(LoopEnd.NO_TOOL_CALLS, iterations, null, null);
    }

    public static LoopOutcome failed(int iterations, String errorMessage) {
        return new LoopOutcome(LoopEnd.ERROR, iterations, null, errorMessage);
    }
}
