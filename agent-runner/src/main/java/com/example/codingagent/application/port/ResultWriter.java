package com.example.codingagent.application.port;

import com.example.codingagent.domain.AgentResult;

public interface ResultWriter {

    void write(AgentResult result);
}
