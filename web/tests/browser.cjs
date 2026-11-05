// Client integration tests use explicit engine fixtures; these do not test model quality.
const assert=require('node:assert/strict');
const {spawn}=require('node:child_process');
const path=require('node:path');
const {chromium}=require(process.env.STUDYFORGE_PLAYWRIGHT||'playwright');
const root=path.resolve(__dirname,'../..');
const server=spawn(process.env.PYTHON||'python3',[path.join(root,'web/local-engine/bridge.py')],{cwd:root,env:{...process.env,STUDYFORGE_PUBLIC_HOST:''},stdio:'ignore'});
(async()=>{
 let browser;
 try{
  for(let attempt=0;attempt<40;attempt++){
   try{if((await fetch('http://127.0.0.1:8766/')).ok)break;}catch{}
   if(attempt===39)throw Error('Bridge did not start');await new Promise(resolve=>setTimeout(resolve,100));
  }
  browser=await chromium.launch({headless:true,...(process.env.STUDYFORGE_CHROME?{executablePath:process.env.STUDYFORGE_CHROME}:{})});
  const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  let submitted,deleted=false,uploads=0,remixed=false;
  await page.route('**/engine/**',async route=>{
   const request=route.request(),pathname=new URL(request.url()).pathname;
   if(pathname==='/engine/health')return route.fulfill({json:{model_configured:true,motion_configured:false,ocr_available:true,image_guidance:{depth_photo:true},video_remix:{ffmpeg:true,opencv:true,person_model:true,ceiling:"laptop"}}});
   if(pathname==='/engine/video-inputs'){uploads++;assert.equal(request.headers()['content-type'],'application/octet-stream');return route.fulfill({status:201,json:{id:'b'.repeat(32)}});}
   if(pathname==='/engine/ocr')return route.fulfill({json:{text:'CELL WALL\nDNA',review_required:true}});
   if(pathname==='/engine/jobs'&&request.method()==='POST'){submitted=request.postDataJSON();remixed=submitted.kind==='remix';return route.fulfill({status:202,json:{id:'a'.repeat(32),status:'queued'}});}
   if(pathname==='/engine/jobs')return route.fulfill({json:{jobs:deleted?[]:[{id:'a'.repeat(32),kind:'image',status:'done'}]}});
   if(request.method()==='DELETE'){deleted=true;return route.fulfill({json:{status:'deleted'}});}
   if(pathname.startsWith('/engine/jobs/'))return route.fulfill({json:{status:'done'}});
   if(pathname.startsWith('/engine/results/')){
    if(remixed){const bytes=Buffer.from('explicit mock video result');return route.fulfill({contentType:'video/mp4',headers:{'Content-Length':String(bytes.length)},body:bytes});}
    const png=Buffer.from(await page.locator('#canvas').evaluate(canvas=>{const fixture=document.createElement('canvas');fixture.width=64;fixture.height=64;fixture.getContext('2d').fillStyle='#00ff00';fixture.getContext('2d').fillRect(0,0,64,64);return fixture.toDataURL('image/png').split(',')[1];}),'base64');
    return route.fulfill({contentType:'image/png',headers:{'Content-Length':String(png.length)},body:png});
   }
   return route.fulfill({status:404,json:{error:'Unknown fixture route'}});
  });
  await page.goto('http://127.0.0.1:8766/');
  await page.locator('#engineConnect').click();await page.getByText(/Local engine connected/).waitFor();
  await page.locator('#prompt').fill('Botanical leaf illustration');
  await page.locator('#style').selectOption('Realistic');
  assert.equal(await page.locator('#enginePromptPreview').inputValue(),'Botanical leaf illustration\nVisual direction: Realistic.');
  await page.locator('#engineControl').selectOption('depth_photo');
  await page.locator('#engineGenerate').click();await page.getByText(/Result ready/).waitFor();
  assert.equal(submitted.control,'depth_photo');assert.equal(submitted.prompt,'Botanical leaf illustration\nVisual direction: Realistic.');assert.ok(submitted.image.length>100);
  assert.equal(await page.locator('#engineDownload').getAttribute('download'),'studyfrog-original.png');
  assert.deepEqual(await page.locator('#canvas').evaluate(c=>Array.from(c.getContext('2d').getImageData(480,270,1,1).data)),[0,255,0,255]);
  await page.locator('#undo').click();await page.getByText('Undone.',{exact:true}).waitFor();
  assert.deepEqual(await page.locator('#canvas').evaluate(c=>Array.from(c.getContext('2d').getImageData(480,270,1,1).data)),[255,255,255,255]);
  await page.locator('#engineOCR').click();await page.getByText(/Review and correct the extracted text before using it/).waitFor();
  assert.equal(await page.locator('#prompt').inputValue(),'Botanical leaf illustration');
  await page.locator('#ocrUse').click();assert.match(await page.locator('#prompt').inputValue(),/CELL WALL\nDNA/);
  await page.locator('#ocrReview').fill('x'.repeat(2000));await page.locator('#ocrUse').click();await page.getByText(/Shorten the reviewed notes/).waitFor();
  await page.locator('#engineKind').selectOption('motion');await page.locator('#engineGenerate').click();await page.getByText(/Guidance applies to image generation/).waitFor();
  await page.getByText('Video remix · keep people and original sound',{exact:true}).click();
  await page.locator('#remixFile').setInputFiles({name:'source.mp4',mimeType:'video/mp4',buffer:Buffer.from('explicit upload fixture')});
  await page.locator('#remixGenerate').click();await page.getByText('Confirm permission to edit the video, people and soundtrack.',{exact:true}).waitFor();assert.equal(uploads,0);
  await page.locator('#remixRights').check();await page.locator('#remixProfile').selectOption('desktop');
  await page.locator('#remixGenerate').click();await page.getByText(/Processing computer ceiling is laptop/).waitFor();assert.equal(uploads,0);
  await page.locator('#remixProfile').selectOption('mobile');await page.locator('#remixSeconds').fill('16');
  await page.locator('#remixGenerate').click();await page.getByText(/Check clip start and duration for this profile/).waitFor();assert.equal(uploads,0);
  await page.locator('#remixSeconds').fill('5');await page.locator('#remixGenerate').click();await page.getByText(/Background-edited video ready/).waitFor();
  assert.equal(uploads,1);assert.equal(submitted.kind,'remix');assert.equal(submitted.video_input,'b'.repeat(32));assert.equal(submitted.seconds,5);assert.equal(submitted.profile,'mobile');assert.equal(submitted.rights_confirmed,true);assert.ok(submitted.image.length>100);
  assert.equal(await page.locator('#engineDownload').getAttribute('download'),'studyfrog-video.mp4');
  await page.locator('#engineJobs').click();await page.getByText(/image · done/).waitFor();
  page.once('dialog',dialog=>dialog.accept());await page.getByRole('button',{name:'Delete from computer',exact:true}).click();await page.getByText('No computer jobs yet.').waitFor();
  await page.locator('#engineBackend').selectOption('ollama');await page.locator('#negative').fill('blur');
  await page.locator('#engineGenerate').click();await page.getByText(/Ollama image bridge has no negative conditioning/).waitFor();
  await page.locator('#mainNav button[data-view="science"]').click();
  await page.getByRole('heading',{name:'Human body'}).waitFor();
  await page.getByRole('button',{name:'Use in studio',exact:true}).click();
  assert.match(await page.locator('#prompt').inputValue(),/^Study path: Human body\./);
  await page.getByText('Topic transferred to prompt editor.',{exact:true}).waitFor();
  await page.locator('#mainNav button[data-view="science"]').click();
  await page.getByRole('button',{name:'Explore deeper: Cardiovascular system',exact:true}).click();
  await page.getByRole('heading',{name:'Cardiovascular system'}).waitFor();
  let replaced=false;
  page.once('dialog',dialog=>{replaced=dialog.type()==='confirm';return dialog.accept();});
  await page.getByRole('button',{name:'Open diagram in studio',exact:true}).click();
  await page.getByText(/Schematic opened on the canvas/).waitFor();
  assert.equal(replaced,true);
  assert.notDeepEqual(await page.locator('#canvas').evaluate(c=>Array.from(c.getContext('2d').getImageData(480,270,1,1).data)),[255,255,255,255]);
  await page.locator('#undo').click();await page.getByText('Undone.',{exact:true}).waitFor();
  await page.locator('input[name="tool"][value="pen"]').check();
  const box=await page.locator('#canvas').boundingBox();
  await page.mouse.move(box.x+20,box.y+20);await page.mouse.down();await page.mouse.move(box.x+90,box.y+40);await page.mouse.up();
  await page.getByText('Mark added.',{exact:true}).waitFor();
  assert.equal(await page.locator('#canvas').evaluate(c=>{const data=c.getContext('2d').getImageData(0,0,c.width,c.height).data;for(let i=0;i<data.length;i+=16)if(data[i]!==255||data[i+1]!==255||data[i+2]!==255)return true;return false;}),true);
  await page.locator('#undo').click();await page.getByText('Undone.',{exact:true}).waitFor();
  await page.locator('#theme').click();
  assert.equal(await page.locator('html').getAttribute('data-theme'),'light');
  await page.locator('#theme').click();
  assert.equal(await page.locator('html').getAttribute('data-theme'),'dark');
  page.once('dialog',dialog=>dialog.accept('Leaf notes'));
  await page.locator('#save').click();await page.getByText('Saved locally.',{exact:true}).waitFor();
  await page.locator('#mainNav button[data-view="projects"]').click();
  await page.getByRole('heading',{name:'Leaf notes'}).waitFor();
  await page.locator('#projectSearch').fill('no-such-project');
  await page.getByText('No projects match that search.',{exact:true}).waitFor();
  await page.locator('#projectSearch').fill('');
  page.once('dialog',dialog=>dialog.accept('Leaf diagram'));
  await page.getByRole('button',{name:'Rename Leaf notes',exact:true}).click();
  await page.getByRole('heading',{name:'Leaf diagram'}).waitFor();
  await page.locator('#exportProjects').click();await page.getByText('Project backup downloaded.',{exact:true}).waitFor();
  page.once('dialog',dialog=>dialog.accept());
  await page.getByRole('button',{name:'Delete Leaf diagram',exact:true}).click();
  await page.getByText('No saved projects yet.',{exact:true}).waitFor();
  assert.equal(await page.locator('a.skip').count(),1);
  await page.locator('#mainNav button[data-view="help"]').click();
  await page.getByRole('heading',{name:'Help and tool guide'}).waitFor();
  await page.locator('#mainNav button[data-view="studio"]').click();
  assert.deepEqual(errors,[]);assert.ok(await page.locator('#canvas').isVisible());
  console.log('Browser integration passed: connection, image guidance payload, original export, canvas undo, OCR review/bounds, video validation, remix permission/profile limits and upload payload, job deletion, science catalog handoff, diagram undo, marks, theme, project search/rename/backup/delete; engine fixtures, no real inference.');
 }finally{if(browser)await browser.close();server.kill('SIGTERM');}
})().catch(error=>{console.error(error);process.exitCode=1;});
