# Ambiente onde o agente compila e testa ESTE repositório.
# Exemplo para um projeto Java 21 com Maven. Ajuste para a stack do seu projeto.
FROM maven:3.9-eclipse-temurin-21

# Ferramentas úteis para o agente inspecionar o projeto.
RUN apt-get update \
    && apt-get install -y --no-install-recommends git ripgrep procps \
    && rm -rf /var/lib/apt/lists/*
