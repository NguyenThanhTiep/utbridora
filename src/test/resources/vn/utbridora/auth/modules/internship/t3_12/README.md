# T3-12 / DEV07 — UI rubric, publish và kết quả sinh viên

Owner: Lại Thái Thuận (DEV07). QA phụ trách: QA03. Triển khai 14–15/11/2026.

**Đã hoàn tất tích hợp và kiểm thử DEV**, sẵn sàng để DEV07 review diff, commit/push
và bàn giao QA. Chưa tạo commit/push/PR, chưa xác nhận CI hoặc nghiệm thu thay QA.
Xem [báo cáo kết quả và giới hạn](testing/T3_12_TEST_REPORT.md).

Nguồn yêu cầu: `docs/plans/UTBridora_Plan.xlsx`, sheet `CAP NHAT CHUNG`, dòng 64.
T3-12 phụ thuộc T3-10/DEV05 và T3-11/DEV06; tiêu chí là demo bốn vai trò trên
backend thật, chấm và công bố điểm, không lộ điểm chưa được phép xem.

Baseline đã nhận: `origin/feature/t3-week4` tại `8c39916`, gồm T3-10 `e480abc`,
T3-11, V40 và main tuần 3. Đọc bàn giao `docs/reviews/T3_10_IMPLEMENTATION_HANDOFF.md`,
`T3_11_IMPLEMENTATION_HANDOFF.md`, CP-WS4 trong Contract Pack v1.1 và trạng thái
phê duyệt tại `docs/contracts/README.md`. Không sửa workbook hoặc snapshot hợp đồng.

Đã đối chiếu lại `docs/handoffs/T3_TUAN3_BAN_GIAO_TUAN4.md` theo yêu cầu tiếp tục.
Các ghi chú “chưa có runtime tuần 4/adapter EVALUATION” mô tả base tuần 3 ngày
13/11; bàn giao DEV05/DEV06 và source tuần 4 hiện tại đã bổ sung các phần đó.
Trạng thái duyệt Contract v1.1 đọc theo quyết định cập nhật ở contracts/README,
không theo nhãn Candidate lịch sử. Giữ nguyên yêu cầu: dùng transport/ledger đã có,
không thêm Case closure/PlacementCompleted, không lấy nhánh QA, không sửa DB chung,
không tự gán CLEAN và không thay QA/Lead ký nghiệm thu.

## Chức năng

- Mentor: rubric có version, nhập điểm/nhận xét, dự kiến điểm HALF_UP; tạo/sửa
  DRAFT, đính kèm minh chứng, submit, đọc lại đánh giá và lịch sử sau reload.
- School: danh sách đánh giá theo kỳ/trạng thái, publish, tạo revision với căn
  cứ và mã lý do; bật/tắt chính sách xem điểm của trường.
- HR: bật/tắt policy doanh nghiệp/kỳ hoặc riêng Case. CASE=false được lưu rõ
  ngay từ version 0 để ghi đè chính sách toàn kỳ đang bật.
- Student: chỉ đọc `GET /api/v1/t3/me/results`, hiển thị bản được phép công bố;
  HIDDEN không giữ điểm/nhận xét/minh chứng trong giao diện. Revision mới chưa
  công bố vẫn hiển thị bản published cũ theo phản hồi backend.

## Vị trí source và hồ sơ task

- UI: `web-demo/src/modules/internship/evaluation.ts`,
  `pages/EvaluationsPage.tsx`, `pages/EvaluationPanels.tsx`.
- Tái sử dụng: `useWorkflowCommand.tsx`, `pages/ReportEvidence.tsx`, `styles.css`;
  entry menu **Đánh giá thực tập** trong `web-demo/src/App.tsx`.
- Unit: `web-demo/tests/unit/internship-evaluation.spec.ts`.
- Fixture UI: `web-demo/tests/internship-week4.ui.spec.ts` và
  `web-demo/tests/fixtures/internship-week4*`.
- Runtime UI: `web-demo/tests/internship-week4-runtime.spec.ts`, được khởi động bởi
  `src/test/java/vn/utbridora/auth/modules/internship/api/T312EvaluationBrowserIT.java`.
- [Hướng dẫn và API](guides/T3_12_EVALUATION_UI.md).
- [Báo cáo DEV](testing/T3_12_TEST_REPORT.md), [bàn giao QA](testing/T3_12_QA_HANDOFF.md).
- Minh chứng chạy kiểm thử: `evidence/` cùng thư mục task này.

Source React và test Playwright giữ trong `web-demo` để toolchain tìm đúng entry.
Tài liệu/minh chứng lưu dưới `src/test/resources/.../t3_12`; test Java đặt dưới
`src/test/java/.../internship/api`. Không chuyển tài liệu vào Java production API.

## Git đã thực hiện

```text
git status --short --branch
git fetch origin
git switch --track origin/feature/t3-week4
git merge-base --is-ancestor 965678a HEAD
```

Nhánh ban đầu `feature/t3-week3` sạch nên không cần stash. Checkout tuần 4 đã chứa
commit tuần 3 `965678a`, không cần merge thêm. Nhánh thật của team có tiền tố
`feature/`. DEV07 tự commit/push; phiên triển khai không tạo commit, PR hoặc push.

## Giới hạn tích hợp

V40 chỉ hội tụ schema, không mang theo worker scanner T1. Upload mới PENDING:
lưu DRAFT được, submit/download phải đợi CLEAN; không tự đổi trạng thái file.
Luồng không đính kèm file không bị phụ thuộc scanner. Runtime test dùng tài khoản
thật trên Spring/MySQL riêng nhưng prerequisite Placement ACTIVE/Case là synthetic;
không thay nghiệm thu kích hoạt Placement của T5. Không thay DB `utbridora-local:8088`.
