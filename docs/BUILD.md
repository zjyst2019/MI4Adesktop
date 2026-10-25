# 从零构建（另一台电脑）

**结论**：仓库里有全部源码、Gradle wrapper（JAR + Gradle 8.9 的 sha256 校验）、JVM 测试和
构建/校验脚本，不需要旧电脑的 `local.properties`、Gradle 缓存、Android SDK 或签名密钥。
但新电脑必须自己安装下面的工具，并且**首次构建需要联网**下载 Gradle 和 Maven 依赖。

## 必需的外部依赖

| 依赖 | 版本 / 说明 | 用途 |
|---|---|---|
| JDK | **17**（Temurin、Microsoft、Amazon 等均可） | Gradle / AGP 8.5.2；源码 `sourceCompatibility 17` |
| Android SDK Platform | `platforms;android-34` | `compileSdk 34` / `targetSdk 34` |
| Android SDK Build-Tools | `34.0.0` | AGP 8.5.2 默认使用的版本 |
| Android SDK Platform-Tools | 任意近期版本 | `adb` 连接、安装 APK（构建本身不强制需要） |
| Python | **3.x** | 构建后的 APK 校验脚本 |
| 网络 | 首次构建 | 下载 Gradle 8.9、AGP、AndroidX、desugar_jdk_libs |
| Git | 任意近期版本 | 克隆仓库 |
| PowerShell | Windows 自带 5.1 即可；PowerShell 7 也可以 | Windows 构建脚本 |

不需要：旧电脑的 `~/.android/debug.keystore`、`local.properties`、`~/.gradle`、Android SDK
安装路径、仓库之外的任何项目文件。

## Windows

1. 安装 JDK 17，设置环境变量：

   ```powershell
   $env:JAVA_HOME = 'C:\path\to\jdk17'
   ```

2. 安装 Android 命令行工具（command-line tools），然后用 `sdkmanager` 安装平台和构建工具：

   ```powershell
   sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
   sdkmanager --licenses
   $env:ANDROID_HOME = 'C:\path\to\android-sdk'
   ```

   `ANDROID_HOME` 和 `ANDROID_SDK_ROOT` 指向同一个目录都可以。

3. 安装 Python 3，并确认命令可用：

   ```powershell
   python --version
   ```

   构建脚本调用的是 `python`。如果只装了 `py` 启动器，请把 Python 加入 PATH，或在脚本里
   把 `$py = 'python'` 改成 `'py -3'`。

4. 克隆并构建：

   ```powershell
   git clone https://github.com/zjyst2019/MI4Adesktop.git
   cd MI4Adesktop
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\build-xmen-api23.ps1
   ```

   PowerShell 7 用户也可以运行：

   ```powershell
   pwsh -File .\tools\build-xmen-api23.ps1
   ```

5. 产物和校验输出：

   ```text
   launcher/app/build/outputs/apk/release/app-release.apk
   ```

   脚本会依次执行 `testReleaseUnitTest lintRelease assembleRelease`，然后检查包名、
   `minSdk 23`、没有 `INTERNET` 权限、lint 无 Error、测试全过、DEX 为 035、
   `java.time` / 函数式接口回移类存在，最后打印 APK 大小。

首次构建会从 `services.gradle.org` 下载 Gradle 8.9，从 `google()` / `mavenCentral()`
下载 AGP 和 AndroidX 依赖。之后同一台机器上的 `~/.gradle` 缓存可以复用；仓库本身
不包含这些依赖。

如果访问 Google Maven 或 Gradle 需要代理，可以临时设置：

```powershell
$env:HTTPS_PROXY = 'http://127.0.0.1:7897'
$env:HTTP_PROXY  = 'http://127.0.0.1:7897'
```

或者写入 `~/.gradle/gradle.properties`（**不要提交到仓库**）：

```properties
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=7897
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=7897
```

## Linux / macOS

需要 JDK 17、Android SDK（同上）、Python 3 和 `bash`。设置环境变量后运行：

```sh
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
bash launcher/verify.sh
```

`verify.sh` 会在 `launcher/` 里执行 `./gradlew`。仓库已经给 `gradlew` 和 `verify.sh`
设置了可执行位；如果克隆后仍然报 `Permission denied`，手动补一次：

```sh
chmod +x launcher/gradlew launcher/verify.sh
```

如果脚本报 `bad interpreter` 或行尾相关错误，说明工作区被转成了 CRLF，改成 LF：

```sh
sed -i 's/\r$//' launcher/gradlew launcher/verify.sh
```

`verify.sh` 调用的是 `python3`。只有 `python` 命令的系统可以装 `python3` 包，或把脚本里的
`python3` 改成 `python`。

## 锁定的版本

| 组件 | 版本 |
|---|---|
| Gradle | 8.9（`gradle-wrapper.properties` 带 sha256） |
| Android Gradle Plugin | 8.5.2 |
| compileSdk / targetSdk | 34 |
| minSdk | 23 |
| Java source/target | 17 |
| desugar_jdk_libs | 2.0.4 |
| AndroidX RecyclerView | 1.3.2 |
| AndroidX DynamicAnimation | 1.0.0 |
| JVM 测试 | JUnit 4.13.2 |

Gradle wrapper 的 JAR 和 `gradlew` / `gradlew.bat` 都在 `launcher/gradle/wrapper/` 和
`launcher/` 下，不需要旧电脑的 Gradle 安装。

## 重新构建的 APK 与 `apk/` 里的 APK

- 构建使用 Android Gradle Plugin 自动生成的**调试密钥**（通常在 `~/.android/debug.keystore`）。
  新电脑生成的密钥不同，因此重新构建的 APK 签名不同，MD5/SHA-256 也会不同（zip 时间戳和签名
  都会影响字节）。
- 如果电视上已经装了 `apk/` 里的版本，用新构建的 APK 覆盖安装可能报
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE`（签名不一致）。先在电视的“设置 → 应用”里卸载旧版，
  再安装新包；**不要卸载 `com.mitv.tvhome`**（那是原桌面）。
- 只想装一个可用的包、不打算改代码时，直接使用 `apk/com.peter.tvhome.xmen-2.6.3-xmen-api23.apk`
  即可，不需要构建。

## 常见问题

| 现象 | 处理 |
|---|---|
| `JAVA_HOME` 未设置 / 找不到 Java | 安装 JDK 17，设置 `JAVA_HOME`，重开终端 |
| `ANDROID_HOME` 未设置 / 找不到 SDK | 安装 SDK 并设置 `ANDROID_HOME`；确认 `platforms/android-34` 存在 |
| SDK 许可未接受 | 运行 `sdkmanager --licenses` 并全部接受 |
| 下载 Gradle/依赖超时 | 配置代理（见上）；确认能访问 `services.gradle.org`、`dl.google.com`、`repo.maven.apache.org` |
| `python: command not found`（Windows） | 安装 Python 3 并加入 PATH，或把脚本里的 `python` 改成 `py -3` |
| `python3: command not found`（Linux/macOS） | 安装 `python3`，或把 `verify.sh` 里的 `python3` 改成 `python` |
| Linux 下 `Permission denied: ./gradlew` | `chmod +x launcher/gradlew launcher/verify.sh` |
| PowerShell 拒绝执行脚本 | 使用文档里的 `-ExecutionPolicy Bypass`，或 `Set-ExecutionPolicy -Scope Process Bypass` |
| 安装新包报签名不一致 | 先在电视上卸载旧版应用，再安装；原桌面不要动 |
