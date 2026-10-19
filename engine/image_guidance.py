"""Optional, strictly local SD 1.x image conditioning; no model downloads."""
import os
from pathlib import Path
from PIL import Image, ImageOps

MODES = ("none", "canny", "depth", "pose", "reference", "depth_photo", "pose_photo")

def base_mode(mode):
    return {"depth_photo":"depth", "pose_photo":"pose"}.get(mode,mode)
VARIABLES = {"canny":"STUDYFORGE_CONTROLNET_CANNY", "depth":"STUDYFORGE_CONTROLNET_DEPTH", "pose":"STUDYFORGE_CONTROLNET_POSE", "reference":"STUDYFORGE_IP_ADAPTER"}

def directory(mode):
    mode=base_mode(mode)
    if mode not in VARIABLES: raise ValueError("Choose a configured image-guidance mode")
    value = os.environ.get(VARIABLES[mode], "")
    if not value or not Path(value).is_dir():
        raise RuntimeError(f"Set {VARIABLES[mode]} to a complete local SD 1.x compatible model folder. No weights are downloaded.")
    return Path(value).resolve()

def cache_key(model, mode):
    if mode not in MODES: raise ValueError("Unknown image-guidance mode")
    return (str(Path(model).resolve()), mode, str(directory(mode)) if mode != "none" else "")

def load_pipeline(model, mode, dtype):
    mode=base_mode(mode)
    from diffusers import StableDiffusionPipeline, StableDiffusionControlNetPipeline, ControlNetModel
    options = dict(local_files_only=True, use_safetensors=True, torch_dtype=dtype)
    if mode in ("canny", "depth", "pose"):
        controlnet = ControlNetModel.from_pretrained(str(directory(mode)), **options)
        return StableDiffusionControlNetPipeline.from_pretrained(model, controlnet=controlnet, **options)
    if mode == "reference":
        from transformers import CLIPVisionModelWithProjection
        folder = directory(mode)
        if not (folder/"ip-adapter_sd15.safetensors").is_file() or not (folder/"image_encoder").is_dir():
            raise RuntimeError("Reference guidance needs ip-adapter_sd15.safetensors and a complete local image_encoder folder")
        encoder = CLIPVisionModelWithProjection.from_pretrained(str(folder/"image_encoder"), **options)
        pipeline = StableDiffusionPipeline.from_pretrained(model, image_encoder=encoder, **options)
        # The encoder is explicitly loaded locally; do not allow the adapter loader to obtain one.
        pipeline.load_ip_adapter(str(folder), subfolder="", weight_name="ip-adapter_sd15.safetensors", image_encoder_folder=None, local_files_only=True)
        return pipeline
    return StableDiffusionPipeline.from_pretrained(model, **options)

def condition(image, mode, width, height):
    source = ImageOps.fit(image.convert("RGB"), (width, height), method=Image.Resampling.LANCZOS)
    if mode in ("depth_photo", "pose_photo"):
        from photo_guidance import prepare
        return prepare(source,mode)
    if mode == "canny":
        import cv2
        import numpy as np
        gray = cv2.cvtColor(np.array(source), cv2.COLOR_RGB2GRAY)
        edges = cv2.Canny(gray, 100, 200)
        return Image.fromarray(edges).convert("RGB")
    if mode == "depth": return source.convert("L").convert("RGB")
    if mode in ("pose", "reference"): return source
    raise ValueError("This mode does not take a conditioning map")

def capabilities():
    result={mode:bool(os.environ.get(name)) and Path(os.environ[name]).is_dir() for mode,name in VARIABLES.items()}
    from photo_guidance import capabilities as photo_capabilities
    return {**result, **{mode:ready and result[base_mode(mode)] for mode,ready in photo_capabilities().items()}}
