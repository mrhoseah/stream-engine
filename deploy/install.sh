#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="stream-engine"
SERVICE_USER="${SERVICE_USER:-stream-engine}"
SERVICE_GROUP="${SERVICE_GROUP:-stream-engine}"
INSTALL_DIR="${INSTALL_DIR:-/opt/stream-engine}"
CONFIG_DIR="${CONFIG_DIR:-/etc/stream-engine}"
LOG_DIR="${LOG_DIR:-/var/log/stream-engine}"
UNIT_SRC="deploy/stream-engine.service"
ENV_TEMPLATE_SRC="deploy/stream-engine.env.production.example"
UNIT_DEST="/etc/systemd/system/${SERVICE_NAME}.service"
ENV_DEST="${CONFIG_DIR}/stream-engine.env"
JAR_DEST="${INSTALL_DIR}/stream-engine.jar"
JAR_SRC="${1:-}"

if [[ $EUID -ne 0 ]]; then
  echo "Run as root (or via sudo)." >&2
  exit 1
fi

if [[ ! -f "$UNIT_SRC" ]]; then
  echo "Missing ${UNIT_SRC}. Run from repository root." >&2
  exit 1
fi

if [[ ! -f "$ENV_TEMPLATE_SRC" ]]; then
  echo "Missing ${ENV_TEMPLATE_SRC}. Run from repository root." >&2
  exit 1
fi

if ! getent group "$SERVICE_GROUP" >/dev/null 2>&1; then
  groupadd --system "$SERVICE_GROUP"
fi

if ! id -u "$SERVICE_USER" >/dev/null 2>&1; then
  useradd --system --gid "$SERVICE_GROUP" --home-dir "$INSTALL_DIR" --shell /usr/sbin/nologin "$SERVICE_USER"
fi

mkdir -p "$INSTALL_DIR" "$CONFIG_DIR" "$LOG_DIR"
chown -R "$SERVICE_USER:$SERVICE_GROUP" "$INSTALL_DIR" "$LOG_DIR"

cp "$UNIT_SRC" "$UNIT_DEST"
if [[ -f "$ENV_DEST" ]]; then
  echo "Preserving existing env file: ${ENV_DEST}"
else
  cp "$ENV_TEMPLATE_SRC" "$ENV_DEST"
  chown "root:${SERVICE_GROUP}" "$ENV_DEST"
  chmod 640 "$ENV_DEST"
  echo "Installed env template to ${ENV_DEST}"
fi

if [[ -n "$JAR_SRC" ]]; then
  if [[ ! -f "$JAR_SRC" ]]; then
    echo "Jar not found: ${JAR_SRC}" >&2
    exit 1
  fi
  cp "$JAR_SRC" "$JAR_DEST"
  chown "$SERVICE_USER:$SERVICE_GROUP" "$JAR_DEST"
  chmod 750 "$JAR_DEST"
  echo "Installed jar to ${JAR_DEST}"
else
  echo "No jar path provided. Place your built jar at ${JAR_DEST} before starting."
fi

systemctl daemon-reload
systemctl enable "$SERVICE_NAME"
echo "Install complete."
echo "Next:"
echo "  1) Edit ${ENV_DEST} with real secrets and URLs"
echo "  2) Ensure jar exists at ${JAR_DEST}"
echo "  3) Start service: systemctl start ${SERVICE_NAME}"
echo "  4) Check status: systemctl status ${SERVICE_NAME}"
