import os, re, subprocess, sys, tempfile, glob

EDGE = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"

def render(svg_path, png_path, scale=2):
    src = open(svg_path, "r", encoding="utf-8").read()
    m = re.search(r'width="(\d+)"\s+height="(\d+)"', src)
    w, h = int(m.group(1)), int(m.group(2))
    body = re.sub(r'<\?xml[^>]*\?>', '', src)
    html = ('<!DOCTYPE html><html><head><meta charset="utf-8">'
            '<style>html,body{margin:0;padding:0;background:#ffffff;}</style>'
            '</head><body>' + body + '</body></html>')
    fd, tmp = tempfile.mkstemp(suffix=".html")
    os.close(fd)
    with open(tmp, "w", encoding="utf-8") as f:
        f.write(html)
    url = "file:///" + tmp.replace("\\", "/")
    cmd = [EDGE, "--headless=new", "--disable-gpu", "--hide-scrollbars",
           "--force-device-scale-factor=%d" % scale,
           "--screenshot=" + png_path,
           "--window-size=%d,%d" % (w, h),
           "--virtual-time-budget=4000", url]
    subprocess.run(cmd, capture_output=True, timeout=90)
    os.remove(tmp)
    ok = os.path.exists(png_path) and os.path.getsize(png_path) > 2000
    print(("OK  " if ok else "FAIL") + "  " + os.path.basename(png_path) +
          ("" if not ok else "  %d bytes" % os.path.getsize(png_path)))
    return ok

if __name__ == "__main__":
    d = sys.argv[1]
    for s in sorted(glob.glob(os.path.join(d, "*.svg"))):
        p = s[:-4] + ".png"
        render(s, p)
