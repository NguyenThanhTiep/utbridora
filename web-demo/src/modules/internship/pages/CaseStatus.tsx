import type { CaseRef } from '../cases';

const states: Record<string, string> = {
  WAITING_START: 'Chờ bắt đầu', IN_PROGRESS: 'Đang thực tập', COMPLETED: 'Đã hoàn thành',
  CANCELLED: 'Đã hủy', TERMINATED: 'Đã dừng'
};

export function CaseStatus({ value }: { value: CaseRef }) {
  return <>
    <p>Mã hồ sơ: {value.caseId}</p>
    <p>Trạng thái: {states[value.state] || value.state}</p>
    {value.state === 'WAITING_START' && <p>Chưa được giao hoặc nộp nhiệm vụ trước khi tiếp nhận thực tập được kích hoạt.</p>}
  </>;
}
