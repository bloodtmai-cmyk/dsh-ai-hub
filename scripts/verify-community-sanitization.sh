#!/usr/bin/env bash
set -euo pipefail

patterns=(
  'YO''FC'
  'yo''fc'
  '长''飞'
  'git''cc'
  'fibre''wms'
  'wms_''ai'
  'ai_''agent'
  '10.''140.'
  '10.''192.'
  '10.''98.'
  '10.''90.'
)

failed=0
for pattern in "${patterns[@]}"; do
  matches=$(git grep -n -I -F "$pattern" -- . ':(exclude)scripts/verify-community-sanitization.sh' || true)
  if [[ -n "$matches" ]]; then
    printf 'Disallowed community literal: %s\n%s\n' "$pattern" "$matches" >&2
    failed=1
  fi
  filename_matches=$(git ls-files | grep -iF -- "$pattern" || true)
  if [[ -n "$filename_matches" ]]; then
    printf 'Disallowed tracked filename for %s:\n%s\n' "$pattern" "$filename_matches" >&2
    failed=1
  fi
done

identifier_matches=$(git grep -n -I -E '(^|[^0-9])(5000270|0104486|06996)([^0-9]|$)' -- . \
  ':(exclude)scripts/verify-community-sanitization.sh' || true)
if [[ -n "$identifier_matches" ]]; then
  printf 'Disallowed community identifier:\n%s\n' "$identifier_matches" >&2
  failed=1
fi

if [[ "$failed" -ne 0 ]]; then
  exit 1
fi

printf 'Community sanitization check passed.\n'
