# Diagramas de estado

O diagrama de sequência responde "quem fala com quem". O diagrama de estado responde outra
pergunta: **em que situação a coisa está agora, e o que a faz mudar de situação?** Eu usei um
diagrama por "coisa": a execução, o laço, uma chamada de ferramenta, o container, a
publicação e a issue.

Cada estado final aqui tem um teste automatizado correspondente. A tabela no fim diz qual.

## Índice

1. [A execução inteira (job)](#1-a-execução-inteira-job)
2. [O laço do agente](#2-o-laço-do-agente)
3. [A decisão do status final](#3-a-decisão-do-status-final)
4. [Uma chamada de ferramenta](#4-uma-chamada-de-ferramenta)
5. [Um caminho vindo do modelo](#5-um-caminho-vindo-do-modelo)
6. [O container sandbox](#6-o-container-sandbox)
7. [Publicação (PublishCommand)](#7-publicação-publishcommand)
8. [A issue, vista por quem pediu](#8-a-issue-vista-por-quem-pediu)
9. [Onde cada estado é testado](#9-onde-cada-estado-é-testado)

---

## 1. A execução inteira (job)

```mermaid
stateDiagram-v2
    [*] --> Disparado: workflow_dispatch
    Disparado --> Recusado: workflow inválido (HTTP 422)
    Recusado --> [*]

    Disparado --> Preparando: steps 0 a 4
    Preparando --> LendoIssue: agent-runner compilado
    LendoIssue --> PreparandoModelo: task.json gravado e issue comentada
    PreparandoModelo --> PreparandoSandbox: Ollama respondendo
    PreparandoSandbox --> AgenteTrabalhando: container pronto e sem rede
    AgenteTrabalhando --> Publicando: result.json gravado, saída 0
    Publicando --> Limpando: PR aberto, sem alterações ou sem PR

    Preparando --> Falhou
    LendoIssue --> Falhou
    PreparandoModelo --> Falhou
    PreparandoSandbox --> Falhou
    AgenteTrabalhando --> Falhou: status ERROR, saída 1
    Publicando --> Falhou: git ou API recusou

    Falhou --> Limpando: if always()
    Limpando --> [*]: artefato publicado e container removido
```

## 2. O laço do agente

Este é o `SpringAiAgentLoop.run()`. Os contadores são `iteração` (quantas vezes o modelo foi
chamado) e `lembretes` (quantas vezes o modelo respondeu sem ferramenta).

```mermaid
stateDiagram-v2
    [*] --> ChamandoModelo: iteração = 1

    ChamandoModelo --> ExecutandoFerramentas: resposta com tool calls
    ChamandoModelo --> Lembrando: só texto e lembretes < 2
    ChamandoModelo --> SemFerramentas: só texto e lembretes = 2
    ChamandoModelo --> Erro: exceção (modelo fora do ar, timeout)

    ExecutandoFerramentas --> Finalizado: finish foi chamado
    ExecutandoFerramentas --> ProximaIteracao: sem finish
    ExecutandoFerramentas --> Erro: exceção inesperada

    Lembrando --> ProximaIteracao: adiciona o lembrete ao histórico

    ProximaIteracao --> ChamandoModelo: iteração < limite
    ProximaIteracao --> LimiteDeIteracoes: iteração = limite

    Finalizado --> [*]: LoopEnd.FINISHED
    SemFerramentas --> [*]: LoopEnd.NO_TOOL_CALLS
    LimiteDeIteracoes --> [*]: LoopEnd.ITERATION_LIMIT
    Erro --> [*]: LoopEnd.ERROR
```

## 3. A decisão do status final

Depois do laço, o `AgentOutcome.decide()` junta o `LoopOutcome` com a `Verification` e decide
o status que vai para o `result.json`. Os losangos são decisões.

```mermaid
stateDiagram-v2
    state comoTerminou <<choice>>
    state modeloConcluiu <<choice>>
    state verificacaoPassou <<choice>>

    [*] --> comoTerminou
    comoTerminou --> ERROR: LoopEnd.ERROR
    comoTerminou --> BUDGET_EXCEEDED: LoopEnd.ITERATION_LIMIT
    comoTerminou --> INCOMPLETE: LoopEnd.NO_TOOL_CALLS
    comoTerminou --> modeloConcluiu: LoopEnd.FINISHED

    modeloConcluiu --> INCOMPLETE: finish("incomplete")
    modeloConcluiu --> verificacaoPassou: finish("done")

    verificacaoPassou --> VERIFICATION_FAILED: verify.sh saiu com código diferente de 0
    verificacaoPassou --> COMPLETED: verify.sh saiu com 0 ou não existe

    ERROR --> [*]
    BUDGET_EXCEEDED --> [*]
    INCOMPLETE --> [*]
    VERIFICATION_FAILED --> [*]
    COMPLETED --> [*]
```

O que cada status significa para quem vai revisar:

| Status | Significado | Título do PR | Código de saída |
|---|---|---|---|
| `COMPLETED` | Terminou e a verificação passou (ou não havia verificação) | `título da issue` | 0 |
| `VERIFICATION_FAILED` | O modelo disse que terminou, mas o `verify.sh` falhou | `[WIP] título` | 0 |
| `INCOMPLETE` | O modelo desistiu ou parou de usar ferramentas | `[WIP] título` | 0 |
| `BUDGET_EXCEEDED` | Chegou ao limite de iterações | `[WIP] título` | 0 |
| `ERROR` | O laço quebrou (ex.: modelo indisponível) | sem PR | 1 |

## 4. Uma chamada de ferramenta

Toda ferramenta, exceto `finish`, passa pelo `CodingTools.execute()`. O `run_command` tem uma
etapa a mais: a política de comandos.

```mermaid
stateDiagram-v2
    [*] --> ConferindoOrcamento: modelo pediu uma ferramenta

    ConferindoOrcamento --> LimiteAtingido: ToolUsage.tryStartCall() = false
    ConferindoOrcamento --> Executando: ainda há orçamento

    state Executando {
        [*] --> TipoDaFerramenta
        state TipoDaFerramenta <<choice>>
        TipoDaFerramenta --> OperacaoDeArquivo: list, read, write, replace, search
        TipoDaFerramenta --> ConferindoPolitica: run_command

        ConferindoPolitica --> Bloqueado: CommandPolicy encontrou violação
        ConferindoPolitica --> RodandoNoContainer: permitido

        RodandoNoContainer --> ComandoTerminou: saiu antes do tempo limite
        RodandoNoContainer --> TempoEsgotado: passou do tempo limite (exit 124)

        OperacaoDeArquivo --> [*]
        Bloqueado --> [*]
        ComandoTerminou --> [*]
        TempoEsgotado --> [*]
    }

    Executando --> Sucesso: devolveu texto
    Executando --> ErroEsperado: SecurityException ou IllegalArgumentException
    Executando --> ErroInesperado: outra RuntimeException

    Sucesso --> RegistradaNoDiario
    ErroEsperado --> RegistradaNoDiario: "ERRO: ..."
    ErroInesperado --> RegistradaNoDiario: "ERRO inesperado (...)"

    LimiteAtingido --> [*]: "LIMITE DE CHAMADAS ATINGIDO"
    RegistradaNoDiario --> [*]: texto devolvido ao modelo
```

O ponto principal: **nenhum desses caminhos derruba o agente**. Todos terminam com um texto
que volta para o modelo.

## 5. Um caminho vindo do modelo

O `SafePathResolver.resolve()` é uma sequência de barreiras. Se o caminho passar por todas,
vira um `Path` seguro.

```mermaid
stateDiagram-v2
    state dentroDaRaiz <<choice>>
    state existe <<choice>>
    state linkSeguro <<choice>>
    state dentroDoGit <<choice>>

    [*] --> Limpo: remove espaços e a barra inicial, vazio vira "."
    Limpo --> Normalizado: resolve contra a raiz e normaliza ".."
    Normalizado --> dentroDaRaiz
    dentroDaRaiz --> Recusado: fora da raiz
    dentroDaRaiz --> existe: dentro da raiz
    existe --> dentroDoGit: ainda não existe (arquivo novo)
    existe --> linkSeguro: já existe
    linkSeguro --> Recusado: link simbólico aponta para fora
    linkSeguro --> dentroDoGit: caminho real dentro da raiz
    dentroDoGit --> Recusado: começa com .git
    dentroDoGit --> Aceito: qualquer outro caminho

    Recusado --> [*]: SecurityException
    Aceito --> [*]: Path absoluto seguro
```

## 6. O container sandbox

```mermaid
stateDiagram-v2
    state modoDeRede <<choice>>

    [*] --> SemImagem
    SemImagem --> ImagemPronta: docker build (.agent/Dockerfile) ou docker pull
    ImagemPronta --> ComRede: docker run, repositório em /workspace
    ComRede --> Invalido: falta bash ou timeout na imagem
    ComRede --> InstalandoDependencias: .agent/setup.sh existe
    ComRede --> modoDeRede: sem setup.sh
    InstalandoDependencias --> modoDeRede: setup ok
    InstalandoDependencias --> Invalido: setup falhou ou passou de 30 min
    modoDeRede --> SemRede: SANDBOX_NETWORK=off (padrão), rede desconectada
    modoDeRede --> ComRedeDeProposito: SANDBOX_NETWORK=on

    SemRede --> EmUsoPeloAgente: step 9
    ComRedeDeProposito --> EmUsoPeloAgente: step 9
    EmUsoPeloAgente --> Removido: step 12 (docker rm -f)
    Invalido --> Removido: step 12
    Removido --> [*]
```

`ComRedeDeProposito` existe só para depuração. Nunca use com código que você não confia.

## 7. Publicação (PublishCommand)

O `publish` grava o resultado no campo `publish.state` do `result.json`.

```mermaid
stateDiagram-v2
    state temAlteracoes <<choice>>
    state podeAbrirPr <<choice>>

    [*] --> Preparando: carrega task.json e result.json
    Preparando --> temAlteracoes: git add (sem target, build, node_modules, .gradle)

    temAlteracoes --> NO_CHANGES: nada staged
    temAlteracoes --> DiffSalvo: há arquivos alterados

    DiffSalvo --> podeAbrirPr: grava changes.patch e changedFiles
    podeAbrirPr --> SKIPPED: status ERROR
    podeAbrirPr --> SKIPPED: status diferente de COMPLETED e OPEN_PR_ON_FAILURE=false
    podeAbrirPr --> Publicando: caso contrário

    Publicando --> BranchNoGitHub: commit e push ok
    Publicando --> FalhaGit: commit ou push recusado
    BranchNoGitHub --> PR_CREATED: POST /pulls ok
    BranchNoGitHub --> FalhaApi: POST /pulls recusado (ex.: 403)

    NO_CHANGES --> [*]: comenta na issue, saída 0
    SKIPPED --> [*]: comenta na issue, saída 0
    PR_CREATED --> [*]: comenta na issue com o link, saída 0
    FalhaGit --> [*]: saída 1
    FalhaApi --> [*]: saída 1, a branch já está no GitHub
```

## 8. A issue, vista por quem pediu

```mermaid
stateDiagram-v2
    [*] --> Aberta: pessoa ou assistente cria a issue
    Aberta --> EmAndamento: comentário "O agente começou" (step 5)
    EmAndamento --> ComPrEmRascunho: comentário com o link do PR
    EmAndamento --> SemAlteracoes: comentário "terminou sem alterar arquivos"
    EmAndamento --> SemPr: comentário "não abriu PR, o diff está no artefato"
    EmAndamento --> Silenciosa: job falhou depois do step 5 (ex.: ERROR, 403)
    ComPrEmRascunho --> Fechada: alguém revisa e faz merge ("Closes #N")
    Fechada --> [*]
```

`Silenciosa` é a limitação que eu comentei em [pipeline.md](pipeline.md#6-o-que-acontece-quando-um-step-falha):
nesses casos, o motivo está no log do job e no artefato `agent-result`, não na issue.

## 9. Onde cada estado é testado

| Diagrama | Estado | Teste |
|---|---|---|
| 2. Laço | `FINISHED` | `SpringAiAgentLoopTest.terminaQuandoOModeloChamaFinish` |
| 2. Laço | `Lembrando` e depois `FINISHED` | `SpringAiAgentLoopTest.lembreteSemFerramentaRetomaOTrabalho` |
| 2. Laço | `NO_TOOL_CALLS` | `SpringAiAgentLoopTest.desisteDepoisDeDuasRespostasSemFerramentas` |
| 2. Laço | `ITERATION_LIMIT` | `SpringAiAgentLoopTest.paraNoLimiteDeIteracoes` |
| 2. Laço | `ERROR` | `SpringAiAgentLoopTest.falhaDoModeloViraErro` |
| 3. Status | os cinco status | `AgentOutcomeTest` (um teste por status) |
| 3. Status | verificação roda mesmo com `ERROR` | `RunCodingAgentUseCaseTest.verificacaoRodaMesmoQuandoOLacoFalha` |
| 4. Ferramenta | `Bloqueado` | `SpringAiAgentLoopTest.comandoBloqueadoNaoChegaAoExecutor`, `CommandPolicyTest` |
| 4. Ferramenta | `LimiteAtingido` | `ToolUsageTest.recusaChamadasAlemDoLimite` |
| 5. Caminho | fora da raiz, link, `.git` | `LocalWorkspaceTest` |
| 7. Publicação | `PR_CREATED` | `test_gitops.PublishTest.test_abre_pr_em_rascunho_quando_concluido` |
| 7. Publicação | `[WIP]` | `test_gitops.PublishTest.test_prefixo_wip_quando_verificacao_falha` |
| 7. Publicação | `NO_CHANGES` | `test_gitops.PublishTest.test_sem_alteracoes_apenas_comenta` |
| 7. Publicação | `SKIPPED` | `test_gitops.PublishTest.test_status_error_guarda_patch_e_nao_abre_pr` |
| 7. Publicação | `FalhaApi` | `test_gitops.PublishTest.test_falha_ao_criar_pr_encerra_com_erro` |
