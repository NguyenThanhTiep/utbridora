"""HTTPS/session/scope probe for the dedicated synthetic demo. Never prints credentials or cookies."""
import argparse
import http.cookiejar
import json
from pathlib import Path
import ssl
from urllib.error import HTTPError
from urllib.request import Request, build_opener, HTTPCookieProcessor, HTTPSHandler
import uuid

parser = argparse.ArgumentParser()
parser.add_argument('--base-url', required=True)
parser.add_argument('--ca-file', required=True)
parser.add_argument('--env-file', default='.env.week1-demo')
parser.add_argument('--output', default='target/week1-demo-check.json')
parser.add_argument('--exercise-profile', action='store_true')
args = parser.parse_args()
assert args.base_url.startswith('https://')
settings = dict(line.split('=', 1) for line in Path(args.env_file).read_text(encoding='utf-8-sig').splitlines() if '=' in line)
context = ssl.create_default_context(cafile=args.ca_file)
school = '00000000-0000-4000-8000-000000000011'
term = '00000000-0000-4000-8000-000000000021'
proof = {'url': args.base_url, 'certificateVerified': True, 'checks': []}
for user in ['admin', 'school', 'student', 'hr', 'mentor']:
    jar = http.cookiejar.CookieJar()
    opener = build_opener(HTTPSHandler(context=context), HTTPCookieProcessor(jar))

    def call(method, path, body=None, headers=None):
        request = Request(args.base_url + '/api/v1' + path, method=method,
                          data=None if body is None else json.dumps(body).encode(),
                          headers={'Content-Type': 'application/json', **(headers or {})})
        try:
            with opener.open(request, timeout=30) as response:
                return response.status, json.load(response)
        except HTTPError as error:
            return error.code, json.load(error)

    status, csrf = call('GET', '/auth/csrf')
    assert status == 200
    status, _ = call('POST', '/auth/login', {'email': user + '@example.test', 'password': settings['DEMO_PASSWORD']},
                     {'X-CSRF-TOKEN': csrf['token']})
    assert status == 200, (user, status)
    assert any(cookie.name == 'SESSION' and cookie.secure for cookie in jar)
    status, me = call('GET', '/auth/me')
    assert status == 200 and me['email'] == user + '@example.test'
    if user == 'admin':
        status, data = call('GET', '/admin/users?size=25')
        assert status == 200 and data['totalElements'] == 7
    elif user in ['hr', 'mentor']:
        status, assignments = call('GET', '/t3/context-assignments/me')
        assert status == 200 and len(assignments) == 1
        headers = {'X-Context-Assignment-Id': assignments[0]['id']}
        status, cohorts = call('GET', '/t3/cohorts?termId=' + term, headers=headers)
        assert status == 200 and len(cohorts['content']) == 1
        status, groups = call('GET', '/t3/cohorts/' + cohorts['content'][0]['cohortId'] + '/groups', headers=headers)
        assert status == 200 and groups['content'][0]['code'] == 'DEMO-G1'
        assert len(groups['content'][0]['caseIds']) == 1
    else:
        status, terms = call('GET', '/terms?schoolOrgId=' + school)
        assert status == 200 and terms['content'][0]['termId'] == term
        if user == 'student':
            status, catalog = call('GET', '/students/me/catalogs?termId=' + term + '&kind=MAJOR')
            assert status == 200 and {item['code'] for item in catalog['entries']['content']} == {'AI', 'SE'}
            status, view = call('GET', '/students/me/profile?termId=' + term)
            if status == 404 and args.exercise_profile:
                _, csrf = call('GET', '/auth/csrf')
                payload = dict(expectedProfileVersion=0, termId=term, majorCode='SE', skillsDeclared=False, skills=[],
                               customSkills=[], availabilityDeclared=False, availablePeriods=[], orientationCodes=[],
                               subjectCodes=[], projects=[], certificates=[], cvFileId=None, phone='0901234567')
                headers = {'X-CSRF-TOKEN': csrf['token'], 'Idempotency-Key': str(uuid.uuid4())}
                status, view = call('PUT', '/students/me/profile', payload, headers)
                assert status == 200
                status, replay = call('PUT', '/students/me/profile', payload, headers)
                assert status == 200 and replay == view
                status, reloaded = call('GET', '/students/me/profile?termId=' + term)
                assert status == 200 and reloaded == view
                proof['checks'].append('profile-create-replay-reload')
            else:
                assert status in [200, 404]
    proof['checks'].append(user + '-login-and-scope')
Path(args.output).parent.mkdir(parents=True, exist_ok=True)
Path(args.output).write_text(json.dumps(proof, indent=2) + '\n', encoding='utf-8')
print('PASS: certificate-verified HTTPS, Secure session and five role/scope probes. No secrets exported.')
