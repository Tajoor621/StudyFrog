import base64
import hashlib
import http.client
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import threading
import unittest
from unittest.mock import patch
from PIL import Image
import video_remix as remix
import video_inputs

TOOLS=bool(shutil.which('ffmpeg') and shutil.which('ffprobe'))

def clip(path,audio=True):
    command=['ffmpeg','-v','error','-y','-f','lavfi','-i','color=c=green:s=160x96:r=24:d=1.5']
    if audio: command+=['-f','lavfi','-i','sine=frequency=440:sample_rate=48000:duration=1.5']
    command+=['-vf','drawbox=x=60:y=20:w=40:h=56:color=red:t=fill','-c:v','libx264','-threads','1','-pix_fmt','yuv420p']
    if audio: command+=['-c:a','aac','-shortest']
    subprocess.run(command+[str(path)],check=True,capture_output=True,timeout=30)

class RemixTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name);self.addCleanup(self.temp.cleanup)
    def settings(self,**kwargs):
        with patch.dict(os.environ,{'STUDYFORGE_VIDEO_PROFILE':'desktop'}):
            return remix.options(dict(profile='mobile',remix_mode='green',seconds=1,start=0,rights_confirmed=True,**kwargs))
    def test_limits_and_permission_are_enforced_on_server(self):
        base=dict(profile='mobile',remix_mode='green',seconds=1,rights_confirmed=True)
        for changes in ({'seconds':16},{'seconds':True},{'start':float('nan')},{'rights_confirmed':False},{'profile':'unlimited'},{'profile':[]},{'remix_mode':'swap'}):
            with self.subTest(changes=changes),self.assertRaises(ValueError): remix.options({**base,**changes})
        with patch.dict(os.environ,{'STUDYFORGE_VIDEO_PROFILE':'mobile'}),self.assertRaises(ValueError):
            remix.options({**base,'profile':'desktop'})
        self.assertFalse(remix.capabilities()['measured_performance'])
    def test_portrait_and_small_sources_keep_framing_without_upscale(self):
        self.assertEqual(remix.dimensions(dict(width=1080,height=1920),'mobile'),(480,852))
        self.assertEqual(remix.dimensions(dict(width=160,height=96),'desktop'),(160,96))
    def test_person_mask_preserves_foreground_pixels(self):
        import numpy as np
        rgb=np.full((32,32,3),(200,30,20),dtype=np.uint8);bg=np.full_like(rgb,(0,0,255))
        alpha=np.zeros((32,32),dtype=np.float32);alpha[6:26,6:26]=1
        out=remix.composite(rgb,bg,'person',lambda _:alpha)
        np.testing.assert_array_equal(out[16,16],rgb[16,16]);np.testing.assert_array_equal(out[0,0],bg[0,0])
        with self.assertRaises(RuntimeError): remix.composite(rgb,bg,'person',lambda _:np.zeros((1,1)))
    def test_model_contract_requires_trusted_hash_and_valid_output(self):
        import numpy as np
        model=self.root/'model.onnx';model.write_bytes(b'x'*100000)
        with patch.dict(os.environ,{'STUDYFORGE_SEGMENT_MODEL':str(model),'STUDYFORGE_SEGMENT_SHA256':'0'*64}),self.assertRaisesRegex(RuntimeError,'checksum'):
            remix.PersonMask()
        digest=hashlib.sha256(model.read_bytes()).hexdigest()
        class Net:
            def setPreferableBackend(self,_): pass
            def setPreferableTarget(self,_): pass
            def setInput(self,blob): self.blob=blob
            def forward(self): return np.stack([np.zeros((192,192)),np.ones((192,192))])[None].astype(np.float32)
        net=Net()
        with patch.dict(os.environ,{'STUDYFORGE_SEGMENT_MODEL':str(model),'STUDYFORGE_SEGMENT_SHA256':digest}),patch('cv2.dnn.readNetFromONNX',return_value=net):
            model=remix.PersonMask();mask=model(np.full((32,48,3),(255,0,0),dtype=np.uint8))
        self.assertEqual(mask.shape,(32,48));self.assertEqual(float(mask.min()),1)
        self.assertEqual(float(net.blob[0,0,0,0]),1);self.assertEqual(float(net.blob[0,2,0,0]),-1)
    @unittest.skipUnless(TOOLS,'FFmpeg unavailable')
    def test_real_encode_replaces_background_and_retains_soundtrack(self):
        import numpy as np
        source=self.root/'source.mp4';output=self.root/'output.mp4';clip(source)
        report=remix.render(source,output,Image.new('RGB',(160,96),'blue'),self.settings(),lambda:None,lambda _:None)
        self.assertEqual((report['width'],report['height'],report['fps']),(160,96,15))
        self.assertTrue(report['source_audio']);self.assertFalse(report['faces_resynthesized'])
        streams=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-of','json',str(output)]))['streams']
        self.assertEqual([s['codec_type'] for s in streams],['video','audio'])
        self.assertEqual(streams[0]['nb_frames'],'15');self.assertEqual(streams[1]['codec_name'],'aac')
        raw=subprocess.check_output(['ffmpeg','-v','error','-i',str(output),'-frames:v','1','-f','rawvideo','-pix_fmt','rgb24','pipe:1'])
        pixels=np.frombuffer(raw,dtype=np.uint8).reshape(96,160,3)
        self.assertGreater(int(pixels[5,5,2]),200);self.assertGreater(int(pixels[48,80,0]),200)
        sound=subprocess.check_output(['ffmpeg','-v','error','-i',str(output),'-vn','-ac','1','-ar','48000','-f','f32le','pipe:1'])
        samples=np.frombuffer(sound,dtype=np.float32)
        peak=np.argmax(np.abs(np.fft.rfft(samples)))*48000/len(samples)
        self.assertAlmostEqual(peak,440,delta=3)
        self.assertLess(abs(float(streams[0]['duration'])-float(streams[1]['duration'])),.1)
    @unittest.skipUnless(TOOLS,'FFmpeg unavailable')
    def test_silent_trim_and_cancel_remove_partial_outputs(self):
        source=self.root/'silent.mp4';output=self.root/'output.mp4';clip(source,audio=False)
        settings={**self.settings(),'start':.5,'seconds':.5}
        report=remix.render(source,output,Image.new('RGB',(160,96),'blue'),settings,lambda:None,lambda _:None)
        self.assertFalse(report['source_audio']);self.assertLessEqual(report['seconds'],.6)
        def cancel(): raise RuntimeError('requested cancellation')
        with self.assertRaisesRegex(RuntimeError,'requested cancellation'):
            remix.render(source,output,Image.new('RGB',(160,96)),settings,cancel,lambda _:None)
        self.assertFalse(output.exists());self.assertFalse(list(self.root.glob('remix-*')))
    @unittest.skipUnless(TOOLS,'FFmpeg unavailable')
    def test_http_upload_claim_lifecycle_and_transient_cleanup(self):
        import server
        source=self.root/'source.mp4';clip(source)
        with patch.object(server,'ROOT',self.root):
            http=server.Server(('127.0.0.1',0),server.Handler);thread=threading.Thread(target=http.serve_forever,daemon=True);thread.start()
            try:
                def request(path,body=None,method='POST',auth=True):
                    c=__import__('http.client',fromlist=['HTTPConnection']).HTTPConnection('127.0.0.1',http.server_port,timeout=10)
                    headers={'Content-Type':'application/octet-stream' if path=='/video-inputs' else 'application/json'}
                    if auth: headers['Authorization']='Bearer '+server.TOKEN
                    c.request(method,path,body=body,headers=headers);r=c.getresponse();status=r.status;data=json.loads(r.read());c.close();return status,data
                self.assertEqual(request('/video-inputs',source.read_bytes(),auth=False)[0],401)
                code,uploaded=request('/video-inputs',source.read_bytes());self.assertEqual(code,201)
                self.assertTrue(video_inputs.path(self.root,uploaded['id']).exists())
                out=io.BytesIO();Image.new('RGB',(160,96),'blue').save(out,format='PNG')
                body=dict(kind='remix',prompt='Replace background',image=base64.b64encode(out.getvalue()).decode(),video_input=uploaded['id'],**self.settings())
                status,queued=request('/jobs',json.dumps(body).encode());self.assertEqual(status,202)
                self.assertFalse(video_inputs.path(self.root,uploaded['id']).exists())
                self.assertEqual(request('/jobs',json.dumps(body).encode())[0],400)
                job=server.JOBS.get_nowait()
                try: server.process(job)
                finally: server.JOBS.task_done()
                state=server.read(queued['id']);self.assertEqual(state['status'],'done',state)
                self.assertNotIn('image',state);self.assertFalse((self.root/(queued['id']+'.input')).exists())
                self.assertEqual(request('/jobs/'+queued['id'],method='DELETE')[0],200)
                self.assertFalse((self.root/(queued['id']+'.mp4')).exists())
            finally: http.shutdown();http.server_close();thread.join()
    @unittest.skipUnless(TOOLS and os.environ.get('STUDYFORGE_RUN_SEGMENT_SMOKE')=='1','Explicit real segmentation model smoke test not configured')
    def test_real_pphumanseg_onnx_runtime_and_encode(self):
        import numpy as np
        model=remix.PersonMask()
        rgb=np.full((96,160,3),(180,140,120),dtype=np.uint8)
        mask=model(rgb)
        self.assertEqual(mask.shape,(96,160));self.assertTrue(np.isfinite(mask).all())
        source=self.root/'source.mp4';output=self.root/'output.mp4';clip(source)
        settings={**self.settings(),'remix_mode':'person'}
        report=remix.render(source,output,Image.new('RGB',(160,96),'blue'),settings,lambda:None,lambda _:None,masker=model)
        self.assertEqual(report['model_sha256'],os.environ['STUDYFORGE_SEGMENT_SHA256'])
        self.assertTrue(remix.probe(output)['audio'])
        # A synthetic source exercises loading/encoding, not human-edge quality.
    def test_invalid_or_incomplete_upload_never_survives(self):
        with self.assertRaises(ValueError): video_inputs.receive(self.root,io.BytesIO(b'x'),10)
        self.assertFalse(list(self.root.glob('*.upload*')))
        for name in ('../etc','x'*32,None):
            with self.assertRaises(ValueError): video_inputs.path(self.root,name)

if __name__=='__main__': unittest.main()
