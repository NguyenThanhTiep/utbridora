import { StudentTabs } from '../components/StudentTabs';
import { InvitationConsentPage } from './InvitationConsentPage';
import { PreferencesPage } from './PreferencesPage';

// S06 (SRS 14.1): một màn "Lời mời & nguyện vọng" gồm 2 tab. Chia sẻ hồ sơ nằm trong tab lời mời
// nhưng vẫn là thao tác riêng, tách khỏi nút nhận lời (luồng 06, PROP-16, C-04).
export type StudentResponseTab = 'invitations' | 'preferences';
const tabs: { id: StudentResponseTab; label: string }[] = [{ id: 'invitations', label: 'Lời mời' }, { id: 'preferences', label: 'Nguyện vọng' }];

export function InvitationsPreferencesPage({ tab, setTab }: { tab: StudentResponseTab; setTab: (tab: StudentResponseTab) => void }) {
  return <StudentTabs label="Lời mời và nguyện vọng" idPrefix="student" tabs={tabs} tab={tab} setTab={setTab}
    render={id => id === 'invitations' ? <InvitationConsentPage /> : <PreferencesPage />} />;
}
