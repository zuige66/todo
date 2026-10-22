# 一句话待办 · 设计方案（v1.0 定稿）

> **已拍板**：明天到期 → 中午 12:00 提醒；"星期几"已过 → 顺延到下一个；先定稿方案、暂不写代码；不需要模拟器，出 APK 你自己装。
> 待你确认的剩余问题见 §9。

> 定位：Android 原生 / Jetpack Compose / Material 3 Expressive / 离线可用 / 零学习成本。
> 核心一句话：**点右下角 +，像发微信一样打一句话，剩下的（时间、日期、星期、提醒）全自动。**

---

## 1. 你的诉求 → 功能点拆解

| # | 你说的 | 我理解的实现 |
|---|---|---|
| 1 | 点加号，一句话直接添加事件、时间、描述 | 底部弹层输入框，输入"周五 下午3点 交周报"，实时高亮已识别的时间/日期芯片 |
| 2 | M3E 风格 UI | Material 3 Expressive：`MaterialExpressiveTheme` + expressive 动效 + 大圆角 + 动态取色 |
| 3 | 每个待办一行行列出，显示时间、日期、星期 | 单行卡片：左圆圈 / 中间标题+日期时间星期 / 右侧可选标签 |
| 4 | 左边圆圈，点一下 = 归档 | 点圆圈 → 打勾动画(~600ms) → 移出主列表进"已归档"，**顶部弹出应用内"完成"通知条 +【撤销】按钮** |
| 5 | 空格 / 逗号 / 句号 分隔 | ` ` `，` `,` `。` `、` 都当分隔符 |
| 6 | 自动识别整句话 | 本地离线规则解析器（正则 + 词典），不联网、不上传 |
| 7 | "星期几" = 这周 | 本周内该星期几；若本周已过，顺延到下个星期几 |
| 8 | "下个星期几" = 下周那天 | 以今天所在周为基准 +7 天再定位 |
| 9 | 分级提醒（1个月/1周/第二天） | 按"距离多远"选提前量，见 §2 |
| 10 | 全部用通知 | `AlarmManager` 精确定时 + `NotificationCompat` 通知渠道 |
| 11 | 输入精确到分钟 → 提前1小时再提醒一次 | 有 `HH:mm` 时，额外加一条 T-60min 提醒 |

---

## 2. 提醒规则（最需要你拍板的部分）

### 2.1 你原话里的两条规则互相打架

- 规则 A：「第二天的那就前一天，**晚上9点**提醒」
- 规则 B：「所有待办，**明天**就是待办日期了，那就前一天**中午12点**发提醒」

"第二天"和"明天"是同一个东西，但时刻一个 21:00、一个 12:00。
**我的建议方案**：提前量按"距离"分档，时刻只有两个池子，见下表 👇

### 2.2 建议规则表（提前量由"距今天数"决定，越大越早提醒）

| 距今天数 D | 第一次提醒 | 提前量 | 提醒时刻 |
|---|---|---|---|
| D ≥ 30 天（约一个月以上） | D-7 天 | 提前 **7 天** | 21:00 |
| 7 ≤ D < 30 | D-3 天 | 提前 **3 天** | 21:00 |
| 2 ≤ D < 7 | D-1 天 | 提前 **1 天** | 21:00 |
| D = 1（明天） | 今天 | 提前 1 天 | **12:00** ✅ 已定 |
| D = 0（今天） | 当天 | 立即 / 当天 09:00 | — |
| 精确到分钟（含 HH:mm） | T-60 分钟 | 额外一条 | 精确时刻 -1h |

> 逻辑：跨度越大，越早提醒（7天 / 3天 / 1天 阶梯）；临近的用中午提醒（白天能看到、有时间准备），远的用晚上9点（下班后安静）。

### 2.3 边界处理（我准备这样定）

- **同一天多次触发**：每条待办最多 2 条通知（分级提醒 1 条 + 精确 T-1h 1 条）。若两者时间重合则合并。
- **提醒时刻已经过去**（比如现在 22:00，本该今天 21:00 提醒）：不补发晚上的，改为**立即发一条**"明天到期"提醒；若距到期已不足 2 小时，则跳过、只留 T-1h。
- **早于当前的提前点**：自动跳到下一个仍然有效的档位（例如 D=1 的待办，7天/3天档都已过，只用 12:00 那条）。
- **修改待办时间**：旧闹钟全部取消，重算。
- **手机重启 / 时区变化**：`BOOT_COMPLETED` + `TIMEZONE_CHANGED` 重新排程。
- **归档**：归档即取消该待办全部未发出的提醒。
- **精确闹钟权限**：Android 12+ 需要 `SCHEDULE_EXACT_ALARM`（或 `USE_EXACT_ALARM`）；用户拒绝时降级为 `setAndAllowWhileIdle`（可能延迟几分钟）。

---

## 3. 自然语言解析规格（离线，纯 Kotlin）

### 3.1 分词
按 `空格 / ， / , / 。 / 、 / 　`（全角空格）切 token，然后逐 token 尝试匹配词典与正则，匹配到的抽取为"时间/日期"，没匹配到的按原顺序拼回标题。

### 3.2 日期词典

| 类型 | 词 |
|---|---|
| 相对日 | 今天、今日、今晚、明天、明日、明早、明晚、后天、后日、大后天 |
| 星期 | 周一~周日 / 星期一~星期日 / 礼拜一~礼拜日 / 周天、星期天 |
| 下周 | 下周X、下个星期X、下礼拜X、下下周X |
| 绝对 | `10月25日`、`10月25号`、`2026年10月25日`、`15号`（本月/下月就近） |
| 数字短格式日期 | `10.21`→10月21日；`27.1.21`→2027年1月21日（见下方"数字日期"里程碑） |
| 周期 | `3天后`、`两周后`、`一个月后`、`半小时后` |

**数字短格式日期**（你新提的快速输入）：
- **`M.d` 两段式**：`10.21` = 今年 10 月 21 日。
- **`YY.M.d` / `YY.MM.DD` 三段式**：`27.1.21` = **2027 年** 1 月 21 日。当前年份按两位数补全为完整年（如 27 → 2027）。
- 前缀规则：年份两位数 → 20YY；月/日可用 1–2 位（`5.3` = 5月3日）。
- 分隔符：**同时兼容 `.`、`-`、`/`**，且可混用（`10.21`、`10-21`、`10/21`、`2026-10-25` 均认）。
- 优先级：**先于"时间"判断**。例如 `10.21` 不会误读成时间；但如果同一 token 形如 `15:30` 才当纯时间。
- 与分词的关系：数字日期通常在一句话里独立成 token（`交周报 10.21`），也可能紧贴文字（`10.21交周报`）——两侧都是数字/分隔符时才抽取为日期，否则回到按整句匹配。
- **✅ 已定：日期已过 → 报错**。若解析出的完整日期早于今天 00:00（如 `10.21` 今年已过），在输入框下方红色提示"该日期已过，无法创建"，不创建待办，让用户改成未来日期。（如果只写 `M.d` 且该月该日已过，也报同样的错，不再自动顺延到下一年。）

**"星期几"的周基准**：以自然周（周一为一周第一天）为准。
- 说"周五"，今天是周三 → 本周五。
- 说"周一"，今天是周三 → 本周一已过 → **✅ 已定：报错**（输入框下方红色提示："该星期几已过，请输入下一周"），不再自动顺延。
- 说"下周五"→ 下周一那一周里的周五。

### 3.3 时间词典

| 类型 | 词 |
|---|---|
| 时分 | `15:30`、`15：30`、`9点`、`9点半`、`9点一刻`、`9点三刻`、`9点20`、`9点20分` |
| 时段前缀 | 凌晨 / 早上 / 早晨 / 上午 / 中午 / 下午 / 傍晚 / 晚上 / 夜里 / 半夜 |
| 换算 | 下午3点→15:00，晚上8点→20:00，中午12点→12:00，晚上12点→00:00(次日) |

**没写上午/下午时**（如"9点"）：按最近未来推断 —— 现在的时刻之后最近的那个 9 点；若今天已过则明天 9 点。
**12 小时制缺省推断**：8–11 视为上午，12 视为中午，1–6 视为下午，7–11 视为晚上（可用 AM/PM 前缀覆盖）。

### 3.4 无时间信息
- 只有描述（"买牛奶"）→ 存为**无日期待办**，进列表底部"无日期"分组，**不发提醒**。
- 只有日期没时间（"周五 交周报"）→ 默认当天 09:00 为到期时刻，提醒按 §2 走。

### 3.5 输入预览（重要体验点）
输入时下方实时显示一枚可点的解析芯片，例如：

```
[ 周五 ] [ 15:00 ]   交周报
            ↑ 点开可手动改日期/时间
```

识别错的兜底：点芯片 → 弹出的日期时间选择器（M3 Expressive 的 `DatePicker` / `TimePicker`）。
识别不到的兜底：手动编辑页。

---

## 4. UI / 交互（M3E）

### 4.1 主界面
```
┌──────────────────────────────┐
│  今天 · 10月21日 星期三        │  ← LargeFlexibleTopAppBar，滚动收缩
│                              │
│  今天 ─────────────────────── │  ← 分组标题
│  ○  交周报                    │
│     15:00  10/25  周六         │  ← 图标 + 时间/日期/星期
│  ○  买牛奶                    │
│     无日期                     │
│  本周 ─────────────────────── │
│  ○  体检          明天 09:00   │
│                              │
│                       ( + )  │  ← 右下角 expressive FAB
└──────────────────────────────┘
```

### 4.2 一行待办的组成
- 左：**圆圈**（24dp 描边圆），点击 → 打勾动画(~600ms) → 归档，顶部弹"完成"通知条带【撤销】
- 中：标题（单行省略）+ 第二行 `时间 · 日期 · 星期`（灰字，今天/明天用强调色）
- 右：可选"即将到期"小圆点 / 重复标记
- 交互：**点行 → 编辑**；**长按 → 删除**（带撤销）；左滑 → 归档

### 4.3 添加
点 FAB → 底部弹层（BottomSheet）：
- 输入框自动聚焦，键盘立即弹出
- 实时解析芯片（§3.5）
- 右侧"发送"图标 / 键盘回车 = 直接添加并关闭（保持键盘、可连发几条？**建议关闭**，避免误操作）
- 不写时间也能加（走无日期分组）

### 4.4 其他页面
- **归档页**：抽屉 / 顶栏图标进入，按归档日期分组，可"恢复"或"彻底删除"
- **设置**：默认提醒时刻、通知权限状态、动态取色开关、关于

### 4.5 视觉
- 动态取色（Android 12+ Monet），兜底一套品牌绿/青
- 深色模式完整适配
- 大圆角（20–28dp）、expressive 弹簧动效（`MotionScheme.expressive()`）
- 空状态：一句"点 + 说一句话就能加"，带插画式图标

---

## 5. 技术选型

| 层 | 方案 |
|---|---|
| 语言/UI | Kotlin 2.x + Jetpack Compose + Material3 Expressive |
| 存储 | Room（本地 SQLite，离线、隐私） |
| 提醒 | `AlarmManager.setExactAndAllowWhileIdle` + `BroadcastReceiver` + `NotificationCompat`；重启用 `BOOT_COMPLETED` 重排 |
| 解析 | 自研离线规则解析器（纯 Kotlin，可单元测试，无网络依赖） |
| 架构 | 单模块 MVVM + 手动 DI（不引 Hilt，减依赖） |
| 最低版本 | minSdk 26（Android 8） / targetSdk 36 |
| 包名 | **`com.lirui.oneline`**（自有域名反写，避开厂商关键字，降低各渠道拒审风险） |

### 依赖环境（已在本机探测）
- JDK 17 `C:\Users\lirui\.jdks\jdk17.0.20_12`（系统 PATH 上是 JDK 25，AGP 不兼容，需显式指定）
- Android SDK `C:\Users\lirui\AppData\Local\Android\Sdk`（platforms 34/35/36/36.1/37.0）
- Gradle 9.5.0 已解压；Gradle 缓存里已有 AGP 9.2.1/9.3.1、Kotlin 2.4.10、KSP 2.3.10、Room 2.8.4、Compose BOM 2026.06.01、material3 1.4.0/1.5.0-alpha17、WorkManager 2.11.2
- 网络可访问 dl.google.com / Maven Central
- ⚠️ 当前**无连接设备、无模拟器镜像**，所以我能做到"编译出 APK"，但**装机实测需要你插手机或让我下模拟器镜像**

---

## 6. 里程碑

| 阶段 | 内容 | 验收 |
|---|---|---|
| M0 | 构建链路打通 ✅ 已完成 | `assembleDebug` 出 APK（已产出，见下《实测版本矩阵》） |
| M1 | 数据层 + 列表 + 圆圈归档 + FAB 添加（暂不解析） | 能加、能列、能归档 |
| M2 | 自然语言解析器 + 单测 | 30+ 条用例全过（含周三说"周一"报错、下午/晚上换算、下周X、数字日期 `10.21`/`27.1.21`） |
| M3 | 提醒引擎 + 通知 + 权限 + 重启重排 | 能收到分级通知（可把通知时间临时改成 1 分钟验证） |
| M4 | M3E 打磨：动效、空状态、深色、动态取色、归档页、设置 | 视觉验收 |
| M5 | 签名 release APK + 安装说明 | 可安装包 |

---

## 7. 已拍板的决定

| # | 问题 | 决定 |
|---|---|---|
| 1 | 明天到期哪天提醒 | **当天中午 12:00** 一条 |
| 2 | 说的星期几已过 | **报错**，提示用户选下一周（不再顺延） |
| 3 | 交付节奏 | **先定稿方案，确认后再写代码** |
| 4 | 真机验证 | 不出模拟器，**我自己装 APK 验证** |
| 5 | 数字日期 `10.21` / `27.1.21` | 见 §3.2：分隔符兼容 `.`/`-`/`/`；两段式已过 → **报错** |

---

## 9. 决策确认（✅ 全部已定）

1. **圆圈点完动画**：✅ 停留 ~600ms 打个勾再消失（M3E 反馈感）。打勾完成后，**最顶部弹出应用内"完成"通知条，带【撤销】按钮**（可撤销归档、恢复到待办列表）。
2. **整点算不算"精确到分钟"**：✅ 算——"下午3点"也提前 1 小时提醒（有时间点就提前 1 小时）。
3. **无日期待办**（只说"买牛奶"）：✅ 要——归"无日期"分组、不发提醒。
4. **重复规则**（"每天早上8点跑步"）：✅ v1 先不做，v2 再加 `RRULE`。
5. **数字日期** `10.21` / `27.1.21`：✅ 见 §3.2——分隔符兼容 `.`/`-`/`/`；两段式已过 → 报错。
6. **星期几已过** → 报错（见 §3.2 / §7）。

---

## 10. 构建与发布状态（✅ 可安装）

- **单元测试**：30/30 通过（`ParserTest` 23 + `ReminderEngineTest` 7），`TODO` 已全绿。
- **发布签名**：已生成 `app/oneline-release.keystore`（别名 `oneline`，口令 `oneline2026`，SHA-256 `c0847ef1…`），配置写在 `keystore.properties`（不入库）+ `app/build.gradle.kts` 的 `signingConfigs.release`，`release {}` 已接 `signingConfig`。用 `rootProject.file()` 解析路径避免模块级相对路径坑。
- **产物**：`app/build/outputs/apk/release/app-release.apk`（约 9.27 MB，**已签名**，V2，可直接侧载安装）。
- **命令**：`gradle.bat :app:assembleDebug`（调试）、`:app:assembleRelease`（已签名发布）、`:app:testDebugUnitTest`（测试）。

---

## 附：实测版本矩阵（已用 Smoke 项目验证 `assembleDebug` 通过，见 `_smoke/REPORT.md`）

Gradle **9.5.0** · AGP **9.3.1** · Kotlin **2.4.10**（`org.jetbrains.kotlin.plugin.compose`）· KSP **2.3.10** · Compose BOM **2026.06.01** · material3 **1.5.0-alpha17**（M3 Expressive）· compileSdk/targetSdk **36** · minSdk **26** · JDK **17**（`C:\Users\lirui\.jdks\jdk17.0.20_12`）· core-ktx 1.16.0 · lifecycle 2.9.0 · Room 2.8.4 · WorkManager 2.11.2。

关键坑：① 必须显式用 JDK17（系统 PATH 的 java 是 25）；② settings.gradle.kts 要加阿里云镜像（否则 KSP 内建版下载 TLS 失败）；③ 本机 compileSdk 37 的 SDK 目录是空壳，故用 36 + 适配的库版本；④ 所有 Expressive API 需 `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`。

---

## 11. v1.1 变更记录（2026-10-21）

### 11.1 解析：裸时段词 → 默认时刻

原 `hourRe` 强制要求"点"，`周日早上组会` 里的"早上"匹配不到时间，整段被当成标题。现补充"无点时段词"分支（在带"点"规则之后，优先级更低）：

| 时段词 | 默认时刻 |
|---|---|
| 早上 / 早晨 / 上午 | 08:00 |
| 中午 | 12:00 |
| 下午 | 14:00 |
| 傍晚 / 晚上 | 18:00 |

- `周日早上组会` → 10月25日 08:00，标题 `组会`
- 带"点"仍优先：`晚上8点跑步` → 20:00（不是 18:00）
- 时段词与小时分处两个 token（`下午 3点 开会`）时，用 `Acc.pendingPeriod` 把后一个裸小时换算进该时段 → 15:00

### 11.2 事件 / 详情拆分

剩余 token（去掉日期时间后）按空格分段：**第一段 = 事件标题，其余 = 详情**。

- `周六下午3点组会 要带笔记本去` → 事件 `组会`，详情 `要带笔记本去`
- 不加空格则不拆：`周六下午3点组会要带笔记本去` → 标题整段，详情为空

### 11.3 数据层：detail 入库（Room v3）

- `Todo` 新增 `detail TEXT NOT NULL DEFAULT ''`；`ParsedTodo` 新增 `detail: String?`
- 数据库版本 2 → 3，新增 `MIGRATION_2_3`（`ALTER TABLE todos ADD COLUMN detail ...`）
- `TodoDao.updateContent` 增加 `detail` 参数；新增/编辑/循环顺延都会带上 detail
- 列表行：标题主行 → 详情灰色副行 → 日期时间行；归档页同样显示详情

### 11.4 添加弹层（AddSheet）

- **修复幽灵预览**：此前 `parse` 直接读 ViewModel 里全局的 `lastParse`，弹层重开时残留上一条结果（空输入却显示"10月23日"）。现在 `LaunchedEffect(initialText)` 在打开时重新解析，且 `text` 用 `remember` 而非 `rememberSaveable`（避免上次输入被恢复）。
- 预览改为 M3E 卡片四段式：`[日期 星期六] [15点]` / 事件标题 / 详情灰字。
- 输入框改大圆角 `OutlinedTextField`，按钮改胶囊 `Button`。
- 错误提示压缩为两句话：`日期已过，换个未来的日期` / `时间已过，换个未来的时间`。

### 11.5 设置页 M3E 重构

- 每个分区一张 `Surface(shape = extraLarge, color = surfaceContainer)` 卡片，行内用 `ListItem`（headline / supporting / trailing）+ `HorizontalDivider` 分隔。
- **外观**由三个 RadioButton 改成 `ExposedDropdownMenuBox` + `ListItem` 下拉（跟随系统 / 浅色 / 深色），锚点类型 `ExposedDropdownMenuAnchorType.PrimaryNotEditable`。
- 逾期间隔、默认提醒时刻改用 `FilterChip` + `FlowRow`（窄屏自动换行）。
- 权限说明压缩为一句："国产 ROM 可能拦截，请允许通知、精确闹钟和自启动"。
- 去掉设置项内部与外层重复的 `onRescheduleReminders()` 调用（外层回调已统一重排）。

### 11.6 验证

- `:app:testDebugUnitTest` 通过（新增裸时段词 6 例、事件/详情拆分 2 例；原 `strippedTitleConvertsAndWords` 断言改为 title/detail 两段）。
- `:app:assembleDebug` 出包后 `adb install -r` 装到 gauguinpro 实测。

---

## 12. v1.2 变更记录（2026-10-21）

### 12.1 列表圆圈配色

旧：`hasExactTime=false` 时圆圈走 `outlineVariant`，深色模式下几乎看不见（"没有颜色的圈"）。
现统一用主题色：有精确时间 = `primary`，无精确时间 = `primary.copy(alpha = 0.55f)`（浅一档但仍有色）。逾期仍用 `error`。

### 12.2 归档完成通知条（CompletedBar）

旧：底色用 `inverseSurface`，深色模式下反成亮白、很刺眼。
现：改用 `surfaceContainerHigh` + `shadowElevation = 6.dp`，文字 `onSurface`、撤销 `primary`；位置不变（顶部 `TopCenter`）。

### 12.3 时间词嵌入识别（连写也能解析）

`开会晚上` 里"晚上"不在 token 开头，旧规则只认开头，整段被当正文。现新增 token 内部扫描：

- `clockFindRe` / `hourFindRe`：去掉 `^` 锚点的时间正则，用 `Matcher.find()` 在首字符之后定位 `开会3点`(3:00) / `开会晚上9点`(21:00)。
- 裸时段词：在 token 内 `lastIndexOf` 找最靠右的时段词（时间通常写在句尾）→ `开会下午`(14:00)。
- 抽取逻辑抽成 `hourMatchTime(m, acc)`，开头匹配与嵌入匹配共用。
- 抽取后剩余片段重新拼回标题（`(rest[0..start] + rest[end..]).trim()`）。

### 12.4 不做"下载额外语义包"

用户提议用下载语义包换取更小的包体。评估后**不做**：

- 中文时间表达是有限的"时段词 + 点"组合 + 少量绝对/相对日期，靠规则表即可覆盖 95%+，几十个字符串仅几 KB，不会撑大 APK；
- 下载包需引入服务器、本地存储、版本校验、离线失败降级，复杂度与隐私成本远超收益；
- 违背"纯离线、零学习成本"的产品定位。后续若遇长句，优先扩展解析器规则，而非加下载机制。

### 12.5 验证

- `:app:testDebugUnitTest` 通过（新增 5 例：开会晚上 / 开会晚上9点 / 开会3点 / 开会下午 / 今天买牛奶 不误判）。
- 下一步 `:app:assembleRelease` + `adb install -r` 装到 gauguinpro 实测 UI。

---

## 13. v1.3 变更记录（2026-10-21）

### 13.1 一句话多日期 → 多条待办

`明天后天晚上开会` 以前只生成一条（且标题残留"后天"）。新增 `Parser.parseAll()`：

- 逐 token 反复剥离日期前缀（最多 4 次），用 `LinkedHashSet` 收集所有日期；
- 时间 / 标题 / 详情 / 重复规则共用；
- 日期 ≥ 2 时按日期升序生成 N 条 `ParsedTodo`；
- 日期 ≤ 1 时**直接回退到 `parse()`**，保证所有单日期语义（dateError / timeError / repeat）完全不变；
- 重复词（每天/每周一）不算第二个日期。

`MainViewModel.add()` 改为遍历 `parseAll()` 结果批量插入；`AddSheet` 预览支持多行，顶部显示"将创建 N 条待办"。

### 13.2 列表分组改按自然周

旧实现用 `dayOfYear in (today+2)..(today+6)` 判"本周"，今天周三时会把下周一/二的 10/27 也算成本周。
现按周一~周日边界判断，并补"下周"分组：

`已逾期 / 今天 / 明天 / 后天 / 本周 / 下周 / 更晚 / 无日期`

（"后天"等更具体的标签优先于"本周"。）

### 13.3 重复任务勾选后"卡住"

`TodoRow` 的 `checked` 原是 `remember { false }`。重复任务完成时 `archive()` 走 `rollForward()`，id 不变、只改 due，UI 复用同一 composable → 勾选状态永远不复位，显示成灰色已完成。

改用 `remember(todo.id, todo.dueAtMillis)`，顺延后 key 变化、勾选自动复位。

同时：重复任务顺延不属于"归档"，撤销也无意义，因此 `repeatDays > 0 && autoRepeat` 时不再弹完成通知条。

### 13.4 重复任务报"时间已过"

`parse()` 末尾把 `date == today && time < now` 记为 timeError。`每天晚上开会` 在 22:00 输入时，18:00 已过 → 直接被拒。

修复：`timeError && repeatDays > 0` 时调用 `rollToFuture()` 把首次到期滚到下一个未来时刻（每天→次日，每周→下周同日），不报错。

### 13.5 顺延后仍停在已过的今天

`rollForward()` 只比较日期（`next.isBefore(today)`），"每天18点"在 22:00 完成时，next 仍等于今天 → 新到期时间还是今天 18:00（已过），会立刻再次逾期。

现改为比较"日期 + 时刻"：`next.isBefore(today) || (next == today && curTime < nowTime)`。

### 13.6 验证

- `:app:testDebugUnitTest` 通过：新增多日期 4 例（连写 / 逗号 / 单日期回退 / 重复词不拆分）、重复任务顺延 2 例、自然周分组 3 例。
- `:app:assembleRelease` + `adb install -r` 覆盖安装。

---

## 14. v1.4 变更记录（2026-10-21）

### 14.1 列表行：日期靠右，重复徽章在日期左边

原来日期/时间是标题下方的灰色副行，标题长时会被挤。现改为右侧对齐的两行元信息：

```
○  组会                      [每天] 10月22日 周四
   要带笔记本去                        9点
```

- 第一行：`RepeatBadge`（每天/每周/每月/每N天，`secondaryContainer` 小胶囊）+ 日期
- 第二行：时间（`9点` / `15:30`）
- 无重复时不显示徽章；逾期时日期与时间整体转 `error` 色
- 中间标题列 `weight(1f)`，长标题省略，不再与日期抢宽度

### 14.2 编辑时可关闭重复计划

长按编辑一条 `每天/每周/每月` 的待办时，弹层内新增"重复"开关：

- 开：保留 `repeatDays`（文案显示"每天重复"）
- 关：保存时 `ParsedTodo.copy(repeatDays = 0)`，即使输入文本里仍带"每天"关键词也会被清掉
- 添加模式不显示该开关（新增的重复由句子里的"每天/每周"决定）

`AddSheet.onAdd` 签名变为 `(String, Boolean) -> Unit`，`MainViewModel.edit(id, text, keepRepeat)` 接收开关状态。

### 14.3 设置页"测试"分区

新增两个按钮，便于在真机上验证通知链路：

| 按钮 | 行为 |
|---|---|
| 发送测试通知 | 直接调 `NotificationHelper.postReminder("测试通知", "通知权限与提醒渠道正常 ✅")`；无通知权限时 Toast 提示"请先允许通知权限" |
| 新建测试待办 | `TodoRepository.addTestInOneMinute()` 插入一条标题"测试通知"、详情"下一分钟到期"的待办，到期时刻 = **下一个整分钟**（`now.withSecond(0).withNano(0).plusMinutes(1)`），并立即 `scheduleBlocking` 排程 |

用途：第一个验证权限/渠道，第二个验证"创建 → 排程 → 到点弹通知"整条链路是否通。

### 14.4 验证

- `:app:compileDebugKotlin` 无错误无警告；`:app:testDebugUnitTest` 全绿。
- `:app:assembleRelease` + `adb install -r` 覆盖安装。

---

## 15. v1.5 变更记录（2026-10-21）

### 15.1 列表行再次调整

- 右侧只保留：`[每天/每周/每月徽章] 10月22日`（日期不含星期）
- **星期 + 时间放回标题下方**的灰色副行（`周六 15点`）
- 逾期时这两处都转 `error` 色

### 15.2 圆圈统一配色

上一版用"有精确时间 = primary / 无 = primary 55%"，实际观感是"有的圈颜色不一样"。现统一：

```kotlin
val borderColor = if (overdue) error else primary
```

`CheckCircle` 移除 `hasExactTime` 参数。

### 15.3 待办卡片底色

每行改为独立小卡片：`clip(RoundedCornerShape(20.dp))` + 底色

```kotlin
primaryContainer.copy(alpha = 0.45f).compositeOver(surface)
```

即"当前配色的浅容器色"叠在背景上，卡片颜色随配色方案整体变化，浅色/深色都成立；外边距 `horizontal 12.dp / vertical 3.dp`，内边距 `14.dp / 10.dp`。

### 15.4 配色体系（11 套）

`ui/theme/Theme.kt` 新增 `Palette` 与 `Palettes`，设置页"外观"里用 `ExposedDropdownMenu` 选择，带色块圆点与勾选：

| 组 | 名称 |
|---|---|
| Material 3 Expressive | 雅紫（默认）、青碧、海蓝、暖橙、玫瑰、森林 |
| 鲜艳 | 电光紫、荧光青、亮橙红、电光蓝、玫红 |

- 目标色只给 `primary / secondary / tertiary` 三个种子，容器色由 `copy(alpha).compositeOver(surface)` 派生，保证与背景协调
- 深色模式下平衡组会把种子往白提亮 28%（`0.72 alpha` 叠白），否则深色种子在近黑背景上看不清；鲜艳组保持原色
- 默认调色板 + 浅色仍走 material3 自带 `expressiveLightColorScheme()`，不破坏原有观感
- 持久化：`SettingsRepo.paletteId()` / `setPaletteId()`（SharedPreferences，默认 0）
- 主题入口：`OnelineTheme(darkTheme, paletteId)`，MainActivity 持有状态并下发到设置页

### 15.5 验证

- `:app:compileDebugKotlin` 无错误无警告；`:app:testDebugUnitTest` 全绿。
- `:app:assembleRelease` + `adb install -r` 覆盖安装，截图核对卡片/右对齐/徽章。

---

## 16. v1.6 变更记录（2026-10-21）

### 16.1 ★ 排程提醒收不到（根因：精确闹钟权限未授予）

**现象**：设置页"发送测试通知"立即弹通知正常，但"新建测试待办"（1 分钟后）到点不弹。

**排查**：
- `adb logcat` 里没有本应用的 `REMINDER` 触发记录
- `adb shell dumpsys alarm | grep oneline` → 闹钟**确实排上了**（`*walarm*:com.lirui.oneline.REMINDER`），说明创建+排程链路没问题
- `adb shell cmd appops get com.lirui.oneline android:schedule_exact_alarm` → `Default mode: default`，即**从未被授予**

**根因**：Manifest 只声明了 `SCHEDULE_EXACT_ALARM`。Android 13+ 对 **targetSdk ≥ 33** 的应用默认**不授予**该权限，于是 `AlarmManager.canScheduleExactAlarms()` 返回 false，`ReminderScheduler` 降级为 `setAndAllowWhileIdle()`——系统会自由延后它（HyperOS/MIUI 尤其明显），所以"1 分钟后"根本不准时。

**修复**：Manifest 追加 `USE_EXACT_ALARM`（限 API 33+）：

```xml
<uses-permission android:name="android.permission.USE_EXACT_ALARM" android:minSdkVersion="33" />
```

该权限面向闹钟/计时器/日历提醒类应用，**安装即授予、无需用户手动开关**，授予后 `canScheduleExactAlarms()` 返回 true → 走回 `setExactAndAllowWhileIdle`，准时触发。

配套：设置页"权限"分区新增状态检测，**未授予时显示红色提示**（"提醒可能被系统延后几分钟"），便于下次一眼看出。

### 16.2 已归档卡片底色与待办统一

归档页每行套用与待办行完全相同的卡片：`clip(20dp)` + `primaryContainer 45%` 叠 `surface`，外边距 12/3dp、内边距 14/10dp。

### 16.3 底部导航栏

原 M3E `NavigationBarItem` 的 indicator 尺寸固定、字号小。改为自定义 `NavTab`：

- 字号 `titleMedium`（比原来的 labelMedium 明显更大）
- 选中时整项变成 primary 填充的胶囊（52dp 高），**色块在文字底层铺满该项** = "色块覆盖字"
- 文字颜色：选中 `onPrimary`，未选中 `onSurfaceVariant`；选中加粗

### 16.4 验证

- `:app:compileDebugKotlin` 无错误无警告；`:app:testDebugUnitTest` 全绿。
- `:app:assembleRelease` + `adb install -r`；装好后需**重新创建一次测试待办**验证（旧包排的闹钟在新包里不一定重排）。

---

## 17. v1.7 变更记录（2026-10-21）

### 17.1 ★ 时段词 + 冒号时钟被截断（`晚上11:50` → 18:00）

**现象**：输入 `今天晚上11:50测试一`，预览变成 **10月21日 18点**，标题残留 `11:50测试一`，还报"时间已过"。

**根因**：`Parser.stripLeadingTime()` 的匹配顺序是 *冒号时钟 → N点 → 裸时段词 → 内嵌扫描*，
但冒号时钟的正则 `^(\d{1,2}):(\d{1,2})` **不允许时段前缀**。于是 `晚上11:50` 在第一步就不匹配，
被第三步的"裸时段词"规则抢走 → 按 `periodDefaults["晚上"] = 18` 定成 18:00，
剩下的 `11:50测试一` 自然落进标题。

**修复**：把时段变成冒号时钟的**可选组**，并抽出统一的 `clockMatchTime()`：

```kotlin
private val clockBody =
    "(凌晨|早上|早晨|上午|中午|下午|傍晚|晚上|夜里|半夜)?(\\d{1,2})\\s*[:：](\\d{1,2})"
```

`clockMatchTime()` 的取值优先级是 **前缀 > 上一个 token 遗留的 `pendingPeriod` > 原样**，
这样三种写法都能覆盖：

| 输入 | 结果 |
|---|---|
| `晚上11:50` | 23:50 |
| `晚上 11:50`（时段与时钟被空格拆成两个 token） | 23:50（走 `pendingPeriod`） |
| `开会晚上11:50`（内嵌） | 23:50 |

另外 `晚上12:xx` 的 `applyPeriod` 会得到 24 点，`clockMatchTime()` 里判断 `hour >= 24` 就
**把日期 +1 天**再取模，所以 `明天晚上12:30` = 后天 00:30，不会退化成当天 00:30。

新增 8 条回归用例（`ParserTest`）：`periodBeforeColonClock_*` 系列
（23:50 / 15:30 / 7:45 / 12:05 / 跨天 00:30 / 内嵌 / 空格拆开 / 中文数字 `晚上十一点`）。

> 一般规律：**任何"前缀修饰后续数字"的语法，前缀必须做进同一个正则**，
> 否则"短匹配规则"会先吃掉前缀，把数字孤立成文本。裸时段词这类兜底规则只能排在其后。

### 17.2 底部导航栏：胶囊收敛 + 降饱和

上一版是"整项铺满 primary 色块"，问题有二：颜色太艳（尤其鲜艳配色下像一块高亮砖），
色块横向撑满 1/3 屏宽、跟文字大小无关。

现改为**双层 Box**：

- 外层仍 `weight(1f) + height(52dp)` —— 保住等宽的点击热区；
- 内层胶囊只由文字撑开：`clip(RoundedCornerShape(50))` + `padding(horizontal = 22.dp, vertical = 7.dp)`，
  即"色块刚好覆盖字 + 一圈呼吸边距"；
- 颜色从 `primary` 换成 **`secondaryContainer`**，文字用 `onSecondaryContainer`；
  未选中仍是 `onSurfaceVariant`。

`secondaryContainer` 在 `Theme.kt` 里同样是由调色板种子派生的低透明度容器色（浅色 ~0.20，
深色 0.30），所以换成鲜艳配色也不会刺眼。

### 17.3 统一卡片底色（含设置页）

原先三处各写各的：
- 待办行 / 归档行：`primaryContainer.copy(alpha = 0.45f).compositeOver(surface)`
- 设置页 `SettingsCard`：`surfaceContainer`

`primaryContainer` 本身已经是"种子色低透明度叠在 surface 上"的结果，**再乘 0.45 属于二次冲淡**，
所以卡片几乎看不出底色，同时设置页又和列表不是一套颜色。

现在抽出一个共享口径（`TodoListScreen.kt`）：

```kotlin
private val ColorScheme.cardBackground: Color get() = primaryContainer
```

待办行、已归档行、`SettingsCard` 全部改用它。`primaryContainer` 的透明度在 `Theme.kt`
里按配色组区分（平衡组浅色 0.16 / 深色 0.30，鲜艳组浅色 0.22），换配色后卡片自动跟着变，
深色模式也不会变成发光的白块。

### 17.4 验证

- `:app:testDebugUnitTest` 全绿：ParserTest 80 + ReminderEngineTest 12 + FormatTest 6 = **98 条**。
- `:app:assembleRelease` → `adb install -r`。
- 真机自查要点：加一条 `晚上11:50开会` 看预览是否为 23:50 / 标题只剩"开会"；
  切到鲜艳配色看底部胶囊是否收敛在文字周围。

---

## 18. v1.8 变更记录（2026-10-21）

### 18.1 ★ 到点了不显示逾期（两个独立 bug 叠加）

**现象**：23:59 时，23:39 / 23:49 / 23:55 的三条待办还挂在"今天"下面，标题不标红。

**bug ①：标红被一个设置项绑架**

```kotlin
// 旧代码
val overdue = overduePin && todo.dueAtMillis != null && todo.dueAtMillis <= now
```

`overduePin` 是设置页里的"逾期置顶**标红**"开关。它同时控制两件事——排序和配色——
于是关掉这个开关 = 连红色都没有了。**排序可以关，标红不该关。**

改为：

```kotlin
val overdue = todo.dueAtMillis != null && todo.dueAtMillis <= now
```

`overduePin` 只留给 `sortedWith` 的置顶逻辑。

**bug ②：分组标签只比日期，不比时刻**

`Format.groupLabel` 原先是 `d.isBefore(today) -> "已逾期"`，`d == today` 一律进"今天"。
所以"今天 23:39"在 23:59 依旧算今天。加上 `nowMillis` 参数后：

```kotlin
d == today && due <= nowMillis -> "已逾期"
```

**bug ③（隐藏的）：`now` 根本不会往前走**

```kotlin
val today = java.time.LocalDate.now()
val now = System.currentTimeMillis()
```

这两行只在**重组**时求值，而列表只在数据变化时重组——没有新待办、没有勾选，
界面就一直用着几分钟前甚至几小时前的 `now`。即使 ① ② 都修好，不加定时器也不会自己变红。

现在 `TodoList` 里挂一个 20 秒的 ticker：

```kotlin
var now by remember { mutableStateOf(System.currentTimeMillis()) }
LaunchedEffect(Unit) {
    while (true) { delay(20_000); now = System.currentTimeMillis() }
}
val today = java.time.Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
```

`today` 也从 `now` 推导，跨零点时"今天/明天"会一起跟着翻。

> 教训：**凡是"随时间推移而改变"的 UI 状态，必须有一个时间源驱动重组**，
> 不能指望用户操作来触发。同理，"分组"与"配色"必须用同一个 `now`，否则会出现
> "分在已逾期组里但不红"这种自相矛盾的画面。

### 18.2 详情行同步标红

标题、时间（`周三 23:39`）、右侧日期本来就跟着 `overdue` 变 `error`，只有详情行漏了。
现在详情也用 `error`，透明度降到 0.85 保住"标题 > 详情"的主次。

### 18.3 归档撤销弹条统一底色

`CompletedBar` 从 `surfaceContainerHigh`（一块和调色板无关的灰）换成 `cardBackground`，
和待办卡片、归档行、设置卡片同色；保留 `shadowElevation = 6.dp` 把它从列表里"抬"起来。

### 18.4 验证

- `:app:testDebugUnitTest` 全绿：ParserTest 80 + ReminderEngineTest 12 + FormatTest 10 = **102 条**。
  FormatTest 新增 `groupLabel_todayPastTime_isOverdue` / `groupLabel_todayFutureTime_isToday` /
  `groupLabel_todayExactNow_isOverdue` / `groupLabel_tomorrowStaysTomorrow_evenLateTonight`，
  并把原有分组的断言全部改成**显式传入 now**（原来默认取真实时钟，跑起来会随时钟漂移）。
- `:app:assembleRelease` → `adb install -r`。

---

## 19. v1.9 变更记录（2026-10-22）

### 19.1 配色下拉面板：圆角 + 统一底色

`ExposedDropdownMenu` 用的是 M3 默认值：`containerShape` 只有 4dp（看起来就是直角），
`containerColor` 是 `surfaceContainer`（一块与调色板无关的灰）。所以弹出时像一张突兀的灰板，
压在同色的设置卡片上，属于"两套卡片系统"。

```kotlin
ExposedDropdownMenu(
    expanded = expanded,
    onDismissRequest = { expanded = false },
    shape = MaterialTheme.shapes.extraLarge,          // 与设置卡片同为 28dp
    containerColor = MaterialTheme.colorScheme.cardBackground,
    shadowElevation = 8.dp                            // 底色一致后靠阴影分出层次
)
```

外观（主题）下拉同样处理，两处保持一致。

### 19.2 ★ 深色模式不再"把颜色加深"——改为三档可选

**问题**：原来的深色容器是

```kotlin
primaryContainer = p.primary.copy(alpha = 0.30f).compositeOver(Color(0xFF141218))
```

这是把种子色**混进近黑背景**。青碧 `#00696D` 混完变成 `#0E2C31`——明度从 0.42 掉到 0.19，
色相还在但整块发暗发脏，观感是"你选的颜色被压黑了"，而不是"你选的颜色"。

**根因**：往黑里做 alpha 混合，本质是**同时降低明度和饱和度**；M3 官方暗色容器并不是这么来的，
它是「同一色相取 tone 30」（雅紫 → `#4F378B`，明度反而被抬住）。

**修法**：抽一个只动明度、不动色相的 `tone()`：

```kotlin
private fun tone(seed: Color, brightness: Float, chroma: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seed.toArgb(), hsv)
    hsv[1] = (hsv[1] * chroma).coerceIn(0f, 1f)   // 饱和度倍率
    hsv[2] = brightness.coerceIn(0f, 1f)          // 明度
    return Color(android.graphics.Color.HSVToColor(hsv))
}
```

并把它做成**用户可选**的四档（设置 → 外观 → 深色卡片底色，`SettingsRepo.darkCardStyleId`）：

| 选项 | brightness | chroma | 效果 |
|---|---|---|---|
| 不染色 | 0.21 | ×0 | 纯中性灰，只靠圆角区分卡片 |
| 淡彩 | 0.30 | ×1.0 | 比背景略亮、带一点色气 |
| 标准暗色（默认） | 0.50 | ×1.15 | 能明确看出是紫/青/橙，M3 暗色容器观感 |
| 深彩 | 0.62 | ×1.30 | 浓郁彩底，存在感最强 |

`DarkCardStyle.all` 按「无色 → 浓色」排序，所以枚举的声明顺序和显示顺序解耦了；
`id` 是持久化值，**跨版本必须保持不变**（加新档只追加 id，不重排）。

四档都是「只动明度/饱和度、不动色相」，所以任何一档都不会把配色压成脏色；
浅色模式不受影响（浅色仍走 `seed.copy(alpha).compositeOver(#FFFBFE)`，
那种"淡"是靠低透明度得到的，方向是对的）。

### 19.3 验证

- `:app:compileDebugKotlin` 无错误；`:app:testDebugUnitTest` 102 条全绿。
- `:app:assembleRelease` → `adb install -r`。
- 自查：设置 → 外观，展开配色下拉看是否圆角、底色是否与下方卡片同色；
  切深色模式，在 不染色 / 淡彩 / 标准暗色 / 深彩 四档之间切换看卡片明度变化。

---

## 20. 项目体检与功能候选（2026-10-22）

### 20.1 体检发现的问题（按严重度）

> **状态（2026-10-22 v2.0 更新）**：P0②③、P1④⑤⑥、P2⑦⑧⑨ 已在 **v1.9.1** 修复（见 §21）；
> **P0① 逾期反复叮嘱链条已在 v2.0 修复**（见 §22.1）。本条清单全部闭环。

**P0 — 功能实际不生效**

1. **「逾期反复叮嘱」对新创建的待办永远不会触发。**
   `ReminderScheduler.scheduleBlocking()` 里只有 `if (nagEnabled)` 分支，但它依赖
   `ReminderEngine.nextOverdueReminder()`；而该函数开头有一句
   `if (due > now) return null` —— 即**到期之前不排第一个唠叨闹钟**。
   于是：新建一条 2 小时后到期的待办 → 只排了 DUE 闹钟；DUE 触发后
   `ReminderReceiver` 只在 `kind == OVERDUE` 时才续排 → **链条根本没启动**。
   只有"创建时就已逾期"或"开机时已逾期"的待办才会被唠叨。
   *修复方向*：让 `nextOverdueReminder()` 无条件返回 `due + interval`（并做「对齐到 ≥ now」的对齐），
   到期前也把第一个唠叨点排上。

2. **`BootReceiver` 声明了 `TIMEZONE_CHANGED` 却没处理。**
   Manifest 里注册了 `BOOT_COMPLETED` 和 `TIMEZONE_CHANGED`，但 `onReceive` 的 `when`
   只匹配 `ACTION_BOOT_COMPLETED`。而 `dueAtMillis` 存的是**绝对 epoch**，
   跨时区后墙钟含义就变了（原本 09:00 的闹钟会变成 10:00 响）。
   *修复方向*：`when` 里补 `Intent.ACTION_TIMEZONE_CHANGED -> scheduleAllActive(context)`。

3. **所有提醒通知共用一个通知 id（`NOTIFICATION_ID_REMINDER = 1001`）。**
   两条待办同时到期时，后一条会**直接覆盖前一条**，用户只看到最后一个。
   *修复方向*：`notify` 的 id 用 `(todoId % Int.MAX_VALUE).toInt()` 或 `todoId.toInt()`。

**P1 — 逻辑不一致**

4. **无时刻的待办会"立刻逾期"。**
   `parsedToDue()` 对 `dueTime == null` 用 `defaultMinuteOfDay`（默认 09:00）兜底。
   晚上 23:59 输入「今天开会」→ 到期时间 = 今天 09:00 → 立刻判为逾期并进入唠叨。
   对**重复**待办这点尤其不对：「每天」应当顺延到明天 09:00，而不是一创建就逾期。
   *修复方向*：`parse()` 里对 `repeatDays > 0` 的情况，用"默认时刻"参与 `rollToFuture` 判断。

5. **`gradle.properties` 写死了本机绝对路径**
   （`org.gradle.java.home=C:\\Users\\lirui\\.jdks\\...`）。换机器 / CI 会直接构建失败。
   *修复方向*：该行移到 `~/.gradle/gradle.properties`。

6. **没有 Gradle Wrapper。**
   仓库里没有 `gradlew` / `gradle/wrapper/`，构建依赖本机恰好装了 Gradle 9.5.0。
   别人 clone 下来大概率跑不起来。
   *修复方向*：`gradle wrapper --gradle-version 9.5.0`，把 `gradlew`、`gradlew.bat`、
   `gradle/wrapper/gradle-wrapper.jar`（+ properties）提交进去。

**P2 — 小问题**

7. `NotificationHelper.CHANNEL_ID_COMPLETE` 声明了但 `init()` 没有创建该渠道，且无 `postComplete()` 调用点 —— 死代码。
8. `versionCode = 1` / `versionName = "1.0"` 一直没动过，功能已经迭代到 v1.9。
9. `ReminderScheduler.canScheduleExact()` 用 `as AlarmManager` 强转，理论上有 NPE 风险（同文件 `scheduleBlocking` 里用的就是 `as?`）。

### 20.2 功能候选（待与 zuige 讨论，未实施）

见 §20.3 的分级与取舍；**在拍板前不动手**。

### 20.3 候选清单

| # | 功能 | 说明 | 依赖改动 |
|---|---|---|---|
| A | **子待办 / 清单** | 一条待办下挂若干勾选项 | `Todo` 新增表或 JSON 列 |
| B | **标签 / 分类** | `#工作` 之类，支持按标签筛选 | 新表 + 过滤 UI |
| C | **优先级** | 高/中/低，影响排序与配色 | `Todo` 加字段 |
| D | **日历月视图** | 按月看所有待办分布 | 新 Screen |
| E | **自然的"推迟"** | 「推到明天」「半小时后」 | Parser 规则 |
| F | **Widget / 桌面小组件** | 桌面直接看今日待办 | Glance |
| G | **数据导出 / 备份** | 导出 JSON，可导入 | 文件读写 |
| H | **统计 / 完成率** | 周/月完成趋势 | 聚合查询 |
| I | **批量操作** | 多选归档 / 删除 | 选择态 |
| J | **通知快捷操作** | 通知上直接「完成」「推迟 10 分钟」 | `NotificationCompat.Action` |
| K | **重复规则增强** | 「每月第一个周一」「每两周」 | Parser + 计算 |
| L | **地点提醒** | 到达某地提醒 | 位置权限（重） |

### 20.4 v0.1 发布记录（2026-10-22）

- `versionCode = 1` / `versionName = "0.1"`
- 仓库：`https://github.com/zuige66/todo.git`（远端名 `origin`，默认分支 `main`）
- 首个提交 `76c0047`，共追踪 **41 个文件**；已核验远端**不含** `keystore.properties`、
  `app/oneline-release.keystore`、`local.properties`、`_smoke/`
- 打 `v0.1` 附注标签并推送
- ⚠ **签名密钥已备份到 `D:/Workplace/Oneline-keystore-backup/`**（仓库外）。
  它不在 git 里，丢了就无法覆盖安装已装机版本（签名不符 → 更新失败、必须卸载）
- ⚠ 本机全局 git 配置有 `url.git@github.com:.insteadof https://github.com/`，
  所以虽然 remote 写的是 HTTPS，**实际推送走的是 SSH**。换机器/CI 时要注意这点
- ~~⚠ **仓库仍缺 Gradle Wrapper**~~ → 已在 v1.9.1 补上（`gradlew` / `gradlew.bat` /
  `gradle/wrapper/`，9.5.0），现在 clone 下来可直接 `./gradlew assembleRelease`

---

## 21. v1.9.1 变更记录（2026-10-22）

### 21.1 ★ 全应用时区钉死为北京时间

**问题**：时区散落在各处，且**两边口径不一致**：

| 位置 | 原来用什么 |
|---|---|
| `Format.kt`（4 处显示） | `ZoneId.systemDefault()` |
| `TodoRepository`（算 epoch） | `ZoneId.systemDefault()` |
| **`Parser.refreshClock()`（算"今天"）** | **`LocalDate.now()` —— 设备默认时区** |

`LocalDate.now()` 用的是 `TimeZone.getDefault()`，也就是**设备时区**；而 `ZonedDateTime.of(date, time, systemDefault())` 也是设备时区。表面上一致，**但设备时区一变就不一致了**：
跨时区后，同一个 `dueAtMillis`（绝对 epoch）对应的墙钟变了 —— 原本"09:00"的闹钟会变成 10:00 响。而列表显示也会跟着跳。用户的诉求是**始终按北京时间**，所以不能依赖设备时区。

**修法**：新增 `util/AppTime.kt`，全应用只认一个时区：

```kotlin
object AppTime {
    val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
    fun today(): LocalDate = LocalDate.now(ZONE)
    fun now(): LocalTime = LocalTime.now(ZONE)
    fun nowMillis(): Long = System.currentTimeMillis()
}
```

替换点：`Format.kt` 4 处、`TodoRepository` 3 处、`ReminderEngine` 2 处默认值、
`TodoListScreen`（`TodoList` 与编辑弹层回填）、`MainViewModel.previewExisting`。

单元测试里原本就写死 `Asia/Shanghai`，所以不受影响、仍然确定性。

### 21.2 时区广播：删掉而不是补上

`BootReceiver` 声明了 `TIMEZONE_CHANGED` 却从没处理过它。既然时区已被钉死，
`dueAtMillis` 的含义不再随设备时区漂移 —— **这个 action 已经没有存在的理由**，
所以从 Manifest 移除，并在 `BootReceiver` 的 KDoc 里写清楚原因，
避免后人又把它加回来。
（如果哪天 `AppTime.ZONE` 变成用户可配，这条要重新考虑。）

### 21.3 每条待办独立通知 id

原来所有提醒共用一个 `NOTIFICATION_ID_REMINDER = 1001`，
两条待办同时到期时后一条**静默覆盖**前一条。改为按 todoId 派生：

```kotlin
private fun reminderId(todoId: Long): Int = (200_000 + todoId % 1_000_000).toInt()
```

"发送测试通知"按钮不属于任何待办，单独走固定 id（1001）。

### 21.4 P1-④：只写日期的待办不会立刻逾期

`parsedToDue()` 对 `dueTime == null` 用 `defaultMinuteOfDay`（默认 09:00）兜底，
于是晚上 23:59 输入「今天开会」→ 到期 = 今天 09:00 → **一创建就红 + 唠叨**。

现在按「日期粒度」判定：

```kotlin
fun isOverdue(due, today, nowMillis, hasExactTime = true): Boolean {
    if (due == null) return false
    if (!hasExactTime) return dueAtMillis(due).isBefore(today) // 只写日期：过了那天才算
    return due <= nowMillis                                    // 有精确时刻：过点即逾期
}
```

**分组与标红现在共用这一个函数**（`Format.groupLabel` 内部也调 `isOverdue`），
从根上杜绝"分在已逾期组里却不红"这类自相矛盾。

### 21.5 P1-⑤ / P1-⑥ / P2

- **P1-⑤**：`gradle.properties` 里的 `org.gradle.java.home` 已移到 `~/.gradle/gradle.properties`，
  项目里不再有机器相关路径（移除后重新编译验证通过）。
- **P1-⑥**：补上 Gradle Wrapper（9.5.0），`gradlew` / `gradlew.bat` /
  `gradle/wrapper/*` 一并提交，换机器可直接构建。
- **P2-⑦**：删掉死代码 `NotificationHelper.CHANNEL_ID_COMPLETE`（无渠道创建、无调用点）。
- **P2-⑧**：版本号 `versionCode=1 / versionName="0.1"`。
- **P2-⑨**：`canScheduleExact()` 改用 `as?` + `?: return false`，与同文件其他取服务的写法一致。

### 21.6 验证

- `:app:testDebugUnitTest` **107 条全绿**（ParserTest 80 / ReminderEngineTest 12 / FormatTest 15）
  - FormatTest 新增 5 条：无时刻待办当天不逾期 / 昨天才算逾期 / 有时刻过点即逾期 /
    有时刻未到点不逾期 / 无日期永不逾期
- `:app:assembleRelease` → `adb install -r` `Success` → 启动 FATAL 数 0

---

## 22. v2.0 变更记录（2026-10-22）

> §20.1 的体检清单到此全部闭环：P0①（本轮）＋ P0②③、P1④⑤⑥、P2⑦⑧⑨（v1.9.1）。
> 另加一项新功能：通知栏「完成 / 撤回」。

### 22.1 ★ bug1：逾期反复叮嘱链从创建那一刻就没启动

**现象**：新建一条两小时后到期的待办，到期后只响一次 DUE 通知，之后再也不唠叨；
只有「创建时就已逾期」或「开机时就已逾期」的待办才会反复提醒。

**根因**：`ReminderScheduler.scheduleBlocking()` 是唯一排 OVERDUE 闹钟的地方，而它依赖
`ReminderEngine.nextOverdueReminder()`；该函数开头有一句

```kotlin
if (due.toInstant().toEpochMilli() > now.toInstant().toEpochMilli()) return null
```

即**到期之前不返回任何唠叨点**。而 `ReminderReceiver` 只在 `kind == OVERDUE` 触发时才续排下一个：

```
创建(2h 后到期) → 只排 DUE
DUE 响        → 发通知，不续排（kind 不是 OVERDUE）
              ✗ 链到此为止，第一个 OVERDUE 从没人排过
```

**修法**：删掉"未到期返回 null"的判断，无条件返回 `due + interval`，并对齐到 ≥ now 的整周期边界：

```kotlin
val firstNagMs = dueAtMillis + stepMs
val steps = if (firstNagMs >= nowMs) 0L else ((nowMs - firstNagMs) / stepMs) + 1L
```

**为什么"提前排"是安全的**：

- 唠叨点恒为 `due + k·interval`（k ≥ 1），**严格晚于**到期时刻，不会在 DUE 之前打扰用户；
- 同一个 `requestCode`（`kind.ordinal = 3`）覆盖写，重复 schedule 幂等；
- 到期前重排（编辑 / 开机 / 改设置）结果完全不变 → 单测
  `overdueNag_notYetDue_stableAcrossReschedules` 锁死。

**修复后的完整链路**：

```
创建 → DUE(ordinal 2) + OVERDUE(ordinal 3, due+24h) 同时排上
DUE 响      → 只发通知（OVERDUE 已在排队，无需续排）
OVERDUE 响  → 发通知 + reScheduleOverdue() → 重排，得到下一个 OVERDUE
完成/归档    → cancelForId() 把 0..3 全部取消，链自然终止
```

### 22.2 新功能：通知栏「完成」→「撤回」

提醒通知上多了一个按钮：点「完成」→ 按钮原地变成「撤回」。

| 环节 | 实现 |
|---|---|
| 按钮 | `NotificationCompat.addAction`，PendingIntent 指向 `NotificationActionReceiver`（`exported=false`） |
| 完成 | `TodoRepository.completeFromNotification()` → 直接 `archive()`，与列表圆圈**同一个入口**（普通待办归档、重复待办 `rollForward` 顺延） |
| 态切换 | 用**同一个 notification id** 重发 + `setOnlyAlertOnce(true)`，文案变「已完成 · 点「撤回」可恢复」 |
| 撤回 | 普通待办 `unarchive()` + 重排提醒；重复待办回滚一个周期；**通知保留**并静默切回「完成」态 |
| 点击通知本体 | 新增 `contentIntent` → 打开 App |

**为什么撤回后通知不消失**：v2.0 初版是"撤回即取消通知"，真机反馈是操作后整条通知消失，
没法接着再点一次完成。现在撤回 = 把同一条通知静默重发回「完成」态：
`setSilent(true)` + `setOnlyAlertOnce(true)` 保证不再响铃/震动，原提醒文案由
`EXTRA_MESSAGE` 随 action 的 PendingIntent 带下去，撤回时原样还原。

**关于「完成」按钮能不能放在右侧**：不能。action 落在系统 StatusBar 的 action row 里，
由 SystemUI 从起始边（中文环境是从左）依次排布，App 只能决定按钮文字、图标和顺序，
没有任何 API 可以右对齐。真要放右边只能整条通知换成自定义 `RemoteViews`，代价是丢掉
系统的折叠/展开、大字号、无障碍与深浅色适配，且在 HyperOS 上可能被折叠成一行 —— 不值得。

**重复任务的撤回为什么是"回滚一个周期"而不是"回到原始时刻"**：库里没有存"完成前的 epoch"，
不想为这个功能加字段/迁移。每天任务拖了 5 天才完成时，`rollForward` 会一路跳到明天，
回滚一个周期落在**今天**（最近一次过去的到期点）——语义上"我还没做完这次"，成立。
函数抽成纯逻辑 `ReminderEngine.previousOccurrence(due, repeatDays)`，可单测。

**防串扰**：`requestCode` 由 `(todoId, slot)` 共同决定（3=完成、4=撤回），否则
`FLAG_UPDATE_CURRENT` 会让两个 action 的 PendingIntent 互相改掉对方的 extras。

### 22.3 v1.9.1 漏改的两处时区

§21.1 声称已把时区统一到 `AppTime.ZONE`，但还有两处漏了：

| 文件 | 原来 | 现在 |
|---|---|---|
| `MainViewModel.previewExisting()` | `atZone(ZoneId.systemDefault())` ×2 | `atZone(AppTime.ZONE)` |
| `TodoListScreen` 编辑弹层回填 | `ZoneId.systemDefault()` + `LocalDate.now()` | `AppTime.ZONE` + `AppTime.today()` |

影响：设备时区不是 UTC+8 时，长按编辑一条待办，弹层里回填的日期/时间会按设备时区显示，
与列表显示的北京时间不一致 → 直接点保存就把待办改到错误时刻。

### 22.4 版本号

`versionCode 1 → 2`，`versionName "0.1" → "0.2"`。

### 22.5 验证

- `:app:testDebugUnitTest` **113 条全绿**（ParserTest 80 / ReminderEngineTest 18 / FormatTest 15）
  - 删 1 条（`overdueNag_notDue_returnsNull`，语义已反转）
  - 新增 7 条：未到期也返回第一个唠叨点 / 唠叨点恒晚于到期 / 到期前重排幂等 /
    `previousOccurrence` 每天 / 每周 / 非重复返回 null / 无到期返回 null
- `:app:assembleRelease` → `adb install -r`。
- 真机自查要点：
  1. 新建一条 1 分钟后到期的待办，等它响 → 通知上应出现「完成」按钮；
  2. 点「完成」→ 列表里该条消失，通知按钮变「撤回」且**不会**再响一声；
  3. 点「撤回」→ 待办回到列表，**通知仍在**、按钮变回「完成」且不再响一声；
  4. 设置里把「逾期反复叮嘱」的间隔调成 1 小时，新建一条 1 分钟后到期的待办，
     到期后等 1 小时确认收到 OVERDUE 唠叨（这是 bug1 的直接验证路径）。


---

## 23. v2.1（安装包 0.3）— 首页分组自定义 + 深色彩降亮

> 文档里的 `v2.x` 是**变更集编号**，`versionName` 是安装包版本（当前 0.3 / versionCode 3）。
> 两者不同步是有意的：变更集按设计迭代编号，安装包按发版次数编号。

### 23.1 分组逻辑搬家：`Format.groupLabel` → `HomeGrouping`

旧实现是一条写死的 `when` 链（`已逾期 → 今天 → 明天 → 后天 → 本周 → 下周 → 更晚`），
顺序和档位都不可配置。v2.1 拆成两层，放在新文件 `util/HomeGrouping.kt`（纯 Kotlin，可单测）：

| 层 | 枚举 | 可配置项 |
|---|---|---|
| 板块 | `HomeBoard`：逾期 / 有日期 / 无日期 | 顺序、显隐 |
| 档位 | `HomeBucket`：今日/明天/后天/本周/下周/更晚 | 多选（只作用于「有日期」板块内部） |

`Format.groupLabel` 已删除。**分组逻辑只有一份实现**——这正是 P0 时代"分组说已逾期、颜色不红"
那种事故的根因（两处各写一套判定），不能再犯。

### 23.2 归并规则（这是整个功能的关键）

用户只勾一部分档位时，未勾档位的待办**不能消失**。规则：

1. 先算"自然档位"（`bucketOf`，今天/明天/后天优先于本周，否则本周内的周一二会被吞掉）；
2. 往上找**第一个已勾选的档位**，归进去；
3. 后面没有已勾档位 → 归到**最后一个已勾档位**兜住；
4. 一个都没勾 → `resolveBucket` 返回 null → 该板块**平铺**，不显示档位小标题。

| 勾选 | 今天 | 明天 | 后天 | 下周三 |
|---|---|---|---|---|
| 全部 | 今日 | 明天 | 后天 | 更晚 |
| 今日+本周+更晚 | 今日 | 本周 | 本周 | 更晚 |
| 今日+明天+本周+更晚 | 今日 | 明天 | 本周 | 更晚 |
| 全不勾 | 平铺 | 平铺 | 平铺 | 平铺 |

### 23.3 板块

- **归属互斥**：`due == null → 无日期`；`Format.isOverdue(...) → 逾期`；否则 `有日期`。
  一条待办不可能同时出现在两个板块里。
- **顺序存储**：逗号分隔的 id 字符串（`"overdue,dated,undated"`）。用 `StringSet` 会丢顺序。
  读取时对非法/缺失 id 做补齐，配置损坏也不会少一个板块。
- **显隐**：设置页开关；**禁止关掉最后一个**（`visibleBoardCount > 1` 才允许关），
  `HomeGrouping.visibleBoards()` 还有一层兜底（全隐藏则回落到默认顺序），
  避免首页变成空白页且找不到回来的路。
- **「逾期置顶」开关退役**：板块顺序已经能表达"逾期排最前"（默认值就是它）。
  两个机制并存会出现"逾期板块排到最下面、但仍在顶部置顶"的矛盾。
  `KEY_OVERDUE_PIN` 常量一并删除（旧安装里的残留键不再读取）。

### 23.4 设置页新增「首页分组」

```
首页分组
  板块顺序
    ↑ ↓ 逾期      [开关]
    ↑ ↓ 有日期    [开关]
    ↑ ↓ 无日期    [开关]
  ─────────
  「有日期」板块内的分组
    [今日] [明天] [后天] [本周] [下周] [更晚]
  没勾选的档位会并入下一个已勾选的档位；全部不勾时该板块直接平铺。
```

### 23.5 深色彩降亮

真机反馈「深彩太亮，颜色是深了但太亮」。四档的 HSV 明度整体下压一档：

| 档位 | brightness | chroma |
|---|---|---|
| 不染色 NEUTRAL | 0.21 → 0.19 | 1.0 → 0 |
| 淡彩 SOFT | 0.30 → 0.26 | 1.0 |
| 标准暗色 STANDARD | 0.50 → 0.42 | 1.15 → 1.05 |
| 深彩 RICH | 0.62 → **0.50** | 1.30 → **1.15** |

`深彩` 原来的 0.62 在 OLED 上读起来是"发光"而不是"深色"，卡片比正文还抢眼。
现在 0.50 仍是四档里最浓的，但不会再盖过文字。

### 23.6 验证

- `:app:testDebugUnitTest` **128 条全绿**
  （ParserTest 80 / ReminderEngineTest 18 / FormatTest 9 / **HomeGroupingTest 21**）
  - 新增 HomeGroupingTest 21 条：板块归属（无日期/逾期/日期粒度）× 档位边界（今日~更晚、跨月、
    周日/周一） × 归并规则（全勾/部分勾/单档/全不勾） × 板块顺序与兜底
  - FormatTest 由 15 条减到 9 条：`groupLabel` 相关断言随函数一起删除，
    逾期判定（`isOverdue`）的 6 条全部保留并补了"恰好在到期时刻也算逾期"
- `:app:assembleRelease` 成功。
