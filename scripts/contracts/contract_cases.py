"""Counterexamples and boundaries for the documented contract, separate from runtime QA."""
from copy import deepcopy
from decimal import Decimal

U='00000000-0000-4000-8000-000000000001'


def cases(bundle, examples, manifest):
    tests=[]
    def add(id,schema,value,valid=True,scope='schema-boundary'):
        tests.append(dict(id=id,schema=schema,value=value,valid=valid,scope=scope))
    for ex in examples:
        schema=ex['schema'];value=ex['value']
        if isinstance(value,dict):
            unknown=deepcopy(value);unknown['uncontractedSecret']='synthetic'
            add(ex['id']+'-UNKNOWN-FIELD',schema,unknown,False,'closed-object')
            # Every top-level field in the 69 published examples is required except optional displayName.
            for field in value:
                mutated=deepcopy(value);del mutated[field]
                add(ex['id']+'-MISSING-'+field,schema,mutated,False,'required-field')
    for password,valid,label in [
        ('x'*11,False,'ASCII11'),('x'*12,True,'ASCII12'),('x'*72,True,'ASCII72'),
        ('x'*73,False,'ASCII73'),('\u1ed9'*24,True,'UTF8-72'),('\u1ed9'*25,False,'UTF8-75'),
        ('\U0001f642'*18,True,'ASTRAL-72'),('\U0001f642'*19,False,'ASTRAL-76'),
        (' '*12,False,'BLANK'),(' x'*6,True,'KEEP-WHITESPACE'),
    ]:
        add('PASSWORD-'+label,'ActivationRequestV11',{'token':'synthetic-token','newPassword':password},valid,'password-codepoint-byte-boundary')
    legacy={'token':'synthetic-token','displayName':'Demo','password':'x'*12}
    modern={'token':'synthetic-token','newPassword':'x'*12}
    add('ACTIVATION-LEGACY','LegacyAcceptInvitation',legacy)
    add('ACTIVATION-NEW','ActivationRequestV11',modern)
    add('ACTIVATION-CROSS-LEGACY-IN-NEW','ActivationRequestV11',legacy,False)
    add('ACTIVATION-CROSS-NEW-IN-LEGACY','LegacyAcceptInvitation',modern,False)
    add('ACTIVATION-MIXED-BODY','ActivationRequestV11',{**modern,'password':'x'*12},False)
    for bad in ['not-a-uuid','1',42,None]:
        add('CONFIRM-BAD-ID-'+str(bad),'ConfirmImport',{'expectedJobVersion':1,'fileHash':'a'*64,'selectedRowIds':[bad]},False)
    for version,valid in [(9007199254740991,True),(9007199254740992,False),(-1,False),(1.5,False),(True,False)]:
        add('VERSION-'+str(version),'ResumeImport',{'expectedJobVersion':version},valid)
    add('VERSION-JSON-INTEGER-DECIMAL','ResumeImport',{'expectedJobVersion':Decimal('1.0')},True)
    for email,valid in [('demo@example.test',True),('a@',False),('@example.test',False),('a@@example.test',False),('not-an-email',False)]:
        add('EMAIL-FORMAT-'+email,'Email',email,valid,'email-syntax-no-delivery')
    score={'criterionCode':'QUALITY','score':Decimal('0.07'),'comment':None,'evidenceFileIds':[]}
    add('DECIMAL-EXACT-SCALE','Score',score)
    add('DECIMAL-TOO-MANY-PLACES','Score',{**score,'score':Decimal('0.071')},False)
    for count,valid in [(0,False),(1,True),(5000,True),(5001,False)]:
        ids=[f'00000000-0000-4000-8000-{n:012d}' for n in range(count)]
        add('CONFIRM-ROWS-'+str(count),'ConfirmImport',{'expectedJobVersion':1,'fileHash':'a'*64,'selectedRowIds':ids},valid)
    add('CONFIRM-DUPLICATE-ROWS','ConfirmImport',{'expectedJobVersion':1,'fileHash':'a'*64,'selectedRowIds':[U,U]},False)
    artifact={'expectedAssignmentVersion':1,'fileIds':[U],'url':None,'note':None}
    add('SUBMISSION-FILE','SubmissionInput',artifact)
    add('SUBMISSION-URL','SubmissionInput',{**artifact,'fileIds':[],'url':'https://example.test/submission'})
    add('SUBMISSION-BOTH','SubmissionInput',{**artifact,'url':'https://example.test/submission'},False)
    add('SUBMISSION-EMPTY','SubmissionInput',{**artifact,'fileIds':[]},False)
    add('SUBMISSION-DUPLICATE','SubmissionInput',{**artifact,'fileIds':[U,U]},False)
    for url in ['http://example.test/a','https://name:password@example.test/a','file:///tmp/a','https://example.test/a\n']:
        add('SUBMISSION-UNSAFE-URL-'+str(len(tests)),'SubmissionInput',{**artifact,'fileIds':[],'url':url},False)
    profile=dict(expectedProfileVersion=0,termId=U,majorCode=None,skillsDeclared=True,
                 skills=[dict(skillCode='JAVA',level=1,yearsPractice=0,note=None)],
                 customSkills=[dict(customSkillId=None,name='Demo custom',categoryCode='DEV',level=2,yearsPractice=1,note='Demo note')],
                 availabilityDeclared=True,availablePeriods=[dict(startDate='2026-11-01',endDate='2026-12-31')],
                 orientationCodes=[],subjectCodes=[],projects=[],certificates=[],cvFileId=None,phone=None)
    add('PROFILE-CANONICAL-CUSTOM-NOTES','ProfileInput',profile)
    bad=deepcopy(profile);bad['skills'][0]['source']='SCHOOL_VERIFIED'
    add('PROFILE-FORGED-SOURCE','ProfileInput',bad,False)
    add('PROFILE-DECLARED-FALSE-WITH-DATA','ProfileInput',{**profile,'skillsDeclared':False},False)
    add('PROFILE-NULL-ARRAY','ProfileInput',{**profile,'customSkills':None},False)
    for level,valid in [(0,False),(1,True),(5,True),(6,False)]:
        bad=deepcopy(profile);bad['skills'][0]['level']=level
        add('PROFILE-LEVEL-'+str(level),'ProfileInput',bad,valid)
    projection=dict(studentUserId=U,profileVersion=1,allowedFields=['majorCode'],data={'majorCode':'SE'})
    add('MASK-VALID','ProfileProjection',projection,scope='pure-field-mask-oracle')
    add('MASK-LEAK','ProfileProjection',{**projection,'data':{'majorCode':'SE','email':'demo@example.test'}},False,'pure-field-mask-oracle')
    add('MASK-UNSUPPORTED','ProfileProjection',{**projection,'allowedFields':['gpa'],'data':{}},False,'pure-field-mask-oracle')
    add('MASK-DUPLICATE','ProfileProjection',{**projection,'allowedFields':['majorCode','majorCode']},False)
    snapshot=dict(profileSnapshotId=U,studentUserId=U,schoolOrgId=U,termId=U,majorCode='SE',
                  skillsDeclared=True,skills=[dict(skillCode='JAVA',level=1,yearsPractice=0,source='SELF_DECLARED')],
                  availabilityDeclared=True,availablePeriods=[dict(startDate='2026-11-01',endDate='2026-12-31')],
                  profileVersion=1,relevantHash='a'*64,completenessVersion='demo-v1',academicCatalogVersion='academic-v1',taxonomyVersion='skills-v1',capturedAt='2026-11-05T00:00:00Z')
    add('SNAPSHOT-VALID','ProfileSnapshot',snapshot)
    add('SNAPSHOT-CUSTOM-NOT-IN-MATCH','ProfileSnapshot',{**snapshot,'customSkills':[]},False)
    bad=deepcopy(snapshot);bad['skills'][0]['note']='private note'
    add('SNAPSHOT-NOTE-NOT-IN-MATCH','ProfileSnapshot',bad,False)
    for band in ['SUPPRESSED','5-9','10-19','20+']:
        aggregate={'releaseId':U,'matchRunId':U,'eligibleBand':band}
        add('PRIVACY-BAND-'+band,'ProtectedAggregate',aggregate)
        for forbidden in ['rawCount','total','percentage','students','matched']:
            add('PRIVACY-FORBIDDEN-'+band+'-'+forbidden,'ProtectedAggregate',{**aggregate,forbidden:1},False)
    add('PRIVACY-EXACT-COUNT-AS-BAND','ProtectedAggregate',{'releaseId':U,'matchRunId':U,'eligibleBand':'4'},False)
    for action in manifest['auditActions']:
        event=dict(action=action['action'],resourceType=action['resourceType'],resourceId=U,organizationId=None,
                   result=action['results'][0],reasonCode=None,metadata={},eventVersion=1)
        add('AUDIT-VALID-'+action['action'],'AuditEventInput',event,scope='registry-schema')
        add('AUDIT-SECRET-'+action['action'],'AuditEventInput',{**event,'metadata':{'token':'synthetic'}},False,'registry-schema')
    login=dict(action='LOGIN',resourceType='user',resourceId=None,organizationId=None,result='SUCCESS',reasonCode=None,metadata={},eventVersion=1)
    add('AUDIT-UNKNOWN-ACTION','AuditEventInput',{**login,'action':'LOGIN_SUCCESS'},False)
    add('AUDIT-EVENT-AS-ACTION','AuditEventInput',{**login,'action':'PlacementActivated'},False)
    add('AUDIT-INVALID-RESULT','AuditEventInput',{**login,'result':'PARTIAL'},False)
    add('AUDIT-FOREIGN-METADATA','AuditEventInput',{**login,'metadata':{'jobVersion':1}},False)
    submission={**login,'action':'SUBMISSION_CREATED','resourceType':'submission','metadata':{'fileIds':[U],'versionNo':1}}
    add('AUDIT-MULTIFILE-METADATA','AuditEventInput',submission)
    add('AUDIT-MIXED-FILE-METADATA','AuditEventInput',{**submission,'metadata':{'fileId':U,'fileIds':[U]}},False,'pure-audit-oracle')
    add('AUDIT-VERSION-NO-ZERO','AuditEventInput',{**submission,'metadata':{'versionNo':0}},False)
    invitation={**login,'action':'INVITATION_RESPONDED','resourceType':'internship_invitation'}
    add('AUDIT-WITHDRAW-DECISION','AuditEventInput',{**invitation,'metadata':{'decision':'WITHDRAW'}})
    add('AUDIT-WRONG-DECISION','AuditEventInput',{**invitation,'metadata':{'decision':'REVIEW'}},False,'pure-audit-oracle')
    match={**login,'action':'MATCH_AGGREGATE_VIEWED','resourceType':'protected_release'}
    add('AUDIT-CODE-VERSION','AuditEventInput',{**match,'metadata':{'ruleVersion':'demo-v1'}})
    add('AUDIT-CODE-VERSION-NUMBER','AuditEventInput',{**match,'metadata':{'ruleVersion':1}},False)
    add('AUDIT-PII-METADATA','AuditEventInput',{**login,'metadata':{'email':'demo@example.test'}},False)
    error=deepcopy(examples[0]['value']);error['error']='SOMETHING_ELSE'
    add('ERROR-LEGACY-ALIAS-MUST-MATCH','ErrorEnvelope',error,False,'pure-error-oracle')
    for permission in ['*','audit','account:admin:manage','student:profile:self:read_write','NOT_GRANTED','audit:global:read:extra']:
        add('PERMISSION-REJECT-'+permission,'PermissionCode',permission,False,'registry-schema')
    for permission in ['account:self:read','schoolRosterImport:manage','t3:mentor:invite']:
        add('PERMISSION-EXACT-'+permission,'PermissionCode',permission,True,'registry-schema')
    job=deepcopy(examples[63]['value']['job'])
    add('COUNTERS-PREVIEW','ImportJobDto',job,scope='pure-counter-oracle')
    add('COUNTERS-BAD-TOTAL','ImportJobDto',{**job,'totalRows':job['totalRows']+1},False,'pure-counter-oracle')
    done={**job,'jobState':'COMPLETED','selectedRows':1,'queuedRows':0,'processingRows':0,'transientFailedRows':0,
          'provisionedRows':1,'alreadyLinkedRows':0,'needsReviewRows':0,'permanentFailedRows':0,'processedRows':1,'successRows':1,
          'startedAt':'2026-11-05T00:00:00Z','finishedAt':'2026-11-05T00:01:00Z'}
    add('COUNTERS-JOB-DONE-INDEPENDENT-OF-SMTP','ImportJobDto',done,scope='pure-counter-oracle')
    add('COUNTERS-FALSE-DONE','ImportJobDto',{**done,'queuedRows':1},False,'pure-counter-oracle')
    add('COUNTERS-FALSE-SUCCESS','ImportJobDto',{**done,'successRows':2},False,'pure-counter-oracle')
    add('COUNTERS-EMPTY-COMPLETED','ImportJobDto',{**done,'selectedRows':0,'provisionedRows':0,'processedRows':0,'successRows':0},False,'pure-counter-oracle')
    add('COUNTERS-PREVIEW-WITH-SELECTION','ImportJobDto',{**job,'selectedRows':1,'queuedRows':1},False,'pure-counter-oracle')
    event=deepcopy(examples[55]['value'])
    add('EVENT-VERSION-MISMATCH','PlacementLifecycleEvent',{**event,'placementVersion':event['aggregateVersion']+1},False,'pure-event-oracle')
    for field in ('traceId','eventId','aggregateVersion'):
        bad=deepcopy(event);del bad[field]
        add('EVENT-MISSING-'+field,'PlacementLifecycleEvent',bad,False)
    return tests
