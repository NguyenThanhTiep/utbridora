export type CodeKind = 'ENTERPRISE_10' | 'BRANCH_13' | 'PERSONAL_ID_12' | 'INVALID_FORMAT';
export function normalizeTaxCode(raw: string): string {
  const code = raw.trim().replace(/[\s\p{Z}]+/gu, '').replace(/[‐‑‒–—−]/g, '-');
  return /^[0-9]{13}$/.test(code) ? code.slice(0, 10) + '-' + code.slice(10) : code;
}
export function validateTaxCodeInput(raw: string): { normalizedTaxCode: string; codeKind: CodeKind; checksumValid: boolean | null; validEnterprise: boolean; message: string } {
  const code = normalizeTaxCode(raw);
  const result = (codeKind: CodeKind, checksumValid: boolean | null, message: string) => ({ normalizedTaxCode: code, codeKind, checksumValid,
    validEnterprise: (codeKind === 'ENTERPRISE_10' || codeKind === 'BRANCH_13') && checksumValid === true, message });
  if (/^[0-9]{12}$/.test(code)) return result('PERSONAL_ID_12', null, 'Số định danh cá nhân 12 chữ số; cần đối chiếu hồ sơ, không kiểm checksum MST doanh nghiệp.');
  if (/^[0-9]{10}(-[0-9]{3})?$/.test(code)) {
    if (code.endsWith('-000')) return result('INVALID_FORMAT', false, 'Mã đơn vị phụ thuộc phải từ 001 đến 999.');
    const sum = [31, 29, 23, 19, 17, 13, 7, 5, 3].reduce((total, w, i) => total + Number(code[i]) * w, 0);
    const valid = (10 - sum % 11) % 10 === Number(code[9]);
    return result(code.length === 10 ? 'ENTERPRISE_10' : 'BRANCH_13', valid, valid
      ? 'MST đúng cấu trúc và checksum; chưa xác nhận tình trạng hoạt động.' : 'MST không đúng checksum. Vui lòng kiểm tra lại mã.');
  }
  return result('INVALID_FORMAT', null, code ? 'Chưa nhận diện MST. Vẫn có thể lưu hồ sơ để đối chiếu thủ công.' : 'Nhập mã số thuế để tra cứu.');
}
