import copy
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from release_readiness import assess, reference, subject_digest

class ReadinessTests(unittest.TestCase):
    def test_current_empty_register_is_explicitly_pending(self):
        result = assess()
        self.assertFalse(result['requested_validation_complete'])
        self.assertFalse(result['independent_reviews']['science'])
        self.assertEqual([],result['invalid_records'])

    def test_modified_and_external_evidence_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory);file = root/'review.txt';file.write_text('Human review evidence')
            item = {'path':'review.txt','sha256':hashlib.sha256(file.read_bytes()).hexdigest()}
            self.assertEqual(file.read_bytes(),reference(root,item))
            file.write_text('changed')
            with self.assertRaises(ValueError): reference(root,item)
            with self.assertRaises(ValueError): reference(root,{'path':'../outside','sha256':'0'*64})

    def test_source_changes_invalidate_review_digest(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory);(root/'source').write_text('first')
            first = subject_digest(root,['source']);(root/'source').write_text('second')
            self.assertNotEqual(first,subject_digest(root,['source']))

    def test_independent_approval_and_physical_records_are_required(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory);(root/'app').mkdir();(root/'app/build.gradle.kts').write_text('versionName = "fixture-version"');(root/'subject').write_text('fixture subject')
            manifest = root/'register.json'
            data = {'schema':1,'subjects':{'science':['subject'],'family':['subject']},'models':[],'science_reviews':[],'family_reviews':[],'benchmarks':[],'device_sessions':[]}
            note = root/'review.md';note.write_text('Synthetic test-only review text. This is a validator fixture and is never a real approval. '*2)
            approval = {'reviewer':'fixture-reviewer','independent':True,'qualifications':'test fixture qualifications','outcome':'approved','subject_digest':subject_digest(root,['subject']),'evidence':{'path':'review.md','sha256':hashlib.sha256(note.read_bytes()).hexdigest()}}
            data['science_reviews'] = [approval];manifest.write_text(json.dumps(data))
            result = assess(root,manifest);self.assertTrue(result['independent_reviews']['science']);self.assertFalse(result['requested_validation_complete'])
            altered = copy.deepcopy(data);altered['science_reviews'][0]['reviewer'] = 'Codex';manifest.write_text(json.dumps(altered))
            self.assertFalse(assess(root,manifest)['independent_reviews']['science'])
            (root/'subject').write_text('modified');manifest.write_text(json.dumps(data))
            self.assertFalse(assess(root,manifest)['independent_reviews']['science'])

    def test_complete_fixture_is_accepted_but_old_build_is_not(self):
        # Synthetic validator fixtures only; no real review/device approval is created.
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory);(root/'app').mkdir();gradle = root/'app/build.gradle.kts';gradle.write_text('versionName = "test-build"');(root/'subject').write_text('subject')
            def record(name, value):
                path = root/name;path.write_text(json.dumps(value) if isinstance(value,dict) else value)
                return {'path':name,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()}
            note = record('review.txt','Synthetic test fixture only. Not an actual review or physical device result. '*3)
            approval = {'reviewer':'test-human','independent':True,'qualifications':'synthetic test qualification','outcome':'approved','subject_digest':subject_digest(root,['subject']),'evidence':note}
            data = {'schema':1,'subjects':{'science':['subject'],'family':['subject']},'science_reviews':[approval],'family_reviews':[approval],'models':[],'benchmarks':[],'device_sessions':[]}
            for digest in ['a'*64,'b'*64]:
                data['models'].append({'sha256':digest,'name':'test-only','source_url':'https://example.org/source','license_url':'https://example.org/license'})
                for platform in ['android','ios','windows']:
                    data['benchmarks'].append(record(platform+digest[:1]+'.json',{'status':'completed','platform':platform,'build_version':'test-build','physical_device':True,'model_sha256':digest,'attempts':[{'status':'passed','case':case,'elapsed_ms':10} for case in ['botany','product']]}))
            for platform in ['android','ios','windows']:
                data['device_sessions'].append(record(platform+'-manual.json',{'platform':platform,'build_version':'test-build','physical_device':True,'operator':'test-human','hardware':'synthetic fixture only','recorded_at':'2026-10-19T00:00:00Z','attachments':[note],'checks':{check:'passed' for check in ['screen_reader','font_scaling','contrast','low_memory_recovery','encoder_repeat']}}))
            manifest = root/'register.json';manifest.write_text(json.dumps(data))
            self.assertTrue(assess(root,manifest)['requested_validation_complete'])
            gradle.write_text('versionName = "new-build"');self.assertFalse(assess(root,manifest)['requested_validation_complete'])
