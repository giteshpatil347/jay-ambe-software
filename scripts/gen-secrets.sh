#!/usr/bin/env bash
# Prints fresh secrets to paste into .env. Nothing is written to disk.
set -euo pipefail
echo "DB_PASSWORD=$(openssl rand -base64 24 | tr -d '/+=')"
echo "JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')"
echo "BOOTSTRAP_ADMIN_PASSWORD=$(openssl rand -base64 18 | tr -d '/+=')"
