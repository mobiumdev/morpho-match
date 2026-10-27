#!/bin/sh
# The duo's bug matrix: duo-play.sh with no bug on, then with each server bug
# alone. It fails unless the game passes with none, and each bug fails the
# game at the step that should catch it — a bug caught by the wrong step is
# not caught, it is a lucky failure.
# usage: tests-duo/duo-matrix.sh [A-serial] [B-serial]   (the server already running)
SERVER=${DUO_SERVER:-http://127.0.0.1:8787}
HERE=$(cd "$(dirname "$0")" && pwd)
bad=0
# bug:step — the step of duo-play.sh that must fail with it on (none: 0, it passes)
for row in none:0 out-of-turn:2 match-hands-over:3 score-swap:3 turn-stuck:4; do
  b=${row%:*}; want=${row#*:}
  curl -sf -X POST "$SERVER/bugs?off=all" >/dev/null || { echo "no duo server at $SERVER" >&2; exit 1; }
  [ "$b" = none ] || curl -sf -X POST "$SERVER/bugs?on=$b" >/dev/null || { echo "the server has no bug $b" >&2; exit 1; }
  out=$(sh "$HERE/duo-play.sh" "$@" 2>&1); code=$?
  last=$(echo "$out" | sed -n 's/^\([0-9][0-9]*\)\. .*/\1/p' | tail -1)
  why=$(echo "$out" | grep -E '^(FAIL|PASS)' | head -1)
  if [ "$want" = 0 ]; then
    if [ "$code" = 0 ] && [ "$why" = PASS ]; then echo "ok   $b: $why"
    else echo "BAD  $b: the game should pass, got exit $code: $why"; bad=1; fi
  elif [ "$code" = 0 ]; then
    echo "BAD  $b: not caught, the game passed"; bad=1
  elif [ "$((${last:-0} + 1))" != "$want" ]; then
    echo "BAD  $b: caught at step $((${last:-0} + 1)), not $want: $why"; bad=1
  else
    echo "ok   $b: caught at step $want: $why"
  fi
done
curl -sf -X POST "$SERVER/bugs?off=all" >/dev/null
[ "$bad" = 0 ] && echo "PASS: the game passes, and every bug is caught where it should be"
exit "$bad"
