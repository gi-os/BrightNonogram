# Installing Nonogram on a Light Phone III

## From a release

1. Grab `LightNonogram-<version>.apk` from
   [Releases](https://github.com/gi-os/LightNonogram/releases).
2. Enable USB debugging on the phone.
3. `adb install -r LightNonogram-<version>.apk`

LightOS will warn that the tool isn't signed by Light. That's expected — it's
signed with a personal sideload key. Accept and it installs.

### Automatic updates

Track this repository in [Obtainium](https://github.com/ImranR98/Obtainium) with
the APK filter `LightNonogram-.*\.apk`.

### About the signing key

Android only accepts an update signed by the same key that installed the app, so
every release here uses one sideload keystore. Two consequences:

- Updates must come from this repository. An APK from anywhere else, including a
  build you make yourself, cannot install over it — you'd have to uninstall
  first, which wipes your progress.
- If Light ever signs and distributes this tool officially, that APK will have a
  different signature and will also need a clean install.

Each release publishes the certificate fingerprint and a `.sha256` alongside the
APK if you want to verify what you're installing.

## Building it yourself

This repository is a fork of
[lightphone/light-sdk](https://github.com/lightphone/light-sdk) with the game in
`tool/`, so the SDK builds from source and there's nothing extra to check out.

```sh
git clone https://github.com/gi-os/LightNonogram.git
cd LightNonogram
./gradlew :tool:assembleDebug
./gradlew :tool:testDebugUnitTest      # 29 tests
```

Debug builds are signed with the SDK's committed development key, so they
install fine over each other but not over a release build.

Resolving the SDK's keyboard dependency needs GitHub Packages read access. Add a
token to `local.properties`:

```
gpr.user=<your github username>
gpr.key=<a PAT with read:packages>
```

CI reads the same values from the `GH_PACKAGES_USER` and `GH_PACKAGES_TOKEN`
secrets.

## Running against the emulator instead

Set `serverPackage = "com.thelightphone.sdk.emulator"` in `tool/lighttool.toml`,
then follow `docs/system_app` to install the LightOS emulator app as a system app
on an AVD. An AVD close to the real device: 1080×1240, 3.92", API 34, no Google
Play Services.

## Cutting a release

```sh
# bump versionName and versionCode in tool/lighttool.toml first
git tag v0.1.0 && git push origin v0.1.0
```

The release workflow refuses to run if the tag doesn't match `versionName`.
It needs these repository secrets:

| Secret | What it is |
| --- | --- |
| `LIGHTNONOGRAM_KEYSTORE_BASE64` | `base64 -w0 lightnonogram-release.jks`. Optional — without it releases are signed with the SDK development key. |
| `LIGHTNONOGRAM_KEYSTORE_PASSWORD` | keystore password |
| `LIGHTNONOGRAM_KEY_ALIAS` | key alias inside the keystore |
| `GH_PACKAGES_USER` / `GH_PACKAGES_TOKEN` | GitHub Packages read access |

Generate a keystore once and keep it somewhere safe — losing it means nobody can
update an existing install:

```sh
keytool -genkeypair -v -keystore lightnonogram-release.jks \
  -alias lightnonogram -keyalg RSA -keysize 4096 -validity 10000
```
