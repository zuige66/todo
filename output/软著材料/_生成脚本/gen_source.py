# -*- coding: utf-8 -*-
"""生成《源代码鉴别材料》：前 30 页 + 后 30 页，每页 50 行。"""
import os, sys, glob
from docx.shared import Pt, Cm
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING, WD_BREAK
from docx.enum.section import WD_SECTION
from docx.oxml.ns import qn
import docx_common as C

SRC_ROOT = sys.argv[1] if len(sys.argv) > 1 else r"/tmp/cr-src"
OUT = sys.argv[2] if len(sys.argv) > 2 else r"D:/Workplace/Todo/output/软著材料"
SOFT = "一句话待办"
VER = "V1.0.2"
LINES_PER_PAGE = 50
PAGES_EACH = 30
LINE_PT = 12.5          # 精确行距，50 行 = 625pt，页面可用 698pt，余量容忍折行

TARGET = (SOFT + " " + VER + " 源代码").strip()


def collect(root):
    base = os.path.join(root, "app", "src", "main")
    files = sorted(glob.glob(os.path.join(base, "**", "*.kt"), recursive=True))
    out = []
    for f in files:
        rel = "app/src/main/" + os.path.relpath(f, base).replace("\\", "/")
        out.append(("file", rel))
        with open(f, "r", encoding="utf-8", errors="replace") as fh:
            for ln in fh:
                out.append(("code", ln.rstrip("\n").rstrip()))
    return out, len(files)


def emit_block(doc, rows):
    """rows 为 (kind, text) 列表，每 LINES_PER_PAGE 行强制分页。"""
    for i, (kind, text) in enumerate(rows):
        if i > 0 and i % LINES_PER_PAGE == 0:
            pb = doc.add_paragraph()
            pb.paragraph_format.space_after = Pt(0)
            pb.add_run().add_break(WD_BREAK.PAGE)
        para = doc.add_paragraph()
        pf = para.paragraph_format
        pf.line_spacing_rule = WD_LINE_SPACING.EXACTLY
        pf.line_spacing = Pt(LINE_PT)
        pf.space_before = Pt(0)
        pf.space_after = Pt(0)
        pf.first_line_indent = Pt(0)
        pf.widow_control = False
        run = para.add_run(text if text else " ")
        run.font.size = Pt(9)
        run.font.name = C.MONO
        rpr = run._element.get_or_add_rPr()
        rf = rpr.find(qn('w:rFonts'))
        if rf is None:
            from docx.oxml import OxmlElement
            rf = OxmlElement('w:rFonts'); rpr.append(rf)
        rf.set(qn('w:eastAsia'), C.SONG)
        rf.set(qn('w:ascii'), C.MONO)
        rf.set(qn('w:hAnsi'), C.MONO)
        if kind == "file":
            run.bold = True


def main():
    rows, nfiles = collect(SRC_ROOT)
    total = len(rows)
    need = LINES_PER_PAGE * PAGES_EACH
    head = rows[:need]
    tail = rows[-need:]
    print("源文件 %d 个，总行数（含文件路径行）%d，前 %d 行 + 后 %d 行"
          % (nfiles, total, len(head), len(tail)))

    doc = C.new_document()
    sec = doc.sections[0]
    sec.top_margin = Cm(2.2); sec.bottom_margin = Cm(2.2)
    sec.left_margin = Cm(2.2); sec.right_margin = Cm(2.2)
    sec.header_distance = Cm(1.2); sec.footer_distance = Cm(1.2)

    hp = sec.header.paragraphs[0]
    hp.alignment = WD_ALIGN_PARAGRAPH.CENTER
    C._set_run(hp.add_run(TARGET), cn=C.SONG, size=9)
    fp = sec.footer.paragraphs[0]
    fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
    C._page_field(fp)

    emit_block(doc, head)

    # 分隔页
    doc.add_paragraph().add_run().add_break(WD_BREAK.PAGE)
    for _ in range(12):
        doc.add_paragraph()
    p1 = doc.add_paragraph(); p1.alignment = WD_ALIGN_PARAGRAPH.CENTER
    C._set_run(p1.add_run("（以上为源程序前连续 30 页，每页 50 行）"),
               cn=C.SONG, size=12, bold=True)
    p2 = doc.add_paragraph(); p2.alignment = WD_ALIGN_PARAGRAPH.CENTER
    C._set_run(p2.add_run("（以下为源程序后连续 30 页，每页 50 行）"),
               cn=C.SONG, size=12, bold=True)
    p3 = doc.add_paragraph(); p3.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p3.paragraph_format.space_before = Pt(18)
    C._set_run(p3.add_run("源程序共 %d 个文件、%d 行，此处提交前 1500 行与后 1500 行"
                          % (nfiles, total - nfiles)), cn=C.SONG, size=10.5)

    doc.add_paragraph().add_run().add_break(WD_BREAK.PAGE)
    emit_block(doc, tail)

    doc.add_paragraph().add_run().add_break(WD_BREAK.PAGE)
    pend = doc.add_paragraph(); pend.alignment = WD_ALIGN_PARAGRAPH.CENTER
    C._set_run(pend.add_run("（以上为源程序后连续 30 页，每页 50 行）"),
               cn=C.SONG, size=12, bold=True)

    path = os.path.join(OUT, "源代码鉴别材料_一句话待办.docx")
    doc.save(path)
    print("已生成：" + path)


if __name__ == "__main__":
    main()
