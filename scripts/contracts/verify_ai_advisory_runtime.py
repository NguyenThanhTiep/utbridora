"""Independent extension schema/registration checks, not a replacement for runtime IT/model evals."""
from pathlib import Path
from datetime import datetime, timezone
import argparse
import csv
import hashlib
import json
import re
from check_ai_advisory import load, invariants
from jsonschema import Draft202012Validator, FormatChecker

ROOT=Path(__file__).resolve().parents[2]
PACK=ROOT/'docs/contracts/extensions/ai-advisory-v1'
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()

def run(wire):
    results=[]
    def record(name, okay, detail): results.append({'id':name,'result':'PASS' if okay else 'FAIL','detail':detail})
    decision=load(ROOT/'docs/contracts/extensions/AI_ADVISORY_IMPLEMENTATION_DECISION.json')
    assert decision['authorization']=='USER_REQUESTED_STAGE_6_IMPLEMENTATION_2026_11_21'
    assert sha(ROOT/decision['specManifest'])==decision['specManifestRawSha256']
    manifest=load(PACK/'manifest.json')
    assert all(sha(ROOT/p)==digest for p,digest in manifest['artifactHashes'].items())
    record('frozen-extension-and-implementation-decision',True,'exact stage-5 artifact bytes; historical source-map is not relabelled as current')
    schema=load(PACK/'schemas.json');Draft202012Validator.check_schema(schema)
    cases=load(PACK/'examples.json')['cases']
    for case in cases:
        validator=Draft202012Validator({'$ref':'#/$defs/'+case['schema'],'$defs':schema['$defs']},format_checker=FormatChecker())
        errors=list(validator.iter_errors(case['value']))
        if not errors: errors=invariants(case['schema'],case['value'])
        record('spec-'+case['id'],bool(not errors)==case['valid'],'synthetic specification example')
    values=load(wire)
    assert len(values)==4
    for value in values:
        validator=Draft202012Validator({'$ref':'#/$defs/'+value['schema'],'$defs':schema['$defs']},format_checker=FormatChecker())
        errors=[e.message for e in validator.iter_errors(value['value'])]
        if not errors: errors=invariants(value['schema'],value['value'])
        record('production-dto-'+value['schema'],not errors,'; '.join(errors) if errors else 'actual production Java DTO/policy serialization, synthetic inputs')
    sql=(ROOT/'src/main/resources/db/migration/base/V44__school_ai_advisory.sql').read_text(encoding='utf-8')
    with (PACK/'permissions.csv').open(encoding='utf-8',newline='') as f: permissions=list(csv.DictReader(f))
    for p in permissions:
        record(p['permission'],"('SCHOOL_STAFF','"+p['permission']+"')" in sql and "('"+p['permission']+"','matching_advisory'" in sql,'migration registration; does not automatically grant a delegation')
    enum=(ROOT/'src/main/java/vn/utbridora/auth/audit/AdvisoryAuditAction.java').read_text(encoding='utf-8')
    registry=(ROOT/'src/main/java/vn/utbridora/auth/audit/WorkflowAuditRegistry.java').read_text(encoding='utf-8')
    with (PACK/'audit.csv').open(encoding='utf-8',newline='') as f: audit=list(csv.DictReader(f))
    for a in audit:
        match=re.search(r'Map.entry\("'+a['action']+r'",new Spec\("matching_advisory",Set.of\(([^)]*)\)\)\)',registry)
        okay=bool(match) and set(re.findall(r'"([^"]+)"',match[1]))==set(a['metadata'].split('; ')) and a['action'] in enum
        record(a['action'],okay,'exact separate extension enum/resource/metadata allowlist')
    security=(ROOT/'src/main/java/vn/utbridora/auth/security/SecurityConfig.java').read_text(encoding='utf-8')
    record('security-route-registration',all(p in security for p in ('/api/v1/matching/runs/*/advisories','/api/v1/matching/advisories/*','/api/v1/matching/advisories/*/items')),'real SecurityConfig tests separately cover session/CSRF/default deny')
    return {'checkedAtUtc':datetime.now(timezone.utc).isoformat(),'scope':'EXTENSION_SCHEMA_AND_STATIC_BINDINGS_WITH_SERIALIZED_PRODUCTION_DTOS',
            'counts':{'checks':len(results),'passed':sum(x['result']=='PASS' for x in results),'failed':sum(x['result']=='FAIL' for x in results)},
            'notClaimed':['MySQL runtime','real model quality','full application browser E2E','Docker build'], 'results':results}

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--wire',type=Path,default=ROOT/'target/advisory-wire-values.json');parser.add_argument('--output',type=Path)
    args=parser.parse_args();report=run(args.wire)
    if args.output: args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in report.items() if k!='results'},indent=2))
    for r in report['results']:
        if r['result']=='FAIL': print(r)
    raise SystemExit(1 if report['counts']['failed'] else 0)
