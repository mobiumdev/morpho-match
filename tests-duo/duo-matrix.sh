#!/bin/sh
# The duo's bug matrix: duo-play.sh with no bug on, then with each server bug alone.
# usage: tests-duo/duo-matrix.sh [A-serial] [B-serial]   (the server already running)
SERVER=${DUO_SERVER:-http://127.0.0.1:8787}
HERE=$(cd "$(dirname "$0")" && pwd)
for b in none out-of-turn turn-stuck score-swap; do
  curl -sf -X POST "$SERVER/bugs?off=all" >/dev/null || { echo "no duo server at $SERVER" >&2; exit 1; }
  [ "$b" = none ] || curl -sf -X POST "$SERVER/bugs?on=$b" >/dev/null
  out=$(sh "$HERE/duo-play.sh" "$@" 2>&1); code=$?
  echo "$b exit=$code: $(echo "$out" | grep -E '^(FAIL|PASS)' | head -1)"
done
curl -sf -X POST "$SERVER/bugs?off=all" >/dev/null
