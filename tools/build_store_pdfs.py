# -*- coding: utf-8 -*-
"""一键生成软著申请用的两个 PDF（源程序 + 说明书）。

    python tools/build_store_pdfs.py

产物：
    build/store/source-code.pdf          → dist/store/01-一句话待办-源程序.pdf
    build/store/manual.pdf               → dist/store/02-一句话待办-说明书.pdf

依赖：本机装有 Edge 或 Chrome（用它把 HTML 打印成 PDF，中文字体不会缺）。
"""

import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BUILD_DIR = os.path.join(ROOT, 'build', 'store')
DIST_DIR = os.path.join(ROOT, 'dist', 'store')

EDGE_PATHS = [
    r'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe',
    r'C:\Program Files\Microsoft\Edge\Application\msedge.exe',
    r'C:\Program Files\Google\Chrome\Application\chrome.exe',
    r'C:\Program Files (x86)\Google\Chrome\Application\chrome.exe',
]


def find_browser():
    for path in EDGE_PATHS:
        if os.path.exists(path):
            return path
    raise SystemExit('找不到 Edge / Chrome，无法打印 PDF')


def run(cmd):
    print('  $', ' '.join(str(c) for c in cmd[:2]), '...')
    subprocess.run(cmd, check=True, cwd=ROOT)


def print_pdf(browser, html_name, pdf_name):
    html_path = os.path.join(BUILD_DIR, html_name)
    pdf_path = os.path.join(BUILD_DIR, pdf_name)
    url = 'file:///' + html_path.replace(os.sep, '/')
    run([browser, '--headless=new', '--disable-gpu', '--no-pdf-header-footer',
         f'--print-to-pdf={pdf_path}', url])
    if not os.path.exists(pdf_path):
        raise SystemExit(f'PDF 未生成：{pdf_path}')
    return pdf_path


def main():
    os.makedirs(BUILD_DIR, exist_ok=True)
    os.makedirs(DIST_DIR, exist_ok=True)

    print('[1/4] 生成源程序 HTML')
    run([sys.executable, os.path.join('tools', 'gen_source.py')])

    print('[2/4] 生成说明书 HTML')
    run([sys.executable, os.path.join('tools', 'gen_manual.py')])

    browser = find_browser()
    print(f'[3/4] 打印 PDF（{os.path.basename(browser)}）')
    source_pdf = print_pdf(browser, 'source.html', 'source-code.pdf')
    manual_pdf = print_pdf(browser, 'manual.html', 'manual.pdf')

    print('[4/4] 复制到 dist/store')
    shutil.copy(source_pdf, os.path.join(DIST_DIR, '01-一句话待办-源程序.pdf'))
    shutil.copy(manual_pdf, os.path.join(DIST_DIR, '02-一句话待办-说明书.pdf'))

    for f in sorted(os.listdir(DIST_DIR)):
        size = os.path.getsize(os.path.join(DIST_DIR, f))
        print(f'  {f}  {size / 1024 / 1024:.2f} MB')
    print('完成')


if __name__ == '__main__':
    main()
