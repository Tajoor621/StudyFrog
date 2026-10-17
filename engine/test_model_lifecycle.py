import base64
import io
import sys
import types
import unittest
import tempfile
from pathlib import Path
import weakref
from unittest.mock import patch
from PIL import Image
import server

class ModelLifecycleTests(unittest.TestCase):
    def setUp(self):
        directory=tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        root=patch.object(server,'ROOT',Path(directory.name))
        root.start();self.addCleanup(root.stop)
    def run_motion(self,fail):
        class Pipeline: pass
        pipeline=Pipeline();reference=weakref.ref(pipeline);server.PIPE=pipeline;del pipeline
        cache=[]
        torch=types.SimpleNamespace(cuda=types.SimpleNamespace(is_available=lambda: True,empty_cache=lambda: cache.append(True)))
        image=io.BytesIO();Image.new('RGB',(64,64)).save(image,format='PNG')
        job=server.validate(dict(kind='motion',prompt='Leaf movement',image=base64.b64encode(image.getvalue()).decode()))
        job.update(id=('d' if fail else 'e')*32,status='queued')
        def render(initial,output,*args):
            self.assertIsNone(reference(),'Image pipeline must be freed before video loading')
            self.assertTrue(cache)
            if fail: raise RuntimeError('Video model failed')
            output.write_bytes(b'fixture')
        with patch.dict(sys.modules,{'torch':torch}),patch('motion_video.render',side_effect=render):
            server.process(job)
        self.assertEqual('failed' if fail else 'done',server.read(job['id'])['status'])
        self.assertGreaterEqual(len(cache),2)
        self.assertIsNone(server.PIPE)
    def test_image_memory_is_released_for_motion(self): self.run_motion(False)
    def test_failed_motion_also_releases_model_memory(self): self.run_motion(True)

    def test_failed_cuda_cleanup_does_not_terminate_the_worker(self):
        torch=types.SimpleNamespace(cuda=types.SimpleNamespace(is_available=lambda: True,empty_cache=lambda: (_ for _ in ()).throw(RuntimeError('CUDA context failed'))))
        server.PIPE=object()
        with patch.dict(sys.modules,{'torch':torch}): server.release_image_pipeline()
        self.assertIsNone(server.PIPE)
