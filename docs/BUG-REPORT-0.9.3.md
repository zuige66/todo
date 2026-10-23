# 0.9.3 复查报告（e8bafe0 → 41cf61c）

基线：`:app:testDebugUnitTest` 重跑通过（248 条），编译通过，工作区干净。
本轮为纯静态复查（adb 无设备）。

## 一、0.9.2 报告的 6 条 —— 已全部修复，逐条核对无误

| # | 问题 | 修复位置 | 核对结果 |
|---|---|---|---|
| P0-1 | 日期/时间已过点「添加」→ 弹层关、没建成、无提示 | TodoListScreen.kt:690-693 | ✅ 只有 Success 关弹层 |
| P0-2 | 只改标题就绕过「日期已过」校验 | AddSheet.kt:147/165 | ✅ dateTouched 门控，没动过的维度保留原判 |
| P1-3 | 红字读原始解析，与校验脱节 | AddSheet.kt:295-299 | ✅ 三处（预览/红字/提交）共用 `effective` |
| P1-4 | 编辑多日期只存第一条 | TodoListScreen.kt:672-678 + AddSheet.kt:632-638 | ✅ Toast + 预览卡编辑模式提前警示 |
| P2-5 | 小组件头部「4 条」是截断后数量 | TodoWidgetProvider.kt:173-177 | ✅ 先取全量再 take |
| P2-6 | 弹层内「现在」跨时刻不刷新 | AddSheet.kt:127-128 | ✅ 去掉 remember |

另外三项自检修复（归档延迟撤通知、删除二次确认、双主页面、MIGRATION_4_5 闪退）
也都核对过，写法正确，无新问题。回收站撤得干净，无残留代码。

## 二、新发现（按严重度）

### P1-1 小组件圆圈完成 → 通知永远不撤（0.9.3 只接了应用内一条路）

`TodoWidgetActionReceiver.archiveWithTick`（TodoWidgetActionReceiver.kt:61）直接调
`repo.archive(id)` 写库，**没有接** `NotificationDismissScheduler`。1896f53 把延时撤通知
只接在了 `MainViewModel.requestArchive`（应用内点圆圈/右滑）这一条路上。

三个归档入口的覆盖情况：

| 入口 | 延时撤通知 |
|---|---|
| 应用内点圆圈 / 右滑（MainViewModel.kt:175-179） | ✅ 10 秒 |
| **小组件圆圈（TodoWidgetActionReceiver.kt:61）** | ❌ 完全没有 |
| 通知栏「完成」（NotificationActionReceiver.kt:60） | 不撤（设计如此，保留「撤回」） |

两个症状：

1. **普通待办**：归档了，通知还挂着「该办了」。点通知上的「完成」→ `todo.archived`
   为 true → 静默 return，按了没反应 —— 正是 0.9.3 要修的那个症状，换了扇门进来。
2. **重复待办**（更糟）：完成 → 顺延到下一周期，通知还挂着且「完成」按钮仍然有效 →
   再点一次 → **又顺延一次，直接跳过一个周期**。

修法（二选一）：
- `archiveWithTick` 里 `archive` 成功后补一行
  `NotificationDismissScheduler.scheduleCancel(context, id)`；
- 或把 scheduleCancel 下沉进 `TodoRepository.archive()`，通知栏路径在重发 done
  通知后调 `cancelScheduled` 把自己摘出来（一处收口，以后新增入口不会再漏）。

### P1-2 时间段「今天开始」的通知文案变成「提醒」

`ReminderEngine` 给 START 认真写了 `message = "今天开始"`（ReminderEngine.kt:189），
但 `ReminderReceiver` 的 kind→文案映射（ReminderReceiver.kt:38-44）没有 START 分支，
落到 `else -> "提醒"`。用户实际看到的是「出差 提醒」——四个字里三个字是废话，
engine 里那句文案根本没到通知上。

单测 248 条全绿也抓不到：测试只覆盖了 engine（ReminderEngineTest.kt:193 断言了
message），receiver 的映射没有测试。

修法：`when` 里补 `Kind.START -> "今天开始"`。顺手建议把这个映射抽成一个纯函数
放进 engine 或单独文件，才能被单测覆盖。

### P2-1 通知栏「撤回」重复+时间段待办 → 开始日没回退，区间倒挂

`rollForward` 顺延时开始日平移了 `nextEpoch - due`（TodoRepository.kt:140），但
`undoFromNotification`（TodoRepository.kt:225-248）只把 due 回退一个周期，
`startAtMillis` 原样留着 → **start > due**。

后果：列表/小组件显示「10月27日–26日」这种倒挂文案；`isInProgress` 恒 false；
START 提醒也不再排（`startAtMillis >= dueAtMillis` 被跳过）。

触发路径是 0.9.3 新开的：解析器一句里不支持「重复+区间」（Parser.kt:567 有拦截），
以前造不出这种组合；现在编辑一条重复待办 → 预览卡「+ 时间段」加上区间 → 保存，
这条路通了。

修法：`undoFromNotification` 里 start 同步回退同样幅度：
`todo.startAtMillis?.let { it - (cur - prev) }`（cur = 回退前的 due）。

### P2-2 无日期待办设了时间，保存后时间消失（老问题，非本轮引入）

「买牛奶」→ 点「+ 时间」选 14:00（或直接输入「买牛奶 14点」）→ 预览卡挂着时间
chip，但 `dueDate == null` 时 `parsedToDue` 返回 null（TodoRepository.kt:49-56）
→ `dueAtMillis = null`，**时间静默丢失**，`hasExactTime` 还落了个 true。

修法（二选一）：提交前拦一下，预览卡提示「先选日期才能设时间」；或
`dueDate == null && dueTime != null` 时默认落到今天。

## 三、P3 / 设计取舍（不急着改）

1. **dateError 是「touched 就清空」而不是重算**（AddSheet.kt:165），和 timeError
   （:166-172 真的重算）不对称。区间选择器允许选过去日期（为进行中区间开的门），
   于是选一个已结束的日期也不再报「日期已过」；而单日选择器又是 TodayOrLater 禁过去
   的，两个选择器口径不一致。
   ⚠️ 不能简单地改成无条件 `end.isBefore(today)` —— 那会让编辑一条已逾期待办时红字
   复现、只改标题都存不下去。要重算必须挂在 dateTouched 上：
   `if (dateTouched) end != null && end.isBefore(today) else p.dateError`。
2. **应用内撤销条对重复待办无效**：点圆圈 → rollForward，撤销条不显示
   （TodoListScreen.kt:448-450 有 rollsForward 判断，这是对的）；但通知栏「撤回」
   能回退，应用内却不能。不对称，影响小。
3. **nextOverdueReminder 在 now == firstNagMs 时返回 now 本身**（ReminderEngine.kt:130），
   理论可立即连发，毫秒级精度实际撞不上。上一轮报过，仍然建议加个 `>` 兜底。

## 四、待拍板（上一轮问过，还没定）

**重复待办在小组件里点圆圈**：勾亮起 → 行消失 → 带着新日期又出现
（`archiveWithTick` 的两帧 + rollForward）。要不要重复任务不画勾、不给完成动效？

## 五、建议动手顺序

~~P1-1、P1-2 一起改（都不大，各一两行 + 测试）；P2-1 跟 P1-1 可以合成一个提交
（都涉及「完成/撤回」链路）。~~ **已修（见 DEV-NOTES「0.9.3 补丁二」）**：

- **P1-1** ✅ `scheduleCancel` 下沉进 `TodoRepository.archive()`，三入口共用；
  通知栏路径显式摘出；`unarchive` 对称补 `cancelScheduled`。
- **P1-2** ✅ 删 `Reminder.message`，`ReminderEngine.messageFor` 唯一出处 + 2 条回归测试。
- **P2-1** ✅ `undoFromNotification` 开始日同步回退。

剩余未动：P2-2（无日期 + 时间静默丢失，等产品预期）、三条 P3、待拍板的组件动效。
