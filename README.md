# LightNonogram

Picross for the [Light Phone III](https://www.thelightphone.com/). 103 hand-drawn picture puzzles across 10×10 and 15×15, plus endless generated ones that get names and go in a collection. No network, no permissions, no backend.

A black-and-white logic grid is close to the ideal Light Phone tool: natively 1-bit, playable offline, and the board itself never scrolls.

## Status

This repository is a fork of [lightphone/light-sdk](https://github.com/lightphone/light-sdk)
with the game in `tool/`, so the SDK builds from source. See [INSTALL.md](INSTALL.md)
to build or sideload it.

| Piece | State |
|---|---|
| `tool/` game core — board, drag-fill, auto-mark, win, progress | **Working.** |
| `tool/` on-device generator + naming + collection | **Working.** |
| Test suite | **70 tests**, all green. |
| `tool/` Compose UI + SDK screen | **Builds and runs on device.** |
| `art/icons-10.txt` — 69 hand-drawn 10×10 | **Done.** All uniquely solvable, CC0. |
| `art/icons-15.txt` — 34 hand-drawn 15×15 | **Done.** All uniquely solvable, CC0. |
| `tools/picross-gen` — generator & validator | **Working.** 9 tests + a brute-force cross-check. |

Everything with no Android dependency is compiled and tested here. The Compose
layer needs a full Android toolchain, so `.github/workflows/build.yml` is what
actually proves it — and its first run may well fail.

## How to play

Fill cells so each row and column matches its numbers, and a picture appears. The title stays hidden until you solve it.

**Forgiving rules.** Nothing punishes a wrong fill — no timer, no lives, no red flash. On a 5 mm cell, mis-taps are the hardware's fault, not the player's.

Design decisions that carry most of the feel:

- **Axis-locked drag-fill.** The first cell you move to fixes the stroke to that row or column. Crucially, drifting off the line *projects onto it* rather than cancelling — a thumb always drifts, and a stroke that stalls mid-run feels broken.
- **Strokes protect existing marks.** A drag only writes into empty cells, so sweeping fill across a row never destroys crosses you placed deliberately. The cell you press is always the exception, so a mis-crossed cell is never stuck.
- **Auto-cross satisfied lines.** Complete a row's clue and its leftovers cross themselves out. Removes a lot of dull tapping.
- **Free crosses on load.** Lines clued `0` are known-empty the moment you read them, so they start crossed. Most pictures have blank border rows.
- **Undo folds auto-crosses in.** Undoing a fill also reverses the crosses it triggered — otherwise you'd be left picking up litter.
- **Dimmed clues.** A satisfied line's numbers fade, so you know where to stop looking.
- **A dot, not an ✕.** Auto-marking covers many cells at once, and a grid full of ✕ is loud. Dots stay quiet.
- **Auto-mark is a setting.** On by default because it saves a lot of dull tapping, off for anyone who wants the grid to hold only the marks they placed. Turning it off also skips the free crosses on lines clued `0`.
- **Two halves, on the SDK's own bottom bar.** Play holds the campaign, random puzzles and settings; Collection holds what you've made. The bar is hidden while a board is open, because it costs about four grid units of height and on the board every one of those belongs to the grid.
- **Continue resumes whatever you last touched**, campaign or generated. One slot, not one per puzzle — that's what "continue" means, and a single slot gives it for free. The board is saved after every stroke as two bit-per-cell masks, about eighty characters.
- **An explicit Home on the board.** LightOS's hardware back can't be intercepted: `LightActivity` wires its back dispatcher straight to its own `goBack()`, which pops the SDK's stack and calls `finish()` when it empties. `LightScreen.goBack` — the one that consults `LightViewModel.onBackPressed` — is only reached if a tool calls it itself. With one screen on the stack, back always closes the tool, so every view carries its own way back.

## The wheel

Turning the phone's wheel scrolls the campaign grid, the collection, the menu and, if it
ever appears, the startup trace. The campaign is the reason: 69 pictures in four columns is
about eighteen rows, and most of them start below the fold.

**Nothing else has to be installed for that.** Light patched
`/system/usr/keylayout/Generic.kl` and relabelled an optical sensor's two scancodes
`WHEEL_CCW` and `WHEEL_CW`, so a notch is an ordinary key event that lands in whichever app
holds focus, and the game reads it itself. No companion service, no permission, no root.

**Not the board.** A notch on a puzzle has nothing to move — the grid is sized to fit the
panel — and swallowing the key there would break something that does work today. A tool
gets keys from the SDK, which forwards anything the screen doesn't claim on to LightOS,
where a turn is brightness. Since the board is where you sit longest, and dimming is a
reasonable thing to want while sitting there, the game claims the wheel only on the views
that can actually move and lets the board pass it through.

Notches arrive faster than a frame, so each one becomes a debt that a share of gets paid off
per frame, and the first notch after a pause is held back, because the wheel sits under a
thumb. The wheel *click* and the camera button are left alone; they belong to
[LightControl](https://github.com/gi-os/LightControl), which is optional and owns them
across the phone: hold the wheel in and turn for brightness, tap it for the flashlight,
press the camera button for the camera. Each of those is rebindable, tap and hold
separately, to any installed app, and apps that handle no wheel keys of their own get
brightness or a synthetic-swipe scroll out of it. The long version is in
[LightNews](https://github.com/gi-os/LightNews#the-wheel-and-the-camera-button).

Installing it costs the game neither its scrolling nor the board its brightness.
LightControl passes bare turns straight through to `com.gios.*`, which is this tool, because
per-notch scrolling inside an app beats anything reachable from outside it — so a turn on a
list still scrolls, and a turn on the board still travels on through the SDK to LightOS and
dims the screen, exactly as it does with nothing installed.

```bash
# Optional: LightControl, for brightness, the flashlight and the camera button
adb install -r LightControl-v1.0.x.apk

# The key service. NOTE: this setting is a list, and this command REPLACES it —
# if you also run LightVoice's push-to-talk, colon-join both components instead.
adb shell settings put secure enabled_accessibility_services \
  com.gios.lightcontrol/com.gios.lightcontrol.keys.ControlService
adb shell settings put secure accessibility_enabled 1

# Brightness, and the level readout + opening apps from the service
adb shell appops set com.gios.lightcontrol WRITE_SETTINGS allow
adb shell appops set com.gios.lightcontrol SYSTEM_ALERT_WINDOW allow
```

The current build is at
[LightControl/releases/latest](https://github.com/gi-os/LightControl/releases/latest).

## Generated puzzles get names

Tap Random and you get a fresh puzzle at the current size, titled something like *The Umbral Cartographer* or *Rookhaven's Sable Orrery*. Solve it and it joins **Your collection** — a grid of everything you've finished.

You can also type a seed in yourself — **From a seed** on the menu — and it takes a word as readily as a number. That's the same trick Minecraft uses: if the box doesn't parse as a number it hashes the text instead, which is how word seeds work there. `String.hashCode` is specified by the JDK, so the same word gives the same puzzle on any device. Type a word and it becomes the puzzle's title; type a number and you get a generated name. The seed of whatever you're playing shows in the header, so one worth keeping can be written down and typed back. Text entry on LightOS is a screen of its own hosting the Light keyboard, which is why this is the tool's only second screen.

The whole collection is a few characters per entry: a seed and a size. The picture and the name are both derived from the seed, so nothing is stored but the number that made them. Around 17,000 name combinations, and the seed is run through SplitMix64's finalizer first, because seeds come from the clock and consecutive puzzles are milliseconds apart — a weaker mix would name a whole session almost identically.

## Why the puzzles are hand-drawn

There is no large, freely-redistributable nonogram corpus. [webpbn.com](https://webpbn.com/) is the canonical archive but every puzzle is author-copyrighted and explicitly not licensed for redistribution.

That matters here specifically: Light builds and signs community tools **from a public git commit and archives the source at build time**. Anything of murky provenance would be permanent and attributable.

Photos are no help either — thresholding a real image to 10×10 destroys anything recognisable. So the 69 pictures in `art/icons-10.txt` are drawn cell by cell and released CC0.

## The guarantee

Every puzzle that ships — bundled or generated on device — is checked against an **optimal line solver** that deduces exactly the cells a line's clues force, and never guesses. If pure line logic can't finish a grid, the grid is thrown away.

This is what separates real Picross from a coin flip. A randomly generated grid is usually either ambiguous or trivial; the validator is the whole product.

The property worth testing isn't "does the solver solve puzzles" but *"is every deduction it makes actually forced?"* An over-eager solver ships unsolvable puzzles silently and nobody finds out until a player is stuck on level 40. So it's cross-checked against exhaustive enumeration:

```
PASS  a known EMPTY splits the line, only one segment fits [3]
PASS  two viable segments force nothing
PASS  500 random grids: every deduction agrees with truth
PASS  'Solved' always means exhaustively unique
```

And the game is play-tested end to end — all 69 bundled puzzles solved through the real input API, by tapping and by dragging:

```
PASS  every bundled puzzle is winnable by tapping()
PASS  every bundled puzzle is winnable by dragging runs()
     drag playthrough: 0 puzzle(s) needed a repair tap after auto-cross
```

## On-device generation

Generation is cheap enough to run on tap. Measured on a desktop JVM, one validated 10×10 takes **~0.2 ms** including rejected attempts; a full grid solve is 0.04 ms. Even 20–50× slower under ART that's single-digit milliseconds, so there's no loading state and no backend.

Puzzles are **deterministic from an `Int` seed**, so "puzzle #4821" is one integer and shareable as a code. Bump `Generate.ALGORITHM_VERSION` if you change generation, or old seeds stop reproducing.

Yield depends sharply on fill ratio, which is the difficulty knob — time to produce one *validated* puzzle including rejected attempts:

| size | fill 0.58 (default) | fill 0.50 | fill 0.45 |
|---|---|---|---|
| 10×10 | 0.19 ms | 0.32 ms | 0.11 ms |
| 15×15 | 0.07 ms | 0.37 ms | 1.04 ms |
| 20×20 | 0.15 ms | 1.80 ms | 15.9 ms |

## Layout

```
tool/                          the game (this is the Light SDK tool module)
  lighttool.toml               tool metadata; no permissions requested
  src/main/kotlin/.../game/    board rules — pure Kotlin, fully tested
  src/main/kotlin/.../gen/     on-device generator — pure Kotlin, fully tested
  src/main/kotlin/.../data/    bundled pack + progress store
  src/main/kotlin/.../ui/      Compose grid
  src/main/kotlin/.../hw/      wheel keys and the notch-to-scroller bus
  src/main/kotlin/.../HomeScreen.kt   the single SDK screen
  src/test/                    29 tests
art/icons-10.txt               69 hand-drawn 10x10 puzzles (CC0) — source of truth
art/icons-15.txt               34 hand-drawn 15x15 puzzles (CC0)
tools/picross-gen/             generator, solver, validator, bundler
packs/                         generated abstract packs, 10x10 to 20x20 (CC0)
sdk/ plugin/ examples/ docs/   vendored from light-sdk, unmodified
```

It's one SDK screen, not three. Menu, gallery and board are ordinary Compose
state, so LightOS's own back bar is the only back affordance — one navigation
stack instead of two that can disagree.

## Regenerating the bundled pack

The art files are the source of truth. Edit a picture, then regenerate its pack:

```bash
# 10x10
./gradlew -p tools/picross-gen run --args="bundle \
    --art $PWD/art/icons-10.txt --pack-id bundled-10 --const BUNDLED_PACK_10 \
    --kotlin $PWD/tool/src/main/kotlin/com/gios/lightnonogram/data/BundledPack.kt"

# 15x15
./gradlew -p tools/picross-gen run --args="bundle \
    --art $PWD/art/icons-15.txt --pack-id bundled-15 --const BUNDLED_PACK_15 \
    --kotlin $PWD/tool/src/main/kotlin/com/gios/lightnonogram/data/BundledPack15.kt"
```

The bundler **refuses to emit an ambiguous puzzle** and names the offender, so a bad edit fails loudly at build time instead of shipping.

The pack compiles into the APK as a Kotlin string constant rather than loading from `assets`. That's not laziness: the Light SDK blocks `android.content.Context`, and therefore `AssetManager`. Compiling it in means no I/O, no Android API, and puzzles that cannot go missing — for 8 KB of source.

## Tests

```bash
./gradlew :tool:testDebugUnitTest        # 70 game, generator, naming and session tests
./gradlew -p tools/picross-gen test      # solver correctness, brute-force cross-check
```

CI runs both on every push, and also regenerates the puzzle pack from
`art/icons-10.txt` and fails if it differs from what's committed — so the art and
the shipped puzzles can't drift apart.

## Origin and credits

- **[The Light Phone](https://www.thelightphone.com/)** for
  [light-sdk](https://github.com/lightphone/light-sdk), which `tool/` builds against.
- **[webpbn.com](https://webpbn.com/)**, built by the late Jan Wolter and kept running
  since, is the reference archive for this puzzle form. Its
  [solver survey](https://webpbn.com/survey/) and its write-ups on line-solving and
  puzzle difficulty shaped the solver here. No webpbn puzzle appears in this repo,
  because each one belongs to the person who posted it and carries no redistribution
  license. Thank you for the documentation.
- The line solver goes past the left-most/right-most packing overlap method usually
  described in the literature. Overlap misses deductions that appear once known cells
  split a line into segments, so this computes, for every cell, whether it *can* be
  filled and whether it *can* be empty across all valid clue placements — which makes
  it optimal per line. The implementation is original.
- The 69 pictures in `art/icons-10.txt` are drawn by hand and dedicated CC0. Every
  puzzle in `packs/` is CC0 too. Take them.
- The app's mark follows the collection convention set by
  [LightFog](https://github.com/gi-os/LightFog)'s `scripts/generate-icon.js`: the
  app's first letter in Public Sans, white on black, 85.4pt on a 100pt canvas,
  centred on the ink rather than the line box. Regenerate with
  `python3 tools/generate_icon.py`. Public Sans is SIL OFL 1.1 — see
  [assets/fonts/README.md](assets/fonts/README.md).

[LightSolitaire](https://github.com/gi-os/LightSolitaire) went the other way in this
collection — it put a complete game in the SDK `tool` module first, so it's the model
to follow when wiring `tool/` up.

## The gi-os Light App collection

Twelve tools for the Light Phone III, all open source, all built in one run.

| Tool | What it does | Built on |
| --- | --- | --- |
| [LightPass](https://github.com/gi-os/LightPass) | Photograph a movie ticket, keep the stub | Plain Android |
| [LightQR](https://github.com/gi-os/LightQR) | QR scanner, plus a browser generator | Plain Android |
| [LightRSS](https://github.com/gi-os/LightRSS) | RSS and Atom reader with images and QR subscribe | light-sdk, fork of [zachattack323/LightRSS](https://github.com/zachattack323/LightRSS) |
| [LightNYCSubway](https://github.com/gi-os/LightNYCSubway) | Live MTA subway arrivals | light-sdk fork |
| [chat](https://github.com/gi-os/chat) | iMessage over a self-hosted BlueBubbles server | Fork of [craigeley/chat](https://github.com/craigeley/chat) |
| [LightFog](https://github.com/gi-os/LightFog) | Fog of World companion, GPS recorder and fog map | Fork of [garado/light-topographic](https://github.com/garado/light-topographic) |
| **LightNonogram** (this repo) | Picross, plus a generator that only ships solvable puzzles | Kotlin generator, light-sdk tool |
| [LightSolitaire](https://github.com/gi-os/LightSolitaire) | Klondike, draw one, unlimited redeals | light-sdk |
| [LightFastread](https://github.com/gi-os/LightFastread) | RSVP speed reader for EPUB and MOBI | Fork of [fluffyspace/FastRead](https://github.com/fluffyspace/FastRead) |
| [LightTip](https://github.com/gi-os/LightTip) | Tip calculator, plus a receipt splitter that reads the line items | Plain Android |
| [LightNoise](https://github.com/gi-os/LightNoise) | Twelve synthesized sounds, a two-layer mixer and a sleep timer | Plain Android |
| [LightPods](https://github.com/gi-os/LightPods) | AirPods battery, in-ear and lid status | Plain Android, ports [LibrePods](https://github.com/kavishdevar/librepods) |

The Light Phone does not sponsor or endorse any of these. Licences vary per repo.

## Licenses

Forked from [light-sdk](https://github.com/lightphone/light-sdk) under MIT; see
[LICENSE](LICENSE). Everything under `tool/`, `art/` and `tools/picross-gen/` is
mine and also MIT. The puzzles themselves — `art/icons-10.txt` and `packs/` — are
CC0-1.0, see [packs/LICENSE](packs/LICENSE).
