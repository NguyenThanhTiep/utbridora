import type { Demand, DemandInput, SkillCriterion } from '../types';
export type SkillRow = { id: string; skillCode: string; minLevel: string; minYearsPractice: string };
export type FormData = {
  schoolOrgId: string; termId: string; title: string; quantity: string; description: string;
  location: string; benefits: string; deadline: string; majorCodes: string; startDate: string; endDate: string;
  academicCatalogVersion: string; taxonomyVersion: string; required: SkillRow[]; preferred: SkillRow[];
};
export const skillRow = (skill?: SkillCriterion): SkillRow => ({ id: crypto.randomUUID(), skillCode: skill?.skillCode || '',
  minLevel: skill?.minLevel?.toString() || '', minYearsPractice: skill?.minYearsPractice?.toString() || '' });
function localDateTime(value: string) {
  const date = new Date(value);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 19);
}
export function initialForm(existing: Demand | null): FormData {
  const data = existing?.data;
  return { schoolOrgId: data?.schoolOrgId || '', termId: data?.termId || '', title: data?.title || '', quantity: data?.quantity.toString() || '1',
    description: data?.description || '', location: data?.location || '', benefits: data?.benefits || '',
    deadline: data ? localDateTime(data.deadline) : '', majorCodes: data?.required.majorCodes.join(', ') || '',
    startDate: data?.required.dateRange.startDate || '', endDate: data?.required.dateRange.endDate || '',
    academicCatalogVersion: data?.academicCatalogVersion || '', taxonomyVersion: data?.taxonomyVersion || '',
    required: data?.required.skills.map(skillRow) || [], preferred: data?.preferred.map(skillRow) || [] };
}
export function demandInput(form: FormData, existing: Demand | null): DemandInput {
  const toSkills = (rows: SkillRow[]) => rows.map(row => ({ skillCode: row.skillCode.trim(),
    minLevel: row.minLevel === '' ? null : Number(row.minLevel),
    minYearsPractice: row.minYearsPractice === '' ? null : Number(row.minYearsPractice) }));
  const required = toSkills(form.required), preferred = toSkills(form.preferred);
  const codes = [...required, ...preferred].map(skill => skill.skillCode);
  if (!form.schoolOrgId || !form.termId) throw new Error('Chọn trường và kỳ thực tập trước khi lưu bản nháp.');
  if (codes.some(code => !code)) throw new Error('Mã kỹ năng không được để trống hoặc chỉ có khoảng trắng.');
  if (new Set(codes).size !== codes.length) throw new Error('Mỗi mã kỹ năng chỉ xuất hiện một lần trong cả phần Bắt buộc và Ưu tiên.');
  if (form.endDate < form.startDate) throw new Error('Ngày kết thúc thực tập phải bằng hoặc sau ngày bắt đầu.');
  if (!form.title.trim() || !form.description.trim()) throw new Error('Tên nhu cầu và mô tả không được chỉ có khoảng trắng.');
  if (!form.academicCatalogVersion.trim() || !form.taxonomyVersion.trim()) throw new Error('Cần phiên bản danh mục học vụ và kỹ năng chính thức.');
  return { schoolOrgId: form.schoolOrgId.trim(), termId: form.termId.trim(), title: form.title.trim(), quantity: Number(form.quantity),
    description: form.description.trim(), location: form.location.trim() || null, benefits: form.benefits.trim() || null,
    deadline: existing && form.deadline === localDateTime(existing.data.deadline) ? existing.data.deadline : new Date(form.deadline).toISOString(),
    required: { majorCodes: form.majorCodes.split(/[,;]+/).map(code => code.trim()).filter(Boolean), skills: required,
      dateRange: { startDate: form.startDate, endDate: form.endDate } }, preferred,
    academicCatalogVersion: form.academicCatalogVersion.trim(), taxonomyVersion: form.taxonomyVersion.trim() };
}
