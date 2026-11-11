import { proposalStates } from './types';

export function ProposalStateFilter({ school, value, disabled, changed }: {
  school: boolean; value: string; disabled: boolean; changed: (value: string) => void;
}) {
  const options = school ? Object.keys(proposalStates) : ['SHARED', 'ENTERPRISE_ACCEPTED', 'DECLINED', 'PLACED'];
  return <label>{school ? 'Lọc trạng thái đề xuất' : 'Lọc trạng thái ứng viên'}
    <select value={value} disabled={disabled} onChange={event => changed(event.target.value)}>
      <option value="">Tất cả trạng thái</option>{options.map(state => <option value={state} key={state}>
        {!school && state === 'DECLINED' ? 'Doanh nghiệp đã từ chối' : proposalStates[state]}
      </option>)}
    </select>
  </label>;
}
