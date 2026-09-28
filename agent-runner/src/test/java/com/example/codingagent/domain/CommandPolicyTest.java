package com.example.codingagent.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommandPolicyTest {

    private final CommandPolicy policy = new CommandPolicy();

    @Test
    void permiteBuildETestes() {
        assertThat(this.policy.findViolation("mvn -o -q test")).isEmpty();
        assertThat(this.policy.findViolation("git status && git diff")).isEmpty();
    }

    @Test
    void bloqueiaOperacoesDaPipeline() {
        assertThat(this.policy.findViolation("git push origin main")).isPresent();
        assertThat(this.policy.findViolation("git commit -m x")).isPresent();
        assertThat(this.policy.findViolation("sudo apt-get install x")).isPresent();
        assertThat(this.policy.findViolation("rm -rf /")).isPresent();
    }

    @Test
    void bloqueiaComandoVazio() {
        assertThat(this.policy.findViolation("  ")).contains("Comando vazio.");
    }
}
