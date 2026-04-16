#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout "$DIR/server.key" \
  -out    "$DIR/server.crt" \
  -subj   "/C=US/ST=Dev/L=Local/O=Ticketing/CN=localhost" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"
echo "Self-signed cert written to $DIR/server.{crt,key}"
