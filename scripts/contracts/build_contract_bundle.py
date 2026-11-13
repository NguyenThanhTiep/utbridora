"""Compile the documented contract notation; reject unresolved types, never infer open maps.

This is a documentation build, not a generator of runtime controllers or migrations.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import html
import json
import re
import io
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONTRACT = ROOT / 'docs/contracts/UTBridora_Contract_Pack_B01_B06_v1.1_Candidate.md'
OUTPUT = ROOT / 'docs/contracts/v1.1'
SCHEMA_ID = 'urn:utbridora:contracts:1.1'


def digest(path):
    path=Path(path)
    data=path.read_bytes()
    if path.suffix!='.docx' and path.name!='UTBridora_Contract_Pack_B01_B06_v1.0_Candidate.md':
        data=data.replace(b'\r\n',b'\n')
    return hashlib.sha256(data).hexdigest()


def split_top(text, delimiter=','):
    result, start, stack = [], 0, []
    pairs = {')': '(', ']': '[', '}': '{', '>': '<'}
    for i, char in enumerate(text):
        if char in '([{':
            stack.append(char)
        elif char == '<' and re.search(r'(?:Page|Result|Success)$', text[:i]):
            stack.append(char)
        elif char in pairs and stack and stack[-1] == pairs[char]:
            stack.pop()
        elif char == delimiter and not stack:
            result.append(text[start:i].strip())
            start = i + 1
    result.append(text[start:].strip())
    return result


def table_rows(text, heading, end_heading):
    section = text.split(heading, 1)[1].split(end_heading, 1)[0]
    rows = []
    for line in section.splitlines():
        if line.startswith('|'):
            cells = [x.strip().replace('\\|', '|') for x in re.split(r'(?<!\\)\|', line)[1:-1]]
            if cells and not all(re.fullmatch(r'[-: ]+', c) for c in cells):
                rows.append(cells)
    return rows[1:]


def read_registries(text):
    permissions = []
    for section, end, implemented in [
        ('### D.1.', '### D.2.', True), ('### D.2.', '### D.3.', False)
    ]:
        for row in table_rows(text, section, end):
            code, legacy, action, role, scope, grantor, owner = row
            permissions.append(dict(code=code, legacy=legacy, action=action, roles=role,
                                    scope=scope, grantor=grantor, owner=owner,
                                    catalogImplemented=implemented,
                                    businessGuardImplemented=False if owner != 'T1' else None))
    aliases = [dict(alias=r[0], canonical=r[1], restriction=r[2])
               for r in table_rows(text, '### D.3.', '### D.4.')]
    audit = []
    for section, end, implemented in [
        ('### E.2.', '### E.3.', True), ('### E.3.', '### E.4.', False)
    ]:
        for code, legacy, result, resource, metadata, producer in table_rows(text, section, end):
            audit.append(dict(action=code, legacy=legacy, results=result.split('/'),
                              resourceType=resource, metadata=[] if metadata == '—' else metadata.split(','),
                              producer=producer, enumImplemented=implemented))
    audit_aliases = [dict(label=r[0], mapping=r[1], condition=r[2])
                     for r in table_rows(text, '### E.4.', '## F.')]
    return permissions, aliases, audit, audit_aliases


class Compiler:
    def __init__(self, text, permissions, audits):
        self.raw = {}
        self.locations = {}
        self.defs = {}
        self.permissions = permissions
        self.audits = audits
        for fence in re.finditer(r'```text\s*\n(.*?)\n```', text, re.S):
            offset = text.count('\n', 0, fence.start()) + 2
            for n, line in enumerate(fence[1].splitlines()):
                line = html.unescape(line.split(' //', 1)[0]).strip()
                m = re.fullmatch(r'([A-Z]\w*)\s*(?:=\s*(.+)|(\{.*\}))', line)
                if m:
                    name, assigned, object_expression = m.groups()
                    expression = assigned or object_expression
                    if '{' not in expression and '|' not in expression:
                        continue
                    if name in self.raw and self.raw[name] != expression:
                        raise ValueError(f'Conflicting definition {name}')
                    self.raw[name] = expression
                    self.locations[name] = offset + n
        self.primitives()
        self.audit_types()
        self.port_models = []
        self.port_types(text)

    def port_types(self, text):
        for fence in re.finditer(r'```text\s*\n(.*?)\n```',text,re.S):
            for n,line in enumerate(fence[1].splitlines()):
                match=re.fullmatch(r'([A-Z]\w*\.\w+)\((.*)\):(.+)',line.strip())
                if not match:continue
                operation,params,returns=match.groups()
                domain=[];context=[]
                for param in split_top(params):
                    if ':' in param:
                        name,kind=param.split(':',1)
                    elif param in {'actor','mutation'}:
                        name=param;kind='ActorContext' if param=='actor' else 'MutationContext'
                    else:
                        name='input';kind=param
                    if name in {'actor','mutation','context'}:
                        context.append(dict(name=name,type=kind,source='SERVER_ONLY'))
                    else:domain.append(name+':'+kind)
                input_name=operation.replace('.','_')+'Input'
                output_name=operation.replace('.','_')+'Value'
                self.raw[input_name]='{'+','.join(domain)+'}'
                wrapped=re.fullmatch(r'Result<(.+)>',returns)
                self.raw[output_name]=wrapped[1] if wrapped else returns
                loc=text.count('\n',0,fence.start())+2+n
                self.locations[input_name]=self.locations[output_name]=loc
                self.port_models.append(dict(operation=operation,inputSchema=input_name,successValueSchema=output_name,
                                             domainErrorSchema='DomainError' if wrapped else None,
                                             contextParameters=context,line=loc,status='TARGET_NOT_IMPLEMENTED'))

    def audit_types(self):
        self.defs['AuditAction']={'type':'string','enum':[a['action'] for a in self.audits]}
        self.defs['AuditResourceType']={'type':'string','pattern':'^[a-z][a-z0-9_]{0,79}$'}
        keys = {key for action in self.audits for key in action['metadata']}
        keys.update({'initiatedBy','operationId'})
        codes = {'ruleVersion','taxonomyVersion','scoringVersion','academicCatalogVersion','completenessVersion'}
        bools = {'enabled','lateFlag','rangeRequest','fromApproved','toApproved','terminal'}
        hashes = {'criteriaHash','fileHash','releaseFingerprint','profileSnapshotVersion'}
        counts = {'versionNo','reviewedVersionNo','reviewedVersion','revisionNo','submissionNo'}
        props = {}
        for key in sorted(keys):
            if key in codes:
                expression='Code'
            elif key.endswith('Id') or key=='initiatedBy':
                expression='U'
            elif key=='fileIds':
                expression='U[0..20]'
            elif key in bools:
                expression='boolean'
            elif key in hashes:
                expression='H'
            elif key in counts:
                expression='int>=1'
            elif key=='attemptNo':
                expression='int[1..4]'
            elif key=='weekStart':
                expression='D'
            elif key.endswith('At') or key in {'validFrom','validUntil','reservationUntil'}:
                expression='I'
            elif key.endswith('Version') or key in {'version','securityVersion'}:
                expression='V'
            elif key.endswith('Rows') or key.endswith('Count') or key in {'rowNo','page','size','sizeBytes'}:
                expression='long'
            elif key=='reasonCode':
                expression='Reason'
            elif key=='role':
                expression='Role'
            elif key=='fieldCodes':
                expression='ProfileField[]'
            elif key in {'changedFieldCodes','missingFieldCodes'}:
                expression='Text(1..64)[]'
            elif key=='permissionCode':
                expression='PermissionCode'
            elif key=='eligibleBand':
                expression='SUPPRESSED|5-9|10-19|20+'
            elif key=='contentType':
                expression='FileContentType'
            elif key=='resourceType':
                expression='AuditResourceType'
            else:
                expression='Code'
            props[key]=self.type(expression)
        self.defs['AuditAction']={'type':'string','enum':[a['action'] for a in self.audits]}
        self.defs['AuditResourceType']={'type':'string','pattern':'^[a-z][a-z0-9_]{0,79}$'}
        self.defs['AuditMetadata']={'type':'object','properties':props,'additionalProperties':False}

    def primitives(self):
        string = lambda lo, hi: dict(type='string', minLength=lo, maxLength=hi)
        self.defs.update({
            'U': dict(type='string', format='uuid'),
            'V': dict(type='integer', minimum=0, maximum=9007199254740991),
            'I': dict(type='string', format='utb-utc-instant'),
            'D': dict(type='string', format='date'),
            'H': dict(type='string', pattern='^[0-9a-f]{64}$'),
            'Code': string(1, 64),
            'Email': {**string(1, 254), 'format': 'email'},
            'Reason': dict(type='string', pattern='^[A-Z0-9_]{3,64}$'),
            'Ref': string(1, 120),
            'HTTPSUrl': dict(type='string', format='utb-https-url'),
            'PasswordInput': {**string(1, 1000), 'format': 'utb-java-password-input'},
            'NewPassword': {**string(12, 1000), 'format': 'utb-new-password'},
            'TokenInput': {**string(1, 128), 'pattern': r'\S'},
            'PermissionCode': dict(type='string', enum=[p['code'] for p in self.permissions]),
        })

    def object(self, expression):
        properties, required = {}, []
        for field in split_top(expression[1:-1]):
            if not field:
                continue
            key, value = field.split(':', 1)
            key = key.strip()
            optional = key.endswith('?')
            key = key.rstrip('?')
            if key in properties:
                raise ValueError('Duplicate field '+key)
            properties[key] = self.type(value.strip())
            if not optional:
                required.append(key)
        return dict(type='object', properties=properties, required=required,
                    additionalProperties=False)

    def type(self, value):
        value = value.strip()
        branches = split_top(value, '|')
        if len(branches) > 1:
            return {'oneOf' if all(b.startswith('{') for b in branches) else 'anyOf':
                    [self.type(b) for b in branches]}
        if value.startswith('{') and value.endswith('}'):
            return self.object(value)
        if value in ('null', 'void', 'boolean', 'string'):
            return dict(type={'null': 'null', 'void':'null', 'boolean': 'boolean', 'string': 'string'}[value])
        if value in ('true', 'false'):
            return dict(const=value == 'true')
        if re.fullmatch(r'\d+', value):
            return dict(const=int(value))
        text = re.fullmatch(r'Text\((\d+)\.\.(\d+)\)', value)
        if text:
            return dict(type='string', minLength=int(text[1]), maxLength=int(text[2]))
        number = re.fullmatch(r'(int|long|decimal)(?:\((\d+),(\d+)\))?(.*)', value)
        if number:
            kind, precision, scale, tail = number.groups()
            result = dict(type='number' if kind == 'decimal' else 'integer', minimum=0)
            if scale:
                result['multipleOf'] = 10 ** -int(scale)
            bound = re.fullmatch(r'\[(\d+)\.\.(\d+)\]', tail)
            if bound:
                result.update(minimum=int(bound[1]), maximum=int(bound[2]))
            elif tail:
                compare = re.fullmatch(r'(>=|>)(\d+)', tail)
                if not compare:
                    raise ValueError('Unsupported numeric bound '+value)
                result.pop('minimum', None)
                result['minimum' if compare[1] == '>=' else 'exclusiveMinimum'] = int(compare[2])
            return result
        array = re.fullmatch(r'(.+)\[(\d*|\d+\.\.\d+)\]', value)
        if array:
            item, bounds = array.groups()
            result = dict(type='array', items=self.type(item))
            if '..' in bounds:
                lo, hi = bounds.split('..')
                result.update(minItems=int(lo), maxItems=int(hi))
            elif bounds:
                result.update(minItems=int(bounds), maxItems=int(bounds))
            return result
        page = re.fullmatch(r'Page<(.+)>', value)
        if page:
            name = 'PageOf'+page[1]
            if name not in self.defs:
                self.defs[name] = self.object('{content:'+page[1]+'[],page:int>=0,size:int[1..100],totalElements:long,totalPages:int>=0}')
            return {'$ref': '#/$defs/'+name}
        if value in self.raw or value in self.defs:
            return {'$ref': '#/$defs/'+value}
        profile_fields = self.raw.get('ProfileField', '').split('|')
        if value in profile_fields or re.fullmatch(r'[A-Z][A-Z0-9_]*|demo-v1|5-9|10-19|20\+|(?:application|image|text)/[A-Za-z0-9.+-]+|PlacementApproved|PlacementActivated|PlacementCancelled|PlacementCompleted|CaseClosed|StudentAccountActivated', value):
            return dict(const=value)
        raise ValueError('Unresolved type '+value)

    def compile(self):
        for name, expression in self.raw.items():
            self.defs[name] = {**self.type(expression), 'x-source-line': self.locations[name]}
        # All UUID/artifact masks below are explicitly sets in the source contract.
        distinct = {'groupIds', 'caseIds', 'fileIds', 'evidenceFileIds', 'placementIds',
                    'selectedRowIds', 'requestedFields', 'fieldWhitelist', 'permissionCodes',
                    'profileReadFields', 'allowedFields'}
        for definition in self.defs.values():
            for name, prop in definition.get('properties', {}).items():
                if name in distinct and prop.get('type') == 'array':
                    prop['uniqueItems'] = True
        for name in ('SubmissionInput', 'SubmissionDto'):
            self.defs[name]['oneOf'] = [
                {'properties': {'url': {'type': 'null'}, 'fileIds': {'minItems': 1}}},
                {'properties': {'url': {'type': 'string'}, 'fileIds': {'maxItems': 0}}},
            ]
        for name in ('ProfileInput', 'SelfProfileData'):
            self.defs[name]['allOf'] = [
                {'if': {'properties': {'skillsDeclared': {'const': False}}},
                 'then': {'properties': {'skills': {'maxItems': 0}, 'customSkills': {'maxItems': 0}}}},
                {'if': {'properties': {'availabilityDeclared': {'const': False}}},
                 'then': {'properties': {'availablePeriods': {'maxItems': 0}}}},
            ]
        for name in ('ImportJobDto', 'ImportAccepted'):
            for key, prop in self.defs[name]['properties'].items():
                if key.endswith('Rows'):
                    prop['maximum'] = 5000
        self.defs['AuditEventInput']['allOf']=[
            {'if': {'properties': {'action': {'const': a['action']}}},
             'then': {'properties': {
                 'result': {'enum':a['results']},'resourceType':{'const':a['resourceType']},
                 'metadata':{'properties':{k:{} for k in a['metadata']+['initiatedBy','operationId']},
                             'additionalProperties':False}}}}
            for a in self.audits]
        self.legacy()
        return {'$schema': 'https://json-schema.org/draft/2020-12/schema',
                '$id': SCHEMA_ID, 'title': 'UT.Bridora Contract Pack 1.1 Candidate',
                '$defs': self.defs,
                '$comment': 'Target schemas, not evidence that runtime operations exist. All $refs are local.'}

    def legacy(self):
        # Exact declared body fields; no new unknown-property policy is imposed on legacy Jackson.
        for name, expression in {
            'LegacyLogin': '{email:Email,password:PasswordInput}',
            'LegacyAcceptInvitation': '{token:TokenInput,displayName:Text(1..150),password:PasswordInput}',
            'LegacyResetPassword': '{token:TokenInput,newPassword:NewPassword}',
            'LegacyChangePassword': '{currentPassword:PasswordInput,newPassword:NewPassword}',
            'LegacyError': '{error:string,message:string}',
        }.items():
            self.defs[name] = self.object(expression)
            self.defs[name]['additionalProperties'] = True
            self.defs[name]['x-scope'] = 'Documented fields; runtime validation/unknown properties checked separately.'


EXAMPLE_SCHEMAS = {
    2:'MeDto',3:'ActivationRequestV11',4:'MessageDto',6:'GrantHrAssignment',7:'ContextAssignment',
    9:'FileDto',11:'MentorInviteRequest',12:'MentorInviteDto',13:'AcceptMentorMembership',14:'MentorInviteDto',
    17:'CreateCohort',18:'CohortDto',20:'SubmissionInput',21:'SubmissionDto',23:'ReportInput',24:'ReportDto',
    26:'EvaluationInput',27:'EvaluationDto',29:'EnterpriseQualityInput',30:'EnterpriseQualityDto',
    32:'CaptureProfilesRequest',33:'SnapshotSet',35:'ResolveSkillsRequest',36:'ResolveSkillsResponse',
    38:'AssertEligibilityRequest',39:'StudentTermEligibility',41:'SubmitPreference',42:'PreferenceView',
    44:'InvitationDecision',45:'InternshipInvitation',47:'GrantConsent',48:'ConsentGrant',50:'NominateCandidates',
    51:'NominationResult',53:'EvaluateMatchRequest',54:'EvaluateMatchResponse',56:'PlacementLifecycleEvent',
    57:'EventConsumeResult',58:'EventConsumeResult',59:'SchoolVerificationView',61:'SubmitCooperationRequest',
    62:'CooperationDto',64:'ImportPreviewDto',65:'ConfirmImport',66:'ImportAccepted',69:'ResendAccepted',
}


def examples(text):
    result = []
    for index, match in enumerate(re.finditer(r'```json\s*\n(.*?)\n```', text, re.S), 1):
        data = json.loads(match[1])
        sections = re.findall(r'^###? (.+)', text[:match.start()], re.M)
        schema = 'ErrorEnvelope' if isinstance(data, dict) and 'errorCode' in data else EXAMPLE_SCHEMAS[index]
        result.append(dict(id=f'EX-{index:02}', schema=schema, section=sections[-1],
                           line=text.count('\n', 0, match.start())+1, value=data))
    return result


def inventory_operations(text):
    operations = {}
    ports = []
    for number, line in enumerate(text.splitlines(), 1):
        clean = html.unescape(line)
        section = re.findall(r'^###? (.+)', text[:text.find(line)], re.M)
        for method, path in re.findall(r'\b(GET|POST|PUT|PATCH|DELETE)\s+(/api/v1/[A-Za-z0-9_/{},:.-]+)', clean):
            # Concrete example UUIDs are fixtures, not a second route definition.
            if re.search(r'/[0-9a-f]{8}-[0-9a-f-]{27,}', path):
                continue
            path = path.rstrip('.,;')
            key = method+' '+re.sub(r'\{[^}]+\}', '{}', path)
            operations.setdefault(key, dict(method=method, path=path, line=number,
                                            contractSection=section[-1] if section else '',
                                            status='TARGET_NOT_IMPLEMENTED', evidenceLine=clean.strip()))
        if re.match(r'^[A-Z]\w*Port\.\w+\(', clean):
            ports.append(dict(signature=clean, line=number, status='TARGET_NOT_IMPLEMENTED'))
    core = json.loads((ROOT/'docs/openapi.json').read_text(encoding='utf-8'))
    legacy=[]
    for path, item in core['paths'].items():
        for method in item:
            if method in {'get','post','put','patch','delete'}:
                legacy.append(dict(method=method.upper(),path=path))
                key=method.upper()+' '+re.sub(r'\{[^}]+\}', '{}',path)
                if key in operations:
                    operations[key]['status']='CORE_IMPLEMENTED'
                else:
                    operations[key]=dict(method=method.upper(),path=path,line=None,
                                         contractSection='CP-AUTH1/core OpenAPI',status='CORE_IMPLEMENTED',
                                         evidenceLine='docs/openapi.json: '+method.upper()+' '+path)
    return list(operations.values()),ports,legacy


def build(check=False):
    text = CONTRACT.read_text(encoding='utf-8')
    permissions, aliases, audit, audit_aliases = read_registries(text)
    compiler = Compiler(text, permissions, audit)
    schema = compiler.compile()
    ops,ports,legacy=inventory_operations(text)
    old=ROOT/'docs/contracts/UTBridora_Contract_Pack_B01_B06_v1.0_Candidate.md'
    frozen=[old,ROOT/'docs/srs/UTBridora_SRS_Tong_The_v1.2.4.docx']
    # Path ordering on Windows is case-insensitive; use the same order as Linux CI.
    frozen += sorted((ROOT/'src/main').rglob('*.java'), key=lambda p: p.relative_to(ROOT).as_posix())
    frozen += sorted((ROOT/'src/main/resources/db').rglob('*.sql'), key=lambda p: p.relative_to(ROOT).as_posix())
    frozen += [ROOT/'pom.xml',ROOT/'docs/openapi.json',ROOT/'docs/contracts/permission_matrix.csv']
    frozen += [ROOT/'src/main/resources/application.yml']
    errors = sorted(set(re.findall(r'\b(?:400|401|403|404|409|413|416|422|429|500|503)\s*([A-Z][A-Z0-9_]{2,})', text)))
    manifest=dict(packVersion='1.1',baseVersion='1.0',baseApproval='USER_APPROVED_2026-11-05',
                  candidateApproval='USER_APPROVED_2026-11-13',permissions=permissions,permissionAliases=aliases,
                  auditActions=audit,auditAliases=audit_aliases,operations=ops,ports=ports,
                  typedPorts=compiler.port_models,
                  legacyCoreOperations=legacy,errorCodesInHttpExamples=errors,
                  sourceHashEncoding='raw for frozen v1.0/DOCX; UTF-8 LF for other text',
                  sourceHashes={str(p.relative_to(ROOT)).replace('\\','/'):digest(p) for p in frozen},
                  currentContractHash=digest(CONTRACT))
    if not check:OUTPUT.mkdir(parents=True, exist_ok=True)
    rendered_files={name:json.dumps(data,ensure_ascii=False,indent=2)+'\n'
                    for name,data in [('schemas.json',schema),('manifest.json',manifest),('examples.json',examples(text))]}
    for filename,rows in [
        ('permission_matrix.csv',[{k:p[k] for k in ('code','legacy','action','roles','scope','grantor','owner','catalogImplemented')} for p in permissions]),
        ('audit_registry.csv',[{k:a[k] if not isinstance(a[k],list) else ','.join(a[k]) for k in ('action','legacy','results','resourceType','metadata','producer','enumImplemented')} for a in audit]),
    ]:
        stream=io.StringIO(newline='');writer=csv.DictWriter(stream,fieldnames=list(rows[0]),lineterminator='\n')
        writer.writeheader();writer.writerows(rows);rendered_files[filename]=stream.getvalue()
    for filename,rendered in rendered_files.items():
        if check:
            if not (OUTPUT/filename).exists() or (OUTPUT/filename).read_text(encoding='utf-8')!=rendered:
                raise SystemExit('Bundle drift: '+filename+'; run build_contract_bundle.py and review the diff.')
        else:
            (OUTPUT/filename).write_text(rendered,encoding='utf-8',newline='\n')
    print(f'Compiled {len(compiler.raw)} model definitions including port wrappers, {len(schema["$defs"])} schemas, '
          f'{len(examples(text))} examples, {len(permissions)} permissions, {len(audit)} audit actions.')


if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--check',action='store_true')
    build(parser.parse_args().check)
