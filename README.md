# BrightNonogram

Picross for the [Light Phone III](https://www.thelightphone.com/). 103 hand-drawn picture
puzzles across 10×10 and 15×15, plus endless generated ones that get names and go in a
collection. No network, no permissions, no backend.

A black-and-white logic grid is close to the ideal Light Phone tool: natively 1-bit,
playable offline, and the board itself never scrolls.

This repository is a fork of [lightphone/light-sdk](https://github.com/lightphone/light-sdk)
— not of an existing nonogram game — with the game built as a tool in `tool/`, so the SDK
builds from source and there's nothing extra to check out.

**Current version:** `versionName` in `tool/lighttool.toml` is `0.4.0` (tag `v0.4.0`,
2026-07-29). One untagged commit sits on top of it as of 2026-07-30 (wheel-in-README
docs only — no code change since the tag). See [Version history](#version-history) for
every release.

## Quick start

Sideload the latest release APK — no build required:

```bash
# grab LightNonogram-<version>.apk from https://github.com/gi-os/BrightNonogram/releases
adb install -r LightNonogram-0.4.0.apk
```

LightOS will warn the tool isn't signed by Light — expected, it's signed with a personal
sideload key. Accept and it installs. Track the repo in
[Obtainium](https://github.com/ImranR98/Obtainium) with the APK filter
`LightNonogram-.*\.apk` for automatic updates.

To build it yourself instead:

```bash
git clone https://github.com/gi-os/BrightNonogram.git
cd BrightNonogram
./gradlew :tool:assembleDebug
./gradlew :tool:testDebugUnitTest      # 70 tests
```

Debug builds are signed with the SDK's committed development key, so they install over
each other but not over a release build. Resolving the SDK's keyboard dependency needs a
GitHub Packages token in `local.properties` (`gpr.user` / `gpr.key`, PAT with
`read:packages`) — CI reads the same values from `GH_PACKAGES_USER` /
`GH_PACKAGES_TOKEN`. Full detail, including running against the LightOS emulator and
cutting a signed release, is in [INSTALL.md](INSTALL.md).

## Status

| Piece | State |
|---|---|
| `tool/` game core — board, drag-fill, auto-mark, win, progress | **Working.** |
| `tool/` on-device generator + naming + collection | **Working.** |
| Test suite | **70 tests**, all green. |
| `tool/` Compose UI + SDK screen | **Builds and runs on device.** |
| `art/icons-10.txt` — 69 hand-drawn 10×10 | **Done.** All uniquely solvable, CC0. |
| `art/icons-15.txt` — 34 hand-drawn 15×15 | **Done.** All uniquely solvable, CC0. |
| `tools/picross-gen` — generator & validator | **Working.** 9 tests + a brute-force cross-check. |
| Hardware wheel scrolling (campaign, collection, menu) | **Working**, since v0.4.0. |

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
[BrightControl](https://github.com/gi-os/BrightControl), which is optional and owns them
across the phone: hold the wheel in and turn for brightness, tap it for the flashlight,
press the camera button for the camera. Each of those is rebindable, tap and hold
separately, to any installed app, and apps that handle no wheel keys of their own get
brightness or a synthetic-swipe scroll out of it. The long version is in
[LightNews](https://github.com/gi-os/LightNews#the-wheel-and-the-camera-button).

Installing it costs the game neither its scrolling nor the board its brightness.
BrightControl passes bare turns straight through to `com.gios.*`, which is this tool, because
per-notch scrolling inside an app beats anything reachable from outside it — so a turn on a
list still scrolls, and a turn on the board still travels on through the SDK to LightOS and
dims the screen, exactly as it does with nothing installed.

```bash
# Optional: BrightControl, for brightness, the flashlight and the camera button
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
[BrightControl/releases/latest](https://github.com/gi-os/BrightControl/releases/latest).

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

## Configuration

The tool takes no configuration outside the in-game Settings screen (auto-mark on/off,
per [How to play](#how-to-play) above) — there is no config file, no server URL, no
account. `tool/lighttool.toml` is the one file that configures the *tool itself* to the
SDK:

```toml
[tool]
id = "com.gios.lightnonogram"
label = "Nonogram"
versionCode = 11
versionName = "0.4.0"
orientation = "portrait"
permissions = []
serverPackage = "com.lightos"   # swap to com.thelightphone.sdk.emulator for the emulator
```

No permissions at all: every bundled puzzle compiles into the APK and everything
generated is computed on device, so the tool never touches the network, storage or
notifications.

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

## Contributing

- New puzzles go in `art/icons-10.txt` / `art/icons-15.txt`, one row per puzzle picture —
  see [Regenerating the bundled pack](#regenerating-the-bundled-pack) above, and don't
  hand-edit `BundledPack.kt` / `BundledPack15.kt`, they're generated.
- Every puzzle you add must pass the bundler's uniqueness check (it refuses to emit an
  ambiguous one) — that's the whole quality bar.
- Game/generator changes: run `./gradlew :tool:testDebugUnitTest` before opening a PR;
  changes to the solver should extend `tools/picross-gen`'s brute-force cross-check
  rather than only adding example-based tests.
- Cutting a release is a tag: bump `versionName`/`versionCode` in `tool/lighttool.toml`,
  then `git tag v0.x.y && git push origin v0.x.y` — the release workflow refuses to run
  if the tag doesn't match `lighttool.toml`. Full secrets list in
  [INSTALL.md](INSTALL.md#cutting-a-release).

## Version history

| Version | Date | Notes |
|---|---|---|
| `v0.4.0` | 2026-07-29 | Hardware wheel scrolls the campaign grid, collection and menu — not the board, which passes turns through to LightOS brightness. |
| `v0.3.2` | 2026-07-29 | Bigger clue digits; collection titles centred. |
| `v0.3.1` | 2026-07-29 | Gave the board more of the screen. |
| `v0.3.0` | 2026-07-28 | Two tabs on the SDK's own bottom bar (Play / Collection); Continue resumes anything, campaign or generated; centring fixes. |
| `v0.2.2` | 2026-07-29 | Word seeds (type a word, not just a number); live Undo; uncropped clues; a properly centred grid. |
| `v0.2.1` | 2026-07-28 | Centred the grid; a seed can be typed in. |
| `v0.2.0` | 2026-07-28 | docs only (collection table). |
| `v0.1.3` | 2026-07-28 | Fixed the launch crash: a `Regex` the JVM accepts but Android rejects. |
| `v0.1.2` | 2026-07-28 | Minification off; the tool reports its own startup failure instead of a silent black screen. |
| `v0.1.1` | 2026-07-28 | Fixed the startup crash: the tool module was missing the KSP plugin. |
| `v0.1.0` | 2026-07-28 | First tagged release, after fixing the three compile errors the first real build found. |

Two untagged milestones sit before `v0.1.0` in `git log`: `2cca4e0` restructured the repo
as a light-sdk fork with CI that builds and releases the APK, and `c91ab30` added the game
itself — 69 hand-drawn 10×10 puzzles and a playable tool, on top of `a2bb764`'s standalone
line solver, validator and 336 CC0 puzzles that predates the SDK integration entirely.

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
  [FogLight](https://github.com/gi-os/FogLight)'s `scripts/generate-icon.js`: the
  app's first letter in Public Sans, white on black, 85.4pt on a 100pt canvas,
  centred on the ink rather than the line box. Regenerate with
  `python3 tools/generate_icon.py`. Public Sans is SIL OFL 1.1 — see
  [assets/fonts/README.md](assets/fonts/README.md).

[BrightSolitaire](https://github.com/gi-os/BrightSolitaire) went the other way in this
collection — it put a complete game in the SDK `tool` module first, so it's the model
to follow when wiring `tool/` up.

## The gi-os Light App collection

Twelve tools for the Light Phone III, all open source, all built in one run.

| Tool | What it does | Built on |
| --- | --- | --- |
| [BrightPasses](https://github.com/gi-os/BrightPasses) | Photograph a movie ticket, keep the stub | Plain Android |
| [LightQR](https://github.com/gi-os/LightQR) | QR scanner, plus a browser generator | Plain Android |
| [BrightNews](https://github.com/gi-os/BrightNews) | RSS and Atom reader with images and QR subscribe | light-sdk, fork of [zachattack323/LightRSS](https://github.com/zachattack323/LightRSS) |
| [BrightTransit](https://github.com/gi-os/BrightTransit) | Live MTA subway arrivals | light-sdk fork |
| [chat](https://github.com/gi-os/chat) | iMessage over a self-hosted BlueBubbles server | Fork of [craigeley/chat](https://github.com/craigeley/chat) |
| [FogLight](https://github.com/gi-os/FogLight) | Fog of World companion, GPS recorder and fog map | Fork of [garado/light-topographic](https://github.com/garado/light-topographic) |
| **BrightNonogram** (this repo) | Picross, plus a generator that only ships solvable puzzles | Kotlin generator, light-sdk tool |
| [BrightSolitaire](https://github.com/gi-os/BrightSolitaire) | Klondike, draw one, unlimited redeals | light-sdk |
| [BrightLibrary](https://github.com/gi-os/BrightLibrary) | RSVP speed reader for EPUB and MOBI | Fork of [fluffyspace/FastRead](https://github.com/fluffyspace/FastRead) |
| [BrightTip](https://github.com/gi-os/BrightTip) | Tip calculator, plus a receipt splitter that reads the line items | Plain Android |
| [BrightNoise](https://github.com/gi-os/BrightNoise) | Twelve synthesized sounds, a two-layer mixer and a sleep timer | Plain Android |
| [LightPods](https://github.com/gi-os/LightPods) | AirPods battery, in-ear and lid status | Plain Android, ports [LibrePods](https://github.com/kavishdevar/librepods) |

The Light Phone does not sponsor or endorse any of these. Licences vary per repo.

## Licenses

Forked from [light-sdk](https://github.com/lightphone/light-sdk) under MIT; see
[LICENSE](LICENSE). Everything under `tool/`, `art/` and `tools/picross-gen/` is
mine and also MIT. The puzzles themselves — `art/icons-10.txt` and `packs/` — are
CC0-1.0, see [packs/LICENSE](packs/LICENSE).
