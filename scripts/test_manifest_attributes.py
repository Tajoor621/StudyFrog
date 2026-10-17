import unittest
from manifest_attributes import has_attribute

class ManifestAttributeTests(unittest.TestCase):
    def test_modern_aapt2_namespace_uri_and_plain_booleans(self):
        xml='''N: android=http://schemas.android.com/apk/res/android
  E: manifest (line=0)
    A: http://schemas.android.com/apk/res/android:splitTypes(0x0101064f)="base__density" (Raw: "base__density")
    A: split="config.xxhdpi" (Raw: "config.xxhdpi")
      E: application (line=0)
        A: http://schemas.android.com/apk/res/android:hasCode(0x0101000c)=false
        A: http://schemas.android.com/apk/res/android:extractNativeLibs(0x010104ea)=true'''
        self.assertTrue(has_attribute(xml,'splitTypes','base__density'))
        self.assertTrue(has_attribute(xml,'split','config.xxhdpi'))
        self.assertTrue(has_attribute(xml,'hasCode',False))
        self.assertTrue(has_attribute(xml,'extractNativeLibs',True))
    def test_legacy_typed_boolean_values(self):
        self.assertTrue(has_attribute(' A: android:hasCode(0x0101000c)=(type 0x12)0x0','hasCode',False))
        self.assertTrue(has_attribute(' A: android:extractNativeLibs(0x010104ea)=(type 0x12)0xffffffff','extractNativeLibs',True))
    def test_resource_identifier_is_never_a_boolean_value(self):
        self.assertFalse(has_attribute(' A: android:hasCode(0x0101000c)=true','hasCode',False))
        self.assertFalse(has_attribute(' A: android:extractNativeLibs(0x010104ea)=false','extractNativeLibs',True))
    def test_similar_attribute_names_and_raw_strings_do_not_match(self):
        self.assertFalse(has_attribute(' A: requiredSplitTypes="base__density"','splitTypes','base__density'))
        self.assertFalse(has_attribute(' A: splitTypes="other" (Raw: "base__density")','splitTypes','base__density'))

if __name__=='__main__': unittest.main()
