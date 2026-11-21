"""Deterministic canonical T1/T2 load fixture in an EMPTY isolated synthetic database.

No live import, no emails, no consent, no approval/Placement evidence. The50 scoped
School identities exist only to exercise50 independent authenticated load sessions.
"""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import re
import uuid
from handoff import config, connect, database_inventory, digest, encoded, ident, private_json

SKILLS=['JAVA','SQL','SPRING','GIT','DOCKER','PYTHON','JAVASCRIPT','TYPESCRIPT','LINUX','HTTP']


def seed(c, start, end):
    if start >= end:raise ValueError('Fixture period is invalid')
    password_hash=os.environ['UTB_OPS_LOAD_PASSWORD_HASH']
    if not re.fullmatch(r'\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}',password_hash):
        raise ValueError('Supply an externally generated BCrypt hash, never a plaintext fixture password')
    namespace=uuid.UUID(c['scopeId'])
    def uid(name):return str(uuid.uuid5(namespace,name))
    school=uid('school');terms={400:uid('term400'),5000:uid('term5000')};grantor=uid('grantor');hr=uid('hr')
    fixed=dt.datetime(2026,1,1);batch={}
    def add(table,**row):batch.setdefault(table,[]).append(row)
    def user(key,email,password='!SYNTHETIC_NO_LOGIN!'):
        id=uid(key);add('users',id=id,email=email,password_hash=password,display_name='SYNTHETIC '+key,status='ACTIVE',created_at=fixed,updated_at=fixed);return id
    def member(user,org,role):add('memberships',id=uid('member:'+user+':'+str(org)+':'+role),user_id=user,organization_id=org,role=role,created_at=fixed)
    with connect(c) as conn:
        with conn.cursor() as cur:
            for table in ('users','organizations','student_profiles','demands','skill_categories'):
                cur.execute('SELECT COUNT(*) FROM '+ident(table))
                if cur.fetchone()[0]:raise ValueError('Load fixture requires a newly migrated EMPTY database; existing rows are preserved')
            add('organizations',id=school,name='SYNTHETIC Load School',type='UNIVERSITY',status='VERIFIED',created_at=fixed)
            for i in range(100):add('organizations',id=uid('enterprise'+str(i)),name='SYNTHETIC Enterprise '+str(i),type='ENTERPRISE',status='VERIFIED',created_at=fixed)
            user('grantor','grantor@load.invalid');member(grantor,school,'SCHOOL_STAFF')
            user('hr','hr@load.invalid',password_hash);member(hr,uid('enterprise0'),'ENTERPRISE')
            for count,term in terms.items():
                add('terms',id=term,school_org_id=school,term_code='LOAD-'+str(count),start_date=start,end_date=end,status='ACTIVE',version=1,created_at=fixed,updated_at=fixed)
                add('academic_catalogs',term_id=term,school_org_id=school,catalog_version='load-academic-v1')
                add('academic_catalog_entries',term_id=term,kind='MAJOR',code='SE',name='Synthetic Software Engineering',major_codes='[]',status='ACTIVE')
                for i in range(50):
                    actor=uid('staff'+str(i));delegation=uid('delegation'+str(count)+':'+str(i))
                    add('school_delegations',id=delegation,user_id=actor,school_org_id=school,term_id=term,granted_by=grantor,decision_ref='SYNTHETIC-LOAD-NOT-REAL-AUTHORIZATION',
                        profile_read_fields_json='[]',valid_from=dt.datetime(2020,1,1),valid_until=dt.datetime(2040,1,1),active=True,version=1)
                    for permission in ('matching:run:execute','matching:school:detail_read'):
                        add('school_delegation_permissions',assignment_id=delegation,permission_code=permission)
            for i in range(50):
                actor=user('staff'+str(i),'school'+str(i)+'@load.invalid',password_hash);member(actor,school,'SCHOOL_STAFF')
            category=uid('category')
            add('skill_categories',id=category,category_code='LOAD_SKILLS',name='Load skills',normalized_name='load skills',status='ACTIVE',version=1,created_at=fixed,updated_at=fixed)
            for code in SKILLS:add('skills',id=uid('skill'+code),skill_code=code,name=code,normalized_name=code.lower(),category_id=category,status='ACTIVE',version=1,created_at=fixed,updated_at=fixed)
            for i in range(5000):
                actor=user('student'+str(i),'student'+str(i)+'@load.invalid');member(actor,school,'STUDENT');profile=uid('profile'+str(i));roster=uid('roster'+str(i))
                add('student_rosters',id=roster,school_org_id=school,student_code=f'LOAD{i:05}',user_id=actor,full_name='Synthetic '+str(i),email='student'+str(i)+'@load.invalid',major_code='SE',term_code='LOAD-5000',created_at=fixed)
                add('student_profiles',id=profile,user_id=actor,skills_declared=True,profile_version=1,created_at=fixed,updated_at=fixed)
                for ordinal,code in enumerate(SKILLS):add('student_profile_skills',profile_id=profile,skill_code=code,ordinal=ordinal,level=3,years_practice=1,source='SELF_DECLARED')
                for count,term in terms.items():
                    if i>=count:continue
                    add('student_profile_terms',profile_id=profile,term_id=term,school_org_id=school,major_code='SE',availability_declared=True,created_at=fixed,updated_at=fixed)
                    add('student_profile_term_periods',profile_id=profile,term_id=term,ordinal=0,start_date=start,end_date=end)
                    add('student_term_eligibilities',id=uid('eligibility'+str(count)+':'+str(i)),student_roster_id=roster,term_code='LOAD-'+str(count),user_id=actor,school_org_id=school,term_id=term,version=1,school_approved=False,created_at=fixed)
            # Explicit FK order, not dictionary insertion order; never disable constraints.
            order=['organizations','users','memberships','terms','academic_catalogs','academic_catalog_entries','school_delegations','school_delegation_permissions','skill_categories','skills','student_rosters','student_profiles','student_profile_terms','student_profile_term_periods','student_profile_skills','student_term_eligibilities']
            for table in order:
                rows=batch[table];columns=list(rows[0]);query='INSERT INTO '+ident(table)+'('+','.join(map(ident,columns))+') VALUES('+','.join(['%s']*len(columns))+')'
                for offset in range(0,len(rows),500):cur.executemany(query,[tuple(row.get(col) for col in columns) for row in rows[offset:offset+500]])
            cur.execute('SELECT taxonomy_version FROM taxonomy_catalog_state WHERE id=1');taxonomy='skills-v'+str(cur.fetchone()[0])
            conn.commit();fingerprints=database_inventory(conn)
    requests={}
    for count,term in terms.items():
        requests[str(count)]={'schoolOrgId':school,'termId':term,'title':'SYNTHETIC Load '+str(count),'quantity':count,
            'description':'Synthetic load fixture; no real hiring or approval','location':None,'benefits':None,
            'deadline':(start-dt.timedelta(days=1)).isoformat()+'T00:00:00Z',
            'required':{'majorCodes':['SE'],'skills':[{'skillCode':code,'minLevel':1,'minYearsPractice':0} for code in SKILLS[:5 if count==400 else 10]],
                        'dateRange':{'startDate':start.isoformat(),'endDate':end.isoformat()}},'preferred':[],
            'academicCatalogVersion':'load-academic-v1','taxonomyVersion':taxonomy}
    return {'version':1,'status':'SEEDED_NOT_MEASURED','scopeId':c['scopeId'],'schema':c['schema'],'schoolOrgId':school,
            'enterpriseOrgId':uid('enterprise0'),'profiles':5000,'enterprises':100,'skillRelations':50000,'termProfiles':{str(k):v for k,v in terms.items()},
            'fingerprints':fingerprints,'seedSha256':digest(encoded(fingerprints)),'demandCreateRequests':requests,
            'accounts':[{'emailEnv':'UTB_LOAD_EMAIL_'+str(i),'passwordEnv':'UTB_LOAD_PASSWORD'} for i in range(50)],
            'accountRecipe':'school0@load.invalid through school49@load.invalid; password comes only from the separately supplied hash',
            'limits':['Synthetic capability prerequisites only; no real school approval, consent, Placement or emails','Fixture success is not a400/5000 performance PASS']}


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--config',required=True);p.add_argument('--confirm-scope',required=True)
    p.add_argument('--start-date',required=True);p.add_argument('--end-date',required=True);p.add_argument('--output',required=True);a=p.parse_args();c=config(a.config)
    if a.confirm_scope!=c['scopeId'] or Path(a.output).exists():raise ValueError('Scope/output confirmation failed')
    result=seed(c,dt.date.fromisoformat(a.start_date),dt.date.fromisoformat(a.end_date));private_json(a.output,result);print(result['status'])


if __name__=='__main__':main()
