const el = id => document.getElementById(id);
const status = text => { el('learningStatus').textContent = text; };
const path = '/learning';
const bridge = ['localhost', '127.0.0.1'].includes(location.hostname) && location.port === '8766'
  || location.protocol === 'https:' && location.hostname.endsWith('.ts.net');
const types = {
  png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', webp: 'image/webp',
  heic: 'image/heic', heif: 'image/heif', mp4: 'video/mp4', mov: 'video/quicktime', webm: 'video/webm'
};
let sources = [], busy = false, poll;

async function request(route, method = 'GET', body, headers = {}) {
  const response = await fetch(path + route, {
    method, cache: 'no-store',
    headers: {'X-StudyFrog-Learning': '1', 'Content-Type': 'application/json', ...headers}, body
  });
  const data = await response.json();
  if (!response.ok) throw Error(data.error || 'Local learning request failed.');
  return data;
}

function node(tag, text) {
  const element = document.createElement(tag);
  if (text !== undefined) element.textContent = text;
  return element;
}

function source() {
  return sources.find(item => item.id === el('learningSource').value);
}

function update() {
  const selected = source(), ready = selected?.status === 'ready';
  el('learningSourceInfo').textContent = selected
    ? [selected.status, ...selected.warnings].join(' · ') : '';
  el('learningNotes').disabled = busy || !ready;
  el('learningDelete').disabled = busy || !selected || selected.status === 'indexing';
  el('learningCaption').disabled = busy || !ready || selected.kind !== 'video';
  for (const button of document.querySelectorAll('#learningImport button, #learningAsk button')) {
    button.disabled = busy;
  }
}

async function refresh(preferred = el('learningSource').value) {
  clearTimeout(poll);
  sources = (await request('/sources')).sources;
  const picker = el('learningSource');
  picker.replaceChildren(new Option('All sources', ''), ...sources.map(item =>
    new Option(item.name + (item.status === 'ready' ? '' : ' · ' + item.status), item.id)));
  picker.value = sources.some(item => item.id === preferred) ? preferred : '';
  el('learningUnavailable').hidden = true;
  el('learningTools').hidden = false;
  update();
  if (sources.some(item => item.status === 'indexing')) {
    poll = setTimeout(() => refresh().catch(error => status(error.message)), 3000);
  }
}

async function action(task) {
  if (busy) return;
  busy = true;
  update();
  try { await task(); }
  catch (error) { status(error.message); }
  finally { busy = false; update(); }
}

function formatTime(seconds) {
  const total = Math.floor(seconds);
  return [Math.floor(total / 3600), Math.floor(total / 60) % 60, total % 60]
    .map(n => String(n).padStart(2, '0')).join(':');
}

function display(data) {
  const out = el('learningResult');
  out.replaceChildren(node('h2', 'Study response'), node('pre', data.answer));
  data.citations.forEach((item, i) => {
    const article = node('article');
    const time = item.meta.timestamp_start;
    article.append(node('h3', `[${i + 1}] ${item.name}${time === undefined ? '' : ' · ' + formatTime(time)}`));
    if (item.meta.preview) {
      const img = node('img');
      img.loading = 'lazy';
      img.width = 240;
      img.alt = time === undefined ? 'Original study image preview'
        : 'Saved video frame at ' + formatTime(item.meta.keyframe_timestamp ?? time);
      img.src = `${path}/sources/${item.doc}/asset/${item.meta.preview}`;
      article.append(img);
    }
    const detail = node('details');
    const image = item.meta.source_type === 'image';
    const extract = node('pre', item.body);
    detail.append(node('summary', image ? 'View extracted text' : 'View source excerpt'), extract);
    if (image) detail.addEventListener('toggle', async () => {
      if (!detail.open || detail.dataset.loaded) return;
      try {
        extract.textContent = (await request(`/sources/${item.doc}/notes`)).markdown;
        detail.dataset.loaded = 'true';
      } catch (error) { status(error.message); }
    });
    article.append(detail);
    out.append(article);
  });
}

if (bridge) {
  let connected = false;
  const connect = () => {
    if (connected) return;
    refresh().then(() => { connected = true; }).catch(error => status(error.message));
  };
  document.querySelector('[data-view="learning"]').addEventListener('click', connect);
  window.addEventListener('hashchange', () => { if (location.hash === '#learning') connect(); });
  if (location.hash === '#learning') connect();
  el('learningSource').onchange = update;
  el('learningRefresh').onclick = () => action(() => refresh());
  el('learningImport').onsubmit = event => {
    event.preventDefault();
    action(async () => {
      const file = el('learningFile').files[0];
      const mime = types[file?.name.split('.').pop().toLowerCase()];
      if (!mime) throw Error('Choose a supported image or video.');
      const limit = mime.startsWith('image') ? 12_000_000 : 128_000_000;
      if (!file.size || file.size > limit) throw Error('This file exceeds the upload limit.');
      status('Sending to your local computer…');
      const result = await request('/sources', 'POST', file, {
        'Content-Type': mime, 'X-StudyFrog-Name': encodeURIComponent(file.name)
      });
      el('learningImport').reset();
      await refresh(result.id);
      status('Indexing started. You can leave this page and return later.');
    });
  };
  const study = quiz => action(async () => {
    const question = el('learningQuestion').value.trim();
    if (!quiz && !question) throw Error('Enter a question or keywords.');
    status(quiz ? 'Preparing review questions…' : 'Looking up source excerpts…');
    const data = await request(quiz ? '/quiz' : '/ask', 'POST', JSON.stringify({
      doc: source()?.id, question, vision: el('learningVision').checked
    }));
    display(data);
    status('Ready. Check the source excerpts before relying on generated answers.');
  });
  el('learningAsk').onsubmit = event => { event.preventDefault(); study(false); };
  el('learningQuiz').onclick = () => study(true);
  el('learningDelete').onclick = () => action(async () => {
    const doc = source();
    if (!doc || !confirm('Remove this source and its local text and images?')) return;
    await request(`/sources/${doc.id}`, 'DELETE');
    el('learningResult').replaceChildren();
    await refresh('');
    status('Source removed.');
  });
  el('learningNotes').onclick = () => action(async () => {
    const doc = source();
    const {markdown} = await request(`/sources/${doc.id}/notes`);
    const url = URL.createObjectURL(new Blob([markdown], {type: 'text/markdown;charset=utf-8'}));
    const anchor = node('a');
    anchor.href = url;
    anchor.download = doc.kind === 'video' ? 'studyfrog-video-notes.md' : 'studyfrog-image-extract.md';
    anchor.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    status('Markdown exported.');
  });
  el('learningEnrich').onsubmit = event => {
    event.preventDefault();
    action(async () => {
      const doc = source();
      if (doc?.kind !== 'video') throw Error('Select a video.');
      status('Describing one saved frame on this computer…');
      const data = await request('/enrich', 'POST', JSON.stringify({
        doc: doc.id, timestamp: el('learningTimestamp').valueAsNumber
      }));
      status(`Saved at ${formatTime(data.timestamp)}: ${data.caption}`);
    });
  };
}
