package com.example.codingagent.application.port;

import com.example.codingagent.domain.CommandResult;

import java.time.Duration;

public interface CommandExecutor {

    CommandResult run(String command, Duration timeout);

    String describe();
}
