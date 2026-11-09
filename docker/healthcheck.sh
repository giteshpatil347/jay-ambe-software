#!/bin/sh
# Container health probe for both modes: plain HTTP (default) and built-in HTTPS (SERVER_SSL_ENABLED=true).
# The certificate is not verified: the probe talks to the container itself over loopback.
scheme=http
[ "$SERVER_SSL_ENABLED" = "true" ] && scheme=https
wget -q --no-check-certificate -O - "$scheme://127.0.0.1:${SERVER_PORT:-8080}/actuator/health" | grep -q '"UP"'
