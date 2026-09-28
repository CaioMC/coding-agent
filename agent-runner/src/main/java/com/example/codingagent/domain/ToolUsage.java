package com.example.codingagent.domain;

import java.util.ArrayList;
import java.util.List;

public class ToolUsage {

    private final int maxCalls;
    private final List<String> commandsRun = new ArrayList<>();
    private int calls;

    public ToolUsage(int maxCalls) {
        this.maxCalls = maxCalls;
    }

    public synchronized boolean tryStartCall() {
        this.calls++;
        return this.calls <= this.maxCalls;
    }

    public synchronized void recordCommand(String command) {
        this.commandsRun.add(command);
    }

    public synchronized int calls() {
        return this.calls;
    }

    public int maxCalls() {
        return this.maxCalls;
    }

    public synchronized List<String> commandsRun() {
        return List.copyOf(this.commandsRun);
    }
}
