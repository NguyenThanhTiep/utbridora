"""Paired advisory evaluation. Consumes frozen, independently reviewed labels; never creates labels."""
import argparse
from datetime import datetime
from decimal import Decimal, ROUND_HALF_UP
import json
import math
from pathlib import Path
import random
import re
from uuid import UUID
from common import sha, write

POLICY = 'semantic-desc-canonical-tie-v1'
TEMPLATE = 'school-advisory-text-v1'
MODEL_REVISION = json.loads((Path(__file__).resolve().parents[2] / 'ai-service/model-lock.json').read_text())['revision']


def require(condition, code):
    if not condition:
        raise ValueError(code)


def number(value):
    require(not isinstance(value, bool), 'INVALID_NUMBER')
    result = Decimal(str(value))
    require(result.is_finite() and abs(result) <= Decimal('1e6'), 'INVALID_NUMBER')
    return result


def uuid(value):
    require(isinstance(value, str) and str(UUID(value)) == value, 'INVALID_OPAQUE_ID')
    return value


def timestamp(value):
    result = datetime.fromisoformat(value.replace('Z', '+00:00'))
    require(result.tzinfo is not None, 'TIMEZONE_REQUIRED')
    return result


def metrics(order, labels):
    def dcg(values):
        return sum((2**label - 1) / math.log2(i + 2) for i, label in enumerate(values[:10]))
    ideal = dcg(sorted(labels.values(), reverse=True))
    ndcg = None if ideal == 0 else dcg([labels[k] for k in order]) / ideal
    precision = sum(labels[k] >= 2 for k in order[:10]) / 10
    return ndcg, precision


def bootstrap(deltas):
    if not deltas:
        return None
    rng = random.Random(20261121)
    values = sorted(sum(rng.choices(deltas, k=len(deltas))) / len(deltas) for _ in range(10000))
    return [values[249], values[9749]]


def evaluate(dataset, scores):
    require(dataset['schemaVersion'] == 'ai-advisory-labels-v1' and scores['schemaVersion'] == 'ai-advisory-scores-v1', 'SCHEMA_VERSION')
    require(dataset['dataKind'] in ('HUMAN_REVIEWED', 'SYNTHETIC'), 'DATA_KIND')
    reviewers = dataset['reviewerIds']
    require(len(reviewers) == 2 and len(set(reviewers)) == 2 and all(reviewers), 'TWO_INDEPENDENT_REVIEWERS_REQUIRED')
    require(timestamp(dataset['frozenAtUtc']) < timestamp(scores['measuredAtUtc']), 'FREEZE_MUST_PRECEDE_MEASUREMENT')
    require(scores['modelRevision'] == MODEL_REVISION and scores['templateVersion'] == TEMPLATE and scores['policyVersion'] == POLICY, 'MEASUREMENT_VERSION_MISMATCH')
    require(re.fullmatch('[a-f0-9]{64}', scores['sourceContentSha256']) is not None, 'SOURCE_ID_REQUIRED')
    require(isinstance(dataset['protocolReviewRef'], str) and dataset['protocolReviewRef'].strip(), 'PREMEASUREMENT_PROTOCOL_REVIEW_REQUIRED')
    demands, split_groups = {}, {'tuning': set(), 'heldout': set()}
    for demand in dataset['demands']:
        key = uuid(demand['demandId'])
        require(key not in demands and demand['split'] in split_groups, 'DUPLICATE_OR_INVALID_SPLIT')
        require(bool(demand['duplicateGroup']), 'DEMAND_DUPLICATE_GROUP_REQUIRED')
        groups = split_groups[demand['split']]
        groups.add(('demand', demand['duplicateGroup']))
        candidates = {}
        require(10 <= len(demand['candidates']) <= 5000, 'POPULATION_SIZE')
        for candidate in demand['candidates']:
            student = uuid(candidate['studentUserId'])
            require(student not in candidates and candidate['overallStatus'] == 'MATCH', 'NON_MATCH_OR_DUPLICATE_CANDIDATE')
            number(candidate['fitScore'])
            require(re.fullmatch('[a-f0-9]{64}', candidate['renderedSourceSha256']) is not None, 'RENDERED_SOURCE_HASH_REQUIRED')
            require(bool(candidate['duplicateGroup']), 'PROFILE_DUPLICATE_GROUP_REQUIRED')
            groups.add(('profile', candidate['duplicateGroup']))
            labels = candidate['reviewerLabels']
            final = candidate['relevance']
            require(len(labels) == 2 and all(type(x) is int and 0 <= x <= 3 for x in labels + [final]), 'INVALID_RELEVANCE_LABEL')
            require((labels[0] == labels[1] == final) or (labels[0] != labels[1] and bool(candidate.get('adjudicationRef'))), 'ADJUDICATION_REQUIRED')
            candidates[student] = candidate
        demands[key] = (demand, candidates)
    require(not split_groups['tuning'].intersection(split_groups['heldout']), 'CROSS_SPLIT_DUPLICATE_LEAKAGE')
    measured = {}
    for demand in scores['demands']:
        key = uuid(demand['demandId'])
        require(key in demands and key not in measured, 'SCORE_DEMAND_MISMATCH')
        expected = demands[key][1]
        rows = {}
        for row in demand['items']:
            student = uuid(row['studentUserId'])
            require(student not in rows and student in expected, 'SCORE_POPULATION_MISMATCH')
            status, score = row['scoreStatus'], row['semanticScore']
            require(status in ('SCORED', 'NO_CONTENT', 'INPUT_TOO_LARGE'), 'INVALID_SCORE_STATUS')
            if status == 'SCORED':
                require(score is not None and -1 <= number(score) <= 1, 'INVALID_SEMANTIC_SCORE')
            else:
                require(score is None, 'UNSCORED_MUST_BE_NULL')
            rows[student] = row
        require(set(rows) == set(expected), 'SCORE_POPULATION_MISMATCH')
        measured[key] = rows
    heldout = {k for k, (d, _) in demands.items() if d['split'] == 'heldout'}
    require(heldout.issubset(measured), 'HELDOUT_MEASUREMENT_MISSING')
    results, ndcg_deltas, precision_deltas = [], [], []
    scored_count = total_count = 0
    quantum = Decimal('0.000001')
    for key in sorted(heldout):
        _, candidates = demands[key]
        rows = measured[key]
        canonical = lambda k: (-number(candidates[k]['fitScore']), k)
        baseline = sorted(candidates, key=canonical)
        def ai_key(k):
            score = rows[k]['semanticScore']
            quantized = Decimal(0) if score is None else number(score).quantize(quantum, rounding=ROUND_HALF_UP)
            return (score is None, -quantized, *canonical(k))
        advisory = sorted(candidates, key=ai_key)
        labels = {k: v['relevance'] for k, v in candidates.items()}
        base_n, base_p = metrics(baseline, labels)
        ai_n, ai_p = metrics(advisory, labels)
        if base_n is not None:
            ndcg_deltas.append(ai_n - base_n)
        precision_deltas.append(ai_p - base_p)
        scored = sum(v['scoreStatus'] == 'SCORED' for v in rows.values())
        scored_count += scored
        total_count += len(rows)
        results.append(dict(demandId=key, candidates=len(rows), scored=scored, coverage=scored / len(rows),
                            baselineNdcg10=base_n, advisoryNdcg10=ai_n,
                            baselinePrecision10=base_p, advisoryPrecision10=ai_p,
                            idcgZero=base_n is None))
    require(bool(results), 'NO_HELDOUT_DATA')
    mean = lambda values: sum(values) / len(values) if values else None
    ndcg_ci, precision_ci = bootstrap(ndcg_deltas), bootstrap(precision_deltas)
    coverage = scored_count / total_count
    tuning_count = sum(d['split'] == 'tuning' for d, _ in demands.values())
    tuning_groups = {d['duplicateGroup'] for d, _ in demands.values() if d['split'] == 'tuning'}
    decision_groups = {demands[r['demandId']][0]['duplicateGroup'] for r in results if not r['idcgZero']}
    gates = dict(independentSample=tuning_count >= 10 and len(tuning_groups) >= 10 and len(ndcg_deltas) >= 30 and len(decision_groups) >= 30,
                 ndcgImprovement=bool(ndcg_ci and mean(ndcg_deltas) >= .03 and ndcg_ci[0] > 0),
                 precisionNoRegression=mean(precision_deltas) >= 0 and precision_ci[0] >= -.02,
                 coverage=coverage >= .8)
    return dict(status='METRICS_COMPUTED_NOT_ACCEPTANCE', dataKind=dataset['dataKind'],
                sourceContentSha256=scores['sourceContentSha256'], modelRevision=MODEL_REVISION,
                proposedGates=gates, eligibleForQualityReview=all(gates.values()) and dataset['dataKind'] == 'HUMAN_REVIEWED',
                qualityAcceptance=False, humanPilot='NOT_RUN', tuningDemands=tuning_count, heldoutDemands=len(results),
                independentTuningGroups=len(tuning_groups), independentDecisionGroups=len(decision_groups),
                ndcgEligibleDemands=len(ndcg_deltas), idcgZeroDemands=len(results) - len(ndcg_deltas),
                macroNdcg10Delta=mean(ndcg_deltas), ndcg95CI=ndcg_ci,
                macroPrecision10Delta=mean(precision_deltas), precision95CI=precision_ci,
                coverage=coverage, bootstrapSamples=10000, bootstrapSeed=20261121, perDemand=results)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dataset', type=Path, required=True)
    parser.add_argument('--scores', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    require(not args.output.exists(), 'OUTPUT_EXISTS')
    labels = json.loads(args.dataset.read_text(encoding='utf-8'))
    scores = json.loads(args.scores.read_text(encoding='utf-8'))
    require(scores['datasetSha256'] == sha(args.dataset), 'FROZEN_DATASET_HASH_MISMATCH')
    report = evaluate(labels, scores)
    report.update(datasetSha256=sha(args.dataset), scoresSha256=sha(args.scores))
    write(args.output, report)
    print(json.dumps({k: report[k] for k in ('status', 'eligibleForQualityReview', 'qualityAcceptance')}))
