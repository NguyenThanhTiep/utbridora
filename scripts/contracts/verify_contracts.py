"""DEV contract checks only. No network, database, controller or email side effects."""
from __future__ import annotations

import argparse
import csv
from copy import deepcopy
from datetime import datetime, timezone
from decimal import Decimal
from email.headerregistry import Address
from email.errors import NonASCIILocalPartDefect, HeaderParseError
import hashlib
import json
import re
import subprocess
import sys
from urllib.parse import urlsplit
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker, validators
from build_contract_bundle import ROOT, OUTPUT, CONTRACT, digest
from contract_cases import cases

FORMATS = FormatChecker()


def json_integer(checker,value):
    if isinstance(value,bool):return False
    if isinstance(value,int):return True
    if isinstance(value,Decimal):return value.is_finite() and value==value.to_integral_value()
    return isinstance(value,float) and value.is_integer()


ContractValidator=validators.extend(Draft202012Validator,type_checker=Draft202012Validator.TYPE_CHECKER.redefine('integer',json_integer))


@FORMATS.checks('email')
def email_address(value):
    if not isinstance(value,str):return True
    if value!=value.strip() or '\r' in value or '\n' in value:return False
    try:
        try:
            address=Address(addr_spec=value)
        except NonASCIILocalPartDefect:
            # Preserve EAI syntax acceptance; only the syntax skeleton is parsed here, never DNS/delivery.
            address=Address(addr_spec=''.join('x' if ord(c)>127 else c for c in value))
        return bool(address.username and address.domain) and len(address.username)<=64
    except (ValueError,IndexError,HeaderParseError):
        return False


@FORMATS.checks('utb-utc-instant')
def utc_instant(value):
    if not isinstance(value, str):
        return True
    try:
        return bool(re.fullmatch(r'\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?(?:Z|\+00:00)', value)) and datetime.fromisoformat(value.replace('Z','+00:00')).utcoffset().total_seconds() == 0
    except ValueError:
        return False


@FORMATS.checks('utb-https-url')
def https_url(value):
    if not isinstance(value, str):
        return True
    try:
        url = urlsplit(value)
        return (url.scheme == 'https' and bool(url.hostname) and url.username is None
                and url.password is None and not re.search(r'[\x00-\x20]', value)
                and (url.port is None or 1 <= url.port <= 65535))
    except ValueError:
        return False


@FORMATS.checks('utb-java-password-input')
def java_password(value):
    if not isinstance(value, str):
        return True
    try:
        return bool(value.strip()) and len(value.encode('utf-16-le')) // 2 <= 1000
    except UnicodeError:
        return False


@FORMATS.checks('utb-new-password')
def new_password(value):
    if not isinstance(value, str):
        return True
    try:
        return bool(value.strip()) and len(value) >= 12 and len(value.encode('utf-8')) <= 72
    except UnicodeError:
        return False


def load(path):
    return json.loads(Path(path).read_text(encoding='utf-8'), parse_float=Decimal,
                      parse_constant=lambda token: (_ for _ in ()).throw(ValueError('Non-finite JSON number')))


def validator(bundle, name):
    return ContractValidator({'$schema':bundle['$schema'], '$defs':bundle['$defs'],
                                 '$ref':'#/$defs/'+name}, format_checker=FORMATS)


def semantic_errors(name, value):
    """Pure fixture oracles from the contract; never an application authorization guard."""
    errors=[]
    if name == 'ErrorEnvelope' and value['error'] != value['errorCode']:
        errors.append('error must equal errorCode')
    if name == 'ProfileProjection':
        allowed=value['allowedFields']
        if set(value['data']) != set(allowed):
            errors.append('data fields must equal allowedFields')
        if set(allowed) & {'gpa','transcript'}:
            errors.append('PROFILE_FIELD_UNSUPPORTED')
    if name == 'PlacementLifecycleEvent' and value['aggregateVersion'] != value['placementVersion']:
        errors.append('aggregateVersion must equal placementVersion')
    if name == 'CaseClosedEvent' and value['aggregateVersion'] != value['caseVersion']:
        errors.append('aggregateVersion must equal caseVersion')
    if name == 'AuditEventInput':
        metadata=value['metadata']
        if 'fileId' in metadata and 'fileIds' in metadata:
            errors.append('audit must not emit fileId and fileIds together')
        decisions={'INVITATION_RESPONDED':{'ACCEPT','DECLINE','WITHDRAW'},
                   'PREFERENCE_REVIEWED':{'REVIEW'},'COOPERATION_REVIEWED':{'APPROVE','REJECT','REQUEST_INFO'}}
        if value['action'] in decisions and 'decision' in metadata and metadata['decision'] not in decisions[value['action']]:
            errors.append('audit decision does not match action')
    if name == 'ImportJobDto':
        j=value
        terminal=j['provisionedRows']+j['alreadyLinkedRows']+j['needsReviewRows']+j['permanentFailedRows']
        if j['validRows']+j['invalidRows']+j['previewNeedsReviewRows'] != j['totalRows']:
            errors.append('preview counters do not sum to totalRows')
        if j['queuedRows']+j['processingRows']+j['transientFailedRows']+terminal != j['selectedRows']:
            errors.append('selected counters do not partition selectedRows')
        if j['processedRows'] != terminal or j['successRows'] != j['provisionedRows']+j['alreadyLinkedRows']:
            errors.append('processed/success counters incorrect')
        if j['selectedRows'] > j['validRows']:
            errors.append('selectedRows exceeds validRows')
        if j['jobState'] in {'COMPLETED','COMPLETED_WITH_ERRORS'}:
            if j['selectedRows']<1:
                errors.append('completed job requires selected rows')
            if terminal != j['selectedRows'] or j['finishedAt'] is None:
                errors.append('terminal job has unfinished rows/time')
            with_errors=j['needsReviewRows']+j['permanentFailedRows'] > 0
            if with_errors != (j['jobState']=='COMPLETED_WITH_ERRORS'):
                errors.append('terminal job state contradicts counters')
        if j['jobState'] in {'VALIDATING','PREVIEW_READY','EXPIRED'} and (j['selectedRows']!=0 or j['startedAt'] is not None):
            errors.append('preview job must not have selected/provisioned rows')
    if name == 'ImportRowDto':
        r=value
        if not r['selected'] and r['rowState'] != 'NOT_SELECTED':
            errors.append('unselected row must remain NOT_SELECTED')
        if r['selected'] and r['validationState'] != 'VALID':
            errors.append('only preview VALID rows can be selected')
    return errors


def validate_value(bundle, name, value, semantic=True):
    errors=[f'{"/".join(map(str,e.path))}: {e.validator}'
            for e in validator(bundle,name).iter_errors(value)]
    if not errors and semantic:
        errors.extend(semantic_errors(name,value))
    return errors


def check_audit_enum(enum_values, audit):
    legacy = {a['action'] for a in audit if a['enumImplemented']}
    canonical = {a['action'] for a in audit}
    problems = []
    if len(legacy) != 30 or not legacy <= enum_values:
        problems.append('legacy audit actions missing from Java enum')
    if enum_values - canonical:
        problems.append('unregistered Java audit actions: ' + ', '.join(sorted(enum_values - canonical)))
    return problems


def check_registries(manifest):
    problems=[]
    perms=manifest['permissions'];audit=manifest['auditActions']
    sql=(ROOT/'src/main/resources/db/migration/base/V3__permissions_and_membership_integrity.sql').read_text(encoding='utf-8')
    seed=sql.split('INSERT INTO permissions',1)[1].split('INSERT INTO role_permissions',1)[0]
    core_codes=set(re.findall(r"\('([^']+)'",seed))
    contract_core={p['code'] for p in perms if p['catalogImplemented']}
    if core_codes != contract_core or len(core_codes) != 18:
        problems.append('core permission registry differs from V3')
    enum=(ROOT/'src/main/java/vn/utbridora/auth/audit/AuditAction.java').read_text(encoding='utf-8').split('public enum AuditAction {',1)[1].split('}',1)[0]
    enum=re.sub(r'/\*.*?\*/|//[^\n]*', '', enum, flags=re.S)
    enum_values=set(re.findall(r'\b[A-Z][A-Z_]+\b',enum))
    problems.extend(check_audit_enum(enum_values,audit))
    if len(perms)!=51 or len({p['code'] for p in perms})!=len(perms):
        problems.append('duplicate/missing permission')
    if len(audit)!=93 or len({a['action'] for a in audit})!=len(audit):
        problems.append('duplicate/missing audit action')
    if any('*' in p['code'] for p in perms) or any('*' in a['action'] for a in audit):
        problems.append('wildcard registry code')
    canonical={p['code'] for p in perms}
    for alias in manifest['permissionAliases']:
        if alias['canonical'] in canonical and not alias['restriction']:
            problems.append('alias missing action/scope restriction')
    aliases={a['alias']:a['canonical'] for a in manifest['permissionAliases'] if a['canonical'] in canonical}
    with (ROOT/'docs/contracts/permission_matrix.csv').open(encoding='utf-8',newline='') as stream:
        for row in csv.DictReader(stream):
            code=row['permission_code']
            if code not in {'NOT_GRANTED','NOT_IMPLEMENTED'} and code not in canonical and code not in aliases:
                problems.append('unmapped legacy CSV permission: '+code)
    for path,expected in manifest['sourceHashes'].items():
        if digest(ROOT/path)!=expected:
            problems.append('source hash changed: '+path)
    if digest(CONTRACT)!=manifest['currentContractHash']:
        problems.append('contract changed without rebuilding bundle')
    fixed={
        'docs/contracts/UTBridora_Contract_Pack_B01_B06_v1.0_Candidate.md':'9c62f229908f3c66f300455e33bdde6aef127f8e6edd99b286def0687ff9f2ef',
        'docs/srs/UTBridora_SRS_Tong_The_v1.2.4.docx':'92efaaec4efb66bfe0fa69e5de6a706476f93f58f808575982b9d95662438bd4',
    }
    for path,expected in fixed.items():
        if digest(ROOT/path)!=expected:problems.append('frozen baseline changed: '+path)
    return problems


def check_routes(manifest):
    actual=set()
    for path in (ROOT/'src/main/java').rglob('*Controller.java'):
        source=path.read_text(encoding='utf-8')
        prefix=re.search(r'@RequestMapping\("([^"]+)"\)',source)
        if not prefix:continue
        for method,suffix in re.findall(r'@(Get|Post|Put|Patch|Delete)Mapping\("([^"]*)"\)',source):
            actual.add((method.upper(),prefix[1]+suffix))
    declared={(x['method'],x['path']) for x in manifest['legacyCoreOperations']}
    # New business controllers are allowed; this check protects the existing published core routes.
    return [] if declared<=actual else ['missing published core route: '+str(sorted(declared-actual))]


def check_schema_refs(bundle):
    refs=[]
    def walk(value):
        if isinstance(value,dict):
            if '$ref' in value:refs.append(value['$ref'])
            for child in value.values():walk(child)
        elif isinstance(value,list):
            for child in value:walk(child)
    walk(bundle)
    return [r for r in refs if not r.startswith('#/$defs/') or r[8:] not in bundle['$defs']]


def check_ports(bundle,manifest):
    errors=[]
    ports=manifest['typedPorts']
    if len({p['operation'] for p in ports})!=len(ports):errors.append('duplicate typed port')
    for port in ports:
        for key in ('inputSchema','successValueSchema'):
            if port[key] not in bundle['$defs']:errors.append('unresolved port schema '+port['operation'])
        input_schema=bundle['$defs'][port['inputSchema']]
        if input_schema.get('additionalProperties') is not False:errors.append('open port input')
        if set(input_schema.get('properties',{})) & {'actor','mutation','context'}:errors.append('trusted context in domain input')
    return errors


def run(output=None):
    bundle=load(OUTPUT/'schemas.json');manifest=load(OUTPUT/'manifest.json');examples=load(OUTPUT/'examples.json')
    Draft202012Validator.check_schema(bundle)
    results=[]
    def record(id,errors,scope):
        results.append(dict(id=id,result='FAIL' if errors else 'PASS',scope=scope,errors=errors))
    record('SCHEMA-REFS',check_schema_refs(bundle),'all-local-references')
    record('REGISTRIES',check_registries(manifest),'static-source-and-registry')
    record('LEGACY-ROUTES',check_routes(manifest),'static-controller-openapi')
    record('TYPED-PORTS',check_ports(bundle,manifest),'typed-input-output-and-server-context-separation')
    for example in examples:
        record(example['id'],validate_value(bundle,example['schema'],example['value']),
               'schema-and-pure-fixture-oracle')
    regression=cases(bundle,examples,manifest)
    for case in regression:
        errors=validate_value(bundle,case['schema'],case['value'])
        matches=bool(not errors)==case['valid']
        record(case['id'],[] if matches else ['expected '+('accept' if case['valid'] else 'reject')],case['scope'])
    passed=sum(x['result']=='PASS' for x in results)
    report=dict(date='2026-11-05',checkedAtUtc=datetime.now(timezone.utc).isoformat(),
                scope='DEV: schema/static/pure fixture checks; not runtime of new modules or Tester acceptance',
                sourceCommit=subprocess.check_output(['git','-c','safe.directory='+str(ROOT).replace('\\','/'),'rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
                packVersion='1.1',candidateApproval='NOT_RECORDED',baseApproval=manifest['baseApproval'],
                counts=dict(schemas=len(bundle['$defs']),examples=len(examples),regressionCases=len(regression),checks=len(results),passed=passed,failed=len(results)-passed),
                results=results,sourceHashes=manifest['sourceHashes'],contractHash=manifest['currentContractHash'],
                toolHashes={str(p.relative_to(ROOT)).replace('\\','/'):digest(p) for p in sorted((ROOT/'scripts/contracts').glob('*')) if p.is_file()},
                bundleHashes={str(p.relative_to(ROOT)).replace('\\','/'):digest(p) for p in sorted(OUTPUT.glob('*')) if p.is_file()},
                permissionCount=len(manifest['permissions']),auditActionCount=len(manifest['auditActions']),
                typedPortCount=len(manifest['typedPorts']),coreOperationCount=len(manifest['legacyCoreOperations']),
                runtimeNewModules='NOT_RUN',tester='NOT_RUN_BY_CODEX')
    if output:
        Path(output).parent.mkdir(parents=True,exist_ok=True)
        Path(output).write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    for result in results:
        if result['result']=='FAIL':print(result['id'],result['errors'])
    print(f'{passed}/{len(results)} contract DEV checks PASS; {len(results)-passed} FAIL')
    return report


if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    parser=argparse.ArgumentParser()
    parser.add_argument('--output',type=Path)
    args=parser.parse_args()
    report=run(args.output)
    sys.exit(0 if report['counts']['failed']==0 else 1)
