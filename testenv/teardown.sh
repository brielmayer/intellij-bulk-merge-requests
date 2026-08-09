#!/usr/bin/env bash
# Removes the whole test environment: container, volumes and the generated workspace.
set -euo pipefail
cd "$(dirname "$0")"

docker compose down -v
rm -rf workspace .token
echo "Test environment removed."
