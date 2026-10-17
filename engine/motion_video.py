"""Offline LTX image-to-video. Five chained six-second segments, encoded as 30s/24fps.
Model resolution is 768x448; 1080p output is explicitly an upscale, not native detail.
"""
from pathlib import Path
import os, subprocess, tempfile
from PIL import Image, ImageOps

FRAMES_PER_SEGMENT=144
SEGMENTS=5
FPS=24

def frames_from_segments(initial,generate,checkpoint,progress):
    image=initial
    for segment in range(SEGMENTS):
        checkpoint()
        frames=generate(image,segment)
        if len(frames)!=FRAMES_PER_SEGMENT+1: raise RuntimeError("Video model returned an incomplete segment")
        # Drop the first conditioning frame; each emitted frame has a new timestamp.
        for frame in frames[1:]:
            checkpoint()
            yield ImageOps.fit(frame.convert("RGB"),(768,448),method=Image.Resampling.LANCZOS)
        image=frames[-1]
        progress((segment+1)*100//SEGMENTS)

def render(initial,output,prompt,negative,seed,steps,checkpoint,progress,generate=None):
    if generate is None:
        model=Path(os.environ.get("STUDYFORGE_VIDEO_MODEL","/__unconfigured__"))
        if not model.is_dir(): raise RuntimeError("Configure STUDYFORGE_VIDEO_MODEL with a complete local LTX-Video Diffusers folder")
        import torch
        from diffusers import LTXImageToVideoPipeline
        if not torch.cuda.is_available(): raise RuntimeError("This motion-video adapter requires a CUDA GPU; no remote or reveal fallback is used")
        pipeline=LTXImageToVideoPipeline.from_pretrained(str(model),local_files_only=True,use_safetensors=True,torch_dtype=torch.bfloat16)
        pipeline.enable_model_cpu_offload();pipeline.vae.enable_tiling()
        def generate(image,segment):
            checkpoint()
            def callback(pipe,step,timestep,kwargs): checkpoint();return kwargs
            return pipeline(image=ImageOps.fit(image,(768,448)),prompt=prompt,negative_prompt=negative,
                height=448,width=768,num_frames=145,frame_rate=24,num_inference_steps=steps,
                guidance_scale=3.0,generator=torch.Generator(device="cpu").manual_seed(seed+segment),
                callback_on_step_end=callback).frames[0]
    output=Path(output)
    # Drain stderr to disk to avoid deadlocks. No prompt is placed in command arguments.
    with tempfile.TemporaryFile() as errors:
        command=["ffmpeg","-hide_banner","-loglevel","error","-y","-f","rawvideo","-pixel_format","rgb24","-video_size","768x448","-framerate","24","-i","pipe:0","-an","-vf","scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2","-c:v","libx264","-preset","veryfast","-crf","20","-pix_fmt","yuv420p","-frames:v","720","-movflags","+faststart",str(output)]
        process=subprocess.Popen(command,stdin=subprocess.PIPE,stdout=subprocess.DEVNULL,stderr=errors)
        completed=False
        try:
            for frame in frames_from_segments(initial,generate,checkpoint,progress):
                checkpoint();process.stdin.write(frame.tobytes())
            process.stdin.close()
            if process.wait(timeout=120)!=0:
                errors.seek(0);raise RuntimeError("Motion-video encoding failed: "+errors.read(300).decode(errors="replace"))
            completed=True
        finally:
            if process.poll() is None: process.kill();process.wait()
            if process.stdin and not process.stdin.closed: process.stdin.close()
            if not completed: output.unlink(missing_ok=True)
