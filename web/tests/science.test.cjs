const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

(async () => {
  const {generationPrompt, paintDiagram, studyPath, validateCatalog} = await import('../science.mjs');
  const catalog = validateCatalog(JSON.parse(fs.readFileSync(path.join(__dirname, '../../content/study-catalog.json'), 'utf8')));
  const human = generationPrompt(catalog, 'human-body');
  assert.equal(human, 'Study path: Human body. Show the body organization hierarchy; emphasize the cardiovascular system. Educational illustration; verify all generated labels against the references.');
  const titles = studyPath(catalog.nodes, 'cardiomyocyte').map(node => node.title);
  assert.deepEqual(titles, ['Human body', 'Cardiovascular system', 'Heart: chamber flow', 'Cardiac muscle tissue', 'Cardiac muscle cell']);
  assert.equal(studyPath(catalog.nodes, 'telophase').at(-1).id, 'telophase');
  for (const node of catalog.nodes) {
    const prompt = generationPrompt(catalog, node.id);
    assert.ok(prompt.length < 1900, node.id);
    assert.match(prompt, /^Study path: /);
    const calls = [];
    const ctx = new Proxy({}, {
      get: () => (...args) => { calls.push(args); return undefined; },
      set: () => true
    });
    paintDiagram(ctx, node, true);
    paintDiagram(ctx, node, false);
    assert.ok(calls.length > 10, node.id);
  }
  assert.throws(() => validateCatalog({version: 1, nodes: [], roots: ['missing'], sources: []}), /Invalid study catalog/);
  console.log(`Science catalog web helpers passed for ${catalog.nodes.length} views`);
})().catch(error => { console.error(error); process.exitCode = 1; });
