package com.empresa.copilotlogistico.agent.core;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Bloqueios simples de comandos. Não é a barreira principal de segurança (essa é o
 * container sem rede e sem credenciais); serve para dar ao modelo um retorno claro
 * quando ele tenta algo que é papel da pipeline, como fazer push.
 */
public class CommandPolicy {

    private record Rule(Pattern pattern, String reason) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("\\bgit\\s+(push|remote|config\\s+--global|credential)\\b"),
                    "Operações de push, remote e credenciais são feitas pela pipeline, não pelo agente."),
            new Rule(Pattern.compile("\\bgit\\s+(commit|checkout\\s+-b|switch\\s+-c|reset\\s+--hard|rebase)\\b"),
                    "A pipeline cuida de branch e commit. Apenas edite os arquivos."),
            new Rule(Pattern.compile("\\bsudo\\b"), "sudo não é permitido."),
            new Rule(Pattern.compile("\\brm\\s+-[a-zA-Z]*r[a-zA-Z]*f?\\s+(/|~|\\$HOME)(\\s|$)"),
                    "Remoção recursiva fora do workspace não é permitida."),
            new Rule(Pattern.compile("\\b(shutdown|reboot|mkfs|dd\\s+if=)\\b"), "Comando de sistema não permitido."));

    /** @return null se permitido; senão, o motivo do bloqueio (enviado ao modelo). */
    public String check(String command) {
        if (command == null || command.isBlank()) {
            return "Comando vazio.";
        }
        for (Rule rule : RULES) {
            if (rule.pattern().matcher(command).find()) {
                return rule.reason();
            }
        }
        return null;
    }
}
