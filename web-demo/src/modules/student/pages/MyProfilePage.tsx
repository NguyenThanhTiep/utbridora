import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { ApiError, type Grant, type Me } from '../../../api';
import { EmptyState, Field, Icon, LoadingState, Notice, useAction } from '../../../components';
import { getMyProfile, listTerms, newIdempotencyKey, saveMyProfile, toProfileInput } from '../api';
import { AvailabilityEditor } from '../components/AvailabilityEditor';
import type { DateRange, ProfileInput, SelfProfileView, TermReference } from '../types';
import '../styles.css';

// S02 chỉ sửa số điện thoại và thời gian tham gia; ngành chờ catalog BC12, kỹ năng/dự án/chứng chỉ/CV thuộc S03/S04.
type Draft = { phone: string; availabilityDeclared: boolean; availablePeriods: DateRange[] };
type DraftErrors = { phone?: string; periods: Record<number, string> };
type ProfileState = { status: 'loading' } | { status: 'missing' } | { status: 'error'; message: string } | { status: 'ready'; view: SelfProfileView };

const draftOf = (view: SelfProfileView): Draft => ({
  phone: view.profile.phone ?? '', availabilityDeclared: view.profile.availabilityDeclared,
  availablePeriods: view.profile.availablePeriods.map(({ startDate, endDate }) => ({ startDate, endDate }))
});
// PUT thay toàn bộ hồ sơ: các trường S02 không sửa được chép nguyên từ dữ liệu đã tải.
const inputOf = (view: SelfProfileView, draft: Draft): ProfileInput => ({
  ...toProfileInput(view), phone: draft.phone.trim() || null, availabilityDeclared: draft.availabilityDeclared,
  availablePeriods: draft.availabilityDeclared ? draft.availablePeriods.map(({ startDate, endDate }) => ({ startDate, endDate })) : []
});
function validate(draft: Draft): DraftErrors {
  const errors: DraftErrors = { periods: {} };
  const phoneLength = [...draft.phone.trim()].length;
  if (phoneLength > 0 && (phoneLength < 8 || phoneLength > 20)) errors.phone = 'Số điện thoại cần từ 8 đến 20 ký tự.';
  if (draft.availabilityDeclared) draft.availablePeriods.forEach((period, index) => {
    if (!period.startDate || !period.endDate) errors.periods[index] = 'Nhập đủ ngày bắt đầu và ngày kết thúc.';
    else if (period.startDate > period.endDate) errors.periods[index] = 'Ngày bắt đầu phải trước hoặc trùng ngày kết thúc.';
  });
  return errors;
}
const hasErrors = (errors: DraftErrors) => Boolean(errors.phone) || Object.keys(errors.periods).length > 0;
// Tạo hồ sơ lần đầu (GET 404): đủ 14 trường, chưa khai gì, danh sách rỗng thay vì null; version 0 là đề xuất chờ leader ghi vào contract.
const emptyInput = (termId: string): ProfileInput => ({
  expectedProfileVersion: 0, termId, majorCode: null, skillsDeclared: false, skills: [], customSkills: [],
  availabilityDeclared: false, availablePeriods: [], orientationCodes: [], subjectCodes: [], projects: [], certificates: [],
  cvFileId: null, phone: null
});
// Lỗi server chỉ gắn vào ô khi `field` trùng đúng tên trường ProfileInput; định dạng đường dẫn lồng chưa được contract quy định.
// Không hiển thị nguyên văn message của server: dùng câu cố định theo ô, lỗi khác kèm mã yêu cầu để tra cứu.
const PHONE_FIELDS = ['phone'], AVAILABILITY_FIELDS = ['availabilityDeclared', 'availablePeriods'];
type Rejected = { fields: string[]; requestId: string | null };

export function MyProfilePage({ me, schools }: { me: Me; schools: Grant[] }) {
  const [terms, setTerms] = useState<TermReference[] | null>(null);
  const [termsFailed, setTermsFailed] = useState(false);
  const [termsAttempt, setTermsAttempt] = useState(0);
  const [termId, setTermId] = useState('');
  const [profile, setProfile] = useState<ProfileState>({ status: 'loading' });
  const [profileAttempt, setProfileAttempt] = useState(0);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [errors, setErrors] = useState<DraftErrors>({ periods: {} });
  const [conflict, setConflict] = useState(false);
  const [rejected, setRejected] = useState<Rejected | null>(null);
  const save = useAction();
  const create = useAction();
  const pending = useRef<{ body: string; key: string } | null>(null);
  const saving = useRef(false);
  const generation = useRef(0);
  const phoneErrorId = useId();
  const schoolIds = schools.flatMap(school => school.organizationId ? [school.organizationId] : []).join(',');

  useEffect(() => {
    let active = true;
    setTerms(null); setTermsFailed(false); setTermId('');
    void Promise.all(schoolIds.split(',').filter(Boolean).map(listTerms)).then(pages => {
      if (!active) return;
      const open = pages.flat().filter(term => term.status === 'ACTIVE');
      setTerms(open); setTermId(open.length === 1 ? open[0].termId : '');
    }, () => { if (active) { setTerms([]); setTermsFailed(true); } });
    return () => { active = false; };
  }, [schoolIds, termsAttempt]);

  useEffect(() => {
    if (!termId) return;
    const current = ++generation.current;
    setProfile({ status: 'loading' }); setDraft(null); setErrors({ periods: {} }); setRejected(null); setConflict(false);
    save.setMessage(''); create.setMessage(''); pending.current = null;
    getMyProfile(termId).then(view => {
      if (generation.current !== current) return;
      setProfile({ status: 'ready', view }); setDraft(draftOf(view));
    }, (failure: unknown) => {
      if (generation.current !== current) return;
      // BC11 không nêu trường hợp chưa có hồ sơ; 404 theo C.0 là không tồn tại hoặc ngoài phạm vi.
      if (failure instanceof ApiError && failure.status === 404) setProfile({ status: 'missing' });
      else setProfile({ status: 'error', message: failure instanceof Error ? failure.message : 'Không thể tải hồ sơ. Vui lòng thử lại.' });
    });
    return () => { generation.current += 1; };
  }, [termId, profileAttempt, save.setMessage, create.setMessage]);

  const view = profile.status === 'ready' ? profile.view : null;
  const term = terms?.find(item => item.termId === termId) || null;
  const dirty = Boolean(view && draft && JSON.stringify(inputOf(view, draft)) !== JSON.stringify(inputOf(view, draftOf(view))));
  const change = (next: Draft) => { setDraft(next); setRejected(null); setErrors(previous => hasErrors(previous) ? validate(next) : previous); };
  const rejectedFields = rejected?.fields || [];
  const phoneError = errors.phone || (rejectedFields.some(field => PHONE_FIELDS.includes(field)) ? 'Số điện thoại chưa được hệ thống chấp nhận.' : '');
  const availabilityError = rejectedFields.some(field => AVAILABILITY_FIELDS.includes(field)) ? 'Thời gian có thể tham gia chưa được hệ thống chấp nhận.' : '';
  const otherRejected = rejectedFields.some(field => !PHONE_FIELDS.includes(field) && !AVAILABILITY_FIELDS.includes(field));
  const reload = () => {
    if ((dirty || conflict) && !window.confirm('Thay đổi chưa lưu sẽ được thay bằng dữ liệu mới nhất. Tiếp tục?')) return;
    setProfileAttempt(previous => previous + 1);
  };
  const createProfile = () => {
    if (saving.current || !termId) return;
    const input = emptyInput(termId), body = JSON.stringify(input);
    if (pending.current?.body !== body) pending.current = { body, key: newIdempotencyKey() };
    const key = pending.current.key, current = generation.current;
    saving.current = true;
    void create.run(async () => {
      try {
        const created = await saveMyProfile(input, key);
        if (generation.current !== current) return;
        pending.current = null;
        setProfile({ status: 'ready', view: created }); setDraft(draftOf(created));
        save.setMessage('Đã tạo hồ sơ. Bạn có thể khai thông tin chung bên dưới.');
      } catch (failure) {
        if (generation.current !== current) return;
        if (failure instanceof ApiError && failure.status === 409) {
          if (failure.errorCode === 'REQUEST_IN_PROGRESS') throw new Error('Yêu cầu tạo hồ sơ vẫn đang được xử lý. Vui lòng thử lại sau giây lát.');
          // Hồ sơ vừa được tạo ở nơi khác: tải lại để dùng đúng phiên bản đang có.
          setProfileAttempt(previous => previous + 1);
          return;
        }
        throw failure;
      } finally { saving.current = false; }
    });
  };
  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (saving.current || !view || !draft || conflict) return;
    const found = validate(draft);
    setErrors(found); setRejected(null);
    if (hasErrors(found)) { save.setMessage(''); return; }
    const input = inputOf(view, draft), body = JSON.stringify(input);
    // Cùng nội dung dùng lại key (thử lại sau lỗi mạng/503); nội dung khác phải dùng key mới (C.0 v1.0 dòng 92-94).
    if (pending.current?.body !== body) pending.current = { body, key: newIdempotencyKey() };
    const key = pending.current.key, current = generation.current;
    saving.current = true;
    void save.run(async () => {
      try {
        const saved = await saveMyProfile(input, key);
        if (generation.current !== current) return;
        pending.current = null;
        setProfile({ status: 'ready', view: saved }); setDraft(draftOf(saved));
        save.setMessage('Đã lưu thông tin chung.');
      } catch (failure) {
        if (generation.current !== current) return;
        if (failure instanceof ApiError && failure.status === 409) {
          // REQUEST_IN_PROGRESS được thử lại với cùng key (C.0 v1.0 dòng 93); các mã 409 khác cần tải lại dữ liệu.
          // Chỉ báo hồ sơ đã đổi khi đúng mã PROFILE_STALE; không suy ra từ riêng HTTP 409.
          if (failure.errorCode === 'REQUEST_IN_PROGRESS') throw new Error('Yêu cầu lưu trước đó vẫn đang được xử lý. Vui lòng thử lại sau giây lát.');
          setConflict(true);
          throw new Error(failure.errorCode === 'PROFILE_STALE'
            ? 'Hồ sơ đã thay đổi ở nơi khác. Bản nháp của bạn vẫn được giữ; hãy tải lại dữ liệu mới nhất trước khi lưu.'
            : 'Chưa thể lưu do xung đột với yêu cầu hoặc dữ liệu hiện tại. Bản nháp của bạn vẫn được giữ; hãy tải lại dữ liệu mới nhất.');
        }
        // Thiếu trường/sai định dạng trả 400, lỗi nghiệp vụ trả 422 (C.0 v1.0 dòng 123); cả hai có thể kèm fieldErrors.
        if (failure instanceof ApiError && (failure.status === 400 || failure.status === 422)) {
          if (failure.fieldErrors.length > 0) {
            setRejected({ fields: failure.fieldErrors.map(item => item.field), requestId: failure.requestId });
            throw new Error('Một số thông tin chưa hợp lệ. Kiểm tra lại các ô được đánh dấu rồi lưu lại.');
          }
          if (failure.status === 422) throw new Error('Một số thông tin chưa hợp lệ. Kiểm tra lại dữ liệu rồi lưu lại.');
        }
        throw failure;
      } finally { saving.current = false; }
    });
  };

  const content = terms === null ? <LoadingState label="Đang tải học kỳ…" />
    : terms.length === 0 ? <EmptyState icon="calendar" title="Chưa xác định học kỳ"
      description={(termsFailed ? 'Chưa tải được danh sách học kỳ của trường. ' : 'Trường chưa có học kỳ đang mở. ') + 'Hồ sơ sinh viên được lưu theo từng học kỳ nên chưa thể tải hoặc cập nhật hồ sơ.'}>
      <button type="button" className="secondary" onClick={() => setTermsAttempt(previous => previous + 1)}><Icon name="refresh" size={16} />Thử lại</button></EmptyState>
    : !termId ? <EmptyState icon="calendar" title="Chọn học kỳ" description="Trường có nhiều học kỳ đang mở. Chọn học kỳ để xem hồ sơ tương ứng." />
    : profile.status === 'loading' ? <LoadingState label="Đang tải hồ sơ…" />
    : profile.status === 'missing' ? <EmptyState icon="user" title="Bạn chưa có hồ sơ sinh viên"
      description="Tạo hồ sơ để bắt đầu khai thông tin chung cho học kỳ này. Hồ sơ mới chưa có ngành, kỹ năng hay thời gian tham gia; bạn bổ sung sau khi tạo.">
      <Notice message={create.message} tone="error" />
      <button type="button" disabled={create.busy} onClick={createProfile}>
        {create.busy ? <span className="spinner spinner-light" /> : <Icon name="plus" size={16} />}Tạo hồ sơ</button></EmptyState>
    : profile.status === 'error' ? <EmptyState icon="alert" title="Không tải được hồ sơ" description={profile.message}>
      <button type="button" className="secondary" onClick={() => setProfileAttempt(previous => previous + 1)}><Icon name="refresh" size={16} />Thử lại</button></EmptyState>
    : view && draft && <form className="student-profile-form" noValidate aria-busy={save.busy} onSubmit={submit}>
      <div className="student-readonly">
        <span className="identity-label"><Icon name="graduation" size={15} />Ngành đào tạo</span><strong>{view.profile.majorCode || 'Chưa khai báo'}</strong>
        <p className="field-hint">Chọn ngành từ danh mục của trường sẽ có khi danh mục học vụ sẵn sàng.</p>
      </div>
      <Field label="Số điện thoại" type="tel" autoComplete="tel" required={false} value={draft.phone} disabled={save.busy}
        onChange={phone => change({ ...draft, phone })} {...(phoneError ? { 'aria-invalid': true, 'aria-describedby': phoneErrorId } : {})} />
      {phoneError && <p className="student-field-error" id={phoneErrorId}>{phoneError}</p>}
      <AvailabilityEditor declared={draft.availabilityDeclared} periods={draft.availablePeriods} errors={errors.periods} groupError={availabilityError} disabled={save.busy}
        setDeclared={availabilityDeclared => change({ ...draft, availabilityDeclared })} setPeriods={availablePeriods => change({ ...draft, availablePeriods })} />
      <div className="student-kept"><h3>Dữ liệu được giữ nguyên khi lưu</h3>
        <ul>
          <li>Kỹ năng: {view.profile.skillsDeclared ? `${view.profile.skills.length} kỹ năng chuẩn, ${view.profile.customSkills.length} kỹ năng tự khai` : 'Chưa khai báo'}</li>
          <li>Định hướng: {view.profile.orientationCodes.length}</li>
          <li>Môn học: {view.profile.subjectCodes.length}</li>
          <li>Dự án: {view.profile.projects.length}</li>
          <li>Chứng chỉ: {view.profile.certificates.length}</li>
          <li>CV: {view.profile.cvFileId ? 'Đã có' : 'Chưa có'}</li>
        </ul>
        <p className="field-hint">Các mục này được chỉnh ở màn kỹ năng và năng lực; lưu thông tin chung không thay đổi chúng.</p>
      </div>
      <Notice message={save.message} tone={save.error ? 'error' : 'success'} />
      {otherRejected && <p className="student-error-note">Một số dữ liệu khác trong hồ sơ chưa hợp lệ{rejected?.requestId ? ' (mã yêu cầu: ' + rejected.requestId + ')' : ''}.</p>}
      <div className="button-row">
        <button type="submit" disabled={save.busy || conflict || !dirty}>{save.busy ? <span className="spinner spinner-light" /> : <Icon name="check" size={17} />}Lưu thông tin chung</button>
        <button type="button" className="secondary" disabled={save.busy} onClick={reload}><Icon name="refresh" size={16} />Tải lại dữ liệu mới nhất</button>
      </div>
    </form>;

  return <div className="student-page">
    <section className="panel identity-panel">
      <div className="section-heading"><div><p className="section-kicker">TÀI KHOẢN ĐĂNG NHẬP</p><h2>Thông tin cá nhân</h2>
        <p className="section-description">Lấy từ tài khoản đăng nhập hiện tại của bạn.</p></div>
        <span className="section-icon"><Icon name="user" size={21} /></span></div>
      <div className="identity-grid"><div className="identity-item"><span className="identity-label"><Icon name="user" size={15} />Họ và tên</span><strong>{me.displayName}</strong></div>
        <div className="identity-item"><span className="identity-label"><Icon name="mail" size={15} />Email</span><strong>{me.email}</strong></div></div>
    </section>
    <section className="panel student-term-panel">
      <div className="section-heading"><div><p className="section-kicker">HỒ SƠ THEO HỌC KỲ</p><h2>Hồ sơ sinh viên</h2>
        {term && <p className="section-description">Học kỳ {term.termCode} · {term.startDate} – {term.endDate}</p>}</div>
        {terms && terms.length > 1 && <label className="student-term-select">Học kỳ<select value={termId} onChange={event => setTermId(event.target.value)}>
          <option value="" disabled>Chọn học kỳ</option>
          {terms.map(item => <option key={item.termId} value={item.termId}>{item.termCode}</option>)}</select></label>}</div>
      {content}
    </section>
  </div>;
}
