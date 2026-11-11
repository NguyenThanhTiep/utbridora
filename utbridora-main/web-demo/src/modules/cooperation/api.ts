import { api } from '../../api';
import type { Demand, DemandInput, DemandPage } from './types';

export type SaveAttempt = {
  key: string;
  path: string;
  method: 'POST' | 'PATCH';
  body: string;
};

export function listDemands(organizationId: string, page: number, signal: AbortSignal) {
  return api<DemandPage>(`/enterprise/demands?page=${page}&size=25`, {
    signal,
    headers: { 'X-Organization-Id': organizationId }
  });
}

export function prepareSave(data: DemandInput, existing: Demand | null): SaveAttempt {
  return {
    key: crypto.randomUUID(),
    path: '/enterprise/demands' + (existing ? '/' + encodeURIComponent(existing.demandId) : ''),
    method: existing ? 'PATCH' : 'POST',
    body: JSON.stringify(existing ? { expectedVersion: existing.version, data } : data)
  };
}

export function saveDemand(organizationId: string, attempt: SaveAttempt) {
  return api<Demand>(attempt.path, {
    method: attempt.method,
    body: attempt.body,
    headers: { 'X-Organization-Id': organizationId, 'Idempotency-Key': attempt.key }
  });
}
