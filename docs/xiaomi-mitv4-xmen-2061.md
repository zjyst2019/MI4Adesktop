# Xiaomi MiTV4A (xmen), Android 6.0.1

Tested on a retail MiTV4A, `ro.product.model=MiTV4A`, device `xmen`, board `gxbaby`
(`ro.boot.platform_name=txl_xmen_t33`, Amlogic), Android 6.0.1 (API 23), build `MHC19J`,
firmware `20.3.9.2061` (build 2061, 2020-03-09), security patch 2017-04-01, `armeabi-v7a`,
1920×1080 at 320 dpi. China PatchWall build, 1.6 GB usable RAM, /data 5.0 GB with 756 MB free,
87 packages, single user (0). No Play Store and no Play Services; the only Google component
is `com.google.android.webview`.

Same board family as [MiTV4-ANSM0](xiaomi-mitv4-ansm0.md) (xmen / gxbaby), a newer firmware.

This is not a disable list. **Every shell disable channel is refused on this firmware** — see
below. Do not run `tools/disable.sh` here, and do not run a full `tools/restore.sh`.
Nothing in this note uninstalls or disables a package.

## Blocked: all shell disable channels

| Channel | Result |
|---|---|
| `pm disable-user --user 0 <pkg>` | `SecurityException: Permission Denial: attempt to change component state ... uid=2000, package uid=...` |
| `pm disable --user 0 <pkg>` | same `SecurityException` |
| `pm disable-until-used --user 0 <pkg>` | same `SecurityException` |
| `pm hide <pkg>` | `SecurityException: Neither user 2000 nor current process has android.permission.MANAGE_USERS` |
| `pm block` | not a command on API 23 |
| `su` | absent |

Tried on a third-party package (`cn.jj.tv`, uid 10015) and a system package
(`com.mitv.care`, uid 1000): both refused. Status re-read after each failure, never changed.

`dumpsys package com.android.shell` shows shell holds only `INSTALL_PACKAGES` and
`DELETE_PACKAGES` — no `CHANGE_COMPONENT_ENABLED_STATE`, no `MANAGE_USERS`. This is not one
package: this firmware took the shell's component-state permission away entirely. Do not retry
in a loop; stop and say so.

## What still works

- `settings put/get/delete global|secure|system` — write verified with a temporary key, then deleted.
- `appops set <pkg> <op> deny|allow` — one benign op changed and restored; the write took effect.
- `am force-stop <pkg>` — returns 0 (temporary; the app comes back by itself).
- `pm install` — the installer itself works (a dummy APK answered
  `INSTALL_FAILED_INVALID_APK`, not a verification block).
- `screencap` (`tools/tv.py --screenshot`).

## Already disabled before this session

`com.xiaomi.mitv.advertise` and `com.mitv.care` are `enabled=2`, `stopped=true` — turned off in
the TV's own interface, not from the shell.

## Memory users (PSS)

| Process | PSS |
|---|---|
| `com.mitv.tvhome` (PatchWall) | 325 MB |
| `com.xiaomi.tv.gallery` (screensaver) | 183 MB |
| `com.xiaomi.voicecontrol` | 69 MB |
| `com.xiaomi.mi_connect_service` | 54 MB |
| `com.xiaomi.mitv.appstore` | 35 MB |
| `com.xiaomi.smarthome.tv` | 30 MB |

## Privacy settings that can be changed

| Key | Namespace | Current |
|---|---|---|
| `upload_log_pref` | secure | 1 (on) |
| `is_shown_rec_apps` | system | 1 (on) |
| `personalized_recommendation` | system | 0 (off) |
| `individual_privacy_enabled` | global | 1 |
| `captive_portal_detection_enabled` | global | 0 |

These are the TV's own settings, not a disable list; record the value before changing it.

## Launcher

The launcher in this repository is `minSdk 26` and this set is SDK 23, so the published build
refuses to install (`INSTALL_FAILED_OLDER_SDK`). This host has no JDK, Android SDK or Gradle
cache by default either.

The API 23 port lives outside this repository: [Xmen Android 6 port](https://github.com/yukunwu/cleanyourtv/tree/xmen-android6/ports/xmen-android6)
(`com.peter.tvhome.xmen`, minSdk 23, core-library desugaring, no INTERNET permission), already
smoke-tested on the same board family. Building it needs JDK 17, Android SDK platform 34 and
build-tools 34.0.0. It carries a UsageStats Home fallback that needs an explicit owner decision
and the stock launcher left enabled; read its README before enabling anything.

## Settings screens worth noting

The stock Settings app is `com.xiaomi.mitv.settings`. Captured for reference:

- 帐号与安全 (`security.json`): 安装未知来源的应用 = 允许, ADB调试 = 允许 — leave both as the owner
  set them; the launcher half needs a sideload to work.
- 关于 (`about.json`): 系统更新, MIUI TV版本 1.3.125 (稳定版), 智能更新 = 关闭, 关于本机, 联系客服, 磁盘信息.
- 电视管家 (`tvmgr.json`): V 2.7.61 (30040036) — 一键优化, 内存加速, 垃圾清理, 软件卸载, 深度清理,
  U盘安装包, 装机必备, 智能清理, 网络测速. This is the app that fills PatchWall's 推荐 row with
  preloads.
- 连接设置 (`after.json` / `dialog.json`): 有线网络, 检测, 网络检测, 网络测速, 高级,
  允许弹出登录认证页面 (currently 关闭).
