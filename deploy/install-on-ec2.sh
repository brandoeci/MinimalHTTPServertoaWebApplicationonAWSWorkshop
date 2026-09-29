#!/usr/bin/env bash
# Installs minihttp-server on an Amazon Linux 2023 instance as a managed service.
#
# Usage (run it on the instance, from the directory that holds both files):
#   scp -i <key.pem> target/minihttp-server.jar deploy/minihttp-server.service \
#       deploy/install-on-ec2.sh ec2-user@<public-dns>:~
#   ssh -i <key.pem> ec2-user@<public-dns> 'bash ~/install-on-ec2.sh'
#
# No credential, key or account identifier is stored in this file.
set -euo pipefail

APP_DIR=/opt/minihttp
JAR=minihttp-server.jar
UNIT=minihttp-server.service
PORT="${PORT:-35000}"

echo "==> Installing the Java 17 runtime"
sudo dnf install -y java-17-amazon-corretto-headless

echo "==> Copying the artifact to ${APP_DIR}"
sudo mkdir -p "${APP_DIR}"
sudo cp "${JAR}" "${APP_DIR}/${JAR}"
sudo chown -R ec2-user:ec2-user "${APP_DIR}"

echo "==> Installing the systemd unit"
sudo cp "${UNIT}" "/etc/systemd/system/${UNIT}"
sudo systemctl daemon-reload
sudo systemctl enable --now "${UNIT}"

echo "==> Service status"
sudo systemctl status "${UNIT}" --no-pager || true

echo "==> Health check from inside the instance"
# Give the JVM a moment to bind the port before asking.
for attempt in 1 2 3 4 5; do
  if curl -fsS "http://localhost:${PORT}/health"; then
    echo
    echo "==> Ready. Open http://<public-dns>:${PORT}/ from the browser."
    exit 0
  fi
  sleep 2
done

echo "The health service did not answer. Last log lines:" >&2
sudo tail -n 40 /var/log/minihttp/server.log >&2
exit 1
