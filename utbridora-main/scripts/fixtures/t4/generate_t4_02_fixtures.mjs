import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const outDir = dirname(fileURLToPath(import.meta.url));
const targetSchema = 'utbridora';
const studentCount = 100;
const enterpriseCount = 10;
const schoolOrgId = 'f4a10001-0000-4000-8000-000000000001';
const termId = 'f4a30001-0000-4000-8000-000000000001';
const dateRange = { startDate: '2026-12-01', endDate: '2027-02-28' };
const required = {
  majorCodes: ['SE'],
  skills: [{ skillCode: 'JAVA', minLevel: 3, minYearsPractice: 1.0 }],
  dateRange,
};
const outcomes = ['MATCH', 'MISSING_DATA', 'NOT_MATCH'];
const industries = [
  'Phần mềm', 'Thương mại điện tử', 'Tài chính số', 'Giáo dục số', 'Y tế số',
  'Logistics', 'Viễn thông', 'An ninh mạng', 'Phân tích dữ liệu', 'Dịch vụ đám mây',
];
const locations = ['Hà Nội', 'Đà Nẵng', 'TP. Hồ Chí Minh', 'Cần Thơ', 'Huế'];

const id = (prefix, number) => `${prefix}-0000-4000-8000-${String(number).padStart(12, '0')}`;
const quoteSql = (value) => `'${String(value).replaceAll('\\', '\\\\').replaceAll("'", "''")}'`;
const sqlJson = (value) => `CAST(${quoteSql(JSON.stringify(value))} AS JSON)`;
const csvCell = (value) => `"${String(value ?? '').replaceAll('"', '""')}"`;

function makeEnterprise(number) {
  const suffix = String(number).padStart(2, '0');
  const name = `Doanh nghiệp công nghệ giả lập ${suffix}`;
  const industry = industries[number - 1];
  const location = locations[(number - 1) % locations.length];
  const demandId = id('f4a40001', number);
  return {
    number,
    id: id('f4a20001', number),
    name,
    industry,
    sizeBand: ['11-50', '51-200', '201-500'][(number - 1) % 3],
    location,
    demandId,
    title: `Thực tập sinh Backend Java ${suffix}`,
    description: `Vị trí giả lập thuộc lĩnh vực ${industry}; dùng riêng cho kiểm thử Matching.`,
    benefits: 'Có mentor, được hướng dẫn dự án thử nghiệm và nhận xác nhận hoàn thành.',
    quantity: 3 + ((number - 1) % 8),
    deadline: '2026-11-30T23:59:59Z',
  };
}

function makeStudent(number) {
  const suffix = String(number).padStart(3, '0');
  const studentId = id('f4100001', number);
  const outcome = outcomes[(number - 1) % outcomes.length];
  let skillsDeclared = true;
  let skills;
  let notes;
  if (outcome === 'MATCH') {
    skills = [{ skillCode: 'JAVA', level: 3 + (number % 3), yearsPractice: 1 + (number % 4), source: 'SELF_DECLARED' }];
    notes = 'Đủ dữ liệu; đạt ngành SE, kỹ năng JAVA và lịch yêu cầu.';
  } else if (outcome === 'MISSING_DATA') {
    skillsDeclared = false;
    skills = [];
    notes = 'Chưa khai báo kỹ năng; UNKNOWN không đồng nghĩa FAIL.';
  } else {
    skills = [{ skillCode: ['PYTHON', 'C_SHARP', 'PHP'][number % 3], level: 4, yearsPractice: 2, source: 'SELF_DECLARED' }];
    notes = 'Đủ dữ liệu nhưng thiếu kỹ năng JAVA bắt buộc; Required FAIL cho kết quả NOT_MATCH.';
  }
  return {
    number,
    id: studentId,
    email: `t4.student${suffix}@example.test`,
    studentCode: String(number).padStart(6, '0'),
    displayName: `Sinh viên giả lập ${suffix}`,
    outcome,
    skillsDeclared,
    skills,
    notes,
  };
}

const school = { id: schoolOrgId, name: 'Trường đại học giả lập T4' };
const enterprises = Array.from({ length: enterpriseCount }, (_, i) => makeEnterprise(i + 1));
const students = Array.from({ length: studentCount }, (_, i) => makeStudent(i + 1));

const csvHeaders = [
  'case_id', 'expected_outcome', 'student_user_id', 'school_org_id', 'school_name',
  'enterprise_org_id', 'enterprise_name', 'enterprise_industry', 'enterprise_size_band',
  'demand_id', 'demand_title', 'demand_quantity', 'demand_description', 'demand_location',
  'demand_deadline', 'demand_benefits', 'term_id', 'student_code', 'display_name',
  'major_code', 'skills_declared', 'skills_json', 'availability_declared',
  'available_periods_json', 'required_major_codes_json', 'required_skills_json',
  'required_date_range_json', 'preferred_skills_json', 'notes',
];
const csvRows = [csvHeaders.map(csvCell).join(',')];
const caseRows = [];

for (const enterprise of enterprises) {
  const enterpriseProfile = {
    name: enterprise.name,
    type: 'ENTERPRISE',
    status: 'VERIFIED',
    industry: enterprise.industry,
    sizeBand: enterprise.sizeBand,
    headquarters: enterprise.location,
  };
  const demandCriteria = {
    demandId: enterprise.demandId,
    schoolOrgId,
    enterpriseOrgId: enterprise.id,
    termId,
    enterprise: enterpriseProfile,
    title: enterprise.title,
    description: enterprise.description,
    location: enterprise.location,
    benefits: enterprise.benefits,
    deadline: enterprise.deadline,
    quantity: enterprise.quantity,
    required,
    preferred: [],
  };

  for (const student of students) {
    const caseId = `T4-02-D${String(enterprise.number).padStart(2, '0')}-S${String(student.number).padStart(3, '0')}-${student.outcome}`;
    const profileInput = {
      termId,
      studentCode: student.studentCode,
      displayName: student.displayName,
      majorCode: 'SE',
      skillsDeclared: student.skillsDeclared,
      skills: student.skills,
      availabilityDeclared: true,
      availablePeriods: [dateRange],
    };
    const notes = `${student.notes} Nhu cầu: ${enterprise.title}.`;

    const values = [
      caseId, student.outcome, student.id, school.id, school.name, enterprise.id,
      enterprise.name, enterprise.industry, enterprise.sizeBand, enterprise.demandId,
      enterprise.title, enterprise.quantity, enterprise.description, enterprise.location,
      enterprise.deadline, enterprise.benefits, termId, student.studentCode,
      student.displayName, 'SE', student.skillsDeclared, JSON.stringify(student.skills),
      true, JSON.stringify([dateRange]), JSON.stringify(required.majorCodes),
      JSON.stringify(required.skills), JSON.stringify(dateRange), '[]', notes,
    ];
    csvRows.push(values.map(csvCell).join(','));

    caseRows.push(`(${[
      quoteSql(caseId), quoteSql(student.outcome), quoteSql(student.id), quoteSql(school.id),
      quoteSql(enterprise.id), quoteSql(enterprise.demandId), quoteSql(termId),
      quoteSql(student.studentCode), quoteSql(student.displayName),
      sqlJson(profileInput), sqlJson(demandCriteria), quoteSql(notes),
    ].join(',')})`);
  }
}

const sql = [
  '-- T4-02 synthetic mock data v1. TEAM TEST DATABASE ONLY: utbridora.',
  '-- Not a Flyway migration and not a T2/T5 runtime schema.',
  'SET NAMES utf8mb4;',
  '',
  `CREATE TABLE IF NOT EXISTS ${targetSchema}.t4_fixture_cases (`,
  '  case_id VARCHAR(40) NOT NULL PRIMARY KEY,',
  '  expected_outcome VARCHAR(20) NOT NULL,',
  '  student_user_id VARCHAR(36) NOT NULL,',
  '  school_org_id VARCHAR(36) NOT NULL,',
  '  enterprise_org_id VARCHAR(36) NOT NULL,',
  '  demand_id VARCHAR(36) NOT NULL,',
  '  term_id VARCHAR(36) NOT NULL,',
  '  student_code VARCHAR(32) NOT NULL,',
  '  display_name VARCHAR(150) NOT NULL,',
  '  profile_input JSON NOT NULL,',
  '  demand_criteria JSON NOT NULL,',
  '  notes VARCHAR(500) NOT NULL,',
  "  CONSTRAINT chk_t4_fixture_outcome CHECK (expected_outcome IN ('MATCH','MISSING_DATA','NOT_MATCH')),",
  `  CONSTRAINT fk_t4_fixture_student FOREIGN KEY (student_user_id) REFERENCES ${targetSchema}.users(id),`,
  `  CONSTRAINT fk_t4_fixture_school FOREIGN KEY (school_org_id) REFERENCES ${targetSchema}.organizations(id),`,
  `  CONSTRAINT fk_t4_fixture_enterprise FOREIGN KEY (enterprise_org_id) REFERENCES ${targetSchema}.organizations(id),`,
  '  INDEX ix_t4_fixture_demand (demand_id),',
  '  INDEX ix_t4_fixture_expected (expected_outcome)',
  ') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;',
  '',
  '-- Remove the three IDs from the initial small sample; generated case IDs below are stable/upsertable.',
  `DELETE FROM ${targetSchema}.t4_fixture_cases WHERE case_id IN ('T4-02-MATCH','T4-02-MISSING','T4-02-NOT-MATCH');`,
  '',
  '-- Synthetic non-login users; IDs and emails are deterministic and reserved for this fixture.',
  `INSERT INTO ${targetSchema}.users (id,email,password_hash,display_name,status,security_version,failed_attempts,locked_until,created_at,updated_at) VALUES`,
  students.map((s) => `(${[
    quoteSql(s.id), quoteSql(s.email), quoteSql('!T4_FIXTURE_NO_LOGIN!'), quoteSql(s.displayName),
    quoteSql('PENDING_ACTIVATION'), '0', '0', 'NULL', 'UTC_TIMESTAMP(6)', 'UTC_TIMESTAMP(6)',
  ].join(',')})`).join(',\n'),
  'AS incoming ON DUPLICATE KEY UPDATE email=incoming.email,password_hash=incoming.password_hash,display_name=incoming.display_name,status=incoming.status,security_version=incoming.security_version,failed_attempts=incoming.failed_attempts,locked_until=incoming.locked_until,updated_at=incoming.updated_at;',
  '',
  `INSERT INTO ${targetSchema}.organizations (id,name,type,status,created_at) VALUES`,
  [`(${[quoteSql(school.id), quoteSql(school.name), quoteSql('UNIVERSITY'), quoteSql('VERIFIED'), 'UTC_TIMESTAMP(6)'].join(',')})`,
    ...enterprises.map((e) => `(${[quoteSql(e.id), quoteSql(e.name), quoteSql('ENTERPRISE'), quoteSql('VERIFIED'), 'UTC_TIMESTAMP(6)'].join(',')})`)].join(',\n'),
  'AS incoming ON DUPLICATE KEY UPDATE name=incoming.name,type=incoming.type,status=incoming.status;',
  '',
  '-- Ten demands x one hundred students = 1,000 deterministic student-demand cases.',
  ...Array.from({ length: Math.ceil(caseRows.length / 100) }, (_, chunk) => {
    const rows = caseRows.slice(chunk * 100, (chunk + 1) * 100);
    return [
      `INSERT INTO ${targetSchema}.t4_fixture_cases (case_id,expected_outcome,student_user_id,school_org_id,enterprise_org_id,demand_id,term_id,student_code,display_name,profile_input,demand_criteria,notes) VALUES`,
      rows.join(',\n'),
      'AS incoming ON DUPLICATE KEY UPDATE expected_outcome=incoming.expected_outcome,student_user_id=incoming.student_user_id,school_org_id=incoming.school_org_id,enterprise_org_id=incoming.enterprise_org_id,demand_id=incoming.demand_id,term_id=incoming.term_id,student_code=incoming.student_code,display_name=incoming.display_name,profile_input=incoming.profile_input,demand_criteria=incoming.demand_criteria,notes=incoming.notes;',
      '',
    ].join('\n');
  }),
].join('\n');

writeFileSync(join(outDir, 'T4-02_matching_fixtures.csv'), `\uFEFF${csvRows.join('\r\n')}\r\n`, 'utf8');
writeFileSync(join(outDir, 'T4-02_seed.sql'), `${sql}\n`, 'utf8');
console.log(`Generated ${studentCount} students, ${enterpriseCount} enterprises, ${enterpriseCount} demands, ${caseRows.length} cases.`);
