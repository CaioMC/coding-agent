# Instruções para o agente de codificação

Este arquivo é lido pelo agente no início de cada tarefa. Escreva como escreveria para
alguém que entrou no time hoje: curto, concreto e com os comandos exatos.

## Stack
- Java 17, Spring Boot 3, Maven.
- Testes com JUnit 5 e AssertJ em `src/test/java`.

## Comandos (sem internet: use sempre `-o`)
- Compilar: `mvn -B -o -q compile`
- Rodar todos os testes: `mvn -B -o -q test`
- Rodar um teste: `mvn -B -o -q test -Dtest=NomeDoTeste`

## Convenções
- Pacotes em `com.empresa.<modulo>`; controllers em `api`, regras em `service`, acesso a dados em `repository`.
- Novas regras de negócio precisam de teste unitário.
- Não altere `pom.xml` para adicionar dependências: o ambiente não tem internet. Se for indispensável,
  explique no resumo final e termine com status `incomplete`.

## Não mexa
- `src/main/resources/application-prod.yml`
- Pasta `infra/`
