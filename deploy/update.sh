#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="${SERVICE_NAME:-stream-engine}"
SERVICE_USER="${SERVICE_USER:-stream-engine}"
SERVICE_GROUP="${SERVICE_GROUP:-stream-engine}"
INSTALL_DIR="${INSTALL_DIR:-/opt/stream-engine}"
JAR_DEST="${INSTALL_DIR}/stream-engine.jar"
BACKUP_DIR="${INSTALL_DIR}/releases"
HEALTH_URL="${HEALTH_URL:-https://127.0.0.1:8080/actuator/health}"
HEALTH_TIMEOUT_SEC="${HEALTH_TIMEOUT_SEC:-60}"
CURL_INSECURE_LOCAL="${CURL_INSECURE_LOCAL:-true}"
NEW_JAR="${1:-}"

if [[ $EUID -ne 0 ]]; then
  echo "Run as root (or via sudo)." >&2
  exit 1
fi

if [[ -z "$NEW_JAR" ]]; then
  echo "Usage: sudo bash deploy/update.sh /path/to/new.jar" >&2
  exit 1
fi

if [[ ! -f "$NEW_JAR" ]]; then
  echo "Jar not found: ${NEW_JAR}" >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"

timestamp="$(date +%Y%m%d%H%M%S)"
backup_jar="${BACKUP_DIR}/stream-engine-${timestamp}.jar"

if [[ -f "$JAR_DEST" ]]; then
  cp "$JAR_DEST" "$backup_jar"
  echo "Backed up current jar to ${backup_jar}"
else
  echo "No existing jar at ${JAR_DEST}; proceeding with first deployment."
fi

cp "$NEW_JAR" "$JAR_DEST"
chown "$SERVICE_USER:$SERVICE_GROUP" "$JAR_DEST"
chmod 750 "$JAR_DEST"
echo "Installed new jar at ${JAR_DEST}"

systemctl restart "$SERVICE_NAME"
echo "Restarted ${SERVICE_NAME}, waiting for health..."

curl_flags=(--silent --show-error --max-time 5 --output /dev/null --write-out "%{http_code}")
if [[ "$CURL_INSECURE_LOCAL" == "true" ]]; then
  curl_flags+=(--insecure)
fi

deadline=$((SECONDS + HEALTH_TIMEOUT_SEC))
healthy=false
while (( SECONDS < deadline )); do
  set +e
  code="$(curl "${curl_flags[@]}" "$HEALTH_URL")"
  rc=$?
  set -e
  if [[ $rc -eq 0 && "$code" == "200" ]]; then
    healthy=true
    break
  fi
  sleep 2
done

if [[ "$healthy" == "true" ]]; then
  echo "Deployment successful. Health check passed (${HEALTH_URL})."
  exit 0
fi

echo "Health check failed; rolling back..."
if [[ -f "$backup_jar" ]]; then
  cp "$backup_jar" "$JAR_DEST"
  chown "$SERVICE_USER:$SERVICE_GROUP" "$JAR_DEST"
  chmod 750 "$JAR_DEST"
  systemctl restart "$SERVICE_NAME"
  echo "Rollback complete. Restored ${backup_jar}."
else
  echo "No backup jar available to roll back." >&2
fi

systemctl status "$SERVICE_NAME" --no-pager || true
exit 1
