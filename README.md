<p align="center">
  <img src="docs/icon.png" width="150" alt="Todo" />
</p>

<h1 align="center">Todo</h1>

<p align="center">
  用一句话把事情记下来，日期、时间、提醒都由它自己解析
</p>

<p align="center">
  <a href="https://github.com/zuige66/todo/releases"><img src="https://img.shields.io/badge/release-v0.3-3F3F46?style=flat-square" alt="release" /></a>
  <img src="https://img.shields.io/badge/platform-Android-3DDC84?style=flat-square" alt="platform" />
  <img src="https://img.shields.io/badge/build-Kotlin%20%2B%20Compose-7F52FF?style=flat-square" alt="build" />
  <img src="https://img.shields.io/badge/storage-Room-4479A1?style=flat-square" alt="storage" />
  <img src="https://img.shields.io/badge/network-offline-4B5563?style=flat-square" alt="offline" />
  <img src="https://img.shields.io/badge/min%20sdk-26-F59E0B?style=flat-square" alt="min sdk" />
</p>

<p align="center">
  <a href="https://github.com/zuige66/todo/releases/latest"><b>下载 APK</b></a>
  &nbsp;·&nbsp;
  <a href="DESIGN.md">设计文档</a>
  &nbsp;·&nbsp;
  <a href="QUOTES.md">文案池</a>
</p>

---

独立的 Android 待办应用：**输入框是唯一入口**，没有日期选择器、没有分类和标签、没有账号。一句话里写了日期和时间，就自动解析并排好精确闹钟；只写了一句事，就安静地落在「无日期」里。数据全部留在本机 —— 未声明 `INTERNET` 权限，也不依赖任何在线服务。

---

## 目录

- [为什么是"一句话"](#为什么是一句话)
- [一句话输入](#一句话输入)
- [首页](#首页)
- [提醒](#提醒)
- [完成与重复](#完成与重复)
- [外观](#外观)
- [设置项一览](#设置项一览)
- [技术实现](#技术实现)
- [项目结构](#项目结构)
- [构建与运行](#构建与运行)
- [两条贯穿全局的约定](#两条贯穿全局的约定)
- [文档](#文档)

---

## 为什么是"一句话"

大多数待办应用记一件事要经过：新建 → 点日期 → 选日历 → 滚时间 → 填标题 → 选提醒。六个动作记一件事，摩擦比事情本身还大。

真正的待办都是**想起来的那一秒**产生的——走在路上、开会中途、刷牙的时候。这一秒能给的只有一句话。所以本应用的输入框是唯一的入口，没有日期选择器，没有时间滚轮，没有分类和标签。

一句话说清楚就够了：

| 你说 | 得到的待办 |
|---|---|
| `今天开会` | 今天 · 标题「开会」 |
| `明天下午4点 交周报` | 明天 16:00 · 标题「交周报」 |
| `周五 下午3点 组会 要带笔记本` | 本周五 15:00 · 标题「组会」· 详情「要带笔记本」 |
| `十号 交房租` | 本月 10 日 09:00 · 标题「交房租」（若本月 10 日已过则顺延到下月 10 日） |
| `十二月三十一号 跨年` | 12 月 31 日 09:00 · 标题「跨年」 |
| `8:30 晨会` | 今天（已过则明天）08:30 · 标题「晨会」 |
| `晚上11:50 睡觉` | 今天 23:50 · 标题「睡觉」 |
| `每天 8点 吃药` | 08:00 · 每天重复 · 标题「吃药」（今天的 8 点已过则从明天开始） |
| `明天，后天 开会` | **两条**待办：明天一条、后天一条，标题都是「开会」 |

---

## 一句话输入

```
周五 下午3点 交周报
└┬─┘ └──┬──┘ └─┬─┘
 日期   时间    事件
```

解析器是**纯离线的规则引擎**，不联网、不调用任何模型，正则与词表都在本地。

### 支持的日期写法

| 类别 | 写法 |
|---|---|
| 相对天 | 今天、明天、后天、大后天 |
| 本周 | 周四、星期四、礼拜四、本周四、这周四、这个星期四 |
| 下周 | 下周四、下个星期四、下礼拜四、**下下周四**（+2 周） |
| 月日 | 1月2日、一月二日、12月三十一号 |
| 纯日号 | 5号、十五号、二号 |
| 数字短日期 | `1.2` / `26.1.2` / `2026.1.2`（分隔符支持 `.` `-` `/`，两位年份按 20yy） |
| 年份词 | 今年、明年、下一年 |
| 月份词 | 本月、这个月、下个月（可再接 `X月` / `X日`） |
| 容错 | 全角数字与全角冒号、点号会先归一化（`８：３０` → `8:30`） |

中文数字支持到三十一，`1月二日`、`十二月三十一号` 都能读。

### 支持的时刻写法

- `15:30`、`15：30`
- `3点`、`下午3点`、`晚上11点`
- `3点半`、`3点一刻`、`3点三刻`、`3点20分`

**时段词带默认时刻**，只写时段不写具体时间时用它：

| 时段词 | 默认时刻 |
|---|---|
| 半夜 | 00:00 |
| 凌晨 | 01:00 |
| 清晨 | 06:00 |
| 早晨 / 早上 / 上午 | 08:00 |
| 中午 | 12:00 |
| 下午 | 14:00 |
| 傍晚 / 晚上 | 18:00 |
| 夜里 | 22:00 |

`今晚`、`明晚`、`后晚`、`今夜` 这类词还会让后面的裸小时发生 12→24 小时换算，`今晚8点` = 今天 20:00。

### 标题与详情

去掉日期时间之后剩下的部分，**第一个词是事件标题，其后全部是详情**：

```
周五 下午3点 组会 要带笔记本和充电器
              └┬┘ └───────┬───────┘
             标题         详情
```

### 几个刻意的判断

解析器在歧义处的取舍是明确写进代码的，不是"随便选一个"：

- **本周里已经过去的星期** → 报错 `dateError`，不会自作主张推到下周。你说"周四"，今天是周五，那说明你记错了，应该告诉你，不该偷偷改。
- **没写年份、算出来是过去的日期** → 静默顺延（月日推到明年，日号推到下个月）。因为 `1月2日` 本来就是循环的写法，顺延符合预期。
- **写死了年份、算出来是过去的日期** → 报错。`2026年1月2日` 指的是确定的某一天，它过去了就该提醒你。
- **只写时刻、今天已过** → 顺延到明天。`3点开会` 下午说的，指的是明天下午 3 点。
- **显式写了"今天"但时刻已过** → 报错，不静默顺延。你已经指定了是哪一天。
- **一句话里有多个日期** → 生成多条待办，一次性录入。写「明天，后天 开会」，得到两条待办。

---

## 首页

待办按**板块**分组，板块顺序可调整、可隐藏：

```
逾期      ← 过了时间还没做的，统一标红
有日期    ← 未来的待办
无日期    ← 只记了一件事，没定时间
```

「有日期」内部再分档位：**今日 / 明天 / 后天 / 本周 / 下周 / 更晚**，多选。

档位的归并规则：没勾的档位会并到**其后第一个已勾的档位**里；如果后面没有已勾的，由最后一个已勾的兜住。这样无论怎么勾，数据都不会凭空消失——只勾「今日」时，下周的待办会落在「今日」这一档里，而不是不见。

列表每一行右侧是 `[重复徽章] 日期`，第二行是周几与时间。逾期用醒目的颜色标记，且**每 20 秒刷新一次**——界面会自己从「今天」翻成「已逾期」，不需要退出重进。

---

## 提醒

提醒建立在 **AlarmManager 精确闹钟**上（`USE_EXACT_ALARM`），不是靠轮询，也不依赖后台常驻。

一条有精确时刻的待办，会有这些提醒点：

| 时刻 | 内容 |
|---|---|
| 提前 7 天 | 提前一周提醒 |
| 提前 3 天 | 提前 3 天提醒 |
| 提前 1 天 | 明天到期 |
| 提前 1 小时 | 1小时后到期（仅当写了精确时刻） |
| 到点 | 该办了 |
| 逾期后 | 还没完成，别忘了哦（按间隔反复，默认每天） |

### 通知

通知标题的版式是 `事件 + 空格 + 前缀 + 提醒语 + 后缀`：

```
开组会 🚀🚀该办了⏳⏳
```

这四段（提醒语、前缀装饰、后缀装饰、完成后装饰）都可以在设置里改，改完立刻能在预览里看到效果。清空任意一段就会整段隐藏，连它前后的空格一起省掉。

通知正文是同一条待办的文案（见 [QUOTES.md](QUOTES.md)），按待办 id 取模选取，所以同一条待办从生到死永远是同一句，点「完成」「撤回」来回切换也不会变。

通知带一个动作按钮，点「完成」后**同一条通知原地翻转**成已完成态（标题换成 `事件 🎉🎉`，按钮变成「撤回」），不会叠出第二条通知。点「撤回」则恢复原状并回到列表。

> 折叠状态下，正文只有一行，动作按钮的位置由系统通知栏决定。国产 ROM（澎湃 / MIUI / ColorOS）通常在通知展开后才渲染动作行。

---

## 完成与重复

点圆圈即可完成，任务移入归档页，可以查看也可以撤回。

给待办加重复（每天 / 每周 / 每月）之后，完成它**不会归档**，而是顺延到下一次：

```
每天 8点 吃药  →  完成后自动变成明天 8:00
```

这样"每天"的任务永远只有一条在列表里，不需要每天重新创建。

---

## 外观

基于 Material 3 Expressive，支持浅色、深色、跟随系统三种模式。

**11 套配色**，分两组：

| 平衡 | 雅紫 · 青碧 · 海蓝 · 暖橙 · 玫瑰 · 森林 |
|---|---|
| **鲜艳** | 电光紫 · 荧光青 · 亮橙红 · 电光蓝 · 玫红 |

深色模式的卡片底色有三档可选（标准暗色 / 淡彩 / 不染色）。深色底上的色彩强调走"抬明度、保色相"，而不是把颜色混进背景——前者得到的是深色版的红与青，后者只会得到发灰的脏色。

应用图标是一枚紫色的波浪花瓣印章，中间压着一道白色对勾。关于页有滚动联动的渐变背景与淡出效果。

---

## 设置项一览

| 分区 | 内容 |
|---|---|
| 提醒 | 提醒总开关、到点提醒、提前提醒 |
| 通知标题 | 提醒语、前缀装饰、后缀装饰、完成后装饰（含实时预览） |
| 权限 | 跳转系统设置，开启通知与精确闹钟 |
| 测试 | 发送测试通知、创建下一分钟到期的测试待办 |
| 外观 | 主题模式、配色、深色卡片底色 |
| 首页分组 | 板块排序与显隐、有日期档位（多选） |
| 逾期处理 | 逾期是否反复叮嘱、叮嘱间隔 |
| 待办行为 | 无日期待办的默认时刻、重复任务完成后是否自动顺延 |
| 关于 | 版本、开发者、开源地址 |

---

## 技术实现

| | |
|---|---|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 Expressive |
| 存储 | Room（schema v3）+ SharedPreferences |
| 提醒 | AlarmManager 精确闹钟 + BroadcastReceiver |
| 导航 | 单 Activity，Compose 内部状态切换 |
| 最低版本 | Android 8.0（API 26） |
| 编译 / 目标 | API 36 |
| 版本 | 0.3（versionCode 3） |
| 包名 | `com.lirui.oneline` |
| 网络 | **未声明 `INTERNET` 权限** —— 全部数据留在本机 |

### 数据模型

```kotlin
@Entity(tableName = "todos")
data class Todo(
    val id: Long,
    val title: String,        // 事件标题
    val detail: String,       // 详情
    val dueAtMillis: Long?,   // 到期时刻；null = 无日期
    val hasExactTime: Boolean,// 是否给了精确时刻，决定是否发 T-1h 提醒
    val repeatDays: Int,      // 重复步长：1 每天 / 7 每周 / 30 每月
    val archived: Boolean,
    val createdAt: Long,
    val completedAt: Long?
)
```

### 提醒链路

```
ReminderEngine   算出所有提醒点（时间 + 类型）
      ↓
ReminderScheduler 排进 AlarmManager，用 PendingIntent 指向 ReminderReceiver
      ↓
ReminderReceiver  闹钟响 → 查库 → 发通知 → 若是逾期类，再排下一次
      ↓
NotificationActionReceiver  「完成」/「撤回」→ 复用同一个归档入口
```

`ReminderScheduler` 是唯一排逾期提醒的入口，`ReminderEngine.nextOverdueReminder()` 必须在**到期前也返回** `due + interval`，否则新建待办的唠叨链永远不会启动。这条链路是这套提醒系统的关键约束。

### 测试

163 条单元测试，覆盖解析器、提醒引擎、格式化与首页分组：

```
Compose 之外的逻辑全部是纯函数，可以直接测
ParserTest          113 条
HomeGroupingTest     21 条
ReminderEngineTest   18 条
FormatTest           11 条
```

---

## 项目结构

```
app/src/main/kotlin/com/lirui/oneline/
├── MainActivity.kt              入口，主题与设置状态
├── OnelineApp.kt                Application：初始化数据库与通知渠道
├── data/
│   ├── AppDatabase.kt           Room 数据库与迁移
│   ├── TodoDao.kt
│   ├── TodoRepository.kt        唯一的数据入口
│   └── model/Todo.kt
├── parser/
│   ├── Parser.kt                自然语言解析（规则引擎）
│   └── ParsedTodo.kt
├── reminder/
│   ├── ReminderEngine.kt        计算提醒点
│   ├── ReminderScheduler.kt     排程
│   ├── ReminderReceiver.kt      闹钟回调 → 发通知
│   ├── NotificationHelper.kt    通知构建与标题拼装
│   ├── NotificationActionReceiver.kt  完成 / 撤回
│   └── BootReceiver.kt          重启后重建闹钟
├── ui/
│   ├── MainViewModel.kt
│   ├── screen/
│   │   ├── TodoListScreen.kt    首页、归档页、设置页
│   │   ├── AddSheet.kt          输入弹层
│   │   └── AboutScreen.kt
│   └── theme/Theme.kt           配色与深色卡片
└── util/
    ├── AppTime.kt               固定时区
    ├── Format.kt                日期格式化、逾期判定
    ├── HomeGrouping.kt          板块与档位分组
    ├── Quotes.kt                通知文案池
    └── SettingsRepo.kt          设置持久化

app/src/test/java/com/lirui/oneline/
├── parser/ParserTest.kt
├── reminder/ReminderEngineTest.kt
└── util/{FormatTest,HomeGroupingTest}.kt
```

---

## 构建与运行

需要 JDK 17。项目不带 Gradle Wrapper，使用本机 Gradle 9.x。

```bash
export JAVA_HOME=/path/to/jdk17

# 单元测试
gradle :app:testDebugUnitTest

# 打包
gradle :app:assembleRelease
# → app/build/outputs/apk/release/app-release.apk

# 安装
adb install -r app/build/outputs/apk/release/app-release.apk
```

如果想在同一台设备上覆盖安装，签名必须一致；debug 包与 release 包不通用。

---

## 两条贯穿全局的约定

### 时区固定为北京时间

全应用只用 `AppTime.ZONE`（`Asia/Shanghai`）计算，不读设备时区。设备时区变了，存下来的时间戳对应的墙钟时间不会漂移——"明天下午 3 点"永远是北京时间的明天下午 3 点。

### 逾期的判定口径

- **写了精确时刻** → 当前时间超过该时刻，就算逾期
- **只写了日期** → 过了那一天才算逾期

第二条尤其重要：只写"3号"的任务，不能在 3 号早上 9:01 就被标红。默认时刻（09:00）是用来排提醒的，不是用来判逾期的。列表里的分组与标红共用同一套判定，口径必须一致。

---

## 文档

| 文件 | 内容 |
|---|---|
| [DESIGN.md](DESIGN.md) | 设计决策、解析语义、踩过的坑与源码级结论 |
| [QUOTES.md](QUOTES.md) | 通知文案池 |
| [SMOKE-REPORT.md](SMOKE-REPORT.md) | 真机冒烟验证记录 |

---

## 作者

[zuige](https://github.com/zuige66) · [blog.zuiges.com](https://blog.zuiges.com)
