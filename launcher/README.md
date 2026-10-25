> **Note on this copy.** This is the port's own README, kept verbatim below for
> provenance. Two things no longer apply in this repository: the `cd
> ports/xmen-android6` instruction (here the port is the whole repo, so just
> `cd launcher`), and the "do not publish a prebuilt binary" line (this repo
> ships its own build in `../apk/`). Read the top-level `../README.md` first.

# CleanYourTV: Xmen Android 6 Port

Independent, experimental source port for Xiaomi MiTV4-ANSM0 (xmen),
Android 6.0.1 / API 23. Derived from [CleanYourTV](https://github.com/Gathin23/cleanyourtv)
commit `56cd3e81c062244ea313899d5af8d75294f2daae`, version 2.6, under its MIT license.
This is not an upstream-supported release. Basic on-device launch and D-pad
navigation were verified on the target TV on 2026-10-22; full validation remains.

## Build

From the fork's root, first run `cd ports/xmen-android6`. The original root
`launcher/` and install scripts still belong to upstream and do not install this
port. This isolated directory preserves the independently tested source project.

Requirements: JDK 17, Android SDK platform 34, build-tools 34.0.0 and Python 3
for verification. Set JAVA_HOME and ANDROID_HOME, then run:

```sh
bash verify.sh
```

The Gradle wrapper is pinned to 8.9 and verifies its download checksum.
Release output: `launcher/app/build/outputs/apk/release/app-release.apk`.
Do not publish a prebuilt binary as a substitute for reproducible source.

Local validation on 2026-10-22: 195 JVM tests passed; API 23 lint has zero
errors (existing non-fatal warnings remain); release is about 690 KiB. APK
metadata reports minSdk 23, its v1 signature verifies for API 23, and DEX 035
contains the time and functional-interface backport implementations. These
checks do not replace a cold launch and remote-navigation check on the TV.

On-device smoke test on 2026-10-22: direct local-network ADB transfer passed
checksum verification, package installation returned Success, explicit activity
launch returned Status ok (~857 ms), and the home UI rendered in Chinese.
Right/Left/Down/Up injections each retained this activity's window focus and
screenshots showed movement between the dock and app grid. Sampled logs had no
fatal exception or missing-API error. Stock Home remained enabled and the
disabled-package list was unchanged. That initial smoke test performed no default-Home selection or accessibility
grant. Later owner-authorized Home compatibility testing is documented below. Physical remote, streaming playback, HDMI/tuner, reboot
and prolonged runtime remain unverified. A prior navigation sample switched to
stock Home during overlapping device use, so only the focus-checked run counts.

## Compatibility changes

- Separate application ID `com.peter.tvhome.xmen`; Java namespace stays
  `com.gathin.tvhome`. This cannot replace the original launcher package.
- Minimum SDK 23, target/compile SDK 34. No INTERNET permission is added.
- Core-library desugaring (`desugar_jdk_libs:2.0.4`) backports java.time,
  functional interfaces and supported Java collection methods. Lambda syntax
  desugaring alone is insufficient. The extra library increases APK size;
  upstream's 480 KiB size budget is not claimed for this independent port.
- Guard newer framework calls: API 23 uses network-request callbacks, legacy
  locale lookup, standard TV input labels, and no overlapping-render override.
  Hidden-input filtering and custom input labels need API 24 and degrade on 23.
- Package lists are re-queried on resume on API 23, as change sequences need 26.
- API 23 uses system sans-serif fonts, not resource/variable fonts. API 26+
  retains resource fonts. The appearance is similar, not pixel-identical.
- Wallpaper decode retains software bitmaps below API 26. Captions use buffered
  UTF-8 file I/O. Hardware bitmap conversion stays available on API 26+.
- Accessibility helper unlock checks are guarded on API 23. Helpers are NOT
  enabled automatically. Boot-to-live-TV is unavailable below API 24 and stays off.
- The unrelated default MediaTek Live TV package is cleared. Xiaomi input/tuner
  selection uses the device's native Settings receiver, configured as the source
  panel: `com.xiaomi.mitv.settings/.SettingsBroadcastReceiver`, action
  `com.xiaomi.mitv.settings.INPUTSOURCE_POPUP`. The port supports explicit
  receiver panels in addition to activity/service panels. On-device request
  through the port displayed HDMI1/2/3, TV, AV and DTMB; no source was selected
  during this check. Playback is still unverified. This firmware's standard
  TvInputManager only lists ATV/DTV despite HDMI services being present.
- App library and its top-bar grid button are enabled. Both Home and the library
  merge LEANBACK_LAUNCHER and LAUNCHER activities plus two verified exported
  legacy Xiaomi entries (App Store and Media Explorer). The library reported
  16 applications on the target TV. Explicit user-hidden/removed choices win.
  Arbitrary MAIN activities are never included (factory/test entry risk).
- NewApi and InlinedApi lint findings are errors; no compatibility suppression
  or upstream guard bypass is used.

## Safety and validation boundary

This project deliberately excludes upstream build-and-install scripts, which
assume Android 7+ `cmd package` Home operations. `verify.sh` builds only: no ADB,
installation, Home takeover, disabling, rebooting or permission grants.

Before any separate user-authorized installation: preserve a current baseline
backup, leave `com.mitv.tvhome` enabled, install only this locally built port and
open its activity as an ordinary app first. Home takeover requires a separate
explicit request; the owner authorized Home/boot compatibility on 2026-10-22.
Check cold launch, return from apps, D-pad from every edge, settings, Wi-Fi,
HDMI/tuner and crash logs. Accessibility keys require real remote presses.
Only consider the TV's own Home chooser after successful runtime checks and an
explicit user decision; never disable stock Home to force the chooser.

Static/JVM tests cannot prove compatibility with Xiaomi's vendor framework,
Home allowlist, sideload verification or hardware rendering. If the TV rejects
installation, stop; do not bypass protection or retry other package identities.
Close debugging and stop the user's loopback relay when device work finishes.

## Xmen Home compatibility (owner-authorized)

References: Aerial TV issues [209](https://github.com/pengchujin/aerial-tv/issues/209)
and [81](https://github.com/pengchujin/aerial-tv/issues/81). Reports describe
firmware-dependent missing HOME key/window events and late PatchWall boot takeover;
they are evidence for a compatibility strategy, not proof this device behaves identically.

This TV has no Android 7+ `cmd package set-home-activity` command and no standard
HOME_SETTINGS handler. The Home helper binds, but stock Home accessibility events
are unreliable here. Injected Home keys do not reach its key callback; the physical
remote does when key_filter=true, which is how this port intercepts Home before
PatchWall starts. A true system-level default Home replacement is NOT possible without
root. This firmware's first accessibility bind after a boot does not enable key
filtering at all; 2.6.7 forces one fresh bind from Home (see below).

The independent port now includes a local UsageStats fallback in HomeKeyService:

- Requires the owner's explicit accessibility and usage-access grants. No INTERNET
  permission, screen-content access, notification listener or root is used.
- Queries local activity events once per second while interactive and unlocked;
  tracks every application's foreground/background transitions, clearing stale
  evidence on screen-off, lock, permission loss or clock rollback.
- Redirects only verified `com.mitv.tvhome.MainActivityUserMode` / `MainActivity`
  after one second of foreground evidence (local change from upstream's six seconds). Never matches the whole stock package,
  BootActivity, settings or video-player classes. Each foreground event gets one
  attempt; evidence older than two minutes is rejected.
- Global key filtering is on in the local Xmen build to intercept the physical Home key
  before PatchWall starts (config.xml key_filter=true). On the target MiTV4A the physical
  Home key then opens this port directly; holding volume still repeats. Revert to false if
  holding volume no longer repeats; some firmware drops key auto-repeat once a service
  filters keys. With key_filter=false, Home falls back to the one-second UsageStats path
  and PatchWall can be visible briefly.
- This local port also drops FLAG_ACTIVITY_RESET_TASK_IF_NEEDED from the helper's
  startActivity. With clearTaskOnLaunch it recreated the task on every Home press
  (~3 s on the target TV); NEW_TASK alone resumes in under 200 ms (`am start -W`).
- **The first accessibility bind after a boot does not filter keys on this firmware.**
  The helper is enabled, connected, and receives stock-Home window events, but the
  physical Home key still goes to PatchWall; calling `setServiceInfo` with
  `FLAG_REQUEST_FILTER_KEY_EVENTS` again does not restore it. A bind made later (the
  service re-enabled by hand, or after an install) does filter. Since 2.6.7 Home
  forces that fresh bind once per boot-time process: it takes only its own component
  off `enabled_accessibility_services`, puts the exact original string back two
  seconds later (three retries), and leaves the voice/karaoke entries untouched. This
  needs `WRITE_SECURE_SETTINGS`, granted by hand after install (see below); without it
  the helper logs that filtering can stay off and the UsageStats redirect is all that
  remains. Verified on the target TV after a reboot with raw evdev Home presses from
  inside an app (the same input device as the remote; `input keyevent` bypasses
  accessibility filtering and proves nothing): `Opened HOME: remote HOME key`, no
  stock-Home launch, focus back on this port, over 10+ cycles.
- On-device HOME injection (this does not exercise key_filter): a three-second screenshot
  showed stock Home, later focus showed this port; log recorded `usage foreground (1s)`.
  Settings stayed in front for a 12-second observation. The physical remote with
  key_filter=true was verified separately on 2026-10-25 and opens this port directly.
  Media-browser launch did not acquire focus during the injection test, so
  media-no-interruption is unverified.
- Stock Home stays enabled; no system package was disabled or uninstalled and the
  framework default has not been changed. A 2026-10-25 reboot passed: the stock HOME
  started, this port came to the front about 1.1 s later, helper/usage access persisted,
  and the owner reported almost no PatchWall. Physical remote, D-pad, long-press volume
  and a software reboot were verified in that session. Power-disconnect cold boot and
  HDMI/playback remain unverified.

Enable only after the explicit owner decision, preserving other enabled services:
`com.peter.tvhome.xmen/com.gathin.tvhome.HomeKeyService`. Usage access uses
`appops set com.peter.tvhome.xmen GET_USAGE_STATS allow`, and the post-boot rebind
needs `pm grant com.peter.tvhome.xmen android.permission.WRITE_SECURE_SETTINGS`.
To undo, remove just that service from `enabled_accessibility_services`, revoke the
grant and set the op back to its recorded state (initially no operation/default).
Preserve the voice/other service entries. The private session keeps an executable
restore script and pre-change backups; none belong in published source.
Reinstall/force-stop and firmware updates may unbind the helper; recheck binding
rather than assuming persistent operation.

## License

Original work copyright (c) 2026 Gathin; see LICENSE. Modifications are
provided under the same MIT license. No APKs, secrets or device dumps belong
in version control.
