#!/usr/bin/env bash
# Prepara o ambiente isolado onde os comandos do agente vão rodar.
#
#  1. Usa a imagem do projeto (.agent/Dockerfile no repositório alvo) ou uma imagem padrão.
#  2. Sobe o container com o repositório montado em /workspace, com o mesmo usuário do host
#     (assim os arquivos criados lá dentro continuam editáveis e "commitáveis" fora).
#  3. Roda .agent/setup.sh COM internet, para baixar dependências.
#  4. Desconecta a rede. A partir daqui, o código gerado pelo modelo roda sem internet.
#
# Variáveis: WORKSPACE_DIR, CONTAINER_NAME, SANDBOX_DEFAULT_IMAGE, SANDBOX_NETWORK (off|on)
set -euo pipefail

: "${WORKSPACE_DIR:?defina WORKSPACE_DIR}"
: "${CONTAINER_NAME:?defina CONTAINER_NAME}"
# No Azure, uma variável não definida chega como o texto literal "$(NOME)": tratamos como vazia.
[[ "${SANDBOX_DEFAULT_IMAGE:-}" == \$\(* ]] && SANDBOX_DEFAULT_IMAGE=""
DEFAULT_IMAGE="${SANDBOX_DEFAULT_IMAGE:-maven:3.9-eclipse-temurin-17}"
NETWORK_MODE="${SANDBOX_NETWORK:-off}"
SETUP_TIMEOUT="${SANDBOX_SETUP_TIMEOUT:-1800}"

if [[ -f "$WORKSPACE_DIR/.agent/Dockerfile" ]]; then
  IMAGE="agent-sandbox-image:${CONTAINER_NAME}"
  echo "==> Construindo imagem a partir de .agent/Dockerfile"
  docker build -t "$IMAGE" -f "$WORKSPACE_DIR/.agent/Dockerfile" "$WORKSPACE_DIR/.agent"
else
  IMAGE="$DEFAULT_IMAGE"
  echo "==> Repositório sem .agent/Dockerfile; usando imagem padrão $IMAGE"
  docker pull -q "$IMAGE"
fi

echo "==> Subindo container $CONTAINER_NAME"
docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
docker run -d --name "$CONTAINER_NAME" \
  --user "$(id -u):$(id -g)" \
  --cpus "${SANDBOX_CPUS:-2}" --memory "${SANDBOX_MEMORY:-6g}" --pids-limit 1024 \
  -e HOME=/tmp/home \
  -e MAVEN_OPTS="-Duser.home=/tmp/home" \
  -e GRADLE_USER_HOME=/tmp/home/.gradle \
  -e npm_config_cache=/tmp/home/.npm \
  -v "$WORKSPACE_DIR:/workspace" \
  -w /workspace \
  --entrypoint sleep \
  "$IMAGE" infinity >/dev/null

docker exec "$CONTAINER_NAME" mkdir -p /tmp/home

for tool in bash timeout; do
  if ! docker exec "$CONTAINER_NAME" sh -c "command -v $tool" >/dev/null 2>&1; then
    echo "ERRO: a imagem precisa ter '$tool' instalado." >&2
    exit 1
  fi
done

if [[ -f "$WORKSPACE_DIR/.agent/setup.sh" ]]; then
  echo "==> Rodando .agent/setup.sh (com internet)"
  docker exec -w /workspace "$CONTAINER_NAME" timeout "$SETUP_TIMEOUT" bash -lc "bash .agent/setup.sh"
else
  echo "==> Repositório sem .agent/setup.sh; nenhuma dependência pré-instalada"
fi

if [[ "$NETWORK_MODE" == "off" ]]; then
  echo "==> Desconectando a rede do container"
  docker network disconnect bridge "$CONTAINER_NAME"
else
  echo "==> ATENÇÃO: container mantido COM rede (SANDBOX_NETWORK=$NETWORK_MODE)"
fi

echo "==> Sandbox pronto"
