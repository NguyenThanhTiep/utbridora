"""Offline specification lint/examples only; does not assert runtime or model quality."""
from __future__ import annotations

import argparse
import csv
from datetime import datetime, timedelta, timezone
from decimal import Decimal
import hashlib
import json
from pathlib import Path
import re
import sys

from jsonschema import Draft202012Validator, FormatChecker

ROOT = Path(__file__).resolve().parents[2]
PACK = ROOT / 'docs/contracts/extensions/ai-advisory-v1'


def load(path):
    def reject(value):
        raise ValueError('Non-finite JSON literal: ' + value)
    return json.loads(path.read_text(encoding='utf-8'), parse_float=Decimal, parse_constant=reject)


def invariants(shape, value):
    errors = []
    if shape == 'AdvisoryMetadata':
        start = datetime.fromisoformat(value['createdAt'])
        end = datetime.fromisoformat(value['expiresAt'])
        if not start < end <= start + timedelta(hours=24):
            errors.append('expiry must be after create and within 24 hours')
        if value['completedAt'] is not None and not start <= datetime.fromisoformat(value['completedAt']) < end:
            errors.append('completion outside valid lifetime')
        counts = value['counts']
        if counts is not None:
            if counts['eligibleCount'] != sum(counts[k] for k in ('scoredCount', 'noContentCount', 'inputTooLargeCount')):
                errors.append('count partition mismatch')
            if (value['state'] == 'READY') != (counts['scoredCount'] > 0):
                errors.append('READY/NO_DATA score count mismatch')
        if value['state'] == 'FAILED' and value['failureCode'] not in {'MODEL_UNAVAILABLE', 'PROTOCOL_INVALID', 'INFERENCE_UNAVAILABLE', 'WORK_TIMEOUT'}:
            errors.append('failed job requires a safe failure code')
    if shape == 'AdvisoryPage':
        eligible, scored, size, page = (value[k] for k in ('eligibleCount', 'scoredCount', 'size', 'page'))
        if scored > eligible or value['totalElements'] != eligible or value['totalPages'] != (eligible + size - 1) // size:
            errors.append('totals mismatch')
        expected = Decimal(scored) / eligible if eligible else Decimal(0)
        if abs(Decimal(value['coverage']) - expected) > Decimal('0.000000001'):
            errors.append('coverage mismatch (tolerance 1e-9)')
        if (value['state'] == 'READY') != (scored > 0):
            errors.append('page state inconsistent with scored count')
        rows = value['content']
        if len(rows) != max(0, min(size, eligible - page * size)):
            errors.append('wrong number of rows for page')
        if len({x['studentUserId'] for x in rows}) != len(rows):
            errors.append('duplicate candidate')
        keys = []
        for i, row in enumerate(rows, start=page * size + 1):
            is_scored = row['scoreStatus'] == 'SCORED'
            if row['position'] != i or (is_scored and row['rank'] != i):
                errors.append('global position/rank mismatch')
            if is_scored != (row['position'] <= scored):
                errors.append('scored group boundary mismatch')
            score = Decimal(row['semanticScore']) if is_scored else Decimal(0)
            keys.append((0 if is_scored else 1, -score, -Decimal(row['fitScore']), row['studentUserId']))
        if keys != sorted(keys):
            errors.append('ranking policy order mismatch')
    if shape == 'Error':
        if value['errorCode'] != value['error']:
            errors.append('error alias mismatch')
        if value['errorCode'] == 'AI_ADVISORY_CAPACITY_EXCEEDED' and value['retryAfterSeconds'] != 30:
            errors.append('capacity retry must be 30 seconds')
        if value['errorCode'] == 'REQUEST_IN_PROGRESS' and value['retryAfterSeconds'] != 1:
            errors.append('ledger retry must be 1 second')
    if shape in {'SemanticSet', 'DemandSemanticSnapshot'}:
        if datetime.fromisoformat(value['capturedAt']) >= datetime.fromisoformat(value['validUntil']):
            errors.append('source lifetime invalid')
    return errors


def run():
    schema = load(PACK / 'schemas.json')
    Draft202012Validator.check_schema(schema)
    results = []
    def record(name, okay, detail):
        results.append({'id': name, 'result': 'PASS' if okay else 'FAIL', 'detail': detail})
    def refs(value):
        if isinstance(value, dict):
            if '$ref' in value:
                name = value['$ref']
                assert name.startswith('#/$defs/') and name[8:] in schema['$defs'], name
            for v in value.values(): refs(v)
        elif isinstance(value, list):
            for v in value: refs(v)
    refs(schema)
    record('schema-metaschema-local-references', True, 'JSON Schema 2020-12; no network resolution')
    cases = load(PACK / 'examples.json')['cases']
    assert len({case['id'] for case in cases}) == len(cases)
    for case in cases:
        validator = Draft202012Validator({'$ref': '#/$defs/' + case['schema'], '$defs': schema['$defs']}, format_checker=FormatChecker())
        errors = [e.message for e in validator.iter_errors(case['value'])]
        if not errors:
            errors = invariants(case['schema'], case['value'])
        valid = not errors
        record(case['id'], valid == case['valid'], 'expected ' + ('valid' if case['valid'] else 'invalid') + '; ' + ('accepted' if valid else 'rejected'))
    for path in PACK.glob('*.md'):
        content = path.read_text(encoding='utf-8')
        assert '\ufffd' not in content
        for link in re.findall(r'\]\(([^)]+)\)', content):
            if not link.startswith(('https://', 'http://', '#')):
                assert (path.parent / link.split('#')[0]).exists(), (path.name, link)
    record('document-local-links-utf8', True, 'local documentation targets exist')
    with (PACK / 'permissions.csv').open(encoding='utf-8', newline='') as f:
        permissions = list(csv.DictReader(f))
    assert {row['permission'] for row in permissions} == {'matching:school:advisory_manage', 'matching:school:advisory_read'}
    assert all(row['role'] == 'SCHOOL_STAFF' and row['status'] == 'PROPOSED_EXTENSION_NOT_INSTALLED' for row in permissions)
    record('extension-permissions', True, 'two school-only proposed capabilities')
    with (PACK / 'audit.csv').open(encoding='utf-8', newline='') as f:
        audits = list(csv.DictReader(f))
    assert len(audits) == 5 and len({x['action'] for x in audits}) == 5
    forbidden = {'score', 'semanticScore', 'query', 'text', 'inputHash', 'token', 'studentUserIds', 'embedding'}
    assert all(not (set(x['metadata'].split('; ')) & forbidden) for x in audits)
    record('extension-audit-allowlists', True, 'no raw content, scores, tokens or student lists in extension metadata')
    source_map = load(PACK / 'source-map.json')
    assert all(hashlib.sha256((ROOT / p).read_bytes()).hexdigest() == digest for p, digest in source_map['sourceHashes'].items())
    record('survey-source-hashes', True, 'source survey pins actual local files; not approval of implementation')
    manifest = load(PACK / 'manifest.json')
    assert all(hashlib.sha256((ROOT / p).read_bytes()).hexdigest() == digest for p, digest in manifest['artifactHashes'].items())
    record('extension-artifact-hashes', True, 'specification artifacts and checker match exact manifest')
    report = {'checkedAtUtc': datetime.now(timezone.utc).isoformat(), 'scope': 'SPECIFICATION_STATIC_CHECKS_ONLY',
              'counts': {'checks': len(results), 'passed': sum(x['result'] == 'PASS' for x in results), 'failed': sum(x['result'] == 'FAIL' for x in results),
                         'validExamples': sum(x['valid'] for x in cases), 'invalidExamples': sum(not x['valid'] for x in cases)},
              'runtimeApi': 'NOT_IMPLEMENTED', 'realModel': 'NOT_RUN', 'quality': 'NOT_RUN', 'results': results}
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    report = run()
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({k: v for k, v in report.items() if k != 'results'}, ensure_ascii=False, indent=2))
    for item in report['results']:
        if item['result'] == 'FAIL': print(item)
    sys.exit(1 if report['counts']['failed'] else 0)
