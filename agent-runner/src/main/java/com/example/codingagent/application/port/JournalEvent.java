package com.example.codingagent.application.port;

public enum JournalEvent {
    MODEL_TURN("model_turn"),
    TOOL_CALL("tool_call"),
    COMMAND_RESULT("command_result"),
    TOOL_BUDGET_EXCEEDED("tool_budget_exceeded"),
    FINISH("finish"),
    VERIFICATION("verification"),
    ERROR("error");

    private final String type;

    JournalEvent(String type) {
        this.type = type;
    }

    public String type() {
        return this.type;
    }
}
