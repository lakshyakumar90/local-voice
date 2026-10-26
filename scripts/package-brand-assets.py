"""Produce review-only boards and an integrity manifest; never package signing material."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageOps
from fontTools.ttLib import TTFont
import hashlib, json

ROOT=Path(__file__).resolve().parents[1]
B=ROOT/'assets/brand'
PAPER,INK,TEAL,LIME='#F4FAF8','#123C38','#087F75','#DAF975'
def font(size):
    f=ImageFont.truetype(str(B/'fonts/Manrope.ttf'),size)
    f.set_variation_by_axes([650])
    return f
tt=TTFont(B/'fonts/Manrope.ttf',recalcTimestamp=False);tt.flavor='woff2';tt.save(B/'fonts/Manrope.woff2')
board=Image.new('RGB',(1600,1040),PAPER);d=ImageDraw.Draw(board)
d.text((48,32),'Visual research / references, not production artwork',font=font(32),fill=INK)
refs=[('voiceflow-focuslab.png','Voiceflow / Focus Lab + Odi','Identity across surfaces; simple, scalable symbol.'),('slab-voice-generator.jpg','Synesthesia / Aryo Pamungkas, SLAB','Sound as the main image; avoid its neon/grid style.'),('zajno-speaker.jpg','Loudspeaker / Daniel, Zajno','Dimensional audio imagery; retain our light palette.')]
for i,(file,title,note) in enumerate(refs):
    x=48+i*510
    image=Image.open(B/'references'/file).convert('RGB')
    thumb=ImageOps.contain(image,(478,390))
    board.paste(thumb,(x,120+(390-thumb.height)//2))
    d.text((x,550),title,font=font(20),fill=INK)
    # Simple explicit wrapping to keep attribution legible.
    words=note.split();line='';lines=[]
    for word in words:
        if len(line+' '+word)>39: lines.append(line);line=word
        else: line=(line+' '+word).strip()
    lines.append(line)
    d.multiline_text((x,590),'\n'.join(lines),font=font(19),fill=TEAL,spacing=8)
d.text((48,740),'Motion reference: Voxworks / David Akanbi',font=font(26),fill=INK)
d.multiline_text((48,792),'Creator walkthrough describes idle / hover states and reactive waveform motion.\nWritten behavior reviewed; video not played. Adopt the state logic, not the coin or logo.\nFull URLs, credits and review boundaries are in references/REFERENCES.md.',font=font(22),fill=INK,spacing=12)
d.text((48,972),'Third-party works remain owned by their creators. Do not ship this board on the website.',font=font(19),fill=TEAL)
board.save(B/'previews/reference-board.png')

# Review-only static website art direction, deliberately not a screenshot of a built site/app.
im=Image.new('RGB',(1800,1480),'#E0ECE8');d=ImageDraw.Draw(im)
d.text((48,26),'Local Voice / future website layout study',font=font(32),fill=INK)
d.text((48,76),'Static design concept. No website or app screenshot is represented here.',font=font(20),fill=TEAL)
d.rounded_rectangle((40,140,1310,1410),radius=24,fill=PAPER)
logo=Image.open(B/'logos/wordmark-ink.png');logo.thumbnail((220,60));im.paste(logo,(84,180),logo)
d.text((680,194),'How it works     Models     Guide',font=font(18),fill=INK)
d.rounded_rectangle((1090,180,1260,228),radius=12,fill=TEAL)
d.text((1109,192),'Download APK',font=font(18),fill='white')
hero=Image.open(B/'illustrations/voice-material.png').convert('RGB')
im.paste(ImageOps.fit(hero,(635,430)),(650,285))
d.multiline_text((88,306),'Your voice.\nYour words.\nOn your phone.',font=font(57),fill=INK,spacing=4)
d.multiline_text((90,544),'Offline Android dictation.\nDownload a model, then speak locally.',font=font(20),fill=TEAL,spacing=7)
d.rounded_rectangle((88,625,310,683),radius=12,fill=TEAL)
d.text((111,641),'Download APK',font=font(23),fill='white')
d.text((337,644),'Read the setup guide',font=font(19),fill=INK)
d.line((88,758,1260,758),fill='#C5DDD7',width=2)
d.text((88,804),'Speak. Transcribe. Keep writing.',font=font(36),fill=INK)
d.text((90,865),'An example of speech becoming a formatted list',font=font(21),fill=TEAL)
d.rounded_rectangle((88,928,570,1140),radius=18,fill='#DFEEE9')
d.multiline_text((118,964),'“I want these features:\nUI, front end, back end,\ndatabase.”',font=font(25),fill=INK,spacing=10)
d.rounded_rectangle((662,928,1260,1140),radius=18,fill='white',outline='#C5DDD7',width=2)
d.multiline_text((695,958),'I want these features:\n• UI\n• Front end\n• Back end\n• Database.',font=font(21),fill=INK,spacing=5)
d.text((88,1200),'Choose your local model',font=font(28),fill=INK)
d.text((694,1200),'Understand your permissions',font=font(28),fill=INK)
d.text((90,1260),'Models and storage explained',font=font(19),fill=TEAL)
d.text((696,1260),'Microphone, field mic and insertion',font=font(19),fill=TEAL)
d.text((88,1350),'Guide     Releases     Privacy     Terms     Contact',font=font(19),fill=INK)

# Narrow composition demonstrates order rather than scaling the desktop page down.
d.rounded_rectangle((1360,140,1760,1410),radius=24,fill=PAPER)
logo=Image.open(B/'logos/wordmark-ink.png');logo.thumbnail((190,50));im.paste(logo,(1388,179),logo)
d.text((1684,191),'Menu',font=font(16),fill=INK)
d.multiline_text((1388,274),'Your voice.\nYour words.\nOn your phone.',font=font(39),fill=INK,spacing=4)
d.multiline_text((1388,448),'Offline Android dictation.\nDownload a model,\nthen speak locally.',font=font(20),fill=TEAL,spacing=7)
d.rounded_rectangle((1388,566,1732,626),radius=12,fill=TEAL)
d.text((1467,584),'Download APK',font=font(22),fill='white')
im.paste(ImageOps.fit(hero,(344,280)),(1388,674))
d.multiline_text((1388,1004),'Speak. Transcribe.\nKeep writing.',font=font(29),fill=INK,spacing=6)
d.multiline_text((1388,1122),'Setup guide\nLocal models\nPermissions\nPrivacy and terms',font=font(21),fill=TEAL,spacing=17)
im.save(B/'previews/website-layout-study.png')

maskboard=Image.new('RGB',(1200,480),PAPER);d=ImageDraw.Draw(maskboard)
d.text((48,30),'Launcher icon / shape and scale review',font=font(30),fill=INK)
for x,file,label in [(64,'ic_launcher.png','Rounded square'),(368,'ic_launcher_round.png','Circle')]:
    sample=Image.open(ROOT/'app/src/main/res/mipmap-xxxhdpi'/file).resize((192,192),Image.Resampling.LANCZOS)
    maskboard.paste(sample,(x,130),sample)
    d.text((x,350),label,font=font(21),fill=INK)
for i,size in enumerate([24,32,48,64]):
    sample=Image.open(B/'icons/app-icon-192.png').resize((size,size),Image.Resampling.LANCZOS)
    x=690+i*112
    maskboard.paste(sample,(x,202-size//2),sample)
    d.text((x,275),str(size)+' px',font=font(17),fill=TEAL)
d.text((680,350),'Small-size raster review',font=font(21),fill=INK)
d.text((48,420),'Adaptive and monochrome XML are supplied separately; phone rendering is not verified.',font=font(19),fill=TEAL)
maskboard.save(B/'previews/launcher-masks.png')

manifest=[]
for p in B.rglob('*'):
    if p.is_file() and p.suffix in ['.svg','.md','.json','.txt']:
        p.write_text(p.read_text(encoding='utf-8'),encoding='utf-8',newline='\n')
for p in sorted(B.rglob('*')):
    if not p.is_file() or p.name=='manifest.json': continue
    data=p.read_bytes()
    entry={'path':p.relative_to(B).as_posix(),'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest(),'production':not p.relative_to(B).parts[0] in ['references','previews']}
    if p.suffix.lower() in ['.png','.jpg','.webp','.ico']:
        with Image.open(p) as img: entry.update(width=img.width,height=img.height,frames=getattr(img,'n_frames',1))
    manifest.append(entry)
(B/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8',newline='\n')
print(f'Created font export, review boards and manifest for {len(manifest)} files.')
