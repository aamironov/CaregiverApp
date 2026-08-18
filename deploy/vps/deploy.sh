#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd -- "${script_dir}/../.." && pwd)"
env_file="${script_dir}/.env"
compose_file="${script_dir}/compose.yaml"

if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
  echo "Docker with the Compose plugin is required. On Ubuntu, run bootstrap-ubuntu.sh first." >&2
  exit 1
fi

if [[ ! -f ${env_file} ]]; then
  cp "${script_dir}/.env.example" "${env_file}"
  chmod 600 "${env_file}"
  echo "Created ${env_file}. Fill in APP_DOMAIN and ACME_EMAIL, then rerun this script." >&2
  exit 1
fi

chmod 600 "${env_file}"
set -a
# shellcheck disable=SC1090
source "${env_file}"
set +a

if [[ -z ${APP_DOMAIN:-} || ${APP_DOMAIN} == "care.example.com" || ${APP_DOMAIN} == *"://"* ]]; then
  echo "APP_DOMAIN must be a real DNS name without http:// or https://." >&2
  exit 1
fi
if [[ -z ${ACME_EMAIL:-} || ${ACME_EMAIL} == "admin@example.com" ]]; then
  echo "ACME_EMAIL must be set to a real contact address." >&2
  exit 1
fi

cd "${project_dir}"
docker compose --env-file "${env_file}" -f "${compose_file}" config --quiet
docker compose --env-file "${env_file}" -f "${compose_file}" build --pull backend
docker compose --env-file "${env_file}" -f "${compose_file}" up -d --remove-orphans

echo "Waiting for https://${APP_DOMAIN}/v1/health ..."
for attempt in {1..36}; do
  if response="$(curl -fsS --max-time 10 "https://${APP_DOMAIN}/v1/health" 2>/dev/null)" && grep -q '"status":"ok"' <<<"${response}"; then
    echo "CareBinder is live at https://${APP_DOMAIN}"
    exit 0
  fi
  sleep 5
done

echo "Deployment started, but the public health check did not pass within 3 minutes." >&2
echo "Check DNS and firewall rules, then run: ${script_dir}/status.sh" >&2
exit 1
