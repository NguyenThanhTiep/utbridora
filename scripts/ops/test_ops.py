import copy
import json
import os
from pathlib import Path
import tempfile
import unittest
import uuid
from handoff import config, inventory, private_json, safe_root
from load import nearest_rank, summarize
from rtm import build


class OperationsSafetyTest(unittest.TestCase):
    def test_percentile_includes_timeouts_and_errors(self):
        data=[{'elapsedMs':10,'ok':True} for _ in range(94)]+[{'elapsedMs':60000,'ok':False} for _ in range(6)]
        report=summarize(data,2000)
        self.assertEqual(60000,report['p95Ms']);self.assertEqual(6,report['errors']);self.assertFalse(report['meetsRecipe'])
        self.assertEqual(95,nearest_rank(list(range(1,101))))
        data=[{'elapsedMs':10,'ok':True} for _ in range(99)]+[{'elapsedMs':60000,'ok':False}]
        self.assertEqual(10,summarize(data,2000)['p95Ms']);self.assertFalse(summarize(data,2000)['meetsRecipe'])

    def test_requirement_inventory_keeps_unreviewed_and_repeated_team_ids_visible(self):
        report=build();self.assertEqual(402,report['counts']['requirements']);self.assertEqual(0,report['counts']['acceptancePass'])
        self.assertTrue(any(r['status']=='SPECIFIED' and r['testStatus']=='NOT_RUN' for r in report['requirements']))
        self.assertFalse(any(r.get('acceptance')=='PASS' for r in report['requirements']))
        self.assertEqual(len(report['requirements']),len({r['key'] for r in report['requirements']}))

    def test_scope_marker_and_shared_database_refusal(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);scope=str(uuid.uuid4());schema='utbridora_pkg9_'+uuid.uuid4().hex[:16]
            private_json(root/'.utbridora-demo-scope.json',{'scopeId':scope,'schema':schema})
            c=dict(version=1,exclusiveSynthetic=True,writersStopped=True,allCopiesRegistered=True,
                   schema=schema,scopeId=scope,serverUuid=str(uuid.uuid4()),host='127.0.0.1',
                   acceptedAt='2026-01-01T00:00:00Z',approvalRef='SYNTHETIC',resources=[{'path':str(root),'kind':'private-files'}])
            path=root/'config.json';path.write_text(json.dumps(c));self.assertEqual(schema,config(path)['schema'])
            for field,value in [('schema','utbridora'),('schema','utbridora_dev'),('writersStopped',False),('exclusiveSynthetic',False),('allCopiesRegistered',False),('host','remote.example')]:
                changed=copy.deepcopy(c);changed[field]=value;path.write_text(json.dumps(changed))
                with self.assertRaises(ValueError):config(path)

    def test_symlinks_and_evidence_overwrite_are_refused(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);file=root/'evidence.json';private_json(file,{'status':'NOT_RUN'})
            with self.assertRaises(FileExistsError):private_json(file,{'status':'PASS'})
            if os.name!='nt':
                (root/'link').symlink_to(file)
                with self.assertRaises(ValueError):inventory(root)
                with self.assertRaises(ValueError):safe_root(str(root/'link'))


if __name__=='__main__':unittest.main()
