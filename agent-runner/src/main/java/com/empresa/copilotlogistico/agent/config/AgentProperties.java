package com.empresa.copilotlogistico.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configurações do agente (prefixo "agent" no application.yml ou em variáveis AGENT_*). */
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    /** Pasta do repositório clonado. */
    private String workspace = ".";
    /** Arquivo JSON com a tarefa. */
    private String taskFile = "task.json";
    /** Pasta onde result.json e journal.jsonl são gravados. */
    private String outputDir = "agent-output";
    /** "docker" (pipeline) ou "local" (desenvolvimento). */
    private String executor = "docker";
    /** Nome do container criado por prepare-sandbox.sh. */
    private String container = "agent-sandbox";
    private String containerWorkdir = "/workspace";

    private int maxIterations = 40;
    private int maxToolCalls = 120;
    private int commandTimeoutSeconds = 300;
    private int maxCommandTimeoutSeconds = 1200;
    private int maxOutputChars = 6000;

    /** Comando de verificação. Vazio = usa .agent/verify.sh se existir. */
    private String verifyCommand = "";
    private int verifyTimeoutSeconds = 1200;

    /** Apenas informativo, gravado no result.json. */
    private String modelLabel = "";

    public String getWorkspace() { return workspace; }
    public void setWorkspace(String workspace) { this.workspace = workspace; }
    public String getTaskFile() { return taskFile; }
    public void setTaskFile(String taskFile) { this.taskFile = taskFile; }
    public String getOutputDir() { return outputDir; }
    public void setOutputDir(String outputDir) { this.outputDir = outputDir; }
    public String getExecutor() { return executor; }
    public void setExecutor(String executor) { this.executor = executor; }
    public String getContainer() { return container; }
    public void setContainer(String container) { this.container = container; }
    public String getContainerWorkdir() { return containerWorkdir; }
    public void setContainerWorkdir(String containerWorkdir) { this.containerWorkdir = containerWorkdir; }
    public int getMaxIterations() { return maxIterations; }
    public void setMaxIterations(int maxIterations) { this.maxIterations = maxIterations; }
    public int getMaxToolCalls() { return maxToolCalls; }
    public void setMaxToolCalls(int maxToolCalls) { this.maxToolCalls = maxToolCalls; }
    public int getCommandTimeoutSeconds() { return commandTimeoutSeconds; }
    public void setCommandTimeoutSeconds(int commandTimeoutSeconds) { this.commandTimeoutSeconds = commandTimeoutSeconds; }
    public int getMaxCommandTimeoutSeconds() { return maxCommandTimeoutSeconds; }
    public void setMaxCommandTimeoutSeconds(int maxCommandTimeoutSeconds) { this.maxCommandTimeoutSeconds = maxCommandTimeoutSeconds; }
    public int getMaxOutputChars() { return maxOutputChars; }
    public void setMaxOutputChars(int maxOutputChars) { this.maxOutputChars = maxOutputChars; }
    public String getVerifyCommand() { return verifyCommand; }
    public void setVerifyCommand(String verifyCommand) { this.verifyCommand = verifyCommand; }
    public int getVerifyTimeoutSeconds() { return verifyTimeoutSeconds; }
    public void setVerifyTimeoutSeconds(int verifyTimeoutSeconds) { this.verifyTimeoutSeconds = verifyTimeoutSeconds; }
    public String getModelLabel() { return modelLabel; }
    public void setModelLabel(String modelLabel) { this.modelLabel = modelLabel; }
}
