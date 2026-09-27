package com.empresa.copilotlogistico.agent.tools;

import com.empresa.copilotlogistico.agent.core.CommandExecutor;
import com.empresa.copilotlogistico.agent.core.CommandPolicy;
import com.empresa.copilotlogistico.agent.core.CommandResult;
import com.empresa.copilotlogistico.agent.core.ExecutionJournal;
import com.empresa.copilotlogistico.agent.core.Workspace;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * As ferramentas que o modelo pode chamar. É a "interface agente e computador" (ACI).
 *
 * Cada método devolve texto, porque é isso que o modelo lê. Erros não derrubam o agente:
 * viram uma mensagem "ERRO: ..." para que o modelo corrija o rumo na próxima iteração.
 */
public class CodingTools {

    private final Workspace workspace;
    private final CommandExecutor executor;
    private final CommandPolicy policy;
    private final ExecutionJournal journal;
    private final int defaultTimeoutSeconds;
    private final int maxTimeoutSeconds;

    private volatile FinishSignal finishSignal;

    public record FinishSignal(String status, String summary) {
    }

    public CodingTools(Workspace workspace, CommandExecutor executor, CommandPolicy policy,
                       ExecutionJournal journal, int defaultTimeoutSeconds, int maxTimeoutSeconds) {
        this.workspace = workspace;
        this.executor = executor;
        this.policy = policy;
        this.journal = journal;
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
        this.maxTimeoutSeconds = maxTimeoutSeconds;
    }

    public FinishSignal finishSignal() {
        return finishSignal;
    }

    @Tool(name = "list_files", description = """
            Lista arquivos e pastas do repositório. Pastas terminam com '/'.
            Ignora .git, node_modules, target e build.""")
    public String listFiles(
            @ToolParam(description = "Pasta relativa à raiz do repositório. Use '.' para a raiz.", required = false) String path,
            @ToolParam(description = "Profundidade máxima (padrão 3).", required = false) Integer maxDepth) {
        return call("list_files", Map.of("path", String.valueOf(path), "maxDepth", String.valueOf(maxDepth)), () -> {
            List<String> files = workspace.listFiles(path, maxDepth == null ? 3 : maxDepth, 400);
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
        return call("read_file", Map.of("path", String.valueOf(path)),
                () -> workspace.readFile(path, startLine, endLine));
    }

    @Tool(name = "write_file", description = """
            Cria um arquivo ou substitui TODO o conteúdo de um arquivo existente.
            Para alterar só um trecho de um arquivo existente, prefira replace_in_file.""")
    public String writeFile(
            @ToolParam(description = "Caminho relativo à raiz do repositório.") String path,
            @ToolParam(description = "Conteúdo completo do arquivo.") String content) {
        return call("write_file", Map.of("path", String.valueOf(path),
                "chars", String.valueOf(content == null ? 0 : content.length())), () -> {
            workspace.writeFile(path, content);
            return "OK: " + path + " gravado (" + (content == null ? 0 : content.length()) + " caracteres).";
        });
    }

    @Tool(name = "replace_in_file", description = """
            Substitui um trecho exato de um arquivo. O trecho antigo deve aparecer exatamente uma vez,
            copiado do arquivo sem os números de linha e com a mesma indentação.""")
    public String replaceInFile(
            @ToolParam(description = "Caminho relativo à raiz do repositório.") String path,
            @ToolParam(description = "Trecho atual, exatamente como está no arquivo.") String oldText,
            @ToolParam(description = "Novo trecho.") String newText) {
        return call("replace_in_file", Map.of("path", String.valueOf(path)), () -> {
            workspace.replaceInFile(path, oldText, newText);
            return "OK: trecho substituído em " + path + ".";
        });
    }

    @Tool(name = "search_code", description = """
            Procura uma expressão regular nos arquivos do repositório.
            Devolve linhas no formato caminho:linha: conteúdo.""")
    public String searchCode(
            @ToolParam(description = "Expressão regular (Java) ou texto simples.") String pattern,
            @ToolParam(description = "Filtra por trecho do caminho, ex.: 'src/main' ou '.java'.", required = false) String pathContains) {
        return call("search_code", Map.of("pattern", String.valueOf(pattern), "path", String.valueOf(pathContains)), () -> {
            List<String> hits = workspace.search(pattern, pathContains, 80);
            return hits.isEmpty() ? "Nenhum resultado." : String.join("\n", hits);
        });
    }

    @Tool(name = "run_command", description = """
            Executa um comando de shell no ambiente do projeto (container, sem internet) e devolve
            o código de saída e o final da saída. Use para compilar, rodar testes e inspecionar o projeto.
            Não use para git commit, push ou criar branch: a pipeline faz isso.""")
    public String runCommand(
            @ToolParam(description = "Comando bash, executado na raiz do repositório.") String command,
            @ToolParam(description = "Tempo limite em segundos (padrão 300).", required = false) Integer timeoutSeconds) {
        return call("run_command", Map.of("command", String.valueOf(command)), () -> {
            String blocked = policy.check(command);
            if (blocked != null) {
                return "BLOQUEADO: " + blocked;
            }
            int t = timeoutSeconds == null || timeoutSeconds <= 0 ? defaultTimeoutSeconds
                    : Math.min(timeoutSeconds, maxTimeoutSeconds);
            journal.recordCommand(command);
            CommandResult result = executor.run(command, Duration.ofSeconds(t));
            journal.log("command_result", Map.of("command", command, "exitCode", result.exitCode(),
                    "timedOut", result.timedOut(), "durationMs", result.durationMs(), "output", result.output()));
            return result.toObservation();
        });
    }

    @Tool(name = "finish", returnDirect = true, description = """
            Encerra a tarefa. Chame UMA vez, quando a implementação estiver pronta e verificada,
            ou quando concluir que não é possível terminar.""")
    public String finish(
            @ToolParam(description = "'done' se concluiu, 'incomplete' se não conseguiu.") String status,
            @ToolParam(description = "Resumo para o revisor: o que mudou, em quais arquivos e como foi verificado.") String summary) {
        String normalized = "done".equalsIgnoreCase(status == null ? "" : status.trim()) ? "done" : "incomplete";
        this.finishSignal = new FinishSignal(normalized, summary == null ? "" : summary);
        journal.log("finish", Map.of("status", normalized, "summary", String.valueOf(summary)));
        return "FINISHED: " + normalized;
    }

    private String call(String tool, Map<String, String> args, Supplier<String> body) {
        if (!journal.startToolCall()) {
            journal.log("tool_budget_exceeded", Map.of("tool", tool));
            return "LIMITE DE CHAMADAS ATINGIDO (" + journal.maxToolCalls()
                    + "). Não chame mais ferramentas de trabalho: chame finish agora com o que foi feito.";
        }
        long start = System.nanoTime();
        String result;
        try {
            result = body.get();
        } catch (SecurityException | IllegalArgumentException e) {
            result = "ERRO: " + e.getMessage();
        } catch (RuntimeException e) {
            result = "ERRO inesperado (" + e.getClass().getSimpleName() + "): " + e.getMessage();
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("tool", tool);
        entry.put("args", args);
        entry.put("durationMs", (System.nanoTime() - start) / 1_000_000);
        entry.put("result", result);
        journal.log("tool_call", entry);
        return result;
    }
}
