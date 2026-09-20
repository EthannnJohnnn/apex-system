"""Rebuild Apex SVG masters. Requires fontTools; no raster tracing."""
from pathlib import Path
from xml.sax.saxutils import escape
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.boundsPen import BoundsPen

ROOT = Path(__file__).resolve().parents[1]
SVG = ROOT / 'svg'
SVG.mkdir(exist_ok=True)
font = instantiateVariableFont(TTFont(ROOT / 'source/Outfit-Variable.ttf'), {'wght': 800})
glyphs = font.getGlyphSet()
cmap = font.getBestCmap()
cursor = 0
parts = []
bounds = []
for char in 'Apex':
    glyph = glyphs[cmap[ord(char)]]
    pen = SVGPathPen(glyphs)
    glyph.draw(pen)
    bp = BoundsPen(glyphs)
    glyph.draw(bp)
    x0, y0, x1, y1 = bp.bounds
    bounds.append((cursor + x0, y0, cursor + x1, y1))
    parts.append(f'<path transform="translate({cursor} 0)" d="{pen.getCommands()}"/>')
    cursor += glyph.width - 20
x0 = min(b[0] for b in bounds)
y0 = min(b[1] for b in bounds)
x1 = max(b[2] for b in bounds)
y1 = max(b[3] for b in bounds)
scale = 110 / (y1-y0)
word_width = (x1-x0)*scale
logo_width = round(236 + word_width + 32)

# Apex adaptation of the approved retro direction: stepped A with a colored crossbar.
A = 'M120 0H180V30H210V60H240V90H270V342H210V222H60V342H0V90H30V60H60V30H120ZM90 90V162H180V90Z'
def mark(primary, accent):
    return f'<path fill="{primary}" fill-rule="evenodd" d="{A}"/><path fill="{accent}" d="M90 162H180V222H90Z"/>'

def word(color):
    return f'<g fill="{color}" transform="translate({236-x0*scale:.4f} {140+(y1+y0)*scale/2:.4f}) scale({scale:.6f} {-scale:.6f})">'+''.join(parts)+'</g>'

def wrap(body, width, height, title):
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}" role="img" aria-label="{escape(title)}"><title>{escape(title)}</title>{body}</svg>'

themes = {
    'light': ('#4C75A3', '#86ADD6', '#17212E'),
    'dark': ('#86ADD6', '#B8D4EF', '#F1F5F9'),
    'red-light': ('#B4232D', '#E77C83', '#17212E'),
    'red-dark': ('#F16B6B', '#FF9C9C', '#F1F5F9'),
    'black': ('#000000', '#000000', '#000000'),
    'white': ('#FFFFFF', '#FFFFFF', '#FFFFFF'),
}
logos = {}
for theme, (primary, accent, text) in themes.items():
    body = f'<g transform="translate(32 40) scale({200/342:.8f})">{mark(primary,accent)}</g>'+word(text)
    logos[theme] = body
    (SVG / f'apex-logo-{theme}.svg').write_text(wrap(body,logo_width,280,f'Apex logo - {theme}'),encoding='utf-8')
    (SVG / f'apex-mark-{theme}.svg').write_text(wrap(f'<g transform="translate(65 29)">{mark(primary,accent)}</g>',400,400,f'Apex symbol - {theme}'),encoding='utf-8')

fav = ROOT / 'favicon'
fav.mkdir(exist_ok=True)
# Simplified 16-unit optical drawing keeps the favicon's pixel steps sharp.
fc = 'M6 2H10V3H11V4H12V5H13V14H10V10H6V14H3V5H4V4H5V3H6ZM6 5V7H10V5Z'
def favicon_body(primary,accent):
    return f'<path fill="{primary}" fill-rule="evenodd" d="{fc}"/><path fill="{accent}" d="M6 8H10V10H6Z"/>'
for theme in ['light','dark','red-light','red-dark']:
    a,b,_ = themes[theme]
    (fav / f'favicon-{theme}.svg').write_text(wrap(favicon_body(a,b),16,16,'Apex'),encoding='utf-8')
adaptive = '<style>.a{fill:#4C75A3}.p{fill:#86ADD6}@media(prefers-color-scheme:dark){.a{fill:#86ADD6}.p{fill:#B8D4EF}}</style>'+f'<path class="a" fill-rule="evenodd" d="{fc}"/><path class="p" d="M6 8H10V10H6Z"/>'
(fav/'favicon.svg').write_text(wrap(adaptive,16,16,'Apex'),encoding='utf-8')
adaptive_red = '<style>.a{fill:#B4232D}.p{fill:#E77C83}@media(prefers-color-scheme:dark){.a{fill:#F16B6B}.p{fill:#FF9C9C}}</style>'+f'<path class="a" fill-rule="evenodd" d="{fc}"/><path class="p" d="M6 8H10V10H6Z"/>'
(fav/'favicon-red.svg').write_text(wrap(adaptive_red,16,16,'Apex'),encoding='utf-8')

preview = '<rect width="1400" height="1050" fill="#F5F7FA"/><rect y="525" width="1400" height="525" fill="#10151C"/>'
for theme, top, fg, note in [('light',0,'#17212E','LIGHT BACKGROUND'),('dark',525,'#F1F5F9','DARK BACKGROUND')]:
    preview += f'<text x="64" y="{top+60}" fill="{fg}" font-family="Arial" font-size="19" letter-spacing="3">APEX / {note}</text>'
    preview += f'<g transform="translate(70 {top+110}) scale({1050/logo_width:.5f})">{logos[theme]}</g>'
    a,b,_=themes[theme]
    preview += f'<g transform="translate(1200 {top+205}) scale(6)">{favicon_body(a,b)}</g>'
    preview += f'<text x="1200" y="{top+335}" fill="{fg}" font-family="Arial" font-size="16">Favicon detail</text>'
    preview += f'<text x="64" y="{top+480}" fill="{fg}" font-family="Arial" font-size="17">{a}  /  {b}  /  {fg}</text>'
(ROOT/'apex-preview.svg').write_text(wrap(preview,1400,1050,'Apex light and dark logo preview'),encoding='utf-8')
print(f'Created outlined logo SVGs ({logo_width} x 280), symbols, and adaptive favicon.')
