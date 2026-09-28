package com.example.codingagent.domain;

public record Verification(String command, boolean executed, Integer exitCode, String outputTail) {

    public static Verification skipped(String reason) {
        return new Verification(reason, false, null, null);
    }

    public static Verification executed(String command, int exitCode, String outputTail) {
        return new Verification(command, true, exitCode, outputTail);
    }

    public boolean failed() {
        return this.executed && this.exitCode != null && this.exitCode != 0;
    }
}
