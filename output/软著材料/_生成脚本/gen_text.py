# -*- coding: utf-8 -*-
"""把申请表的字段导出为纯文本，便于直接复制粘贴到在线表单。"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_form as F

OUT = sys.argv[1] if len(sys.argv) > 1 else r"D:/Workplace/Todo/output/软著材料"

L = []
A = L.append
A("一句话待办 V1.0.2  软件著作权登记 · 表单填写速查")
A("=" * 56)
A("说明：以下每一段对应版权中心在线表单的一个输入框，")
A("      单独复制【】符号之后的内容即可，不含【】本身。")
A("")
A("-" * 56)
A("一、软件申请信息")
A("-" * 56)
for k, v, note in F.FIELDS:
    A("【%s】" % k)
    A(v)
    if note:
        A("（说明：%s）" % note)
    A("")
A("-" * 56)
A("二、软件功能与特点")
A("-" * 56)
A("【开发目的】（限 50 字，现 %d 字）" % len(F.PURPOSE))
A(F.PURPOSE)
A("")
A("【面向领域 / 行业】（限 50 字，现 %d 字）" % len(F.DOMAIN))
A(F.DOMAIN)
A("")
A("【软件的主要功能】（限 500 至 1300 字，现 %d 字）" % len(F.FUNCS))
A(F.FUNCS.strip())
A("")
A("【软件的技术特点 · 类别】（12 个选项，只勾 1 个）")
A("APP")
A("")
A("【软件的技术特点 · 文本框】（限 100 字，现 %d 字，直接复制下一行）" % len(F.FEATURES))
A(F.FEATURES)
A("")
A("-" * 56)
A("三、需要上传的材料")
A("-" * 56)
A("程序鉴别材料（一般交存）：源代码鉴别材料_一句话待办.pdf   62 页")
A("文档鉴别材料（一般交存）：文档鉴别材料_一句话待办.pdf     32 页")
A("其他相关证明文件：无需上传（个人实名认证已完成时）")
A("")
A("-" * 56)
A("四、两个容易填错的地方")
A("-" * 56)
A("1. 编程语言列表里没有 Kotlin。请勾选 Java，再在下方的")
A("   「若有需要，请输入其他编程语言」中填写 Kotlin。")
A("2. 版本号：本次材料统一按 V1.0.2 出具。若该证书要用于")
A("   应用商店资质审核，而商店在架版本是 V1.0.1，建议把")
A("   版本号改成 V1.0.1 与在架版本对齐，材料可一键重出。")

txt = "\n".join(L)
path = os.path.join(OUT, "表单填写速查.txt")
with open(path, "w", encoding="utf-8-sig") as f:
    f.write(txt)
print("已生成：" + path)

DEV_KEYS = ["开发的硬件环境", "运行的硬件环境", "开发该软件的操作系统",
            "软件开发环境 / 开发工具", "该软件的运行平台 / 操作系统",
            "软件运行支撑环境 / 支持软件"]
D = []
a = D.append
a("一句话待办 V1.0.2 · 「软件开发信息」页 · 6 个输入框")
a("=" * 56)
a("表单每个输入框限 50 字，下列字数已逐项核对。")
a("复制时只取【】下面那一行，不带字数标注。")
a("")
for k in DEV_KEYS:
    v = dict((x, y) for x, y, _ in F.FIELDS)[k]
    a("【%s】 %d 字" % (k, len(v)))
    a(v)
    a("")
a("-" * 56)
a("本页下方还有「编程语言」多选项：")
a("  勾选 Java，并在「若有需要，请输入其他编程语言」填 Kotlin")
a("  （表单列表里没有 Kotlin，纯 Kotlin 项目必须这样填）")
a("")
a("-" * 56)
a("顺带：上一页「软件的技术特点」有两个输入控件")
a("  类别：只勾 APP")
a("  文本框（77 字）：")
a(F.FEATURES)
path2 = os.path.join(OUT, "软件开发信息_可直接复制.txt")
with open(path2, "w", encoding="utf-8-sig") as f:
    f.write("\n".join(D))
print("已生成：" + path2)
