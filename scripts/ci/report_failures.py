"""Expose bounded JUnit diagnostics in GitHub Checks even if artifact download fails."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET


def escape(value):
    return value.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')


for path in sorted(Path('target/failsafe-reports').glob('TEST-*.xml')):
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
