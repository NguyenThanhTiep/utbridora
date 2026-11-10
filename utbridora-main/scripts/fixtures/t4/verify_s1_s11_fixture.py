"""Verify the independent Week 1 QA oracle against frozen SRS text; does not run a classifier."""
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[3]
fixture = json.loads((ROOT / 'docs/testing/fixtures/t4-s1-s11.json').read_text(encoding='utf-8'))
source = ROOT / fixture['source']['path']
assert hashlib.sha256(source.read_bytes()).hexdigest() == fixture['source']['sha256'], 'Re-review oracle after SRS changes'
with ZipFile(source) as archive:
    document = ET.fromstring(archive.read('word/document.xml'))
ns = {'w': 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'}
paragraphs = [''.join(p.itertext()) for p in document.findall('.//w:p', ns)]
source_outcomes = {p: paragraphs[i + 2] for i, p in enumerate(paragraphs) if re.fullmatch(r'S(?:[1-9]|10|11)', p)}
assert {case['id'] for case in fixture['cases']} == set(source_outcomes) == {f'S{i}' for i in range(1, 12)}
assert len(fixture['cases']) == 11 and fixture['impliesEnabled'] is False
for case in fixture['cases']:
    source_text = source_outcomes[case['id']]
    if case['id'] == 'S9':
        assert 'Strict): NOT_MATCH' in source_text and case['expectedOutcome'] == 'NOT_MATCH'
    else:
        assert source_text.startswith(case['expectedOutcome']), case['id']
assert fixture['classifierExecutionStatus'] == 'NOT_RUN_WEEK2_TASK'
assert fixture['humanQaSignoff'] == 'NOT_RECORDED'
print('11 independent SRS expectations verified; classifier execution remains a Week 2 check.')
