"""Expose bounded JUnit diagnostics in GitHub Checks even if artifact download fails."""
from pathlib import Path
import hashlib
import json
import os
import re
import sys
import xml.etree.ElementTree as ET


def escape(value):
    return value.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')


unit = '--unit' in sys.argv
directory = Path('target/surefire-reports' if unit else 'target/failsafe-reports')
groups = [('unit' if unit else 'integration', sorted(directory.glob('TEST-*.xml')))]
if not unit:
    groups.append(('browser', sorted(path for path in Path('target').glob('*browser*.xml'))))
summaries = []
for group, reports in groups:
    counts = dict(tests=0, failures=0, errors=0, skipped=0)
    digests, classes = [], []
    for path in reports:
        raw = path.read_bytes()
        suite = ET.fromstring(raw)
        classes.append(suite.get('name'))
        digests.append(path.name + ':' + hashlib.sha256(raw).hexdigest())
        for case in suite.iter('testcase'):
            counts['tests'] += 1
            for tag, field in [('failure', 'failures'), ('error', 'errors'), ('skipped', 'skipped')]:
                counts[field] += case.find(tag) is not None
    summary = dict(group=group, sourceSha=os.environ.get('GITHUB_SHA'), counts=counts,
                   suiteCount=len(reports), reportDigest=hashlib.sha256('\n'.join(digests).encode()).hexdigest())
    if group == 'integration':
        summary['suiteClasses'] = classes
        summary['shard'] = json.loads(Path('target/ci-shard.json').read_text())['shard']
    summaries.append(summary)
    print('::notice title=JUnit summary::' + escape(json.dumps(summary, separators=(',', ':'))))
Path('target').mkdir(exist_ok=True)
Path('target/ci-result-summary.json').write_text(json.dumps(summaries, indent=2), encoding='utf-8')

for path in sorted(directory.glob('TEST-*.xml')):
    try:
        suite = ET.parse(path).getroot()
    except ET.ParseError:
        print('::error::Invalid JUnit report: ' + escape(path.name))
        continue
    for case in suite.iter('testcase'):
        problem = case.find('failure')
        if problem is None:
            problem = case.find('error')
        if problem is None:
            continue
        lines = (problem.text or '').splitlines()
        causes = [line.strip() for line in lines if line.lstrip().startswith('Caused by:')]
        message = problem.get('message') or (lines[0] if lines else problem.tag)
        detail = f'{case.get("classname")}.{case.get("name")}: {message} | ' + ' | '.join(causes[-3:])
        # Never echo credentials/connection strings from infrastructure exceptions.
        detail = re.sub(r'(?i)(password|token|secret|authorization)[=: ]+\S+', r'\1=<redacted>', detail)
        detail = re.sub(r'jdbc:\S+', '<database>', detail)
        print('::error title=Integration test failed::' + escape(detail[:2000]))
