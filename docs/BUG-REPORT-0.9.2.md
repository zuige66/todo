# 排查报告 · 0.9.2（2026-10-23）

> 范围：全量静态审查 + 单测基线 + 构建验证。
> 未做真机验证：`adb devices` 为空，手机没连上。

## 修复状态（0.9.3 已全部处理）

| 编号 | 问题 | 状态 |
|---|---|---|
| P0-1 | 日期/时间已过时点「添加」：弹层关闭、待办没建成、无提示 | ✅ 只有 Success 才关弹层 |
| P0-2 | 只改标题/详情就绕过「日期已过」校验 | ✅ `dateError` 只在真正动过日期时才清 |
| P1-3 | 红字读的是原始解析，和实际校验脱节 | ✅ 改用 `effectiveParses()` |
| P1-4 | 编辑模式下多日期只保存第一条、其余静默丢弃 | ✅ 预览卡改成警告文案 + 保存时 Toast |
| P2-5 | 小组件头部「4 条」不是活跃总数 | ✅ 先取全量再截断 |
| P2-6 | 弹层里的「现在」跨时刻不刷新 | ✅ 去掉 remember，每次重组现取 |
| 待拍板 A | 小组件里重复任务点圆圈：勾亮起又回来 | ⏸ 未改，等用户决定 |
| 待拍板 B | `now == firstNagMs` 理论上可连发 | ⏸ 经复核不改：AlarmManager 触发必然晚几毫秒，`steps` 会算成 1；改成严格大于会破坏 `overdueNag_alignsToBoundary` 的语义 |

---

## 结论

| 项 | 结果 |
|---|---|
| 单元测试 | 242 条全绿（`--rerun-tasks` 强制重跑，不是缓存） |
| 编译 | 通过，无新增警告 |
| 工作区 | 干净，HEAD = `e8bafe0` |
| 解析器 / 提醒链路 / 数据层 | **未发现新问题**，测试覆盖充分 |
| 问题集中区 | **AddSheet 提交链路**（4 处）、**小组件条数**（1 处） |

找到 **4 个确认的 bug**（2 个 P0）+ 2 个次要问题 + 2 个待拍板项。

---

## P0-1　日期/时间已过时点「添加」：弹层关闭、待办没建成、**没有任何提示**

**位置**　`ui/screen/TodoListScreen.kt:674-679`

```kotlin
when (vm.addParsed(parsedList)) {
    AddResult.Success -> showAdd = false
    AddResult.Blank -> {}                      // ← 只有这个不关
    AddResult.DateError -> showAdd = false     // ← 关掉了
    AddResult.TimeError -> showAdd = false     // ← 关掉了
}
```

**复现**（今天是周五 10/23）

1. 输入 `周一开会` —— 本周一已过，解析器给 `dateError = true`
2. 弹层里出现红字「日期已过，换个未来的日期」
3. 点「添加」→ `addParsed` 返回 `DateError` → `showAdd = false`
4. **弹层消失，列表没有新条目，也没有 Toast** —— 用户以为添加成功了

**根因**　`Blank` 分支留空说明原意是「出错不关弹层」，`DateError` / `TimeError` 两个分支写反了。

**修法**　两个分支都改成 `{}`。红字已经在弹层里说明了原因，用户可以直接改日期再提交。

---

## P0-2　只改标题或详情，就绕过「日期已过」校验，能建出已逾期的待办

**位置**　`ui/screen/AddSheet.kt:161`

```kotlin
p.copy(
    ...
    dateError = false,        // ← 无条件清掉
    timeError = time != null && end == today && time.isBefore(nowTime)
)
```

**复现**

1. 输入 `周一开会` → 红字「日期已过」
2. 点标题 chip，把「开会」改成「开会啊」（**没动日期**）
3. 红字**还在**（错误提示读的是原始 `parses`，见 P1-3）
4. 点「添加」→ `effectiveParses()` 里 `dateError` 已是 `false` → 校验通过 → 弹层关闭
5. 列表里多出一条 **本周一（已逾期）** 的待办

**根因**　`dateError = false` 的本意是「用户把日期改到未来就不该再报错」，但没有判断**用户是否真的改了日期**。只要任一 override 存在（标题、详情也算）就会走到这个 `copy`。

**修法**

```kotlin
val dateTouched = dateOverrides.containsKey(index) || startDateOverrides.containsKey(index)
p.copy(
    ...
    dateError = if (dateTouched) false else p.dateError,
    timeError = if (timeOverrides.containsKey(index))
        (time != null && end == today && time.isBefore(nowTime))
    else p.timeError
)
```

---

## P1-3　弹层红字读的是「原始解析」，不是「合并覆盖后」的结果

**位置**　`ui/screen/AddSheet.kt:275-279`

```kotlin
val err = when {
    parses.any { it.dateError } -> "日期已过，换个未来的日期"
    parses.any { it.timeError } -> "时间已过，换个未来的时间"
    else -> null
}
```

**现象**　输入 `周一开会` → 点日期 chip 改成下周一 → **红字不消失**，但此时点「添加」其实是能成功的（因为 `effectiveParses` 已经清了 error）。提示与实际校验脱节。

**修法**　`err` 改用 `effectiveParses()` 计算。注意与 P0-2 一起改 —— 只改这一处的话，P0-2 会从「能提交」变成「红字消失且能提交」，反而更隐蔽。

---

## P1-4　编辑模式下把句子改成多日期：只保存第一条，其余静默丢弃

**位置**　`ui/screen/TodoListScreen.kt:667`

```kotlin
vm.editParsed(editTarget!!.id, parsedList.first(), keepRepeat)
```

**现象**　编辑一条待办，把输入框改成 `明天 后天 开会` → 预览卡明确写「将创建 2 条待办」→ 保存后**只有明天那条**，后天那条凭空消失，没有任何提示。

**根因**　编辑是 `update` 语义（一条进、一条出），但输入框允许解析出多条，预览卡也照着多条的文案渲染。

**修法（二选一，推荐 ①）**

1. 编辑模式锁定单条：预览卡不显示「将创建 N 条」，提交前 `parsedList.size > 1` 时提示「编辑只能保存一条，多余日期请单独添加」；
2. 第一条 `update`、其余 `insert`：语义更绕（编辑一条却变成两条），且 `editParsed` 要改成批量接口。

---

## P2-5　小组件头部条数显示「4 条」，不是活跃待办总数

**位置**　`widget/TodoWidgetProvider.kt:169-175`

```kotlin
val todos = runBlocking { dao.getActiveAll().take(MAX_ROWS) }   // 已经截断到 4
rv.setTextViewText(R.id.widget_count,
    context.getString(R.string.widget_header_count, todos.size))  // 所以恒 <= 4
```

**现象**　有 7 条活跃待办时，组件头部写「4 条」。用户会以为数据丢了。

**修法**

```kotlin
val all = runBlocking { dao.getActiveAll() }
val todos = all.take(MAX_ROWS)
rv.setTextViewText(R.id.widget_count,
    context.getString(R.string.widget_header_count, all.size))
```

---

## P2-6　弹层里的「现在」是打开那一刻的，跨时刻不刷新

**位置**　`ui/screen/AddSheet.kt:125-126`

```kotlin
val nowMillis = remember { AppTime.nowMillis() }
val today = remember { AppTime.today() }
```

**现象**　弹层开着跨过 0 点（或跨过用户设的那个时刻），`timeError` 仍按打开时的「现在」判断。影响很小（弹层通常只开几十秒），但修起来只要把 `remember` 换成调用时现取。

---

## 待你拍板（不算 bug，但行为可能不符合直觉）

### A. 重复任务在小组件里点圆圈：勾亮起 → 又回来

`archiveWithTick` 对重复任务走 `archive()` → `rollForward()`，第 1 帧画勾、第 2 帧它带着新日期**又出现了**。App 列表里靠「不显示撤销条」区分这种情况，小组件没有对应反馈，用户可能以为「没点中」。

要不要在小组件里对重复任务改成「不画勾，直接跳到新日期」？

### B. `nextOverdueReminder` 在 `now == firstNagMs` 时会返回同一时刻

```kotlin
val steps = if (firstNagMs >= nowMillis) 0L else ((nowMillis - firstNagMs) / stepMs) + 1L
```

`now` 恰好等于 `firstNagMs` 时 `steps = 0`，返回的正是「现在」→ 理论上可以连发。实际 AlarmManager 触发会晚几毫秒，基本撞不上。要不要加 `firstNagMs + stepMs` 的下限做防御？

---

## 建议修复顺序

1. **P0-1 + P0-2 + P1-3 一起改**（同一段逻辑，分开改会互相打架）
2. **P1-4**（需要你选方案 ① 还是 ②）
3. P2-5、P2-6（顺手，风险为零）
4. 待拍板的 A / B 看你的意见

全部改完后单测应仍是 242 条全绿（这几处都不在单测覆盖范围内，建议给 `effectiveParses` 的合并逻辑补 2~3 条纯函数测试 —— 需要先把它从 Composable 里抽出来）。
