#!/usr/bin/env bash
set -euo pipefail

BASE_DIR=$(cd "$(dirname "$0")" && pwd)
JAR_FILE=${AI_HUB_JAR_FILE:-$BASE_DIR/ai-hub.jar}
EXTENSION_FILE=${AI_HUB_EXTENSION_FILE:-$BASE_DIR/ai-hub-extension-host.mjs}
ENV_FILE=${AI_HUB_ENV_FILE:-$BASE_DIR/runtime.env}
PID_FILE=$BASE_DIR/ai-hub.pid
EXTENSION_PID_FILE=$BASE_DIR/ai-hub-extension-host.pid
LOG_DIR=$BASE_DIR/logs
LOG_FILE=$LOG_DIR/ai-hub.log
EXTENSION_LOG_FILE=$LOG_DIR/ai-hub-extension-host.log

if [[ ! -f "$JAR_FILE" ]]; then
  printf 'JAR not found: %s\n' "$JAR_FILE" >&2
  exit 1
fi
if [[ ! -f "$EXTENSION_FILE" ]]; then
  printf 'Extension Host not found: %s\n' "$EXTENSION_FILE" >&2
  exit 1
fi
if [[ ! -f "$ENV_FILE" ]]; then
  printf 'Runtime environment not found: %s\n' "$ENV_FILE" >&2
  exit 1
fi
if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null \
    && [[ -f "$EXTENSION_PID_FILE" ]] && kill -0 "$(cat "$EXTENSION_PID_FILE")" 2>/dev/null; then
  printf 'AI Hub is already running, pid=%s\n' "$(cat "$PID_FILE")"
  exit 0
fi

if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  printf 'AI Hub is in a partial running state; stop it before starting\n' >&2
  exit 1
fi
if [[ -f "$EXTENSION_PID_FILE" ]] && kill -0 "$(cat "$EXTENSION_PID_FILE")" 2>/dev/null; then
  printf 'AI Hub is in a partial running state; stop it before starting\n' >&2
  exit 1
fi

set -a
# shellcheck source=/dev/null
source "$ENV_FILE"
set +a
export SPRING_PROFILES_ACTIVE=prod

RELEASE_ARTIFACT_ROOT=${AI_HUB_RELEASE_ARTIFACT_ROOT:-$BASE_DIR/data/client-releases}
mkdir -p "$LOG_DIR" "$RELEASE_ARTIFACT_ROOT"
chmod 700 "$RELEASE_ARTIFACT_ROOT"
nohup "${NODE_BIN:-node}" "$EXTENSION_FILE" >> "$EXTENSION_LOG_FILE" 2>&1 &
extension_pid=$!
printf '%s\n' "$extension_pid" > "$EXTENSION_PID_FILE"

for _ in {1..30}; do
  if ! kill -0 "$extension_pid" 2>/dev/null; then
    printf 'Extension Host exited during startup. See %s\n' "$EXTENSION_LOG_FILE" >&2
    exit 1
  fi
  if curl --fail --silent --max-time 2 "http://127.0.0.1:${AI_HUB_EXTENSION_HOST_PORT:-8091}/health" >/dev/null; then
    break
  fi
  sleep 1
done
if ! curl --fail --silent --max-time 2 "http://127.0.0.1:${AI_HUB_EXTENSION_HOST_PORT:-8091}/health" >/dev/null; then
  kill "$extension_pid" 2>/dev/null || true
  rm -f "$EXTENSION_PID_FILE"
  printf 'Extension Host startup timed out. See %s\n' "$EXTENSION_LOG_FILE" >&2
  exit 1
fi

nohup "${JAVA_BIN:-java}" ${JAVA_OPTS:--Xms256m -Xmx768m} -jar "$JAR_FILE" >> "$LOG_FILE" 2>&1 &
pid=$!
printf '%s\n' "$pid" > "$PID_FILE"

for _ in {1..60}; do
  if ! kill -0 "$pid" 2>/dev/null; then
    kill "$extension_pid" 2>/dev/null || true
    rm -f "$EXTENSION_PID_FILE"
    printf 'AI Hub exited during startup. See %s\n' "$LOG_FILE" >&2
    exit 1
  fi
  if curl --fail --silent --max-time 2 "http://127.0.0.1:${SERVER_PORT:-8090}${AI_HUB_CONTEXT_PATH:-/ai-hub}/actuator/health" >/dev/null; then
    printf 'AI Hub is ready, pid=%s\n' "$pid"
    exit 0
  fi
  sleep 1
done

printf 'AI Hub startup timed out. See %s\n' "$LOG_FILE" >&2
kill "$extension_pid" 2>/dev/null || true
rm -f "$EXTENSION_PID_FILE"
exit 1
