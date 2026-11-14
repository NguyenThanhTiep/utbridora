"""Partition the complete Failsafe inventory; reject missing or extra JUnit suites."""
import argparse
import json
import re
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]


def discover(root):
    classes = []
    for source in sorted((root / 'src/test/java').rglob('*.java')):
        name = source.stem
        # Maven Failsafe defaults: IT*, *IT, *ITCase (not arbitrary *IT*).
        if not (name.startswith('IT') or name.endswith(('IT', 'ITCase'))):
            continue
        package = re.search(r'^\s*package\s+([\w.]+)\s*;', source.read_text(encoding='utf-8'), re.M)
        classes.append(f'{package[1]}.{name}' if package else name)
    if not classes or len(classes) != len(set(classes)):
        raise ValueError('Integration inventory is empty or contains duplicate classes')
    return sorted(classes)


def partition(classes, weights, count):
    if not 1 <= count <= len(classes):
        raise ValueError('Shard count must be between 1 and the integration class count')
    groups, totals = [[] for _ in range(count)], [0.0] * count
    # Longest first; unknown/new classes are included automatically with a 30s estimate.
    for name in sorted(classes, key=lambda name: (-weights.get(name, 30.0), name)):
        index = min(range(count), key=lambda i: (totals[i], i))
        groups[index].append(name)
        totals[index] += weights.get(name, 30.0)
    return [sorted(group) for group in groups]


def make_plan(root, shard, count):
    weights = json.loads((root / 'scripts/ci/integration-timings.json').read_text(encoding='utf-8'))
    groups = partition(discover(root), weights, count)
    if not 1 <= shard <= count:
        raise ValueError('Invalid shard number')
    return {'shard': shard, 'shards': count, 'classes': groups[shard - 1]}


def check_reports(plan, folder):
    actual, tests = [], 0
    for path in sorted(folder.glob('TEST-*.xml')):
        suite = ET.parse(path).getroot()
        name = suite.get('name')
        cases = list(suite.iter('testcase'))
        if not cases or any(case.find(kind) is not None for case in cases for kind in ('failure', 'error')):
            raise ValueError(f'Empty or failed integration suite: {name}')
        if int(suite.get('failures', '0')) or int(suite.get('errors', '0')):
            raise ValueError(f'Failed integration suite: {name}')
        actual.append(name)
        tests += len(cases)
    if sorted(actual) != plan['classes']:
        raise ValueError(f'Integration suite coverage differs: expected {plan["classes"]}, got {actual}')
    return tests


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    plan_parser = sub.add_parser('plan')
    plan_parser.add_argument('--shard', type=int, required=True)
    plan_parser.add_argument('--shards', type=int, required=True)
    sub.add_parser('check')
    args = parser.parse_args()
    target = ROOT / 'target'
    manifest = target / 'ci-shard.json'
    if args.command == 'plan':
        plan = make_plan(ROOT, args.shard, args.shards)
        target.mkdir(exist_ok=True)
        manifest.write_text(json.dumps(plan, indent=2) + '\n', encoding='utf-8')
        includes = ''.join(name.replace('.', '/') + '.java\n' for name in plan['classes'])
        (target / 'ci-includes.txt').write_text(includes, encoding='utf-8')
        print(json.dumps(plan, indent=2))
    else:
        plan = json.loads(manifest.read_text(encoding='utf-8'))
        if plan != make_plan(ROOT, plan['shard'], plan['shards']):
            raise ValueError('Shard manifest differs from current integration inventory')
        tests = check_reports(plan, target / 'failsafe-reports')
        print(f'Shard {plan["shard"]}/{plan["shards"]}: {len(plan["classes"])} suites, {tests} tests; coverage complete.')


if __name__ == '__main__':
    main()
