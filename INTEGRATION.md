# Building this on a Light Phone III

`app/` is a Light SDK tool module. It can't build standalone — it needs the SDK's
`:sdk:client` and `:sdk:ui` projects and the `com.thelightphone.light-sdk` Gradle
plugin, which live in [lightphone/light-sdk](https://github.com/lightphone/light-sdk).

## Setup

```bash
git clone https://github.com/lightphone/light-sdk.git
cd light-sdk

# The SDK hosts its library builds on GitHub Packages, so you need a token with
# package:read. Put it in local.properties:
cat >> local.properties <<'EOF'
gpr.user=gi-os
gpr.key=<a PAT with read:packages>
EOF

# Drop this app in as the tool module
rm -rf tool
git clone https://github.com/gi-os/LightNonogram.git /tmp/LightNonogram
cp -r /tmp/LightNonogram/app tool

./gradlew :tool:assembleDebug
./gradlew :tool:test        # the 29 tests should pass here
```

`settings.gradle.kts` in the SDK already includes `:tool`, so nothing else to wire.

## Running it

Per the SDK README, current LightOS builds in the wild aren't yet ready to run
SDK-built tools seamlessly. Two options:

**Emulator (recommended while developing).** Create an AVD matching the LPIII —
1080×1240, 3.92", API 34, **no Google Play Services** — and install the SDK's
LightOS emulator app as a system app. Instructions are in the SDK's
`docs/system_app`.

**Real hardware.** `adb install` the APK directly. LightOS will warn about
installing a tool it didn't sign; that's expected for a local build.

## Things to verify first on real hardware

The game logic is fully tested on the JVM, but three things can only be judged on
the device:

1. **Cell size.** A 10×10 grid with a ~34% clue gutter gives roughly 37 dp per
   cell — about 5.7 mm at the LPIII's ~330 ppi. That should be comfortable, but
   it's the number to check first. `PicrossGrid` has the gutter fraction as a
   single constant if you need to trade clue room for cell size.
2. **Greyscale rendering.** Dimmed clues and the cross dots use alpha (0.25–0.30).
   If the display dithers those badly, switch dimmed clues to a smaller font
   weight and the dots to solid black at a smaller radius.
3. **Drag smoothness.** Cells are drawn in one `Canvas`, so a stroke shouldn't
   recompose anything, but this is the thing most likely to feel wrong on ART.

## Known deviations to reconcile with the SDK

Written against the SDK's published docs, not compiled against it, so expect to
adjust these:

- **Import paths.** `LightScreen`, `LightViewModel`, `SealedLightActivity`,
  `SimpleLightScreen`, `@InitialScreen` are imported from
  `com.thelightphone.sdk.client[.annotations]`. Fix to match the real packages.
- **`navigateTo` with arguments.** `PuzzleScreen` takes a puzzle id, so
  navigation uses the lambda form — `navigateTo({ act -> PuzzleScreen(act, id, null) }) { ... }`
  — rather than `::PuzzleScreen`. Confirm the factory signature is
  `(SealedLightActivity) -> SimpleLightScreen<R>`.
- **`BasicText` instead of `LightText`.** `:sdk:ui` provides `LightText` and
  theme tokens, and Light say they judge submissions partly on aesthetic fit, so
  swapping these over is worth doing before submitting. `BasicText` is used here
  only because it needs no theme and is definitely on the allow-list.
- **`awaitEachGesture`** needs Compose foundation 1.6+. On older versions use
  the deprecated `forEachGesture`.
- **`viewModelScope`** is assumed available on `LightViewModel` via
  `androidx.lifecycle.ViewModel`. If it isn't exposed, use the SDK's own
  coroutine scope.

## Restrictions this module already respects

The SDK's Gradle plugin fails the build on these, so they're worth not
reintroducing:

- No `AndroidManifest.xml` — generated from `lighttool.toml`.
- No `applicationId`, `versionCode`, `versionName`, or `namespace` in
  `build.gradle.kts` — all derived from `lighttool.toml`.
- No `android.content.Context`, `Intent`, `getSystemService`, `contentResolver`,
  `startActivity`, `LocalContext.current`, or reflection anywhere.
  This is why the puzzle pack is a compiled-in constant instead of an asset.
- Dependencies limited to the allow-list: Compose, coroutines, DataStore,
  activity-compose. No JSON library — `PackReader` is hand-rolled.
