#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
env_file="${script_dir}/.env"
backup_dir="${BACKUP_DIR:-${script_dir}/backups}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
archive="${backup_dir}/carebinder-${timestamp}.tar.gz"

if [[ ! -f ${env_file} ]]; then
  echo "Missing ${env_file}." >&2
  exit 1
fi

mkdir -p "${backup_dir}"
chmod 700 "${backup_dir}"

compose=(docker compose --env-file "${env_file}" -f "${script_dir}/compose.yaml")
"${compose[@]}" stop backend
restart_backend() { "${compose[@]}" start backend >/dev/null; }
trap restart_backend EXIT

docker run --rm \
  --volume carebinder_carebinder_data:/source:ro \
  --volume "${backup_dir}:/backup" \
  alpine:3 sh -c "tar -czf /backup/$(basename "${archive}") -C /source ."

chmod 600 "${archive}"
echo "Created consistent backup: ${archive}"
