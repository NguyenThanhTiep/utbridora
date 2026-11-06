import { api } from '../../../api';

export type CatalogSkill = {
  skillCode: string; name: string; categoryCode: string; parentSkillCode: string | null;
  status: 'ACTIVE' | 'DEPRECATED'; version: number;
};
export type SkillCatalog = {
  taxonomyVersion: string;
  skills: { content: CatalogSkill[]; page: number; size: number; totalElements: number; totalPages: number };
};

export function readSkillCatalog(query: string, page: number, signal: AbortSignal) {
  const params = new URLSearchParams({ q: query.trim(), page: String(page), size: '10', includeDeprecated: 'false' });
  return api<SkillCatalog>(`/taxonomy/skills?${params}`, { signal });
}
