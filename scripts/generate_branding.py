"""Regenerate committed Android/iOS assets using the pinned engine Pillow dependency."""
from pathlib import Path
from PIL import Image, ImageOps, ImageDraw
import json, shutil
r=Path(__file__).resolve().parents[1]
source=r/'branding/studyfrog-original.png'
im=Image.open(source).convert('RGB')
brand=r/'branding';brand.mkdir(exist_ok=True)
assert im.size==(1536,1024), "Review crop coordinates before using different artwork"
im.resize((1200,800),Image.Resampling.LANCZOS).save(brand/'studyfrog-banner.webp',quality=90)
# Deterministic crops/resizing only; preserve the supplied illustration.
icon=im.crop((485,0,1125,640))
icon.resize((1024,1024),Image.Resampling.LANCZOS).save(brand/'studyfrog-icon-1024.png')
res=r/'app/src/main/res'
(res/'drawable-nodpi').mkdir(exist_ok=True)
im.resize((960,640),Image.Resampling.LANCZOS).save(res/'drawable-nodpi/sf_brand_banner.webp',quality=88)
icon.resize((256,256),Image.Resampling.LANCZOS).save(res/'drawable-nodpi/sf_brand_avatar.png')
for density,scale in [('mdpi',1),('hdpi',1.5),('xhdpi',2),('xxhdpi',3),('xxxhdpi',4)]:
 d=res/('mipmap-'+density);d.mkdir(exist_ok=True)
 size=int(48*scale);legacy=icon.resize((size,size),Image.Resampling.LANCZOS);legacy.save(d/'ic_launcher.png')
 mask=Image.new('L',(size,size),0);ImageDraw.Draw(mask).ellipse((0,0,size-1,size-1),fill=255)
 round_icon=legacy.convert('RGBA');round_icon.putalpha(mask);round_icon.save(d/'ic_launcher_round.png')
 sz=int(108*scale);fg=Image.new('RGBA',(sz,sz),(0,0,0,0));inner=int(sz*.74)
 fg.alpha_composite(icon.resize((inner,inner),Image.Resampling.LANCZOS).convert('RGBA'),((sz-inner)//2,(sz-inner)//2))
 fg.save(d/'ic_launcher_foreground.png')
 v=res/'mipmap-anydpi-v26';v.mkdir(exist_ok=True)
 for name in ['ic_launcher','ic_launcher_round']:
  (v/(name+'.xml')).write_text('''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/sf_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
''')
(res/'values/sf_colors.xml').write_text('''<resources><color name="sf_launcher_background">#03101D</color></resources>\n''')
# Keep the exclusive density-test fixture while updating its artwork.
icon.resize((192,192),Image.Resampling.LANCZOS).save(res/'drawable-xxhdpi/ic_xxhdpi.png')
asset=r/'ios/StudyForge/Assets.xcassets';asset.mkdir(parents=True,exist_ok=True)
(asset/'Contents.json').write_text(json.dumps({'info':{'author':'xcode','version':1}},indent=2))
appicon=asset/'AppIcon.appiconset';appicon.mkdir(exist_ok=True)
entries=[]
for idiom,size,scales in [('iphone',20,[2,3]),('iphone',29,[2,3]),('iphone',40,[2,3]),('iphone',60,[2,3]),('ipad',20,[1,2]),('ipad',29,[1,2]),('ipad',40,[1,2]),('ipad',76,[1,2]),('ipad',83.5,[2]),('ios-marketing',1024,[1])]:
 for scale in scales:
  pixels=round(size*scale);name=f'icon-{idiom}-{size:g}@{scale}x.png'
  icon.resize((pixels,pixels),Image.Resampling.LANCZOS).save(appicon/name)
  entries.append({'idiom':idiom,'size':f'{size:g}x{size:g}','scale':f'{scale}x','filename':name})
(appicon/'Contents.json').write_text(json.dumps({'images':entries,'info':{'author':'xcode','version':1}},indent=2))
images=asset/'StudyFrogBrand.imageset';images.mkdir(exist_ok=True)
im.resize((768,512),Image.Resampling.LANCZOS).save(images/'studyfrog.png')
(images/'Contents.json').write_text(json.dumps({'images':[{'idiom':'universal','filename':'studyfrog.png','scale':'1x'}],'info':{'author':'xcode','version':1}},indent=2))
