#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)

cd "$ROOT_DIR/extension-host"
npm run build

cd "$ROOT_DIR/frontend"
npm run build

cd "$ROOT_DIR/backend"
mvn -DskipTests package

printf 'AI Hub package: %s\n' "$ROOT_DIR/backend/target/ai-hub-0.1.0-SNAPSHOT.jar"
printf 'Extension Host package: %s\n' "$ROOT_DIR/extension-host/dist/ai-hub-extension-host.mjs"
