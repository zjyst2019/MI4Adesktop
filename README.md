# MI4A Desktop

小米电视 4A（`MiTV4A`，板卡代号 `xmen`，`gxbaby` / `txl_xmen_t33`，Amlogic）的
**Android 6.0.1（API 23）启动器**。

这是 [CleanYourTV](https://github.com/Gathin23/cleanyourtv) 启动器的一个 API 23 移植版本。
上游的启动器是 `minSdk 26`（Android 8.0），装不上 Android 6 的电视，所以有了这个仓库。

**当前版本**：2.6.7（versionCode 14）
**当前状态**：可编译、可安装；在 MiTV4A 上实体遥控器的 Home 键可接管，按 Home 不再闪 PatchWall；
开机约 1.1 秒后切到自建桌面，且**重启后 Home 键仍持续生效**（2.6.7 修复）。原桌面保持启用，
不卸载、不禁用、不需要 root。

## 它是什么

- 应用包名 `com.peter.tvhome.xmen`，应用名 `TV Home - Xmen API23`
- Java 命名空间仍是 `com.gathin.tvhome`（继承自上游）
- `minSdk 23` / `targetSdk 34` / `compileSdk 34`
- **没有 `INTERNET` 权限**，没有任何网络请求、遥测或分析
- 使用 core-library desugaring 把 `java.time`、函数式接口、集合方法回移到 API 23
- Home 接管是**可选**的无障碍服务：启用后每秒查询一次本机前台应用（UsageStats），
  不读取屏幕内容、不联网；不启用时它只是一个普通启动器应用

2.6.3 相对上游/早期移植版的三处本地改动：

1. Home 助手的判定阈值 6 秒 → 1 秒。
2. `openHome()` 去掉 `FLAG_ACTIVITY_RESET_TASK_IF_NEEDED`，避免配合 `clearTaskOnLaunch`
   重建任务栈。
3. `config.xml` 的 `key_filter` 设为 `true`，让实体 Home 键在原桌面启动前进入
   `onKeyEvent` 并被消费，从而直接打开自建桌面。

第 3 项会请求全局按键过滤。部分固件（已知 TCL Google TV 12）在启用后会丢失按键连发；
本机 MiTV4A 实测长按音量仍能连续调节。若你的电视出现连发丢失，把 `key_filter` 改回
`false` 重新编译即可，Home 会退回“先显示原桌面、约 1 秒后切换”的行为。

### 2.6.7：修复开机后 Home 回到 PatchWall

**根因**：这台固件在开机后的**第一次无障碍绑定不会启用按键过滤**。服务已启用、已绑定，
也能收到 PatchWall 的窗口事件，但实体 Home 键仍然落到 `com.mitv.tvhome`；再次调用
`setServiceInfo(FLAG_REQUEST_FILTER_KEY_EVENTS)` 也无效，只有让系统**重新绑定服务**
（手动关/开无障碍，或重装 APK）后才恢复。过滤失效时，UsageStats 兜底发出
`startActivity` 还会被 MIUI 拦截（`Activity start request from <uid> stopped`），
所以电视会停在 PatchWall，而不是 1 秒后切回来。

**修复**：`MainActivity.onResume()` 调用 `HomeKeyService.ensureKeyFilter()`。如果当前进程
是在开机 5 分钟内启动的（即承载开机那次绑定），第一次回到桌面时强制重绑一次：只把自己的
组件从 `enabled_accessibility_services` 移除，2 秒后写回**原始完整字符串**（失败重试 3 次），
语音/K 歌等服务原样保留。每个进程只执行一次，用户自己关掉助手时不会动无障碍列表。

该操作需要 `WRITE_SECURE_SETTINGS`，安装后授予一次（权限跨重启保留），见“启用 Home 接管”。
未授予时助手会写日志并跳过，只能退回 UsageStats 兜底。

验证时注意：`input keyevent 3` 注入的 Home **不经过**无障碍过滤，不能用来判断修复是否生效；
要用实体遥控器，或用与遥控器同一输入设备的 `sendevent`（本机为 `/dev/input/event4`
`KEY_HOME` 102）。重启后连续测试 10+ 次：日志均为 `Opened HOME: remote HOME key`，
系统日志没有 stock HOME 启动，焦点始终回到自建桌面。

## 编译

仓库包含全部源码、Gradle wrapper（JAR + Gradle 8.9 的 sha256）、JVM 测试和构建脚本；
新电脑不需要旧电脑的 `local.properties`、Gradle 缓存、Android SDK 或签名密钥，但需要自行安装：

- **JDK 17**
- **Android SDK platform 34** 和 **build-tools 34.0.0**（platform-tools 用于安装 APK）
- **Python 3**（构建后的 APK 校验脚本使用）
- 首次构建需要联网下载 Gradle 8.9 和 Maven 依赖

完整的从零安装步骤、代理/离线说明、版本锁定和常见问题见 **[docs/BUILD.md](docs/BUILD.md)**。

```powershell
# Windows（PowerShell 5.1 或 7，从仓库根目录）
$env:JAVA_HOME    = 'C:\path\to\jdk17'
$env:ANDROID_HOME = 'C:\path\to\android-sdk'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\build-xmen-api23.ps1
```

```sh
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
adb install -r apk/com.peter.tvhome.xmen-2.6.7-xmen-api23.apk

# 先当普通应用打开（不会抢 Home）
adb shell am start -n com.peter.tvhome.xmen/com.gathin.tvhome.MainActivity
```

小米电视从电脑安装时会弹“安装确认”，需要在电视上按 **确定**。如果 `adb install` 一直
等待，可改为先 `adb push` 到 `/data/local/tmp/`，再执行 `pm install -r`。

`apk/` 目录下的 APK 是本仓库自己编译的产物；`apk/MD5SUMS.txt` 是对应校验值。
重新编译出的 APK 可能因 zip 时间戳/签名得到不同的 MD5，这是正常的；`apk/` 里的是本次在
电视上实测过的产物，版本号和校验值以它为准。上游 CleanYourTV 不允许在仓库里放预编译
二进制，这个仓库是独立移植，按自己的规则来。

## 启用 Home 接管

前提：原桌面保持启用；这是机主明确授权的可选步骤。不要用 `pm disable-user`、
`pm uninstall` 或 root 去动 `com.mitv.tvhome`。

```bash
# 1. 先记下当前的无障碍服务列表
adb shell settings get secure enabled_accessibility_services

# 2. 把 HomeKeyService 追加到原值末尾（保留原有服务）
adb shell settings put secure enabled_accessibility_services \
  '<原值>:com.peter.tvhome.xmen/com.gathin.tvhome.HomeKeyService'

# 3. 授予使用情况访问（UsageStats 兜底）
adb shell appops set com.peter.tvhome.xmen GET_USAGE_STATS allow

# 4. 授予安全设置写入权限（2.6.7 开机后强制重绑一次无障碍服务）
adb shell pm grant com.peter.tvhome.xmen android.permission.WRITE_SECURE_SETTINGS
```

MiTV4A 上原值示例（不同固件可能不同，以第 1 步读到的为准）：

```text
com.xiaomi.mitv.karaoke.service/com.loostone.puremic.voice.service.accessibility.PuremicKeyService:com.xiaomi.voicecontrol/com.xiaomi.voicecontrol.VoiceAccessibilityService
```

然后重启一次电视。验证：

```bash
adb shell dumpsys activity services com.peter.tvhome.xmen | grep HomeKeyService
adb shell dumpsys activity activities | grep mResumedActivity
```

预期行为：

- 按实体遥控器 Home：直接进入自建桌面，PatchWall 不出现；重启后仍然如此。
- 开机：原桌面先启动，Home 助手约 1.1 秒后切到自建桌面；实测几乎看不到 PatchWall。
- 开机后第一次回到桌面时，助手会把自己的无障碍组件移除约 2 秒再写回，以恢复按键过滤；
  这期间如果恰好按 Home，可能看到一次 PatchWall。
- 撤销：把 `enabled_accessibility_services` 恢复为第 1 步的原值，执行
  `appops set com.peter.tvhome.xmen GET_USAGE_STATS default` 和
  `pm revoke com.peter.tvhome.xmen android.permission.WRITE_SECURE_SETTINGS`，
  再到电视“设置 → 应用”里卸载自建桌面。原桌面从来没有被禁用，不需要恢复。

## 在 MiTV4A 上的实测结果

| 项目 | 结果 |
|---|---|
| 版本 | 2.6.7 / versionCode 14 |
| 安装 | `pm install` Success（电视上按一次安装确认） |
| 启动 | 正常，中文界面完整渲染 |
| 应用库 | 识别到 21 个应用，带“添加到主屏幕 / 已在主屏幕”状态 |
| 输入源 | HDMI1/2/3、TV、AV、DTMB、快速投屏均能打开 |
| D-pad 四向 | RIGHT / LEFT / DOWN / UP 焦点均不丢失 |
| 实体 Home | 直接进入自建桌面，PatchWall 不出现 |
| 开机 | 原桌面启动后约 1.1 秒切换；机主确认几乎看不到 PatchWall |
| 重启后 Home | 2.6.7 重启后从 App 里按 Home 仍由助手消费，PatchWall 不出现；连续 10+ 次一致 |
| 音量连发 | 长按音量可连续调节 |
| 重启 | Home 助手、使用情况访问、`WRITE_SECURE_SETTINGS` 均自动保留 |
| 崩溃 | logcat 未发现 FATAL / AndroidRuntime 异常 |
| 系统包 | 没有禁用或卸载任何系统包；原桌面仍启用 |
| JVM 测试 / lint | 195 个测试通过；lint 0 Error |
| APK | 709,414 字节（692.8 KiB） |
| MD5 | `e286759e731a4f0adb8f25c9a3f41573` |
| SHA-256 | `6cc8323998d28d4ea992bd3674037d201f6a2978724cd5cee160e560b717d424` |

APK 使用调试密钥签名，只适合 sideload 到自己的电视，不要发布到应用商店。

## 为什么不是“真正的默认桌面”

这台电视上，第三方启动器无法成为系统默认 Home：

1. **Android 6 没有接管命令。** `cmd package set-home-activity` 是 Android 7 才有的，
   这台机器上 `cmd` 不存在。
2. **系统的 Home 选择对话框从不弹出。** 按 Home 直接走固件写死的路径（先到 PatchWall），
   对话框从未出现。
3. **shell 没有改组件状态的权限。** `pm disable-user`、`pm disable`、`pm hide` 全部返回
   `SecurityException`；`su` 不存在，`adb root` 也不可用。

所以这里用的是“无障碍 + UsageStats + 按键过滤”的接管方式，而不是替换系统 Home：

- 原桌面始终启用，失败或卸载应用后按 Home 就会回到 PatchWall，不会黑屏。
- 开机时没有 Home 键可拦截，原桌面会先启动，助手随后切换（实测约 1.1 秒）。
- 彻底替换需要 root 或刷机；本仓库不做，也不修改系统分区。

## 这台电视的固件

```text
型号        MiTV4A
板卡        xmen / gxbaby / txl_xmen_t33（Amlogic，2015-2016 年中端芯片）
系统        Android 6.0.1（API 23）
固件        20.3.9.2061，编译于 2020-03-09
安全补丁    2017-04-01
```

小米在 2020 年 3 月后不再更新这块板卡，没有 Android 8+ 的官方镜像。因此：

- 主流 TV 应用（哔哩哔哩、当贝市场、各视频 TV 版）最低要求 Android 7/8，装不上
- 没有 root：`adb root` 返回 `adbd cannot run as root in production builds`，`su` 不存在
- `/system` 分区的预装卸不掉，`pm uninstall` 只能删 `/data/app` 里的第三方预装

## 可选的隐私/DNS 设置

不需要 root，`settings put` 在这台机器上可用，随时可还原：

```bash
adb shell settings put secure upload_log_pref 0       # 关日志上传（原值 1）
adb shell settings put system is_shown_rec_apps 0     # 关应用推荐（原值 1）
```

还原就是把 0 改回 1。

在路由器 DHCP 里把 DNS 改成 AdGuard（`94.140.14.14` / `94.140.15.15`）可以拦掉一部分
小米广告和遥测域名，不需要改电视；它不能拦 YouTube 广告。改回 `223.5.5.5` 即恢复。
`tools/check-dns-adb.ps1` 可以在电视上验证拦截效果。

## 目录结构

```text
launcher/                  启动器源码
  app/
    build.gradle           minSdk 23、core-library desugaring
    src/main/java/         54 个 Java 源文件
    src/main/res/          布局、drawable、多语言 strings
    src/test/java/         28 个 JVM 测试源文件
  gradle/wrapper/          Gradle 8.9，sha256 固定
  verify.sh                编译 + 验证（Linux/macOS）
apk/
  com.peter.tvhome.xmen-2.6-xmen-api23.apk    历史版本
  com.peter.tvhome.xmen-2.6.3-xmen-api23.apk  历史版本
  com.peter.tvhome.xmen-2.6.7-xmen-api23.apk  当前版本
  MD5SUMS.txt                                  APK 校验值
docs/
  BUILD.md                   从零构建与外部依赖说明
  xiaomi-mitv4-xmen-2061.md  这台电视的探测记录
tools/
  build-xmen-api23.ps1     编译 + 验证（Windows）
  check-dns-adb.ps1        DNS 拦截效果验证
```

## 安全提醒

- 端口 5555 是明文传输，只靠一个 ADB 密钥对。用完请在电视上关掉“ADB 调试”，
  撤销 USB 调试授权，然后重启；确认端口已关闭。
- 这台电视的固件没有安全补丁（2017-04-01），已知漏洞都不会修，所以更要关 ADB。
- 本仓库不含设备序列号、Wi-Fi 名称、设备 IP、帐号或 ADB 密钥；文档中的 DNS 地址是
  公共解析器地址。

## 许可

继承上游 CleanYourTV 的 MIT 许可，见 `launcher/LICENSE`。
`launcher/app/src/main/res/font/outfit.ttf` 使用 SIL Open Font License，见
`launcher/FONT-LICENSE-OFL.txt`。
