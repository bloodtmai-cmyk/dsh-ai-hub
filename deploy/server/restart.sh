#!/usr/bin/env bash
set -euo pipefail

BASE_DIR=$(cd "$(dirname "$0")" && pwd)
"$BASE_DIR/stop.sh"
"$BASE_DIR/start.sh"
