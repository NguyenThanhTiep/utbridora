"""Evidence helpers. Standard library only; never load the model here."""
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET


def sha(path):
    digest = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def identity(files):
    return hashlib.sha256(json.dumps(files, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def write(path, value):
    Path(path).write_text(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + '\n', encoding='utf-8')


def verify_source(root):
    root = Path(root).resolve()
    manifest = json.loads((root / '.ai-validation-source.json').read_text(encoding='utf-8'))
    files = manifest['files']
    if not files or identity(files) != manifest['sourceContentSha256']:
        raise ValueError('SOURCE_MANIFEST_INVALID')
    for name, expected in files.items():
        path = root / name
        if path.is_symlink() or not path.resolve().is_relative_to(root) or sha(path) != expected:
            raise ValueError('SOURCE_CONTENT_CHANGED')
    # Reject added application/tool files as well as modifications to registered ones.
    for folder in ('src', 'scripts', 'ai-service', 'web-demo/src', 'web-demo/tests'):
        for path in (root / folder).rglob('*'):
            if path.is_file() and not {'__pycache__', '.pytest_cache'}.intersection(path.parts) and path.suffix != '.pyc':
                if path.relative_to(root).as_posix() not in files:
                    raise ValueError('UNREGISTERED_SOURCE_FILE')
    return manifest


def junit(paths, required=(), allow_skips=False):
    counts = dict(tests=0, passed=0, skipped=0, failures=0, errors=0)
    classes, passed_classes, seen, skips = set(), set(), set(), []
    for path in paths:
        root = ET.parse(path).getroot()
        cases = list(root.iter('testcase'))
        if not cases:
            raise ValueError('EMPTY_TEST_REPORT')
        for suite in root.iter('testsuite'):
            if int(suite.get('failures', 0)) or int(suite.get('errors', 0)):
                raise ValueError('FAILED_TEST_SUITE')
            if suite.find('testsuite') is None and int(suite.get('tests', len(list(suite.iter('testcase'))))) != len(list(suite.iter('testcase'))):
                raise ValueError('INCONSISTENT_TEST_REPORT')
        for case in cases:
            key = (case.get('classname', ''), case.get('name', ''))
            if key in seen:
                raise ValueError('DUPLICATE_TEST_EVIDENCE')
            seen.add(key)
            classes.add(case.get('classname', ''))
            counts['tests'] += 1
            result = next((k for k in ('failure', 'error', 'skipped') if case.find(k) is not None), None)
            key = {'failure': 'failures', 'error': 'errors', 'skipped': 'skipped', None: 'passed'}[result]
            counts[key] += 1
            if result is None:
                passed_classes.add(case.get('classname', ''))
            elif result == 'skipped':
                skips.append(dict(classname=case.get('classname'), name=case.get('name')))
    if not counts['tests'] or counts['failures'] or counts['errors'] or (counts['skipped'] and not allow_skips):
        raise ValueError('TEST_EVIDENCE_NOT_PASS')
    if not set(required).issubset(passed_classes):
        raise ValueError('REQUIRED_TEST_MISSING')
    return dict(counts=counts, classes=sorted(classes), skips=skips)
