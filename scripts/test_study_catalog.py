import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

class StudyCatalogTest(unittest.TestCase):
    def setUp(self):
        self.data = json.loads((ROOT / 'content/study-catalog.json').read_text())
        self.nodes = {node['id']: node for node in self.data['nodes']}

    def test_platforms_bundle_identical_offline_content(self):
        original = (ROOT / 'content/study-catalog.json').read_bytes()
        for path in ['app/src/main/assets/study-catalog.json', 'ios/StudyForge/Resources/study-catalog.json']:
            self.assertEqual(original, (ROOT / path).read_bytes())
        self.assertEqual(1, self.data['version'])

    def test_hierarchy_and_phase_sequence_have_no_broken_links_or_cycles(self):
        self.assertEqual(len(self.nodes), len(self.data['nodes']))
        for node in self.nodes.values():
            for key in node['children'] + [x for x in [node['parent'], node['next']] if x]:
                self.assertIn(key, self.nodes)
            for child in node['children']:
                self.assertEqual(node['id'], self.nodes[child]['parent'])
            for edge in ['parent', 'next']:
                seen = set(); current = node
                while current:
                    self.assertNotIn(current['id'], seen)
                    seen.add(current['id']); current = self.nodes.get(current[edge])
        current = self.nodes['cardiomyocyte']; path = []
        while current:
            path.append(current['level']); current = self.nodes.get(current['parent'])
        self.assertEqual(['Cellular', 'Tissue', 'Organ', 'System', 'Organism'], path)
        self.assertEqual('prometaphase', self.nodes['prophase']['next'])
        self.assertEqual('telophase', self.nodes['anaphase']['next'])

    def test_each_view_has_accessible_description_and_primary_source(self):
        sources = {s['id']: s for s in self.data['sources']}
        for node in self.nodes.values():
            self.assertTrue(node['summary'] and node['prompt'] and node['labels'])
            self.assertGreater(len(node['alt']), 60)
            self.assertLess(len(node['prompt']), 500)
            for ref in node['sourceIds']:
                self.assertTrue(sources[ref]['url'].startswith('https://openstax.org/books/'))

    def test_geometry_is_bounded_and_labels_fit_the_canvas(self):
        for node in self.nodes.values():
            for shape in node['shapes']:
                self.assertIn(shape['kind'], ['ellipse', 'rect', 'line'])
                self.assertGreaterEqual(len(shape['points']), 4)
                self.assertEqual(0, len(shape['points']) % 2)
                self.assertTrue(all(0 <= v <= 1 for v in shape['points']))
            for label in node['labels']:
                self.assertTrue(0 <= label['x'] < 1 and 0 <= label['y'] < 1)
                self.assertLess(len(label['text']), 36)

if __name__ == '__main__':
    unittest.main()
