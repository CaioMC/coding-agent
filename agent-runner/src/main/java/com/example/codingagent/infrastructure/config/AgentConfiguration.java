package com.example.codingagent.infrastructure.config;

import com.example.codingagent.application.PromptFactory;
import com.example.codingagent.application.RunCodingAgentUseCase;
import com.example.codingagent.application.VerificationRunner;
import com.example.codingagent.application.port.AgentLoop;
import com.example.codingagent.application.port.CommandExecutor;
import com.example.codingagent.application.port.ExecutionJournal;
import com.example.codingagent.application.port.ResultWriter;
import com.example.codingagent.application.port.TaskReader;
import com.example.codingagent.application.port.Workspace;
import com.example.codingagent.domain.CommandPolicy;
import com.example.codingagent.domain.CommandTimeouts;
import com.example.codingagent.domain.ToolUsage;
import com.example.codingagent.infrastructure.ai.CodingTools;
import com.example.codingagent.infrastructure.ai.SpringAiAgentLoop;
import com.example.codingagent.infrastructure.executor.DockerCommandExecutor;
import com.example.codingagent.infrastructure.executor.LocalCommandExecutor;
import com.example.codingagent.infrastructure.persistence.JsonResultWriter;
import com.example.codingagent.infrastructure.persistence.JsonTaskReader;
import com.example.codingagent.infrastructure.persistence.JsonlExecutionJournal;
import com.example.codingagent.infrastructure.workspace.LocalWorkspace;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfiguration {

    @Bean
    Workspace workspace(AgentProperties properties) {
        return new LocalWorkspace(properties.workspacePath());
    }

    @Bean
    CommandExecutor commandExecutor(AgentProperties properties, Workspace workspace) {
        return switch (properties.executor()) {
            case DOCKER -> new DockerCommandExecutor(
                    properties.container(), properties.containerWorkdir(), properties.maxOutputChars());
            case LOCAL -> new LocalCommandExecutor(workspace.root(), properties.maxOutputChars());
        };
    }

    @Bean
    ExecutionJournal executionJournal(AgentProperties properties) {
        return new JsonlExecutionJournal(properties.journalFile());
    }

    @Bean
    ToolUsage toolUsage(AgentProperties properties) {
        return new ToolUsage(properties.maxToolCalls());
    }

    @Bean
    CodingTools codingTools(AgentProperties properties, Workspace workspace, CommandExecutor executor,
                            ToolUsage toolUsage, ExecutionJournal journal) {
        CommandTimeouts timeouts = new CommandTimeouts(properties.commandTimeout(), properties.maxCommandTimeout());
        return new CodingTools(workspace, executor, new CommandPolicy(), timeouts, toolUsage, journal);
    }

    @Bean
    AgentLoop agentLoop(ChatModel chatModel, CodingTools tools, ExecutionJournal journal, AgentProperties properties) {
        return new SpringAiAgentLoop(chatModel, tools, journal, properties.maxIterations());
    }

    @Bean
    TaskReader taskReader(AgentProperties properties) {
        return new JsonTaskReader(properties.taskFilePath());
    }

    @Bean
    ResultWriter resultWriter(AgentProperties properties) {
        return new JsonResultWriter(properties.resultFile());
    }

    @Bean
    PromptFactory promptFactory(Workspace workspace, CommandExecutor executor, AgentProperties properties) {
        return new PromptFactory(workspace, executor, properties.maxIterations());
    }

    @Bean
    VerificationRunner verificationRunner(Workspace workspace, CommandExecutor executor, ExecutionJournal journal,
                                          AgentProperties properties) {
        return new VerificationRunner(workspace, executor, journal, properties.verifyCommand(), properties.verifyTimeout());
    }

    @Bean
    RunCodingAgentUseCase runCodingAgentUseCase(TaskReader taskReader, PromptFactory promptFactory, AgentLoop agentLoop,
                                                VerificationRunner verificationRunner, ResultWriter resultWriter,
                                                ToolUsage toolUsage, AgentProperties properties) {
        return new RunCodingAgentUseCase(taskReader, promptFactory, agentLoop, verificationRunner, resultWriter,
                toolUsage, properties.maxIterations(), properties.modelLabel());
    }

    @Bean
    AgentCommandLineRunner agentCommandLineRunner(RunCodingAgentUseCase useCase, Workspace workspace,
                                                  CommandExecutor executor) {
        return new AgentCommandLineRunner(useCase, workspace, executor);
    }
}
