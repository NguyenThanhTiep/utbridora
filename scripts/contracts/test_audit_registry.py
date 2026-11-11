"""Add canonical business actions without weakening the protected legacy registry."""
import json
from pathlib import Path
import unittest
from verify_contracts import check_audit_enum


class AuditRegistryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        root = Path(__file__).resolve().parents[2]
        cls.actions = json.loads((root / 'docs/contracts/v1.1/manifest.json').read_text(encoding='utf-8'))['auditActions']
        cls.legacy = {a['action'] for a in cls.actions if a['enumImplemented']}

    def test_canonical_business_actions_are_allowed(self):
        self.assertEqual([], check_audit_enum(self.legacy | {'DEMAND_CHANGED', 'TAXONOMY_CHANGED'}, self.actions))

    def test_missing_legacy_action_is_rejected(self):
        self.assertTrue(check_audit_enum(set(sorted(self.legacy)[1:]), self.actions))

    def test_unknown_action_is_rejected(self):
        self.assertTrue(check_audit_enum(self.legacy | {'UNREGISTERED_EVENT'}, self.actions))


if __name__ == '__main__':
    unittest.main()
