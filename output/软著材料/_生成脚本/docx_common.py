# -*- coding: utf-8 -*-
"""软著材料 DOCX 生成公共模块：分节、页眉页脚、页码、目录域、表格与图片。"""
import os
from docx import Document
from docx.shared import Pt, Cm, RGBColor, Emu
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

SONG = '宋体'
HEI = '黑体'
MONO = 'Consolas'
LATIN = 'Times New Roman'


def _set_run(run, cn=SONG, size=12, bold=False, latin=LATIN, color=None):
    run.font.name = latin
    run.font.size = Pt(size)
    run.bold = bold
    rpr = run._element.get_or_add_rPr()
    rfonts = rpr.find(qn('w:rFonts'))
    if rfonts is None:
        rfonts = OxmlElement('w:rFonts')
        rpr.append(rfonts)
    rfonts.set(qn('w:eastAsia'), cn)
    rfonts.set(qn('w:ascii'), latin)
    rfonts.set(qn('w:hAnsi'), latin)
    if color:
        run.font.color.rgb = color
    return run


def new_document():
    doc = Document()
    sec = doc.sections[0]
    sec.page_width = Cm(21.0)
    sec.page_height = Cm(29.7)
    sec.top_margin = Cm(2.54)
    sec.bottom_margin = Cm(2.54)
    sec.left_margin = Cm(3.0)
    sec.right_margin = Cm(3.0)
    sec.header_distance = Cm(1.5)
    sec.footer_distance = Cm(1.5)

    st = doc.styles['Normal']
    st.font.name = LATIN
    st.font.size = Pt(12)
    st.element.rPr.rFonts.set(qn('w:eastAsia'), SONG)
    pf = st.paragraph_format
    pf.line_spacing_rule = WD_LINE_SPACING.ONE_POINT_FIVE
    pf.space_before = Pt(0)
    pf.space_after = Pt(0)

    for lvl, size in ((1, 16), (2, 14), (3, 12)):
        s = doc.styles['Heading %d' % lvl]
        s.font.name = LATIN
        s.font.size = Pt(size)
        s.font.bold = True
        s.font.color.rgb = RGBColor(0, 0, 0)
        rpr = s.element.get_or_add_rPr()
        rfonts = rpr.find(qn('w:rFonts'))
        if rfonts is None:
            rfonts = OxmlElement('w:rFonts')
            rpr.append(rfonts)
        rfonts.set(qn('w:eastAsia'), HEI)
        s.paragraph_format.space_before = Pt(12 if lvl == 1 else 10)
        s.paragraph_format.space_after = Pt(8 if lvl == 1 else 6)
        s.paragraph_format.line_spacing_rule = WD_LINE_SPACING.ONE_POINT_FIVE
        s.paragraph_format.keep_with_next = True
    return doc


def _page_field(paragraph, fmt=None, start=None):
    """在段落中插入 PAGE 域。"""
    run = paragraph.add_run()
    _set_run(run, size=10.5)
    f1 = OxmlElement('w:fldChar'); f1.set(qn('w:fldCharType'), 'begin')
    it = OxmlElement('w:instrText'); it.set(qn('xml:space'), 'preserve'); it.text = ' PAGE '
    f2 = OxmlElement('w:fldChar'); f2.set(qn('w:fldCharType'), 'separate')
    t = OxmlElement('w:t'); t.text = '1'
    f3 = OxmlElement('w:fldChar'); f3.set(qn('w:fldCharType'), 'end')
    for e in (f1, it, f2, t, f3):
        run._r.append(e)


def _pg_num_type(section, fmt='decimal', start=1):
    sectPr = section._sectPr
    for old in sectPr.findall(qn('w:pgNumType')):
        sectPr.remove(old)
    el = OxmlElement('w:pgNumType')
    el.set(qn('w:fmt'), fmt)
    el.set(qn('w:start'), str(start))
    sectPr.append(el)


def add_cover(doc, doc_title, soft_name, version, date_str, extra=None):
    for _ in range(4):
        doc.add_paragraph()
    p = doc.add_paragraph(); p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    _set_run(p.add_run(doc_title), cn=HEI, size=36, bold=True)
    doc.add_paragraph()
    p = doc.add_paragraph(); p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    _set_run(p.add_run(soft_name), cn=HEI, size=22, bold=True)
    doc.add_paragraph()
    p = doc.add_paragraph(); p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    _set_run(p.add_run('版本 ' + version), cn=SONG, size=16)
    for _ in range(6):
        doc.add_paragraph()
    if extra:
        for line in extra:
            p = doc.add_paragraph(); p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            _set_run(p.add_run(line), cn=SONG, size=12)
    for _ in range(2):
        doc.add_paragraph()
    p = doc.add_paragraph(); p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    _set_run(p.add_run(date_str), cn=SONG, size=14)


def add_toc_section(doc, title='目  录'):
    sec = doc.add_section(WD_SECTION.NEW_PAGE)
    sec.top_margin = Cm(2.54); sec.bottom_margin = Cm(2.54)
    sec.left_margin = Cm(3.0); sec.right_margin = Cm(3.0)
    # 封面节保持无页眉页脚
    sec.header.is_linked_to_previous = False
    sec.footer.is_linked_to_previous = False
    _pg_num_type(sec, 'upperRoman', 1)
    fp = sec.footer.paragraphs[0]
    fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
    _page_field(fp)

    p = doc.add_paragraph(); p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    _set_run(p.add_run(title), cn=HEI, size=18, bold=True)
    p.paragraph_format.space_after = Pt(16)

    para = doc.add_paragraph()
    run = para.add_run()
    f1 = OxmlElement('w:fldChar'); f1.set(qn('w:fldCharType'), 'begin')
    it = OxmlElement('w:instrText'); it.set(qn('xml:space'), 'preserve')
    it.text = 'TOC \\o "1-2" \\h \\z \\u'
    f2 = OxmlElement('w:fldChar'); f2.set(qn('w:fldCharType'), 'separate')
    t = OxmlElement('w:t'); t.text = '（在 Word 中右键此处选择「更新域」，即可生成带页码的目录）'
    f3 = OxmlElement('w:fldChar'); f3.set(qn('w:fldCharType'), 'end')
    for e in (f1, it, f2, t, f3):
        run._r.append(e)
    return sec


def start_body(doc, header_text):
    sec = doc.add_section(WD_SECTION.NEW_PAGE)
    sec.top_margin = Cm(2.54); sec.bottom_margin = Cm(2.54)
    sec.left_margin = Cm(3.0); sec.right_margin = Cm(3.0)
    sec.header.is_linked_to_previous = False
    sec.footer.is_linked_to_previous = False
    _pg_num_type(sec, 'decimal', 1)

    hp = sec.header.paragraphs[0]
    hp.alignment = WD_ALIGN_PARAGRAPH.CENTER
    _set_run(hp.add_run(header_text), cn=SONG, size=9)
    fp = sec.footer.paragraphs[0]
    fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
    _page_field(fp)
    return sec


def h1(doc, text):
    p = doc.add_heading(text, level=1)
    return p


def h2(doc, text):
    return doc.add_heading(text, level=2)


def h3(doc, text):
    return doc.add_heading(text, level=3)


def p(doc, text, indent=True, bold=False, size=12, align=None, space_after=0):
    para = doc.add_paragraph()
    if indent:
        para.paragraph_format.first_line_indent = Pt(size * 2)
    if align == 'center':
        para.alignment = WD_ALIGN_PARAGRAPH.CENTER
    para.paragraph_format.space_after = Pt(space_after)
    _set_run(para.add_run(text), size=size, bold=bold)
    return para


def bullet(doc, text, level=0):
    para = doc.add_paragraph()
    para.paragraph_format.left_indent = Pt(24 + level * 18)
    para.paragraph_format.first_line_indent = Pt(-12)
    _set_run(para.add_run('· ' + text))
    return para


def numbered(doc, idx, text):
    para = doc.add_paragraph()
    para.paragraph_format.left_indent = Pt(30)
    para.paragraph_format.first_line_indent = Pt(-18)
    _set_run(para.add_run('%d）' % idx + text))
    return para


def caption(doc, text):
    para = doc.add_paragraph()
    para.alignment = WD_ALIGN_PARAGRAPH.CENTER
    para.paragraph_format.space_before = Pt(4)
    para.paragraph_format.space_after = Pt(10)
    _set_run(para.add_run(text), cn=SONG, size=10.5)
    return para


def figure(doc, png_path, cap, width_cm=15.0):
    if os.path.exists(png_path):
        para = doc.add_paragraph(); para.alignment = WD_ALIGN_PARAGRAPH.CENTER
        para.paragraph_format.space_before = Pt(8)
        para.add_run().add_picture(png_path, width=Cm(width_cm))
        caption(doc, cap)
    else:
        placeholder(doc, cap)


def placeholder(doc, cap, height_pt=150):
    """截图占位框（无实际图片时使用）。"""
    para = doc.add_paragraph()
    para.alignment = WD_ALIGN_PARAGRAPH.CENTER
    para.paragraph_format.space_before = Pt(8)
    _set_run(para.add_run('【此处插入界面截图】'), size=10.5,
             color=RGBColor(0x33, 0x33, 0x33))
    caption(doc, cap)


def table(doc, headers, rows, widths=None, cap=None, font_size=10.5):
    if cap:
        cp = doc.add_paragraph(); cp.alignment = WD_ALIGN_PARAGRAPH.CENTER
        cp.paragraph_format.space_before = Pt(8)
        cp.paragraph_format.space_after = Pt(4)
        _set_run(cp.add_run(cap), cn=SONG, size=10.5)
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = 'Table Grid'
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    hdr = t.rows[0].cells
    for i, htext in enumerate(headers):
        hdr[i].text = ''
        para = hdr[i].paragraphs[0]
        para.alignment = WD_ALIGN_PARAGRAPH.CENTER
        _set_run(para.add_run(htext), cn=HEI, size=font_size, bold=True)
        shd = OxmlElement('w:shd')
        shd.set(qn('w:fill'), 'F2F2F2')
        hdr[i]._tc.get_or_add_tcPr().append(shd)
    for row in rows:
        cells = t.add_row().cells
        for i, val in enumerate(row):
            cells[i].text = ''
            para = cells[i].paragraphs[0]
            para.paragraph_format.line_spacing_rule = WD_LINE_SPACING.SINGLE
            _set_run(para.add_run(str(val)), size=font_size)
    if widths:
        for r in t.rows:
            for i, w in enumerate(widths):
                if i < len(r.cells):
                    r.cells[i].width = Cm(w)
    if cap:
        para = doc.add_paragraph(); para.paragraph_format.space_after = Pt(6)
    else:
        doc.add_paragraph()
    return t


def add_appendix_note(doc, text):
    para = doc.add_paragraph()
    para.paragraph_format.space_before = Pt(12)
    _set_run(para.add_run(text), size=10.5, color=RGBColor(0x33, 0x33, 0x33))
    return para
