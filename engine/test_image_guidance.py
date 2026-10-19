import base64
import io
import os
import sys
import tempfile
import types
import unittest
import weakref
from pathlib import Path
from unittest.mock import patch
from PIL import Image, ImageDraw
import image_guidance
import server

class ImageGuidanceTests(unittest.TestCase):
    def setUp(self):
        self.temporary=tempfile.TemporaryDirectory();self.addCleanup(self.temporary.cleanup)
        self.folder=Path(self.temporary.name)
        out=io.BytesIO();Image.new("RGB",(64,64),"teal").save(out,format="PNG")
        self.encoded=base64.b64encode(out.getvalue()).decode()
    def test_validation_requires_maps_and_finite_strength(self):
        for body in ({"control":"unknown"},{"control":[]},{"control":"pose"},{"control":"depth","kind":"motion","image":self.encoded},{"control_scale":True},{"control_scale":float("nan")},{"control_scale":1.1}):
            with self.subTest(body=body),self.assertRaises(ValueError): server.validate({"prompt":"Plant",**body})
        for mode in image_guidance.MODES:
            result=server.validate(dict(prompt="Plant",control=mode,image=self.encoded,control_scale=.3))
            self.assertEqual(result["control"],mode);self.assertEqual(result["control_scale"],.3)
    def test_depth_and_pose_maps_are_bounded_and_prepared_without_downloads(self):
        source=Image.new("RGB",(64,128),(10,30,50))
        depth=image_guidance.condition(source,"depth",256,256)
        pose=image_guidance.condition(source,"pose",256,256)
        self.assertEqual(depth.size,(256,256));self.assertEqual(depth.mode,"RGB")
        self.assertEqual(len(set(depth.getpixel((100,100)))),1)
        self.assertEqual(pose.getpixel((100,100)),(10,30,50))
    def test_canny_produces_a_real_binary_three_channel_edge_map(self):
        try: import cv2
        except ImportError: self.skipTest("Install requirements-test.txt for OpenCV verification")
        source=Image.new("RGB",(128,128),"black");ImageDraw.Draw(source).rectangle((24,24,100,100),fill="white")
        result=image_guidance.condition(source,"canny",256,256)
        values=set(result.getdata());self.assertEqual(values,{(0,0,0),(255,255,255)})
    def test_missing_guidance_weights_fail_instead_of_downloading(self):
        with patch.dict(os.environ,{},clear=True):
            self.assertTrue(all(not configured for configured in image_guidance.capabilities().values()))
            for mode in image_guidance.VARIABLES:
                with self.assertRaisesRegex(RuntimeError,"No weights are downloaded"):
                    image_guidance.cache_key(str(self.folder),mode)
    def test_adapter_loader_uses_explicit_local_encoder_and_safetensors(self):
        (self.folder/"image_encoder").mkdir();(self.folder/"ip-adapter_sd15.safetensors").write_bytes(b"fixture")
        calls=[]
        class Pipeline:
            @classmethod
            def from_pretrained(cls,path,**kwargs): calls.append(("base",path,kwargs));return cls()
            def load_ip_adapter(self,path,**kwargs): calls.append(("adapter",path,kwargs))
        class Encoder:
            @staticmethod
            def from_pretrained(path,**kwargs): calls.append(("encoder",path,kwargs));return object()
        diffusers=types.SimpleNamespace(StableDiffusionPipeline=Pipeline,StableDiffusionControlNetPipeline=Pipeline,ControlNetModel=Pipeline)
        transformers=types.SimpleNamespace(CLIPVisionModelWithProjection=Encoder)
        with patch.dict(os.environ,{"STUDYFORGE_IP_ADAPTER":str(self.folder)}),patch.dict(sys.modules,{"diffusers":diffusers,"transformers":transformers}):
            image_guidance.load_pipeline(str(self.folder),"reference","float32")
        self.assertEqual([c[0] for c in calls],["encoder","base","adapter"])
        self.assertTrue(all(c[2]["local_files_only"] for c in calls))
        self.assertIsNone(calls[-1][2]["image_encoder_folder"])
        self.assertTrue(calls[0][2]["use_safetensors"])
    def test_mode_switch_releases_old_pipeline_and_reuse_updates_scale(self):
        calls=[];scales=[];conditions=[]
        class Generator:
            def __init__(self,**kwargs): pass
            def manual_seed(self,seed): return self
        class Pipeline:
            safety_checker=object();device="cpu"
            def enable_attention_slicing(self): pass
            def enable_vae_tiling(self): pass
            def to(self,device): pass
            def set_ip_adapter_scale(self,value): scales.append(value)
            def __call__(self,**kwargs):
                conditions.append(kwargs);return types.SimpleNamespace(images=[Image.new("RGB",(256,256))],nsfw_content_detected=[False])
        def load(model,mode,dtype): calls.append(mode);return Pipeline()
        torch=types.SimpleNamespace(float16="float16",float32="float32",Generator=Generator,cuda=types.SimpleNamespace(is_available=lambda:False))
        modules={"torch":torch,"diffusers":types.SimpleNamespace(StableDiffusionImg2ImgPipeline=Pipeline)}
        env={"STUDYFORGE_MODEL":str(self.folder),"STUDYFORGE_IP_ADAPTER":str(self.folder),"STUDYFORGE_CONTROLNET_POSE":str(self.folder)}
        with patch.dict(os.environ,env),patch.dict(sys.modules,modules),patch.object(image_guidance,"load_pipeline",side_effect=load),patch.object(server,"PIPE",None),patch.object(server,"PIPE_KEY",None):
            job=server.validate(dict(prompt="Plant",control="reference",image=self.encoded,control_scale=.3))
            server.diffusion(job);old=weakref.ref(server.PIPE)
            server.diffusion({**job,"control_scale":.8});self.assertEqual(calls,["reference"]);self.assertEqual(scales,[.3,.8])
            server.diffusion({**job,"control":"pose"})
            self.assertEqual(calls,["reference","pose"]);self.assertIsNone(old())
            self.assertEqual(conditions[-1]["controlnet_conditioning_scale"],.3)
            self.assertEqual(conditions[-1]["image"].size,(768,432))

    def test_empty_model_settings_do_not_claim_a_configured_engine(self):
        with patch.dict(os.environ,{"STUDYFORGE_MODEL":"","STUDYFORGE_VIDEO_MODEL":""}):
            self.assertFalse(server.configured_model("STUDYFORGE_MODEL"));self.assertFalse(server.configured_model("STUDYFORGE_VIDEO_MODEL"))
        with patch.dict(os.environ,{"STUDYFORGE_MODEL":str(self.folder)}): self.assertTrue(server.configured_model("STUDYFORGE_MODEL"))
