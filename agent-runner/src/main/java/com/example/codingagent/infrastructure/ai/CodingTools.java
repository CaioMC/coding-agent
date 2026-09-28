package com.example.codingagent.infrastructure.ai;

import com.example.codingagent.application.port.CommandExecutor;
import com.example.codingagent.application.port.ExecutionJournal;
import com.example.codingagent.application.port.JournalEvent;
import com.example.codingagent.application.port.Workspace;
import com.example.codingagent.domain.CommandPolicy;
import com.example.codingagent.domain.CommandResult;
import com.example.codingagent.domain.CommandTimeouts;
import com.example.codingagent.domain.FinishSignal;
import com.example.codingagent.domain.ToolUsage;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class CodingTools {

    private static final int DEFAULT_LIST_DEPTH = 3;
    private static final int MAX_LISTED_ENTRIES = 400;
    private static final int MAX_SEARCH_RESULTS = 80;

    private final Workspace workspace;
    private final CommandExecutor executor;
    private final CommandPolicy policy;
    private final CommandTimeouts timeouts;
    private final ToolUsage usage;
    private final ExecutionJournal journal;

    private volatile FinishSignal finishSignal;

    public CodingTools(Workspace workspace, CommandExecutor executor, CommandPolicy policy,
                       CommandTimeouts timeouts, ToolUsage usage, ExecutionJournal journal) {
        this.workspace = workspace;
        this.executor = executor;
        this.policy = policy;
        this.timeouts = timeouts;
        this.usage = usage;
        this.journal = journal;
    }

    public Optional<FinishSignal> finishSignal() {
        return Optional.ofNullable(this.finishSignal);
    }

    @Tool(name = "list_files", description = """
            Lista arquivos e pastas do repositório. Pastas terminam com '/'.
            Ignora .git, node_modules, target e build.""")
    public String listFiles(
            @ToolParam(description = "Pasta relativa à raiz do repositório. Use '.' para a raiz.", required = false) String path,
            @ToolParam(description = "Profundidade máxima (padrão 3).", required = false) Integer maxDepth) {
        return this.execute("list_files", Map.of("path", String.valueOf(path), "maxDepth", String.valueOf(maxDepth)), () -> {
            List<String> files = this.workspace.listFiles(path, maxDepth == null ? DEFAULT_LIST_DEPTH : maxDepth, MAX_LISTED_ENTRIES);
            return files.isEmpty() ? "(pasta vazia)" : String.join("\n", files);
        });
    }

    @Tool(name = "read_file", description = """
            Lê um arquivo de texto e devolve o conteúdo com número de linha no início de cada linha.
            Os números NÃO fazem parte do arquivo. Use startLine/endLine para arquivos grandes.""")
    public String readFile(
            @ToolParam(description = "Caminho relativo à raiz do repositório.") String path,
            @ToolParam(description = "Primeira linha (1 = início).", required = false) Integer startLine,
            @ToolParam(description = "Última linha, inclusive.", required = false) Integer endLine) {
        return this.execute("read_file", Map.of("path", String.valueOf(path)),
                () -> this.workspace.readFile(path, startLine, endLine));
    }

    @Tool(name = "write_file", description = """
            Cria um arquivo ou substitui TODO o conteúdo de um arquivo existente.
            Para alterar só um trecho de um arquivo existente, prefira replace_in_file.""")
    public String writeFile(
            @ToolParam(description = "Caminho relativo à raiz do repositório.") String path,
            @ToolParam(description = "Conteúdo completo do arquivo.") String content) {
        int chars = content == null ? 0 : content.length();
        return this.execute("write_file", Map.of("path", String.valueOf(path), "chars", String.valueOf(chars)), () -> {
            this.workspace.writeFile(path, content);
            return "OK: " + path + " gravado (" + chars + " caracteres).";
        });
    }

    @Tool(name = "replace_in_file", description = """
            Substitui um trecho exato de um arquivo. O trecho antigo deve aparecer exatamente uma vez,
            copiado do arquivo sem os números de linha e com a mesma indentação.""")
    public String replaceInFile(
            @ToolParam(description = "Caminho relativo à raiz do repositório.") String path,
            @ToolParam(description = "Trecho atual, exatamente como está no arquivo.") String oldText,
            @ToolParam(description = "Novo trecho.") String newText) {
        return this.execute("replace_in_file", Map.of("path", String.valueOf(path)), () -> {
            this.workspace.replaceInFile(path, oldText, newText);
            return "OK: trecho substituído em " + path + ".";
        });
    }

    @Tool(name = "search_code", description = """
            Procura uma expressão regular nos arquivos do repositório.
            Devolve linhas no formato caminho:linha: conteúdo.""")
    public String searchCode(
            @ToolParam(description = "Expressão regular (Java) ou texto simples.") String pattern,
            @ToolParam(description = "Filtra por trecho do caminho, ex.: 'src/main' ou '.java'.", required = false) String pathContains) {
        return this.execute("search_code", Map.of("pattern", String.valueOf(pattern), "path", String.valueOf(pathContains)), () -> {
            List<String> hits = this.workspace.search(pattern, pathContains, MAX_SEARCH_RESULTS);
            return hits.isEmpty() ? "Nenhum resultado." : String.join("\n", hits);
        });
    }

    @Tool(name = "run_command", description = """
            Executa um comando de shell no ambiente do projeto (container, sem internet) e devolve
            o código de saída e o final da saída. Use para compilar, rodar testes e inspecionar o projeto.
            Não use para git commit, push ou criar branch: o workflow faz isso.""")
    public String runCommand(
            @ToolParam(description = "Comando bash, executado na raiz do repositório.") String command,
            @ToolParam(description = "Tempo limite em segundos (padrão 300).", required = false) Integer timeoutSeconds) {
        return this.execute("run_command", Map.of("command", String.valueOf(command)), () -> this.policy.findViolation(command)
                .map(reason -> "BLOQUEADO: " + reason)
                .orElseGet(() -> this.runAllowedCommand(command, timeoutSeconds)));
    }

    @Tool(name = "finish", returnDirect = true, description = """
            Encerra a tarefa. Chame UMA vez, quando a implementação estiver pronta e verificada,
            ou quando concluir que não é possível terminar.""")
    public String finish(
            @ToolParam(description = "'done' se concluiu, 'incomplete' se não conseguiu.") String status,
            @ToolParam(description = "Resumo para o revisor: o que mudou, em quais arquivos e como foi verificado.") String summary) {
        this.finishSignal = FinishSignal.from(status, summary);
        this.journal.record(JournalEvent.FINISH, Map.of(
                "status", this.finishSignal.statusLabel(),
                "summary", String.valueOf(summary)));
        return "FINISHED: " + this.finishSignal.statusLabel();
    }

    private String runAllowedCommand(String command, Integer timeoutSeconds) {
        this.usage.recordCommand(command);
        CommandResult result = this.executor.run(command, this.timeouts.resolve(timeoutSeconds));
        this.journal.record(JournalEvent.COMMAND_RESULT, Map.of(
                "command", command,
                "exitCode", result.exitCode(),
                "timedOut", result.timedOut(),
                "durationMs", result.durationMs(),
                "output", result.output()));
        return result.toObservation();
    }

    private String execute(String tool, Map<String, String> args, Supplier<String> action) {
        if (!this.usage.tryStartCall()) {
            this.journal.record(JournalEvent.TOOL_BUDGET_EXCEEDED, Map.of("tool", tool));
            return "LIMITE DE CHAMADAS ATINGIDO (" + this.usage.maxCalls()
                    + "). Não chame mais ferramentas de trabalho: chame finish agora com o que foi feito.";
        }
        long start = System.nanoTime();
        String result = this.safely(action);
        this.recordToolCall(tool, args, start, result);
        return result;
    }

    private String safely(Supplier<String> action) {
        try {
            return action.get();
        } catch (SecurityException | IllegalArgumentException e) {
            return "ERRO: " + e.getMessage();
        } catch (RuntimeException e) {
            return "ERRO inesperado (" + e.getClass().getSimpleName() + "): " + e.getMessage();
        }
    }

    private void recordToolCall(String tool, Map<String, String> args, long startNanos, String result) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("tool", tool);
        entry.put("args", args);
        entry.put("durationMs", (System.nanoTime() - startNanos) / 1_000_000);
        entry.put("result", result);
        this.journal.record(JournalEvent.TOOL_CALL, entry);
    }
}
