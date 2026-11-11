import { useId } from 'react';
import { Field, Icon } from '../../../components';
import type { DateRange } from '../types';

// availabilityDeclared=false thì availablePeriods phải là []; declared=true với [] là khai báo rỗng có chủ ý (BC11 v1.0 dòng 784).
export function AvailabilityEditor({ declared, periods, errors, groupError, disabled, setDeclared, setPeriods }: {
  declared: boolean; periods: DateRange[]; errors: Record<number, string>; groupError: string; disabled: boolean;
  setDeclared: (value: boolean) => void; setPeriods: (value: DateRange[]) => void;
}) {
  const id = useId();
  const update = (index: number, key: keyof DateRange, value: string) =>
    setPeriods(periods.map((period, at) => at === index ? { ...period, [key]: value } : period));
  return <fieldset className="student-fieldset" disabled={disabled}>
    <legend>Thời gian có thể tham gia</legend>
    <label className="student-checkbox"><input type="checkbox" checked={declared} onChange={event => setDeclared(event.target.checked)}
      {...(groupError ? { 'aria-invalid': true, 'aria-describedby': id + '-group' } : {})} />Tôi khai báo thời gian có thể tham gia thực tập</label>
    {groupError && <p className="student-field-error" id={id + '-group'}>{groupError}</p>}
    {declared && <>
      {periods.length === 0 && <p className="field-hint">Chưa có khoảng thời gian nào. Nếu lưu như vậy, hồ sơ ghi nhận bạn đã khai báo nhưng chưa có khoảng thời gian phù hợp.</p>}
      {periods.map((period, index) => {
        const errorId = id + '-period-' + index;
        const invalid = errors[index] ? { 'aria-invalid': true, 'aria-describedby': errorId } : {};
        return <fieldset className="student-period" key={index}>
          <legend>Khoảng thời gian {index + 1}</legend>
          <div className="student-period-fields">
            <Field label="Từ ngày" type="date" value={period.startDate} onChange={value => update(index, 'startDate', value)} {...invalid} />
            <Field label="Đến ngày" type="date" value={period.endDate} min={period.startDate || undefined} onChange={value => update(index, 'endDate', value)} {...invalid} />
          </div>
          {errors[index] && <p className="student-field-error" id={errorId}>{errors[index]}</p>}
          <button type="button" className="text-button" onClick={() => setPeriods(periods.filter((_, at) => at !== index))}><Icon name="close" size={15} />Xóa khoảng {index + 1}</button>
        </fieldset>;
      })}
      <button type="button" className="secondary student-add-period" onClick={() => setPeriods([...periods, { startDate: '', endDate: '' }])}><Icon name="plus" size={16} />Thêm khoảng thời gian</button>
    </>}
  </fieldset>;
}
