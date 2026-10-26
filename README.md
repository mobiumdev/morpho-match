# Morpho Match

A small memory game built as an app under test for
[Mobium](https://github.com/mobiumdev/mobium): flip cards to find pairs of
butterflies, then sort them by dragging. Android (Jetpack Compose), for
phones and for Meta Quest's 2D panels, run on the Meta Spatial Simulator
([Mobium's HORIZON-OS.md](https://github.com/mobiumdev/mobium/blob/main/docs/HORIZON-OS.md)).
A sibling of [Morpho Market](https://github.com/mobiumdev/morpho-market).

## Where it stands

| Device | Single player | Bugs caught | The duo |
| --- | --- | --- | --- |
| Android 15 emulator | 7 / 7 | 4 / 4 | passes, 3 / 3 server bugs caught |
| Meta Spatial Simulator (Horizon OS build 207) | **5 / 7** | not run | passes, as player B |

The two failures on the Quest are the sort level's drags, and they fail on
purpose: on a Quest panel a drag moves about 2.5 times as far as the finger
(Mobium's CHALLENGES 367, open), and the sort level drops a card where its
drag carried it. It is the known-good case that fix will be measured
against.

Each mechanic is there to test something:

| Mechanic | What it tests |
| --- | --- |
| Pairs of cards, flipped | taps, reading state from content descriptions (`Card 3, Blue Morpho`) |
| A mismatch turned back after 800 ms | waits on a screen that changes by itself |
| A seed in the link (`morphomatch://play?seed=42`) | a deal a test knows; `GameTest` pins seed 42's |
| The sort level, cards dragged into slots | drags. A card drops where its *offset* carried it (the drag's summed steps), so a device that magnifies a drag (the Quest: CHALLENGES 367) drops it in the wrong slot |
| A chime on a match | sound, for `mobium audio` (not yet in a test) |
| A switch of bugs (`morphomatch://bugs?on=…&off=all`) | `tests/matrix.sh` runs the suite with each one on |

The bugs: `score-double`, `pair-never`, `best-lost`, `sort-three`.

```sh
cd android && ANDROID_HOME=… ./gradlew :app:testDebugUnitTest :app:assembleRelease
mobium install app/build/outputs/apk/release/app-release.apk
mobium test --config tests/mobium.config.json                                  # emulator-5554
MOBIUM_QUEST_DEVICE=emulator-5556 mobium test --config tests/quest.config.json # the Quest simulator
MOBIUM=mobium DEVICE=emulator-5554 sh tests/matrix.sh out/                     # the bug matrix
```

Not yet: the gray box (MobiumApp's opt-in library), the chime in a test,
the bug matrix on the Quest, Fire TV's D-pad, an iPhone port.

## The duo (Stage 1b)

Two devices, one game. `server/` is the authority (Go, loopback
`127.0.0.1:8787`, which an emulator reaches as `10.0.2.2:8787`): turns, a
match keeps the turn, a mismatch is turned back and hands it over. The app
opens as a player with `morphomatch://duo?player=A&server=http://10.0.2.2:8787`.
Server bugs: `out-of-turn`, `turn-stuck`, `score-swap`.

```sh
cd server && go test ./... && go run . &            # seed 42, pinned in duo_test.go
tests-duo/duo-play.sh emulator-5554 emulator-5556   # both screens read at every step
tests-duo/duo-matrix.sh emulator-5554 emulator-5556 # each server bug alone
```

A `mobium test` file cannot do this: its steps run in batches on its
project's device, and a step naming another device is refused.

## License

Apache License 2.0; see [LICENSE](LICENSE) and [NOTICE](NOTICE).
