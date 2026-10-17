import tempfile,unittest,json,subprocess,shutil
from pathlib import Path
from PIL import Image
import motion_video

class MotionVideoTests(unittest.TestCase):
    def test_segments_chain_from_last_frame_without_duplicate_conditioning_frames(self):
        seen=[]
        def generate(image,index):
            seen.append(image.getpixel((0,0)))
            return [Image.new('RGB',(8,8),(index+1,0,0)) for _ in range(145)]
        frames=list(motion_video.frames_from_segments(Image.new('RGB',(8,8)),generate,lambda:None,lambda value:None))
        self.assertEqual(len(frames),720)
        self.assertEqual(seen,[(0,0,0),(1,0,0),(2,0,0),(3,0,0),(4,0,0)])
    def test_incomplete_segment_fails(self):
        with self.assertRaises(RuntimeError): list(motion_video.frames_from_segments(Image.new('RGB',(8,8)),lambda image,index:[],lambda:None,lambda value:None))
    def test_missing_local_video_model_does_not_fall_back_to_reveal(self):
        import os
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as root,patch.dict(os.environ,{'STUDYFORGE_VIDEO_MODEL':root+'/missing'}):
            output=Path(root)/'result.mp4'
            with self.assertRaises(RuntimeError): motion_video.render(Image.new('RGB',(8,8)),output,'Plant','','42',20,lambda:None,lambda value:None)
            self.assertFalse(output.exists())
    @unittest.skipUnless(shutil.which('ffmpeg') and shutil.which('ffprobe'),'FFmpeg required')
    def test_motion_container_is_exactly_30_seconds_and_1080p(self):
        def generate(image,index): return [Image.new('RGB',(8,8),(index*30,80,40)) for _ in range(145)]
        with tempfile.TemporaryDirectory() as root:
            output=Path(root)/'motion.mp4'
            motion_video.render(Image.new('RGB',(8,8)),output,'Study','',42,20,lambda:None,lambda value:None,generate=generate)
            stream=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-of','json',str(output)]))['streams'][0]
            self.assertEqual((stream['width'],stream['height']),(1920,1080));self.assertEqual(stream['nb_frames'],'720');self.assertEqual(float(stream['duration']),30.0)
