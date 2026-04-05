#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="${SERVICE_NAME:-stream-engine}"
INSTALL_DIR="${INSTALL_DIR:-/opt/stream-engine}"
CONFIG_DIR="${CONFIG_DIR:-/etc/stream-engine}"
UNIT_PATH="${UNIT_PATH:-/etc/systemd/system/${SERVICE_NAME}.service}"
ENV_PATH="${ENV_PATH:-${CONFIG_DIR}/stream-engine.env}"
JAR_PATH="${JAR_PATH:-${INSTALL_DIR}/stream-engine.jar}"

fatal() {
  echo "ERROR: $*" >&2
  exit 1
}

warn() {
  echo "WARN: $*" >&2
}

require_file() {
  local path="$1"
  [[ -f "$path" ]] || fatal "Missing file: $path"
}

parse_env_value() {
  local key="$1"
  local file="$2"
  awk -F= -v k="$key" '$1==k {sub(/^[[:space:]]+/, "", $2); print $2}' "$file" | tail -n 1
}

echo "Running preflight checks..."

require_file "$UNIT_PATH"
require_file "$ENV_PATH"
require_file "$JAR_PATH"

if ! systemctl cat "$SERVICE_NAME" >/dev/null 2>&1; then
  warn "systemd does not yet know service '${SERVICE_NAME}' (run systemctl daemon-reload after install)."
fi

# shellcheck disable=SC1090
set +u
source "$ENV_PATH"
set -u

TLS_ENABLED="${STREAM_ENGINE_TLS_ENABLED:-false}"
TLS_CLIENT_AUTH="${STREAM_ENGINE_TLS_CLIENT_AUTH:-none}"
KEY_STORE="${STREAM_ENGINE_TLS_KEY_STORE:-}"
KEY_STORE_PASSWORD="${STREAM_ENGINE_TLS_KEY_STORE_PASSWORD:-}"
TRUST_STORE="${STREAM_ENGINE_TLS_TRUST_STORE:-}"
TRUST_STORE_PASSWORD="${STREAM_ENGINE_TLS_TRUST_STORE_PASSWORD:-}"
RED5_ENFORCE_HTTPS="${RED5_ENFORCE_HTTPS:-true}"
RED5_BASE_URL="${RED5_BASE_URL:-}"
RECASTLY_ENFORCE_HTTPS="${RECASTLY_ENFORCE_HTTPS:-true}"
RECASTLY_BASE_URL="${RECASTLY_BASE_URL:-}"

if [[ "$TLS_ENABLED" == "true" ]]; then
  [[ -n "$KEY_STORE" ]] || fatal "STREAM_ENGINE_TLS_ENABLED=true but STREAM_ENGINE_TLS_KEY_STORE is empty"
  [[ -n "$KEY_STORE_PASSWORD" ]] || fatal "STREAM_ENGINE_TLS_ENABLED=true but STREAM_ENGINE_TLS_KEY_STORE_PASSWORD is empty"
  require_file "$KEY_STORE"
  if [[ "$TLS_CLIENT_AUTH" == "need" || "$TLS_CLIENT_AUTH" == "want" ]]; then
    [[ -n "$TRUST_STORE" ]] || fatal "client-auth=${TLS_CLIENT_AUTH} but STREAM_ENGINE_TLS_TRUST_STORE is empty"
    [[ -n "$TRUST_STORE_PASSWORD" ]] || fatal "client-auth=${TLS_CLIENT_AUTH} but STREAM_ENGINE_TLS_TRUST_STORE_PASSWORD is empty"
    require_file "$TRUST_STORE"
  fi
else
  warn "STREAM_ENGINE_TLS_ENABLED is false."
fi

if [[ "$RED5_ENFORCE_HTTPS" == "true" && -n "$RED5_BASE_URL" && "$RED5_BASE_URL" != https://* ]]; then
  fatal "RED5_ENFORCE_HTTPS=true but RED5_BASE_URL is not https:// (${RED5_BASE_URL})"
fi

if [[ "$RECASTLY_ENFORCE_HTTPS" == "true" && -n "$RECASTLY_BASE_URL" && "$RECASTLY_BASE_URL" != https://* ]]; then
  fatal "RECASTLY_ENFORCE_HTTPS=true but RECASTLY_BASE_URL is not https:// (${RECASTLY_BASE_URL})"
fi

if command -v java >/dev/null 2>&1; then
  java -version >/dev/null 2>&1 || fatal "java exists but failed to run"
else
  fatal "java not found in PATH"
fi

if ! command -v curl >/dev/null 2>&1; then
  warn "curl not found (deploy/update.sh health check uses curl)."
fi

if ! command -v systemctl >/dev/null 2>&1; then
  fatal "systemctl not found; this script expects systemd."
fi

echo "Preflight OK."
