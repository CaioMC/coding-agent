package com.empresa.copilotlogistico.agent.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommandPolicyTest {

    private final CommandPolicy policy = new CommandPolicy();

    @Test
    void permiteBuildETestes() {
        assertThat(policy.check("mvn -o -q test")).isNull();
        assertThat(policy.check("git status && git diff")).isNull();
    }

    @Test
    void bloqueiaOperacoesDaPipeline() {
        assertThat(policy.check("git push origin main")).isNotNull();
        assertThat(policy.check("git commit -m x")).isNotNull();
        assertThat(policy.check("sudo apt-get install x")).isNotNull();
        assertThat(policy.check("rm -rf /")).isNotNull();
    }
}
