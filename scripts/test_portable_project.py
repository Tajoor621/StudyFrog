"""Shared exchange fixture contract. Native codecs are tested on emulator/simulator."""
import base64
import hashlib
import json
import struct
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

class PortableProjectContractTest(unittest.TestCase):
    def test_native_tests_receive_identical_exchange_fixture(self):
        original = (ROOT/'content/portable-project-v1.json').read_bytes()
        for path in ['app/src/androidTest/assets/portable-project-v1.json', 'ios/StudyForgeTests/Resources/portable-project-v1.json']:
            self.assertEqual(original, (ROOT/path).read_bytes())

    def test_fixture_assets_and_layer_contract(self):
        document = json.loads((ROOT/'content/portable-project-v1.json').read_text())
        self.assertEqual(('studyforge-layered-raster', 1), (document['format'], document['version']))
        self.assertEqual(2, len(document['layers']))
        self.assertEqual(1, document['selected'])
        for asset in [document['base']] + [layer['image'] for layer in document['layers']]:
            png = base64.b64decode(asset['png'], validate=True)
            self.assertEqual(asset['sha256'], hashlib.sha256(png).hexdigest())
            self.assertEqual(b'\x89PNG\r\n\x1a\n', png[:8])
            self.assertEqual((asset['width'], asset['height']), struct.unpack('>II', png[16:24]))
        self.assertEqual(['screen', 'multiply'], [layer['blend'] for layer in document['layers']])
        self.assertFalse(document['layers'][0]['visible'])
