"""Offline video compositing. Source people/audio are retained, never synthesized."""
from pathlib import Path
import hashlib
import importlib.util
import json
import math
import os
import re
import shutil
import subprocess
import tempfile
import threading
import time
from PIL import Image, ImageOps

PROFILES = {
    'mobile': dict(seconds=15, width=854, height=480, fps=15, upload_bytes=64_000_000),
    'laptop': dict(seconds=30, width=1280, height=720, fps=24, upload_bytes=128_000_000),
    'desktop': dict(seconds=60, width=1920, height=1080, fps=30, upload_bytes=128_000_000),
}
MAX_UPLOAD = 128_000_000
MAX_RESULT = 256_000_000
MODES = ('person', 'green')

def capabilities():
    ceiling = os.environ.get('STUDYFORGE_VIDEO_PROFILE', 'laptop')
    if ceiling not in PROFILES: ceiling = 'laptop'
    return dict(profiles=PROFILES, ceiling=ceiling, ffmpeg=bool(shutil.which('ffmpeg') and shutil.which('ffprobe')),
                person_model=bool(os.environ.get('STUDYFORGE_SEGMENT_MODEL')) and Path(os.environ['STUDYFORGE_SEGMENT_MODEL']).is_file(),
                opencv=importlib.util.find_spec('cv2') is not None, local_only=True,
                audio='Original first audio track, re-encoded AAC; no voice cloning', measured_performance=False)

def options(body):
    profile = body.get('profile', 'mobile')
    if not isinstance(profile, str) or profile not in PROFILES: raise ValueError('Choose mobile, laptop or desktop profile')
    ceiling = capabilities()['ceiling']
    if list(PROFILES).index(profile) > list(PROFILES).index(ceiling):
        raise ValueError(f'Engine ceiling is {ceiling}; choose a lower profile or configure the processing computer')
    mode = body.get('remix_mode', 'person')
    if not isinstance(mode, str) or mode not in MODES: raise ValueError('Choose person or green background removal')
    start, seconds = body.get('start', 0), body.get('seconds', PROFILES[profile]['seconds'])
    for value in (start, seconds):
        if type(value) not in (int, float) or not math.isfinite(value): raise ValueError('Clip times must be finite numbers')
    if not 0 <= start <= 3600 or not .5 <= seconds <= PROFILES[profile]['seconds']:
        raise ValueError(f'Clip duration must be 0.5–{PROFILES[profile]["seconds"]} seconds; start 0–3600 seconds')
    if body.get('rights_confirmed') is not True: raise ValueError('Confirm permission to edit the video, people and soundtrack')
    return dict(profile=profile, remix_mode=mode, start=float(start), seconds=float(seconds), rights_confirmed=True)

def probe(path):
    if not shutil.which('ffmpeg') or not shutil.which('ffprobe'): raise RuntimeError('Install free FFmpeg and ffprobe on the processing computer')
    command = ['ffprobe', '-v', 'error', '-protocol_whitelist', 'file,pipe', '-show_streams', '-show_format', '-of', 'json', str(path)]
    result = subprocess.run(command, capture_output=True, timeout=15)
    if result.returncode or len(result.stdout) > 1_000_000: raise ValueError('Unreadable video container')
    data = json.loads(result.stdout)
    names = set(data.get('format', {}).get('format_name', '').split(','))
    if not names.intersection({'mov', 'mp4', 'matroska', 'webm'}): raise ValueError('Use a local MP4, MOV or WebM video')
    video = next((s for s in data.get('streams', []) if s.get('codec_type') == 'video' and not s.get('disposition', {}).get('attached_pic')), None)
    if not video: raise ValueError('No playable video track')
    w, h = int(video.get('width', 0)), int(video.get('height', 0))
    if min(w,h) < 16 or max(w,h) > 4096 or w*h > 8_847_360: raise ValueError('Input video exceeds the 4K decode limit')
    duration = float(video.get('duration') or data.get('format', {}).get('duration', 0))
    if not math.isfinite(duration) or not 0 < duration <= 3600: raise ValueError('Video duration must be known and at most one hour')
    if video.get('sample_aspect_ratio','1:1') not in ('1:1','0:1','N/A'): raise ValueError('Export the source with square pixels before importing')
    if video.get('color_transfer') in ('smpte2084', 'arib-std-b67'): raise ValueError('Export HDR video as SDR before importing')
    rotation = next((float(s.get('rotation', 0)) for s in video.get('side_data_list', []) if 'rotation' in s), 0)
    if abs(rotation) % 180 == 90: w,h = h,w
    return dict(width=w, height=h, duration=duration, audio=any(s.get('codec_type')=='audio' for s in data['streams']))

def dimensions(source, profile):
    cap = PROFILES[profile]
    # Rotate the target box for portrait sources; preserve framing and never upscale.
    bw,bh = (cap['height'],cap['width']) if source['height']>source['width'] else (cap['width'],cap['height'])
    scale = min(1, bw/source['width'], bh/source['height'])
    return max(16,int(source['width']*scale)//2*2), max(16,int(source['height']*scale)//2*2)

class PersonMask:
    def __init__(self):
        import cv2
        self.cv = cv2
        path = Path(os.environ.get('STUDYFORGE_SEGMENT_MODEL', '/__not_configured__'))
        if not path.is_file() or path.suffix.lower() != '.onnx' or not 100_000 <= path.stat().st_size <= 64_000_000:
            raise RuntimeError('Configure STUDYFORGE_SEGMENT_MODEL with the local OpenCV Zoo PPHumanSeg FP32 ONNX model')
        expected = os.environ.get('STUDYFORGE_SEGMENT_SHA256', '')
        if not re.fullmatch('[0-9a-f]{64}', expected): raise RuntimeError('Set STUDYFORGE_SEGMENT_SHA256 to the trusted model SHA-256')
        actual = hashlib.sha256(path.read_bytes()).hexdigest()
        if actual != expected: raise RuntimeError('Person segmentation model checksum mismatch')
        self.digest = actual
        self.net = cv2.dnn.readNetFromONNX(str(path))
        self.net.setPreferableBackend(cv2.dnn.DNN_BACKEND_OPENCV)
        self.net.setPreferableTarget(cv2.dnn.DNN_TARGET_CPU)
    def __call__(self, rgb):
        import numpy as np
        cv = self.cv
        # OpenCV Zoo's PPHumanSeg contract: RGB, 192px, mean/std 0.5.
        image = cv.resize(rgb, (192,192)).astype(np.float32)/127.5-1.0
        self.net.setInput(cv.dnn.blobFromImage(image))
        output = self.net.forward()
        if output.shape != (1,2,192,192) or not np.isfinite(output).all(): raise RuntimeError('Incompatible PPHumanSeg output')
        labels = np.argmax(output[0],axis=0).astype(np.float32)
        return cv.resize(labels, (rgb.shape[1],rgb.shape[0]), interpolation=cv.INTER_LINEAR)

def composite(rgb, background, mode, masker=None):
    import numpy as np
    import cv2
    if mode == 'person':
        if masker is None: raise RuntimeError('Person segmentation is not configured')
        alpha = masker(rgb)
    elif mode == 'green':
        # No model: remove saturated green only. Green clothing will also disappear.
        hsv = cv2.cvtColor(rgb, cv2.COLOR_RGB2HSV)
        key = cv2.inRange(hsv, (35,60,40), (85,255,255))
        alpha = 1.0-key.astype(np.float32)/255
    else: raise ValueError('Unknown remix mode')
    if alpha.shape != rgb.shape[:2] or not np.isfinite(alpha).all(): raise RuntimeError('Invalid foreground mask')
    alpha = cv2.GaussianBlur(np.clip(alpha,0,1), (5,5), 0)[...,None]
    return np.rint(rgb.astype(np.float32)*alpha+background.astype(np.float32)*(1-alpha)).clip(0,255).astype(np.uint8)

class Processes:
    """Watchdog interrupts blocking pipe reads/writes on cancellation or wall timeout."""
    def __init__(self, checkpoint, timeout=1800):
        self.checkpoint=checkpoint;self.deadline=time.monotonic()+timeout
        self.items=[];self.lock=threading.Lock();self.stopped=threading.Event();self.error=None
    def add(self, process):
        with self.lock:
            self.items.append(process)
            if self.stopped.is_set() and process.poll() is None: process.kill()
        return process
    def watch(self):
        while not self.stopped.wait(.1):
            try:
                self.checkpoint()
                if time.monotonic()>self.deadline: raise TimeoutError('Video remix exceeded 30 minutes; try a smaller profile')
            except Exception as exc:
                self.error=exc;self.close();return
    def close(self):
        self.stopped.set()
        with self.lock:
            for p in self.items:
                if p.poll() is None: p.kill()
    def check(self):
        if self.error: raise self.error
        self.checkpoint()

def render(source, output, background, settings, checkpoint, progress, masker=None):
    import numpy as np
    source,output=Path(source),Path(output)
    info=probe(source);profile=settings['profile'];cap=PROFILES[profile]
    if source.stat().st_size>cap['upload_bytes']: raise ValueError('Source exceeds the selected device profile upload limit')
    start,seconds=settings['start'],settings['seconds']
    if start>=info['duration']: raise ValueError('Clip start is beyond the source video')
    seconds=min(seconds,info['duration']-start)
    if seconds<.5: raise ValueError('At least half a second of source video is required')
    w,h=dimensions(info,profile);fps=cap['fps']
    bg=np.asarray(ImageOps.fit(background.convert('RGB'),(w,h),method=Image.Resampling.LANCZOS))
    if settings['remix_mode']=='person' and masker is None: masker=PersonMask()
    if shutil.disk_usage(output.parent).free < max(512_000_000,w*h*3*4): raise RuntimeError('Free at least 512 MB before remixing')
    frames=0;watch=Processes(checkpoint);thread=threading.Thread(target=watch.watch,daemon=True);thread.start()
    succeeded=False
    try:
        with tempfile.TemporaryDirectory(dir=output.parent,prefix='remix-') as temp, tempfile.TemporaryFile() as errors:
            silent=Path(temp)/'silent.mp4'
            decode=['ffmpeg','-v','error','-nostdin','-threads','1','-protocol_whitelist','file,pipe','-ss',str(start),'-i',str(source),'-map','0:v:0','-t',str(seconds),'-an','-sn','-dn','-vf',f'scale={w}:{h},fps={fps}','-f','rawvideo','-pix_fmt','rgb24','pipe:1']
            encode=['ffmpeg','-v','error','-nostdin','-y','-f','rawvideo','-pix_fmt','rgb24','-s',f'{w}x{h}','-r',str(fps),'-i','pipe:0','-an','-c:v','libx264','-threads','2','-preset','veryfast','-crf','20','-maxrate','12M','-bufsize','24M','-pix_fmt','yuv420p',str(silent)]
            decoder=watch.add(subprocess.Popen(decode,stdout=subprocess.PIPE,stderr=errors))
            encoder=watch.add(subprocess.Popen(encode,stdin=subprocess.PIPE,stdout=subprocess.DEVNULL,stderr=errors))
            try:
                frame_bytes=w*h*3
                while True:
                    watch.check();raw=decoder.stdout.read(frame_bytes)
                    if not raw: break
                    if len(raw)!=frame_bytes: raise RuntimeError('Video decoder returned an incomplete frame')
                    frames+=1
                    if frames>math.ceil(seconds*fps)+1: raise RuntimeError('Decoded frame limit exceeded')
                    rgb=np.frombuffer(raw,dtype=np.uint8).reshape(h,w,3)
                    encoder.stdin.write(composite(rgb,bg,settings['remix_mode'],masker).tobytes())
                    if frames%fps==0: progress(min(94,int(frames/(seconds*fps)*94)))
                encoder.stdin.close()
                if decoder.wait(timeout=15) or encoder.wait(timeout=60) or not frames: raise RuntimeError('Video decode/encode failed')
            finally:
                decoder.stdout.close()
                if not encoder.stdin.closed: encoder.stdin.close()
            watch.check();duration=frames/fps
            # The source timeline is unchanged. Re-encode only the first original audio track.
            mux=['ffmpeg','-v','error','-nostdin','-y','-i',str(silent),'-ss',str(start),'-protocol_whitelist','file,pipe','-i',str(source),'-map','0:v:0','-map','1:a:0?','-c:v','copy','-c:a','aac','-b:a','192k','-t',str(duration),'-map_metadata','-1','-metadata','comment=StudyFrog background edited; source performance and audio','-movflags','+faststart',str(output)]
            muxer=watch.add(subprocess.Popen(mux,stdout=subprocess.DEVNULL,stderr=errors))
            if muxer.wait(timeout=90): raise RuntimeError('Original soundtrack could not be muxed')
            watch.check()
            if output.stat().st_size>MAX_RESULT: raise RuntimeError('Result exceeds the 256 MB download limit')
            progress(100);succeeded=True
            return dict(profile=profile,width=w,height=h,fps=fps,frames=frames,seconds=duration,
                        source_audio=info['audio'],audio='original first track re-encoded AAC' if info['audio'] else 'silent source',
                        model_sha256=getattr(masker,'digest',None),faces_resynthesized=False,background_edited=True,
                        measured_performance=False)
    except (subprocess.TimeoutExpired, BrokenPipeError) as exc:
        watch.check();raise RuntimeError('Video tool interrupted; try a shorter clip or lower profile') from exc
    finally:
        watch.close();thread.join(timeout=2)
        for process in watch.items:
            try: process.wait(timeout=5)
            except subprocess.TimeoutExpired: pass
        if not succeeded: output.unlink(missing_ok=True)
