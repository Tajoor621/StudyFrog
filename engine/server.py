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

def configured_model(name):
    value=os.environ.get(name,"")
    return bool(value) and Path(value).is_dir()


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
                self.reply(200,{"local_only":True,"model_configured":configured_model("STUDYFORGE_MODEL"),"video":"30-second reveal; optional LTX motion model", "motion_configured":configured_model("STUDYFORGE_VIDEO_MODEL"), "image_guidance":__import__("image_guidance").capabilities(), "ocr_available":bool(shutil.which("tesseract"))});return
            prefix,job_id=self.path.strip("/").split("/",1)
            if prefix not in ("jobs","results"): raise ValueError("UnknownPOST':'GET'){
 const response=await fetch(base()+path,{method,headers:body?{'Content-Type':'application/json'}:{},body:body?JSON.stringify(body):undefined,cache:'no-store'});
 const data=await response.json();if(!response.ok)throw Error(data.error||'Local request failed');return data;
}
function picture(){return el('canvas').toDataURL('image/png').split(',')[1];}
function original(blob,name){
 if(resultURL)URL.revokeObjectURL(resultURL);resultURL=URL.createObjectURL(blob);
 if(blob.type==='image/png'){el('engineVideo').pause();el('engineVideo').removeAttribute('src');el('engineVideo').load();el('engineVideo').hidden=true;}
 const anchor=el('engineDownload');anchor.href=resultURL;anchor.download=name;anchor.hidden=false;
}
el('engineBackend').onchange=()=>{el('engineModel').disabled=el('engineBackend').value!=='ollama';};
el('engineConnect').onclick=async()=>{
 if(busy)return;setBusy(true);
 try{
  status('Checking your computer…');
  if(el('engineBackend').value==='sd'){
   const health=await json('/engine/health');
   const ready=Object.entries(health.image_guidance||{}).filter(([,value])=>value).map(([name])=>name).join(', ')||'none';
   status(`Local engine connected. Image model: ${health.model_configured?'configured':'missing'}. Motion: ${health.motion_configured?'configured':'missing'}. OCR: ${health.ocr_available?'installed':'missing'}. Guidance: ${ready}. Configuration does not prove model compatibility.`);
  }else{
   const data=await json('/health');el('engineModel').replaceChildren();
   for(const name of data.models||[]){const option=document.createElement('option');option.value=name;option.textContent=name;el('engineModel').append(option);}
   status('Connected. Choose an installed compatible image model.');
  }
 }catch(error){status(error.message);}finally{setBusy(false);}
};
el('engineCancel').onclick=async()=>{
 if(!job)return;
 try{await json(`/engine/jobs/${job}/cancel`,{});status('Cancellation requested; waiting for the next safe processing step.');}
 catch(error){status(error.message);}
};
async function deliver(id,video){
 const response=await fetch(base()+`/engine/results/${id}`,{cache:'no-store'});
 if(!response.ok)throw Error('Result download failed');
 const length=Number(response.headers.get('Content-Length'));if(!Number.isFinite(length)||length<=0||length>256000000)throw Error('Result exceeds download limit');
 const blob=await response.blob();original(blob,video?'studyfrog-30s.mp4':'studyfrog-original.png');
 el('engineVideo').hidden=!video;
 if(video){el('engineVideo').src=resultURL;}
 else{const image=new Image();image.src=resultURL;await image.decode();window.dispatchEvent(new CustomEvent('studyforge-image',{detail:image}));}
 status('Result ready. Download the original to preserve its full resolution. Review before study use.');
}
el('engineGenerate').onclick=async()=>{
 if(busy)return;setBusy(true);
 try{
  const prompt=effectivePrompt(),negative=el('negative').value;previewPrompt();
  if(!el('prompt').value.trim()||prompt.length>2000||negative.length>2000)throw Error('Use a prompt of 1–2000 characters including the selected visual direction, and negative prompt up to 2000.');
  const width=Number(el('engineWidth').value),height=Number(el('engineHeight').value),steps=Number(el('engineSteps').value),seed=Number(el('engineSeed').value);
  if(![width,height,steps,seed].every(Number.isInteger)||width<256||width>1024||height<256||height>1024||width%8||height%8||steps<10||steps>40||seed<0||seed>2147483647)throw Error('Check dimensions (multiples of 8), steps (10–40) and seed.');
  if(el('engineBackend').value==='ollama'){
   if(negative.trim())throw Error('Ollama image bridge has no negative conditioning. Choose StudyForge to use your negative prompt.');
   if(el('engineKind').value!=='image'||el('engineControl').value!=='none'||el('enginePhoto').checked)throw Error('This Ollama bridge supports text-to-image only. Choose StudyForge for guidance, photos, refinement or video.');
   const model=el('engineModel').value;if(!model)throw Error('Connect and select an installed image model.');
   status('Generating on your computer…');const data=await json('/generate',{model,prompt,width,height,steps});
   if(typeof data.image!=='string'||data.image.length>24000000)throw Error('Invalid image response');
   const bytes=Uint8Array.from(atob(data.image),character=>character.charCodeAt(0));original(new Blob([bytes],{type:'image/png'}),'studyfrog-original.png');
   const image=new Image();image.src=resultURL;await image.decode();window.dispatchEvent(new CustomEvent('studyforge-image',{detail:image}));status('Image ready. Download the original result.');return;
  }
  const selected=el('engineKind').value,kind=selected.startsWith('refine')?'refine':selected,control=el('engineControl').value;
  if(control!=='none'&&kind!=='image')throw Error('Guidance applies to image generation. Choose None for refinement/video.');
  const body={kind,prompt,negative,width,height,steps,seed,control,control_scale:Number(el('engineScale').value),strength:Number(el('engineStrength').value)};
  if(selected==='refine4k'){body.width=3840;body.height=2160;}
  if(selected==='refine8k'){body.width=7680;body.height=4320;}
  if(el('enginePhoto').checked||control!=='none'||kind!=='image')body.image=picture();
  status('Sending your prompt and selected canvas to your computer…');
  const queued=await json('/engine/jobs',body);if(!/^[0-9a-f]{32}$/.test(queued.id))throw Error('Invalid job identifier');
  job=queued.id;el('engineCancel').disabled=false;
  for(;;){
   const state=await json(`/engine/jobs/${job}`);
   if(state.status==='done'){await deliver(job,['video','motion'].includes(kind));break;}
   if(['failed','cancelled'].includes(state.status))throw Error(state.error||'Job cancelled');
   status(`Computer job: ${state.status}${state.progress!==undefined?' · '+state.progress+'%':''}. Keep this page open.`);
   await new Promise(resolve=>setTimeout(resolve,2000));
  }
 }catch(error){status(error.message);}finally{job=null;setBusy(false);}
};
el('engineOCR').onclick=async()=>{
 if(busy)return;setBusy(true);
 try{status('Reading text on your computer…');const data=await json('/engine/ocr',{image:picture(),language:el('ocrLanguage').value.trim()});el('ocrReview').value=data.text||'';status(data.text?'Review and correct the extracted text before using it. Nothing was added to your prompt.':'No text found. Try a sharper, cropped photo.');}
 catch(error){status(error.message);}finally{setBusy(false);}
};
el('ocrUse').onclick=()=>{
 const notes=el('ocrReview').value.trim(),prompt='Create a clearly labeled educational illustration from these reviewed notes:\n'+notes;
 if(!notes)return status('Extract or enter notes first.');if(prompt.length>2000)return status('Shorten the reviewed notes to fit the 2000-character prompt limit.');
 el('prompt').value=prompt;previewPrompt();status('Reviewed notes copied to the prompt. Choose a generation engine when ready.');
};
window.addEventListener('pagehide',()=>{if(resultURL)URL.revokeObjectURL(resultURL);});

el('engineJobs').onclick=async()=>{
 try{
  const data=await json('/engine/jobs'),list=el('engineJobList');list.replaceChildren();
  for(const item of data.jobs||[]){
   if(!/^[0-9a-f]{32}$/.test(item.id))continue;
   const row=document.createElement('div'),label=document.createElement('p');label.textContent=`${item.kind} · ${item.status} · ${item.id}`;row.append(label);
   const active=['queued','running','cancelling'].includes(item.status);
   const button=document.createElement('button');button.textContent=active?'Cancel':'Delete from computer';
   button.onclick=async()=>{
    if(!active&&!confirm('Delete this result and prompt history from your computer? Exported files remain available.'))return;
    try{await json(`/engine/jobs/${item.id}${active?'/cancel':''}`,active?{}:undefined,active?'POST':'DELETE');status(active?'Cancellation requested. Refresh jobs to check completion.':'Deleted computer job.');el('engineJobs').click();}catch(error){status(error.message);}
   };row.append(button);
   if(item.status==='done'){
    const download=document.createElement('button');download.textContent='Open result';download.onclick=()=>deliver(item.id,['video','motion'].includes(item.kind)).catch(error=>status(error.message));row.append(download);
   }
   list.append(row);
  }
  if(!list.children.length)list.textContent='No computer jobs yet.';
 }catch(error){status(error.message);}
};
