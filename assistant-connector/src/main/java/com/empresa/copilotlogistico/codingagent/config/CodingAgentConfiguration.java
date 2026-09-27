package com.empresa.copilotlogistico.codingagent.config;

import com.empresa.copilotlogistico.codingagent.CodingAgentTools;
import com.empresa.copilotlogistico.codingagent.CodingTaskService;
import com.empresa.copilotlogistico.codingagent.pipeline.AzurePipelinesClient;
import com.empresa.copilotlogistico.codingagent.pipeline.CodingPipelineClient;
import com.empresa.copilotlogistico.codingagent.pipeline.GitHubActionsClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Beans do conector. Com esta classe no classpath do assistente, basta injetar
 * {@link CodingAgentTools} e registrá-lo no ChatClient.
 */
@Configuration
@EnableConfigurationProperties(CodingAgentProperties.class)
public class CodingAgentConfiguration {

    @Bean
    CodingPipelineClient codingPipelineClient(CodingAgentProperties props) {
        if ("github".equalsIgnoreCase(props.getProvider())) {
            CodingAgentProperties.Github gh = props.getGithub();
            return new GitHubActionsClient(gh.getApiUrl(), gh.getAgentRepo(), gh.getWorkflowFile(), gh.getRef(),
                    props.getArtifactName(), gh::getToken);
        }
        CodingAgentProperties.Azure az = props.getAzure();
        return new AzurePipelinesClient(az.getOrgUrl(), az.getProject(), az.getPipelineId(), az.getPipelineRef(),
                props.getArtifactName(), AzurePipelinesClient.pat(az.getPat()));
    }

    @Bean
    CodingTaskService codingTaskService(CodingPipelineClient client, CodingAgentProperties props) {
        return new CodingTaskService(client, props.getDefaultBaseBranch());
    }

    @Bean
    CodingAgentTools codingAgentTools(CodingTaskService service) {
        return new CodingAgentTools(service);
    }
}
