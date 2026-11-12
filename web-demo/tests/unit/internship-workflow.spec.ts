import { test, expect } from '@playwright/test';
import { ApiError } from '../../src/api';
import { httpsArtifact, mondayDate, textWithin, validVersion } from '../../src/modules/internship/workflow';
import { accessLost, newAttempt, uncertainOutcome, workflowError } from '../../src/modules/internship/workflow-api';

test('T3-09 URL rejects executable schemes, userinfo, whitespace and ambiguous backslashes', () => {
  for (const value of ['javascript:alert(1)', 'http://example.test', 'https://u:p@example.test', 'https://u@example.test',
    ' https://example.test', 'https://example.test/a b', 'https:\\example.test', 'https://', '']) expect(httpsArtifact(value), value).toBe(false);
  expect(httpsArtifact('https://example.test/a?version=2#result')).toBe(true);
});
test('T3-09 date is an actual Monday independent of browser timezone', () => {
  expect(mondayDate('2026-11-09')).toBe(true);
  for (const value of ['2026-11-10', '2026-02-30', '2026-13-01', '', '2026-1-01']) expect(mondayDate(value)).toBe(false);
});
test('T3-09 text limits use trimmed Unicode codepoints', () => {
  expect(textWithin('  ', 1, 2000)).toBe(false);
  expect(textWithin(' 😀 '.repeat(2).trim(), 1, 2000)).toBe(true);
  expect(textWithin('😀'.repeat(2000), 1, 2000)).toBe(true);
  expect(textWithin('😀'.repeat(2001), 1, 2000)).toBe(false);
});
test('T3-09 expectedVersion must be a safe positive integer', () => {
  expect(validVersion(1)).toBe(true); expect(validVersion(Number.MAX_SAFE_INTEGER)).toBe(true);
  for (const value of [0, -1, 1.5, NaN, Number.MAX_SAFE_INTEGER + 1]) expect(validVersion(value)).toBe(false);
});
test('T3-09 attempt freezes the submitted payload and creates a distinct operation key', () => {
  const input = { expectedVersion: 2, text: 'Nội dung gốc' }, first = newAttempt('/t3/reports', 'POST', input);
  input.text = 'Changed later';
  expect(JSON.parse(first.body).text).toBe('Nội dung gốc');
  expect(first.key).toMatch(/^[0-9a-f-]{36}$/); expect(newAttempt(first.path, first.method, input).key).not.toBe(first.key);
});
test('T3-09 uncertain outcomes preserve operation; definite version conflict permits refresh', () => {
  expect(uncertainOutcome(new Error('Network'))).toBe(true);
  for (const [status, code] of [[503, 'DEPENDENCY_UNAVAILABLE'], [409, 'REQUEST_IN_PROGRESS'], [403, 'CSRF_INVALID']] as const)
    expect(uncertainOutcome(new ApiError(status, '', null, code))).toBe(true);
  expect(uncertainOutcome(new ApiError(409, '', null, 'VERSION_CONFLICT'))).toBe(false);
});
test('T3-09 CSRF expiry does not discard draft as a revoked permission', () => {
  expect(accessLost(new ApiError(403, '', null, 'CSRF_INVALID'))).toBe(false);
  expect(accessLost(new ApiError(403, '', null, 'ACCESS_DENIED'))).toBe(true);
  expect(accessLost(new ApiError(401, '', null))).toBe(true);
});
test('T3-09 conflict exposes support code without displaying a raw server message', () => {
  const message = workflowError(new ApiError(409, 'private SQL', 'request-1', 'STALE_SUBMISSION'));
  expect(message).toContain('STALE_SUBMISSION'); expect(message).toContain('request-1'); expect(message).not.toContain('private SQL');
});
