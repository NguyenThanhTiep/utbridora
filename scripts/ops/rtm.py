"""Export requirement-by-requirement DEV traceability; never infer acceptance from a green suite."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile
import xml.etree.ElementTree as ET
from handoff import encoded, private_json, read

ROOT=Path(__file__).resolve().parents[2]
CATEGORIES=('positive','negative','boundary','race')


def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def inventory():
    data=read(ROOT/'docs/verification/requirements.json')
    for path,expected in data['frozenSources'].items():
        if sha(ROOT/path)!=expected:raise ValueError('Frozen requirement source changed')
    rows=data['requirements'];ids=[r['key'] for r in rows]
    if len(ids)!=len(set(ids)):raise ValueError('Duplicate canonical requirement key')
    return data


def evidence(path):
    if path is None:return None,{}
    data=read(path);cases={}
    if data.get('conclusion')!='success':raise ValueError('Evidence run is not successful')
    # A summary alone cannot promote a requirement. Require the actual SHA-checked ZIP/XML.
    for artifact in data['artifacts']:
        location=Path(path).parent/('ci-'+str(data['runId']))/(artifact['name']+'.zip')
        if sha(location)!=artifact['sha256']:raise ValueError('Raw artifact digest mismatch')
        with zipfile.ZipFile(location) as archive:
            for name in archive.namelist():
                if 'failsafe-reports/TEST-' not in name or not name.endswith('.xml'):continue
                xml=ET.fromstring(archive.read(name))
                for case in xml.iter('testcase'):
                    key=(case.get('classname'),case.get('name'))
                    if key in cases:raise ValueError('Duplicate integration testcase in evidence')
                    cases[key]=not any(case.find(kind)is not None for kind in ('failure','error','skipped'))
    return data,cases


def build(evidence_path=None):
    source=inventory();traces=read(ROOT/'docs/verification/traces.json');keys={r['key'] for r in source['requirements']}
    if set(traces)-keys:raise ValueError('Orphan requirement trace')
    run,cases=evidence(evidence_path);rows=[]
    for requirement in source['requirements']:
        r=dict(requirement);trace=traces.get(r['key']);r['trace']=trace;r['acceptance']='UNVERIFIED'
        r['status']='SPECIFIED';r['testStatus']='NOT_RUN';r['gaps']=[]
        if not trace:
            r['gaps']=['No reviewed per-requirement trace; candidate module/suite links cannot certify this requirement']
        else:
            paths=trace['implementation']+trace['apiOrPort']+trace['schema']
            for text in paths:
                if not (ROOT/text).is_file():raise ValueError('Broken implementation/schema trace: '+text)
            if not trace.get('actorGuard'):raise ValueError('Missing actor/guard')
            r['status']='IMPLEMENTED'
            selected=[]
            for category in CATEGORIES:
                tests=trace['tests'].get(category,[])
                if not tests:r['gaps'].append('Missing '+category+' evidence mapping')
                for test in tests:
                    path=ROOT/test['source'];content=path.read_text(encoding='utf-8')
                    if not re.search(r'\bvoid\s+'+re.escape(test['method'])+r'\s*\(',content):raise ValueError('Orphan test method')
                    selected.append((test['class'],test['method']))
            if run and selected:
                if all(cases.get(item) is True for item in selected):
                    r['testStatus']='HISTORICAL_PASS'
                    r['evidence']={'runId':run['runId'],'commit':run['sha'], 'rawArtifacts':[a['sha256'] for a in run['artifacts']]}
                    # A changed implementation/test since the tested commit invalidates that binding.
                    check_paths=paths+[t['source'] for group in trace['tests'].values() for t in group]
                    proc=subprocess.run(['git','-c','safe.directory='+ROOT.as_posix(),'diff','--quiet',run['sha'],'--',*check_paths],cwd=ROOT)
                    if proc.returncode:r['testStatus']='UNVERIFIED';r['gaps'].append('Relevant source differs from evidence commit')
                else:r['testStatus']='UNVERIFIED';r['gaps'].append('Mapped tests missing, skipped or failed in exact run')
            if trace.get('limits'):r['gaps'].extend(trace['limits'])
        rows.append(r)
    return {'version':1,'source':{k:v for k,v in source.items() if k!='requirements'},'requirements':rows,
            'counts':{'requirements':len(rows),'reviewedTraces':len(traces),'historicalPassTests':sum(r['testStatus']=='HISTORICAL_PASS' for r in rows),
                      'acceptancePass':0,'requirementsWithGaps':sum(bool(r['gaps']) for r in rows)},
            'rules':['IMPLEMENTED records reviewed source/test mappings, not full SRS acceptance',
                     'HISTORICAL_PASS binds exact real integration cases and raw artifact digests; never QA signoff',
                     'SPECIFIED/NOT_RUN/UNVERIFIED remain explicit; all unreviewed requirements remain visible',
                     'Canonical oracle overrides are recorded separately; frozen sources and Plan are unchanged']}


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--evidence');p.add_argument('--output',required=True)
    p.add_argument('--require-complete',action='store_true');a=p.parse_args();report=build(a.evidence);private_json(a.output,report)
    print(json.dumps(report['counts']))
    if a.require_complete and any(r['gaps'] or r['testStatus']!='HISTORICAL_PASS' for r in report['requirements'] if r['mandatory']):
        raise SystemExit('Required RTM review/evidence remains incomplete; no acceptance gate was passed')


if __name__=='__main__':main()
