#!/bin/sh
# Two devices in turn, through one daemon: the same seed on A and B, pairs
# taken in turn. The runner cannot do this: a test's steps run in batches on
# its project's device, and a step naming another is refused.
# usage: tests-duo/duo.sh [A-serial] [B-serial]
set -eu
M=${MOBIUM:-mobium}
A=${1:-emulator-5554}; B=${2:-emulator-5556}
fail() { echo "FAIL: $*" >&2; exit 1; }
for d in "$A" "$B"; do
  $M open --device "$d" "morphomatch://reset" >/dev/null
  $M open --device "$d" "morphomatch://play?seed=42" >/dev/null
done
n=0
for p in "0 4" "1 12" "2 10" "3 7"; do
  set -- $p; n=$((n + 1))
  if [ $((n % 2)) -eq 1 ]; then d=$A; else d=$B; fi
  $M tap --device "$d" "testid=card_$1" >/dev/null
  $M tap --device "$d" "testid=card_$2" >/dev/null
  $M wait --device "$d" testid=moves --for text --text "Moves: $(((n + 1) / 2))" --exact --timeout 5s >/dev/null \
    || fail "turn $n on $d: moves not $(((n + 1) / 2))"
  echo "turn $n on $d: pair $1/$2 ok"
done
$M wait --device "$A" 'label="Card 3, Blue Morpho, matched"' --timeout 3s >/dev/null || fail "A lost its own pair"
$M wait --device "$B" 'label="Card 3, face down"' --timeout 3s >/dev/null || fail "B shows A's pair"
$M wait --device "$B" 'label="Card 4, Monarch, matched"' --timeout 3s >/dev/null || fail "B lost its own pair"
$M wait --device "$A" 'label="Card 4, face down"' --timeout 3s >/dev/null || fail "A shows B's pair"
echo "PASS: two devices took turns, each kept its own board"
