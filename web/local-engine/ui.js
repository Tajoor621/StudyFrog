const panel=document.createElement('section');panel.className='panel';
panel.innerHTML=`<h2>Free local generation</h2>
<p>Your own computer runs the models. Open this app through the local bridge, or your private HTTPS Tailscale Serve URL. GitHub Pages provides the editor and cannot run the computer engine.</p>
<details><summary>First run · choose your setup</summary>
<p>Start with the editor, import a photo, or use bundled study content without an AI model. Nothing is downloaded automatically.</p>
<ol><li>On Windows, run scripts/start-windows.ps1 -Mode Check from your checkout, then start Engine and Web in two terminals using the same private session token.</li><li>For image generation, configure your own local SD 1.x Diffusers folder with safetensors and its safety checker. A phone GGUF file is a different model format and cannot be used by this computer adapter.</li><li>Open http://127.0.0.1:8766 and check the engine. Keep the source/license of each model. Missing video/OCR tools only disable their own features.</li></ol>
</details>
<label for="engineBackend">Local engine</label><select id="engineBackend"><option value="sd">StudyForge SD / ControlNet / LTX</option><option value="ollama">Ollama compatible image model</option></select>
<button id="engineConnect">Check local engine</button>
<label for="engineModel">Installed Ollama image model</label><select id="engineModel" disabled><option value="">Connect to list models</option></select>
<label for="enginePromptPreview">Prompt sent to computer</label><textarea id="enginePromptPreview" rows="4" readonly aria-label="Effective generation prompt"></textarea>
<label for="engineKind">Output</label><select id="engineKind"><option value="image">Image</option><option value="refine4k">4K tiled refinement of canvas</option><option value="refine8k">8K tiled refinement of canvas</option><option value="video">30-second still reveal · 1080p/24fps</option><option value="motion">30-second AI motion · local LTX/CUDA</option></select>
<label for="enginePhoto"><input id="enginePhoto" type="checkbox"> Use current canvas for photo conversion</label>
<label for="engineControl">Image guidance</label><select id="engineControl"><option value="none">None</option><option value="canny">Canny edges from canvas</option><option value="depth_photo">Estimate depth from canvas photo</option><option value="pose_photo">Detect body pose from canvas photo</option><option value="depth">Prepared depth map on canvas</option><option value="pose">Prepared OpenPose body map on canvas</option><option value="reference">IP-Adapter reference photo</option></select>
<label for="engineScale">Guidance strength</label><input id="engineScale" type="range" min="0" max="1" step="0.05" value="0.7">
<label for="engineWidth">Image width</label><input id="engineWidth" type="number" min="256" max="1024" step="8" value="768">
<label for="engineHeight">Image height</label><input id="engineHeight" type="number" min="256" max="1024" step="8" value="432">
<label for="engineSteps">Steps</label><input id="engineSteps" type="number" min="10" max="40" value="20">
<label for="engineSeed">Seed</label><input id="engineSeed" type="number" min="0" max="2147483647" value="42">
<label for="engineStrength">Photo transformation strength</label><input id="engineStrength" type="range" min="0.1" max="1" step="0.05" value="0.65">
<div class="actions"><button id="engineGenerate">Generate with local computer</button><button id="engineCancel" disabled>Cancel job</button></div>
<details><summary>Video remix · keep people and original sound</summary>
<p>Import or generate the new background on the canvas first. Then upload a video to your own processing computer. People and faces keep their original motion; the first original audio track is retained as AAC. This changes the background, without voice cloning or face reenactment.</p>
<label for="remixFile">Source MP4, MOV or WebM</label><input id="remixFile" type="file" accept="video/mp4,video/quicktime,video/webm">
<label for="remixProfile">Output budget</label><select id="remixProfile"><option value="mobile">Mobile · 15s · up to 480p · 15fps · 64 MB input</option><option value="laptop">Laptop · 30s · up to 720p · 24fps · 128 MB input</option><option value="desktop">Desktop · 60s · up to 1080p · 30fps · 128 MB input</option></select>
<label for="remixMode">Background removal</label><select id="remixMode"><option value="person">Person segmentation · local PPHumanSeg ONNX required</option><option value="green">Green screen · no model, removes green clothing too</option></select>
<label for="remixStart">Source start, seconds</label><input id="remixStart" type="number" min="0" max="3600" step="0.5" value="0">
<label for="remixSeconds">Clip duration, seconds</label><input id="remixSeconds" type="number" min="0.5" max="15" step="0.5" value="15">
<label for="remixRights"><input id="remixRights" type="checkbox"> I have permission to edit the people, video and soundtrack.</label>
<button id="remixGenerate">Replace video background with canvas</button>
<p>Portrait framing is preserved and small videos are not upscaled. Segmentation can miss hair, hands or people; inspect the result. Processing is local to the chosen computer, including when controlled from a phone. No phone-only AI remix is claimed. Unused uploads expire after 30 minutes; used source copies are deleted when jobs finish or fail. Delete results in Manage computer jobs.</p>
</details>
<button id="engineJobs">Manage computer jobs</button><div id="engineJobList"></div>
<p>Guidance and OCR need separately installed local models/tools. Pose is body-only; depth is relative, not a medical measurement. LTX output is scaled to 1080p; model quality and 8K refinement depend on your hardware.</p>
<label for="ocrLanguage">Installed OCR languages</label><input id="ocrLanguage" value="eng" maxlength="11" placeholder="eng or eng+urd">
<button id="engineOCR">Read canvas photo text on local computer</button>
<label for="ocrReview">Review and correct extracted text</label><textarea id="ocrReview" rows="6" maxlength="20000" placeholder="OCR is fallible. Check every label and scientific statement before use."></textarea>
<button id="ocrUse">Use reviewed text in prompt</button>
<p id="engineStatus" role="status" aria-live="polite"></p>
<a id="engineDownload" hidden>Download original result</a><video id="engineVideo" controls hidden aria-label="Generated video preview"></video>`;
document.querySelector('#studio .grid > .panel').append(panel);
const el=id=>document.getElementById(id),status=text=>el('engineStatus').textContent=text;
let busy=false,job=null,resultURL=null,uploadAbort=null;
function effectivePrompt(){
 const text=el('prompt').value.trim(),style=el('style').value;
 return style?`${text}\nVisual direction: ${style}.`:text;
}
function previewPrompt(){el('enginePromptPreview').value=effectivePrompt();}
el('prompt').addEventListener('input',previewPrompt);el('style').addEventListener('change',previewPrompt);window.addEventListener('studyforge-prompt-changed',previewPrompt);previewPrompt();
function base(){
 if(['127.0.0.1','localhost'].includes(location.hostname)||location.protocol==='https:'&&location.hostname.endsWith('.ts.net'))return location.origin;
 throw Error('Open the local bridge URL on your computer, or your private HTTPS Tailscale Serve URL.');
}
function setBusy(value){busy=value;for(const id of ['engineGenerate','remixGenerate','engineConnect','engineOCR','engineBackend'])el(id).disabled=value;el('engineCancel').disabled=!value||(!job&&!uploadAbort);}
async function json(path,body,method=body?'POST':'GET'){
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
 if(uploadAbort){uploadAbort.abort();return;}
 if(!job)return;
 try{await json(`/engine/jobs/${job}/cancel`,{});status('Cancellation requested; waiting for the next safe processing step.');}
 catch(error){status(error.message);}
};
async function deliver(id,video){
 const response=await fetch(base()+`/engine/results/${id}`,{cache:'no-store'});
 if(!response.ok)throw Error('Result download failed');
 const length=Number(response.headers.get('Content-Length'));if(!Number.isFinite(length)||length<=0||length>256000000)throw Error('Result exceeds download limit');
 const blob=await response.blob();original(blob,video?'studyfrog-video.mp4':'studyfrog-original.png');
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
   if(state.status==='done'){await deliver(job,['video','motion','remix'].includes(kind));break;}
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
    const download=document.createElement('button');download.textContent='Open result';download.onclick=()=>deliver(item.id,['video','motion','remix'].includes(item.kind)).catch(error=>status(error.message));row.append(download);
   }
   list.append(row);
  }
  if(!list.children.length)list.textContent='No computer jobs yet.';
 }catch(error){status(error.message);}
};

const remixCaps={mobile:{seconds:15,bytes:64000000},laptop:{seconds:30,bytes:128000000},desktop:{seconds:60,bytes:128000000}};
el('remixProfile').onchange=()=>{
 const cap=remixCaps[el('remixProfile').value];el('remixSeconds').max=cap.seconds;el('remixSeconds').value=Math.min(Number(el('remixSeconds').value)||cap.seconds,cap.seconds);
};
el('remixGenerate').onclick=async()=>{
 if(busy)return;
 let input=null;setBusy(true);
 try{
  if(el('engineBackend').value!=='sd')throw Error('Video remix uses the StudyForge local engine.');
  const file=el('remixFile').files[0],profile=el('remixProfile').value,cap=remixCaps[profile];
  if(!file||!file.size||file.size>cap.bytes)throw Error(`Choose a video within the ${cap.bytes/1000000} MB input limit.`);
  if(!el('remixRights').checked)throw Error('Confirm permission to edit the video, people and soundtrack.');
  const start=Number(el('remixStart').value),seconds=Number(el('remixSeconds').value);
  if(!Number.isFinite(start)||start<0||start>3600||!Number.isFinite(seconds)||seconds<0.5||seconds>cap.seconds)throw Error('Check clip start and duration for this profile.');
  const mode=el('remixMode').value,background=picture();
  const health=await json('/engine/health'),available=health.video_remix;
  if(!available?.ffmpeg||!available?.opencv)throw Error('Install engine/requirements-remix.txt, FFmpeg and ffprobe on the processing computer.');
  if(Object.keys(remixCaps).indexOf(profile)>Object.keys(remixCaps).indexOf(available.ceiling))throw Error(`Processing computer ceiling is ${available.ceiling}. Choose a lower budget.`);
  if(mode==='person'&&!available.person_model)throw Error('Configure the trusted local PPHumanSeg ONNX and its SHA-256 on your processing computer, or choose green screen.');
  status('Uploading video to your own computer… Cancel can stop the transfer.');
  uploadAbort=new AbortController();el('engineCancel').disabled=false;
  const response=await fetch(base()+'/engine/video-inputs',{method:'POST',headers:{'Content-Type':'application/octet-stream'},body:file,signal:uploadAbort.signal,cache:'no-store'});
  const uploaded=await response.json();if(!response.ok)throw Error(uploaded.error||'Video upload failed');
  if(!/^[0-9a-f]{32}$/.test(uploaded.id))throw Error('Invalid video input identifier');
  input=uploaded.id;uploadAbort=null;
  const queued=await json('/engine/jobs',{kind:'remix',prompt:'Replace background; retain source people and sound',image:background,video_input:input,profile,remix_mode:mode,start,seconds,rights_confirmed:true});
  if(!/^[0-9a-f]{32}$/.test(queued.id))throw Error('Invalid remix job identifier');
  job=queued.id;input=null;el('engineCancel').disabled=false;
  for(;;){
   const state=await json(`/engine/jobs/${job}`);
   if(state.status==='done'){await deliver(job,true);status('Background-edited video ready. Original performance/audio retained; inspect person edges and sync before sharing.');break;}
   if(['failed','cancelled'].includes(state.status))throw Error(state.error||'Remix cancelled');
   status(`Video remix: ${state.status}${state.progress!==undefined?' · '+state.progress+'%':''}. Keep this page open.`);
   await new Promise(resolve=>setTimeout(resolve,1500));
  }
 }catch(error){status(error.name==='AbortError'?'Video upload cancelled. Any interrupted upload is removed; an unclaimed completed transfer expires after 30 minutes.':error.message);}
 finally{
  uploadAbort=null;if(input)try{await json('/engine/video-inputs/'+input,undefined,'DELETE');}catch{}
  job=null;setBusy(false);
 }
};
