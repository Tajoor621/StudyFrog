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
  let submitted,deleted=false;
  await page.route('**/engine/**',async route=>{
   const request=route.request(),pathname=new URL(request.url()).pathname;
   if(pathname==='/engine/health')return route.fulfill({json:{model_configured:true,motion_configured:false,ocr_available:true,image_guidance:{depth_photo:true}}});
   if(pathname==='/engine/ocr')return route.fulfill({json:{text:'CELL WALL\nDNA',review_required:true}});
   if(pathname==='/engine/jobs'&&request.method()==='POST'){submitted=request.postDataJSON();return route.fulfill({status:202,json:{id:'a'.repeat(32),status:'queued'}});}
   if(pathname==='/engine/jobs')return route.fulfill({json:{jobs:deleted?[]:[{id:'a'.repeat(32),kind:'image',status:'done'}]}});
   if(request.method()==='DELETE'){deleted=true;return route.fulfill({json:{status:'deleted'}});}
   if(pathname.startsWith('/engine/jobs/'))return route.fulfill({json:{status:'done'}});
   if(pathname.startsWith('/engine/results/')){
    const png=Buffer.from(await page.locator('#canvas').evaluate(canvas=>{const fixture=document.createElement('canvas');fixture.width=64;fixture.height=64;fixture.getContext('2d').fillStyle='#00ff00';fixture.getContext('2d').fillRect(0,0,64,64);return fixture.toDataURL('image/png').split(',')[1];}),'base64');
    return route.fulfill({contentType:'image/png',headers:{'Content-Length':String(png.length)},body:png});
   }
   return route.fulfill({status:404,json:{error:'Unknown fixture route'}});
  });
  await page.goto('http://127.0.0.1:8766/');
  await page.locator('#engineConnect').click();await page.getByText(/Local engine connected/).waitFor();
  await page.locator('#prompt').fill('Botanical leaf illustration');
  await page.locator('#engineControl').selectOption('depth_photo');
  await page.locator('#engineGenerate').click();await page.getByText(/Result ready/).waitFor();
  assert.equal(submitted.control,'depth_photo');assert.equal(submitted.prompt,'Botanical leaf illustration');assert.ok(submitted.image.length>100);
  assert.equal(await page.locator('#engineDownload').getAttribute('download'),'studyfrog-original.png');
  assert.deepEqual(await page.locator('#canvas').evaluate(c=>Array.from(c.getContext('2d').getImageData(480,270,1,1).data)),[0,255,0,255]);
  await page.locator('#undo').click();await page.getByText('Undone.',{exact:true}).waitFor();
  assert.deepEqual(await page.locator('#canvas').evaluate(c=>Array.from(c.getContext('2d').getImageData(480,270,1,1).data)),[255,255,255,255]);
  await page.locator('#engineOCR').click();await page.getByText(/Review and correct the extracted text before using it/).waitFor();
  assert.equal(await page.locator('#prompt').inputValue(),'Botanical leaf illustration');
  await page.locator('#ocrUse').click();assert.match(await page.locator('#prompt').inputValue(),/CELL WALL\nDNA/);
  await page.locator('#ocrReview').fill('x'.repeat(2000));await page.locator('#ocrUse').click();await page.getByText(/Shorten the reviewed notes/).waitFor();
  await page.locator('#engineKind').selectOption('motion');await page.locator('#engineGenerate').click();await page.getByText(/Guidance applies to image generation/).waitFor();
  await page.locator('#engineJobs').click();await page.getByText(/image · done/).waitFor();
  page.once('dialog',dialog=>dialog.accept());await page.getByRole('button',{name:'Delete from computer',exact:true}).click();await page.getByText('No computer jobs yet.').waitFor();
  assert.deepEqual(errors,[]);assert.ok(await page.locator('#canvas').isVisible());
  console.log('Browser integration passed: connection, image guidance payload, original export, canvas undo, OCR review/bounds, video validation, job deletion; engine fixtures, no real inference.');
 }finally{if(browser)await browser.close();server.kill('SIGTERM');}
})().catch(error=>{console.error(error);process.exitCode=1;});
