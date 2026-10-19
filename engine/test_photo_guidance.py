import os
import unittest
from unittest.mock import patch
import numpy as np
from PIL import Image
import photo_guidance
import image_guidance

class PhotoGuidanceTests(unittest.TestCase):
    def test_relative_depth_normalizes_and_rejects_invalid_predictions(self):
        image=photo_guidance.depth_map([[1,2],[3,5]],(2,2))
        self.assertEqual(image.mode,"RGB");self.assertEqual(image.getpixel((0,0)),(0,0,0));self.assertEqual(image.getpixel((1,1)),(255,255,255))
        for values in ([],[1,2],[[1,1]],[[float("nan"),1]],[[float("inf"),1]]):
            with self.subTest(values=values),self.assertRaises(RuntimeError): photo_guidance.depth_map(values,(2,2))
    def test_coco_pose_renders_neck_limbs_and_confidence_gate(self):
        points=np.array([[20+i*5,20+i*6] for i in range(17)])
        result=photo_guidance.pose_map([dict(keypoints=points,scores=np.ones(17))],(128,128))
        self.assertIsNotNone(result.getbbox());self.assertEqual(result.getpixel((0,0)),(0,0,0))
        with self.assertRaisesRegex(RuntimeError,"No confident"): photo_guidance.pose_map([dict(keypoints=points,scores=np.zeros(17))],(128,128))
        with self.assertRaisesRegex(RuntimeError,"COCO-17"): photo_guidance.pose_map([dict(keypoints=points[:10],scores=np.ones(10))],(128,128))
        points=points.astype(float);points[0,0]=float("nan")
        with self.assertRaisesRegex(RuntimeError,"finite"): photo_guidance.pose_map([dict(keypoints=points,scores=np.ones(17))],(128,128))
    def test_missing_estimators_fail_locally_before_importing_torch(self):
        with patch.dict(os.environ,{},clear=True):
            self.assertEqual(photo_guidance.capabilities(),{"depth_photo":False,"pose_photo":False})
            for mode in photo_guidance.ENV:
                with self.assertRaisesRegex(RuntimeError,"No weights are downloaded"): photo_guidance.prepare(Image.new("RGB",(64,64)),mode)
    def test_photo_modes_preserve_prepared_modes_and_target_size(self):
        with patch.object(photo_guidance,"prepare",return_value=Image.new("RGB",(256,512))) as prepare:
            image_guidance.condition(Image.new("RGB",(32,32)),"depth_photo",256,512)
            self.assertEqual(prepare.call_args.args[0].size,(256,512));self.assertEqual(prepare.call_args.args[1],"depth_photo")
        self.assertEqual(image_guidance.base_mode("depth_photo"),"depth");self.assertEqual(image_guidance.base_mode("pose_photo"),"pose")
        self.assertEqual(image_guidance.base_mode("pose"),"pose")

class EstimatorFlowTests(unittest.TestCase):
    def test_local_cpu_depth_and_person_pose_loaders_use_only_local_safetensors(self):
        import contextlib
        import sys
        import tempfile
        import types
        from pathlib import Path
        calls=[]
        class Tensor:
            def __init__(self,value): self.value=np.asarray(value)
            def detach(self): return self
            def cpu(self): return self
            def numpy(self): return self.value
            def tolist(self): return self.value.tolist()
            def __getitem__(self,index): return Tensor(self.value[index])
        class Processor:
            @classmethod
            def from_pretrained(cls,path,**kwargs): calls.append((cls.__name__,path,kwargs));return cls()
            def __call__(self,**kwargs): return {"pixel_values":kwargs["images"]}
            def post_process_object_detection(self,*args,**kwargs): return [dict(labels=Tensor([0]),boxes=Tensor([[4,4,124,124]]))]
            def post_process_pose_estimation(self,*args,**kwargs):
                self.boxes=kwargs["boxes"]
                return [[dict(keypoints=Tensor([[20+i*4,20+i*5] for i in range(17)]),scores=Tensor(np.ones(17)))]]
        class PoseProcessor(Processor): pass
        class Depth:
            @classmethod
            def from_pretrained(cls,path,**kwargs): calls.append((cls.__name__,path,kwargs));return cls()
            def eval(self): return self
            def __call__(self,**kwargs): return types.SimpleNamespace(predicted_depth=Tensor([[[0,1],[2,3]]]))
        class Detector(Depth):
            config=types.SimpleNamespace(id2label={0:"person"})
            def __call__(self,**kwargs): return object()
        class Pose(Depth):
            def __call__(self,**kwargs): return object()
        transformers=types.SimpleNamespace(AutoImageProcessor=Processor,VitPoseImageProcessor=PoseProcessor,AutoModelForDepthEstimation=Depth,RTDetrForObjectDetection=Detector,VitPoseForPoseEstimation=Pose)
        with tempfile.TemporaryDirectory() as temp,patch.dict(sys.modules,{"torch":types.SimpleNamespace(inference_mode=contextlib.nullcontext),"transformers":transformers}),patch.dict(os.environ,{name:temp for names in photo_guidance.ENV.values() for name in names}):
            for mode in photo_guidance.ENV:
                result=photo_guidance.prepare(Image.new("RGB",(128,128)),mode);self.assertEqual(result.size,(128,128));self.assertIsNotNone(result.getbbox())
        self.assertTrue(all(kwargs["local_files_only"] and kwargs["trust_remote_code"] is False for _,_,kwargs in calls))
        self.assertTrue(all(kwargs["use_safetensors"] for name,_,kwargs in calls if name in ("Depth","Detector","Pose")))
        self.assertIn("PoseProcessor",[name for name,_,_ in calls])
