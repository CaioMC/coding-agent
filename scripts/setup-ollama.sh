#!/usr/bin/env bash
# Garante um Ollama acessível para o agent-runner e exporta OLLAMA_BASE_URL para os próximos passos.
#
#  - Se EXTERNAL_OLLAMA_URL estiver definido (secret OLLAMA_BASE_URL), só usa esse endereço.
#  - Senão, instala o Ollama no runner, sobe o servidor e baixa o modelo.
#    Roda em CPU: serve para demonstrar a POC sem infraestrutura, não para desempenho.
set -euo pipefail

MODEL="${OLLAMA_MODEL:?defina OLLAMA_MODEL}"

if [[ -n "${EXTERNAL_OLLAMA_URL:-}" ]]; then
  echo "==> Usando Ollama externo"
  echo "OLLAMA_BASE_URL=$EXTERNAL_OLLAMA_URL" >> "$GITHUB_ENV"
  exit 0
fi

if ! command -v ollama >/dev/null 2>&1; then
  echo "==> Instalando Ollama no runner"
  curl -fsSL https://ollama.com/install.sh | sh
fi

# O instalador pode registrar um serviço do sistema; paramos para subir com a pasta de modelos
# do usuário (~/.ollama/models), que é a pasta guardada em cache entre execuções.
sudo systemctl stop ollama >/dev/null 2>&1 || true
export OLLAMA_MODELS="$HOME/.ollama/models"
mkdir -p "$OLLAMA_MODELS"
nohup ollama serve > "$RUNNER_TEMP/ollama.log" 2>&1 &

echo "==> Aguardando o Ollama responder"
for _ in $(seq 1 30); do
  curl -fs http://localhost:11434/api/version >/dev/null && break
  sleep 1
done
curl -fs http://localhost:11434/api/version >/dev/null || { cat "$RUNNER_TEMP/ollama.log"; exit 1; }

echo "==> Baixando o modelo $MODEL (usa o cache se já existir)"
ollama pull "$MODEL"

echo "OLLAMA_BASE_URL=http://localhost:11434" >> "$GITHUB_ENV"
echo "==> Ollama pronto com $MODEL"
