package com.empresa.copilotlogistico.codingagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuração do conector (prefixo "coding-agent"). */
@ConfigurationProperties(prefix = "coding-agent")
public class CodingAgentProperties {

    /** "azure" ou "github". */
    private String provider = "azure";
    private String defaultBaseBranch = "main";
    private String artifactName = "agent-result";
    private final Azure azure = new Azure();
    private final Github github = new Github();

    public static class Azure {
        /** Ex.: https://dev.azure.com/minha-org */
        private String orgUrl;
        /** Projeto onde está a pipeline do agente (e os repositórios alvo). */
        private String project;
        /** Id da definição da pipeline (aparece na URL: definitionId=...). */
        private int pipelineId;
        /** Branch do repositório do agente usada para rodar a pipeline. */
        private String pipelineRef = "main";
        /** PAT da conta de serviço (POC). Em produção, use token do Entra ID. */
        private String pat;

        public String getOrgUrl() { return orgUrl; }
        public void setOrgUrl(String orgUrl) { this.orgUrl = orgUrl; }
        public String getProject() { return project; }
        public void setProject(String project) { this.project = project; }
        public int getPipelineId() { return pipelineId; }
        public void setPipelineId(int pipelineId) { this.pipelineId = pipelineId; }
        public String getPipelineRef() { return pipelineRef; }
        public void setPipelineRef(String pipelineRef) { this.pipelineRef = pipelineRef; }
        public String getPat() { return pat; }
        public void setPat(String pat) { this.pat = pat; }
    }

    public static class Github {
        private String apiUrl = "https://api.github.com";
        /** Repositório onde está o workflow do agente, ex.: minha-org/coding-agent-poc */
        private String agentRepo;
        private String workflowFile = "coding-agent.yml";
        private String ref = "main";
        private String token;

        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
        public String getAgentRepo() { return agentRepo; }
        public void setAgentRepo(String agentRepo) { this.agentRepo = agentRepo; }
        public String getWorkflowFile() { return workflowFile; }
        public void setWorkflowFile(String workflowFile) { this.workflowFile = workflowFile; }
        public String getRef() { return ref; }
        public void setRef(String ref) { this.ref = ref; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getDefaultBaseBranch() { return defaultBaseBranch; }
    public void setDefaultBaseBranch(String defaultBaseBranch) { this.defaultBaseBranch = defaultBaseBranch; }
    public String getArtifactName() { return artifactName; }
    public void setArtifactName(String artifactName) { this.artifactName = artifactName; }
    public Azure getAzure() { return azure; }
    public Github getGithub() { return github; }
}
