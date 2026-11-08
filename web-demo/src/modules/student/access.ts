import type { Grant } from '../../api';

// Chỉ phục vụ hiển thị; backend vẫn quyết định quyền của từng request.
export function hasScopedPermission(grants: Grant[], organizationId: string, role: string, permission: string) {
  return grants.some(grant => grant.organizationId === organizationId && grant.role === role && grant.permissions.includes(permission));
}

// /auth/me/permissions chỉ trả grant STUDENT khi trường VERIFIED và đúng loại UNIVERSITY.
export function studentGrants(grants: Grant[]) {
  return grants.filter(grant => grant.role === 'STUDENT' && grant.organizationId !== null && grant.permissions.includes('student_profile:self:read'));
}
