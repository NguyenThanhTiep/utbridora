# Bàn giao QA — T3-12 / DEV07

Người thực hiện: Lại Thái Thuận. QA theo plan: QA03. Cập nhật: 15/11/2026.
Nhánh: `feature/t3-week4`; baseline `8c39916`. Commit/PR do DEV07 cung cấp sau push.
Đây là bàn giao DEV, chưa xác nhận QA/Lead/CI PASS hoặc sửa trạng thái workbook.

## Phạm vi và điều kiện

Mở menu **Đánh giá thực tập**. Chạy UI với API thật từ nhánh tuần 4.
Chuẩn bị 4 tài khoản riêng School, HR, Mentor, Student; thêm Student khác để kiểm
tra truy cập trái phạm vi. Kỳ/Case/Placement và chuỗi phân công phải có hiệu lực.
Rubric đã cấp có version, đầy đủ tiêu chí và trọng số tổng 100. Không dùng API
Mentor cho màn kết quả Student; không gán CLEAN bằng SQL để lấy bằng chứng antivirus.

Đọc [hướng dẫn UI/API](../guides/T3_12_EVALUATION_UI.md),
[báo cáo thực thi](T3_12_TEST_REPORT.md) và README của task.

## Các ca nghiệm thu đề nghị

1. Mentor mới: GET 204 → chọn rubric → nhập đủ điểm. Điểm trống, âm, >5,
   quá 2 số thập phân và nhận xét >2.000 ký tự không gửi được. 40/60 × 5/3 = 76.00.
2. DRAFT lưu/reload vẫn đúng; PUT gửi expectedVersion, mọi mutation có CSRF/key/context.
   Double click không tạo hai thao tác. Mất phản hồi → thử lại cùng payload/key.
3. Hai cửa sổ sửa cùng bản: 409 giữ nội dung nhập; tải lại version rồi lưu lại.
   SUBMITTED/PUBLISHED và version lịch sử không hiện form sửa.
4. Mentor nộp; Student HIDDEN khi chưa publish dù hai policy đang bật.
5. School chọn đúng kỳ/Case, nhập căn cứ và publish. Không đủ quyền/delegation,
   sai trạng thái/version hoặc không có căn cứ phải bị chặn.
6. Student chỉ VISIBLE khi cả ba điều kiện đúng; kiểm tra JSON HIDDEN có
   evaluation=null, không có total100, rubric, nhận xét, file IDs.
7. School tắt policy → tải lại/focus Student: điểm cũ biến mất. Bật lại → bản đã
   công bố trở lại. HR tắt/bật có cùng hiệu lực theo quyền backend.
8. HR toàn kỳ bật; tạo CASE=false ngay lần đầu (expectedVersion=0) → kết quả HIDDEN.
   CASE=true → VISIBLE. HR nhóm/Case không có thao tác quản lý toàn doanh nghiệp.
9. School tạo revision; Mentor sửa tổng 20.00. Student còn thấy 76.00 trong cả
   DRAFT và SUBMITTED; sau publish thấy 20.00. GET history/version1 vẫn giữ 76.00.
10. Upload PDF/PNG/JPEG đúng binding EVALUATION/EVIDENCE, chọn đúng tiêu chí, lưu draft.
    PENDING/REJECTED không submit/download. Không có file thì submit bình thường.
    Khi tích hợp scanner thật, kiểm tra thêm CLEAN → submit → publish → download
    với từng vai trò; tắt policy và thử lại URL file cũ phải bị từ chối.
11. Sai Case/ngoài trường/khác doanh nghiệp, thu hồi session hoặc hết hạn phân công:
    không đọc được điểm. Chuyển scope/logout loại bỏ dữ liệu và bản nháp của scope cũ.
12. Giao diện 320px, dùng bàn phím, thông báo lỗi/version, reload lịch sử và truy
    cập Case không có task. Không dùng màn hình trống để thay lỗi backend.

## Khi có lỗi

Gửi mã ca, vai trò, trường/kỳ/Case synthetic, bước thao tác, expected/actual,
HTTP status/errorCode/requestId, screenshot đã loại thông tin nhạy cảm và commit
được test. Không đính kèm mật khẩu/session/CSRF. Chỉ định owner: UI DEV07;
rubric/evaluation/history DEV05; publish/revision/policy/result DEV06;
scanner T1; activation/prerequisite Placement T5.

Các ca runtime DEV dùng MySQL Testcontainers riêng và tài khoản thật, nhưng dữ
liệu prerequisite được dựng synthetic. QA tích hợp phải kiểm thêm dữ liệu từ luồng
đầu vào liên team. Không coi kết quả migration V40 là bằng chứng worker scanner.

## Commit/push do DEV07 thực hiện

Sau khi đọc báo cáo PASS và diff, chạy ở repo root. Chỉ stage phạm vi T3-12:

```powershell
git status --short --branch
git diff --check
git add web-demo/src/App.tsx web-demo/src/modules/internship/evaluation.ts web-demo/src/modules/internship/pages/EvaluationsPage.tsx web-demo/src/modules/internship/pages/EvaluationPanels.tsx web-demo/src/modules/internship/pages/ReportEvidence.tsx web-demo/src/modules/internship/styles.css web-demo/src/modules/internship/useWorkflowCommand.tsx
git add web-demo/tests/unit/internship-evaluation.spec.ts web-demo/tests/internship-week4.ui.spec.ts web-demo/tests/internship-week4-runtime.spec.ts web-demo/tests/fixtures/internship-week4.html web-demo/tests/fixtures/internship-week4-harness.tsx
git add src/test/java/vn/utbridora/auth/modules/internship/api/T312EvaluationBrowserIT.java src/test/resources/vn/utbridora/auth/modules/internship/t3_12
git diff --cached --stat
git diff --cached --check
git commit -m "feat(internship): complete T3-12 evaluation and student results UI"
git fetch origin
git merge --no-edit origin/feature/t3-week4
git push origin feature/t3-week4
```

Nếu fetch có code mới và merge thực sự đổi source, chạy lại kiểm thử liên quan
trước push. Nếu merge conflict, dừng để xử lý rồi test; không chạy tiếp push hoặc
force push. Không merge `main` hay nhánh QA ngoài phạm vi bàn giao này.
