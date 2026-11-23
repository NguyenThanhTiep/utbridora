"""Collect exact-commit GitHub DEV evidence, verifying raw artifact digests and full IT inventory."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.error
import urllib.request
import zipfile
import xml.etree.ElementTree as ET
from handoff import private_json

ROOT=Path(__file__).resolve().parents[2]
REPO='NguyenThanhTiep/utbridora'


def git(*args):
    return subprocess.check_output(['git','-c','safe.directory='+ROOT.as_posix(),*args],cwd=ROOT,text=True,encoding='utf-8').strip()


def api(path):
    req=urllib.request.Request('https://api.github.com/repos/'+REPO+'/'+path,
        headers={'Authorization':'Bearer '+os.environ['GITHUB_TOKEN'],'Accept':'application/vnd.github+json','User-Agent':'UTBridora-DEV-evidence'})
    with urllib.request.urlopen(req,timeout=45) as response:return json.load(response)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args):return None


def download(id):
    req=urllib.request.Request('https://api.github.com/repos/'+REPO+'/actions/artifacts/'+str(id)+'/zip',
        headers={'Authorization':'Bearer '+os.environ['GITHUB_TOKEN'],'User-Agent':'UTBridora-DEV-evidence'})
    try:
        with urllib.request.build_opener(NoRedirect()).open(req,timeout=45) as response:return response.read()
    except urllib.error.HTTPError as error:
        if error.code not in (301,302,303,307,308):raise
        # Signed artifact URL receives no GitHub token/header.
        with urllib.request.urlopen(error.headers['Location'],timeout=60) as response:return response.read()


def collect(run_id,expected_sha,folder):
    if not re.fullmatch(r'[0-9a-f]{40}',expected_sha):raise ValueError('Pin a full commit SHA')
    git('cat-file','-e',expected_sha+'^{commit}')
    run=api('actions/runs/'+str(run_id))
    if run['head_sha']!=expected_sha or run['conclusion']!='success' or run['path']!='.github/workflows/verify.yml':
        raise ValueError('Wrong commit/workflow or incomplete/failed run')
    jobs=api('actions/runs/'+str(run_id)+'/jobs?per_page=100')['jobs']
    if {j['name'] for j in jobs}!={'contracts','fast','integration (1)','integration (2)','integration (3)','verify'} or any(j['conclusion']!='success' for j in jobs):
        raise ValueError('Every required check and shard must succeed')
    artifacts=api('actions/runs/'+str(run_id)+'/artifacts?per_page=100')['artifacts']
    expected={'test-results','contract-results','integration-results-1','integration-results-2','integration-results-3'}
    if len(artifacts)!=5 or {a['name'] for a in artifacts}!=expected:raise ValueError('Artifact inventory mismatch')
    folder=Path(folder);folder.mkdir(mode=0o700,exist_ok=False)
    target=folder/('ci-'+str(run_id));target.mkdir(mode=0o700)
    totals={k:dict(tests=0,failures=0,errors=0,skipped=0) for k in ('unit','integration','browser','pythonFakeEncoder','advisoryComponentMockHttp')}
    suites=[];browser_seen=set();verified=[];contract=None;operations={};advisory_contract=None
    for artifact in artifacts:
        if artifact['expired'] or artifact['workflow_run']['head_sha']!=expected_sha:raise ValueError('Expired/wrong-commit artifact')
        raw=download(artifact['id']);digest=hashlib.sha256(raw).hexdigest()
        if artifact.get('digest')!='sha256:'+digest:raise ValueError('Artifact API SHA256 mismatch')
        path=target/(artifact['name']+'.zip')
        with path.open('xb') as file:os.chmod(path,0o600);file.write(raw)
        verified.append({'id':artifact['id'],'name':artifact['name'],'sha256':digest})
        with zipfile.ZipFile(io.BytesIO(raw)) as archive:
            for name in archive.namelist():
                base=Path(name).name
                if base=='contract-verification.json':contract=json.loads(archive.read(name));continue
                if base=='ai-advisory-contract.json':advisory_contract=json.loads(archive.read(name));continue
                if name.endswith(('operations-verification.json','load-fixture-verification.json')):
                    operations[base]=json.loads(archive.read(name));continue
                if not base.endswith('.xml'):continue
                kind=('pythonFakeEncoder' if base=='ai-python-results.xml' else
                      'advisoryComponentMockHttp' if base=='advisory-browser.xml' else
                      'unit' if 'surefire-reports/' in name and base.startswith('TEST-') else
                      'integration' if 'failsafe-reports/' in name and base.startswith('TEST-') else
                      'browser' if 'browser' in base or 'test-results/' in name else None)
                if kind is None:continue
                body=archive.read(name)
                if kind in ('browser','advisoryComponentMockHttp'):
                    fingerprint=(kind,hashlib.sha256(body).hexdigest())
                    if fingerprint in browser_seen:continue
                    browser_seen.add(fingerprint)
                xml=ET.fromstring(body)
                if kind=='integration':suites.append(xml.attrib['name'])
                for case in xml.iter('testcase'):
                    totals[kind]['tests']+=1
                    for tag,field in [('failure','failures'),('error','errors'),('skipped','skipped')]:
                        if case.find(tag)is not None:totals[kind][field]+=1
    inventory=[]
    for path in git('ls-tree','-r','--name-only',expected_sha,'src/test/java').splitlines():
        cls=Path(path).stem
        if not path.endswith('.java') or not(cls.startswith('IT')or cls.endswith(('IT','ITCase'))):continue
        source=git('show',expected_sha+':'+path);package=re.search(r'^\s*package\s+([\w.]+)\s*;',source,re.M)
        inventory.append(package[1]+'.'+cls if package else cls)
    if sorted(suites)!=sorted(inventory) or len(suites)!=len(set(suites)):raise ValueError('Full exact-commit IT coverage mismatch')
    if any(c['failures']or c['errors'] for c in totals.values()):raise ValueError('Test failure/error')
    if totals['integration']['skipped']:raise ValueError('Skipped integration cases cannot certify the operational regression')
    if not contract or contract['counts']['failed']:raise ValueError('Missing/failed contract evidence')
    has_ai=bool(git('ls-tree','-r','--name-only',expected_sha,'ai-service/model-lock.json'))
    if has_ai:
        if not advisory_contract or advisory_contract['counts']['failed'] or not advisory_contract['counts']['passed']:
            raise ValueError('Missing/failed advisory extension evidence')
        for kind in ('pythonFakeEncoder','advisoryComponentMockHttp'):
            if not totals[kind]['tests'] or totals[kind]['skipped']:raise ValueError('Missing/skipped AI lightweight evidence')
    if 'vn.utbridora.auth.Package9OperationsIT' in inventory and set(operations)!={'operations-verification.json','load-fixture-verification.json'}:
        raise ValueError('Package9 actual operational reports are missing')
    for name,report in operations.items():
        if report['commit']!=expected_sha or report['status'] not in ('PASS','FUNCTIONAL_PASS_NOT_LOAD'):raise ValueError('Operational report is not valid for the exact commit')
    result={'runId':str(run_id),'sha':expected_sha,'conclusion':'success','artifacts':verified,'counts':totals,
            'integrationSuites':sorted(suites),'contracts':contract['counts'],'advisoryContracts':advisory_contract['counts'] if advisory_contract else None,'operations':operations,
            'acceptance':'UNVERIFIED','limits':['DEV regression only; conditional browser skips remain explicit','Fixed-hardware load, staging/RPO/RTO and QA/Lead/school acceptance are separate gates']}
    private_json(folder/('VERIFIED_CI-'+str(run_id)+'.json'),result)
    print(json.dumps({'runId':run_id,'sha':expected_sha,'counts':totals,'integrationSuites':len(suites),'operations':list(operations)}))


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--run',type=int,required=True);p.add_argument('--sha',required=True);p.add_argument('--output-dir',required=True);a=p.parse_args()
    collect(a.run,a.sha,a.output_dir)
