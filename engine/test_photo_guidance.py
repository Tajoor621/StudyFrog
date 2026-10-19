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
