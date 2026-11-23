"""Real E5 HTTP microbenchmark on synthetic text; NOT backend job latency, quality or R31."""
import argparse
import json
import math
import os
from pathlib import Path
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
from uuid import uuid4
from common import verify_source, write
from run_isolated import preflight

ROOT = Path(__file__).resolve().parents[2]


def percentile(values, p):
    values = sorted(values)
    return values[max(0, math.ceil(len(values) * p) - 1)]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ack-isolated-host', action='store_true', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--trials', type=int, default=3, choices=range(3, 21))
    args = parser.parse_args()
    if args.output.exists() or os.environ.get('AI_ALLOW_REAL_MODEL_TEST') != '1':
        parser.error('New output and isolated real-model opt-in required')
    manifest = verify_source(ROOT)
    preflight(ROOT, os.environ.get('AI_VALIDATION_EXPECTED_MACHINE_ID', ''))
    import resource
    token = os.environ.get('AI_INTERNAL_TOKEN', '')
    if not token or not os.environ.get('AI_MODEL_PATH'):
        parser.error('Private model environment required')
    lock = json.loads((ROOT / 'ai-service/model-lock.json').read_text())
    # One owned loopback server, no externally supplied endpoint or credential forwarding.
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', 0))
        port = probe.getsockname()[1]
    base = f'http://127.0.0.1:{port}'
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    def call(path, payload=None):
        raw = None if payload is None else json.dumps(payload).encode()
        headers = {} if payload is None else {'Content-Type': 'application/json', 'X-AI-Token': token}
        with opener.open(urllib.request.Request(base + path, data=raw, headers=headers), timeout=12) as reply:
            data = reply.read(131073)
            if len(data) > 131072:
                raise ValueError('RESPONSE_TOO_LARGE')
            return json.loads(data)
    report = dict(status='RUNNING', kind='REAL_MODEL_HTTP_MICROBENCHMARK_SYNTHETIC',
                  sourceContentSha256=manifest['sourceContentSha256'], modelRevision=lock['revision'],
                  backendJobLatencyMeasured=False, qualityAcceptance=False, r31Acceptance=False,
                  trialsPerPopulation=args.trials, populations=[], errors=0)
    process = None
    started = time.perf_counter()
    try:
        with args.output.with_suffix('.server.log').open('xb') as log:
            process = subprocess.Popen([sys.executable, '-m', 'uvicorn', 'app.main:create_app', '--factory',
                '--host', '127.0.0.1', '--port', str(port), '--workers', '1', '--no-access-log'],
                cwd=ROOT / 'ai-service', stdout=log, stderr=subprocess.STDOUT)
            deadline = time.monotonic() + 180
            while True:
                if process.poll() is not None or time.monotonic() > deadline:
                    raise ValueError('OWNED_SERVER_NOT_READY')
                try:
                    ready = call('/ready')
                    if ready.get('modelRevision') != lock['revision']:
                        raise ValueError('MODEL_REVISION_MISMATCH')
                    break
                except (OSError, urllib.error.URLError):
                    time.sleep(.25)
            report['coldReadySeconds'] = time.perf_counter() - started
            for count in (100, 400, 5000):
                durations, batch_times = [], []
                for _ in range(args.trials):
                    trial = time.perf_counter()
                    for offset in range(0, count, 16):
                        if time.perf_counter() - trial > 600:
                            raise ValueError('SYNTHETIC_POPULATION_BUDGET_EXCEEDED')
                        payload = dict(schemaVersion='semantic-v1', requestId=str(uuid4()), templateVersion='school-advisory-text-v1',
                            query='Internship demand\nTitle: Java backend internship\nDescription: Java, SQL, REST APIs and testing.',
                            passages=[dict(key=str(i), text='Student-declared skills and projects\nSynthetic Java SQL project ' + str(i))
                                      for i in range(offset, min(offset + 16, count))])
                        tick = time.perf_counter()
                        reply = call('/internal/v1/similarity', payload)
                        batch_times.append(time.perf_counter() - tick)
                        if (reply.get('modelRevision') != lock['revision'] or reply.get('requestId') != payload['requestId']
                                or reply.get('templateVersion') != payload['templateVersion']
                                or [x['key'] for x in reply['results']] != [x['key'] for x in payload['passages']]
                                or any(x['status'] != 'READY' or x['score'] is None or not math.isfinite(x['score'])
                                       or not -1 <= x['score'] <= 1 for x in reply['results'])):
                            raise ValueError('MODEL_HTTP_RESPONSE_INVALID')
                    durations.append(time.perf_counter() - trial)
                report['populations'].append(dict(candidates=count, syntheticTextOnly=True, trialSeconds=durations,
                    p50Seconds=percentile(durations, .5), p95Seconds=percentile(durations, .95),
                    batchP95Seconds=percentile(batch_times, .95), p95IsSmallSampleEstimate=True))
            report['status'] = 'PASS_WITH_STATED_SCOPE'
    except Exception:
        report['status'] = 'FAILED'
        report['errors'] += 1
        raise
    finally:
        if process is not None:
            if process.poll() is None:
                process.terminate()
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)
        report['ownedServerPeakRssKiB'] = resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss
        write(args.output, report)


if __name__ == '__main__':
    main()
