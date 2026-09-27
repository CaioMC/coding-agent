package com.empresa.copilotlogistico.agent;

import com.empresa.copilotlogistico.agent.config.AgentProperties;
import com.empresa.copilotlogistico.agent.core.AgentResult;
import com.empresa.copilotlogistico.agent.core.CommandExecutor;
import com.empresa.copilotlogistico.agent.core.CommandPolicy;
import com.empresa.copilotlogistico.agent.core.DockerCommandExecutor;
import com.empresa.copilotlogistico.agent.core.ExecutionJournal;
import com.empresa.copilotlogistico.agent.core.LocalCommandExecutor;
import com.empresa.copilotlogistico.agent.core.Workspace;
import com.empresa.copilotlogistico.agent.loop.CodingAgent;
import com.empresa.copilotlogistico.agent.task.AgentTask;
import com.empresa.copilotlogistico.agent.task.TaskLoader;
import com.empresa.copilotlogistico.agent.tools.CodingTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;

/**
 * Ponto de entrada executado pela pipeline:
 *
 *   java -jar agent-runner.jar --agent.workspace=... --agent.task-file=... --agent.executor=docker
 *
 * Lê a tarefa, roda o laço do agente e grava result.json e journal.jsonl.
 * O código de saída é 0 sempre que o agente rodou (mesmo sem concluir), para que a pipeline
 * publique o resultado; só falhas de infraestrutura (ex.: modelo fora do ar) retornam 1.
 */
@SpringBootApplication
@EnableConfigurationProperties(AgentProperties.class)
public class AgentRunnerApplication {

    private static final Logger log = LoggerFactory.getLogger(AgentRunnerApplication.class);

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(AgentRunnerApplication.class, args)));
    }

    @Bean
    ExitHolder exitHolder() {
        return new ExitHolder();
    }

    @Bean
    CommandLineRunner runAgent(ChatModel chatModel, AgentProperties props, ExitHolder exit) {
        return args -> {
            Workspace workspace = new Workspace(Path.of(props.getWorkspace()));
            Path outputDir = Path.of(props.getOutputDir());
            AgentTask task = TaskLoader.load(Path.of(props.getTaskFile()));

            CommandExecutor executor = "local".equalsIgnoreCase(props.getExecutor())
                    ? new LocalCommandExecutor(workspace, props.getMaxOutputChars())
                    : new DockerCommandExecutor(props.getContainer(), props.getContainerWorkdir(), props.getMaxOutputChars());

            ExecutionJournal journal = new ExecutionJournal(outputDir.resolve("journal.jsonl"), props.getMaxToolCalls());
            CodingTools tools = new CodingTools(workspace, executor, new CommandPolicy(), journal,
                    props.getCommandTimeoutSeconds(), props.getMaxCommandTimeoutSeconds());

            log.info("Tarefa {} ({}) no workspace {} usando {}", task.issueKey(), task.requestId(),
                    workspace.root(), executor.describe());

            AgentResult result = new CodingAgent(chatModel, workspace, executor, journal, tools, props).run(task);
            result.writeTo(outputDir.resolve("result.json"));

            log.info("Status: {} | iterações: {} | ferramentas: {}", result.status(), result.iterations(), result.toolCalls());
            log.info("Resumo: {}", result.summary());
            exit.code = "ERROR".equals(result.status()) ? 1 : 0;
        };
    }

    static class ExitHolder implements ExitCodeGenerator {
        int code;

        @Override
        public int getExitCode() {
            return code;
        }
    }
}
