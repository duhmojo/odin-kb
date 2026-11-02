# Odin KB: customizing HeliBoard for wide handhelds

Clone of upstream HeliBoard (`v4.3-beta1-22`, commit `5790f31a`) in `C:\Users\chris\Projects\odin-kb`.

## What HeliBoard already does (try these first, no code needed)

| Goal | Already in HeliBoard? |
|---|---|
| Split keyboard | Yes. Settings > Appearance > Split keyboard, with separate on/off for portrait and landscape, and a "split spacer" width slider. The split inserts a gap in the middle of every row (`KeyboardBuilder.addSplit()`), which pushes keys toward the screen edges. |
| Side padding | Yes, per orientation/split (`PREF_SIDE_PADDING_SCALE`). Keep at 0 so keys hug the edges. |
| iPad-style layout | Partly. There is a tablet functional-keys layout (`assets/layouts/functional/functional_keys_tablet.json`): delete top right, Enter on row 2 right, shift on both sides of row 3, emoji/".com" at the bottom right. HeliBoard only uses it when Android reports a tablet-sized screen (`Settings.isTablet()`, `config_screen_metrics >= 3`). The Odin probably counts as a phone, so it gets the phone layout. HeliBoard's settings have a "secondary layouts" screen where the functional keys layout can be replaced with a custom one, so you can paste the tablet layout in by hand. |
| Custom bottom row | Yes. Functional key layouts are JSON and can be edited in Settings (custom layouts), so the bottom row order is fully configurable at runtime. |

## The "empty row with a down arrow" under the keyboard

That strip is not part of HeliBoard's layout. It is Android's gesture-navigation bar for keyboards, which draws a "hide keyboard" arrow and a keyboard switcher under any keyboard (Gboard included). So rearranging HeliBoard's rows alone won't remove it. Options, cheapest first:

1. Device setting: switch Odin's navigation mode or hide the navigation bar (AYN's settings), then check whether the strip goes away.
2. Code: on newer Android versions a keyboard can draw its own content into that strip and turn the system buttons off. Whether the Odin 2 Portal's Android version allows it needs an on-device test. A screenshot of the keyboard as it looks now would help me confirm what that row is.

## Proposed changes in the fork

1. **Odin defaults** (small, `latin/settings/Defaults.kt`): split keyboard on in landscape, a wide split spacer, zero side padding, and the tablet functional layout by default (treat a wide/landscape screen as a tablet in `Defaults.kt` line 34 instead of only real tablets).
2. **iPad-like functional layout** (new `functional_keys_odin.json`): row 2 Enter on the right, shift on both sides of row 3, delete top right, and a bottom row like `123 · emoji · [space] · 123 · hide`. In split mode, space is split across the gap (HeliBoard already handles a wide space key straddling the split).
3. **Bottom-row tweaks**: whatever order you want; it's one JSON file, so iterating is cheap.
4. **Nav strip**: only after step 1 of the section above shows whether the device setting is enough.
5. **Package rename** (`applicationId` to e.g. `com.odin.kb`) so it installs alongside HeliBoard without clashing.

## Building without installing Android Studio

HeliBoard needs JDK 17+, Android SDK platform 37, and the NDK 28 (it has C++ dictionary code). Your existing `odin-sync-toolchain` has platform 35 and build-tools 35 only, so a local build would add platform 37 and the NDK (about 1 GB+).

**Chosen: GitHub Actions on the fork (github.com/duhmojo/odin-kb).** `.github/workflows/build-debug-apk.yml` now also runs on every push to `main`. Download the `HeliBoard-debug` APK from the run's page and sideload it. Nothing is installed locally.

**Alternative: local build, reusing `odin-sync-toolchain`** (JDK 21 and `cmdline-tools` are already there). Two ways:

- **A. Full source build:** `sdkmanager "platforms;android-37" "build-tools;36.0.0" "ndk;28.0.13004108"`. The platform and build-tools are about 150 MB; the NDK is roughly 700 MB to download and 2 GB on disk.
- **B. Skip the NDK (recommended):** we don't need to change HeliBoard's C++ dictionary code, so the fork can ship the prebuilt `libjni_latinime.so` taken from an official HeliBoard release APK (in `app/src/main/jniLibs/`) and turn off `externalNativeBuild`. Then only the platform and build-tools are added (about 150 MB). The fork must stay on the same native code version as that release; the JNI code rarely changes.

Either way, the first Gradle run also downloads Gradle 9.7 and the library dependencies (a few hundred MB, cached under `~/.gradle`). That happens with any build method.

**Fallback: Gitea runner.** A Docker build image (e.g. JDK 17 plus the same SDK packages) running `./gradlew assembleDebug`, with the APK as the job artifact. Same downloads, but they live on the server instead of your PC.
