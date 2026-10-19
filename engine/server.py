"""StudyForge local-only engine. No remote inference, implicit downloads or paid APIs."""
from __future__ import annotations
import ipaddress
import gc
import sys
import base64, binascii, hmac, io, json, os, queue, re, secrets, shutil, subprocess, threading, uuid, time, tempfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from PIL import Image, ImageDraw, ImageOps

Image.MAX_IMAGE_PIXELS = 40_000_000
ROOT = Path(os.environ.get("STUDYFORGE_DATA", Path(__file__).parent / "data"))
ROOT.mkdir(parents=True, exist_ok=True)
TOKEN = os.environ.get("STUDYFORGE_TOKEN") or secrets.token_urlsafe(32)
JOBS = queue.Queue(maxsize=2)
LOCK = threading.Lock()
UUID = re.compile(r"^[0-9a-f]{32}$")
PIPE = None
PIPE_KEY = None
CANCELLED = set()
class JobCancelled(Exception): pass

def checkpoint(job):
    with LOCK:
        if job.get("id") in CANCELLED: raise JobCancelled("Job cancelled by user")

def cancel_job(job_id):
    with LOCK:
        file=path_for(job_id)
        job=json.loads(file.read_text())
        if job["status"] in ("queued","running","cancelling"):
            CANCELLED.add(job_id);job["status"]="cancelling"
            temp=file.with_suffix(".tmp");temp.write_text(json.dumps(job));temp.replace(file)
        return {"id":job_id,"status":job["status"]}

def delete_job(job_id):
    with LOCK:
        file=path_for(job_id);job=json.loads(file.read_text())
        if job["status"] in ("queued","running","cancelling"): raise RuntimeError("Cancel the job before deleting it")
        for suffix in (".json",".png",".mp4",".source.png",".tmp"):
            (ROOT/(job_id+suffix)).unlink(missing_ok=True)
        CANCELLED.discard(job_id)


def path_for(job_id):
    if not UUID.fullmatch(job_id): raise ValueError("Invalid job id")
    return ROOT / (job_id + ".json")

def save(job):
    with LOCK:
        path = path_for(job["id"])
        tmp = path.with_suffix(".tmp")
        tmp.write_text(json.dumps(job), encoding="utf-8")
        tmp.replace(path)

def read(job_id):
    with LOCK: return json.loads(path_for(job_id).read_text(encoding="utf-8"))

def validate(body):
    if not isinstance(body, dict): raise ValueError("Expected JSON object")
    kind = body.get("kind", "image")
    if kind not in ("image", "diagram", "video", "motion", "refine"): raise ValueError("Unknown kind")
    prompt = body.get("prompt", "")
    if not isinstance(prompt,str) or not 1 <= len(prompt.strip()) <= 2000: raise ValueError("Prompt length must be 1–2000")
    negative=body.get("negative", "")
    if not isinstance(negative,str) or len(negative)>2000: raise ValueError("Negative prompt too long")
    # A guardrail, not a comprehensive moderation system or child-safe guarantee.
    if re.search(r"\b(porn|pornographic|sexually explicit|sexual intercourse)\b", prompt, re.I):
        raise ValueError("Explicit sexual generation is not supported")
    values={}
    for key,default,low,high in (("width",768,256,1024),("height",432,256,1024),("steps",20,10,40),("seed",42,0,2147483647)):
        value=body.get(key,default)
        if kind=="refine" and key in ("width","height"): high=7680 if key=="width" else 4320
        if type(value) is not int or not low<=value<=high: raise ValueError(f"Invalid {key}")
        if key in ("width","height") and value%8: raise ValueError("Dimensions must be multiples of 8")
        values[key]=value
    for key,default,low,high in (("strength",.65,.1,1.0),("guidance",7.5,1.0,15.0)):
        value=body.get(key,default)
        if type(value) not in (int,float) or not low<=value<=high: raise ValueError(f"Invalid {key}")
        values[key]=float(value)
    picture=body.get("image")
    if picture is not None:
        if not isinstance(picture,str) or len(picture)>12_000_000: raise ValueError("Image too large")
        decode_image(picture)  # Reject corrupt input before queueing it.
    if kind in ("video","motion","refine") and not picture: raise ValueError("Video/refine requires an image")
    from image_guidance import MODES
    control=body.get("control","none");scale=body.get("control_scale",.7)
    if not isinstance(control,str) or control not in MODES: raise ValueError("Unknown image-guidance mode")
    if type(scale) not in (int,float) or not 0<=scale<=1: raise ValueError("Image guidance strength must be 0–1")
    if control!="none" and (kind!="image" or not picture): raise ValueError("Image guidance requires an image-generation job with a photo or prepared map")
    return dict(kind=kind,prompt=prompt,negative=negative,image=picture,control=control,control_scale=float(scale),**values)

def decode_image(encoded):
    try:
        raw=base64.b64decode(encoded,validate=True)
        with Image.open(io.BytesIO(raw)) as im:
            if im.width*im.height>40_000_000: raise ValueError("Image exceeds pixel limit")
            im.load()
            return ImageOps.exif_transpose(im).convert("RGB")
    except (ValueError, OSError, binascii.Error, Image.DecompressionBombError) as exc:
        raise ValueError("Invalid or excessive image") from exc

def schematic(prompt, width, height):
    im=Image.new("RGB",(width,height),"#0f172a");draw=ImageDraw.Draw(im)
    draw.text((24,24),"StudyForge / illustrative template",fill="white")
    draw.text((24,48),prompt[:95],fill="#5eead4")
    for i,label in enumerate(("Whole", "System", "Organ", "Cell")):
        x=width*(i+.5)/4;y=height*.52;r=min(width/11,height/5)
        draw.ellipse((x-r,y-r,x+r,y+r),outline="#5eead4",width=3)
        draw.ellipse((x-r/3,y-r/3,x+r/3,y+r/3),fill="#c084fc")
        draw.text((x-r,y+r+15),label,fill="white")
    draw.text((24,height-28),"Schematic only; not a validated anatomical diagram",fill="white")
    return im

def release_image_pipeline():
    """Single-worker boundary: free cached SD before/after a motion-model job."""
    global PIPE, PIPE_KEY
    PIPE=None;PIPE_KEY=None
    gc.collect()
    torch=sys.modules.get("torch")
    if torch is not None:
        try:
            if torch.cuda.is_available(): torch.cuda.empty_cache()
        except RuntimeError:
            pass  # A failed CUDA context must not terminate the job worker during cleanup.

def diffusion(job):
    global PIPE, PIPE_KEY
    checkpoint(job)
    model=os.environ.get("STUDYFORGE_MODEL", "")
    if not model or not Path(model).is_dir():
        raise RuntimeError("Set STUDYFORGE_MODEL to a local SD 1.x Diffusers folder with its safety checker. No model is bundled.")
    import torch
    from diffusers import StableDiffusionImg2ImgPipeline
    from image_guidance import cache_key, load_pipeline, condition
    mode=job.get("control","none");key=cache_key(model,mode)
    source=None
    if mode in ("depth_photo","pose_photo"):
        release_image_pipeline()
        source=condition(decode_image(job["image"]),mode,job["width"],job["height"])
        checkpoint(job)
    if PIPE_KEY!=key: release_image_pipeline()
    if PIPE is None:
        dtype=torch.float16 if torch.cuda.is_available() else torch.float32
        candidate=load_pipeline(model,mode,dtype)
        if candidate.safety_checker is None: raise RuntimeError("This build requires a model safety checker")
        candidate.enable_attention_slicing()
        candidate.enable_vae_tiling()
        candidate.to("cuda" if torch.cuda.is_available() else "cpu")
        PIPE=candidate;PIPE_KEY=key
    generator=torch.Generator(device=PIPE.device).manual_seed(job["seed"])
    args=dict(prompt=job["prompt"],negative_prompt=job["negative"],num_inference_steps=job["steps"],generator=generator,
        guidance_scale=job.get("guidance",7.5),callback_on_step_end=lambda pipe,step,timestep,kwargs: (checkpoint(job),kwargs)[1])
    if mode!="none":
        if source is None: source=condition(decode_image(job["image"]),mode,job["width"],job["height"])
        if mode=="reference":
            PIPE.set_ip_adapter_scale(job.get("control_scale",.7))
            result=PIPE(ip_adapter_image=source,width=job["width"],height=job["height"],**args)
        else: result=PIPE(image=source,controlnet_conditioning_scale=job.get("control_scale",.7),width=job["width"],height=job["height"],**args)
    elif job.get("image"):
        pipe=StableDiffusionImg2ImgPipeline(**PIPE.components)
        source=ImageOps.fit(decode_image(job["image"]),(job["width"],job["height"]))
        result=pipe(image=source,strength=job.get("strength",.65),**args)
    else:
        result=PIPE(width=job["width"],height=job["height"],**args)
    if result.nsfw_content_detected and any(result.nsfw_content_detected):
        raise RuntimeError("The model's safety checker blocked this output")
    return result.images[0]

def tiled_refine(job, generate=diffusion):
    """Resize followed by overlapping 768px diffusion patches. Not native 8K denoising."""
    width,height=job["width"],job["height"]
    source=ImageOps.fit(decode_image(job["image"]),(width,height),method=Image.Resampling.LANCZOS)
    result=source.copy()
    tile=768;overlap=128;stride=tile-overlap
    def positions(length):
        if length<=tile: return [0]
        return sorted(set(list(range(0,length-tile+1,stride))+[length-tile]))
    for y in positions(height):
        for x in positions(width):
            checkpoint(job)
            tw=min(tile,width-x);th=min(tile,height-y)
            patch=source.crop((x,y,x+tw,y+th))
            buffer=io.BytesIO();patch.save(buffer,format="PNG")
            request={**job,"kind":"image","width":tw,"height":th,"image":base64.b64encode(buffer.getvalue()).decode()}
            generated=generate(request).convert("RGB")
            mask=Image.new("L",(tw,th),255)
            # Feather only interior edges so outer image borders are fully generated.
            mp=mask.load()
            for py in range(th):
                wy=min(1.0,py/overlap if y>0 else 1.0,(th-1-py)/overlap if y+th<height else 1.0)
                for px in range(tw):
                    wx=min(1.0,px/overlap if x>0 else 1.0,(tw-1-px)/overlap if x+tw<width else 1.0)
                    mp[px,py]=int(255*wx*wy)
            result.paste(generated,(x,y),mask)
    return result

def reveal_video(image,output,seconds=30,width=1920,height=1080,fps=24,cancel=None):
    """Progressive left-to-right still reveal. NOT AI motion or a drawing simulation."""
    if not seconds>=3: raise ValueError("Video duration must be at least 3 seconds")
    executable=shutil.which("ffmpeg")
    if not executable: raise RuntimeError("Install free FFmpeg to render video")
    src=output.with_suffix(".source.png")
    ImageOps.pad(image,(width,height),color="#0f172a").save(src)
    # Blend original with a dark canvas, exposing successive pixels with time.
    graph=f"[0:v]format=yuv420p,split[a][b];[b]lut=y=24:u=128:v=128[dark];[a][dark]blend=all_expr='if(lte(X,W*T/{seconds-2}),A,B)'[v]"
    try:
        command=[executable,"-hide_banner","-loglevel","error","-y","-loop","1","-framerate",str(fps),"-i",str(src),"-filter_complex",graph,"-map","[v]","-t",str(seconds),"-r",str(fps),"-c:v","libx264","-preset","veryfast","-crf","20","-pix_fmt","yuv420p","-movflags","+faststart",str(output)]
        with tempfile.TemporaryFile() as errors:
            process=subprocess.Popen(command,stdout=subprocess.DEVNULL,stderr=errors)
            deadline=time.monotonic()+1800
            try:
                while process.poll() is None:
                    if cancel: cancel()
                    if time.monotonic()>deadline: raise TimeoutError("Video encoder timed out")
                    threading.Event().wait(.1)
                if process.returncode:
                    errors.seek(0);raise RuntimeError("Video encoder failed: "+errors.read(400).decode(errors="replace"))
            finally:
                if process.poll() is None: process.kill();process.wait()

    finally: src.unlink(missing_ok=True)

def process(job):
    job["status"]="running";save(job)
    output=None
    try:
        checkpoint(job)
        output=ROOT/(job["id"]+(".mp4" if job["kind"] in ("video","motion") else ".png"))
        if job["kind"]=="motion":
            release_image_pipeline()
            from motion_video import render
            def update(value): job["progress"]=value;save(job)
            render(decode_image(job["image"]),output,job["prompt"],job["negative"],job["seed"],job["steps"],lambda: checkpoint(job),update)
        elif job["kind"]=="video": reveal_video(decode_image(job["image"]),output,cancel=lambda: checkpoint(job))
        elif job["kind"]=="refine": tiled_refine(job).save(output)
        elif job["kind"]=="diagram": schematic(job["prompt"],job["width"],job["height"]).save(output)
        else: diffusion(job).save(output)
        checkpoint(job)
        job["status"]="done"
    except JobCancelled:
        job["status"]="cancelled"
    except Exception as exc:
        job["status"]="failed";job["error"]=str(exc)[:400]
    finally:
        with LOCK:
            if job["id"] in CANCELLED: job["status"]="cancelled"
            if job["status"]!="done" and output: output.unlink(missing_ok=True)
            job.pop("image",None)
            path=path_for(job["id"]);temp=path.with_suffix(".tmp")
            temp.write_text(json.dumps(job));temp.replace(path)
            CANCELLED.discard(job["id"])
        if job["kind"]=="motion": release_image_pipeline()

def work():
    while True:
        job=JOBS.get()
        try: process(job)
        finally: JOBS.task_done()

class Handler(BaseHTTPRequestHandler):
    def log_message(self,*args): pass  # No prompts, photos or bearer tokens in request logs.
    def reply(self,code,data):
        data=json.dumps(data).encode();self.send_response(code);self.send_header("Content-Type","application/json");self.send_header("Content-Length",str(len(data)));self.send_header("Cache-Control","no-store");self.end_headers();self.wfile.write(data)
    def authorized(self):
        # Browser-origin requests are rejected; clients use native HTTP.
        if self.headers.get("Origin") or not hmac.compare_digest(self.headers.get("Authorization","").encode(),("Bearer "+TOKEN).encode()):
            self.reply(401,{"error":"Unauthorized"});return False
        return True
    def do_POST(self):
        if not self.authorized(): return
        if re.fullmatch(r"/jobs/[0-9a-f]{32}/cancel",self.path):
            try: self.reply(200,cancel_job(self.path.split("/")[2]))
            except (ValueError,FileNotFoundError): self.reply(404,{"error":"Not found"})
            return
        if self.path not in ("/jobs","/ocr"): self.reply(404,{"error":"Not found"});return
        try:
            n=int(self.headers.get("Content-Length","0"))
            if not 0<n<=13_000_000: raise ValueError("Invalid body size")
            body=json.loads(self.rfile.read(n))
            if self.path=="/ocr":
                from study_ocr import extract, BusyError
                if not isinstance(body,dict) or not isinstance(body.get("image"),str) or len(body["image"])>12_000_000: raise ValueError("Provide a photo up to 12 MB encoded")
                try: self.reply(200,extract(decode_image(body["image"]),body.get("language","eng")))
                except BusyError: self.reply(429,{"error":"OCR is busy; retry shortly"})
                except RuntimeError as exc: self.reply(503,{"error":str(exc)[:200]})
                return
            job=validate(body)
            job.update(id=uuid.uuid4().hex,status="queued",created=time.time())
            save(job)
            try: JOBS.put_nowait(job)
            except queue.Full:
                path_for(job["id"]).unlink(missing_ok=True)
                self.reply(429,{"error":"Queue full; try later"});return
            self.reply(202,{"id":job["id"],"status":"queued"})
        except (ValueError,TypeError,UnicodeDecodeError) as exc: self.reply(400,{"error":str(exc)[:200]})
    def do_GET(self):
        if not self.authorized(): return
        try:
            if self.path=="/jobs":
                items=[]
                for path in sorted(ROOT.glob("*.json"),key=lambda p:p.stat().st_mtime,reverse=True)[:50]:
                    try:
                        job=read(path.stem)
                        items.append({k:job[k] for k in ("id","kind","status","created") if k in job})
                    except (OSError,ValueError): continue
                self.reply(200,{"jobs":items});return
            if self.path=="/health":
                self.reply(200,{"local_only":True,"model_configured":Path(os.environ.get("STUDYFORGE_MODEL","/__unconfigured__")).is_dir(),"video":"30-second reveal; optional LTX motion model", "motion_configured":Path(os.environ.get("STUDYFORGE_VIDEO_MODEL","/__unconfigured__")).is_dir(), "image_guidance":__import__("image_guidance").capabilities(), "ocr_available":bool(shutil.which("tesseract"))});return
            prefix,job_id=self.path.strip("/").split("/",1)
            if prefix not in ("jobs","results"): raise ValueError("Unknown route")
            job=read(job_id)
            if prefix=="jobs": self.reply(200,{k:v for k,v in job.items() if k!="image"});return
            if job["status"]!="done": self.reply(409,{"error":"Result not ready"});return
            result=ROOT/(job_id+(".mp4" if job["kind"] in ("video","motion") else ".png"))
            self.send_response(200);self.send_header("Content-Type","video/mp4" if job["kind"] in ("video","motion") else "image/png");self.send_header("Content-Length",str(result.stat().st_size));self.send_header("Cache-Control","no-store");self.end_headers()
            with result.open("rb") as source: shutil.copyfileobj(source,self.wfile)
        except (ValueError,FileNotFoundError): self.reply(404,{"error":"Not found"})

    def do_DELETE(self):
        if not self.authorized(): return
        try:
            prefix,job_id=self.path.strip("/").split("/",1)
            if prefix!="jobs": raise ValueError("Unknown route")
            delete_job(job_id);self.reply(200,{"id":job_id,"status":"deleted"})
        except (ValueError,FileNotFoundError): self.reply(404,{"error":"Not found"})
        except RuntimeError as exc: self.reply(409,{"error":str(exc)})

class Server(ThreadingHTTPServer):
    daemon_threads=True
    def get_request(self):
        connection,address=super().get_request();connection.settimeout(30);return connection,address

def main():
    # Never silently treat jobs interrupted by a restart as completed.
    for file in ROOT.glob("*.json"):
        try: job=json.loads(file.read_text());path_for(job["id"])
        except (ValueError,KeyError,OSError): continue
        if job.get("status") in ("queued","running","cancelling"):
            job.update(status="failed",error="Engine restarted; resubmit this job");job.pop("image",None);save(job)
    threading.Thread(target=work,daemon=True).start()
    bind=os.environ.get("STUDYFORGE_BIND","127.0.0.1")
    address=ipaddress.ip_address(bind)
    if address.is_unspecified or address.is_multicast or not (address.is_loopback or address.is_private): raise ValueError("Bind only to loopback or your explicit private LAN address")
    if address.version!=4: raise ValueError("This server uses an IPv4 loopback/private LAN address")
    print(f"StudyForge local engine http://{bind}:8765",flush=True)
    print("Session token:",TOKEN,flush=True)
    Server((bind,8765),Handler).serve_forever()

if __name__=="__main__": main()
