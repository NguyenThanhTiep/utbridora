export type SkillCriterion = {
  skillCode: string;
  minLevel: number | null;
  minYearsPractice: number | null;
};

export type DemandInput = {
  schoolOrgId: string;
  termId: string;
  title: string;
  quantity: number;
  description: string;
  location: string | null;
  benefits: string | null;
  deadline: string;
  required: {
    majorCodes: string[];
    skills: SkillCriterion[];
    dateRange: { startDate: string; endDate: string };
    // liên quan đến việc thêm lọc svien theo gpa là MVP: Ngưỡng GPA tối thiểu do doanh nghiệp chọn, null nếu không lọc GPA.
    minGpa: number | null;
  };
  preferred: SkillCriterion[];
  academicCatalogVersion: string;
  taxonomyVersion: string;
};

export type Demand = {
  demandId: string;
  enterpriseOrgId: string;
  state: 'DRAFT' | 'READY';
  version: number;
  criteriaVersion: number;
  validationState: 'VALID' | 'INVALID';
  data: DemandInput;
  cooperationId: string | null;
  softDeletedAt: string | null;
};

// Candidate paging uses `page`; the existing core API uses `number`.
export type DemandPage = {
  content: Demand[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};
