#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
PACKAGE_ROOT=$ROOT_DIR/dist/ai-hub-deploy
ARCHIVE=$ROOT_DIR/dist/ai-hub-deploy.tar.gz

"$ROOT_DIR/deploy/build.sh"

rm -rf "$PACKAGE_ROOT"
mkdir -p "$PACKAGE_ROOT"
cp "$ROOT_DIR/backend/target/ai-hub-0.1.0-SNAPSHOT.jar" "$PACKAGE_ROOT/ai-hub.jar"
cp "$ROOT_DIR/extension-host/dist/ai-hub-extension-host.mjs" "$PACKAGE_ROOT/ai-hub-extension-host.mjs"
cp "$ROOT_DIR/deploy/server/start.sh" "$PACKAGE_ROOT/start.sh"
cp "$ROOT_DIR/deploy/server/stop.sh" "$PACKAGE_ROOT/stop.sh"
cp "$ROOT_DIR/deploy/server/restart.sh" "$PACKAGE_ROOT/restart.sh"
cp "$ROOT_DIR/deploy/server/ai-hub.service" "$PACKAGE_ROOT/ai-hub.service"
cp "$ROOT_DIR/deploy/server/runtime.env.example" "$PACKAGE_ROOT/runtime.env.example"
chmod 750 "$PACKAGE_ROOT/start.sh" "$PACKAGE_ROOT/stop.sh" "$PACKAGE_ROOT/restart.sh"

(
  cd "$PACKAGE_ROOT"
  shasum -a 256 ai-hub.jar ai-hub-extension-host.mjs > checksums.sha256
)

tar -C "$ROOT_DIR/dist" -czf "$ARCHIVE" ai-hub-deploy
printf 'Deployment archive: %s\n' "$ARCHIVE"
