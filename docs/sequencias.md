# Diagramas de sequência

Um diagrama de sequência mostra **quem conversa com quem, e em que ordem**. Eu separei um
diagrama por caso, em vez de um diagrama gigante com todos os `alt`, porque assim fica fácil
achar o cenário que você está investigando.

Os nomes dos participantes são os nomes reais das classes e scripts. Quando um participante é
uma porta (interface) da arquitetura, eu uso o nome da implementação que roda na pipeline:
por exemplo, `DockerCommandExecutor` no lugar de `CommandExecutor`.

## Índice

**Do início ao fim**
1. [Disparo e leitura da issue](#1-disparo-e-leitura-da-issue)
2. [A execução completa do agent-runner](#2-a-execução-completa-do-agent-runner)

**Dentro do laço do agente**
3. [Uma iteração com ferramenta](#3-uma-iteração-com-ferramenta)
4. [O modelo chama finish](#4-o-modelo-chama-finish)
5. [Ferramenta devolve erro e o modelo corrige](#5-ferramenta-devolve-erro-e-o-modelo-corrige)
6. [Comando bloqueado pela política](#6-comando-bloqueado-pela-política)
7. [Tentativa de sair do repositório](#7-tentativa-de-sair-do-repositório)
8. [Modelo responde sem ferramenta](#8-modelo-responde-sem-ferramenta)
9. [Limite de chamadas de ferramenta](#9-limite-de-chamadas-de-ferramenta)
10. [Limite de iterações](#10-limite-de-iterações)
11. [Falha do modelo](#11-falha-do-modelo)

**Depois do laço**
12. [Verificação independente](#12-verificação-independente)
13. [Publicação com PR](#13-publicação-com-pr)
14. [Publicação sem alterações](#14-publicação-sem-alterações)
15. [Publicação sem PR (status ERROR)](#15-publicação-sem-pr-status-error)
16. [Falha ao abrir o PR (HTTP 403)](#16-falha-ao-abrir-o-pr-http-403)

---

## 1. Disparo e leitura da issue

Quem abre a issue pode ser uma pessoa ou o assistente de chat. O disparo usa o
`workflow_dispatch` do repositório alvo.

```mermaid
sequenceDiagram
    autonumber
    actor U as Pessoa ou assistente
    participant GH as API do GitHub
    participant WF as GitHub Actions
    participant F as gitops fetch-issue
    U->>GH: POST /issues (título e corpo com "- [ ] critérios")
    GH-->>U: issue número N
    U->>GH: POST /actions/workflows/coding-agent.yml/dispatches
    GH->>WF: coding-agent.yml chama agent.yml@main
    WF->>WF: steps 0 a 4 (caminhos, checkouts, Java, build)
    WF->>F: python3 scripts/gitops fetch-issue
    F->>GH: GET /repos/{repo}/issues/N
    GH-->>F: título, corpo, html_url
    F->>F: extrai critérios de aceite e grava task.json
    F->>GH: POST comentário "O agente começou" com o link da execução
```

## 2. A execução completa do agent-runner

Esta é a visão da orquestração. O `RunCodingAgentUseCase` não sabe nada de Spring AI, Docker
ou JSON: ele só conversa com portas.

```mermaid
sequenceDiagram
    autonumber
    participant CLI as AgentCommandLineRunner
    participant UC as RunCodingAgentUseCase
    participant TR as JsonTaskReader
    participant PF as PromptFactory
    participant L as SpringAiAgentLoop
    participant V as VerificationRunner
    participant O as AgentOutcome
    participant RW as JsonResultWriter
    CLI->>UC: execute()
    UC->>TR: read()
    TR-->>UC: AgentTask
    UC->>PF: systemPrompt() e taskPrompt(task)
    Note over PF: lê AGENTS.md e a descrição do ambiente
    PF-->>UC: os dois prompts
    UC->>L: run(systemPrompt, taskPrompt)
    Note over L: o laço (casos 3 a 11)
    L-->>UC: LoopOutcome
    UC->>V: run()
    V-->>UC: Verification
    UC->>O: decide(loop, verification, maxIterations)
    O-->>UC: status e resumo
    UC->>RW: write(AgentResult)
    UC-->>CLI: AgentResult
    CLI->>CLI: código de saída 1 se ERROR, senão 0
```

## 3. Uma iteração com ferramenta

É o coração do agente: **pensa, age, observa**. O modelo nunca executa nada. Ele pede, e o
agent-runner executa no container e devolve o código de saída e o final da saída.

```mermaid
sequenceDiagram
    autonumber
    participant L as SpringAiAgentLoop
    participant M as Ollama
    participant TM as ToolCallingManager
    participant T as CodingTools
    participant U as ToolUsage
    participant P as CommandPolicy
    participant E as DockerCommandExecutor
    participant C as Container
    participant J as JsonlExecutionJournal
    L->>M: histórico + ferramentas disponíveis
    M-->>L: tool call run_command("mvn -o -q test")
    L->>J: model_turn
    L->>TM: executeToolCalls(prompt, response)
    TM->>T: runCommand("mvn -o -q test")
    T->>U: tryStartCall()
    U-->>T: true (ainda há orçamento)
    T->>P: findViolation(comando)
    P-->>T: vazio (permitido)
    T->>E: run(comando, 300 s)
    E->>C: docker exec timeout 300 bash -lc "mvn -o -q test"
    C-->>E: exit 1 e a saída do Maven
    E-->>T: CommandResult
    T->>J: command_result e tool_call
    T-->>TM: "exit_code=1 ... [ERROR] Tests run: 3, Failures: 1"
    TM-->>L: histórico com o resultado da ferramenta
    L->>M: próxima iteração, agora com o erro no histórico
```

Na próxima volta, o modelo lê `exit_code=1` e o teste que falhou, e decide o que fazer: ler o
arquivo, corrigir com `replace_in_file` e rodar o teste de novo.

## 4. O modelo chama finish

`finish` é uma ferramenta com `returnDirect = true`. Ao ser chamada, ela registra o
`FinishSignal`, e o laço vê o sinal e para.

```mermaid
sequenceDiagram
    autonumber
    participant L as SpringAiAgentLoop
    participant M as Ollama
    participant TM as ToolCallingManager
    participant T as CodingTools
    participant J as JsonlExecutionJournal
    L->>M: histórico
    M-->>L: tool call finish(status "done", resumo)
    L->>TM: executeToolCalls
    TM->>T: finish("done", resumo)
    T->>T: guarda FinishSignal(done = true, resumo)
    T->>J: finish
    T-->>TM: "FINISHED: done"
    L->>T: finishSignal()
    T-->>L: presente
    L-->>L: LoopOutcome.finished(iteração, sinal)
```

## 5. Ferramenta devolve erro e o modelo corrige

Erros das ferramentas **não derrubam o agente**. Eles viram texto (`ERRO: ...`) que o modelo lê.

```mermaid
sequenceDiagram
    autonumber
    participant M as Ollama
    participant T as CodingTools
    participant W as LocalWorkspace
    M->>T: replace_in_file("App.java", trecho que não existe, novo)
    T->>W: replaceInFile(...)
    W-->>T: IllegalArgumentException "Trecho não encontrado"
    T-->>M: "ERRO: Trecho não encontrado em App.java. Leia o arquivo de novo..."
    M->>T: read_file("App.java")
    T-->>M: conteúdo com números de linha
    M->>T: replace_in_file("App.java", trecho correto, novo)
    T-->>M: "OK: trecho substituído em App.java."
```

Outro erro comum é o trecho aparecer mais de uma vez. A mensagem pede mais contexto para
tornar o trecho único. Essa regra evita edições ambíguas.

## 6. Comando bloqueado pela política

A `CommandPolicy` não é a barreira principal (essa é o container sem rede e sem token). Ela
existe para dar ao modelo uma resposta **clara** quando ele tenta fazer o papel da pipeline.

```mermaid
sequenceDiagram
    autonumber
    participant M as Ollama
    participant T as CodingTools
    participant P as CommandPolicy
    participant E as DockerCommandExecutor
    M->>T: run_command("git push origin main")
    T->>P: findViolation("git push origin main")
    P-->>T: "Operações de push ... são feitas pelo workflow"
    T-->>M: "BLOQUEADO: Operações de push ... são feitas pelo workflow"
    Note over E: o executor nunca é chamado
```

Comandos bloqueados: `git push|remote|config --global|credential`,
`git commit|checkout -b|switch -c|reset --hard|rebase`, `sudo`, `rm -rf /`, `rm -rf ~` e
comandos de sistema como `shutdown` e `mkfs`.

## 7. Tentativa de sair do repositório

Todo caminho que vem do modelo passa pelo `SafePathResolver`.

```mermaid
sequenceDiagram
    autonumber
    participant M as Ollama
    participant T as CodingTools
    participant W as LocalWorkspace
    participant R as SafePathResolver
    M->>T: read_file("../../etc/passwd")
    T->>W: readFile(...)
    W->>R: resolve("../../etc/passwd")
    R->>R: normaliza e confere se continua dentro da raiz
    R-->>W: SecurityException "Caminho fora do workspace"
    W-->>T: SecurityException
    T-->>M: "ERRO: Caminho fora do workspace: ../../etc/passwd"
```

O mesmo acontece com um link simbólico que aponta para fora e com qualquer caminho dentro de
`.git/`.

## 8. Modelo responde sem ferramenta

Modelos pequenos às vezes respondem só com texto ("vou analisar o projeto..."). Eu dou
**dois lembretes** antes de desistir.

```mermaid
sequenceDiagram
    autonumber
    participant L as SpringAiAgentLoop
    participant M as Ollama
    L->>M: histórico
    M-->>L: só texto, sem tool call
    L->>L: lembretes = 1
    L->>M: histórico + "Você respondeu sem chamar ferramentas..."
    alt o modelo volta a usar ferramentas
        M-->>L: tool call
        Note over L: segue normalmente (caso 3)
    else continua só com texto
        M-->>L: só texto
        L->>L: lembretes = 2
        L->>M: histórico + lembrete
        M-->>L: só texto de novo
        L-->>L: LoopOutcome.stoppedWithoutTools
        Note over L: status final INCOMPLETE
    end
```

## 9. Limite de chamadas de ferramenta

Além do limite de iterações, existe um limite de **chamadas de ferramenta** (padrão 90),
porque o modelo pode pedir várias ferramentas numa mesma iteração.

```mermaid
sequenceDiagram
    autonumber
    participant M as Ollama
    participant T as CodingTools
    participant U as ToolUsage
    participant J as JsonlExecutionJournal
    M->>T: list_files(".")
    T->>U: tryStartCall()
    U-->>T: false (91 de 90)
    T->>J: tool_budget_exceeded
    T-->>M: "LIMITE DE CHAMADAS ATINGIDO (90). Chame finish agora..."
    M->>T: finish("incomplete", "faltou ...")
    Note over T: finish não passa pelo orçamento: sempre funciona
```

## 10. Limite de iterações

```mermaid
sequenceDiagram
    autonumber
    participant L as SpringAiAgentLoop
    participant M as Ollama
    loop enquanto iteração < 30
        L->>M: histórico
        M-->>L: tool calls (sem finish)
        L->>L: executa as ferramentas
    end
    L-->>L: LoopOutcome.iterationLimitReached(30)
    Note over L: status final BUDGET_EXCEEDED
```

## 11. Falha do modelo

Se a chamada ao modelo lança uma exceção (Ollama fora do ar, timeout, resposta inválida), o
laço registra o erro e devolve `ERROR`. A verificação **roda mesmo assim**, porque o agente
pode ter deixado arquivos alterados.

```mermaid
sequenceDiagram
    autonumber
    participant UC as RunCodingAgentUseCase
    participant L as SpringAiAgentLoop
    participant M as Ollama
    participant J as JsonlExecutionJournal
    participant V as VerificationRunner
    participant CLI as AgentCommandLineRunner
    UC->>L: run(...)
    L->>M: chamada HTTP
    Note over M: o Spring AI tenta de novo algumas vezes antes de desistir
    M--xL: ResourceAccessException
    L->>J: error
    L-->>UC: LoopOutcome.failed(iteração, mensagem)
    UC->>V: run()
    V-->>UC: Verification
    UC-->>CLI: AgentResult com status ERROR
    CLI->>CLI: código de saída 1
    Note over CLI: o step 9 falha e o step 10 (publish) é pulado
```

## 12. Verificação independente

Depois do laço, eu **não confio** no que o modelo disse. O `VerificationRunner` roda o
`.agent/verify.sh` do repositório alvo, e esse resultado vai para a descrição do PR.

```mermaid
sequenceDiagram
    autonumber
    participant V as VerificationRunner
    participant W as LocalWorkspace
    participant E as DockerCommandExecutor
    participant J as JsonlExecutionJournal
    alt AGENT_VERIFY_COMMAND configurado
        V->>V: usa o comando configurado
    else existe .agent/verify.sh
        V->>W: isFile(".agent/verify.sh")
        W-->>V: true
        V->>V: usa "bash .agent/verify.sh"
    else nenhum dos dois
        V-->>V: Verification.skipped
    end
    V->>E: run(comando, 1200 s)
    E-->>V: CommandResult
    V->>J: verification
    V-->>V: Verification.executed(comando, exitCode, últimos 3000 caracteres)
```

## 13. Publicação com PR

O caminho feliz do step 10.

```mermaid
sequenceDiagram
    autonumber
    participant P as PublishCommand
    participant G as GitRepository
    participant R as AgentResult
    participant D as PullRequestDraft
    participant GH as API do GitHub
    P->>R: load(OUTPUT_DIR)
    P->>G: stage_all_changes()
    P->>G: staged_files()
    G-->>P: ["src/main/java/.../App.java"]
    P->>G: staged_diff()
    P->>P: grava changes.patch
    P->>G: commit("#N: título", resumo)
    P->>G: push(work_branch, token)
    Note over G: token em http.extraheader, só neste comando
    P->>D: title() e body()
    D-->>P: "[WIP] " se status != COMPLETED, "Closes #N"
    P->>GH: POST /pulls (draft = true)
    GH-->>P: número e URL do PR
    P->>R: record_publication(PR_CREATED, ...)
    P->>GH: comentário na issue com o link do PR
```

## 14. Publicação sem alterações

```mermaid
sequenceDiagram
    autonumber
    participant P as PublishCommand
    participant G as GitRepository
    participant R as AgentResult
    participant GH as API do GitHub
    P->>G: stage_all_changes()
    P->>G: staged_files()
    G-->>P: lista vazia
    P->>R: record_publication(NO_CHANGES)
    P->>GH: comentário "terminou sem alterar arquivos" com o resumo
```

## 15. Publicação sem PR (status ERROR)

É uma proteção: se o `result.json` diz `ERROR` (ou nem existe), eu guardo o diff mas não
abro PR. O mesmo vale para qualquer status diferente de `COMPLETED` quando
`OPEN_PR_ON_FAILURE=false`.

```mermaid
sequenceDiagram
    autonumber
    participant P as PublishCommand
    participant G as GitRepository
    participant R as AgentResult
    participant GH as API do GitHub
    P->>G: staged_files()
    G-->>P: arquivos alterados
    P->>P: grava changes.patch
    P->>R: is_error()
    R-->>P: true
    P->>R: record_publication(SKIPPED)
    P->>GH: comentário "não abriu PR, o diff está no artefato"
    Note over G: nada de commit nem push
```

## 16. Falha ao abrir o PR (HTTP 403)

Aconteceu comigo na primeira execução real. O push funciona, mas a criação do PR é recusada
porque o repositório não permite que o `GITHUB_TOKEN` abra PRs.

```mermaid
sequenceDiagram
    autonumber
    participant P as PublishCommand
    participant G as GitRepository
    participant GH as API do GitHub
    participant M as gitops __main__
    P->>G: commit e push
    G-->>P: branch criada no GitHub
    P->>GH: POST /pulls
    GH-->>P: 403 "GitHub Actions is not permitted to create or approve pull requests"
    P--xM: GitHubApiError
    M->>M: imprime o erro e sai com código 1
    Note over GH: a branch fica no GitHub e dá para abrir o PR à mão
```

Como resolver: *Settings → Actions → General → Workflow permissions → Allow GitHub Actions to
create and approve pull requests*, no repositório alvo.
