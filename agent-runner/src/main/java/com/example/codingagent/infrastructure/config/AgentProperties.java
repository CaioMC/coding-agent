package com.example.codingagent.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix = "agent")
public record AgentProperties(
        @DefaultValue(".") String workspace,
        @DefaultValue("task.json") String taskFile,
        @DefaultValue("agent-output") String outputDir,
        @DefaultValue("docker") ExecutorType executor,
        @DefaultValue("agent-sandbox") String container,
        @DefaultValue("/workspace") String containerWorkdir,
        @DefaultValue("30") int maxIterations,
        @DefaultValue("90") int maxToolCalls,
        @DefaultValue("300") int commandTimeoutSeconds,
        @DefaultValue("1200") int maxCommandTimeoutSeconds,
        @DefaultValue("6000") int maxOutputChars,
        String verifyCommand,
        @DefaultValue("1200") int verifyTimeoutSeconds,
        String modelLabel) {

    public Path workspacePath() {
        return Path.of(this.workspace);
    }

    public Path taskFilePath() {
        return Path.of(this.taskFile);
    }

    public Path resultFile() {
        return Path.of(this.outputDir).resolve("result.json");
    }

    public Path journalFile() {
        return Path.of(this.outputDir).resolve("journal.jsonl");
    }

    public Duration commandTimeout() {
        return Duration.ofSeconds(this.commandTimeoutSeconds);
    }

    public Duration maxCommandTimeout() {
        return Duration.ofSeconds(this.maxCommandTimeoutSeconds);
    }

    public Duration verifyTimeout() {
        return Duration.ofSeconds(this.verifyTimeoutSeconds);
    }
}
