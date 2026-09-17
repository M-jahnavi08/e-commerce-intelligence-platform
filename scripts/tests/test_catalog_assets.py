"""Keep the optional sample catalog and its bundled galleries reproducible."""
import ast
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]

class CatalogAssetsTests(unittest.TestCase):
    def test_every_seed_product_has_a_named_local_gallery(self):
        tree = ast.parse((ROOT / 'scripts/demo-data.py').read_text(encoding='utf-8'))
        catalog = []
        for statement in tree.body:
            if isinstance(statement, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'catalog' for t in statement.targets):
                catalog = ast.literal_eval(statement.value)
            elif isinstance(statement, ast.AugAssign) and isinstance(statement.target, ast.Name) and statement.target.id == 'catalog':
                catalog += ast.literal_eval(statement.value)
        self.assertGreaterEqual(len(catalog), 18)
        self.assertEqual(len(catalog), len({item[0] for item in catalog}))
        for sku, name, category, price, stock, description in catalog:
            with self.subTest(sku=sku):
                self.assertNotIn('verification', name.lower())
                self.assertGreater(price, 0)
                self.assertGreaterEqual(stock, 0)
                self.assertTrue(category and description)
                for suffix in ['', '-detail']:
                    asset = ROOT / 'frontend/public/images/products' / (sku.lower() + suffix + '.svg')
                    self.assertEqual(ET.parse(asset).getroot().tag, '{http://www.w3.org/2000/svg}svg')

if __name__ == '__main__':
    unittest.main()
