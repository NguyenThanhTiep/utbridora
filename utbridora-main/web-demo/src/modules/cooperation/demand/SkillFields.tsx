import { Field, Icon } from '../../../components';
import { skillRow, type SkillRow } from './form';

export function SkillFields({ label, rows, change, error }: { label: string; rows: SkillRow[]; change: (rows: SkillRow[]) => void; error?: string }) {
  const update = (id: string, key: keyof SkillRow, value: string) => change(rows.map(row => row.id === id ? { ...row, [key]: value } : row));
  return <div className="cooperation-skills">
    {error && <p className="field-error" tabIndex={-1} aria-invalid="true">{error}</p>}
    <p className="field-hint">{label === 'Bắt buộc' ? 'Tất cả kỹ năng bên dưới đều bắt buộc. Để trống danh sách nếu không yêu cầu kỹ năng.' : 'Kỹ năng ưu tiên hỗ trợ xếp hạng, không thay thế tiêu chí bắt buộc.'} Để trống ngưỡng nếu chỉ cần có kỹ năng.</p>
    {rows.map((row, index) => <div className="cooperation-skill-row" key={row.id}>
      <Field label={`${label}: mã kỹ năng ${index + 1}`} value={row.skillCode} onChange={value => update(row.id, 'skillCode', value)} maxLength={64} />
      <Field label={`${label}: cấp độ tối thiểu ${index + 1}`} value={row.minLevel} onChange={value => update(row.id, 'minLevel', value)} type="number" min={1} max={5} step={1} required={false} />
      <Field label={`${label}: số năm tối thiểu ${index + 1}`} value={row.minYearsPractice} onChange={value => update(row.id, 'minYearsPractice', value)} type="number" min={0} step="any" required={false} />
      <button className="secondary" type="button" aria-label={`Xóa kỹ năng ${label.toLowerCase()} ${index + 1}`} onClick={() => change(rows.filter(item => item.id !== row.id))}><Icon name="close" size={16} /></button>
    </div>)}
    <button type="button" className="secondary" onClick={() => change([...rows, skillRow()])}><Icon name="plus" size={16} />Thêm kỹ năng {label.toLowerCase()}</button>
  </div>;
}
