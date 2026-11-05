/** Offline science-catalog helpers shared by the web studio. No network and no model calls. */

export function studyPath(nodes, id) {
  const byId = new Map(nodes.map(node => [node.id, node]));
  const result = [];
  let current = byId.get(id);
  if (!current) throw new Error('Unknown study view');
  while (current) {
    if (result.some(item => item.id === current.id)) throw new Error('Cyclic study path');
    result.push(current);
    const parentId = current.parent;
    current = parentId ? byId.get(parentId) : null;
    if (parentId && !current) throw new Error('Unknown study view');
  }
  return result.reverse();
}

export function generationPrompt(catalog, id) {
  const path = studyPath(catalog.nodes, id);
  const node = path[path.length - 1];
  return `Study path: ${path.map(item => item.title).join(' > ')}. ${node.prompt} Educational illustration; verify all generated labels against the references.`;
}

export function validateCatalog(data) {
  if (!data || data.version !== 1 || !Array.isArray(data.nodes) || !Array.isArray(data.roots) || !Array.isArray(data.sources)) {
    throw new Error('Invalid study catalog');
  }
  const ids = new Set(data.nodes.map(node => node.id));
  if (ids.size !== data.nodes.length) throw new Error('Invalid study catalog');
  const sources = new Set(data.sources.map(source => source.id));
  for (const node of data.nodes) {
    studyPath(data.nodes, node.id);
    for (const child of node.children || []) if (!ids.has(child)) throw new Error('Invalid study catalog');
    if (node.next && !ids.has(node.next)) throw new Error('Invalid study catalog');
    for (const sourceId of node.sourceIds || []) if (!sources.has(sourceId)) throw new Error('Invalid study catalog');
  }
  for (const root of data.roots) if (!ids.has(root)) throw new Error('Invalid study catalog');
  return data;
}

function roundRect(ctx, x, y, width, height, radius) {
  const r = Math.min(radius, Math.abs(width) / 2, Math.abs(height) / 2);
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + width, y, x + width, y + height, r);
  ctx.arcTo(x + width, y + height, x, y + height, r);
  ctx.arcTo(x, y + height, x, y, r);
  ctx.arcTo(x, y, x + width, y, r);
  ctx.closePath();
}

/** Paint one catalog view onto a 1200×900 context, matching the native schematic geometry. */
export function paintDiagram(ctx, node, labels = true) {
  ctx.save();
  ctx.fillStyle = 'rgb(248, 250, 252)';
  ctx.fillRect(0, 0, 1200, 900);
  const ink = 'rgb(22, 50, 79)';
  const text = (value, x, y, size, bold) => {
    ctx.fillStyle = ink;
    ctx.font = `${bold ? '700 ' : ''}${size}px system-ui, sans-serif`;
    ctx.textAlign = 'left';
    ctx.textBaseline = 'top';
    ctx.fillText(value, x, y - size);
  };
  text(node.title, 60, 66, 38, true);
  text(`${node.level} / simplified schematic`, 60, 106, 23, false);
  for (const shape of node.shapes || []) {
    if (!shape.points || shape.points.length < 4 || shape.points.length % 2) continue;
    const point = index => [60 + shape.points[index] * 1080, 150 + shape.points[index + 1] * 600];
    const [x1, y1] = point(0);
    const [x2, y2] = point(2);
    const left = Math.min(x1, x2);
    const top = Math.min(y1, y2);
    const width = Math.abs(x2 - x1);
    const height = Math.abs(y2 - y1);
    ctx.beginPath();
    ctx.lineWidth = 5;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.strokeStyle = shape.color;
    ctx.fillStyle = shape.color;
    if (shape.kind === 'ellipse') {
      ctx.ellipse(left + width / 2, top + height / 2, Math.max(width / 2, 0.5), Math.max(height / 2, 0.5), 0, 0, Math.PI * 2);
    } else if (shape.kind === 'rect') {
      roundRect(ctx, left, top, Math.max(width, 1), Math.max(height, 1), 18);
    } else {
      ctx.moveTo(x1, y1);
      for (let index = 2; index < shape.points.length; index += 2) {
        const [x, y] = point(index);
        ctx.lineTo(x, y);
      }
    }
    if (shape.fill) ctx.fill();
    ctx.stroke();
  }
  if (labels) {
    for (const label of node.labels || []) text(label.text, 60 + label.x * 1080, 150 + label.y * 600, 29, false);
  }
  text(`References: ${(node.sourceIds || []).join(', ')} / OpenStax`, 60, 823, 22, false);
  text('Original conceptual diagram / not to scale / educational use', 60, 862, 22, false);
  ctx.restore();
}
