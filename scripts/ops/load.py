"""Reproducible authenticated HTTP load recipe for SRS24.3/R31. No credentials in reports.

Run on the isolated VM only, after preparing the canonical synthetic seed. This tool
does not reset caches, seed data, log response bodies, or enable a privacy release.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import datetime as dt
import hashlib
import http.cookiejar
import json
import math
import os
from pathlib import Path
import platform
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from handoff import encoded, private_json, read

RECIPES = {
    'matching400': dict(profiles=400, required=5, concurrency=10, warmup=20, runs=100, thresholdMs=2000),
    'matching5000': dict(profiles=5000, required=10, concurrency=10, warmup=20, runs=100, thresholdMs=5000),
    'list': dict(profiles=5000, concurrency=50, warmup=50, runs=300, thresholdMs=800),
}


def nearest_rank(samples, percentile=0.95):
    if not samples:
        raise ValueError('No samples')
    return sorted(samples)[math.ceil(len(samples)*percentile)-1]


def summarize(samples, threshold):
    errors = sum(not x['ok'] for x in samples)
    p95 = nearest_rank([x['elapsedMs'] for x in samples])
    return {'requests': len(samples), 'errors': errors, 'p95Ms': p95,
            'percentileMethod': 'nearest-rank ceil(0.95*N); every measured sample, including errors/timeouts',
            'thresholdMs': threshold, 'meetsRecipe': not errors and p95 <= threshold}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args):
        return None


class Session:
    def __init__(self, base, account):
        self.base = base
        self.opener = urllib.request.build_opener(NoRedirect(), urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf = None
        self.refresh_csrf()
        code, _ = self.request('POST', '/api/v1/auth/login',
                               {'email': os.environ[account['emailEnv']], 'password': os.environ[account['passwordEnv']]})
        if code != 200:
            raise ValueError('Load session login failed')
        self.refresh_csrf()

    def refresh_csrf(self):
        code, body = self.request('GET', '/api/v1/auth/csrf')
        if code != 200 or not isinstance(body, dict) or not {'headerName','token'} <= set(body):
            raise ValueError('CSRF bootstrap failed')
        self.csrf = body

    def request(self, method, path, body=None, extra_headers=None):
        headers = dict(extra_headers or {})
        if self.csrf and method != 'GET':
            headers[self.csrf['headerName']] = self.csrf['token']
            headers['Idempotency-Key'] = str(uuid.uuid4())
        if body is not None:
            headers['Content-Type'] = 'application/json'
        req = urllib.request.Request(self.base + path, data=None if body is None else encoded(body), headers=headers, method=method)
        try:
            with self.opener.open(req, timeout=60) as response:
                code, raw = response.status, response.read()
        except urllib.error.HTTPError as error:
            code, raw = error.code, error.read()
        # Only status/timing enters raw samples. Malformed JSON is still a failed request.
        try:
            return code, json.loads(raw)
        except (ValueError, UnicodeError):
            return code, None


def measured(session, scenario, recipe, phase, index):
    start = time.perf_counter_ns(); code = None; ok = False; cause = None
    try:
        if scenario['recipe'].startswith('matching'):
            code, body = session.request('POST', '/api/v1/matching/evaluate', scenario['request'])
            summary = body.get('summary') if isinstance(body, dict) else None
            run = summary.get('run', {}) if isinstance(summary, dict) else {}
            ok = (code == 200 and run.get('state') == 'COMPLETED' and
                  sum(summary.get(k, -1) for k in ('matched','missingData','notMatched')) == recipe['profiles'])
        else:
            code, body = session.request('GET', scenario['path'])
            page = body.get('results', body) if isinstance(body, dict) else None
            ok = (code == 200 and isinstance(page, dict) and isinstance(page.get('content'), list)
                  and len(page['content']) <= 100 and page.get('totalElements') == scenario.get('expectedTotal',5000))
        if not ok:
            cause = 'HTTP_OR_RESPONSE_CONTRACT'
    except Exception as error:
        cause = type(error).__name__
    return {'phase': phase, 'sample': index, 'elapsedMs': (time.perf_counter_ns()-start)/1_000_000,
            'httpStatus': code, 'ok': ok, 'errorClass': cause}


def environment():
    memory = None
    if hasattr(os, 'sysconf'):
        memory = os.sysconf('SC_PAGE_SIZE') * os.sysconf('SC_PHYS_PAGES')
    return {'logicalCpu': os.cpu_count(), 'memoryBytes': memory, 'os': platform.platform(),
            'python': platform.python_version()}


def validate(c):
    if c.get('version') != 1 or c.get('isolatedSynthetic') is not True:
        raise ValueError('An explicitly isolated synthetic environment is required')
    base = urllib.parse.urlsplit(c['baseUrl'])
    if base.scheme not in ('http','https') or base.hostname not in ('127.0.0.1','::1') or base.path not in ('','/') or base.query or base.fragment or base.username:
        raise ValueError('Run on the isolated VM against a loopback API')
    if not c.get('coldStartRef') or not c.get('ssdEvidence') or not c.get('runtimeManifest'):
        raise ValueError('Capture fresh process/cache-start evidence, SSD and Java/MySQL/image manifest first')
    if len(c.get('accounts',[])) < 50:
        raise ValueError('Supply50 distinct authorized synthetic accounts for50 independent sessions')
    if len({x['emailEnv'] for x in c['accounts']}) != len(c['accounts']):
        raise ValueError('Account environment references must be distinct')
    seed = Path(c['seedArtifact'])
    if hashlib.sha256(seed.read_bytes()).hexdigest() != c['seedSha256']:
        raise ValueError('Seed checksum mismatch')
    if not isinstance(c.get('commit'), str) or len(c['commit']) != 40 or any(x not in '0123456789abcdef' for x in c['commit']):
        raise ValueError('Pin the actual application commit')
    for scenario in c['scenarios']:
        recipe = RECIPES[scenario['recipe']]
        if scenario.get('fixtureProfiles') != recipe['profiles']:
            raise ValueError('Scenario fixture count mismatch')
        if 'required' in recipe and scenario.get('requiredCriteria') != recipe['required']:
            raise ValueError('Scenario criteria count mismatch')
        if scenario['recipe'] == 'list':
            path = urllib.parse.urlsplit(scenario['path'])
            params = urllib.parse.parse_qs(path.query)
            if path.scheme or path.netloc or not path.path.startswith('/api/v1/') or not 1 <= int(params.get('size',['25'])[0]) <= 100:
                raise ValueError('List recipe must use a local canonical paged API size<=100')


def run(c):
    validate(c)
    if len(c['scenarios']) != 1:
        raise ValueError('Measure one scenario per fresh recorded runtime; select --scenario')
    emails = [os.environ[a['emailEnv']] for a in c['accounts']]
    if len(emails) != len(set(emails)):
        raise ValueError('The50 sessions require50 distinct identities')
    env = environment()
    fixed_hardware = env['logicalCpu'] == 4 and env['memoryBytes'] is not None and 7.5*1024**3 <= env['memoryBytes'] <= 8.5*1024**3
    sessions = [Session(c['baseUrl'].rstrip('/'), a) for a in c['accounts']]
    output = {'version': 1, 'commit': c['commit'], 'seedSha256': c['seedSha256'],
              'manifestSha256': hashlib.sha256(encoded(c)).hexdigest(), 'environment': env,
              'runtimeManifest': c['runtimeManifest'], 'coldStartRef': c['coldStartRef'], 'ssdEvidence': c['ssdEvidence'],
              'startedAt': dt.datetime.now(dt.timezone.utc).isoformat(), 'scenarios': [],
              'limits': ['Network/HTTP wall-clock is included; this is stricter than a classifier microbenchmark',
                         'Cold means first request after operator-recorded restart, not a claim that OS/DB caches were flushed',
                         'SSD/runtime/seed/commit attestations require operator verification; no QA signoff is generated']}
    for scenario in c['scenarios']:
        recipe = RECIPES[scenario['recipe']]
        cold = measured(sessions[0], scenario, recipe, 'cold-first-request', 0)
        def phase(name, total):
            # A session is owned by one worker, never concurrently shared between threads.
            def worker(index):
                return [measured(sessions[index],scenario,recipe,name,i) for i in range(index,total,recipe['concurrency'])]
            with ThreadPoolExecutor(max_workers=recipe['concurrency']) as executor:
                return sorted([s for group in executor.map(worker,range(recipe['concurrency'])) for s in group], key=lambda x:x['sample'])
        warmup = phase('warmup',recipe['warmup']); measured_samples = phase('measured',recipe['runs'])
        summary = summarize(measured_samples,recipe['thresholdMs'])
        entry = {'name': scenario['name'], 'recipe': recipe, 'cold': cold, 'warmup': warmup,
                 'samples': measured_samples, 'summary': summary}
        entry['status'] = ('MEASURED_PASS' if fixed_hardware and summary['meetsRecipe'] and cold['ok'] and all(x['ok'] for x in warmup)
                           else 'MEASURED_FAIL' if fixed_hardware else 'ENVIRONMENT_MISMATCH')
        output['scenarios'].append(entry)
    output['status'] = 'MEASURED_PASS' if output['scenarios'] and all(x['status']=='MEASURED_PASS' for x in output['scenarios']) else 'NOT_ACCEPTED'
    return output


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest',required=True);parser.add_argument('--output',required=True)
    parser.add_argument('--validate-only',action='store_true');parser.add_argument('--scenario')
    a=parser.parse_args();c=read(a.manifest)
    if a.scenario:
        c['scenarios']=[s for s in c['scenarios'] if s['name']==a.scenario]
        if len(c['scenarios'])!=1:raise ValueError('Unknown or duplicate scenario')
    if a.validate_only:
        validate(c);result={'status':'PREPARED_NOT_MEASURED','manifestSha256':hashlib.sha256(encoded(c)).hexdigest()}
    else:
        result=run(c)
    private_json(a.output,result);print(result['status'])
    if result['status']=='NOT_ACCEPTED':
        raise SystemExit(1)


if __name__=='__main__':
    main()
