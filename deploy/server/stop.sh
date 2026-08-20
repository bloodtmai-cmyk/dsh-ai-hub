#!/usr/bin/env bash
set -euo pipefail

BASE_DIR=$(cd "$(dirname "$0")" && pwd)
PID_FILE=$BASE_DIR/ai-hub.pid
EXTENSION_PID_FILE=$BASE_DIR/ai-hub-extension-host.pid

stop_process() {
  local pid_file=$1
  local name=$2
  if [[ ! -f "$pid_file" ]]; then
    printf '%s is not running\n' "$name"
    return 0
  fi

  local pid
  pid=$(cat "$pid_file")
  if ! kill -0 "$pid" 2>/dev/null; then
    rm -f "$pid_file"
    printf 'Removed stale %s pid file\n' "$name"
    return 0
  fi

  kill "$pid"
  for _ in {1..30}; do
    if ! kill -0 "$pid" 2>/dev/null; then
      rm -f "$pid_file"
      printf '%s stopped\n' "$name"
      return 0
    fi
    sleep 1
  done

  printf '%s did not stop within 30 seconds, pid=%s\n' "$name" "$pid" >&2
  return 1
}

rc=0
stop_process "$PID_FILE" "AI Hub" || rc=$?
stop_process "$EXTENSION_PID_FILE" "Extension Host" || rc=$?
exit "$rc"
