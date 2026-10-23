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
