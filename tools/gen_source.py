# -*- coding: utf-8 -*-
"""生成软著申请用的《源程序》PDF 中间产物（HTML）。

软著对源程序的要求：前 30 页 + 后 30 页，每页不少于 50 行，
页眉标明软件名称、版本号与页码。本脚本按 50 行/页切分，共 60 页。

用法（在项目根目录）：
    python tools/gen_source.py
    msedge --headless=new --print-to-pdf=build/store/source-code.pdf \
           file:///<项目绝对路径>/build/store/source.html

★ 代码改动后必须重新生成，保证提交给版权中心的源程序与实际发布的 APK 一致
  （2026 年起是「功能—代码—文档」三重校验的有限实质审查）。
"""

import glob
import html
import os

# 项目根 = 本文件所在目录的上一级
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, 'build', 'store')

SOFTWARE_NAME = '一句话待办'
VERSION = 'V1.0'

MAX_CHARS = 90     # 单行最大字符数，超出折行续排（保证不撑破版心）
PER_PAGE = 50      # 每页行数
HALF_LINES = 30 * PER_PAGE   # 前 30 页 = 1500 行

CSS = '''
@page { size: A4; margin: 16mm 15mm 14mm 15mm; }
* { box-sizing: border-box; }
body { margin:0; font-family:"Microsoft YaHei",sans-serif; }
.page { page-break-after: always; break-after: page; }
.page:last-child { page-break-after: auto; break-after: auto; }
.hdr { display:flex; justify-content:space-between; font-size:9pt; color:#000;
       border-bottom:0.6pt solid #000; padding-bottom:3pt; margin-bottom:7pt; }
pre { font-family:Consolas,"Courier New",monospace; font-size:9pt; line-height:1.45;
      margin:0; white-space:pre; letter-spacing:0; }
'''


def collect_lines():
    """按路径顺序收集全部 Kotlin 源码行。"""
    files = sorted(glob.glob(os.path.join(ROOT, 'app', 'src', 'main', 'kotlin', '**', '*.kt'),
                             recursive=True))
    lines = []
    for path in files:
        with open(path, encoding='utf-8') as fh:
            lines.extend(fh.read().splitlines())
    return lines


def wrap_lines(lines):
    """把超长行折成多行，使每页的物理行数可控。"""
    physical = []
    for line in lines:
        line = line.expandtabs(4).rstrip()
        if not line:
            physical.append('')
            continue
        while len(line) > MAX_CHARS:
            physical.append(line[:MAX_CHARS])
            line = '    ' + line[MAX_CHARS:]   # 续行缩进，表明是同一行代码的延续
        physical.append(line)
    return physical


def build_html(physical):
    front = physical[:HALF_LINES]
    back = physical[-HALF_LINES:]
    pages = ([front[i:i + PER_PAGE] for i in range(0, HALF_LINES, PER_PAGE)] +
             [back[i:i + PER_PAGE] for i in range(0, HALF_LINES, PER_PAGE)])

    def esc(s):
        return html.escape(s).replace(' ', '&nbsp;')

    blocks = []
    for idx, page in enumerate(pages, 1):
        body = '\n'.join(esc(x) for x in page)
        blocks.append(
            f'<div class="page"><div class="hdr">'
            f'<span>{SOFTWARE_NAME} {VERSION}</span>'
            f'<span>第 {idx} 页 / 共 {len(pages)} 页</span></div>'
            f'<pre>{body}</pre></div>'
        )
    return (f'<!DOCTYPE html><html><head><meta charset="utf-8"><style>{CSS}</style>'
            f'</head><body>{"".join(blocks)}</body></html>')


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    raw = collect_lines()
    physical = wrap_lines(raw)
    if len(physical) < HALF_LINES * 2:
        raise SystemExit(f'源码不足：折行后 {len(physical)} 行，'
                         f'至少需要 {HALF_LINES * 2} 行才能凑满 60 页')
    out = os.path.join(OUT_DIR, 'source.html')
    with open(out, 'w', encoding='utf-8') as f:
        f.write(build_html(physical))
    print(f'源程序 HTML 已生成：{out}')
    print(f'源码 {len(raw)} 行 → 折行后 {len(physical)} 行 → 60 页（每页 {PER_PAGE} 行）')


if __name__ == '__main__':
    main()
