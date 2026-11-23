"""Package reviewed working source, including uncommitted AI additions, without Git/private state."""
import argparse
import io
import json
from pathlib import Path
import subprocess
import tarfile
from common import identity, sha, write

ROOT = Path(__file__).resolve().parents[2]
PREFIXES = ('src/', 'scripts/', 'ai-service/', 'web-demo/', 'docs/', 'deploy/', '.mvn/', '.github/')
TOP = {'pom.xml', 'mvnw', 'mvnw.cmd', '.gitattributes', '.gitignore', '.dockerignore',
       'Dockerfile', 'README.md', 'TEAM_INTEGRATION_QUICKSTART.md', '.env.example',
       'compose.local.yaml', 'compose.yaml', 'compose.staging.yaml', 'compose.onboarding.yaml', 'compose.scanner.yaml', 'run-local.cmd'}


def untracked_permitted(name):
    if name.startswith(('src/main/java/', 'src/test/java/')):
        return name.endswith('.java')
    if name == 'src/main/resources/db/migration/base/V44__school_ai_advisory.sql':
        return True
    if name.startswith(('scripts/ai_validation/', 'scripts/contracts/')):
        return name.endswith('.py')
    if name.startswith(('ai-service/app/', 'ai-service/scripts/', 'ai-service/tests/', 'ai-service/tests_real/')):
        return name.endswith('.py')
    if name.startswith('ai-service/'):
        return name.removeprefix('ai-service/') in {
            '.dockerignore', '.gitignore', '.env.example', 'Dockerfile', 'README.md', 'compose.isolated.yaml',
            'constraints-light.txt', 'contracts/openapi.json', 'model-lock.json', 'pytest.ini',
            'requirements.txt', 'requirements-model.txt', 'requirements-test.txt', 'requirements-torch-cpu.txt'}
    if name.startswith('docs/guides/AI_MATCHING_'):
        return name.endswith('.md')
    if name.startswith('docs/contracts/extensions/ai-advisory-v1/'):
        return name.rsplit('/', 1)[-1] in {
            'API.md', 'DATA_PORTS.md', 'EVALUATION_PLAN.md', 'IMPLEMENTATION_HANDOFF.md', 'RANKING_POLICY.md',
            'README.md', 'STORAGE_LIFECYCLE.md', 'audit.csv', 'examples.json', 'manifest.json',
            'permissions.csv', 'schemas.json', 'source-map.json'}
    return name in {'docs/contracts/extensions/AI_ADVISORY_IMPLEMENTATION_DECISION.json',
                    'docs/contracts/extensions/AI_ADVISORY_IMPLEMENTATION_DECISION.md',
                    'web-demo/playwright.advisory.config.ts', 'web-demo/src/modules/matching/AdvisoryPanel.tsx',
                    'web-demo/tests/advisory.ui.spec.ts', 'web-demo/tests/fixtures/advisory-harness.tsx',
                    'web-demo/tests/fixtures/advisory.html'}


def permitted(name):
    parts = Path(name).parts
    lower = name.lower()
    if name not in TOP and not name.startswith(PREFIXES):
        return False
    if any(p in {'.git', 'node_modules', 'target', 'dist', '__pycache__', 'models', '.cache',
                 'test-results', 'playwright-report', 'plans'} for p in parts):
        return False
    if any(p.startswith('.env') and p != '.env.example' for p in parts):
        return False
    if any(term in lower for term in ('private', 'credential', 'secret', 'id_rsa', 'id_ed25519')):
        # Java classes named PrivateFileStorage are application source, not private artifacts.
        if not name.startswith(('src/', 'web-demo/src/', 'web-demo/tests/')):
            return False
    return not lower.endswith(('.pyc', '.log', '.pem', '.key', '.p12', '.xlsx', '.zip', '.tar', '.gz', '.safetensors', '.bin')) or name.startswith('src/main/resources/demo/')


def build(root, destination):
    root, destination = Path(root).resolve(), Path(destination).resolve()
    if destination.exists() or destination.with_suffix(destination.suffix + '.json').exists():
        raise ValueError('OUTPUT_EXISTS')
    def git(*args):
        return subprocess.check_output(['git', '-c', 'safe.directory=' + root.as_posix(), *args], cwd=root).decode().strip()
    names = subprocess.check_output(['git', '-c', 'safe.directory=' + root.as_posix(), 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=root).decode().split('\0')
    untracked = set(subprocess.check_output(['git', '-c', 'safe.directory=' + root.as_posix(), 'ls-files', '-z', '--others', '--exclude-standard'], cwd=root).decode().split('\0'))
    files = {}
    excluded = []
    for name in sorted(set(filter(None, names))):
        if not permitted(name) or (name in untracked and not untracked_permitted(name)):
            excluded.append(name)
            continue
        path = root / name
        if path.is_symlink() or not path.resolve().is_relative_to(root):
            raise ValueError('SOURCE_SYMLINK_REFUSED')
        if path.is_file():
            files[name] = sha(path)
    for required in ('pom.xml', 'ai-service/model-lock.json', 'src/main/resources/db/migration/base/V44__school_ai_advisory.sql', 'scripts/ai_validation/run_isolated.py'):
        if required not in files:
            raise ValueError('SOURCE_INCOMPLETE')
    # Files intentionally excluded for privacy must never be Java/resource build inputs.
    if any(p.startswith('src/') for p in excluded):
        raise ValueError('BUILD_INPUT_EXCLUDED_REVIEW_REQUIRED')
    manifest = dict(format='utbridora-ai-source-v1', baseCommit=git('rev-parse', 'HEAD'),
                    state='DIRTY_WORKING_SOURCE' if git('status', '--porcelain') else 'CLEAN_COMMIT',
                    files=files, sourceContentSha256=identity(files))
    with tarfile.open(destination, 'x:gz') as archive:
        for name in files:
            if sha(root / name) != files[name]:
                raise ValueError('SOURCE_CHANGED_DURING_PACKAGING')
            info = archive.gettarinfo(str(root / name), arcname=name)
            info.uid = info.gid = 0
            info.uname = info.gname = ''
            info.mode = 0o755 if name == 'mvnw' else 0o644
            with (root / name).open('rb') as stream:
                archive.addfile(info, stream)
        value = (json.dumps(manifest, indent=2) + '\n').encode()
        info = tarfile.TarInfo('.ai-validation-source.json')
        info.size, info.mode = len(value), 0o644
        archive.addfile(info, io.BytesIO(value))
    receipt = dict(sourceContentSha256=manifest['sourceContentSha256'], baseCommit=manifest['baseCommit'],
                   archiveSha256=sha(destination), files=len(files), excluded=excluded)
    write(destination.with_suffix(destination.suffix + '.json'), receipt)
    return receipt


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(build(ROOT, args.output)))
