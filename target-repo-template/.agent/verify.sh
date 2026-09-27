#!/usr/bin/env bash
# Verificação independente, executada pela pipeline DEPOIS que o agente termina.
# O resultado vai para a descrição do PR. Mantenha rápido e determinístico.
set -euo pipefail

mvn -B -o -q verify
