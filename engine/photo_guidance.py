"""Optional CPU photo preprocessors. Every model/processor is loaded from disk only.

Depth uses a Transformers depth estimator. Pose uses a local RT-DETR person
recognizer followed by COCO-17 ViTPose and renders an OpenPose-style body map.
No face/hand landmarks, downloaded code, network inference or persistent photos.
"""
import gc
import os
from pathlib import Path
from PIL import Image, ImageDraw

ENV = {"depth_photo":("STUDYFORGE_DEPTH_ESTIMATOR",),
       "pose_photo":("STUDYFORGE_PERSON_DETECTOR","STUDYFORGE_POSE_ESTIMATOR")}

def folders(mode):
    if mode not in ENV: raise ValueError("Unknown photo preparation mode")
    paths=[]
    for name in ENV[mode]:
        value=os.environ.get(name,"")
        if not value or not Path(value).is_dir():
            raise RuntimeError(f"Set {name} to a complete local safetensors model folder. No weights are downloaded.")
        paths.append(str(Path(value).resolve()))
    return paths

def capabilities():
    return {mode:all(os.environ.get(name) and Path(os.environ[name]).is_dir() for name in names) for mode,names in ENV.items()}

def depth_map(values, size):
    import numpy as np
    array=np.asarray(values,dtype=np.float32)
    if array.ndim!=2 or not array.size or not np.isfinite(array).all():
        raise RuntimeError("Depth estimator returned invalid values")
    low,high=float(array.min()),float(array.max())
    if high-low<1e-6: raise RuntimeError("Photo has no usable estimated depth variation")
    # Relative inverse depth: bright is nearer. Use a compatible inverse-depth estimator.
    pixels=np.rint((array-low)/(high-low)*255).astype(np.uint8)
    return Image.fromarray(pixels).resize(size,Image.Resampling.BICUBIC).convert("RGB")

# OpenPose COCO-18 body order, including synthesized shoulder midpoint (neck).
ORDER=(0,None,6,8,10,5,7,9,12,14,16,11,13,15,2,1,4,3)
LIMBS=((1,2),(1,5),(2,3),(3,4),(5,6),(6,7),(1,8),(8,9),(9,10),(1,11),(11,12),(12,13),(1,0),(0,14),(14,16),(0,15),(15,17))
COLORS=((255,0,0),(255,85,0),(255,170,0),(255,255,0),(170,255,0),(85,255,0),(0,255,0),(0,255,85),(0,255,170),(0,255,255),(0,170,255),(0,85,255),(0,0,255),(85,0,255),(170,0,255),(255,0,255),(255,0,170),(255,0,85))

def pose_map(people,size):
    import numpy as np
    result=Image.new("RGB",size,"black");draw=ImageDraw.Draw(result)
    radius=max(2,round(min(size)/180));drawn=0
    for person in people[:8]:
        points=np.asarray(person["keypoints"],dtype=float);scores=np.asarray(person["scores"],dtype=float).reshape(-1)
        if points.shape!=(17,2) or scores.shape!=(17,) or not np.isfinite(points).all() or not np.isfinite(scores).all():
            raise RuntimeError("Pose estimator must return finite COCO-17 keypoints")
        nodes=[]
        for index in ORDER:
            if index is None:
                point=(points[5]+points[6])/2;confidence=min(scores[5],scores[6])
            else: point=points[index];confidence=scores[index]
            x,y=map(float,point)
            nodes.append((x,y) if confidence>=.3 and 0<=x<size[0] and 0<=y<size[1] else None)
        for index,(a,b) in enumerate(LIMBS):
            if nodes[a] is not None and nodes[b] is not None:
                draw.line((nodes[a],nodes[b]),fill=COLORS[index],width=radius*2);drawn+=1
        for index,node in enumerate(nodes):
            if node is not None:
                x,y=node;draw.ellipse((x-radius,y-radius,x+radius,y+radius),fill=COLORS[index])
    if not drawn: raise RuntimeError("No confident body pose found; use a clearer full-body photo or prepared map")
    return result

def prepare(source,mode):
    paths=folders(mode)
    import torch
    from transformers import AutoImageProcessor, AutoModelForDepthEstimation, RTDetrForObjectDetection, VitPoseForPoseEstimation, VitPoseImageProcessor
    options=dict(local_files_only=True,trust_remote_code=False)
    weights={**options,"use_safetensors":True}
    # Deliberately CPU-only, before SD loads, so preprocessing cannot exhaust GPU RAM.
    if mode=="depth_photo":
        processor=AutoImageProcessor.from_pretrained(paths[0],**options)
        model=AutoModelForDepthEstimation.from_pretrained(paths[0],**weights).eval()
        try:
            with torch.inference_mode(): prediction=model(**processor(images=source,return_tensors="pt")).predicted_depth
            return depth_map(prediction[0].detach().cpu().numpy(),source.size)
        finally: del model;gc.collect()
    processor=AutoImageProcessor.from_pretrained(paths[0],**options)
    detector=RTDetrForObjectDetection.from_pretrained(paths[0],**weights).eval()
    try:
        with torch.inference_mode(): detected=detector(**processor(images=source,return_tensors="pt"))
        result=processor.post_process_object_detection(detected,target_sizes=[(source.height,source.width)],threshold=.5)[0]
        people={int(k) for k,v in detector.config.id2label.items() if str(v).lower()=="person"}
        if not people: raise RuntimeError("Person detector must use a COCO label map with a person class")
        boxes=[]
        for label,box in zip(result["labels"].tolist(),result["boxes"].tolist()):
            if label not in people: continue
            x1,y1,x2,y2=box
            x1=max(0,min(source.width,x1));x2=max(0,min(source.width,x2))
            y1=max(0,min(source.height,y1));y2=max(0,min(source.height,y2))
            if x2-x1>=16 and y2-y1>=16: boxes.append([x1,y1,x2-x1,y2-y1])
            if len(boxes)==8: break
    finally: del detector;gc.collect()
    if not boxes: raise RuntimeError("No person found in this photo; use a clearer image or a prepared pose map")
    processor=VitPoseImageProcessor.from_pretrained(paths[1],**options)
    model=VitPoseForPoseEstimation.from_pretrained(paths[1],**weights).eval()
    try:
        with torch.inference_mode(): outputs=model(**processor(images=source,boxes=[boxes],return_tensors="pt"))
        poses=processor.post_process_pose_estimation(outputs,boxes=[boxes])[0]
        return pose_map([{k:v.detach().cpu().numpy() if hasattr(v,"detach") else v for k,v in item.items()} for item in poses],source.size)
    finally: del model;gc.collect()
