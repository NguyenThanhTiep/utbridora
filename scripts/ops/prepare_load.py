"""Create load demands using real HR APIs, verify400/5000 once, and emit an unmeasured recipe.

The one-shot checks are functional verification only. Restart/record the runtime
before the separate load run; no fixed-hardware performance PASS is produced here.
"""
import argparse
import hashlib
import os
from pathlib import Path
import urllib.parse
from handoff import private_json, read
from load import Session, measured, RECIPES


def prepare(seed_path, base, commit):
    parsed=urllib.parse.urlsplit(base)
    if parsed.scheme not in ('http','https') or parsed.hostname not in ('127.0.0.1','::1') or parsed.path not in ('','/') or parsed.query or parsed.fragment or parsed.username:
        raise ValueError('Use the isolated VM loopback API')
    seed=read(seed_path)
    if seed.get('status')!='SEEDED_NOT_MEASURED' or seed['profiles']!=5000 or seed['enterprises']!=100 or seed['skillRelations']!=50000:
        raise ValueError('Canonical5000/100/50000 seed evidence required')
    hr=Session(base,{'emailEnv':'UTB_LOAD_HR_EMAIL','passwordEnv':'UTB_LOAD_PASSWORD'})
    school=Session(base,seed['accounts'][0]);scenarios=[];checks=[];run5000=None
    for count in (400,5000):
        status,created=hr.request('POST','/api/v1/enterprise/demands',seed['demandCreateRequests'][str(count)],{'X-Organization-Id':seed['enterpriseOrgId']})
        if status!=201:raise ValueError('Real HR Demand creation failed')
        scenario={'name':'matching'+str(count),'recipe':'matching'+str(count),'fixtureProfiles':count,'requiredCriteria':5 if count==400 else 10,
                  'request':{'demandId':created['demandId'],'expectedDemandVersion':created['version'],'expectedCriteriaVersion':created['criteriaVersion']}}
        code,body=school.request('POST','/api/v1/matching/evaluate',scenario['request'])
        summary=body.get('summary',{}) if isinstance(body,dict) else {}
        if code!=200 or summary.get('matched')!=count or summary.get('missingData')!=0 or summary.get('notMatched')!=0:
            raise ValueError('Real backend did not evaluate the canonical fixture correctly')
        if count==5000:run5000=summary['run']['matchRunId']
        checks.append({'profiles':count,'requiredCriteria':scenario['requiredCriteria'],'status':'FUNCTIONAL_PASS_NOT_LOAD'})
        scenarios.append(scenario)
    scenarios.append({'name':'matching-details5000','recipe':'list','fixtureProfiles':5000,'expectedTotal':5000,
                      'path':'/api/v1/matching/runs/'+run5000+'/details?overallStatus=MATCH&page=0&size=100'})
    sample=measured(school,scenarios[-1],RECIPES['list'],'functional-setup',0)
    if not sample['ok']:raise ValueError('Prepared5000-row details page failed')
    return {'version':1,'isolatedSynthetic':True,'status':'PREPARED_NOT_MEASURED','baseUrl':base,'commit':commit,
            'seedArtifact':str(Path(seed_path).absolute()),'seedSha256':hashlib.sha256(Path(seed_path).read_bytes()).hexdigest(),
            'accounts':seed['accounts'],'scenarios':scenarios,'fixtureChecks':checks,
            'runtimeManifest':None,'coldStartRef':None,'ssdEvidence':None,
            'next':'Record actual Java/MySQL/image/CPU/RAM/SSD/commit, restart process and set coldStartRef; then run load.py',
            'limits':['No concurrency, percentile or fixed-hardware acceptance inferred from setup checks']}


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--seed',required=True);p.add_argument('--base-url',required=True);p.add_argument('--commit',required=True);p.add_argument('--output',required=True)
    a=p.parse_args()
    if Path(a.output).exists():raise ValueError('Output exists')
    result=prepare(a.seed,a.base_url.rstrip('/'),a.commit);private_json(a.output,result);print(result['status'])
