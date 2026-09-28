package com.example.codingagent.application.port;

import com.example.codingagent.domain.LoopOutcome;

public interface AgentLoop {

    LoopOutcome run(String systemPrompt, String taskPrompt);
}
