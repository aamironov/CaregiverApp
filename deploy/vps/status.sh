#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
env_file="${script_dir}/.env"

if [[ ! -f ${env_file} ]]; then
  echo "Missing ${env_file}; copy .env.example and configure it first." >&2
  exit 1
fi

docker compose --env-file "${env_file}" -f "${script_dir}/compose.yaml" ps
docker compose --env-file "${env_file}" -f "${script_dir}/compose.yaml" logs --tail=100 backend caddy
