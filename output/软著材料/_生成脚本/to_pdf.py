# -*- coding: utf-8 -*-
"""用本机 Word 把 DOCX 批量导出为 PDF，并统计页数/行数。"""
import os, sys, glob
import win32com.client as win32

def main(dirpath):
    word = win32.Dispatch("Word.Application")
    word.Visible = False
    word.DisplayAlerts = 0
    rows = []
    files = [f for f in sorted(glob.glob(os.path.join(dirpath, "*.docx")))
             if not os.path.basename(f).startswith("~$")]
    for f in files:
        doc = None
        try:
            doc = word.Documents.Open(os.path.abspath(f), False, True, False)
            try:
                for toc in doc.TablesOfContents:
                    toc.Update()
                doc.Fields.Update()
            except Exception:
                pass
            pages = doc.ComputeStatistics(2)
            lines = doc.ComputeStatistics(1)
            pdf = os.path.splitext(f)[0] + ".pdf"
            if os.path.exists(pdf):
                os.remove(pdf)
            doc.ExportAsFixedFormat(pdf, 17)
            doc.Close(False)
            doc = None
            rows.append((os.path.basename(f), pages, lines))
            print("%-46s %3d 页  %6d 行" % (os.path.basename(f), pages, lines))
        except Exception as e:
            print("FAIL %s : %s" % (os.path.basename(f), e))
            if doc:
                try:
                    doc.Close(False)
                except Exception:
                    pass
    word.Quit()
    with open(os.path.join(dirpath, "_pages.txt"), "w", encoding="utf-8") as fh:
        for n, p, l in rows:
            fh.write("%s\t%d\t%d\n" % (n, p, l))
    print("done")

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else ".")
