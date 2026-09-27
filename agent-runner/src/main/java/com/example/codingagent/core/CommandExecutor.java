package com.example.codingagent.core;

import java.time.Duration;

/**
 * Onde os comandos do agente rodam de verdade.
 *
 * É a "interface de executor" do estudo: o workflow usa {@link DockerCommandExecutor}
 * (comandos dentro do container do projeto); a execução na máquina do usuário pode
 * usar a mesma interface com outro adaptador, sem mudar o resto do agente.
 */
public interface CommandExecutor {

    CommandResult run(String command, Duration timeout);

    /** Descrição curta do ambiente, incluída no prompt para o modelo saber onde está. */
    String describe();
}
