#!/usr/bin/env bash
# Generates a self-signed cert for local HTTPS into certs/ (git-ignored), used by the app's built-in TLS
# (SERVER_SSL_ENABLED=true, see docs/DEPLOYMENT.md). DEV ONLY: in production use a real certificate.
# Optional: extra host names / IPs as arguments, e.g.  scripts/gen-dev-cert.sh DNS:materials.lan IP:192.168.1.20
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p certs
SAN="DNS:localhost,IP:127.0.0.1"
for extra in "$@"; do SAN="$SAN,$extra"; done
openssl req -x509 -nodes -newkey rsa:2048 -days 365 \
  -keyout certs/privkey.pem -out certs/fullchain.pem \
  -subj "/CN=localhost" -addext "subjectAltName=$SAN"
chmod 600 certs/privkey.pem
echo "Wrote certs/fullchain.pem and certs/privkey.pem"
echo "Linux: the container user is uid 10001 and must be able to read the key: sudo chown 10001 certs/privkey.pem (keeps mode 600)"
