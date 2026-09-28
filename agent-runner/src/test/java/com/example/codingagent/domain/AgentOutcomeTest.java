package com.example.codingagent.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentOutcomeTest {

    private static final int MAX_ITERATIONS = 30;
    private static final Verification PASSED = Verification.executed("bash .agent/verify.sh", 0, "");
    private static final Verification FAILED = Verification.executed("bash .agent/verify.sh", 1, "erro");
    private static final Verification SKIPPED = Verification.skipped("nenhum comando");

    @Test
    void concluidoQuandoFinishDoneEVerificacaoPassa() {
        AgentOutcome outcome = this.decide(LoopOutcome.finished(3, FinishSignal.from("done", "feito")), PASSED);

        assertThat(outcome.status()).isEqualTo(AgentStatus.COMPLETED);
        assertThat(outcome.summary()).isEqualTo("feito");
    }

    @Test
    void concluidoQuandoNaoHaVerificacao() {
        AgentOutcome outcome = this.decide(LoopOutcome.finished(3, FinishSignal.from("done", "feito")), SKIPPED);

        assertThat(outcome.status()).isEqualTo(AgentStatus.COMPLETED);
    }

    @Test
    void verificacaoFalhouMesmoComFinishDone() {
        AgentOutcome outcome = this.decide(LoopOutcome.finished(3, FinishSignal.from("done", "feito")), FAILED);

        assertThat(outcome.status()).isEqualTo(AgentStatus.VERIFICATION_FAILED);
    }

    @Test
    void incompletoQuandoOModeloDeclaraQueNaoConseguiu() {
        AgentOutcome outcome = this.decide(LoopOutcome.finished(3, FinishSignal.from("incomplete", "faltou X")), PASSED);

        assertThat(outcome.status()).isEqualTo(AgentStatus.INCOMPLETE);
        assertThat(outcome.summary()).isEqualTo("faltou X");
    }

    @Test
    void incompletoQuandoOModeloParaDeUsarFerramentas() {
        AgentOutcome outcome = this.decide(LoopOutcome.stoppedWithoutTools(3), PASSED);

        assertThat(outcome.status()).isEqualTo(AgentStatus.INCOMPLETE);
    }

    @Test
    void orcamentoEsgotadoNoLimiteDeIteracoes() {
        AgentOutcome outcome = this.decide(LoopOutcome.iterationLimitReached(MAX_ITERATIONS), PASSED);

        assertThat(outcome.status()).isEqualTo(AgentStatus.BUDGET_EXCEEDED);
        assertThat(outcome.summary()).contains("30 iterações");
    }

    @Test
    void erroQuandoOLacoFalha() {
        AgentOutcome outcome = this.decide(LoopOutcome.failed(1, "Ollama fora do ar"), SKIPPED);

        assertThat(outcome.status()).isEqualTo(AgentStatus.ERROR);
        assertThat(outcome.summary()).contains("Ollama fora do ar");
    }

    private AgentOutcome decide(LoopOutcome loop, Verification verification) {
        return AgentOutcome.decide(loop, verification, MAX_ITERATIONS);
    }
}
