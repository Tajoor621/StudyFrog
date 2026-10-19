import json
import tempfile
import unittest
from pathlib import Path
from benchmark import fingerprint, summary, main

class BenchmarkTests(unittest.TestCase):
    def test_fingerprint_tracks_real_file_contents(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder);(root/'model_index.json').write_text('{}');weights = root/'fixture.safetensors';weights.write_bytes(b'transport fixture')
            first,files = fingerprint(root);self.assertEqual(2,len(files));self.assertEqual(first,fingerprint(root)[0])
            weights.write_bytes(b'changed');self.assertNotEqual(first,fingerprint(root)[0])

    def test_summary_preserves_failures_and_does_not_invent_measurements(self):
        self.assertIsNone(summary([])['median_ms'])
        result = summary([{'status':'passed','elapsed_ms':10},{'status':'passed','elapsed_ms':30},{'status':'failed','elapsed_ms':2}])
        self.assertEqual((20,30,1),(result['median_ms'],result['p95_nearest_rank_ms'],result['failed']))

    def test_preflight_never_claims_inference(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder);model = root/'model';model.mkdir();(model/'model_index.json').write_text('{}');(model/'fixture.safetensors').write_bytes(b'fixture')
            output = root/'evidence.json'
            self.assertEqual(0,main(['--model-directory',str(model),'--model-id','transport-test','--source-url','https://example.org/source','--license-url','https://example.org/license','--output',str(output)]))
            report = json.loads(output.read_text());self.assertEqual('preflight_only',report['status']);self.assertEqual([],report['attempts'])
