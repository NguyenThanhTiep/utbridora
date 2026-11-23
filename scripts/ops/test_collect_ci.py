"""Synthetic ZIP/API fixtures verify evidence separation and fail-closed collection."""
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import collect_ci as collector

SHA = 'a' * 40


def xml(name, result=''):
    return f'<testsuite name="{name}"><testcase classname="{name}" name="example">{result}</testcase></testsuite>'


def fixture(omit=None, component_result='', corrupt_digest=False):
    contents = {
        'test-results': {'surefire-reports/TEST-Unit.xml': xml('Unit'),
            'ai-python-results.xml': xml('FakeEncoder'), 'ai-component/advisory-browser.xml': xml('Component', component_result),
            'ai-advisory-contract.json': json.dumps({'counts': {'passed': 63, 'failed': 0}})},
        'contract-results': {'contract-verification.json': json.dumps({'counts': {'passed': 878, 'failed': 0}})},
        'integration-results-1': {'failsafe-reports/TEST-FirstIT.xml': xml('vn.FirstIT'), 'real-browser.xml': xml('RealBrowser')},
        'integration-results-2': {'failsafe-reports/TEST-SecondIT.xml': xml('vn.SecondIT')},
        'integration-results-3': {'failsafe-reports/TEST-ThirdIT.xml': xml('vn.ThirdIT')},
    }
    if omit:
        contents['test-results'].pop(omit)
    artifacts, downloads = [], {}
    for i, (name, files) in enumerate(contents.items()):
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, 'w') as archive:
            for path, body in files.items():
                archive.writestr(path, body)
        raw = buffer.getvalue()
        downloads[i] = raw
        digest = hashlib.sha256(raw).hexdigest()
        artifacts.append(dict(id=i, name=name, expired=False, workflow_run={'head_sha': SHA}, digest='sha256:' + digest))
    if corrupt_digest:
        artifacts[0]['digest'] = 'sha256:' + '0' * 64
    def api(path):
        if path.endswith('/jobs?per_page=100'):
            return {'jobs': [dict(name=n, conclusion='success') for n in ('contracts', 'fast', 'integration (1)', 'integration (2)', 'integration (3)', 'verify')]}
        if path.endswith('/artifacts?per_page=100'):
            return {'artifacts': artifacts}
        return dict(head_sha=SHA, conclusion='success', path='.github/workflows/verify.yml')
    def git(*args):
        if args[0] == 'ls-tree':
            return 'ai-service/model-lock.json' if args[-1] == 'ai-service/model-lock.json' else '\n'.join(f'src/test/java/vn/{n}IT.java' for n in ('First', 'Second', 'Third'))
        return 'package vn;' if args[0] == 'show' else ''
    return api, git, downloads


class CollectorTests(unittest.TestCase):
    def collect(self, root, **kwargs):
        api, git, downloads = fixture(**kwargs)
        with patch.object(collector, 'api', side_effect=api), patch.object(collector, 'git', side_effect=git), patch.object(collector, 'download', side_effect=lambda i: downloads[i]):
            collector.collect(123, SHA, root / 'evidence')
        return json.loads((root / 'evidence/VERIFIED_CI-123.json').read_text())

    def test_real_browser_counts_exclude_mock_and_fake_reports(self):
        with tempfile.TemporaryDirectory() as tmp:
            result = self.collect(Path(tmp))
            self.assertEqual(1, result['counts']['browser']['tests'])
            self.assertEqual(1, result['counts']['advisoryComponentMockHttp']['tests'])
            self.assertEqual(1, result['counts']['pythonFakeEncoder']['tests'])
            self.assertEqual(3, result['counts']['integration']['tests'])
            self.assertEqual(63, result['advisoryContracts']['passed'])
            self.assertEqual(5, len(result['artifacts']))

    def test_missing_component_python_or_extension_cannot_certify_ai(self):
        for name in ('ai-component/advisory-browser.xml', 'ai-python-results.xml', 'ai-advisory-contract.json'):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as tmp:
                with self.assertRaises(ValueError):
                    self.collect(Path(tmp), omit=name)

    def test_failed_or_skipped_component_and_bad_artifact_digest_rejected(self):
        for changes in ({'component_result': '<failure/>'}, {'component_result': '<skipped/>'}, {'corrupt_digest': True}):
            with self.subTest(changes=changes), tempfile.TemporaryDirectory() as tmp:
                with self.assertRaises(ValueError):
                    self.collect(Path(tmp), **changes)


if __name__ == '__main__':
    unittest.main()
