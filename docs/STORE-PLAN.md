# 上架改造方案（vivo 首发）

> 分支：`release/vivo-store`
> 目标版本：**1.0.0（versionCode 34）**
> 决策：应用名 → **一句话待办**；自更新 → **整体砍掉，应用彻底不联网**
> 状态：**代码改造已完成并出包**（`dist/todo-1.0.0.apk`，2.03 MB）

---

## 一、最终决策：彻底不联网

原本考虑过「保留检查更新入口、只跳浏览器」，但 `zuiges.com` 域名在海外、没有国内备案，
这条路会强制拉出 App 备案的接入商问题（没有境内服务器就备不了）。所以改为 **A 方案：整体砍掉**。

这一刀换来四件事：

| 收益 | 说明 |
|---|---|
| 过审最稳 | 国内商店的高压线是「应用内下载安装 APK」，删干净就没有争议点 |
| **备案大概率豁免** | 工信部备案针对「提供互联网信息服务」的 App；纯单机不提供互联网信息服务 |
| 隐私政策极简且无懈可击 | 「不申请网络权限」是硬事实，不是口头承诺 |
| 「数据只在本地」名副其实 | 之前有联网，这句话严格说是站不住的 |

**代价**：GitHub / 博客渠道的老用户失去应用内更新提示。他们本来就在用 GitHub Releases
或博客下载，改为自己关注发布页即可；商店渠道的用户由商店负责更新。

---

## 二、要申请的三样东西

### 1. 计算机软件著作权登记（最长的一环，今天就要交）

| 项 | 内容 |
|---|---|
| 办理方 | 中国版权保护中心 ccopyright.com.cn（唯一官方，**免费**） |
| 名称 | **一句话待办**（必须与 App 名、商店后台名完全一致） |
| 版本号 | V1.0 |
| 周期 | 受理起 **30 个工作日**（约 6 周），无加急 |
| 申请人 | **你本人**（vivo 3.1.5：个人开发者资质须本人持有，不接受授权） |

**材料清单**：

1. **申请表** —— 官网在线填，需填开发完成日期、首次发表日期（未发表选「未发表」）
2. **源程序** —— 前 30 页 + 后 30 页，每页不少于 50 行，页眉写「一句话待办 V1.0」
   - 本项目代码量充足，直接从 `app/src/main/kotlin/` 按文件顺序抽取即可
   - 不足 60 页则全部提交
3. **软件说明书** —— 图文并茂，建议 30 页以上
   - 现成素材：`README.md`（功能说明）+ `DESIGN.md`（设计）+ `docs/screenshots/`（界面图）
   - 结构：产品简介 → 运行环境 → 功能说明（逐页截图）→ 技术特点
4. **身份证** 正反面扫描件

**注意**：软著名称一旦登记，改名要重新登记。所以「一句话待办」这个名字**提交前最后确认一次**，
并先去 vivo / 华为商店搜一下有没有重名。

---

### 2. App 备案（工信部，免费）—— 先确认是否真的需要

| 项 | 内容 |
|---|---|
| 办理方 | 工信部政务服务平台，经**接入服务商**提交 |
| 费用 | 官方免费 |
| 周期 | 管局审核 20 个工作日内（实际 7~15 工作日） |
| 关键 | 个人无法直接提交，必须通过接入商 |

**本应用不联网，理论上不属于备案对象**（备案针对「提供互联网信息服务」的 App）。
但商店后台的「App 备案号」字段是否必填，由平台决定，需要实测：

```
步骤：
1. 先完成 vivo 开发者注册 → 后台创建应用 → 走到「资质」这一步
2. 看「App 备案号」是必填还是选填
3. 必填 → 联系 vivo 在线客服，说明「纯单机应用、无网络权限、无互联网信息服务」，
   按其指引提交说明或走免备案通道
4. 客服仍要求备案 → 再走下面的备案流程（届时需要境内接入商）
```

> 这也是「砍掉联网」最大的现实收益：**不用为备案去找境内服务器**。
> 如果先按「保留联网」做，这一步会卡死 —— 域名在海外、没有境内接入商，备案根本提交不了。

---

### 3. vivo 开发者账号（免费，可与软著并行）

| 项 | 内容 |
|---|---|
| 地址 | dev.vivo.com.cn |
| 类型 | 个人开发者 |
| 材料 | 身份证正反面 + 实名认证（人脸/手持照） |
| 周期 | 1~2 工作日 |
| 费用 | **0 元**（不像 App Store 收 688/年） |

---

## 三、代码要改什么

### A. 改名

| 文件 | 改法 |
|---|---|
| `app/src/main/res/values/strings.xml` | `app_name` → `一句话待办` |
| `app/build.gradle.kts` | `versionCode = 34`、`versionName = "1.0.0"` |

> `widget_label` 保持 `Todo 待办` 不动 —— 它是桌面组件列表里的名字，与应用名无需一致，
> 改成中文全名反而会让组件标题变长。

---

### B. 自更新整体下线 ✅ 已完成

**删除的文件**：

```
app/src/main/kotlin/.../update/UpdateService.kt
app/src/main/kotlin/.../update/UpdateDownloadService.kt
app/src/main/kotlin/.../update/ApkInstaller.kt
app/src/main/kotlin/.../update/StorageDiag.kt
app/src/main/kotlin/.../update/Version.kt
app/src/test/java/.../update/VersionTest.kt
app/src/main/kotlin/.../ui/screen/UpdateDialog.kt
app/src/main/res/xml/file_paths.xml
```

**`AndroidManifest.xml` 移除的权限**：

| 权限 | 移除方式 |
|---|---|
| `INTERNET` | 直接删 |
| `ACCESS_NETWORK_STATE` | **`tools:node="remove"`** —— androidx.work 会自己注入，不 remove 会出现在最终包里 |
| `REQUEST_INSTALL_PACKAGES` | 直接删 |
| `FOREGROUND_SERVICE_DATA_SYNC` | 直接删 |

同时删掉 `UpdateDownloadService` 的 `<service>` 与 `FileProvider` 的 `<provider>`。

**`AboutScreen.kt`**：删掉 `UpdateUi` 状态机、`UpdateRow`、进度广播接收器、两个更新弹窗。
「项目」卡片里的「更新与下载 → GitHub Releases」保留 —— 它只是 `ACTION_VIEW` 跳浏览器，
联网的是浏览器，本应用不参与。

**`MainActivity.kt`**：删掉 `UpdateDownloadService.cleanupStaleApks()` 与 `StorageDiag` 调用。

**最终包的权限清单（实测）**：

```
POST_NOTIFICATIONS
SCHEDULE_EXACT_ALARM
USE_EXACT_ALARM
RECEIVE_BOOT_COMPLETED
REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
FOREGROUND_SERVICE
FOREGROUND_SERVICE_SPECIAL_USE
WAKE_LOCK（androidx.work 带入）
com.lirui.oneline.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION（androidx.core 带入）
```

无 INTERNET、无 ACCESS_NETWORK_STATE、无 REQUEST_INSTALL_PACKAGES。

---

### C. 隐私政策 ✅ 已完成

**新增 `ui/screen/PrivacyScreen.kt`**，包含两个组件：

| 组件 | 用途 |
|---|---|
| `PrivacyPolicyScreen` | 全屏隐私政策页，**纯本地文本**（不是 WebView / 外链）。商店审核点开必定打得开 |
| `PrivacyConsentDialog` | 首次启动同意弹窗。`dismissOnBackPress=false` + `dismissOnClickOutside=false`，点遮罩和返回键都关不掉 |

**`util/SettingsRepo.kt` 新增**：

```kotlin
const val PRIVACY_POLICY_VERSION = 1
fun privacyAccepted(ctx: Context): Boolean   // 存的是已同意的政策版本号，不是 boolean
fun setPrivacyAccepted(ctx: Context)
```

**`MainActivity.kt` 改动（关键，顺序错了会被拒）**：

```
onCreate
  └─ setContent
       ├─ 未同意 → 只渲染 PrivacyConsentDialog（主界面完全不组合）
       │    ├─ 「同意并继续」→ 写标记 → accepted=true → 触发初始化
       │    ├─ 「不同意并退出」→ finish()
       │    └─ 「《隐私政策》」→ PrivacyPolicyScreen
       └─ 已同意 → LaunchedEffect 里才做：申请通知权限 + 重排闹钟 + 兜底巡检
```

原来 `onCreate` 里**先申请通知权限**、然后才 `setContent` —— 「未征得同意先申请权限」
是明确的拒审理由，已修正。

**`AboutScreen.kt`**：「应用」卡片新增「隐私政策」行，点击打开覆盖层。

---

### D. 备案号展示（拿到号后填）✅ 位置已预留

**`AboutScreen.kt`** 顶部有两个常量：

```kotlin
private const val BEIAN_NUMBER = ""          // ← 拿到号后填这里
private const val BEIAN_URL = "https://beian.miit.gov.cn/"
```

`BEIAN_NUMBER` 为空时整行不渲染（避免拿占位符上线被判定「信息不实」）。
填入后自动在关于页底部显示「备案号：X ICP 备 XXXXXXXX 号-XX」，点击跳备案系统。

`PrivacyScreen.kt` 文末也有一处备案号占位，一并替换。

---

### E. 加固 ✅ 已完成

| 文件 | 改动 | 原因 |
|---|---|---|
| `AndroidManifest.xml` | `android:allowBackup="false"` | 数据不再进系统云备份，与「数据只在本地」一致 |
| `app/build.gradle.kts` | `enableV3Signing = true` | 0.10.0 只有 v2 签名，现已 v2 + v3 |
| keystore | **你还需要另存一份到网盘/U盘** | 丢失后 `com.lirui.oneline` 永远无法更新，只能换包名重上架 |

---

## 四、素材要准备什么

| 素材 | 要求 | 现状 | 动作 |
|---|---|---|---|
| 应用图标 | 512×512、直角、≤500 KB、**无透明** | 512×512 RGBA 14 KB | 导出不带 alpha 的 PNG（透明角会被判「白边」） |
| 截图 | 3~5 张、**1080×1920**、竖图统一、≤2 MB | 1080×2308~2400，含 1 张横图 | **全部重做** |
| 一句话简介 | 5~16 字，结尾只能感叹号/问号 | 无 | 「一句话记下所有待办」（9 字） |
| 应用介绍 | ≥50 字，简体中文 | 无 | 从 README 首段改写 |
| 新版说明 | ≥5 字，且 ≠ 应用介绍 | 无 | 「首个商店版本，支持一句话解析与精确提醒」 |
| 隐私政策 URL | 公网可访问 | 无 | 发布到 `blog.zuiges.com/privacy` |
| 软著证书 | 扫描件 | 待申请 | — |
| 备案号 | — | 待申请 | — |

**截图重做建议**：用 1080×1920 分辨率模拟器（或找一台 1080p 手机）重新截 5 张：
首页 / 一句话添加 / 桌面组件 / 设置 / 归档。组件那张也必须是竖图，横图直接去掉。

---

## 五、执行顺序

### 已完成（2026-10-25）

```
✅ 建分支 release/vivo-store
✅ 应用名 Todo → 一句话待办（strings.xml / 软著 / 商店后台三处统一）
✅ 自更新模块整体删除，应用不再申请任何网络权限
✅ 隐私政策页 + 首次启动同意弹窗（权限申请移到同意之后）
✅ 备案号展示位预留（BEIAN_NUMBER 常量）
✅ allowBackup=false、补 v3 签名
✅ versionCode 34 / versionName 1.0.0，出包 dist/todo-1.0.0.apk（2.03 MB）
✅ 单元测试通过（ParserTest / FormatTest / HomeGroupingTest / ReminderEngineTest /
   NotificationTitleTest / AppTimeTest，共 242 条；VersionTest 8 条随 Version.kt 一并删除）
```

### 待你执行

```
今天
 ├─ ⬜ 真机装一遍：冷启动 → 隐私弹窗 → 同意 → 各页面正常
 │     （重点验证：点遮罩关不掉、返回键关不掉、不同意能退出）
 ├─ ⬜ 提交软著申请（名称已定死，这是唯一的长尾，越早交越好）
 └─ ⬜ 注册 vivo 开发者，提交实名认证（1~2 工作日）

本周
 ├─ ⬜ 重截 1080×1920 截图、导出无透明图标
 ├─ ⬜ 隐私政策页面上线（内容用 docs/PRIVACY.md，改写成 HTML）
 └─ ⬜ vivo 后台创建应用、填素材、存草稿（不提交）

软著下证前（约 6 周）
 ├─ ⬜ 确认「App 备案号」字段是否必填（见第二节第 2 条）
 └─ ⬜ 整理权限说明文案（见第六节）

软著下证后
 ├─ ⬜ 补软著 + 备案号 → 提交审核（1~2 工作日）
 └─ ⬜ 通过后，同一套材料投华为 / 小米 / OPPO / 应用宝
```

---

## 六、提交审核时的权限说明（直接抄）

| 权限 | 说明文案 |
|---|---|
| 通知 | 待办到点提醒，用户可在系统设置关闭 |
| 精确闹钟 | 待办需在用户设定的精确时刻响铃；非精确闹钟会被系统延迟，属核心功能 |
| 开机自启 | 重启后重建已设定的提醒，否则重启会丢提醒 |
| 忽略电池优化 | 仅在用户于「提醒可靠性」页主动点击时跳转系统白名单页，避免后台被清理导致漏提醒，不在启动时自动弹出 |
| 前台服务（specialUse） | 可选的后台常驻通知（默认关闭），用于及时响应提醒通知上的「完成/撤回」，用户可随时关闭 |
| 网络 | 仅访问 `blog.zuiges.com` 读取版本号，用于提示新版本；不上传任何用户数据 |

---

## 七、还没解决的风险

1. **软著 30 个工作日是硬等待** —— 没有任何免费加速办法。想快只能花钱找代理加急。
2. **「App 备案号」字段可能仍必填** —— 纯单机应用在法规上不属于备案对象，但平台表单是否
   强制要，只能到后台看到才知道。若被卡，按第二节第 2 条的步骤联系客服说明。
3. **应用名可能已被占用** —— 你已确认无重名，但商店的查重是实时的；若提交时被提示重名，
   换「浪花待办」等备选，但那样软著要重新申请，所以**越早提交软著越安全**。

## 八、不再存在的风险（已通过砍掉联网消除）

- ~~「应用内提示更新」被判定为引导安装~~ —— 入口已整体删除
- ~~备案需要境内接入商 / 服务器~~ —— 不联网，不构成互联网信息服务
- ~~审核员点「检查更新」遇到网络失败~~ —— 没有这个按钮了
- ~~隐私政策里「不联网」与实际声明 INTERNET 矛盾~~ —— 权限已彻底移除
