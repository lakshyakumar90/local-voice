"""Rebuild deterministic brand vectors, exports and Android icons. Requires Pillow/fontTools.
The generated marketing illustration is maintained separately; no app screenshots are fabricated.
"""
from pathlib import Path
import json
from PIL import Image, ImageDraw, ImageFont, ImageOps
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'assets/brand'
for folder in ['logos', 'icons', 'illustrations', 'social', 'motion', 'previews']:
    (OUT / folder).mkdir(parents=True, exist_ok=True)
TEAL, INK, PAPER, LIME = '#087F75', '#123C38', '#F4FAF8', '#DAF975'
FONT = OUT / 'fonts/Manrope.ttf'
tt = TTFont(FONT)
glyphs = tt.getGlyphSet(location={'wght': 650})
cmap = tt.getBestCmap()
units = tt['head'].unitsPerEm

def word_paths(text, x, baseline, size, color):
    result = []
    scale = size / units
    for char in text:
        glyph = glyphs[cmap[ord(char)]]
        pen = SVGPathPen(glyphs)
        glyph.draw(TransformPen(pen, (scale, 0, 0, -scale, x, baseline)))
        result.append(f'<path fill="{color}" d="{pen.getCommands()}"/>')
        x += glyph.width * scale
    return ''.join(result)

def svg(w, h, content, title):
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}" role="img"><title>{title}</title>{content}</svg>'

def save_svg(name, w, h, body, title):
    (OUT / name).write_text(svg(w, h, body, title), encoding='utf-8')

# Stable mark: five pill strokes. One low-amplitude final note in citron on dark surfaces.
HEIGHTS = [28, 52, 80, 52, 28]
def mark_svg(x=0, y=0, scale=1, color=TEAL, accent=None):
    bars = ''.join(f'<rect x="{14+i*18}" y="{(108-h)/2:g}" width="12" height="{h}" rx="6" fill="{accent if accent and i==4 else color}"/>' for i,h in enumerate(HEIGHTS))
    return f'<g transform="translate({x} {y}) scale({scale})">{bars}</g>'

for name, color in [('teal', TEAL), ('ink', INK), ('white', '#FFFFFF')]:
    save_svg(f'logos/mark-{name}.svg',108,108,mark_svg(color=color),f'Local Voice {name} mark')
    save_svg(f'logos/wordmark-{name}.svg',460,112,mark_svg(0,2,.85,color)+word_paths('Local Voice',116,74,48,color),'Local Voice')
save_svg('icons/app-icon.svg',512,512,f'<rect width="512" height="512" rx="112" fill="{TEAL}"/>'+mark_svg(81.92,81.92,512*.68/108,'#FFFFFF',LIME),'Local Voice app icon')
save_svg('icons/favicon.svg',108,108,f'<rect width="108" height="108" rx="24" fill="{TEAL}"/>'+mark_svg(color='#FFFFFF',accent=LIME),'Local Voice')

def font(size):
    f=ImageFont.truetype(str(FONT),size)
    try: f.set_variation_by_axes([650])
    except (OSError, ValueError): pass
    return f

def draw_mark(draw, x, y, scale, color=TEAL, accent=None):
    for i,h in enumerate(HEIGHTS):
        draw.rounded_rectangle((x+(14+i*18)*scale,y+(108-h)/2*scale,x+(26+i*18)*scale,y+(108+h)/2*scale),radius=6*scale,fill=accent if accent and i==4 else color)

def icon(size, legacy=False):
    im=Image.new('RGBA',(size*4,size*4),(0,0,0,0)); d=ImageDraw.Draw(im)
    if legacy:
        d.ellipse((0,0,size*4-1,size*4-1),fill=TEAL)
    else:
        d.rounded_rectangle((0,0,size*4-1,size*4-1),radius=size*.22*4,fill=TEAL)
    # Android adaptive safe zone: all foreground geometry inside central 66/108.
    draw_mark(d,size*4*.16,size*4*.16,size*4*.68/108,'white',LIME)
    return im.resize((size,size),Image.Resampling.LANCZOS)

for size in [32,48,180,192,512,1024]:
    icon(size).save(OUT/f'icons/app-icon-{size}.png')
icon(32).save(OUT/'icons/favicon.ico',sizes=[(16,16),(32,32)])
for name,color in [('teal',TEAL),('white','#FFFFFF'),('ink',INK)]:
    im=Image.new('RGBA',(920,224));d=ImageDraw.Draw(im)
    draw_mark(d,0,4,1.7,color)
    d.text((232,31),'Local Voice',font=font(96),fill=color)
    im.save(OUT/f'logos/wordmark-{name}.png')

# Original explanatory illustrations; these are diagrams, not screenshots.
save_svg('illustrations/speech-to-text.svg',960,560,
    f'<rect width="960" height="560" rx="32" fill="{PAPER}"/>'+
    mark_svg(76,140,2.4)+
    f'<path d="M390 280h104m-20-20 20 20-20 20" fill="none" stroke="{TEAL}" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>'+
    '<rect x="552" y="100" width="324" height="360" rx="24" fill="white" stroke="#C5DDD7" stroke-width="2"/>'+
    ''.join(f'<rect x="584" y="{164+i*64}" width="{[220,176,204,144][i]}" height="14" rx="7" fill="'+(TEAL if i==0 else '#B8D9D1')+'"/>' for i in range(4)),
    'Diagram: speech becomes organized text')
save_svg('illustrations/local-processing.svg',960,560,
    f'<rect width="960" height="560" rx="32" fill="{PAPER}"/>'+
    f'<rect x="330" y="54" width="300" height="452" rx="42" fill="white" stroke="{INK}" stroke-width="8"/>'+
    f'<rect x="423" y="74" width="114" height="12" rx="6" fill="{INK}"/>'+
    mark_svg(372,140,2,TEAL)+
    f'<path d="M430 355h100m-80 28h60" stroke="{TEAL}" stroke-width="12" stroke-linecap="round"/>'+
    f'<circle cx="661" cy="392" r="56" fill="{LIME}"/><path d="m638 390 17 17 29-34" fill="none" stroke="{INK}" stroke-width="8" stroke-linecap="round" stroke-linejoin="round"/>',
    'Diagram: audio processing stays on the phone')
save_svg('illustrations/listening-wave.svg',640,240,mark_svg(104,-96,4,TEAL),'Listening waveform')

# A standalone looping asset, not a web page or a website implementation.
bars=''.join(f'<rect class="b b{i}" x="{14+i*18}" y="{(108-h)/2:g}" width="12" height="{h}" rx="6" fill="{TEAL}"/>' for i,h in enumerate(HEIGHTS))
style='''<style>.b{transform-box:fill-box;transform-origin:center;animation:listen 1.2s ease-in-out infinite}.b1{animation-delay:-.18s}.b2{animation-delay:-.36s}.b3{animation-delay:-.12s}.b4{animation-delay:-.28s}@keyframes listen{0%,100%{transform:scaleY(.5)}50%{transform:scaleY(1)}}@media(prefers-reduced-motion:reduce){.b{animation:none}}</style>'''
save_svg('motion/listening-loop.svg',108,108,style+bars,'Illustrative listening animation; not live microphone input')
frames=[]
import math
for frame in range(24):
    im=Image.new('RGB',(432,216),PAPER);d=ImageDraw.Draw(im)
    for i,h in enumerate(HEIGHTS):
        hh=h*(.5+.5*(math.sin(frame/24*math.tau-i*.8)+1)/2)*1.9
        d.rounded_rectangle((128+i*36,108-hh/2,152+i*36,108+hh/2),radius=12,fill=TEAL)
    frames.append(im)
frames[0].save(OUT/'motion/listening-loop.webp',save_all=True,append_images=frames[1:],duration=50,loop=0,lossless=True)

hero=Image.open(OUT/'illustrations/voice-material.png').convert('RGB')
hero.save(OUT/'illustrations/voice-material.webp',quality=88,method=6)
hero.resize((960,640),Image.Resampling.LANCZOS).save(OUT/'illustrations/voice-material-mobile.webp',quality=86,method=6)
og=Image.new('RGB',(1200,630),PAPER)
og.paste(ImageOps.fit(hero,(680,630)),(520,0))
d=ImageDraw.Draw(og);draw_mark(d,48,36,.62)
d.text((132,47),'Local Voice',font=font(28),fill=INK)
d.text((56,192),'Your voice.\nYour words.\nOn your phone.',font=font(52),fill=INK,spacing=5)
d.text((58,433),'Offline Android dictation',font=font(24),fill=TEAL)
og.save(OUT/'social/open-graph.png')
feature=Image.new('RGB',(1024,500),TEAL);d=ImageDraw.Draw(feature)
draw_mark(d,650,100,2.2,'white',LIME)
d.text((56,74),'Local Voice',font=font(42),fill='white')
d.text((56,190),'Speak freely.\nKeep it local.',font=font(54),fill='white',spacing=5)
feature.save(OUT/'social/feature-graphic.png')

board=Image.new('RGB',(1600,1100),PAPER);d=ImageDraw.Draw(board)
d.text((64,40),'Local Voice / Identity kit',font=font(38),fill=INK)
d.text((64,100),'Voice becomes words. Everything stays close.',font=font(22),fill=TEAL)
board.paste(icon(240),(64,190),icon(240))
word=Image.open(OUT/'logos/wordmark-ink.png');word.thumbnail((670,164))
board.paste(word,(370,222),word)
for i,(name,color) in enumerate([('Porcelain',PAPER),('Forest',INK),('Teal',TEAL),('Citron',LIME),('Mint','#C5DDD7')]):
    x=64+i*302
    d.rounded_rectangle((x,486,x+270,586),radius=14,fill=color,outline='#C5DDD7')
    d.text((x,603),name,font=font(19),fill=INK);d.text((x,634),color,font=font(17),fill=TEAL)
board.paste(ImageOps.fit(hero,(720,330)),(64,710))
d.text((850,730),'Manrope',font=font(44),fill=INK)
d.text((850,805),'Clear. Human. Local.',font=font(28),fill=TEAL)
d.text((850,870),'Five strokes for listening.\nOne small bright note.\nMotion follows an action.',font=font(22),fill=INK,spacing=12)
board.save(OUT/'previews/brand-board.png')

# Android API 26+ adaptive, round and monochrome icons, plus legacy density exports.
res=ROOT/'app/src/main/res'
for density,size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    dest=res/f'mipmap-{density}';dest.mkdir(exist_ok=True)
    icon(size).save(dest/'ic_launcher.png');icon(size,legacy=True).save(dest/'ic_launcher_round.png')
dest=res/'mipmap-anydpi-v26';dest.mkdir(exist_ok=True)
adaptive='''<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@color/launcher_background"/><foreground android:drawable="@drawable/ic_launcher_foreground"/><monochrome android:drawable="@drawable/ic_launcher_monochrome"/></adaptive-icon>'''
for name in ['ic_launcher','ic_launcher_round']: (dest/f'{name}.xml').write_text(adaptive,encoding='utf-8')
dest=res/'mipmap-anydpi-v33'
for name in ['ic_launcher','ic_launcher_round']:
    (dest/f'{name}.xml').unlink(missing_ok=True)
if dest.exists(): dest.rmdir()
for name,mono in [('foreground',False),('monochrome',True)]:
    paths=[]
    for i,h in enumerate(HEIGHTS):
        # Safe-zone geometry, same 0.68 scale and inset used for raster exports.
        x=17.28+(14+i*18)*.68;y=17.28+(108-h)/2*.68;w=12*.68;hh=h*.68;r=w/2
        path=f'M{x+r},{y} a{r},{r} 0 0 1 {r},{r} v{hh-2*r} a{r},{r} 0 0 1 {-r},{r} a{r},{r} 0 0 1 {-r},{-r} v{-hh+2*r} a{r},{r} 0 0 1 {r},{-r} Z'
        color='#FFFFFF' if mono or i!=4 else LIME
        paths.append(f'<path android:fillColor="{color}" android:pathData="{path}"/>')
    (res/f'drawable/ic_launcher_{name}.xml').write_text('<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">'+''.join(paths)+'</vector>',encoding='utf-8')
(res/'values/launcher_colors.xml').write_text(f'<resources><color name="launcher_background">{TEAL}</color></resources>',encoding='utf-8')
tokens={'color':{'paper':PAPER,'ink':INK,'teal':TEAL,'citron':LIME,'mint':'#C5DDD7','white':'#FFFFFF'},'font':{'family':'Manrope','license':'SIL Open Font License 1.1','bodyWeight':450,'headingWeight':650},'spacing':[4,8,12,16,24,32,48,64,96,128],'radius':{'control':12,'illustration':24,'phone':40},'motion':{'feedbackMs':160,'panelMs':240,'demoMs':1200,'reducedMotion':'static final state'}}
(OUT/'tokens.json').write_text(json.dumps(tokens,indent=2),encoding='utf-8')
print('Brand vectors, PNG/WebP exports, motion assets, board and Android launcher icons generated.')
