"""Explicit Linux isolated-host runner. Never provisions, changes users, or deploys shared services."""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import platform
import re
import runpy
import shutil
import subprocess
import sys
from common import junit, sha, verify_source, write

ROOT = Path(__file__).resolve().parents[2]


def preflight(root, expected_machine, expected_docker=None):
    if platform.system() != 'Linux':
        raise ValueError('ISOLATED_LINUX_HOST_REQUIRED')
    machine = Path('/etc/machine-id').read_text().strip()
    if not expected_machine or machine != expected_machine:
        raise ValueError('MACHINE_ID_MISMATCH')
    memory = dict(line.split(':', 1) for line in Path('/proc/meminfo').read_text().splitlines())
    total = int(memory['MemTotal'].split()[0]) * 1024
    available = int(memory['MemAvailable'].split()[0]) * 1024
    if total < 7 * 1024**3 or available < 4 * 1024**3 or shutil.disk_usage(root).free < 12 * 1024**3:
        raise ValueError('INSUFFICIENT_FREE_RESOURCES')
    result = dict(platform=platform.platform(), machineId=machine, cpuCount=os.cpu_count(),
                  memoryTotalBytes=total, memoryAvailableBytes=available,
                  diskFreeBytes=shutil.disk_usage(root).free, pythonVersion=platform.python_version(), r31Acceptance=False)
    if Path('/proc/cpuinfo').is_file():
        result['cpuModel'] = next((line.split(':', 1)[1].strip() for line in Path('/proc/cpuinfo').read_text().splitlines() if line.startswith('model name')), 'UNKNOWN')
    result['cgroupLimits'] = {name: Path('/sys/fs/cgroup', name).read_text().strip() for name in ('cpu.max', 'memory.max') if Path('/sys/fs/cgroup', name).is_file()}
    if expected_docker is not None:
        if (os.environ.get('DOCKER_HOST', '') not in ('', 'unix:///var/run/docker.sock')
                or any(os.environ.get(k) for k in ('DOCKER_CONTEXT', 'DOCKER_TLS_VERIFY', 'DOCKER_CERT_PATH',
                    'TESTCONTAINERS_HOST_OVERRIDE', 'TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE'))):
            raise ValueError('REMOTE_OR_CONTEXT_DOCKER_REFUSED')
        info = json.loads(subprocess.check_output(['docker', '--host', 'unix:///var/run/docker.sock', 'info', '--format', '{{json .}}']))
        if info.get('ID') != expected_docker or info.get('OSType') != 'linux':
            raise ValueError('DOCKER_ID_MISMATCH')
        if subprocess.check_output(['docker', '--host', 'unix:///var/run/docker.sock', 'ps', '-aq']).strip():
            raise ValueError('DEDICATED_EMPTY_DOCKER_REQUIRED')
        result['dockerId'] = info['ID']
    return result


def execute(argv, cwd, evidence, name, env):
    with (evidence / (name + '.log')).open('xb') as log:
        result = subprocess.run(argv, cwd=cwd, env=env, stdout=log, stderr=subprocess.STDOUT, timeout=7200)
    if result.returncode:
        raise ValueError('COMMAND_FAILED_' + name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ack-isolated-host', action='store_true', required=True)
    parser.add_argument('--expected-machine-id', required=True)
    parser.add_argument('--expected-docker-id')
    parser.add_argument('--stage', choices=['regression', 'model'], required=True)
    parser.add_argument('--evidence', type=Path, required=True)
    args = parser.parse_args()
    evidence = args.evidence.resolve()
    if evidence.exists() or evidence.is_relative_to(ROOT):
        parser.error('Evidence must be a new directory outside the extracted source')
    # All potentially heavyweight operations are after source/isolation checks.
    manifest = verify_source(ROOT)
    if args.stage == 'regression' and not args.expected_docker_id:
        parser.error('Regression requires the dedicated Docker daemon ID')
    host = preflight(ROOT, args.expected_machine_id, args.expected_docker_id if args.stage == 'regression' else None)
    evidence.mkdir(mode=0o700, parents=True)
    os.umask(0o077)
    write(evidence / 'environment.json', host)
    env = os.environ.copy()
    env['MAVEN_OPTS'] = '-Xmx768m -XX:MaxMetaspaceSize=256m -XX:ActiveProcessorCount=2'
    env['DOCKER_HOST'] = 'unix:///var/run/docker.sock'
    env['AI_VALIDATION_EXPECTED_MACHINE_ID'] = args.expected_machine_id
    summary = dict(stage=args.stage, status='RUNNING', startedAtUtc=datetime.now(timezone.utc).isoformat(),
                   sourceContentSha256=manifest['sourceContentSha256'], baseCommit=manifest['baseCommit'],
                   state=manifest['state'], acceptance='DEV_ONLY', qualityAcceptance=False, r31Acceptance=False)
    run = lambda cmd, cwd, name: execute(cmd, cwd, evidence, name, env)
    try:
        if args.stage == 'regression':
            if (ROOT / 'target').exists() or (ROOT / 'web-demo/node_modules').exists():
                raise ValueError('FRESH_EXTRACTION_REQUIRED')
            mvn = shutil.which('mvn')
            if not mvn:
                raise ValueError('MAVEN_REQUIRED')
            for suite in ('scripts/ci', 'scripts/ops', 'scripts/ai_validation'):
                run([sys.executable, '-m', 'unittest', 'discover', '-s', suite, '-p', 'test_*.py'], ROOT, suite.replace('/', '-'))
            run(['npm', 'ci'], ROOT / 'web-demo', 'npm-ci')
            run(['npx', '--no-install', 'playwright', 'install', 'chromium'], ROOT / 'web-demo', 'chromium-install')
            run(['node', 'scripts/ci-browser-ready.cjs'], ROOT / 'web-demo', 'chromium-preflight')
            run(['npm', 'run', 'build'], ROOT / 'web-demo', 'frontend-build')
            fork_limits = '-DargLine=-Xmx1536m -XX:MaxMetaspaceSize=384m -XX:ActiveProcessorCount=2'
            run([mvn, '-B', fork_limits, '-DskipITs', 'verify'], ROOT, 'java-unit')
            unit = junit(sorted((ROOT / 'target/surefire-reports').glob('TEST-*.xml')))
            run([mvn, '-B', fork_limits, 'test-compile', 'failsafe:integration-test', 'failsafe:verify',
                 '-Dfrontend.e2e=true', '-Dt5.week3.browser=true'], ROOT, 'integration')
            it_paths = sorted((ROOT / 'target/failsafe-reports').glob('TEST-*.xml'))
            integrations = junit(it_paths, required=['vn.utbridora.auth.AIAdvisoryRepositoryIT'])
            inventory = runpy.run_path(str(ROOT / 'scripts/ci/integration_shards.py'))['discover'](ROOT)
            if set(integrations['classes']) != set(inventory):
                raise ValueError('INTEGRATION_INVENTORY_MISMATCH')
            expected_browser = set()
            for path in (ROOT / 'src/test/java').rglob('*.java'):
                expected_browser.update(re.findall(r'E2E_REPORT_FILE"\s*,\s*"\.\./target/([^"\n]+\.xml)"', path.read_text(encoding='utf-8')))
            if not expected_browser:
                raise ValueError('BROWSER_INVENTORY_EMPTY')
            browser = {name: junit([ROOT / 'target' / name], allow_skips=True) for name in sorted(expected_browser)}
            for script, name in [('verify_contracts.py', 'canonical-contract'), ('verify_ai_advisory_runtime.py', 'extension-contract')]:
                report = evidence / (name + '.json')
                run([sys.executable, 'scripts/contracts/' + script, '--output', str(report)], ROOT, name)
                value = json.loads(report.read_text())
                if value['counts']['failed'] or not value['counts']['passed']:
                    raise ValueError('CONTRACT_NOT_PASS')
            run([sys.executable, '-m', 'pytest', 'tests', '--junitxml=' + str(evidence / 'python-fake.xml')], ROOT / 'ai-service', 'python-fake')
            junit([evidence / 'python-fake.xml'])
            env['ADVISORY_EVIDENCE_DIRECTORY'] = str(evidence)
            run(['npx', '--no-install', 'playwright', 'test', '--config', 'playwright.advisory.config.ts'], ROOT / 'web-demo', 'advisory-component-browser')
            junit([evidence / 'advisory-browser.xml'])
            summary.update(unit=unit, mysqlIntegration=integrations, fullApplicationBrowser=browser, advisoryComponent='MOCK_HTTP_PASS')
        else:
            if not os.environ.get('AI_MODEL_PATH') or not os.environ.get('AI_INTERNAL_TOKEN'):
                raise ValueError('PRIVATE_MODEL_ENVIRONMENT_REQUIRED')
            env.update(AI_ALLOW_REAL_MODEL_TEST='1', AI_REAL_MODEL_REPORT=str(evidence / 'real-model.json'),
                       HF_HUB_OFFLINE='1', TRANSFORMERS_OFFLINE='1', HF_HUB_DISABLE_TELEMETRY='1', DO_NOT_TRACK='1')
            run([sys.executable, '-m', 'pip', 'check'], ROOT, 'pip-check')
            run([sys.executable, '-m', 'pip', 'freeze', '--all'], ROOT, 'runtime-freeze')
            run([sys.executable, '-m', 'pytest', 'tests_real/test_model_offline.py', '--junitxml=' + str(evidence / 'real-model.xml')], ROOT / 'ai-service', 'real-model')
            summary['realModel'] = junit([evidence / 'real-model.xml'])
            summary['offlineProof'] = 'PYTHON_SOCKET_BLOCK_ONLY_OS_EGRESS_PROOF_PENDING'
            run([sys.executable, 'scripts/ai_validation/benchmark_http.py', '--ack-isolated-host',
                 '--output', str(evidence / 'http-benchmark.json')], ROOT, 'http-benchmark')
        verify_source(ROOT)
        summary['status'] = 'PASS_WITH_STATED_SCOPE'
    except Exception as error:
        summary['status'] = 'FAILED'
        # Avoid recording third-party exceptions, URLs, payloads or environment secrets.
        summary['errorType'] = type(error).__name__
        raise
    finally:
        if args.stage == 'regression':
            for folder in ('surefire-reports', 'failsafe-reports'):
                if (ROOT / 'target' / folder).is_dir():
                    shutil.copytree(ROOT / 'target' / folder, evidence / folder, dirs_exist_ok=True)
            for path in (ROOT / 'target').glob('*'):
                if path.is_file() and (path.suffix in ('.xml', '.json') or path.name.endswith('browser.log')):
                    shutil.copy2(path, evidence / path.name)
            if (ROOT / 'web-demo/test-results').is_dir():
                shutil.copytree(ROOT / 'web-demo/test-results', evidence / 'browser-artifacts', dirs_exist_ok=True)
        summary['finishedAtUtc'] = datetime.now(timezone.utc).isoformat()
        summary['evidenceHashes'] = {p.relative_to(evidence).as_posix(): sha(p) for p in evidence.rglob('*') if p.is_file()}
        write(evidence / 'summary.json', summary)


if __name__ == '__main__':
    main()
