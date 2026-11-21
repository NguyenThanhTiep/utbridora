import type { ReactNode } from 'react';
import { Icon } from '../../../components';
import { StudentTabs } from '../components/StudentTabs';
import { StudentIcon } from '../components/StudentUI';
import { InvitationConsentPage } from './InvitationConsentPage';
import { PreferencesPage } from './PreferencesPage';

// S06 (SRS 14.1): một màn "Lời mời & nguyện vọng" gồm 2 tab. Chia sẻ hồ sơ nằm trong tab lời mời
// nhưng vẫn là thao tác riêng, tách khỏi nút nhận lời (luồng 06, PROP-16, C-04).
export type StudentResponseTab = 'invitations' | 'preferences';
// Thanh tab phân đoạn như màn Năng lực; dòng phụ chỉ mô tả mục (không phải số liệu).
const tabs: { id: StudentResponseTab; label: string; icon: ReactNode; meta: string }[] = [
  { id: 'invitations', label: 'Lời mời', icon: <Icon name="mail" size={19} />, meta: 'Lời mời thực tập dành cho bạn' },
  { id: 'preferences', label: 'Nguyện vọng', icon: <StudentIcon name="send" size={19} />, meta: 'Vị trí bạn đã gửi cho nhà trường' }];

export function InvitationsPreferencesPage({ tab, setTab }: { tab: StudentResponseTab; setTab: (tab: StudentResponseTab) => void }) {
  return <StudentTabs label="Lời mời và nguyện vọng" idPrefix="student" tabs={tabs} tab={tab} setTab={setTab} variant="segmented"
    render={id => id === 'invitations' ? <InvitationConsentPage /> : <PreferencesPage />} />;
}
