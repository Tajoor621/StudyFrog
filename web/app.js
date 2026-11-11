import {generationPrompt, paintDiagram, studyPath, validateCatalog} from './science.mjs';

const $ = id => document.getElementById(id);
const canvas = $('canvas');
const ctx = canvas.getContext('2d');
const buffer = document.createElement('canvas');
buffer.width = canvas.width;
buffer.height = canvas.height;
const bufferCtx = buffer.getContext('2d');
let undoStack = [];
let photo = null;
let installPrompt = null;
let dirty = false;
let tool = 'pointer';
let stroke = null;
let lastPoint = null;
const key = 'studyfrog.projects.v1';
const themeKey = 'studyfrog.appearance.v1';
const scienceKey = 'studyfrog.science.v1';
const styles = ['', 'Scientific diagram', 'Realistic', 'Sketch', 'Minimal'];
const views = ['studio', 'projects', 'science', 'learning', 'help'];
const titles = {
  studio: 'Study Frog — offline creative study workspace',
  projects: 'Saved projects — Study Frog',
  science: 'Science Explorer — Study Frog',
  learning: 'Media Learning | Study Frog',
  help: 'Help and tool guide — Study Frog'
};
const status = text => { $('status').textContent = text; };
const pathNames = {'human-body': 'Body organization', prophase: 'Mitosis sequence'};

function blank() {
  ctx.fillStyle = '#fff';
  ctx.fillRect(0, 0, canvas.width, canvas.height);
}
blank();

function snapshot() {
  undoStack.push(canvas.toDataURL('image/png'));
  if (undoStack.length > 20) undoStack.shift();
}

function drawPhoto(img) {
  snapshot();
  blank();
  const scale = Math.min(canvas.width / img.width, canvas.height / img.height);
  const w = img.width * scale;
  const h = img.height * scale;
  ctx.drawImage(img, (canvas.width - w) / 2, (canvas.height - h) / 2, w, h);
  dirty = true;
}

$('photo').addEventListener('change', event => {
  const file = event.target.files?.[0];
  if (!file) return;
  if (!/^image\/(png|jpeg|webp)$/.test(file.type) || file.size > 12e6) {
    status('Use a PNG, JPEG or WebP under 12 MB.');
    return;
  }
  const url = URL.createObjectURL(file);
  const img = new Image();
  img.onload = () => {
    photo = img;
    drawPhoto(img);
    URL.revokeObjectURL(url);
    status('Photo imported.');
  };
  img.onerror = () => {
    URL.revokeObjectURL(url);
    status('Unable to decode photo.');
  };
  img.src = url;
});

$('addText').onclick = () => {
  const text = $('caption').value.trim();
  if (!text) return status('Enter label text first.');
  let x = 480;
  let y = 270;
  if ($('labelAtPoint').checked) {
    if (!lastPoint) return status('Click the canvas first, or turn off placement at the last click.');
    [x, y] = lastPoint;
  }
  snapshot();
  ctx.font = 'bold 40px system-ui';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'alphabetic';
  ctx.lineWidth = 6;
  ctx.strokeStyle = '#fff';
  ctx.strokeText(text, x, y, 900);
  ctx.fillStyle = '#123c72';
  ctx.fillText(text, x, y, 900);
  dirty = true;
  status('Label added.');
};

function undo() {
  const prev = undoStack.pop();
  if (!prev) return status('Nothing to undo.');
  const img = new Image();
  img.onload = () => {
    blank();
    ctx.drawImage(img, 0, 0);
    status('Undone.');
  };
  img.src = prev;
}
$('undo').onclick = () => {
  if (stroke) return;
  undo();
};

$('clear').onclick = () => {
  snapshot();
  blank();
  dirty = false;
  status('Canvas cleared.');
};

$('export').onclick = () => {
  const anchor = document.createElement('a');
  anchor.href = canvas.toDataURL('image/png');
  anchor.download = 'study-frog-graphic.png';
  anchor.click();
  status('PNG exported.');
};

function read() {
  try { return JSON.parse(localStorage.getItem(key) || '[]'); }
  catch { return []; }
}

function write(projects) {
  localStorage.setItem(key, JSON.stringify(projects.slice(0, 12)));
}

function renderProjects() {
  const root = $('projectList');
  root.replaceChildren();
  const query = ($('projectSearch')?.value || '').trim().toLowerCase();
  const saved = read();
  const projects = saved.filter(project => !query || `${project.title} ${project.prompt || ''}`.toLowerCase().includes(query));
  if (!saved.length) {
    root.textContent = 'No saved projects yet.';
    return;
  }
  if (!projects.length) {
    root.textContent = 'No projects match that search.';
    return;
  }
  for (const project of projects) {
    const card = document.createElement('article');
    card.className = 'card';
    const title = document.createElement('h2');
    title.textContent = project.title;
    const meta = document.createElement('p');
    meta.textContent = project.style ? `Visual direction: ${project.style}` : 'Prompt only';
    const load = document.createElement('button');
    load.type = 'button';
    load.textContent = 'Open';
    load.setAttribute('aria-label', `Open ${project.title}`);
    load.onclick = () => {
      const img = new Image();
      img.onload = () => {
        snapshot();
        blank();
        ctx.drawImage(img, 0, 0);
        dirty = true;
        $('prompt').value = project.prompt || '';
        $('negative').value = project.negative || '';
        $('style').value = styles.includes(project.style) ? project.style : '';
        window.dispatchEvent(new Event('studyforge-prompt-changed'));
        show('studio');
        status('Project loaded.');
      };
      img.src = project.image;
    };
    const rename = document.createElement('button');
    rename.type = 'button';
    rename.textContent = 'Rename';
    rename.setAttribute('aria-label', `Rename ${project.title}`);
    rename.onclick = () => {
      const titleText = prompt('Rename project?', project.title)?.trim();
      if (!titleText) return;
      write(read().map(item => item.id === project.id ? {...item, title: titleText.slice(0, 80)} : item));
      renderProjects();
      status('Project renamed.');
    };
    const duplicate = document.createElement('button');
    duplicate.type = 'button';
    duplicate.textContent = 'Duplicate';
    duplicate.setAttribute('aria-label', `Duplicate ${project.title}`);
    duplicate.onclick = () => {
      try {
        const projects = read();
        projects.unshift({...project, id: Date.now(), title: `${project.title} copy`.slice(0, 80)});
        write(projects);
        renderProjects();
        status('Project duplicated.');
      } catch {
        status('Browser storage full. Export your PNG and remove old projects.');
      }
    };
    const del = document.createElement('button');
    del.type = 'button';
    del.textContent = 'Delete';
    del.setAttribute('aria-label', `Delete ${project.title}`);
    del.onclick = () => {
      if (!confirm('Delete this local project?')) return;
      write(read().filter(item => item.id !== project.id));
      renderProjects();
      status('Project deleted.');
    };
    const actions = document.createElement('div');
    actions.className = 'actions';
    actions.append(load, rename, duplicate, del);
    card.append(title, meta, actions);
    root.append(card);
  }
}

$('projectSearch').addEventListener('input', renderProjects);

$('save').onclick = () => {
  const title = prompt('Project name?', 'Untitled study')?.trim();
  if (!title) return;
  try {
    const projects = read();
    projects.unshift({
      id: Date.now(),
      title: title.slice(0, 80),
      prompt: $('prompt').value,
      negative: $('negative').value,
      style: $('style').value,
      image: canvas.toDataURL('image/png')
    });
    write(projects);
    status('Saved locally.');
    renderProjects();
  } catch {
    status('Browser storage full. Export your PNG and remove old projects.');
  }
};

function download(filename, href) {
  const anchor = document.createElement('a');
  anchor.href = href;
  anchor.download = filename;
  anchor.click();
}

$('exportProjects').onclick = () => {
  const projects = read();
  if (!projects.length) return status('No saved projects to export.');
  const url = URL.createObjectURL(new Blob([JSON.stringify(projects)], {type: 'application/json'}));
  download('study-frog-projects.json', url);
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  status('Project backup downloaded.');
};

function validProject(project) {
  return project && typeof project.title === 'string' && project.title.trim()
    && typeof project.image === 'string' && project.image.startsWith('data:image/png;base64,')
    && project.image.length < 2_500_000;
}

$('importProjects').addEventListener('change', async event => {
  const file = event.target.files?.[0];
  event.target.value = '';
  if (!file) return;
  if (file.size > 8_000_000) return status('Backup file is too large.');
  try {
    const data = JSON.parse(await file.text());
    if (!Array.isArray(data)) throw new Error('invalid');
    const incoming = data.filter(validProject).slice(0, 12).map(project => ({
      id: Number.isFinite(project.id) ? project.id : Date.now() + Math.floor(Math.random() * 1000),
      title: String(project.title).trim().slice(0, 80),
      prompt: String(project.prompt || '').slice(0, 4000),
      negative: String(project.negative || '').slice(0, 2000),
      style: styles.includes(project.style) ? project.style : '',
      image: project.image
    }));
    if (!incoming.length) return status('No PNG projects found in that backup.');
    if (!confirm(`Import ${incoming.length} project${incoming.length === 1 ? '' : 's'} into this browser?`)) return;
    const seen = new Set();
    const merged = [];
    for (const project of [...incoming, ...read()]) {
      if (seen.has(project.id)) continue;
      seen.add(project.id);
      merged.push(project);
      if (merged.length === 12) break;
    }
    write(merged);
    renderProjects();
    status('Backup imported.');
  } catch {
    status('That file is not a Study Frog project backup.');
  }
});

function show(id, options = {}) {
  const updateHash = options.hash !== false;
  const focus = options.focus !== false;
  document.querySelectorAll('.view').forEach(el => { el.hidden = el.id !== id; });
  document.querySelectorAll('#mainNav button').forEach(button => {
    if (button.dataset.view === id) button.setAttribute('aria-current', 'page');
    else button.removeAttribute('aria-current');
  });
  if (id === 'projects') renderProjects();
  document.title = titles[id] || titles.studio;
  if (updateHash && location.hash !== `#${id}`) history.pushState({view: id}, '', `#${id}`);
  if (focus) document.querySelector(`#${id} h1`)?.focus();
}

document.querySelectorAll('#mainNav button').forEach(button => {
  button.onclick = () => show(button.dataset.view);
});
window.addEventListener('hashchange', () => {
  const id = location.hash.slice(1);
  show(views.includes(id) ? id : 'studio', {hash: false});
});
const opening = location.hash.slice(1);
if (views.includes(opening) && opening !== 'studio') show(opening, {hash: false, focus: false});

function pointerPos(event) {
  const rect = canvas.getBoundingClientRect();
  return [
    Math.min(canvas.width, Math.max(0, (event.clientX - rect.left) * (canvas.width / rect.width))),
    Math.min(canvas.height, Math.max(0, (event.clientY - rect.top) * (canvas.height / rect.height)))
  ];
}

function inkStyle() {
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  ctx.lineWidth = Number($('inkSize').value) || 6;
  ctx.strokeStyle = tool === 'eraser' ? '#ffffff' : $('inkColor').value;
}

function drawMark(points) {
  inkStyle();
  const [x0, y0] = points[0];
  const [x1, y1] = points[points.length - 1];
  ctx.beginPath();
  if (tool === 'pen' || tool === 'eraser') {
    ctx.moveTo(x0, y0);
    if (points.length === 1) ctx.lineTo(x0 + 0.01, y0 + 0.01);
    else for (const [x, y] of points.slice(1)) ctx.lineTo(x, y);
    ctx.stroke();
    return;
  }
  if (tool === 'line') {
    ctx.moveTo(x0, y0);
    ctx.lineTo(x1, y1);
    ctx.stroke();
    return;
  }
  const left = Math.min(x0, x1);
  const top = Math.min(y0, y1);
  const width = Math.max(Math.abs(x1 - x0), 0.5);
  const height = Math.max(Math.abs(y1 - y0), 0.5);
  if (tool === 'ellipse') {
    ctx.ellipse(left + width / 2, top + height / 2, width / 2, height / 2, 0, 0, Math.PI * 2);
    ctx.stroke();
    return;
  }
  ctx.strokeRect(left, top, width, height);
}

document.querySelectorAll('input[name="tool"]').forEach(input => {
  input.addEventListener('change', () => {
    if (!input.checked) return;
    tool = input.value;
    canvas.style.touchAction = tool === 'pointer' ? 'manipulation' : 'none';
  });
});
$('inkSize').addEventListener('input', () => { $('inkSizeValue').textContent = $('inkSize').value; });
$('guideGrid').addEventListener('change', () => { $('guide').hidden = !$('guideGrid').checked; });

canvas.addEventListener('pointerdown', event => {
  if (event.button !== 0) return;
  const point = pointerPos(event);
  if (tool === 'pointer') {
    lastPoint = point;
    return;
  }
  snapshot();
  bufferCtx.clearRect(0, 0, buffer.width, buffer.height);
  bufferCtx.drawImage(canvas, 0, 0);
  stroke = {points: [point]};
  dirty = true;
  canvas.setPointerCapture(event.pointerId);
  event.preventDefault();
});
canvas.addEventListener('pointermove', event => {
  if (!stroke) return;
  stroke.points.push(pointerPos(event));
  ctx.drawImage(buffer, 0, 0);
  drawMark(stroke.points);
});
function finishStroke(commit) {
  if (!stroke) return;
  ctx.drawImage(buffer, 0, 0);
  if (commit) {
    drawMark(stroke.points);
    status(tool === 'eraser' ? 'Eraser mark added. Undo can restore covered pixels.' : 'Mark added.');
  } else {
    undoStack.pop();
    status('Mark cancelled.');
  }
  stroke = null;
}
canvas.addEventListener('pointerup', () => finishStroke(true));
canvas.addEventListener('pointercancel', () => finishStroke(false));

document.addEventListener('keydown', event => {
  if (!(event.ctrlKey || event.metaKey) || event.key.toLowerCase() !== 'z' || event.shiftKey || event.altKey) return;
  const tag = document.activeElement?.tagName;
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || document.activeElement?.isContentEditable) return;
  event.preventDefault();
  $('undo').click();
});

let catalog = null;
let selectedScience = 'human-body';
let showLabels = true;
try { selectedScience = sessionStorage.getItem(scienceKey) || selectedScience; } catch { /* private mode */ }

function rememberScience(id) {
  selectedScience = id;
  try { sessionStorage.setItem(scienceKey, id); } catch { /* ignore quota */ }
}

function button(label, click, aria) {
  const element = document.createElement('button');
  element.type = 'button';
  element.textContent = label;
  if (aria) element.setAttribute('aria-label', aria);
  element.onclick = click;
  return element;
}

function renderBasicScience() {
  $('scienceDetail').hidden = true;
  $('sciencePaths').replaceChildren();
  $('scienceList').replaceChildren();
  const topics = [
    ['Cell structure', 'Cells contain membranes, cytoplasm and genetic material.'],
    ['Mitosis', 'Mitosis divides replicated chromosomes between daughter nuclei.'],
    ['Human body systems', 'Organs cooperate in circulatory, respiratory, nervous and other systems.'],
    ['Bacteria', 'Bacteria are prokaryotic organisms with diverse shapes and functions.'],
    ['Fungi', 'Fungi include yeasts and filamentous species with varied life cycles.']
  ];
  const note = document.createElement('p');
  note.textContent = 'The full offline catalog did not load. These short summaries remain available.';
  $('scienceList').append(note);
  for (const [title, desc] of topics) {
    const card = document.createElement('article');
    card.className = 'card';
    const heading = document.createElement('h2');
    heading.textContent = title;
    const paragraph = document.createElement('p');
    paragraph.textContent = desc;
    const use = button('Use in studio', () => {
      $('prompt').value = `Educational ${($('style').value || 'illustration').toLowerCase()} of ${title}. ${desc} Include accurate, clearly separated labels.`;
      window.dispatchEvent(new Event('studyforge-prompt-changed'));
      show('studio', {focus: false});
      $('prompt').focus();
      status('Topic transferred to prompt editor.');
    });
    card.append(heading, paragraph, use);
    $('scienceList').append(card);
  }
}

function studyPrompt(id) {
  const extra = ($('scienceNotes')?.value || '').trim().slice(0, 500);
  let text = generationPrompt(catalog, id);
  if (extra) text += ` Additional instructions: ${extra}`;
  return text;
}

function applyStudyPrompt(id) {
  const text = studyPrompt(id);
  if (text.length > 1900) {
    status('Shorten additional instructions so the prompt stays within 2000 characters.');
    return false;
  }
  $('prompt').value = text;
  window.dispatchEvent(new Event('studyforge-prompt-changed'));
  return true;
}

function paintScience(node) {
  const preview = $('scienceCanvas');
  if (!preview) return;
  preview.width = 1200;
  preview.height = 900;
  paintDiagram(preview.getContext('2d'), node, showLabels);
}

function openDiagram(node) {
  if (dirty && !confirm('Replace the canvas with this schematic? Undo restores the previous canvas.')) return;
  if (!applyStudyPrompt(node.id)) return;
  snapshot();
  const diagram = document.createElement('canvas');
  diagram.width = 1200;
  diagram.height = 900;
  paintDiagram(diagram.getContext('2d'), node, showLabels);
  blank();
  const scale = Math.min(canvas.width / 1200, canvas.height / 900);
  const w = 1200 * scale;
  const h = 900 * scale;
  ctx.drawImage(diagram, (canvas.width - w) / 2, (canvas.height - h) / 2, w, h);
  dirty = true;
  show('studio', {focus: false});
  status('Schematic opened on the canvas. It is a simplified teaching diagram, not a medical image.');
}

function renderScience(focusHeading) {
  const byId = new Map(catalog.nodes.map(node => [node.id, node]));
  if (!byId.has(selectedScience)) selectedScience = catalog.roots[0];
  const node = byId.get(selectedScience);
  const root = catalog.roots.find(id => studyPathIncludes(id, node.id)) || catalog.roots[0];
  $('scienceDetail').hidden = false;
  $('sciencePaths').replaceChildren();
  for (const id of catalog.roots) {
    const pathButton = button(pathNames[id] || byId.get(id).title, () => {
      rememberScience(id);
      renderScience(true);
    });
    pathButton.setAttribute('aria-pressed', id === root ? 'true' : 'false');
    $('sciencePaths').append(pathButton);
  }
  $('scienceList').replaceChildren();
  for (const item of nodesInPath(root, byId)) {
    const card = document.createElement('article');
    card.className = 'card';
    const select = button(item.title, () => {
      rememberScience(item.id);
      renderScience(true);
    }, `${item.title}, ${item.level}`);
    select.className = 'text-button';
    select.setAttribute('aria-pressed', item.id === node.id ? 'true' : 'false');
    const level = document.createElement('p');
    level.textContent = `${item.level}. ${item.summary}`;
    card.append(select, level);
    $('scienceList').append(card);
  }
  const notes = $('scienceNotes')?.value || '';
  const detail = $('scienceDetail');
  detail.replaceChildren();
  const crumbs = document.createElement('div');
  crumbs.className = 'path';
  crumbs.setAttribute('role', 'navigation');
  crumbs.setAttribute('aria-label', 'Study path');
  studyPath(catalog.nodes, node.id).forEach((item, index, path) => {
    if (index) {
      const separator = document.createElement('span');
      separator.setAttribute('aria-hidden', 'true');
      separator.textContent = '›';
      crumbs.append(separator);
    }
    crumbs.append(button(item.title, () => {
      rememberScience(item.id);
      renderScience(true);
    }, `Open ${item.title}`));
    if (index === path.length - 1) crumbs.lastElementChild.setAttribute('aria-current', 'page');
  });
  const heading = document.createElement('h2');
  heading.tabIndex = -1;
  heading.textContent = node.title;
  const summary = document.createElement('p');
  summary.textContent = `${node.level}. ${node.summary}`;
  const preview = document.createElement('canvas');
  preview.id = 'scienceCanvas';
  preview.width = 1200;
  preview.height = 900;
  preview.setAttribute('role', 'img');
  preview.setAttribute('aria-labelledby', 'scienceAlt');
  const labelToggle = document.createElement('label');
  labelToggle.className = 'check';
  const labelInput = document.createElement('input');
  labelInput.id = 'scienceLabels';
  labelInput.type = 'checkbox';
  labelInput.checked = showLabels;
  labelInput.addEventListener('change', () => {
    showLabels = labelInput.checked;
    paintScience(node);
  });
  labelToggle.append(labelInput, document.createTextNode('Show diagram labels'));
  const descriptionHeading = document.createElement('h3');
  descriptionHeading.textContent = 'Diagram description';
  const description = document.createElement('p');
  description.id = 'scienceAlt';
  description.textContent = node.alt;
  const notesLabel = document.createElement('label');
  notesLabel.htmlFor = 'scienceNotes';
  notesLabel.textContent = 'Additional instructions for the prompt';
  const notesField = document.createElement('textarea');
  notesField.id = 'scienceNotes';
  notesField.maxLength = 500;
  notesField.rows = 3;
  notesField.placeholder = 'Optional. 500 characters. These notes are not checked for accuracy.';
  notesField.value = notes;
  const actions = document.createElement('div');
  actions.className = 'actions';
  actions.append(
    button('Use in studio', () => {
      if (!applyStudyPrompt(node.id)) return;
      show('studio', {focus: false});
      $('prompt').focus();
      status('Topic transferred to prompt editor.');
    }),
    button('Open diagram in studio', () => openDiagram(node))
  );
  for (const id of node.children || []) actions.append(button(`Explore deeper: ${byId.get(id).title}`, () => {
    rememberScience(id);
    renderScience(true);
  }));
  if (node.next) actions.append(button(`Next phase: ${byId.get(node.next).title}`, () => {
    rememberScience(node.next);
    renderScience(true);
  }));
  const previous = node.parent || catalog.nodes.find(item => item.next === node.id)?.id;
  if (previous) actions.append(button(`Previous view: ${byId.get(previous).title}`, () => {
    rememberScience(previous);
    renderScience(true);
  }));
  const notice = document.createElement('p');
  notice.className = 'hint';
  notice.textContent = catalog.notice;
  const sourceHeading = document.createElement('h3');
  sourceHeading.textContent = 'Sources';
  const sourceList = document.createElement('ul');
  for (const source of catalog.sources.filter(item => node.sourceIds.includes(item.id))) {
    const item = document.createElement('li');
    const link = document.createElement('a');
    link.href = source.url;
    link.target = '_blank';
    link.rel = 'noopener noreferrer';
    link.textContent = source.title;
    const hint = document.createElement('span');
    hint.className = 'sr-only';
    hint.textContent = ' (opens in a new tab)';
    link.append(hint);
    item.append(link);
    sourceList.append(item);
  }
  detail.append(crumbs, heading, summary, preview, labelToggle, descriptionHeading, description, notesLabel, notesField, actions, notice, sourceHeading, sourceList);
  paintScience(node);
  if (focusHeading) heading.focus();
}

function studyPathIncludes(rootId, id) {
  const seen = new Set();
  const visit = current => {
    if (!current || seen.has(current)) return false;
    seen.add(current);
    if (current === id) return true;
    const node = catalog.nodes.find(item => item.id === current);
    return (node.children || []).some(visit) || visit(node.next);
  };
  return visit(rootId);
}

function nodesInPath(rootId, byId) {
  const ordered = [];
  const seen = new Set();
  const visit = id => {
    if (!id || seen.has(id) || !byId.has(id)) return;
    seen.add(id);
    const node = byId.get(id);
    ordered.push(node);
    for (const child of node.children || []) visit(child);
    visit(node.next);
  };
  visit(rootId);
  return ordered;
}

async function loadCatalog() {
  try {
    const response = await fetch('./content/study-catalog.json');
    if (!response.ok) throw new Error('missing');
    catalog = validateCatalog(await response.json());
    renderScience(false);
  } catch {
    catalog = null;
    renderBasicScience();
  }
}
loadCatalog();

const help = [
  ['Video background remix', 'Import or generate a background on the canvas. Under Free local generation, open Video remix, choose a video, confirm editing permission, choose a Mobile, Laptop, or Desktop budget, and send it to your own computer. Person mode needs a trusted local PPHumanSeg ONNX model. Green screen needs no model. Original faces, performance, and the first audio track are retained. This web workflow processes on your computer. The native Android and iOS apps can also remix video offline on the phone. Inspect edges and audio sync before you share the result.'],
  ['Studio / prompt', 'Write the diagram or illustration you want. The prompt is saved with the project. This browser does not run a generative image model. A visual direction is added only when you generate on your own computer.'],
  ['Negative prompt', 'List unwanted elements. StudyForge SD and LTX jobs receive this text. The Ollama image bridge rejects negative conditioning because that bridge cannot apply it.'],
  ['Visual direction', 'Choose Prompt only to leave your wording unchanged, or choose a direction to append it when a computer job is sent. Read Prompt sent to computer before generating.'],
  ['Import photo', 'Choose a PNG, JPEG, or WebP file up to 12 MB. It is scaled to fit the private 960×540 canvas.'],
  ['Add label', 'Type a label. By default it is drawn at the center in blue with a white outline. Turn on placement at the last canvas click to use a point you marked with the None tool. Undo reverses the last canvas change.'],
  ['Canvas marks', 'Choose None, Pen, Line, Rectangle, Ellipse, or Eraser. Marks are drawn into the canvas and included in PNG export. The eraser paints white; it does not recover older pixels. Use Undo for that. The alignment grid is visual only and is not exported.'],
  ['Export PNG', 'Download the current canvas as a PNG of the pixels you see, not a description of them.'],
  ['Save / Projects', 'Save up to 12 projects in this browser. Search, rename, duplicate, open, or delete them. Download a JSON backup or import one made by this web edition. Storage can fill up; keep exported PNGs of work you need.'],
  ['Science Explorer', 'Open a body-organization or mitosis view from the bundled catalog. Read the description, follow OpenStax sources when you are online, send the view to the prompt, or place its schematic on the canvas. These drawings are simplified and not to scale. They are not medical advice. If the catalog file cannot be read, a short topic list remains.'],
  ['AI media generation', 'Optional image, video, refinement, and OCR tools run on your computer, not on GitHub Pages and not inside this browser. Install Ollama and a supported image model, or run the StudyForge engine, then start python3 web/local-engine/bridge.py and open http://127.0.0.1:8766/ on that computer. Use Check local engine, then Generate image. Configure STUDYFORGE_TOKEN on the computer for the StudyForge engine. Download original results to keep full resolution. The editor preview is 960×540. Missing models disable only the features that need them.'],
  ['Appearance', 'Theme follows the system, or you can choose light or dark. The choice is stored in this browser.'],
  ['Keyboard', 'Ctrl+Z or Command+Z undoes the last canvas change when focus is not in a text field, menu, or checkbox. Tab moves through controls. The main navigation and science paths are buttons.'],
  ['Install / offline', 'Use Install when the browser offers it. After the first successful load, the studio, science catalog, and icons are cached for offline use. Prompts sent to your computer, job history, and generated images or videos are not cached.']
];
for (const [title, body] of help) {
  const article = document.createElement('article');
  article.className = 'card';
  const heading = document.createElement('h2');
  heading.textContent = title;
  const paragraph = document.createElement('p');
  paragraph.textContent = body;
  article.append(heading, paragraph);
  $('helpContent').append(article);
}

const themeOrder = ['system', 'light', 'dark'];
function applyTheme(mode) {
  const next = themeOrder.includes(mode) ? mode : 'system';
  if (next === 'system') document.documentElement.removeAttribute('data-theme');
  else document.documentElement.setAttribute('data-theme', next);
  const label = next === 'system' ? 'Theme: System' : next === 'light' ? 'Theme: Light' : 'Theme: Dark';
  $('theme').textContent = label;
  $('theme').setAttribute('aria-label', `${label}. Activate to change the color theme.`);
  const dark = next === 'dark' || (next === 'system' && matchMedia('(prefers-color-scheme: dark)').matches);
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', dark ? '#111c36' : '#e7eef8');
  try { localStorage.setItem(themeKey, next); } catch { /* ignore quota */ }
}
try { applyTheme(localStorage.getItem(themeKey) || 'system'); } catch { applyTheme('system'); }
$('theme').onclick = () => {
  const current = document.documentElement.getAttribute('data-theme') || 'system';
  applyTheme(themeOrder[(themeOrder.indexOf(current) + 1) % themeOrder.length]);
};
matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
  if (!document.documentElement.hasAttribute('data-theme')) applyTheme('system');
});

window.addEventListener('beforeinstallprompt', event => {
  event.preventDefault();
  installPrompt = event;
  $('install').hidden = false;
});
$('install').onclick = async () => {
  if (!installPrompt) return;
  await installPrompt.prompt();
  installPrompt = null;
  $('install').hidden = true;
};
if ('serviceWorker' in navigator) navigator.serviceWorker.register('./sw.js').catch(() => {});

window.addEventListener('studyforge-image', event => {
  if (!(event.detail instanceof HTMLImageElement)) return;
  photo = event.detail;
  drawPhoto(event.detail);
  status('Generated image loaded; Undo restores your previous canvas.');
});
