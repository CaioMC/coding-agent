package com.example.codingagent.domain;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class CommandPolicy {

    private record Rule(Pattern pattern, String reason) {

        boolean matches(String command) {
            return this.pattern.matcher(command).find();
        }
    }

    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("\\bgit\\s+(push|remote|config\\s+--global|credential)\\b"),
                    "Operações de push, remote e credenciais são feitas pelo workflow, não pelo agente."),
            new Rule(Pattern.compile("\\bgit\\s+(commit|checkout\\s+-b|switch\\s+-c|reset\\s+--hard|rebase)\\b"),
                    "O workflow cuida de branch e commit. Apenas edite os arquivos."),
            new Rule(Pattern.compile("\\bsudo\\b"),
                    "sudo não é permitido."),
            new Rule(Pattern.compile("\\brm\\s+-[a-zA-Z]*r[a-zA-Z]*f?\\s+(/|~|\\$HOME)(\\s|$)"),
                    "Remoção recursiva fora do workspace não é permitida."),
            new Rule(Pattern.compile("\\b(shutdown|reboot|mkfs|dd\\s+if=)\\b"),
                    "Comando de sistema não permitido."));

    public Optional<String> findViolation(String command) {
        if (command == null || command.isBlank()) {
            return Optional.of("Comando vazio.");
        }
        return RULES.stream()
                .filter(rule -> rule.matches(command))
                .map(Rule::reason)
                .findFirst();
    }
}
