# 开发笔记归档 - Oneline

> 本文件是 `.workbuddy/memory/MEMORY.md` 的**完整历史归档**（2026-10-22 因超出记忆体积上限而迁出）。
> MEMORY.md 只保留红线与索引；需要查具体踩坑细节时来这里搜。

## 基本信息
- 项目名：Oneline（settings.gradle.kts 中 rootProject.name = "Oneline"）
- 包名：com.lirui.oneline
- 应用模块：:app
- 本地构建产物：
  - release: app/build/outputs/apk/release/app-release.apk
  - debug:   app/build/outputs/apk/debug/app-debug.apk

## 设备 / 工具
- adb 绝对路径（不在 Git Bash PATH，调用须用绝对路径）：
  C:/Users/lirui/AppData/Local/Android/Sdk/platform-tools/adb.exe
- 调用 adb 时务必先 `export MSYS_NO_PATHCONV=1`，否则 Git Bash 会把 `D:/...` 路径改写导致失败。
- 测试设备：gauguinpro（Redmi Note 9 Pro 系列），serial `1b3a7d8`，状态 device（已授权）。
- `adb install` 在 gauguinpro 上正常（走 pm install，不经 shell input 注入）；MIUI USB 安装已授权，不弹窗。

## 红线 / 习惯
- 任务前先讨论方案，不动手；改动文件后更新对应 md 文档。
- **禁止无脑 `git add -A`**：项目根曾被工具解包产物污染（`_dshextracted/` 1.6 万文件、
  `node_modules/`、`.npm-cache/`、`package.json`），一把 add 全进了 commit。
  提交前先 `git status --short` 看清单，优先 `git add <具体路径>`。这些路径已在 .gitignore。

## 构建 / 装机（实测可用）
- `JAVA_HOME` 必须 JDK17：`C:/Users/lirui/.jdks/jdk17.0.20_12`（否则被 VS Code 扩展 JRE 劫持，jlink 报错）
- Gradle 9.5.0（无 gradlew）：`C:/Users/lirui/.gradle/wrapper/dists/gradle-9.5.0-bin/bvnork1r7n8i6kp5cnkibsc9q/gradle-9.5.0/bin/gradle.bat`
- 流程：先 `gradle --stop`，再 `:app:testDebugUnitTest` / `:app:assembleRelease`
- 设备上是 **release 签名**；装 debug 包会 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，必须打 release 覆盖
- Room schema 现为 **v3**（todos 新增 `detail TEXT NOT NULL DEFAULT ''`，迁移 2→3）
- gauguinpro 拒绝 `INJECT_EVENTS`：`adb shell input` / `keyevent` 全部失败；锁屏时无法远程点亮屏幕或解锁截图，UI 验收需用户手动亮屏

## ★ 精确闹钟权限（排程提醒收不到的头号原因）
- **targetSdk ≥ 33 的应用，`SCHEDULE_EXACT_ALARM` 在 Android 13+ 默认不授予**，
  `AlarmManager.canScheduleExactAlarms()` 返回 false → 降级 `setAndAllowWhileIdle` → 系统随意延后（MIUI/HyperOS 尤其明显）
- 必须在 Manifest 追加（提醒/闹钟类应用，安装即授予、无需用户手动开）：
  `<uses-permission android:name="android.permission.USE_EXACT_ALARM" android:minSdkVersion="33" />`
- 排查命令：
  - `adb shell cmd appops get <pkg> android:schedule_exact_alarm` → `Default mode: default` = 未授予
  - `adb shell dumpsys package <pkg> | grep -i exact` → 看 `USE_EXACT_ALARM: granted=true`
  - `adb shell dumpsys alarm | grep <pkg>` → 确认闹钟是否真的排上（区分"没排程" vs "排了但延迟"）

## 代码结构要点
- 解析：`parser/Parser.kt`；时段词默认时刻表 `periodDefaults`（早上8/中午12/下午14/晚上18）；
  剩余 token 首段=事件标题、其后=详情（`ParsedTodo.detail`）
- **多日期**：`Parser.parseAll()` 返回 List（一句话多个日期→多条待办）；`parse()` 仍单条
- **列表分组**：`util/HomeGrouping.kt`（`HomeBoard` 板块：逾期/有日期/无日期，顺序+显隐；
  `HomeBucket` 档位：今日/明天/后天/本周/下周/更晚，多选）。**`Format.groupLabel` 已删除**，
  不要再写第二套分组逻辑——分组与标红共用 `Format.isOverdue`
- **归并规则**：未勾选档位并入"下一个已勾档位"，后面没有则由最后一个已勾兜住；全不勾=板块平铺。
  改动这条规则前先想清楚"只勾今日"时下周的待办去哪（不能让数据消失）
- **「逾期置顶」(overdue_pin) 已退役**：板块顺序已能表达"逾期排最前"，两机制并存会打架
- 弹层：`ui/screen/AddSheet.kt`（预览卡片四段式，支持多行 + "将创建 N 条"）
- 设置页：`TodoListScreen.kt` 内 `SettingsScreen`（M3E `ListItem` 卡片 + `FilterChip` + 外观 `ExposedDropdownMenu`）
- 重复任务：完成→`rollForward` 顺延（非归档）；`TodoRow.checked` 用 `remember(id, dueAtMillis)` 才会复位
- **提醒/唠叨链的红线**：`ReminderScheduler.scheduleBlocking` 是唯一排 OVERDUE 的入口，
  它依赖 `ReminderEngine.nextOverdueReminder()`；该函数必须**在到期前也返回** `due+interval`。
  一旦加回"未到期返回 null"，新建待办的唠叨链就永远不启动（DUE 触发时 receiver 只续排 OVERDUE）
- **通知栏动作**：`NotificationActionReceiver`（exported=false）处理「完成/撤回」；
  完成必须复用 `TodoRepository.archive()`（与列表圆圈同一入口），不要另写归档逻辑；
  态切换靠**同一 notification id** 重发 + `setOnlyAlertOnce(true)`，requestCode 用 `(todoId, slot)`
- 列表行右侧元信息：`[每天/每周/每月徽章] 日期` + 第二行时间；编辑弹层可关闭重复
- 设置页"测试"分区：发送测试通知 / 新建下一分钟测试待办（`repo.addTestInOneMinute`）
- **深色卡片底色三档**：`DarkCardStyle`（标准暗色 / 淡彩 / 不染色）→ `theme.darkScheme(p, style)`；
  `SettingsRepo.darkCardStyleId` 持久化，`OnelineTheme(darkCardStyle=...)` 传入
- **通则**：深色底上的色彩强调要用「抬明度/保色相」（`tone()` 走 HSV 只改 V），
  不要用 alpha 混合进近黑 —— 前者得到深色版的红/青/紫，后者得到发灰的脏色
- 下拉面板统一：`ExposedDropdownMenu(shape = shapes.extraLarge, containerColor = cardBackground)`，
  M3 默认 `containerShape` 只有 4dp、底色是 `surfaceContainer`
- **配色体系**：`ui/theme/Theme.kt` 的 `Palettes`（11 套：6 平衡 + 5 鲜艳）；
  `OnelineTheme(darkTheme, paletteId)`；`SettingsRepo.paletteId` 持久化；设置页 外观→配色 下拉
- 列表行：右侧 `[重复徽章] 日期`；标题下灰色副行 `周几 时间`
- **共享卡片底色**：`TodoListScreen.kt` 里 `private val ColorScheme.cardBackground get() = primaryContainer`，
  待办行 / 归档行 / `SettingsCard` 全用它。**不要再 `.copy(alpha=0.45f).compositeOver(surface)`**——
  `primaryContainer` 本身已是"种子色低透明度叠 surface"，二次合成会把卡片冲淡到看不见
- 底部导航：自定义 `NavTab`（双层 Box：外层 `weight(1f)+52dp` 保住点击热区，内层胶囊由文字撑开
  `padding(22.dp, 7.dp)`）；选中色 `secondaryContainer` / `onSecondaryContainer`，未选中 `onSurfaceVariant`
- **时区**：全应用只用 `AppTime.ZONE`（Asia/Shanghai，北京时间）。
  禁止再写 `ZoneId.systemDefault()` / `LocalDate.now()`（无参）——
  设备时区一变，存的 epoch 对应墙钟就漂移
- ★ **M3 DatePicker 的毫秒口径是「UTC 当日 00:00」，不是本地午夜**（0.8.7 修的真机 bug）：
  `rememberDatePickerState(initialSelectedDateMillis = d.atStartOfDay(AppTime.ZONE)...)` 早了 8 小时，
  日历内部按 UTC 解读 → 显示成**前一天**；且 `selectedDateMillis` 就是那个值，点「确定」会把日期真的减一天。
  进（初始值）出（确定）两端统一走 `AppTime.pickerMillis(date)` / `AppTime.pickerDate(millis)`，
  `SelectableDates.isSelectableDate` 同样用 `pickerDate`。结束日、开始日两个选择器都要改，
  漏一个就只剩一个选择器是对的。
- ★ **时间段的年份必须"整段一起定"**（0.8.8 修的真机 bug：编辑进行中的时间段，日期跳到明年）：
  `stripRange` 的左半**不能**直接走 `stripLeadingDate`——那条路对「没写年份的月日」会
  `nextMonthDay` 顺延到明年，右半再以左半为基准 → 两端一起 +1 年。
  现在的规则：左半只写月日/日号时先落**今年/本月**（`resolveRangeLeft` 返回 `RangeShift`），
  右半的月日/日号**跟随左半**（`start.year`），最后整段都在今天之前才整体前移一个周期。
  ★ 单测 `today` 固定在 2026-10-21 时**测不出**这类 bug（10/21 恰好是当天），
  新用例统一用 `Parser(2026-10-23)` 这类"参考日之后"的 today。
- 编辑弹层打开时**不再重新解析**回填句子（`AddSheet` 的 `LaunchedEffect` 加 `if (!editing)`）：
  值直接来自 `previewExisting` 的精确反推，避免"点开编辑、什么都没改、一保存日期就变"。
- ★ **时间段的起止用同一个区间日历选**（0.8.9）：「开始 X」「结束 Y」两个 chip 合成一个「X – Y」chip，
  点开是 M3 `DateRangePicker`。三条口径：
  ① 区间选择器**不传 `selectableDates`**（= 允许选过去）—— 进行中 / 已开始的时间段本来就在过去，
     沿用单日选择器的 `TodayOrLater` 会让开始日根本改不了；
  ② 只在日历上点了起点（`selectedEndDateMillis == null`）= 用户只想要一天 → 退回单日：
     `startDateOverrides[index] = null` + `dateOverrides[index] = 起点`；
  ③ 「取消开始日」同样退回单日（结束日留着）。区间上**不放**「无日期」——想彻底去掉日期就先退回单日，
     再从单日 chip 选「无日期」，否则确认区要堆三个文字按钮（360dp 宽度放不下）。
  `DateRangePicker` 放进 `DatePickerDialog` 的内容槽**必须加 `Modifier.weight(1f)`**，
  否则按内容高度撑开、月份滚动区被压没。
- **日历上「今天」的描边圈已去掉**（0.8.9）：`DatePickerDefaults.colors(todayDateBorderColor = Color.Transparent)`，
  单日与区间两个日历共用同一个 `pickerColors`。真机上"选中日实心 + 今天还有个圈"会被读成选了两天；
  去掉的只是描边圈，选中态与区间底色不受影响。
- ★ **区间日历的 headline 必须自己给，且要短**（0.9.0 修的真机 bug）：
  M3 默认 headline 是「2026年10月25日 - 2026年10月31日」一整行，塞进标题栏右侧那个窄槽后
  **逐字折行**（「202」/「6年」/「10」/「月」…，真机上看起来像一列竖排）。
  现在传 `headline = { Column { 开始日; 「至 结束日」 } }`，且用 `Format.monthDay`（「10月25日」）
  而不是 `Format.dateShort`（带周几太长）——每行 6~9 个字，槽再窄也不会折。
- ★ **日期可以写在文字后面**（0.9.0）：`stripLeadingDate` 所有分支都是 `lookingAt()`，
  所以 token 以文字开头时整段日期被当标题吃掉 ——「出差25号到1号」解析不出日期，
  「25号到1号出差」却是对的（真机反馈）。
  `peelTitlePrefix` 兜底：**先按原样走 stripRange/stripDuration**，都失败才扫描"从哪个位置
  开始能解析出日期"，把前面的文字收进 `Acc.title`，尾巴重走一遍。
  三个保险：前缀含数字不剥（别切在 `2026年` 中间）、前缀含 `到/至/从/~` 不剥
  （`到下周三`、`从24日起3天内` 是语法不是标题）、`followedByUnit` 照旧挡住「3号楼」「1号线」。
  教训：给"从开头剥"的解析器加"中间识别"能力时，**必须先让原语法跑一遍**再兜底，
  否则 `到周三` 的「到」会被当成标题文字。
- **逾期判定**：统一调 `Format.isOverdue(due, today, nowMillis, hasExactTime)`。
  有精确时刻 → `due <= now`；**只写日期 → 过了那天才算**（不能拿默认 09:00 兜底去判逾期）。
  分组 `Format.groupLabel` 内部也调它，两者口径必须一致；`overduePin` 只控制列表排序。
  `Format.groupLabel(due, today, nowMillis)` 三参版：今天且已过点 → "已逾期"。
  `TodoList` 内有 20s ticker 驱动 `now`，否则界面不会自己从"今天"翻成"已逾期"
- **通则**：随时间推移而变化的 UI 状态必须有时间源驱动重组；「分组」与「配色」必须用同一个 `now`
- **解析正则的通则**：形如"前缀 + 数字"的语法，前缀必须做进**同一个**正则。
  否则"短匹配的兜底规则"（如裸时段词 `periodDefaults`）会先吃掉前缀，把数字孤立成标题文本
  （教训：`晚上11:50` 曾被解析成 18:00 + 标题 `11:50测试一`）
- ★ **`painterResource` 只能喂 `<vector>` 或位图**：`<adaptive-icon>` / `<layer-list>` / `<shape>` /
  `<selector>` 全部会在**组合阶段**抛 `IllegalArgumentException`（源码 `PainterResources.android.kt:93-96`
  校验根节点名 == "vector"），**编译期不报错**，只有点到那个页面才崩。
  → 这是 0.3「关于页闪退」的根因。关于页现在用 `drawable-nodpi/ic_logo.png`（位图，合法）。
- ★ **图标必须原图直出，不许"为了适配 adaptive icon"去拆图**（2026-10-22 踩过）：
  母图 `icon1_1024_transparent.png` 是**满幅构图**（alpha bbox 946×935，占画布 92%），
  而 adaptive icon 只显示中间 72/108 = **66.7%**，两者根本冲突。
  当时把图拆成「纯色底 #6650A3 + 白勾」，结果**波浪花瓣整圈消失**，被 zuige 直接指出。
  → 现在：删掉 `mipmap-anydpi-v26/ic_launcher.xml`，改用
  `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.png`（48/72/96/144/192）整幅 1:1 缩放。
  **判据：图形已在 92% 内表达完整 → legacy mipmap；单色符号 + 纯色底 → 才用 adaptive icon。**
  代价：launcher 对 legacy icon 会等比 inset 约 0.7，但那是整体缩放、不改形状。

- ★ **绘制型页面（背景动效 / 滚动联动）一律延迟读取**：一个值如果每帧变、且只用于算
  alpha / translation / 画笔颜色，就不要在组合阶段读它。传 `State<T>` 进 `graphicsLayer{}` /
  `Canvas { }` 的 lambda 里 `.value`，只失效图层不失效组合。反例：AboutScreen 曾因
  `rememberAboutAnimationTime(): Float` + `by derivedStateOf` 每帧整页重组
- ★ **`Surface` / `Box` / `Scaffold` 的 content 不带布局方向**：连续写多个子节点是**层叠**
  （内部就是 `Box(propagateMinConstraints=true)`，material3 `Surface.kt` 四个重载都一样）。
  要纵向排列必须自己套 `Column`。`Card` / `Column` / `Row` 才带作用域。
  → 这是「开发者卡片展开后重叠」的根因（抄 HyperMeloLock 时把 `Card` 换成了 `Surface`）
- ★ **`animateContentSize` 不裁子内容**：只动画父节点尺寸，收敛期间子项已按最终高度画出来，
  看起来是"内容先蹦出来、容器再追上去"。要干净的展开/收起用 `AnimatedVisibility` +
  `expandVertically` / `shrinkVertically`（内部 `SizeTransform` 默认裁剪）
- ★ **ripple 的形状靠 `clip(shape)` 决定，不能靠 `ripple(radius=)`**：有界 ripple 以落点为圆心，
  点在角落时会被矩形边界切出直边。CheckCircle 曾因此出现"套在圆外面的灰色方块"
- **Compose 的 overscroll 只在能滚时才派发**（`Scrollable.kt:839-840`
  `canScrollForward || canScrollBackward`）：内容不满一屏的列表没有拉伸回弹，这是既定行为不是 bug

## 发布流程（2026-10-22 实测可用）
- 仓库 `zuige66/todo`，public，默认分支 **main**；remote 是 **SSH**
  （`git@github.com:zuige66/todo.git`）→ `git push origin main` 直接可用，**不需要 PAT**
- 发 Release：
  1. `POST https://api.github.com/repos/zuige66/todo/releases`
     body: `{tag_name, target_commitish:"main", name, body, draft:false, prerelease:false}`
  2. `POST https://uploads.github.com/repos/zuige66/todo/releases/<id>/assets?name=Todo-vX.apk`
     `Content-Type: application/vnd.android.package-archive`，`--data-binary @apk`
  - Windows curl 必须加 `--ssl-no-revoke`，否则 schannel 报错
  - API 用 `Authorization: Bearer <PAT>`（PAT 见用户级记忆，别写进仓库）
- `.gitignore` 忽略了 `*.apk` / `build/` / `dist` 之外的东西 → **APK 只能作为 release asset 分发**，
  不会被提交进仓库
- README 用 `docs/icon.png`（512px，从 `drawable-nodpi/ic_logo.png` 复制）做居中标

## v0.4 起的结构（2026-10-22）

- **四导航项**：Todo / 归档 / 设置 / 关于。关于页是 tab（`AboutScreen()` 无参），
  不再是覆盖层；设置页**不再有**关于入口。
- **归档唯一入口** `MainViewModel.requestArchive(id, animate)`：
  - `pendingArchive: StateFlow<Set<Long>>` 是勾选态的唯一真相源（`TodoRow` 不再自己 remember）
  - 650ms 计时在 `viewModelScope`，UI 销毁不影响 → 快速滑动/切页不丢归档
  - `animate=false` 给右滑用（行已滑出，立即写库）
  - ★ 撤销语义：`unarchive` 会清 pending，计时协程必须检查 `id !in pendingArchive` 才继续
- **右滑归档**：阈值 96dp、`detectHorizontalDragGestures`、底层 `secondaryContainer` 提示层，
  与圆圈共用 `requestArchive` + 同一个「撤销」条。
- **卡片色全部走 `ui/theme/Tokens.kt`**：
  `cardBackground` / `cardDragging` / `cardPlaceholder` / `fieldSurface` / `fieldBorder` /
  `placeholderText` / `actionContainer`+`actionContent` / `actionDisabled*`。
  **禁止再写 `surfaceContainerHigh|Highest` 当卡片底** —— 那是中性灰，和 11 套配色对不上。
  明暗判断用 `surface.luminance() > 0.5`。
- **通知标题 = 一个模板**：`SettingsRepo.NOTIF_REMINDER_TOKEN = "{提醒}"`，
  存储键 `notif_title_template`，`composeTitle` 一次替换后 `trim()` 再 `joinSpace`。
  - 迁移只在首次读时发生：`lead + "{提醒}" + tail`；`notif_due_word` 保留作 DUE 状态语
  - 设置页只有一个保存按钮（draft + dirty + Toast），预览用显式 `style` 重载喂 draft
  - ★ 不要退回"多键各读一次"的写法：那是"框里有、通知里没有"这类不一致的温床
- **拖拽排序**：`LaunchedEffect(index, dragging)`，拖拽中 `baseY.snapTo`（位移只给 dragDelta），
  非拖拽才 spring；`onDragCancel` 回滚顺序；`onDragEnd` 只在顺序变化时提交。
- **检查更新**：`update/UpdateService`（GitHub Releases API，6s 超时，404=未发布）+
  `update/Version`（纯函数语义化比较）+ `UpdateDownloadService`（前台服务 dataSync）+
  `ApkInstaller` + `FileProvider`（`${applicationId}.fileprovider`，只暴露 `filesDir/update/`）。
  版本号读 `PackageManager`（**没开 buildConfig**，别改成 BuildConfig）。
  权限：INTERNET / ACCESS_NETWORK_STATE / FOREGROUND_SERVICE(_DATA_SYNC) / REQUEST_INSTALL_PACKAGES。
  关于页文案已从"完全离线、不申请网络权限"改成"只访问 GitHub 读取版本号"。

## v0.4.1 补充（2026-10-22）

- **拖拽排序动效的三条红线**：
  1. 非拖拽行归位用 `tween(160, FastOutSlowInEasing)`，**不要用 spring**（dampingRatio<1 会过冲回弹，看着就是抖）；
  2. `dragDelta` 必须 `coerceIn(-current*rowH, (lastIndex-current)*rowH)`，否则拖到边界会画出容器、松手跳回；
  3. 拖拽状态色/阴影的变化要过渡（`animateFloatAsState`），硬切会像闪。
- **文案池**：`Quotes.pool(context)` 优先读 `SettingsRepo.customQuotes`（`\n` 分隔，**不用 StringSet**，
  会丢顺序）；`forTodo(context, id)` / `random(context)` 都必须带 Context。
  清空保存 = 删键 = 恢复默认。
- **沉浸式顶栏**：`enterAlwaysScrollBehavior` 默认开（设置→外观可关），
  ★ 它本身是 @Composable，**不能塞进 `remember {}` 的 lambda**；两个 behavior 先构造再 if 选。
  切 tab 要 `topBarState.heightOffset = 0f` 复位。
  ★★ **v0.4.2 起已废弃上面这条**：`enterAlwaysScrollBehavior` 会让 contentPadding 随高度变化，
  用户感知是"滑动变快"。现在用 `TopBarCollapse`：topBar slot 固定高度 Box + `graphicsLayer` 平移，
  `NestedScrollConnection.onPostScroll` 读 `consumed.y` 驱动、**返回 Offset.Zero 不消费**。
- **多行 TextField 的滚动会穿透**：内部 verticalScroll 到底后剩余量传给外层 LazyColumn。
  要隔离就给 TextField 挂 `nestedScroll`，在 `onPostScroll` 里全量消费 `available`。
- **拖拽行的基准值必须能 snap**：`animateFloatAsState` 是"慢慢追"，松手切分支会跳；
  用 `Animatable` + 拖拽中 `snapTo`。spring 一律临界阻尼（`DampingRatioNoBouncy`），
  欠阻尼的过冲就是用户说的"抖"。
- ★★ **高频输入的 state 必须同步写**（右滑 `dragX`、拖动 `dragDelta`）：
  用 `mutableFloatStateOf` 直接读写。`Animatable.snapTo` 是 suspend，投递协程有延迟 ——
  快滑时会丢位移、松手会读到旧值（"必须慢慢滑才生效"就是这么来的）。
  回弹动画用 `animate(...)` 逐帧写回，并用 `Job` 让下次 onDragStart 能 cancel。
- ★ **overlay 型顶栏不能用 Scaffold 的 topBar 槽位**：那样 content 被压到顶栏下方，
  顶栏一收就走出一条空带（内容滚不进去）。正确做法：顶栏叠在 Scaffold 外的 Box 里，
  content 只吃状态栏 padding，各列表自己 `contentPadding.top = 顶栏高度`。
- ★★ **nestedScroll 的 connection 挂在滚动容器自己身上**（LazyColumn 的 modifier），
  不要挂在 Scaffold / 外层 Box 上 —— 实测拿不到派发（`consumed` 恒为 0，顶栏不动）。
  驱动用 `onPreScroll`（滚动前必调用）+ 返回 `Offset.Zero` 不消费。
  ★ **这条也已推翻（2026-10-22 18:30）**：onPreScroll 版真机上顶栏依旧不动。
  **正确做法：把 LazyListState 提升到外层，用 `snapshotFlow { firstVisibleItemIndex /
  firstVisibleItemScrollOffset }` 直接驱动顶栏 translationY**（0..-64dp，与滚动 1:1）。
  这是纯 state，必然拿得到。
- ★★ **滑动类交互的触发条件是「距离 OR 速度」**：只判距离对快滑不友好（抬手早、样本少）。
  右滑归档 = `dragX >= 96dp || velocity.x >= 700 px/s`（`VelocityTracker`）。
  ★ **这条已被推翻（2026-10-22 18:25）**：速度没有方向绑定，斜滑列表时横向分量
  也能过阈值 → "向左下滑也归档"。**归档这类不可逆操作只能用方向明确的累计位移判定**：
  独立 `totalDx`（不 clamp）+ `totalDx >= 96dp`，向左累加为负永不触发。



---

## 沉浸式顶栏（v0.5.1 重写，2026-10-22）

**症状**：下滑后标题停在状态栏上、和信号/电量图标叠在一起；内容不像是滚到状态栏下面；
关于页顶栏永远收不起来。

**三个独立根因，缺一个都修不好**：

1. **收起上限算小了**。顶栏总高 = `statusBar + 64dp`，但 `maxCollapsePx` 只取了 64dp。
   标题在状态栏**下方**，只滑 64dp 时它正好停进状态栏那一截 → 看着就是"卡住了"。
   → 必须滑满 `statusBar + 64dp`，整条彻底离场。
2. **状态栏被加了两遍 / 或一遍都没加**。原来外层 Box 吃 `Scaffold.contentWindowInsets`
   给的状态栏 padding，而各列表的 `contentPadding.top` 只写了 64+8 —— 两个口径。
   一旦 insets 派发时机变化（切 tab、手势导航切换），两边就错开。
   → 现在 `contentWindowInsets = WindowInsets(0,0,0,0)`，状态栏只有一个口径：
   外层 `statusBarDp` 显式传给 bar 和四个列表的 `contentPadding.top`。
3. **关于页把自己的 scroll state 遮掉了**。`AboutScreen(listState: LazyListState? = null)`
   函数体里又写了 `val listState = rememberLazyListState()` —— 参数被同名局部变量遮住，
   外层顶栏监听的 `aboutListState` 从来没被滚动过，于是关于页顶栏永远不动。
   → 改成 `val state = listState ?: rememberLazyListState()`，全页用 `state`。

**附加**：`TopAppBar` 传 `windowInsets = WindowInsets(0,0,0,0)`，状态栏那一截自己用 `Spacer` 让；
标题在滑到状态栏之前按 `56dp` 距离淡出（只位移不淡出，中途会看到标题压着图标）。

**判据**：顶栏收起后，标题必须**完全不可见**，且列表内容能贴到屏幕最顶（状态栏图标压着内容）。

### 0.5.2 补充：状态栏那一截必须有底色

0.5.1 把 `windowInsets` 从 TopAppBar 拿走、改成内部 `Spacer(statusBarDP)` 让位，
但**忘了给这一截画底色**：TopAppBar 自己的 surface 只有 64dp 主体，
状态栏高度那条带就是透明的 —— 表现是"上面一片空白，能看到底下的列表在滚"。

修法：底色画在**滑动的那个外层容器**上（`.background(surfaceContainer)`），
它覆盖 `statusBar + 64dp` 整条；淡出只作用于标题 `Text`，不能作用于容器
（作用于容器会连底色一起淡掉，同样露出内容）。

### 0.5.3：标题不能单独淡出

0.5.2 的做法是"标题按 56dp 淡出、整条按 statusBar+64 滑出"。问题出在两个距离**不相等**：

```
标题在 statusBar+32 处 → 滑 60dp 就退出屏幕
整条要滑 statusBar+64 ≈ 104dp 才完全离场
```

于是 60dp < 位移 < 104dp 之间存在一个**明确的坏状态**：文字已经没了，屏幕上还留一条空底色。
用户报的"上导航栏还在、只是文字消失了"就是这一段。

结论：**标题和底色必须同时离场**，不要给标题单独做淡出。位移 1:1（列表滚多少、顶栏就走多少），
到 statusBar+64 时整条离开屏幕，内容顶到状态栏下面。
中间那段"细条正在滑走"是正常的离场过程，不是卡住。

### 0.5.4：四轮修不好的真因 —— graphicsLayer 的修饰符顺序

顶栏"纹丝不动"的真正原因不是几何、不是淡出，是 **Compose 修饰符链的包含关系**：

```
修饰符链：左 = 外层，右 = 内层；graphicsLayer 只包住它【右边】的绘制。
```

v0.5.2/0.5.3 的写法：

```kotlin
.background(surfaceContainer)   // 画在层外 → translationY 管不到
.clipToBounds()
.graphicsLayer { translationY = ... }   // 层只包住里面的 TopAppBar
```

于是底色永远不动，只有 TopAppBar 在动/淡出 —— "文字没了栏还在"（0.5.2）、
"栏完全不动"（0.5.3）全是这一个原因。v0.5.0 能动是因为 graphicsLayer 直接挂在
TopAppBar 的 modifier 上、链里没有底色。

正确写法：

```kotlin
.graphicsLayer { translationY = ... }   // 先建层
.background(surfaceContainer)           // 底色画进层内 → 跟着动
.clipToBounds()
```

**判据（写代码前先想）**：要让"底色 + 内容"作为一个整体做绘制期变换，
graphicsLayer 必须是链条上最靠左的那个绘制类修饰符。

## 设置页结构 / 列表手势 / 小组件名（v0.9.1）

### 小组件在桌面搜不到「Todo」—— label 是唯一被搜索的字段
系统「小组件」列表的搜索**只匹配组件自己的 label**（`<receiver android:label>`），
完全不看 `app_name`。所以 `widget_label=待办` 时，搜「Todo」永远空。

★ 但 `widget_label` 同时被**桌面组件自己的头部标题**引用（`widget_todo_list.xml` /
`widget_preview_layout.xml` 里的 TextView），改长会把桌面上的标题一起改掉。
已拆成两个字符串，**不能混用**：

| 字符串 | 用途 |
|---|---|
| `widget_label` = 「Todo 待办」 | 只给系统（选择器显示名 + 搜索） |
| `widget_header` = 「待办」 | 只给两个布局的头部标题 |

以后新增 layout 里的小组件标题一律用 `@string/widget_header`。

### 列表手势是开关，不是硬编码
三个动作（`SettingsRepo` 三个键，默认**全开**）：右滑归档 `gesture_swipe_archive`、
长按编辑 `gesture_long_press_edit`、双击编辑 `gesture_double_tap_edit`。
- 关掉的手势**连 `pointerInput` 都不挂**（`if (checked || !gestures.swipeArchive) return@pointerInput`），
  不参与事件分发，不是"接住了再判断"。
- `Gestures` 在 `TodoList` 里用 `rememberGestures()` 读**一次**往下传，不在每行各读一遍 SP。
  切到设置页改完再回来，`TAB_TODO` 那个分支的 group 会被销毁重建 → 自动重读。
- ★ **双击不要另起 `detectTapGestures(onDoubleTap=…)`**：它和行上的 `combinedClickable`
  是两个检测器，会抢同一次抬手，实测可能两边都不触发。现在的做法是在 `onClick` 里
  数时间差（阈值取 `LocalViewConfiguration.doubleTapTimeoutMillis`），只有一个检测器，
  涟漪反馈也还在。
- 长按关掉时给 `combinedClickable(onLongClick = null)`，而不是在回调里 return。

### 档位选择统一用下拉
- 单选：`SettingsDropdown(label, options, selectedValue, onSelect, valueText = null)`，
  `valueText` 默认取 options 里选中项的 label。
- 多选：`SettingsMultiDropdown(label, options, selected, onToggle)`，**点选不关菜单**
  （多选要连着勾，每点一次就收起来会逼用户反复展开）。
- 原来的 `ChoiceItem` / `BucketChips` / `SettingsFilterChip` 三个 chip 控件已删除。
  它们的问题是 3~4 个选项要铺两行，而档位"设一次就不动"，下拉一行更省空间。

### 分节顺序（8 节）
1 提醒·可靠性（带状态徽章，与 2 共用同一节标题）→ 2 提醒·行为 → 3 通知 → 4 外观 →
5 操作（三个手势 + 沉浸式顶栏）→ 6 小组件（含「添加小组件到桌面」指引）→
7 列表与待办 → 8 测试。

为什么沉浸式顶栏在「操作」而不是「外观」：它和手势是同一类东西（"我怎么操作这个列表"），
不是配色/字号那种外观。

## 弹层键盘 / 设置卡片间距 / 小组件字号（0.9.1 补丁，不升版本）

### ★ 编辑弹层：不要用 ModalBottomSheet（v0.9.1 补丁，第三次才修对）

**结论：`ModalBottomSheet` 在这台真机上不可用于带输入框的弹层，已整体换成应用内覆盖层。**

踩过的三个坑，按时间顺序：

1. 第一版：`rememberDatePickerState` 那套没问题，弹层用默认 `ModalBottomSheet`。
   现象 = 键盘弹起后只剩「圆角把手 + 半行标题」，输入框完全被压在键盘下面。
2. 第二版：换成 `skipPartiallyExpanded = true` 全展开。**没用**。
   因为 `skipPartiallyExpanded` 只影响"停半屏还是全屏"，不影响键盘避让。
3. 第三版：把 `contentWindowInsets` 从默认的 `safeDrawing`（含 ime）改成
   `navigationBars`，避免 ime 被扣两遍。**还是没用**：换了输入法之后依旧只有一条缝。

**根因**：`ModalBottomSheet` 的"弹层"是一个**独立的 Dialog 窗口**，而 M3 给这个窗口
设的是 SDK 30+ 的 `SOFT_INPUT_ADJUST_NOTHING`（见 `ModalBottomSheet.android.kt`，
`window.setSoftInputMode(...)`）。窗口自己不随键盘缩小，只能靠 ime insets 把 sheet 顶上去。
这条链路的 insets 上报**依赖输入法**，各家键盘（尤其带 AI 工具栏的国产输入法）不一致，
所以同一个包在两种输入法下表现完全不同 —— 这也是为什么我截图看着好了、用户实测还是被挡。

**最终方案**：`AddSheet` 不再是 Dialog，改成应用内覆盖层 ——
`Box(fillMaxSize)` 里放「遮罩 + `Box(imePadding)` 底部对齐的面板 + `verticalScroll` 的内容」。
它和列表在**同一个窗口**，键盘避让走主窗口的 ime insets（设置页那几个输入框一直是正常的，
证明这条路可靠）。配套两点：
- `AddSheet` 的调用点必须挪进 `TodoAppContent` 的根 `Box` 内（和 Scaffold 同级），
  否则覆盖层不会叠在内容上面；
- 覆盖层不是独立窗口，返回键要自己接 `BackHandler { onDismiss() }`。

**以后不要再往 AddSheet 里塞 ModalBottomSheet**，除非换成"整页导航"。

### （历史）编辑弹层被键盘挡住：sheet 不能停在半展开
`ModalBottomSheet` 默认 `skipPartiallyExpanded = false`，打开先停在 **half-expanded**，
高度按**整块窗口**的一半算。而 M3 的弹层窗口在 SDK 30+ 用的是
`SOFT_INPUT_ADJUST_NOTHING`（窗口自身不随键盘缩小），键盘弹起时它只在外层
`Box(fillMaxSize().imePadding())` 上把 sheet 整体顶上去 —— 半屏高度仍按老窗口算，
结果 sheet 只剩键盘上方一条窄缝，输入框正好被压在键盘边缘（真机=盲打）。

★ 修法：`rememberModalBottomSheetState(skipPartiallyExpanded = true)`。
全展开后 sheet 高度直接取「窗口高度 − 键盘」，输入框必在键盘之上。

★ 内容里**不要再写 `.imePadding()`**：ModalBottomSheet 最外层已经有
`Box(fillMaxSize().imePadding())`，再叠一次等于给滚动内容多塞一段键盘高度的空白，
提交按钮要滚半天才看得见。

### 设置页卡片之间必须有间距
设置页的 `LazyColumn` 原来**没有 `verticalArrangement`**（默认 0 间距）。
同一节标题下有多张卡时（「提醒·可靠性」+「提醒·行为」共用同一个带徽章的标题），
两张卡是边贴边的，真机上看着像一张卡被拦腰切开。
现在 `verticalArrangement = Arrangement.spacedBy(10.dp)`；
`SectionHeader` 的 padding 同步从 `top=20/bottom=6` 调到 `top=10/bottom=2`，
保证节与节之间的视觉留白仍是 20dp。

### 小组件字号 +2sp
真机反馈偏小。四个行布局与头部一起放大：

| 元素 | 原来 | 现在 |
|---|---|---|
| 行标题 | 13sp | 15sp |
| 行日期 | 10sp | 11sp |
| 行高（布局根） | 36dp | 40dp |
| 头部标题 | 13sp | 14sp |
| 头部条数 | 11sp | 12sp |
| 空状态 | 13sp | 14sp |
| 头部行高 | 26dp | 28dp |

★ 三张行布局（`widget_item_todo` / `_below` / `_nodate`）的字号**必须一起改**，
它们按设置切换、尺寸差一点就会看到"换个日期位置字号就变了"。
★ 行高是 `layout_weight` 的下限值（4 行平分 `widget_rows`），调大只是抬下限；
「日期在下方」那张两行文字 15+11sp ≈ 34dp，40dp 才不顶。

## 提醒节只剩一张卡（v0.9.1 补丁）

原来「提醒 · 可靠性」和「提醒 · 行为」是**两张** `SettingsCard`，但共用同一个带状态徽章的
节标题。列表又是 `spacedBy(10dp)` 之前的状态，两张卡边贴边 —— 真机上看着就像一张卡被拦腰切开。

现在 `ReliabilitySection` 多了一个 `behavior: (@Composable ColumnScope.() -> Unit)?` 参数：
可靠性的行（允许通知 / 精确闹钟 / 电池优化 / 自启动 / 后台常驻）排在前面，中间一条
`ItemDivider()`，接行为的开关（开启提醒 / 到点 / 提前 / 逾期 / 间隔 / 默认时刻）——
**一张卡里到底**。

设置页因此从 8 节变 7 节：
1 提醒 → 2 通知 → 3 外观 → 4 操作 → 5 小组件 → 6 列表与待办 → 7 测试。

## 覆盖层必须用 Surface，不能用 Box + background（v0.9.1 补丁）

把 `AddSheet` 从 `ModalBottomSheet` 换成应用内覆盖层之后，换了个深色配色，
标题「添加待办」变成**黑字**、几乎看不见。

原因：`LocalContentColor` 的**默认值是 `Color.Black`**。只有 `Surface` / `Scaffold` /
`ModalBottomSheet`（内部也是 Surface）这类容器会把它设成 `contentColorFor(containerColor)`。
覆盖层当时只写了 `Box(...).background(colorScheme.surface)` —— 底色对了，但没人设内容色，
于是所有没显式指定 color 的 `Text` 都拿到默认的黑色。

修法：面板改成 `Surface(shape = 圆角, color = surface, contentColor = onSurface)`。
★ 以后写任何"自绘背景的容器"，只要里面有 `Text`，要么用 `Surface`，要么显式
`CompositionLocalProvider(LocalContentColor provides …)`。只给 `background()` 不够。

## 小组件「事件优先」开关（v0.9.1 补丁）

默认 **开**（zuige：默认事件覆盖日期）。

它只解决一件事：**日期在右侧时，谁先占宽度**。实现是第四张行布局
`widget_item_todo_event.xml`，和 `widget_item_todo.xml` 只差两个属性：

| | 标题 | 日期 |
|---|---|---|
| 日期优先（`widget_item_todo`） | `0dp + weight=1` → 吃剩下的，很早就省略 | `wrap_content` → **永远完整** |
| 事件优先（`widget_item_todo_event`） | `wrap_content` → **先拿到完整宽度** | `0dp + weight=1` → 吃剩下的，超出省略 |

因为 `LinearLayout` 先测量非 weight 子项，所以"谁是 `wrap_content`"就等于"谁优先"。
长标题在日期优先下变「通知测…」，在事件优先下标题完整、日期变「10月…」。

★ 只在 `widgetDateBelow == false` 时露出这个开关，代码里也是这个分支才选新布局：
  日期换到标题下方之后，标题本来就独占整行，没有"谁让谁"的问题。
★ 代价：事件优先下标题是 `wrap_content`，极长时会被父容器裁掉（LinearLayout 不压缩非
  weight 子项）。实际标题极少长到那个程度，比"永远提前省略"可接受。
★ 新增设置键 `widget_event_priority`（默认 true），加在「小组件 → 日期位置」下面。

## 小组件：圆角底板 + previewLayout 不能用 include（0.9.2）

### 澎湃 3 上组件是直角方块
根布局背景原来是 `@color/widget_bg` —— **纯色 drawable 没有圆角**，铺到桌面上就是一个方块。

★ **系统不会帮自定义背景裁圆角**。Android 12 只是提供了
`@android:dimen/system_app_widget_background_radius` 这个"建议半径"给开发者用，
不会自动套到你的 background 上。所以必须自己画 `<shape>`。

现在：`@drawable/widget_bg_rounded`，半径走 `@dimen/widget_corner_radius`
- `values/dimens.xml` = 20dp（Android 11 及以下）
- `values-v31/dimens.xml` = `@android:dimen/system_app_widget_background_radius`
  （12+ 直接跟随当前桌面的风格值，比自己写死更贴近系统组件）

★ 同时给根布局加 `android:clipToOutline="true"`：圆角背景**不会**自动裁子视图，
行按下态的深色块会从圆角外面露出来。

### previewLayout 里的 `<include>` 会让某些桌面把组件整个丢掉
`widget_preview_layout.xml` 原来用 4 个 `<include layout="@layout/widget_preview_row"/>`。
`previewLayout` 最终也走 **RemoteViews** 那套解析，而 RemoteViews 支持的标签是**白名单**的，
`<include>` 不在其中。部分 Launcher（OPPO ColorOS 反馈的"找不到组件"）解析失败后
不会报错，直接把整个组件从列表里跳过。

现在把 4 行**内联展开**（`widget_preview_row.xml` 已删除）。顺带修了两处：
- 预览高度原来溢出：4×36 + 头部 26 + padding 20 = 190dp > 160dp，第 4 行一直被裁。
  现在 4×30 + 头部约 20 + padding 16 ≈ 156dp。
- 预览的行结构对齐 `widget_item_todo_event`（标题 wrap + 日期吃剩余），和真实观感一致。

★ 以后改 previewLayout：**只用 LinearLayout / FrameLayout / RelativeLayout / GridLayout /
  TextView / ImageView / Button / ProgressBar 这些 RemoteViews 白名单标签**，
  不要 include、不要 ConstraintLayout、不要 ViewStub。

### OPPO 找不到组件——排查顺序
1. 先确认装的版本：`0.8.0/0.8.1` 的 `meta-data android:name` 写成了 action 名，
   系统扫描时**静默跳过**（61 个组件里永远没有我们），必须 ≥ 0.8.2。
2. 组件 label 现在是「Todo 待办」，ColorOS 组件列表的搜索框搜 "Todo" 或 "待办" 都能命中。
3. 装完新版本后长按桌面 → 组件，若还没有，重启一次桌面（ColorOS 会缓存组件列表）。
4. 以上都做了还没有，再怀疑 `previewLayout` 解析（见上一节，0.9.2 已规避）。

## 0.9.3：自检发现的四个问题

这一版不是用户报的，是把数据层 / 提醒链路 / 归档页过了一遍之后找出来的。

### ① 归档后通知不消失（`cancelReminder` 是死代码）
`NotificationHelper.cancelReminder()` 定义了，**全项目零调用**。后果是一条链：

1. 列表点圆圈完成 → 待办进归档，通知还挂在状态栏；
2. 点通知打开 App，`OPEN_TODO` 带 `todoId` 去找，行已经不在了；
3. 点通知上的「完成」→ `NotificationActionReceiver` 里 `getById(todoId) ?: return@launch`
   静默退出，**按了没反应**。

**不能直接取消**：通知上带着「撤回」，立刻撤等于砍掉反悔入口，而应用内撤销条只活 3 秒。
所以 `NotificationDismissScheduler` 延迟 **10 秒**再撤，期间任何一次 `unarchive`
（撤销条 / 通知栏「撤回」）都会 `cancelScheduled` 掉这个定时器。

★ 调度器是**进程内单例**，不是 ViewModel 的 scope —— 撤回也可能从通知栏发起，
   不经过 ViewModel，两边必须能撤掉同一个任务。
★ 进程被杀 → 延时没了 → 通知留着（回到修复前的行为）。刻意接受的降级：
   为一个通知去排 AlarmManager 不值，而这个场景要求"前台完成后 10 秒内杀进程"。
★ 删除走 `delete()`，**立刻**撤，不等 10 秒：删除没有反悔窗口（回收站是另一回事）。

### ② 归档页「删除」→ 回收站（DB v5）
> ⚠️ **这条已撤销（同版本内）**：zuige 决定不要回收站，"删除就多一个确认"就够了。
> 现在归档页点「删除」弹确认对话框，确认后**硬删除**（`dao.deleteById`）。
> **DB 版本号仍是 v5、`deletedAt` 列保留但不使用** —— 已经装过那一版 0.9.3 的设备
> 如果能看到 v4 schema 会直接抛降级异常，不值得为删一列冒这个险。代码里没有任何地方
> 写它，恒为 null。
> 下面是当初的设计记录，留作参考。

原来 `onDelete` 直接 `dao.deleteById(id)`，没有确认也没有撤销。而归档页每一条都是
"已经完成过"的待办，用户容易把"清掉一条旧的"和"彻底销毁"混为一谈。

- `Todo` 加 `deletedAt: Long?`，`MIGRATION_4_5` 只加一列（可空，旧行天然为 null）。
- ★ **所有列表查询都要补 `deletedAt IS NULL`**：漏一处，回收站里的待办会重新出现在
  活跃列表里，而且是 Flow 实时刷新 —— 表现为"删了又自己回来了"，看着像数据损坏。
- 归档页第一项是「回收站 · N 条」，**不是"归档为空才浮出来的按钮"**：
  归档清空后入口必须还在，而"刚清空归档想反悔"恰恰最需要它。
- 回收站里「彻底删除」要二次确认；「恢复」不确认（可逆的操作加确认只是拖慢）。
- 30 天过期清理放在 `OnelineApp.onCreate`（冷启动跑一次，不需要精确时点）。

### ③ 时间段的开始日没有任何提醒
`startAtMillis` 只进库，`scheduleBlocking(...)` 只吃 `dueAtMillis`。于是
「25号到1号出差」在 25 号当天**一条通知都不会有**，第一次提醒是 1 号之前的提前提醒。

`ReminderEngine.computeReminders` 加 `startAtMillis`，开始日当天发一条「今天开始」，
时刻跟随用户的默认时刻（`SettingsRepo.defaultMinuteOfDay`）。

- 只在**真正的区间**才发：start 为 null、开始日==结束日、开始日已过去（含"今天但时刻已过"）
  都不发。否则会跟 DUE 撞在同一天响两次，或者刚建完就弹一条。
- `Reminder.Kind` 追加 `START`（**只能往后追加**：ordinal 就是 PendingIntent 的
  requestCode 尾数，插中间会让已有闹钟整体错位、cancel 不掉）。
- `cancelForId` 改成遍历 `Kind.values()`：原来写死 `0..3`，加新 kind 后旧闹钟取消不掉，
  改一条待办的时间会同时收到新旧两条提醒。
- START 归 `remindAtDue`（"到点提醒"）这一档，不归 `remindInAdvance`。

### ④ 两笔旧账
- **预览卡片和提交结果不一致**：`PreviewCard` 自己又算了一遍 start/date，没做
  `effectiveParses()` 里那两步归一化。症状：把结束日改到开始日之前 → 卡片显示
  「10月25日 – 10月23日」，存进去却是 10/23–25；把结束日选成「无日期」→ 卡片还挂着
  「开始 10月25日」，保存后开始日却没了。现在两边共用 `normalizeRange()`。
- **单日 → 时间段没有 UI 入口**：以前只有句子写成「X号到Y号」才可能有开始日。
  单日 chip 后面补一个「+ 时间段」，点开同一个区间选择器（初始只带结束日）。
  ★ 里面有个语义分叉必须分开：区间选择器里**只点了起点**时，
  - 原本是单日（`start == null`）→ 这一点是**起点**，终点沿用原来那天，成区间；
  - 原本是区间 → 用户其实只想要一天，退回单日。
  混在一起会导致"从单日点一下反而把日期改掉了"。

## 0.9.3 追加：撤掉回收站 + 修「返回两次主页面」（不升版本）

### 删除只要一次二次确认
zuige：不需要回收站，删除时多一个确认就行。改动：

- 归档页「删除」→ 弹 `AlertDialog`「删除待办？」「「xxx」将被永久删除，无法恢复。」
  → 确认后硬删除。恢复（`unarchive`）不弹确认 —— 它是可逆的，加确认只是拖慢。
- 回收站相关的一整套（`trashPane` 状态、`TrashList`、`TrashEntryRow`、`restore`/`purge`、
  `purgeExpiredTrash`、`trash` flow）全部删除。
- **`deletedAt` 列和 DB v5 保留**：已经装过上一版 0.9.3 的设备，如果代码里 schema
  退回 v4，Room 会判定为降级并抛异常。为删一个没人用的列冒这个险不值得。
  `TodoDao` 里的查询也不再带 `deletedAt IS NULL`（没有地方写它，条件恒真，去掉更干净）。

### 从小组件点进 App，返回时出现两份主页面
小组件用的是 `PendingIntent.getActivity(context, requestCode, Intent(context, MainActivity::class.java), ...)`，
**Intent 上一个 flag 都没带**。系统对 `PendingIntent.getActivity` 会补 `FLAG_ACTIVITY_NEW_TASK`，
但那只是"用哪个 task"，不决定"要不要新压一个实例"—— MainActivity 当时是默认的
`standard` 启动模式，于是每次点击都会在同一个 task 里**再压一个 MainActivity 实例**。
用户按返回，看到的是下面那个实例，也就是"还有一个主页面"，要点两次才退到桌面。

修法（两道都上，互为保险）：
1. `AndroidManifest` 里 MainActivity 改 `android:launchMode="singleTask"` —— 全系统唯一实例；
2. 所有"打开 App"的 Intent 显式带 `NEW_TASK | CLEAR_TOP | SINGLE_TOP`：
   - 小组件的 `widget_header` / `widget_empty` / 每行 `widget_row`
   - 通知的 `openAppIntent`

★ 注意 `widget_row` 的 Intent 带 `data = todo://open/<id>`，不同行是不同 Intent，
  flag 必须写在 Intent **自己**身上，不能只依赖外面统一的那个。
★ `singleTask` 生效后，已有的 `onNewIntent` 不会被调用（MainActivity 本来就没重写），
  行为不受影响；`todoId` extra 目前没有任何地方消费，属于另一个待补的缺口。
