"""Build the Demo's original EPUB showcase books. Standard library only; no downloaded content."""
from pathlib import Path
from zipfile import ZipFile, ZipInfo, ZIP_STORED, ZIP_DEFLATED
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "app/src/main/assets/epubs"


def png():
    width, height = 240, 160
    rows = bytearray()
    for y in range(height):
        rows.append(0)
        for x in range(width):
            rows.extend((36 + x // 3, 77 + y // 2, 68 + (x + y) // 4))

    def chunk(name, data):
        return struct.pack(">I", len(data)) + name + data + struct.pack(">I", zlib.crc32(name + data) & 0xffffffff)

    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b"")


SVG = '''<svg xmlns="http://www.w3.org/2000/svg" width="600" height="360" viewBox="0 0 600 360">
<rect width="600" height="360" fill="#244d44"/><circle cx="470" cy="90" r="52" fill="#f2d487"/>
<path d="M0 300L170 80L350 300L470 170L600 320V360H0Z" fill="#789887"/>
<path d="M0 340L230 190L420 340L600 250V360H0Z" fill="#b8cbb6"/>
</svg>'''

CONTAINER = '''<?xml version="1.0" encoding="UTF-8"?>
<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
<rootfiles><rootfile full-path="EPUB/package.opf" media-type="application/oebps-package+xml"/></rootfiles></container>'''


def page(title, body, fixed=False):
    viewport = '<meta name="viewport" content="width=600,height=800"/>' if fixed else ''
    css = "fixed.css" if fixed else "book.css"
    return f'''<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="zh-CN" xml:lang="zh-CN">
<head><title>{title}</title>{viewport}<link rel="stylesheet" type="text/css" href="styles/{css}"/></head><body>{body}</body></html>'''


def build(name, title, chapters, fixed=False):
    manifest = '\n'.join(f'<item id="chapter{i}" href="chapter{i}.xhtml" media-type="application/xhtml+xml"/>' for i in range(1, len(chapters) + 1))
    spine = ''.join(f'<itemref idref="chapter{i}"/>' for i in range(1, len(chapters) + 1))
    nav = ''.join(f'<li><a href="chapter{i}.xhtml">{heading}</a></li>' for i, (heading, _) in enumerate(chapters, 1))
    layout = '<meta property="rendition:layout">pre-paginated</meta><meta property="rendition:spread">none</meta>' if fixed else '<meta property="rendition:layout">reflowable</meta>'
    package = f'''<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id" prefix="rendition: http://www.idpf.org/vocab/rendition/#">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:identifier id="book-id">urn:iread:sample:{name}:1</dc:identifier><dc:title>{title}</dc:title><dc:language>zh-CN</dc:language>
<dc:creator>iRead 示例</dc:creator><meta property="dcterms:modified">2026-10-18T00:00:00Z</meta>{layout}</metadata>
<manifest>{manifest}<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
<item id="css" href="styles/{'fixed.css' if fixed else 'book.css'}" media-type="text/css"/>
<item id="landscape" href="images/landscape.svg" media-type="image/svg+xml"/>
<item id="raster" href="images/colors.png" media-type="image/png"/></manifest><spine>{spine}</spine></package>'''
    entries = {
        "mimetype": b"application/epub+zip", "META-INF/container.xml": CONTAINER,
        "EPUB/package.opf": package,
        "EPUB/nav.xhtml": page("目录", f'<nav epub:type="toc" id="toc"><h1>目录</h1><ol>{nav}</ol></nav>', fixed),
        "EPUB/images/landscape.svg": SVG, "EPUB/images/colors.png": png(),
    }
    if fixed:
        entries["EPUB/styles/fixed.css"] = '''html,body{width:600px;height:800px;margin:0;padding:0;background:#f6f4ed;color:#244d44;overflow:hidden}
body{position:relative;font-family:serif}.art{position:absolute;left:40px;top:120px;width:520px;height:312px}
h1{position:absolute;left:40px;top:30px;font-size:36px}.caption{position:absolute;left:40px;top:470px;width:520px;font-size:24px;line-height:1.6}
.label{position:absolute;right:40px;bottom:35px;font-size:20px}'''
    else:
        entries["EPUB/styles/book.css"] = '''body{font-family:serif;line-height:1.75;color:#244d44}h1{border-bottom:3px solid #b99454;padding-bottom:.3em}
p{text-indent:2em}.lead{font-size:1.15em;text-indent:0}.scene{width:100%;height:auto}.thumb{float:right;width:35%;margin:0 0 1em 1em}
figure{margin:1em 0}figcaption{font-size:.85em;text-align:center}blockquote{border-left:.2em solid #b99454;margin:1em;padding:.5em;background:#f6f4ed}
table{border-collapse:collapse;width:100%}td,th{border:1px solid #789887;padding:.4em}a{color:#3759a0}.note{font-size:.85em}'''
    for i, (heading, body) in enumerate(chapters, 1):
        entries[f"EPUB/chapter{i}.xhtml"] = page(heading, body, fixed)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    with ZipFile(OUTPUT / f"{name}.epub", "w") as archive:
        for path, payload in entries.items():
            info = ZipInfo(path, date_time=(2026, 10, 18, 0, 0, 0))
            info.compress_type = ZIP_STORED if path == "mimetype" else ZIP_DEFLATED
            archive.writestr(info, payload.encode("utf-8") if isinstance(payload, str) else payload)


build("illustrated-flow", "山间来信 · EPUB 图文示例", [
    ("一 · 原书图文", '''<h1 id="start">山间来信</h1><p class="lead">这本小书展示 EPUB 原书样式、图片与链接。文字可随屏幕重新排版。</p>
<figure><img class="scene" src="images/landscape.svg" alt="青绿色山峦与金色太阳"/><figcaption>SVG 矢量插图，随正文宽度缩放。</figcaption></figure>
<p><img class="thumb" src="images/colors.png" alt="绿色渐变的 PNG 图片"/>清晨，窗边落下一封信。信纸上写着：沿着山路前行，水声会带你找到答案。这里的 PNG 图片使用原书 CSS 向右浮动，文字环绕在图片旁边。</p>
<p>这是<strong>粗体</strong>、<em>斜体</em>与<ruby>山间<rt>shān jiān</rt></ruby>。请访问<a href="chapter2.xhtml#letter">第二章的信件</a>，或阅读脚注<a id="note-ref" epub:type="noteref" href="#note-1">[1]</a>。</p>
<blockquote>排版、图片和正文属于同一份 XHTML 文档。</blockquote>
<aside id="note-1" epub:type="footnote" class="note"><p>[1] 这是书内脚注内容。<a href="#note-ref">返回正文</a></p></aside>'''),
    ("二 · 信件与目录", '''<h1 id="letter">来自山中的信</h1><p>你已经通过一个跨章节片段链接抵达这里。</p>
<table><thead><tr><th>资源</th><th>用途</th></tr></thead><tbody><tr><td>XHTML 与 CSS</td><td>正文结构和原书排版</td></tr><tr><td>PNG / SVG</td><td>栅格图片和矢量插图</td></tr></tbody></table>
<p><a href="chapter1.xhtml#start">回到第一章</a>，或由宿主打开<a href="https://readium.org/">Readium 官方网站</a>。</p>
<p>本书内容与插图在仓库内生成，可离线阅读；它不是完整 EPUB 一致性验收套件。</p>'''),
])
build("fixed-layout", "山间画册 · EPUB 固定版式示例", [
    ("山间画册", '<h1>山间画册</h1><img class="art" src="images/landscape.svg" alt="青山与太阳"/><p class="caption">这张画页固定为 600 × 800。插图与文字保持作者指定的位置，不拆成独立文字块。</p><span class="label">01 / 固定版式</span>'),
    ("第二张画页", '<h1>另一种颜色</h1><img class="art" src="images/colors.png" alt="绿色渐变"/><p class="caption">固定版式由 EPUB 引擎缩放到可用区域。使用下一页、上一页或目录在画页间导航。</p><span class="label">02 / 固定版式</span>'),
], fixed=True)
print(OUTPUT)
