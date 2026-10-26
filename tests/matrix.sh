#!/bin/sh
# The bug matrix: the suite with no bug on, then with each bug on alone.
# usage: [CONFIG=tests/quest.config.json DEVICE=<serial>] tests/matrix.sh OUT_DIR [bug...]
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=$1; shift
M=${MOBIUM:-mobium}  # the mobium binary; MOBIUM names another
CONFIG=${CONFIG:-$HERE/mobium.config.json}
ON=${DEVICE:+--device $DEVICE}
BUGS=${*:-$(sed -n 's/^    Bug("\([a-z-]*\)".*/\1/p' "$HERE/../android/app/src/main/java/dev/mobium/match/Game.kt")}
[ -n "$BUGS" ] || { echo "no bugs found" >&2; exit 1; }
for b in none $BUGS; do
  "$M" open "morphomatch://bugs?off=all" $ON >/dev/null || exit 1
  [ "$b" = none ] || "$M" open "morphomatch://bugs?on=$b" $ON >/dev/null || exit 1
  mkdir -p "$OUT/$b"
  "$M" test --config "$CONFIG" --reporter list,junit --output "$OUT/$b" > "$OUT/$b/console.txt" 2>&1
  echo "$b exit=$? $(grep -c '^  FAIL' "$OUT/$b/console.txt") failed"
done
"$M" open "morphomatch://bugs?off=all" $ON >/dev/null
