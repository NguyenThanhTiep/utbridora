"""Synthetic tests of validation tools; these are not model/quality acceptance evidence."""
from types import SimpleNamespace
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
from uuid import UUID
sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import identity, junit, sha, verify_source, write
from evaluate_quality import bootstrap, evaluate, metrics, MODEL_REVISION
from package_source import permitted, untracked_permitted
from run_isolated import preflight


def fixture():
    demands, scores = [], []
    for i in range(40):
        demand = dict(demandId=str(UUID(int=i + 1)), split='tuning' if i < 10 else 'heldout', duplicateGroup='d' + str(i), candidates=[])
        measured = dict(demandId=demand['demandId'], items=[])
        for j in range(10):
            label = 3 if j == 9 else 0
            student = str(UUID(int=1000 + i * 10 + j))
            demand['candidates'].append(dict(studentUserId=student, overallStatus='MATCH', fitScore=str(100 - j),
                renderedSourceSha256='a' * 64, duplicateGroup='p' + str(i * 10 + j), reviewerLabels=[label, label], relevance=label))
            measured['items'].append(dict(studentUserId=student, scoreStatus='SCORED', semanticScore='0.9' if label else '0.1'))
        demands.append(demand)
        scores.append(measured)
    return (dict(schemaVersion='ai-advisory-labels-v1', dataKind='SYNTHETIC', reviewerIds=['test-r1', 'test-r2'],
                 frozenAtUtc='2026-11-21T00:00:00Z', protocolReviewRef='synthetic-test-only', demands=demands),
            dict(schemaVersion='ai-advisory-scores-v1', sourceContentSha256='b' * 64, modelRevision=MODEL_REVISION,
                 templateVersion='school-advisory-text-v1', policyVersion='semantic-desc-canonical-tie-v1',
                 measuredAtUtc='2026-11-22T00:00:00Z', demands=scores))


class QualityTests(unittest.TestCase):
    def test_known_metrics(self):
        self.assertEqual(metrics(['a', 'b'], {'a': 3, 'b': 0}), (1, .1))
        self.assertEqual(metrics(['a'], {'a': 0}), (None, 0))

    def test_bootstrap_is_paired_and_reproducible(self):
        self.assertEqual(bootstrap([.1] * 30), bootstrap([.1] * 30))
        self.assertAlmostEqual(bootstrap([.1] * 30)[0], .1)

    def test_synthetic_never_quality_acceptance(self):
        report = evaluate(*fixture())
        self.assertTrue(all(report['proposedGates'].values()))
        self.assertFalse(report['eligibleForQualityReview'])
        self.assertFalse(report['qualityAcceptance'])

    def test_missing_population_rejected(self):
        data, scores = fixture()
        scores['demands'][-1]['items'].pop()
        with self.assertRaisesRegex(ValueError, 'POPULATION'):
            evaluate(data, scores)

    def test_nonmatch_rejected(self):
        data, scores = fixture()
        data['demands'][-1]['candidates'][0]['overallStatus'] = 'NOT_MATCH'
        with self.assertRaisesRegex(ValueError, 'NON_MATCH'):
            evaluate(data, scores)

    def test_duplicate_group_across_split_rejected(self):
        data, scores = fixture()
        data['demands'][-1]['candidates'][0]['duplicateGroup'] = data['demands'][0]['candidates'][0]['duplicateGroup']
        with self.assertRaisesRegex(ValueError, 'LEAKAGE'):
            evaluate(data, scores)

    def test_disagreement_requires_adjudication(self):
        data, scores = fixture()
        data['demands'][0]['candidates'][0]['reviewerLabels'] = [0, 1]
        with self.assertRaisesRegex(ValueError, 'ADJUDICATION'):
            evaluate(data, scores)

    def test_nan_and_missing_scores_rejected(self):
        for invalid in ('NaN', 'Infinity', None, True):
            data, scores = fixture()
            scores['demands'][0]['items'][0]['semanticScore'] = invalid
            with self.assertRaises((ValueError, ArithmeticError)):
                evaluate(data, scores)

    def test_null_scores_retained_and_coverage_reported(self):
        data, scores = fixture()
        for demand in scores['demands']:
            for row in demand['items']:
                row.update(scoreStatus='NO_CONTENT', semanticScore=None)
        report = evaluate(data, scores)
        self.assertEqual(report['coverage'], 0)
        self.assertEqual(report['macroNdcg10Delta'], 0)
        self.assertEqual(len(report['perDemand']), 30)

    def test_rounding_ties_follow_canonical_order(self):
        data, scores = fixture()
        for demand in scores['demands']:
            for row in demand['items']:
                row['semanticScore'] = '0.50000049'
            demand['items'][0]['semanticScore'] = '0.50000040'
        self.assertEqual(evaluate(data, scores)['macroNdcg10Delta'], 0)

    def test_zero_idcg_reported_and_sample_gate_fails(self):
        data, scores = fixture()
        for c in data['demands'][-1]['candidates']:
            c.update(reviewerLabels=[0, 0], relevance=0)
        report = evaluate(data, scores)
        self.assertEqual(report['idcgZeroDemands'], 1)
        self.assertEqual(report['ndcgEligibleDemands'], 29)
        self.assertFalse(report['proposedGates']['independentSample'])

    def test_post_measurement_freeze_rejected(self):
        data, scores = fixture()
        data['frozenAtUtc'] = scores['measuredAtUtc']
        with self.assertRaisesRegex(ValueError, 'FREEZE'):
            evaluate(data, scores)

    def test_duplicate_demands_do_not_inflate_independent_sample(self):
        data, scores = fixture()
        data['demands'][-1]['duplicateGroup'] = data['demands'][-2]['duplicateGroup']
        self.assertFalse(evaluate(data, scores)['proposedGates']['independentSample'])


class EvidenceTests(unittest.TestCase):
    def test_report_failures_skips_duplicates_missing_required_fail(self):
        with tempfile.TemporaryDirectory() as tmp:
            file = Path(tmp) / 'test.xml'
            for body in ('<failure/>', '<error/>', '<skipped/>'):
                file.write_text('<testsuite tests="1"><testcase classname="IT" name="test">' + body + '</testcase></testsuite>')
                with self.assertRaises(ValueError):
                    junit([file], required=['IT'])
            file.write_text('<testsuite tests="1"><testcase classname="IT" name="test"/></testsuite>')
            self.assertEqual(junit([file], required=['IT'])['counts']['passed'], 1)
            with self.assertRaisesRegex(ValueError, 'REQUIRED_TEST'):
                junit([file], required=['DifferentIT'])
            with self.assertRaisesRegex(ValueError, 'DUPLICATE'):
                junit([file, file])

    def test_no_credentials_plan_model_or_outputs_in_package(self):
        for name in ('ai-service/.env', '.env.production', 'docs/plans/UTBridora_Plan.xlsx',
                     'ai-service/models/model.safetensors', 'web-demo/node_modules/a.js',
                     'target/app.jar', 'docs/ACCOUNTS_PRIVATE.md', 'credentials.private.json', '.git/config'):
            self.assertFalse(permitted(name), name)
        for name in ('src/main/java/PrivateFileStorage.java', 'ai-service/.env.example',
                     'src/main/resources/demo/portable/full-cycle-v1.json.gz', 'web-demo/src/main.tsx'):
            self.assertTrue(permitted(name), name)
        for name in ('ai-service/labels.json', 'ai-service/results.xml', 'docs/contracts/extensions/ai-advisory-v1/labels.json'):
            self.assertFalse(untracked_permitted(name), name)

    def test_source_hash_change_and_unregistered_source_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / 'src').mkdir()
            source = root / 'src/A.java'
            source.write_text('class A {}')
            files = {'src/A.java': sha(source)}
            write(root / '.ai-validation-source.json', dict(files=files, sourceContentSha256=identity(files)))
            verify_source(root)
            (root / 'ai-service/.pytest_cache').mkdir(parents=True)
            (root / 'ai-service/.pytest_cache/README.md').write_text('pytest-generated cache')
            verify_source(root)
            source.write_text('changed')
            with self.assertRaisesRegex(ValueError, 'CHANGED'):
                verify_source(root)
            source.write_text('class A {}')
            (root / 'src/B.java').write_text('class B {}')
            with self.assertRaisesRegex(ValueError, 'UNREGISTERED'):
                verify_source(root)

    def test_workstation_refused_before_docker_or_model(self):
        with patch('run_isolated.platform.system', return_value='Windows'), patch('run_isolated.subprocess.check_output') as command:
            with self.assertRaisesRegex(ValueError, 'ISOLATED_LINUX'):
                preflight(Path('.'), 'expected', 'docker-id')
            command.assert_not_called()

    def test_linux_wrong_machine_id_refused_before_docker(self):
        with patch('run_isolated.platform.system', return_value='Linux'), patch('pathlib.Path.read_text', return_value='actual-id'), patch('run_isolated.subprocess.check_output') as command:
            with self.assertRaisesRegex(ValueError, 'MACHINE_ID_MISMATCH'):
                preflight(Path('.'), 'different-id', 'docker-id')
            command.assert_not_called()

    def test_docker_daemon_mismatch_or_existing_containers_refused(self):
        def read(path, *args, **kwargs):
            return 'machine-id' if path.name == 'machine-id' else 'MemTotal: 16777216 kB\nMemAvailable: 8388608 kB\n'
        for response, code in (([b'{"ID":"wrong","OSType":"linux"}'], 'DOCKER_ID_MISMATCH'),
                               ([b'{"ID":"expected","OSType":"linux"}', b'container'], 'DEDICATED_EMPTY_DOCKER')):
            with patch('run_isolated.platform.system', return_value='Linux'), patch('run_isolated.platform.platform', return_value='Synthetic Linux'), patch('pathlib.Path.read_text', read), patch('run_isolated.shutil.disk_usage', return_value=SimpleNamespace(free=100 * 1024**3)), patch('run_isolated.subprocess.check_output', side_effect=response), patch.dict('os.environ', {}, clear=True):
                with self.assertRaisesRegex(ValueError, code):
                    preflight(Path('.'), 'machine-id', 'expected')


if __name__ == '__main__':
    unittest.main()
