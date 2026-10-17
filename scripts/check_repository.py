"""Fail fast if exports omit required files or source manifests hardcode splits."""
from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
required=["app/src/main/java/com/scenio/mth/features/data/ProgressStore.kt",
          "app/src/main/java/com/scenio/mth/features/data/SettingsStore.kt",
          "app/src/main/res/drawable-xxhdpi/ic_xxhdpi.png",
          "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
          "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
          "app/src/main/cpp/native-lib.cpp","gradle/wrapper/gradle-wrapper.jar"]
for name in required:
    assert (root/name).is_file(),f"Missing source: {name}"
for path in (root/'app/src').rglob('*.xml'): ET.parse(path)
manifest=ET.parse(root/'app/src/main/AndroidManifest.xml').getroot()
android='{http://schemas.android.com/apk/res/android}'
assert 'split' not in manifest.attrib and 'splitTypes' not in manifest.attrib
app=manifest.find('application')
assert app.get(android+'extractNativeLibs')=='true'
assert app.get(android+'icon')=='@mipmap/ic_launcher'
assert 'com.android.vending.derived.apk.id' not in ET.tostring(manifest).decode()
print(f'Repository integrity passed: {len(required)} required files, XML, icons and native packaging')

expected={"root","file","database","sharedpref","external","device_root","device_file","device_database","device_sharedpref"}
for name in ["sf_backup_rules.xml","sf_data_extraction_rules.xml"]:
    rules=ET.parse(root/"app/src/main/res/xml"/name).getroot()
    sections=[rules] if rules.tag=="full-backup-content" else list(rules)
    for section in sections:
        assert {node.get("domain") for node in section.findall("exclude") if node.get("path")=="."}==expected, "Backup exclusions must cover every storage domain"
print("Backup privacy exclusions passed")
