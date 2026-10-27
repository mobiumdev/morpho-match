#!/bin/sh
# Morpho Match Duel, played by a script on two devices to a draw: the
# server's seed 42 (pinned in server/duo_test.go), card numbers from 1 and
# duo_card_N ids from 0:
#   owl 1/11, emerald 2/4, glasswing 3/9, monarch 5/8, atlas 6/12,
#   swallowtail 7/13, peacock 10/16, morpho 14/15
# Every step reads both screens: the server's game is one game, and two
# devices that disagree about it is the failure worth finding. A mismatch is
# held up for 3 seconds (the game's own is 0.8), so both screens can be read
# while it is up; read one after the other, the Quest's missed it.
# Each step prints its number; duo-matrix.sh reads the last one to say where
# a bug was caught.
# usage: tests-duo/duo-play.sh [A-serial] [B-serial]   (the server already running)
set -eu
M=${MOBIUM:-mobium}
A=${1:-emulator-5554}; B=${2:-emulator-5556}
SERVER=${DUO_SERVER:-http://127.0.0.1:8787}   # as the Mac reaches it
ON_DEVICE=${DUO_DEVICE_SERVER:-http://10.0.2.2:8787}   # as an emulator does
fail() { echo "FAIL: $*" >&2; exit 1; }
both() { # both <locator> [wait flags] — the same thing on both screens
  loc=$1; shift
  for d in "$A" "$B"; do
    $M wait --device "$d" "$loc" "$@" --timeout 4s >/dev/null || fail "$d does not show $loc $*"
  done
}

curl -sf -X POST "$SERVER/reset?seed=42&hide=3s" >/dev/null || fail "no duo server at $SERVER"
$M open --device "$A" "morphomatch://duo?player=A&server=$ON_DEVICE" >/dev/null
$M open --device "$B" "morphomatch://duo?player=B&server=$ON_DEVICE" >/dev/null
$M wait --device "$A" testid=you --for text --text "You are A" --exact >/dev/null || fail "A did not open as A"
$M wait --device "$B" testid=you --for text --text "You are B" --exact >/dev/null || fail "B did not open as B"
both testid=turn --for text --text "Turn: A" --exact
echo "1. both devices joined; A to play"

$M tap --device "$B" testid=duo_card_0 >/dev/null
$M wait --device "$B" testid=duoMessage --for text --text "not your turn" --exact --timeout 4s >/dev/null \
  || fail "B's flip on A's turn was not refused"
both 'label="Card 1, face down"'
echo "2. B's flip on A's turn refused; the card stayed down on both"

$M tap --device "$A" testid=duo_card_4 >/dev/null
$M tap --device "$A" testid=duo_card_7 >/dev/null
both 'label="Card 5, Monarch, matched"'
both testid=scoreA --for text --text "A: 1" --exact
both testid=turn --for text --text "Turn: A" --exact
echo "3. A took the Monarchs: A 1 on both, and A plays again"

$M tap --device "$A" testid=duo_card_0 >/dev/null
$M tap --device "$A" testid=duo_card_1 >/dev/null
both 'label="Card 1, Owl"'
both 'label="Card 2, Emerald"'
both testid=turn --for text --text "Turn: B" --exact
both 'label="Card 1, face down"'
echo "4. A's Owl and Emerald did not match: turned back, B to play, on both"

$M tap --device "$B" testid=duo_card_2 >/dev/null
$M tap --device "$B" testid=duo_card_8 >/dev/null
both 'label="Card 3, Glasswing, matched"'
both testid=scoreB --for text --text "B: 1" --exact
both testid=scoreA --for text --text "A: 1" --exact
echo "5. B took the Glasswings: A 1, B 1 on both"

P=$B
for p in "0 10 1 Owl" "1 3 2 Emerald" "5 11 6 Atlas"; do
  set -- $p
  a=$1 b=$2 n=$3; shift 3   # the rest is the species' name
  $M tap --device "$P" "testid=duo_card_$a" >/dev/null
  $M tap --device "$P" "testid=duo_card_$b" >/dev/null
  both "label=\"Card $n, $*, matched\""
done
both testid=scoreB --for text --text "B: 4" --exact
both testid=turn --for text --text "Turn: B" --exact
echo "6. B took the Owls, Emeralds and Atlases: B 4, and B plays on"

$M tap --device "$B" testid=duo_card_6 >/dev/null
$M tap --device "$B" testid=duo_card_13 >/dev/null
both 'label="Card 7, Swallowtail"'
both 'label="Card 14, Blue Morpho"'
both testid=turn --for text --text "Turn: A" --exact
both 'label="Card 7, face down"'
echo "7. B's Swallowtail and Blue Morpho did not match: turned back, A to play, on both"

P=$A
for p in "6 12 7 Swallowtail" "13 14 14 Blue Morpho" "9 15 10 Peacock"; do
  set -- $p
  a=$1 b=$2 n=$3; shift 3   # the rest is the species' name
  $M tap --device "$P" "testid=duo_card_$a" >/dev/null
  $M tap --device "$P" "testid=duo_card_$b" >/dev/null
  both "label=\"Card $n, $*, matched\""
done
both testid=scoreA --for text --text "A: 4" --exact
both testid=scoreB --for text --text "B: 4" --exact
both testid=duoOver --for text --text "A draw, 4 each" --exact
echo "8. A took the last three: every pair found, a draw at 4 each on both"
echo "PASS"
