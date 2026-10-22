# 构建链路验证报告（Smoke）

> 目的：确认本机 Android 构建链路 + Material3 Expressive API 能真实编译，为正式项目定版本矩阵。
> 结论：**全部通过，APK 已产出。**

## 版本矩阵（实测可用）

| 组件 | 版本 | 备注 |
|---|---|---|
| Gradle | 9.5.0 | 用已解压发行版 `C:\Users\lirui\.gradle\wrapper\dists\gradle-9.5.0-bin\bvnork1r7n8i6kp5cnkibsc9q\gradle-9.5.0` |
| AGP | 9.3.1 | |
| Kotlin | 2.4.10 | 用 `org.jetbrains.kotlin.plugin.compose`，非 `kotlin-android` 插件 |
| KSP | 2.3.10 | |
| Compose BOM | 2026.06.01 | |
| material3 | **1.5.0-alpha17** | M3 Expressive（BOM 钉的是 1.4.0，需显式覆盖） |
| compileSdk / targetSdk | **36** / 36 | ⚠️ 36 而非 37（见坑4） |
| minSdk | 24 | 正式项目拟用 26 |
| JDK | 17 | `C:\Users\lirui\.jdks\jdk17.0.20_12` |
| core-ktx / lifecycle | 1.16.0 / 2.9.0 | cSdk36 兼容（见坑4） |
| Room | 2.8.4 | KSP 生成 |
| WorkManager | 2.11.2 | |
| coroutines | 1.11.0 | |

## M3 Expressive API 可用性（全部编译通过）

- `MaterialExpressiveTheme(colorScheme/, motionScheme/)`
- `expressiveLightColorScheme()`（动态/深色同理有 expressive 变体）
- `MotionScheme.expressive()`
- `LargeFlexibleTopAppBar`（`ExperimentalMaterial3ExpressiveApi`）
- `FloatingActionButtonMenu` / `FloatingActionButtonMenuItem`（expressive FAB 菜单）
- 顶栏 `TopAppBarDefaults.exitUntilCollapsedScrollBehavior` + `rememberTopAppBarState` + `nestedScroll`

⚠️ 所有 Expressive API 都要 `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`，否则编译报错（活动/组合函数都要）。

## APK

`D:\Workplace\Todo\_smoke\app\build\outputs\apk\debug\app-debug.apk`（约 12.7 MB debug）

## 可复现构建命令

```powershell
$env:JAVA_HOME="C:\Users\lirui\.jdks\jdk17.0.20_12"
$gradle="C:\Users\lirui\.gradle\wrapper\dists\gradle-9.5.0-bin\bvnork1r7n8i6kp5cnkibsc9q\gradle-9.5.0\bin\gradle.bat"
& $gradle :app:assembleDebug --console=plain   # 在项目根目录执行
```

## 坑清单

1. **系统 PATH 的 java 是 JDK 25**，AGP 不兼容。必须设 `org.gradle.java.home`（gradle.properties）或用 JDK17：`C:\Users\lirui\.jdks\jdk17.0.20_12`。
2. **KSP 内建版本下载失败（TLS 握手）**：AGP 9.3.1 内建 KSP 会解析 `symbol-processing-api:2.0.10-1.0.24`，`repo.maven.apache.org` 握手失败 → **在 settings.gradle.kts 加阿里云镜像**（`maven.aliyun.com/repository/public`)；用户 `.gradle\init.d\mirror.gradle` 已加过但 settings 的 `PREFER_SETTINGS` 覆盖了它，需在项目 settings 里显式加。
3. **Compose/Kotlin 插件变化**：AGP 9 用 `org.jetbrains.kotlin.plugin.compose`（不要用废弃的 `kotlinOptions` / `composeOptions.kotlinCompilerExtensionVersion`）。Room 用 `ksp(...)`。
4. **compileSdk 37 的 SDK 布局是坏的**：本机 `platforms\android-37.0` 是个空壳（真实包在 `android-37.0-2`），AGP 找不到。且 core-ktx 1.19 / lifecycle 2.11 要求 compileSdk 37。**解决：用 compileSdk 36 + core-ktx 1.16 / lifecycle 2.9**，完全够（targetSdk 36）。
5. `minSdk` 用 24 在 manifest 需 `enableEdgeToEdge`；正式项目用 26。