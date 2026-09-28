package com.example.codingagent.domain;

public record FinishSignal(boolean done, String summary) {

    private static final String DONE = "done";
    private static final String INCOMPLETE = "incomplete";

    public static FinishSignal from(String status, String summary) {
        boolean done = status != null && DONE.equalsIgnoreCase(status.trim());
        return new FinishSignal(done, summary == null ? "" : summary);
    }

    public String statusLabel() {
        return this.done ? DONE : INCOMPLETE;
    }
}
