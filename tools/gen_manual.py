# -*- coding: utf-8 -*-
"""生成软著申请用的《软件说明书》PDF 中间产物（HTML）。

用法：
    python build/store/gen_manual.py
    msedge --headless=new --print-to-pdf=build/store/manual.pdf build/store/manual.html
"""

import os

# 项目根 = 本文件所在目录的上一级
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, 'build', 'store')

# 截图目录（file:// URL 交给 Edge 渲染时直接读取本地文件）
IMG = 'file:///' + os.path.join(ROOT, 'docs', 'screenshots').replace(os.sep, '/')


def img(name, cap, w=230):
    return (f'<div class="fig"><img src="{IMG}/{name}.jpg" style="width:{w}px">'
            f'<div class="cap">{cap}</div></div>')


CSS = '''
@page { size: A4; margin: 20mm 19mm 16mm 19mm; }
* { box-sizing: border-box; }
body { margin:0; font-family:"Microsoft YaHei",sans-serif; color:#111;
       font-size:10.5pt; line-height:1.62; }
.page { page-break-after: always; break-after: page; }
.page:last-child { page-break-after: auto; break-after: auto; }
h1 { font-size:15.5pt; margin:0 0 9pt; padding-bottom:5pt;
     border-bottom:1.4pt solid #4A3B8C; color:#2D2454; }
h2 { font-size:12.5pt; margin:13pt 0 5pt; color:#2D2454; }
h3 { font-size:11pt; margin:10pt 0 3pt; color:#3A2F6B; }
p { margin:0 0 6pt; text-align:justify; }
ul, ol { margin:0 0 6pt; padding-left:18pt; }
li { margin-bottom:2pt; }
table { border-collapse:collapse; width:100%; margin:6pt 0 9pt; font-size:9.5pt; }
th, td { border:0.6pt solid #999; padding:4pt 6pt; text-align:left; vertical-align:top; }
th { background:#EFEBFA; font-weight:bold; }
.cover { text-align:center; padding-top:150pt; }
.cover .t1 { font-size:30pt; font-weight:bold; letter-spacing:2pt; color:#2D2454; }
.cover .t2 { font-size:15pt; color:#555; margin-top:9pt; }
.cover .meta { margin-top:120pt; font-size:12pt; line-height:2.3; }
.cover .meta b { display:inline-block; width:96pt; text-align:right;
                 margin-right:10pt; color:#333; }
.fig { text-align:center; margin:10pt 0; page-break-inside:avoid; }
.fig img { border:0.6pt solid #ccc; border-radius:6pt; }
.cap { font-size:9.5pt; color:#666; margin-top:4pt; }
.toc { font-size:11pt; line-height:2.0; margin-top:14pt; }
.toc div { display:flex; justify-content:space-between;
           border-bottom:0.4pt dotted #bbb; }
.note { background:#F6F4FD; border-left:3pt solid #6C5CE7; padding:7pt 11pt;
        margin:8pt 0; font-size:10pt; }
'''

# 目录页码在生成 PDF 后按实际页数校准（见文件末尾 TOC 常量）
TOC = [
    ('一、软件概述', 3),
    ('二、运行环境', 4),
    ('三、功能说明', 5),
    ('　　3.1 一句话输入与解析', 5),
    ('　　3.2 首页分组展示', 7),
    ('　　3.3 提醒机制', 8),
    ('　　3.4 完成、归档与重复', 9),
    ('　　3.5 桌面小组件', 10),
    ('　　3.6 外观与个性化', 11),
    ('　　3.7 隐私保护与权限说明', 12),
    ('四、技术架构', 13),
    ('五、版本信息', 14),
]


def build_pages():
    pages = []

    # ── 封面 ──
    pages.append('''
<div class="page cover">
  <div class="t1">一句话待办</div>
  <div class="t2">软件说明书</div>
  <div class="t2" style="margin-top:6pt">版本号 V1.0</div>
  <div class="meta">
    <div><b>软件全称</b>一句话待办</div>
    <div><b>版本号</b>V1.0</div>
    <div><b>著作权人</b>李睿</div>
    <div><b>开发完成日期</b>2026 年 10 月</div>
    <div><b>文档编写日期</b>2026 年 10 月</div>
  </div>
</div>''')

    # ── 目录 ──
    toc_rows = ''.join(f'<div><span>{t}</span><span>{p}</span></div>' for t, p in TOC)
    pages.append(f'''
<div class="page">
  <h1>目　录</h1>
  <div class="toc">{toc_rows}</div>
</div>''')

    # ── 一、概述 ──
    pages.append('''
<div class="page">
  <h1>一、软件概述</h1>
  <h2>1.1 产品定位</h2>
  <p>「一句话待办」是一款运行于 Android 平台的本地待办事项管理软件。软件以<strong>单一文本输入框</strong>作为唯一录入入口，用户在输入框中用日常语言描述一件事，软件自动从中解析出日期、时间与事件内容，并据此生成待办事项与提醒。</p>
  <p>软件不设日期选择器、时间滚轮、分类体系与标签体系，也不要求注册账号。其设计目标是降低「记录一件事」的操作成本，使用户在想到的瞬间即可完成录入。</p>

  <h2>1.2 核心特点</h2>
  <ul>
    <li><strong>自然语言解析</strong>：支持「周五下午3点交周报」「明天，后天 开会」「25号到1号出差」等表述，自动识别日期、时间段与时刻。</li>
    <li><strong>精确提醒</strong>：基于系统 AlarmManager 精确闹钟，在用户设定时刻准点发出通知，不依赖后台轮询。</li>
    <li><strong>本地存储</strong>：全部数据存放于设备本地的应用私有目录，软件不申请网络权限，不向任何服务器传输数据。</li>
    <li><strong>桌面小组件</strong>：在桌面直接查看最近待办，点击行首圆圈即可完成。</li>
    <li><strong>离线可用</strong>：全部功能无需联网。</li>
  </ul>

  <h2>1.3 软件形态</h2>
  <p>软件为单机应用，安装包格式为 APK，安装后以独立应用图标出现在系统桌面，不依赖任何服务端程序或第三方账号体系。</p>
</div>''')

    # ── 二、运行环境 ──
    pages.append('''
<div class="page">
  <h1>二、运行环境</h1>
  <h2>2.1 硬件环境</h2>
  <table>
    <tr><th style="width:28%">项目</th><th>要求</th></tr>
    <tr><td>设备类型</td><td>Android 智能手机或平板电脑</td></tr>
    <tr><td>处理器架构</td><td>ARM（软件不含本地原生库，对架构无额外要求）</td></tr>
    <tr><td>存储空间</td><td>安装约需 10 MB，运行数据随待办数量增长</td></tr>
    <tr><td>内存</td><td>建议 2 GB 及以上</td></tr>
  </table>

  <h2>2.2 软件环境</h2>
  <table>
    <tr><th style="width:28%">项目</th><th>要求</th></tr>
    <tr><td>操作系统</td><td>Android 8.0（API 26）及以上</td></tr>
    <tr><td>目标版本</td><td>API 36</td></tr>
    <tr><td>网络</td><td><strong>不需要</strong>。软件不申请网络权限，全部功能离线可用</td></tr>
    <tr><td>账号</td><td>不需要。无注册、无登录</td></tr>
    <tr><td>第三方依赖</td><td>无服务端依赖；内置开源组件均为本地运行库</td></tr>
  </table>

  <h2>2.3 权限使用说明</h2>
  <table>
    <tr><th style="width:38%">权限</th><th>用途</th></tr>
    <tr><td>通知</td><td>在待办到期时发送提醒通知</td></tr>
    <tr><td>精确闹钟</td><td>使提醒在用户设定的精确时刻响铃</td></tr>
    <tr><td>开机自启</td><td>设备重启后重建已设定的提醒</td></tr>
    <tr><td>忽略电池优化</td><td>仅在用户主动点击时跳转系统白名单页，避免后台被清理导致漏提醒</td></tr>
    <tr><td>前台服务</td><td>可选的后台常驻通知，用于在通知栏直接完成或撤回待办</td></tr>
  </table>
  <div class="note">软件不申请位置、通讯录、短信、通话记录、相机、麦克风、存储读写、设备标识及网络等权限。</div>
</div>''')

    # ── 3.1 ──
    pages.append(f'''
<div class="page">
  <h1>三、功能说明</h1>
  <h2>3.1 一句话输入与解析</h2>
  <p>输入框是软件的唯一录入入口。用户在输入框中键入一句话后，软件调用本地规则引擎进行解析，将句子拆分为「日期」「时间」「事件标题」「详情」四个部分。</p>
  <h3>3.1.1 支持的日期写法</h3>
  <table>
    <tr><th style="width:30%">类别</th><th>示例</th></tr>
    <tr><td>相对天</td><td>今天、明天、后天、大后天</td></tr>
    <tr><td>本周 / 下周</td><td>周四、星期四、本周四、下周四、下下周四</td></tr>
    <tr><td>月日</td><td>1月2日、一月二日、12月三十一号</td></tr>
    <tr><td>纯日号</td><td>5号、十五号、二号</td></tr>
    <tr><td>时间段</td><td>25号到1号、10月24日到26日</td></tr>
    <tr><td>数字短日期</td><td>1.2、26.1.2、2026.1.2</td></tr>
    <tr><td>周末 / 边界</td><td>周末、下周末、月底、年底、明年初</td></tr>
  </table>
  <h3>3.1.2 支持的时刻写法</h3>
  <p>支持「15:30」「下午3点」「3点半」「3点一刻」「3点20分」，以及时间区间「下午3点到5点」「9:00-10:30」。区间写法中，结束时刻自动继承开始时刻的时段词。</p>
  <h3>3.1.3 标题与详情的划分</h3>
  <p>去掉日期时间后剩余文本中，第一个词作为事件标题，其后内容作为详情。例如「周五 下午3点 组会 要带笔记本」，标题为「组会」，详情为「要带笔记本」。</p>
</div>''')

    # ── 3.1 续（图放这页顶部：3.1 首页正文已排满，图留在那儿会被挤成单独一页） ──
    pages.append(f'''
<div class="page">
  {img('add', '图 3-1　一句话添加待办', 165)}
  <h2>3.1 一句话输入与解析（续）</h2>
  <h3>3.1.4 解析示例</h3>
  <table>
    <tr><th style="width:40%">输入</th><th>生成结果</th></tr>
    <tr><td>今天开会</td><td>今天 · 标题「开会」</td></tr>
    <tr><td>明天下午4点 交周报</td><td>明天 16:00 · 标题「交周报」</td></tr>
    <tr><td>周五 下午3点 组会 要带笔记本</td><td>本周五 15:00 · 标题「组会」· 详情「要带笔记本」</td></tr>
    <tr><td>十号 交房租</td><td>本月 10 日 09:00 · 标题「交房租」</td></tr>
    <tr><td>每天 8点 吃药</td><td>08:00 · 每天重复 · 标题「吃药」</td></tr>
    <tr><td>明天，后天 开会</td><td>生成两条待办，分别为明天与后天</td></tr>
  </table>
  <h3>3.1.5 歧义处理规则</h3>
  <ul>
    <li>本周内已经过去的星期，软件提示日期有误，不自动顺延至下周。</li>
    <li>未写年份且结果为过去日期时静默顺延（月日推至明年，日号推至下月）。</li>
    <li>明确写出年份且日期已过时，提示错误，不顺延。</li>
    <li>仅写时刻且当天该时刻已过时，顺延至次日。</li>
    <li>显式写「今天」但时刻已过时，提示错误，不顺延。</li>
  </ul>
  <h3>3.1.6 手动修正</h3>
  <p>解析结果以预览卡片形式呈现，用户可在提交前通过日期选择器与时间选择器覆盖解析值，也可直接修改标题与详情；亦可将多条单日结果合并为一个时间段。</p>
</div>''')

    # ── 3.2 ──
    pages.append(f'''
<div class="page">
  <h2>3.2 首页分组展示</h2>
  <p>首页按板块对待办进行分组，板块顺序与显隐均可由用户调整。默认板块为：</p>
  <table>
    <tr><th style="width:24%">板块</th><th>含义</th></tr>
    <tr><td>逾期</td><td>已过截止时间且未完成的待办，统一以醒目颜色标记</td></tr>
    <tr><td>有日期</td><td>截止时间在未来的待办，内部再分今日 / 明天 / 后天 / 本周 / 下周 / 更晚六档</td></tr>
    <tr><td>无日期</td><td>仅记录事件、未指定时间的待办</td></tr>
  </table>
  <h3>3.2.1 档位归并</h3>
  <p>未被勾选的档位会并入其后的第一个已勾选档位；若其后没有已勾选档位，则由最后一个已勾选档位承接。该规则保证无论用户如何配置档位，待办均不会从列表中消失。</p>
  <h3>3.2.2 逾期判定口径</h3>
  <ul>
    <li>写了精确时刻：当前时间超过该时刻即判定逾期。</li>
    <li>只写日期：该日结束后才判定逾期。</li>
    <li>时间段：以结束日为准，进行中的时间段不标记逾期。</li>
  </ul>
  <p>列表每 20 秒刷新一次逾期状态，界面无需退出重进即可从「今天」翻转为「已逾期」。</p>
  {img('home', '图 3-2　首页板块分组', 215)}
</div>''')

    # ── 3.3 ──
    pages.append('''
<div class="page">
  <h2>3.3 提醒机制</h2>
  <p>提醒功能基于 Android 系统的 AlarmManager 精确闹钟实现。软件在待办创建或修改时，计算出全部提醒时间点并逐一注册闹钟；系统在到达时间点时唤醒软件进程并发出通知。</p>
  <h3>3.3.1 提醒时间点</h3>
  <table>
    <tr><th style="width:32%">时刻</th><th>通知内容</th></tr>
    <tr><td>提前 7 天</td><td>提前一周提醒</td></tr>
    <tr><td>提前 3 天</td><td>提前 3 天提醒</td></tr>
    <tr><td>提前 1 天</td><td>明天到期</td></tr>
    <tr><td>提前 1 小时</td><td>1 小时后到期（仅限写了精确时刻的待办）</td></tr>
    <tr><td>时间段开始日</td><td>今天开始</td></tr>
    <tr><td>到点</td><td>该办了</td></tr>
    <tr><td>逾期后</td><td>还没完成，别忘了哦（按设定间隔重复）</td></tr>
  </table>
  <h3>3.3.2 通知标题模板</h3>
  <p>通知标题由「事件名称 + 标题模板」组成，模板中的占位符 {提醒} 会替换为当前提醒类型的状态语。用户可在设置中编辑模板与完成后缀，保存后立即生效，页面下方同时给出三种效果预览。</p>
  <h3>3.3.3 通知交互</h3>
  <p>通知携带动作按钮。点击「完成」后，同一条通知就地翻转为已完成状态，按钮变为「撤回」；点击「撤回」则恢复原状。软件不会为此叠加第二条通知。</p>
  <h3>3.3.4 可靠性保障</h3>
  <ul>
    <li>设备重启、软件升级、系统时间被修改后，自动重建全部提醒。</li>
    <li>每 12 小时执行一次兜底巡检，补齐被系统清理掉的闹钟。</li>
    <li>设置页提供四项自检：通知权限、精确闹钟、电池优化白名单、自启动，并给出处理入口。</li>
  </ul>
</div>''')

    # ── 3.4 ──
    pages.append(f'''
<div class="page">
  <h2>3.4 完成、归档与重复</h2>
  <h3>3.4.1 两种完成方式</h3>
  <ul>
    <li><strong>点击圆圈</strong>：圆圈以 650 毫秒填充动画完成后归档。</li>
    <li><strong>向右滑动卡片</strong>：滑动超过阈值后归档，未达阈值自动弹回。</li>
  </ul>
  <p>归档后底部浮出「已完成」提示，3 秒内可点击「撤销」恢复。归档的计时器挂载于视图模型，滑动与切页均不会丢失归档动作。</p>
  <h3>3.4.2 重复待办</h3>
  <p>为待办设置每天、每周或每月重复后，完成该待办不会归档，而是顺延至下一次。例如「每天 8点 吃药」完成后自动变为次日 8:00。该机制使周期性任务在列表中始终只保留一条。</p>
  <h3>3.4.3 归档页</h3>
  <p>归档页展示全部已完成待办，支持恢复与删除。删除需二次确认，确认后从本地数据库彻底移除。</p>
  {img('archive', '图 3-3　归档页', 215)}
</div>''')

    # ── 3.5 ──
    pages.append(f'''
<div class="page">
  <h2>3.5 桌面小组件</h2>
  <p>软件提供桌面小组件，固定展示最近 4 条待办。点击行首圆圈可直接完成对应待办，无需进入应用。</p>
  <h3>3.5.1 显示配置</h3>
  <table>
    <tr><th style="width:34%">开关</th><th>说明</th></tr>
    <tr><td>显示日期</td><td>总开关，关闭后仅显示事件名称</td></tr>
    <tr><td>日期位置</td><td>标题下方（事件优先）或标题右侧（日期优先）</td></tr>
    <tr><td>只显示日期</td><td>开启显示「10月23日」，关闭显示「10月23日 14点」</td></tr>
    <tr><td>隐藏头部</td><td>隐藏顶部标题与条数统计</td></tr>
  </table>
  <p>上述配置与组件尺寸无关，组件拉伸为不同尺寸时行为保持一致。</p>
  <h3>3.5.2 添加方式</h3>
  <p>长按桌面空白处，选择「小组件」，搜索「Todo 待办」添加；也可在软件设置页点击「添加小组件到桌面」一键添加。</p>
  {img('widget', '图 3-4　桌面小组件', 300)}
</div>''')

    # ── 3.6 ──
    pages.append(f'''
<div class="page">
  <h2>3.6 外观与个性化</h2>
  <p>软件界面基于 Material 3 Expressive 设计语言构建，提供以下个性化选项：</p>
  <table>
    <tr><th style="width:30%">设置项</th><th>可选值</th></tr>
    <tr><td>主题模式</td><td>浅色 / 深色 / 跟随系统</td></tr>
    <tr><td>配色方案</td><td>11 套，分为「平衡」与「鲜艳」两组</td></tr>
    <tr><td>深色卡片底色</td><td>不染色 / 淡彩 / 标准暗色 / 深彩</td></tr>
    <tr><td>沉浸式顶栏</td><td>开启后列表下滑时标题栏收起，上滑立即恢复</td></tr>
    <tr><td>列表手势</td><td>右滑归档、长按编辑、双击编辑，可分别开关</td></tr>
  </table>
  <h3>3.6.1 配色体系</h3>
  <p>「平衡」组包含雅紫、青碧、海蓝、暖橙、玫瑰、森林六套；「鲜艳」组包含电光紫、荧光青、亮橙红、电光蓝、玫红五套。深色模式下采用抬明度、保色相的方式生成强调色，避免直接混色造成的灰浊效果。</p>
  <h3>3.6.2 关于页</h3>
  <p>关于页展示软件图标、名称、版本号、包名、开发者信息、隐私政策入口及备案编号。页面背景为随滚动联动的渐变动效。</p>
  {img('settings', '图 3-5　设置页', 215)}
</div>''')

    # ── 3.7 ──
    pages.append('''
<div class="page">
  <h2>3.7 隐私保护与权限说明</h2>
  <h3>3.7.1 数据存储</h3>
  <p>全部待办数据存储于设备本地的应用私有目录（Android 应用沙盒），其他应用无法读取。数据不上传服务器，不同步云端。软件已关闭系统自动备份功能，数据不会随账号备份至云端。</p>
  <h3>3.7.2 不收集个人信息</h3>
  <ul>
    <li>无账号体系，不要求注册、登录，不采集手机号、邮箱。</li>
    <li>不采集设备标识符（IMEI、Android ID、OAID、MAC 地址等）。</li>
    <li>不采集位置信息，不读取通讯录、短信、通话记录。</li>
    <li>不访问相机、麦克风、相册。</li>
    <li>不采集使用行为与崩溃日志，不集成任何第三方统计、广告或推送组件。</li>
  </ul>
  <h3>3.7.3 不申请网络权限</h3>
  <p>软件未声明 INTERNET 权限，运行期间不发起任何网络请求，全部功能离线可用。关于页中的外部链接点击后交由系统浏览器打开，网络访问由浏览器完成，软件本身不参与。</p>
  <h3>3.7.4 隐私政策与授权</h3>
  <p>软件首次启动时展示隐私政策同意弹窗，用户须明确选择「同意并继续」或「不同意并退出」。弹窗不响应点击遮罩与返回键，须显式选择。所有运行时权限的申请均在用户同意隐私政策之后发起。用户同意后可在「关于 → 隐私政策」中随时查阅完整政策文本。</p>
  <h3>3.7.5 数据删除</h3>
  <p>在软件内删除单条待办，该条数据立即从本地数据库移除；在系统设置中清除应用数据，全部待办与设置立即删除；卸载软件则全部数据随应用一并删除。</p>
</div>''')

    # ── 四、技术架构 ──
    pages.append('''
<div class="page">
  <h1>四、技术架构</h1>
  <h2>4.1 开发技术</h2>
  <table>
    <tr><th style="width:30%">项目</th><th>说明</th></tr>
    <tr><td>开发语言</td><td>Kotlin</td></tr>
    <tr><td>界面框架</td><td>Jetpack Compose + Material 3</td></tr>
    <tr><td>本地存储</td><td>Room 数据库（schema v6）+ SharedPreferences</td></tr>
    <tr><td>定时机制</td><td>AlarmManager 精确闹钟 + BroadcastReceiver</td></tr>
    <tr><td>后台任务</td><td>WorkManager 周期巡检</td></tr>
    <tr><td>界面结构</td><td>单 Activity，Compose 内部状态切换，四个主导航页</td></tr>
  </table>

  <h2>4.2 模块划分</h2>
  <table>
    <tr><th style="width:26%">模块</th><th>职责</th></tr>
    <tr><td>parser</td><td>自然语言解析规则引擎，将句子解析为日期、时间、标题、详情</td></tr>
    <tr><td>data</td><td>Room 数据库、数据访问对象与仓库层，统一数据出入口</td></tr>
    <tr><td>reminder</td><td>提醒时间点计算、闹钟排程、通知构建与通知动作处理</td></tr>
    <tr><td>keepalive</td><td>可选后台常驻服务与各厂商系统兼容处理</td></tr>
    <tr><td>widget</td><td>桌面小组件的布局渲染与交互处理</td></tr>
    <tr><td>ui</td><td>界面、视图模型、主题与配色</td></tr>
    <tr><td>util</td><td>时间处理、格式化、分组、文案与设置持久化</td></tr>
  </table>

  <h2>4.3 数据模型</h2>
  <p>待办实体包含以下字段：唯一标识、事件标题、详情、到期时刻、时间段开始日、是否含精确时刻、重复步长、是否已归档、创建时间、完成时间。到期时刻为空表示无日期待办；重复步长为 1、7、30 分别表示每天、每周、每月。</p>
  <h2>4.4 测试</h2>
  <p>软件包含 242 项单元测试，覆盖解析器、提醒引擎、通知标题拼装、日期格式化、首页分组与时间计算等纯逻辑模块。</p>
</div>''')

    # ── 五、版本信息 ──
    pages.append('''
<div class="page">
  <h1>五、版本信息</h1>
  <table>
    <tr><th style="width:32%">项目</th><th>内容</th></tr>
    <tr><td>软件全称</td><td>一句话待办</td></tr>
    <tr><td>版本号</td><td>V1.0</td></tr>
    <tr><td>版本代码</td><td>34</td></tr>
    <tr><td>应用包名</td><td>com.lirui.oneline</td></tr>
    <tr><td>安装包大小</td><td>约 2.0 MB</td></tr>
    <tr><td>最低系统版本</td><td>Android 8.0（API 26）</td></tr>
    <tr><td>目标系统版本</td><td>API 36</td></tr>
    <tr><td>开发完成日期</td><td>2026 年 10 月</td></tr>
  </table>
  <h2>5.1 本版本功能范围</h2>
  <ul>
    <li>一句话自然语言解析，支持日期、时刻、时间段、重复规则识别</li>
    <li>首页板块分组与档位归并，逾期状态实时刷新</li>
    <li>精确闹钟提醒，含提前提醒、到点提醒与逾期重复提醒</li>
    <li>通知内直接完成与撤回</li>
    <li>归档、撤销与重复待办顺延</li>
    <li>桌面小组件，支持点击完成与四项显示配置</li>
    <li>11 套配色、三种主题模式、四档深色卡片底色</li>
    <li>隐私政策同意流程与权限自检</li>
  </ul>
  <div class="note">本版本不包含：账号体系、云同步、社交分享、广告、内购、应用内更新。</div>
</div>''')

    return pages


def main():
    pages = build_pages()
    doc = ('<!DOCTYPE html><html><head><meta charset="utf-8">'
           f'<style>{CSS}</style></head><body>{"".join(pages)}</body></html>')
    os.makedirs(OUT_DIR, exist_ok=True)
    out = os.path.join(OUT_DIR, 'manual.html')
    with open(out, 'w', encoding='utf-8') as f:
        f.write(doc)
    print(f'说明书 HTML 已生成：{out}（{len(pages)} 个分页块）')


if __name__ == '__main__':
    main()
