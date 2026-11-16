export function DemandSkillSummary({ required, preferred }: { required: number; preferred: number }) {
  return <dl className="demand-skill-summary" aria-label="Tóm tắt kỹ năng tuyển dụng">
    <div><dt>Kỹ năng bắt buộc</dt><dd>{required}</dd></div><div><dt>Kỹ năng ưu tiên</dt><dd>{preferred}</dd></div>
  </dl>;
}
