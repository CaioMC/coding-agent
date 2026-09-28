package com.example.codingagent.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ToolUsageTest {

    @Test
    void recusaChamadasAlemDoLimite() {
        ToolUsage usage = new ToolUsage(2);

        assertThat(usage.tryStartCall()).isTrue();
        assertThat(usage.tryStartCall()).isTrue();
        assertThat(usage.tryStartCall()).isFalse();
        assertThat(usage.calls()).isEqualTo(3);
    }

    @Test
    void tempoLimiteDoComandoRespeitaPadraoEMaximo() {
        CommandTimeouts timeouts = new CommandTimeouts(Duration.ofSeconds(300), Duration.ofSeconds(1200));

        assertThat(timeouts.resolve(null)).isEqualTo(Duration.ofSeconds(300));
        assertThat(timeouts.resolve(60)).isEqualTo(Duration.ofSeconds(60));
        assertThat(timeouts.resolve(5000)).isEqualTo(Duration.ofSeconds(1200));
    }
}
