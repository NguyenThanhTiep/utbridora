import { expect, test } from '@playwright/test';
import { normalizeTaxCode, validateTaxCodeInput } from '../../src/modules/cooperation/workflow/taxCode';

test('tax checksum mirrors backend for enterprise, branch, remainder zero and personal ID', () => {
  for (const code of ['0101248141', '0300588569', '0100109106', '0100112437', '1000010000', '0300000000', '0300000018']) {
    expect(validateTaxCodeInput(code).validEnterprise).toBe(true);
    expect(validateTaxCodeInput(code + '-001').validEnterprise).toBe(true);
    expect(validateTaxCodeInput(code + '-000').validEnterprise).toBe(false);
  }
  expect(validateTaxCodeInput('0101248142').checksumValid).toBe(false);
  expect(validateTaxCodeInput('0300000001').checksumValid).toBe(false);
  expect(validateTaxCodeInput('079123456789')).toMatchObject({ codeKind: 'PERSONAL_ID_12', checksumValid: null, validEnterprise: false });
});
test('normalizes lookup only and accepts no arbitrary nonnumeric format', () => {
  expect(normalizeTaxCode(' 0101 248141 – 001 ')).toBe('0101248141-001');
  expect(normalizeTaxCode('0101248141001')).toBe('0101248141-001');
  for (const code of ['CT-TEST', 'SYNTHETIC', 'DEMO-123', 'PRIVATE-DRAFT', '０１０１２４８１４１', ''])
    expect(validateTaxCodeInput(code).codeKind).toBe('INVALID_FORMAT');
});
