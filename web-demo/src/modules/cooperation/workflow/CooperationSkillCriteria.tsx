import { useId, useState } from 'react';
import { Icon } from '../../../components';
import type { SkillCriterion } from '../types';

export const SKILL_PREVIEW_COUNT = 6;

export function CooperationSkillCriteria({ label, skills, majorCodes = [], wide = false }: {
  label: string; skills: SkillCriterion[]; majorCodes?: string[]; wide?: boolean;
}) {
  const id = useId(), [expanded, setExpanded] = useState(false);
  const visible = expanded ? skills : skills.slice(0, SKILL_PREVIEW_COUNT);
  return <section className={'school-coop-fact school-coop-skill-card' + (wide ? ' is-wide' : '')}>
    <span className="school-coop-fact-icon" aria-hidden="true"><Icon name="layers" size={18} /></span>
    <h4>{label}</h4>
    {skills.length > 1 && <span className="school-coop-skill-count">{skills.length} kỹ năng</span>}
    {skills.length ? <ul id={id} className="school-coop-skill-tags" role="list" aria-label={label}>
      {visible.map(skill => <li key={skill.skillCode}><span className="school-coop-skill-code">{skill.skillCode}</span>
        {skill.minLevel != null && <small>Mức {skill.minLevel}</small>}
        {skill.minYearsPractice != null && <small>{skill.minYearsPractice} năm</small>}</li>)}
    </ul> : <p>Không yêu cầu kỹ năng cụ thể</p>}
    {skills.length > SKILL_PREVIEW_COUNT && <button type="button" className="secondary school-coop-expand" aria-expanded={expanded} aria-controls={id}
      onClick={() => setExpanded(value => !value)}>{expanded ? 'Thu gọn kỹ năng' : `Xem tất cả ${skills.length} kỹ năng`}</button>}
    {majorCodes.length > 0 && <p className="school-coop-majors">Ngành: {majorCodes.join(', ')}</p>}
  </section>;
}
