import { Field, Icon } from '../../../components';
import { skillRow, type SkillRow } from './form';

export function SkillFields({ label, rows, change, error }: { label: string; rows: SkillRow[]; change: (rows: SkillRow[]) => void; error?: string }) {
  const update = (id: string, key: keyof SkillRow, value: string) => change(rows.map(row => row.id === id ? { ...row, [key]: value } : row));
  return <div className="cooperation-skills">
    {error && <p className="field-error" tabIndex={-1} aria-invalid="true">{error}</p>}
    {rows.map((row, index) => <div className="cooperation-skill-row" key={row.id}>
      <div className="demand-skill-card-heading"><span>Kỹ năng {String(index + 1).padStart(2, '0')}</span>
        <button className="secondary" type="button" aria-label={`Xóa kỹ năng ${label.toLowerCase()} ${index + 1}`} onClick={() => change(rows.filter(item => item.id !== row.id))}><Icon name="close" size={16} /></button>
      </div>
      <Field label="Mã kỹ năng" aria-label={`${label}: mã kỹ năng ${index + 1}`} value={row.skillCode} onChange={value => update(row.id, 'skillCode', value)} maxLength={64} />
      <Field label="Cấp độ tối thiểu" aria-label={`${label}: cấp độ tối thiểu ${index + 1}`} value={row.minLevel} onChange={value => update(row.id, 'minLevel', value)} type="number" min={1} max={5} step={1} required={false} />
      <Field label="Số năm tối thiểu" aria-label={`${label}: số năm tối thiểu ${index + 1}`} value={row.minYearsPractice} onChange={value => update(row.id, 'minYearsPractice', value)} type="number" min={0} step="any" required={false} />
    </div>)}
    <button type="button" className="secondary" onClick={() => change([...rows, skillRow()])}><Icon name="plus" size={16} />Thêm kỹ năng {label.toLowerCase()}</button>
  </div>;
}
