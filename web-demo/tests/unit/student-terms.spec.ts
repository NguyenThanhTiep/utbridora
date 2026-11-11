import { test, expect } from '@playwright/test';
import { listTerms } from '../../src/modules/student/api';

const originalFetch = globalThis.fetch;
test.afterEach(() => { globalThis.fetch = originalFetch; });

test('keeps active terms on later pages and the selected school filter', async () => {
  const requests: URL[] = [];
  globalThis.fetch = async input => {
    const url = new URL(String(input), 'http://localhost');
    requests.push(url);
    const page = Number(url.searchParams.get('page'));
    return Response.json({ content: [{ termId: 'term-' + page, status: page ? 'ACTIVE' : 'DRAFT' }], totalPages: 2 });
  };
  const terms = await listTerms('school A');
  expect(terms.filter(term => term.status === 'ACTIVE').map(term => term.termId)).toEqual(['term-1']);
  expect(requests.map(url => url.searchParams.get('page'))).toEqual(['0', '1']);
  expect(requests.every(url => url.searchParams.get('schoolOrgId') === 'school A'
    && url.searchParams.get('size') === '100')).toBe(true);
});

test('an empty first page does not request another page', async () => {
  let calls = 0;
  globalThis.fetch = async () => { calls++; return Response.json({ content: [], totalPages: 0 }); };
  expect(await listTerms('school')).toEqual([]);
  expect(calls).toBe(1);
});

test('a failed later page rejects rather than presenting a partial term list', async () => {
  let calls = 0;
  globalThis.fetch = async () => ++calls === 1
    ? Response.json({ content: [{ termId: 'first' }], totalPages: 2 })
    : Response.json({ error: 'DEPENDENCY_UNAVAILABLE' }, { status: 503 });
  await expect(listTerms('school')).rejects.toThrow();
});
