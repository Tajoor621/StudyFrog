"""Read-only integration checks. Run from any directory with Python 3; requires git."""
from collections import defaultdict
from pathlib import Path
import json
import re
import struct
import subprocess
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]

def audit():
    checks=[]
    def check(condition,name):
        if not condition: raise AssertionError(name)
        checks.append(name)
    names=sorted(set(subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard'],cwd=ROOT,text=True).splitlines()))
    paths=[ROOT/name for name in names if (ROOT/name).is_file()]
    sources={}
    for path in paths:
        try: content=path.read_text(encoding='utf-8')
        except UnicodeError: continue
        sources[path.relative_to(ROOT).as_posix()]=content
        if path.suffix in {'.kt','.kts','.cpp','.h','.swift','.py','.json','.xml','.yml','.yaml','.cmake'}:
            check(not re.search(r'^(?:<<<<<<< |>>>>>>> |={7}$)',content,re.M),'No merge markers: '+str(path.relative_to(ROOT)))
        check(not re.search(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',content),'No private-key payload: '+str(path.relative_to(ROOT)))
    check(not any(p.suffix in {'.jks','.keystore'} or p.name in {'google-services.json','.env'} for p in paths),'No tracked signing/configuration secrets')
    declarations=defaultdict(list)
    for name,content in sources.items():
        if not name.startswith('app/src/main/') or not name.endswith('.kt'): continue
        package=re.search(r'^package ([\w.]+)',content,re.M)
        if not package: continue
        for declaration in re.finditer(r'^(?:(?:data|enum|sealed|abstract|internal|open)\s+)*(?:class|object|interface)\s+(\w+)',content,re.M):
            declarations[package[1]+'.'+declaration[1]].append(name)
    duplicates={key:files for key,files in declarations.items() if len(files)>1}
    check(not duplicates,'No duplicate top-level Kotlin type identities')
    manifest=ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot()
    android='{http://schemas.android.com/apk/res/android}'
    app=manifest.find('application')
    for element in [app,*list(app)]:
        name=element.get(android+'name','')
        if name.startswith('com.scenio.mth.'):
            check(name in declarations,'Manifest component resolves: '+name)
    launchers=[a.get(android+'name') for a in app.findall('activity') if any(c.get(android+'name')=='android.intent.category.LAUNCHER' for c in a.findall('./intent-filter/category'))]
    check(launchers==['com.scenio.mth.features.FeaturesActivity'],'One intended launcher activity')
    check(app.get(android+'extractNativeLibs')=='true','Base native extraction is enabled')
    check('split' not in manifest.attrib and 'splitTypes' not in manifest.attrib,'No source-generated split metadata')
    gradle=sources['app/build.gradle.kts'];plugins=sources['build.gradle.kts'];ios=sources['ios/project.yml']
    for value in ['applicationId = "com.scenio.mth"','namespace = "com.scenio.mth"','minSdk = 24','targetSdk = 35','compileSdk = 35','useLegacyPackaging = true','"arm64-v8a", "x86_64"']:
        check(value in gradle,'Android invariant: '+value)
    version=re.search(r'versionName = "([^"]+)"',gradle)[1];code=re.search(r'versionCode = (\d+)',gradle)[1]
    check('MARKETING_VERSION: '+version in ios and 'CURRENT_PROJECT_VERSION: '+code in ios,'Android/iOS version alignment')
    def plugin(name): return re.search(r'id\("'+re.escape(name)+r'"\) version "([^"]+)"',plugins)[1]
    check(plugin('org.jetbrains.kotlin.android')==plugin('org.jetbrains.kotlin.plugin.compose'),'Kotlin and Compose plugin versions align')
    check(plugin('com.google.devtools.ksp').startswith(plugin('org.jetbrains.kotlin.android')+'-'),'KSP matches Kotlin compiler')
    room=plugin('androidx.room')
    check(all('androidx.room:'+name+':'+room in gradle for name in ['room-runtime','room-ktx','room-compiler','room-testing']),'Room plugin/runtime/compiler/test versions align')
    check('ndk;'+re.search(r'ndkVersion = "([^"]+)"',gradle)[1] in sources['.github/workflows/android.yml'],'CI and app NDK versions align')
    check('assembleDebugAndroidTest' in sources['.github/workflows/android.yml'],'Test APK compilation precedes emulator tests')
    check('androidx.test.espresso:espresso-core:3.6.1' in gradle,'Native UI test dependency is explicit and pinned')
    native=sources['app/src/main/cpp/local-engine.cpp']
    for method in re.findall(r'external fun (\w+)\(',sources['app/src/main/java/com/scenio/mth/inference/NativeEngine.kt']):
        check('Java_com_scenio_mth_inference_NativeEngine_'+method+'(' in native,'JNI symbol resolves: '+method)
    revision=re.search(r'GIT_TAG ([0-9a-f]{40})',sources['shared/runtime.cmake'])[1]
    check(revision in sources['app/src/main/java/com/scenio/mth/inference/LocalGenerationWorker.kt'],'Benchmark names the actual pinned inference runtime')
    for density,size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
        path=ROOT/f'app/src/main/res/mipmap-{density}/ic_launcher.png'
        data=path.read_bytes();check(data[:8]==b'\x89PNG\r\n\x1a\n' and struct.unpack('>II',data[16:24])==(size,size),'Android launcher dimensions: '+density)
    icons=ROOT/'ios/StudyForge/Assets.xcassets/AppIcon.appiconset'
    for icon in json.loads((icons/'Contents.json').read_text())['images']:
        if 'filename' not in icon: continue
        expected=tuple(round(float(v)*float(icon['scale'][:-1])) for v in icon['size'].split('x'))
        data=(icons/icon['filename']).read_bytes()
        check(struct.unpack('>II',data[16:24])==expected,'iOS launcher dimensions: '+icon['filename'])
    for name,content in sources.items():
        if not name.endswith('.md'): continue
        for target in re.findall(r'\]\(([^)]+)\)',content):
            if '://' not in target and not target.startswith('#'):
                check((ROOT/name).parent.joinpath(target.split('#')[0]).exists(),'Documentation link: '+name+' -> '+target)
    return {'files_examined':len(paths),'text_files_examined':len(sources),'checks_passed':len(checks),'checks':checks,'limitations':'Static integration checks do not prove model quality, all runtime behavior, clinical accuracy, hardware compatibility or complete feature delivery.'}

if __name__=='__main__':
    print(json.dumps(audit(),indent=2))
