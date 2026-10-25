# Oneline 0.9.3 复查报告（第二轮，2026-10-24）

> 范围：更新链路 / 常驻与设置 / 解析器与输入面板 / 提醒投递链路 / 小组件联动。
> 方法：全量静态走查 + 单测回归核对（250 全绿）+ 真机行为对照。
> 结论先行：**没有发现会日常崩溃或数据损坏的问题**；1 条 P1 已经在真实发生（修复版发不到用户手上），
> 5 条 P2 建议在本版本或下个补丁修掉，其余为理论缺陷与一致性瑕疵。

## 修复进度（2026-10-24 当日更新）

| 项 | 状态 |
|---|---|
| P2-2 下载文件名未消毒 | ✅ 已修（`ApkInstaller.sanitizeVersion`，白名单折叠） |
| P2-3 update/ APK 只下不清 | ✅ 已修（下载成功后 prune + 启动时 `cleanupStaleApks`） |
| P2-4 点通知定位不到待办 | ✅ 已修（MainActivity 接 OPEN_TODO → 滚动 + 3 秒高亮） |
| P2-5 「+ 时间」初始值硬编码 | ✅ 已修（读 `SettingsRepo.defaultMinuteOfDay`） |
| P2-1 全角破折号时段失效 | ✅ 已修（normalize 不再转 —/–；num3Re/absYearRe 分隔符类收编 —/–） |
| P1-1 只比 versionName | ✅ 已修（比对优先 versionCode，见下「发版须知」） |
| P3-1~6 防御性小修 | ✅ 已修（coerceIn / requestCode 同构 / refresh runCatching / 过滤空标题 / guard 400→4000 / KDoc 对齐） |
| 新功能：合并为时间段 | ✅ 已做（`parser/RangeMerge.kt` + AddSheet 开关 + 11 条单测） |

装机验证：268 项单测全绿（新增 7 项：Version.buildCode×3、破折号时段/日期×4），release 2.0M，覆盖安装成功。

### 发版须知（P1-1 修完后，发布动作要多一步）

比对已改成**优先 versionCode**（整数）。所以从今往后每个 release 必须让 versionCode 可见，二选一：

- **推荐**：release 的 tag 写成 `v0.9.3+32`（`+` 后跟 versionCode）；或
- release body 里加一行 `versionCode: 32`。

两个都不写也不崩——会退回比 versionName（老 release 的兼容路径），但补丁版又会被吞。博客 `latest.json` 已带 `versionCode` 字段，无需改。

---

## 附：17MB「用户数据」调查（zuige 2026-10-24，已结案）

**现象**：APK 只有 2MB，设置里「用户数据」却显示 17MB，而待办只有几条。

**结论（实锤）**：`filesDir/update/` 里躺着一个 **9.4MB 的 `Oneline-v0.8.4.apk`**
（未开 R8 时期的老包）。用户最后一次应用内更新停在 0.8.4，之后全走外部安装，
再没有新包触发清理，这个孤儿包被"保留最新一个"的策略永久留下。

**取证过程**（release 包 + 无 root 的机器上，`/data/data` 拿不到明细）：
- `run-as` → "package not debuggable"；`du` → permission denied；`su` → not found
- `dumpsys diskstats` 的 App Data Size 2.76MB **不含 filesDir 大文件**（实测滞后），
  与 MIUI 设置显示对不上 —— 这个口径不能当真相
- `adb backup` → 需要用户在设备上确认，锁屏无法远程操作（0 字节）
- **最终方案**：`StorageDiag.logDataDir()` 启动时把 data 目录树打进 logcat
  （`adb logcat -s Oneline[Storage]`），一眼定位

**清理后实测**：dataDir 总量 **9.64MB → 0.48MB**（数据库 164KB + WorkManager 330KB + SP 0.2KB，
全部正常）。MIUI 设置里的数字有统计缓存，进「存储」页面刷新一下即可看到下降。

**清理策略演进**（`UpdateDownloadService.pruneApks`）：
- v1「保留最新一个」→ **不够**：单个孤儿包会被永久保留（就是这次的 0.8.4）
- v2（现行）：**版本旧于本机**（`Version.isNewer(current, pkgVersion)`）或
  **下载超过 7 天** → 删。下载中的包 mtime 新且版本不旧，不会误伤

---

---

## 一、新发现的 Bug

### P1-1 检查更新只比 versionName —— 修复版永远发不出去

- **位置**：`update/UpdateService.kt`（远端版本比对）
- **现象**：versionCode 31 与 32 的 versionName 都是 `0.9.3`。装了 v31 的用户点「检查更新」
  得到「已是最新」，**拿不到 v32 这个修复版**。这不是理论问题——0.9.3 的两个补丁
  （小组件完成不撤通知 / START 文案）就是这样被吞掉的。
- **根因**：比对用 versionName 字符串相等。补丁发布惯例是不动 versionName 只涨 versionCode。
- **修法**：
  1. 比对改 versionCode（整数比较），versionName 仅用于展示；
  2. GitHub 源需要把 versionCode 暴露出来——release tag 写成 `v0.9.3+32`，或 body 固定行
     `versionCode: 32`（解析时两个源都兼容）；
  3. 博客 latest.json 已有 versionCode 字段，直接用。
- **验收**：装 v31 包点检查更新，必须提示可升级到 v32。

### P2-1 全角破折号写的时段失效，日期碎片漏进标题

- **位置**：`parser/Parser.kt` `normalize()` + `stripRange()` 的 `rangeConnectors`
- **现象**：输入「10月1日—3日 出差」→ `normalize` 把 `—` 归一成 `-`，而连接词表里收的是
  `—`/`–` 原字符、`-` 故意不收（怕误切 `2026-10-24`）。结果时段没识别，标题变成
  「出差 -3日」。全角波浪「～」不受影响，只有破折号写法中招。
- **修法**：`normalize` 不再转 `—`/`–`；把这两个字符补进 `num3Re` / `absYearRe` 的
  分隔符字符类（保持「2026－10－24」这类写法仍可解析）。连接词匹配优先于日期分隔的
  优先级不变。

### P2-2 下载文件名未消毒

- **位置**：`update/ApkInstaller.kt:20` `fileName = "Oneline-v${version}.apk"`
- **现象**：`version` 直接来自远端 tag/接口，未做字符过滤。tag 里出现 `/`（如
  `v0.9.3/rc`）会让 `File(filesDir/update, fileName)` 落到子目录甚至失败；异常字符同理。
  远端一旦抽风，普通用户看到的就是「下载失败」且无法自救。
- **修法**：落盘前过滤，只保留 `[0-9A-Za-z._-]`，其余替换为 `_`，并限制总长度
  （如 64 字符）。

### P2-3 `filesDir/update/` 的 APK 只下不清

- **位置**：`update/UpdateDownloadService.kt`
- **现象**：每升级一个版本下载一个新包，旧包永远留在 `filesDir/update/`。按每版本 2MB
  算不痛，但这是无增长上线的累积项，且用户从「应用信息→存储」看到的是本应用占的空间。
- **修法**：下载成功后删除目录内其它 `.apk`（保留自己）；或安装器回调成功后删自己。

### P2-4 点通知打开 App 后定位不到对应待办

- **位置**：`reminder/NotificationHelper.kt` `openAppIntent()` vs `MainActivity`
- **现象**：通知的 contentIntent 带了 `EXTRA_TODO_ID` 和 `OPEN_TODO` action，但
  MainActivity 从未读取（无 `onNewIntent`、无 extra 消费）。多条待办同时到期时，
  点开通知只是落到首页，用户还要自己找是哪一条。
- **修法**：MainActivity 接住 intent（`singleTask` 下走 `onNewIntent` + `onCreate` 两处），
  把 todoId 传进列表页，滚动到该行并短暂高亮；拿不到 id（null/0）时维持现状。

### P2-5 「+ 时间」选择器初始值硬编码 9:00，不读用户设置的默认时刻

- **位置**：`ui/screen/AddSheet.kt:485` `initialHour = current?.hour ?: 9`
- **现象**：设置里把「默认提醒时刻」改成 21:00 后，添加待办时点「+ 时间」仍从 9:00 开始滚。
  两个"默认时刻"语义是同一个（只写日期的待办落哪个点），UI 却不共享。
- **修法**：`SettingsRepo.defaultMinuteOfDay(context).let { it / 60 to it % 60 }`。

### P3 级（理论缺陷 / 一致性，不紧急）

| # | 位置 | 问题 | 修法 |
|---|------|------|------|
| P3-1 | `data/TodoRepository.kt:71` | `defaultTime()` 的 `LocalTime.of(mo/60, mo%60)` 无边界钳制。写入路径目前只有设置页 4 个固定档位，实际不可达；但与 `ReminderEngine.startReminder` 的 `coerceIn(0,1439)` 防护风格不一致 | 一行 `coerceIn` 对齐 |
| P3-2 | `reminder/NotificationHelper.kt:297` | `requestCode = todoId % 100_000 * 10 + slot`：id 相差 10 万的两条待办，通知的打开/完成/撤回 PendingIntent 会互相覆盖 extras（点 A 的操作了 B）。需 10 万条待办才触发 | 改 `todoId * 10 + slot`，与 `ReminderScheduler` 同构 |
| P3-3 | `keepalive/KeepAlive.kt` | `refresh()` 无 try/catch，ROM 上 `startForeground` 受限时异常会冒到设置页 | 包 runCatching，失败只回滚开关状态 |
| P3-4 | `ui/MainViewModel.kt:94` | `addParsed` 只拦"全部标题为空"；理论上部分空标题会建出无名待办。实际不可达（parseAll 共享 title、标题编辑器禁空保存） | `filter { it.cleanTitle.isNotBlank() }` 后判空 |
| P3-5 | `data/TodoRepository.kt:146` | `rollForward` 的 `guard < 400` 走满后若仍在过去，会排一个过去时刻的闹钟（立即触发）。需 400+ 个周期没人管 | 提高 guard 或排前判 `fireMs < now` 则跳过 |
| P3-6 | `parser/ParsedTodo.kt` | KDoc 漂移：`@param forceDateError`（已改名 dateError）、`repeatDays` 写着 "null/0"（实为 Int 非空） | 顺手改文档 |

---

## 二、新功能建议（按性价比排序）

1. **通知「稍后提醒」** —— 通知栏加一个 action：+1 小时 / +3 小时 / 明天。
   现在到点不想处理只有「完成」（语义错）或划掉（丢提醒）。实现就是 receiver 里
   `scheduleBlocking` 换个时刻重排，成本半天，是提醒类 App 的标配。
2. **导出 / 导入备份** —— Room 数据目前唯一的出口是系统 `allowBackup`（国内 ROM 的
   自动备份基本不可用），换机 = 数据蒸发。做 JSON 导出到「下载」目录 + 从文件恢复，
   比任何新功能都更能建立信任。
3. **首页搜索** —— 标题/详情关键字过滤。待办过百后是刚需，现在完全没有入口。
4. **重复待办「今天跳过」** —— 每天提醒的待办某天就是不想做，现在只能归档（丢周期）
   或删掉。加一个 skip-once：把 due 顺延一天但不产生完成记录。
5. **系统分享接入** —— 注册 `ACTION_SEND`（text/plain）：在微信/浏览器里复制一段话，
   分享到 Oneline 直接带着文本打开添加面板。零新增 UI，Parser 现成。
6. **统计页** —— 本周/本月完成数、连续记录天数。配合「坚持」主题的文案池，
   是这个 App 气质最搭的功能。
7. **时段进度** —— 进行中的「10月21日–26日」在列表显示「第 3/6 天」，让"进行中"可感知。
8. **小组件完成动画**（历史悬挂项）—— 重复任务点圆圈，勾亮起→行消失→带新日期回来，
   中间没有动画解释"它去哪了"。可考虑闪一行「已顺延到明天」。

---

## 三、复查中确认"没问题"的重点区域

- **归档三入口**（应用内圆圈 / 右滑 / 小组件 / 通知完成撤回）：撤通知、撤闹钟、重复顺延、
  start 回退均已成对，0.9.3 的收口改动复核无误。
- **ReminderEngine 纯函数**：kind 枚举、messageFor 单一出处、START 注解日期不误伤、
  nag 间隔脏值防护，均有回归测试覆盖。
- **解析器边界**：中文数字、全角归一、`N号线` 序号防误判、时间段整段定年、greeting
  防误判（早上好/下午茶）、`8:70` / `25点` 不崩——均核实。
- **AddSheet 覆盖合并**：预览/红字/提交三处共用 `effectiveParses`，dateTouched/timeTouched
  的判定逻辑正确（改标题不会清掉日期错误）。
- **冷启动对账 + 12h 巡检**：force-stop 后的自愈链路完整，巡检幂等。

---

## 四、建议的修复顺序

1. **P1-1**（影响所有补丁发布，必修）
2. **P2-1 / P2-2 / P2-3**（解析器与更新链路，互不依赖，可一批）
3. **P2-4 / P2-5**（体验类，一批）
4. P3 随手下修
5. 新功能从「稍后提醒」和「导出备份」里挑，这两个用户价值最高
