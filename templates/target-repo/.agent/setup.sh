#!/usr/bin/env bash
# Roda COM internet, antes do agente começar. Baixe aqui tudo o que o build e os testes precisam,
# porque depois a rede do container é desligada.
set -euo pipefail

mvn -B -q dependency:go-offline
# Garante que plugins de teste também fiquem em cache.
mvn -B -q -DskipTests package || true
