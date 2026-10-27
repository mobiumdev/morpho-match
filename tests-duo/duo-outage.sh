#!/bin/sh
# The server going away mid-game, and coming back. The script runs its own
# server, so it can stop it: a game is under way (A has the Monarchs), the
# server is stopped, and both devices must say it did not answer — a flip
# included — while keeping the last board they were told. The server is
# started again, and both must drop the message and show what it now holds:
# a new game, since the game lives in the server's memory and nowhere else.
# usage: tests-duo/duo-outage.sh [A-serial] [B-serial]   (needs Go, port 8788 free)
set -eu
M=${MOBIUM:-mobium}
A=${1:-emulator-5554}; B=${2:-emulator-5556}
PORT=${DUO_OUTAGE_PORT:-8788}
SERVER=http://127.0.0.1:$PORT
ON_DEVICE=${DUO_DEVICE_SERVER:-http://10.0.2.2:$PORT}
HERE=$(cd "$(dirname "$0")" && pwd)
TMP=$(mktemp -d)
pid=
fail() { echo "FAIL: $*" >&2; exit 1; }
stop() { [ -z "$pid" ] || { kill "$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true; pid=; }; }
trap 'stop; rm -rf "$TMP"' EXIT
both() { # both <locator> [wait flags] — the same thing on both screens
  loc=$1; shift
  for d in "$A" "$B"; do
    $M wait --device "$d" "$loc" "$@" --timeout 6s >/dev/null || fail "$d does not show $loc $*"
  done
}
start() {
  "$TMP/duo-server" -addr "127.0.0.1:$PORT" -seed 42 >"$TMP/server.log" 2>&1 &
  pid=$!
  i=0
  until curl -sf "$SERVER/state?player=A" >/dev/null; do
    i=$((i + 1)); [ "$i" -lt 50 ] || fail "the server did not start: $(cat "$TMP/server.log")"
    kill -0 "$pid" 2>/dev/null || fail "the server exited: $(cat "$TMP/server.log")"
    sleep 0.1
  done
}

if curl -s "$SERVER/state?player=A" >/dev/null 2>&1; then fail "something already answers on port $PORT"; fi
(cd "$HERE/../server" && go build -o "$TMP/duo-server" .) || fail "the server did not build"
start
$M open --device "$A" "morphomatch://duo?player=A&server=$ON_DEVICE" >/dev/null
$M open --device "$B" "morphomatch://duo?player=B&server=$ON_DEVICE" >/dev/null
both testid=turn --for text --text "Turn: A" --exact
$M tap --device "$A" testid=duo_card_4 >/dev/null
$M tap --device "$A" testid=duo_card_7 >/dev/null
both 'label="Card 5, Monarch, matched"'
both testid=scoreA --for text --text "A: 1" --exact
echo "1. a game under way: A took the Monarchs, on both"

stop
both testid=duoMessage --for text --text "The server did not answer"
both 'label="Card 5, Monarch, matched"'
both testid=scoreA --for text --text "A: 1" --exact
echo "2. the server stopped: both say it did not answer, and keep the last board"

$M tap --device "$A" testid=duo_card_0 >/dev/null
$M wait --device "$A" testid=duoMessage --for text --text "The server did not answer" --timeout 6s >/dev/null \
  || fail "A's flip with no server did not say so"
$M wait --device "$A" 'label="Card 1, face down"' --timeout 6s >/dev/null || fail "A shows a card the server never turned"
echo "3. A's flip with no server: refused as unanswered, the card still down"

start
both testid=duoMessage --for text --text "did not answer" --not
both testid=scoreA --for text --text "A: 0" --exact
both 'label="Card 5, face down"'
both testid=turn --for text --text "Turn: A" --exact
echo "4. the server back: the message gone on both, and both show its new game"
echo "PASS"
