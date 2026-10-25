# MI4A Desktop

小米电视 4A（`MiTV4A`，板卡代号 `xmen`，`gxbaby` / `txl_xmen_t33`，Amlogic）的
**Android 6.0.1（API 23）启动器**。

这是 [CleanYourTV](https://github.com/Gathin23/cleanyourtv) 启动器的一个 API 23 移植版本。
上游的启动器是 `minSdk 26`（Android 8.0），装不上 Android 6 的电视，所以有了这个仓库。

**当前状态**：可以编译、可以安装、可以作为普通应用正常使用。
**Home 接管做不到** —— 原因见下面「限制」一节，这不是 bug，是系统能力缺失。

## 它是什么

- 应用包名 `com.peter.tvhome.xmen`，应用名 `TV Home - Xmen API23`
- Java 命名空间仍是 `com.gathin.tvhome`（继承自上游）
- `minSdk 23` / `targetSdk 34` / `compileSdk 34`
- **没有 `INTERNET` 权限**，没有任何网络请求、遥测、后台服务或轮询
- 使用 core-library desugaring 把 `java.time`、函数式接口、集合方法回移到 API 23

## 编译

需要：**JDK 17**、**Android SDK platform 34**、**build-tools 34.0.0**、Python 3。

```bash
# Windows
$env:JAVA_HOME    = 'C:\path\to\jdk17'
$env:ANDROID_HOME = 'C:\path\to\android-sdk'
pwsh .\tools\build-xmen-api23.ps1

# Linux / macOS
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
bash launcher/verify.sh
```

Gradle wrapper 固定在 8.9 并校验 sha256。Release 产物在
`launcher/app/build/outputs/apk/release/app-release.apk`。

`tools/build-xmen-api23.ps1` 除了编译，还会验证：JVM 测试全过、lint 无 Error、
`minSdkVersion=23`、无 `INTERNET` 权限、DEX 是 `035`（API 23 兼容）、
`java.time` 与函数式接口的回移类确实在包里。

## 安装

```bash
adb connect <tv ip>:5555
adb install -r apk/com.peter.tvhome.xmen-2.6-xmen-api23.apk

# 当普通应用打开（不会抢 Home）
adb shell am start -n com.peter.tvhome.xmen/com.gathin.tvhome.MainActivity
```

`apk/` 目录下的 APK 是本仓库自己编译出来的，与源码一致（见下面的校验值）。
上游 CleanYourTV 不允许在仓库里放预编译二进制，这个仓库是独立移植，按自己的规则来。

## 在 MiTV4A 上的实测结果

| 项目 | 结果 |
|---|---|
| 安装 | `pm install` Success |
| 启动 | 正常，中文界面完整渲染 |
| 应用库 | 识别到 21 个应用，带「添加到主屏幕 / 已在主屏幕」状态 |
| D-pad 四向 | RIGHT / LEFT / DOWN / UP 焦点均不丢失 |
| 崩溃 | logcat 无 FATAL，无 AndroidRuntime 异常 |
| 内存 | 约 47 MB（Native 9 MB + Java 16 MB） |
| APK 大小 | 708,399 字节（691.8 KiB） |
| MD5 | `e81ffbcc957a7396aedf0d9b61a53cc8` |

**未验证**：物理遥控器按键、HDMI/ tuner 播放、冷启动断电重启、长时间运行。

## 限制：为什么换不了桌面

这台电视上，启动器**无法成为 Home**，原因有三层：

1. **Android 6 没有接管命令。** `cmd package set-home-activity` 是 Android 7 才有的，
   在这台机器上 `cmd` 不存在。
2. **系统的 Home 选择对话框从不弹出。** `preferred_home_activity` 是 `null`，
   但按 Home 直接走固件写死的路径（先进屏保广告 `com.xiaomi.tv.gallery/.ad.AdActivity`，
   再到 PatchWall `com.mitv.tvhome`），对话框从未出现。
3. **注入的 Home 键到不了启动器。** 这是固件行为，`adb shell input keyevent 3` 无效。

移植版里有一个 `HomeKeyService`（无障碍 + UsageStats 回退方案）：后台检测到 PatchWall
持续在前台 6 秒就把用户拉回来。它需要显式授权，且每次按 Home 都要先看几秒 PatchWall。
**默认不启用**，是否启用由机主决定。

启用方法（会保留其他无障碍服务）：

```bash
adb shell settings put secure enabled_accessibility_services \
  com.xiaomi.mitv.karaoke.service/com.looststone.puremic.voice.service.accessibility.PuremicKeyService:\
com.xiaomi.voicecontrol/com.xiaomi.voicecontrol.VoiceAccessibilityService:\
com.peter.tvhome.xmen/com.gathin.tvhome.HomeKeyService
adb shell appops set com.peter.tvhome.xmen GET_USAGE_STATS allow
```

撤销就是把上面那串里属于 `com.peter.tvhome.xmen` 的一段删掉，再把 appops 设回默认。

## 这台电视的固件为什么停更

```
型号        MiTV4A
板卡        xmen / gxbaby / txl_xmen_t33（Amlogic Txl，2015-2016 年中端芯片）
系统        Android 6.0.1
固件        20.3.9.2061，编译于 2020-03-09
安全补丁    2017-04-01
```

小米在 2020 年 3 月发了最后一个版本后放弃了这块板卡，**从未为它做过 Android 8+ 的官方镜像**
（同族的 MiTV4-ANSM0 也一样，停在 `20.3.6.2057`）。所以：

- 主流 TV 应用（哔哩哔哩、当贝市场、各视频 TV 版）最低要求 Android 7/8，装不上
- 没有 root：`adb root` 返回 `adbd cannot run as root in production builds`，`su` 不存在
- `/system` 分区的预装卸不掉，`pm uninstall` 只能删 `/data/app` 里的第三方预装

## 真正有效的改造：DNS 层拦广告

不改系统、不需要 root、随时可回退。在**路由器**的 DHCP 设置里把 DNS 改成：

```
94.140.14.14
94.140.15.15
```

这是 AdGuard DNS，会拦截小米的广告投放接口和遥测上报。实测效果（6/7 被挡）：

| 域名 | 改动前 | 改动后 |
|---|---|---|
| `api.ad.xiaomi.com` | 218.30.103.85 | BLOCKED |
| `data.mistat.xiaomi.com` | 218.92.226.6 | BLOCKED |
| `tracking.miui.com` | 106.120.178.63 | BLOCKED |
| `sdkconfig.ad.xiaomi.com` | 106.38.242.68 | BLOCKED |
| `test.ad.xiaomi.com` | 220.181.106.25 | BLOCKED |
| `ad.mi.com` | 220.181.52.24 | 仍解析（不在 AdGuard 公开名单） |
| `ad.xiaomi.com` | BLOCKED | BLOCKED |

正常服务零误伤（`account.xiaomi.com`、`app.market.xiaomi.com`、`v.qq.com`、
`www.baidu.com` 全部正常）。

想恢复原样就把路由器 DNS 改回 `223.5.5.5`。

`tools/check-dns-adb.ps1` 可以在电视上直接验证拦截效果。

## 可以在电视上直接关的隐私项

不需要 root，`settings put` 在这台机器上可用，随时可还原：

```bash
adb shell settings put secure upload_log_pref 0       # 关日志上传（原值 1）
adb shell settings put system is_shown_rec_apps 0     # 关应用推荐（原值 1）
```

还原就是把 0 改回 1。

## 目录结构

```
launcher/                  启动器源码（189 个文件，1.06 MB）
  app/
    build.gradle           minSdk 23、core-library desugaring
    src/main/java/         58 个 Java 源文件
    src/main/res/          布局、drawable、28 种语言的 strings
    src/test/java/         30 个 JVM 测试
  gradle/wrapper/          Gradle 8.9，sha256 固定
  verify.sh                编译 + 验证（Linux/macOS）
apk/                       编译产物
docs/
  xiaomi-mitv4-xmen-2061.md  这台电视的完整探测记录
tools/
  build-xmen-api23.ps1     编译 + 验证（Windows）
  check-dns-adb.ps1        DNS 拦截效果验证
```

## 安全提醒

- 端口 5555 是明文传输，只靠一个 ADB 密钥对。用完请在电视上关掉
  「设置 → 帐号与安全 → ADB调试」，然后重启电视。
- 这台电视的固件没有安全补丁（2017-04-01），已知漏洞都不会修，所以更要关 ADB。

## 许可

继承上游 CleanYourTV 的 MIT 许可，见 `LICENSE`。
`launcher/app/src/main/res/font/outfit.ttf` 使用 SIL Open Font License，见
`launcher/FONT-LICENSE-OFL.txt`。

本仓库不含任何预编译二进制（除 `apk/` 下自己编译的产物）、不含设备序列号、
Wi-Fi 名称、IP 地址或帐号信息。
