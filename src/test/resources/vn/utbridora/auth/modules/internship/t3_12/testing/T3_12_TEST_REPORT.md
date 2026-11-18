# T3-12 — Báo cáo kiểm thử DEV

Owner: DEV07 / Lại Thái Thuận. Hoàn tất kiểm thử: **15/11/2026**, Asia/Bangkok.
Nhánh `feature/t3-week4`, base `8c39916`; thay đổi DEV07 chưa commit.
QA03, Lead và CI của commit sau push chưa xác nhận.

## Kết luận và phạm vi

UI Mentor rubric/DRAFT/submit/history, School publish/revision/policy, HR visibility
và Student result đã tích hợp backend thật DEV05/DEV06. Demo bốn vai trò chạy qua
session/CSRF/HTTP thật trên Spring Boot và MySQL 8.4 riêng, không mock API runtime.
Không có thay đổi Java production, SQL migration, DTO, permission registry hoặc contract.

Đã đọc Plan dòng 64 và đối chiếu lại `docs/handoffs/T3_TUAN3_BAN_GIAO_TUAN4.md`.
Các target tuần 4 trong handoff cũ nay được xác minh bằng code và runtime, không
suy từ model/fixture thành API đã chạy. Không bổ sung Case closure/PlacementCompleted.

## Kết quả cuối

- **Java unit: 1.333 PASS, 1 SKIP**, tổng 1.334; 0 FAIL/ERROR. Chạy ngày 14/11.
  Skip duy nhất là `PrivateFileStorageTest`: Windows không cho tạo symlink trong
  môi trường test. Không có ca T3-12 bị skip.
- **Java integration chọn lọc: 56 PASS**, 0 FAIL/ERROR/SKIP sau lần chạy sửa lỗi test.
  T312EvaluationBrowserIT: 1; T309WorkflowBrowserIT: 1; T310EvaluationRuntimeIT: 15;
  T310SchemaUpgradeIT: 1; T311GradeRuntimeIT: 20; T311SchemaUpgradeIT: 1;
  Week4ConvergenceIT: 13; Week4MigrationCollisionIT: 3; Week4SchemaReferenceIT: 1.
- **Frontend: 89 PASS**, 0 FAIL/SKIP: 74 ca UI/unit trong cấu hình browser và
  15 ca unit riêng. T3-12 có 12 ca UI + 4 unit; các ca còn lại là hồi quy T3-03,
  T3-09 và student terms. Chạy cuối ngày 15/11.
- **Browser runtime: 7 PASS**, 0 FAIL/SKIP: 3 ca T3-12 chạy cuối ngày 15/11,
  4 ca T3-09 chạy ngày 14/11. Hai Java harness phía trên bao bọc 7 ca này;
  không cộng 7 vào 56 để gọi là 63 integration độc lập.
- **TypeScript, typecheck tests và Vite build: PASS**, ngày 15/11.
  Java compile/package thành công trong lượt Maven ngày 14/11; rerun T3-12 ngày
  15/11 kết thúc `BUILD SUCCESS`. Vite còn cảnh báo chunk khoảng 599 kB và module
  vừa static/dynamic import; không phải lỗi build, chưa tối ưu bundle trong task này.
- **Contract 868/868 PASS**, bundle `--check` PASS. Audit registry: 3 tests PASS;
  portability: 1 test PASS; fixture T4: 11 static expectations PASS.
- `git diff --check` PASS. Không có merge conflict, không commit/push/deploy.

Kết quả integration là tổng hợp theo class: 55 ca backend/hồi quy đã PASS ngày
14/11 và 1 harness T3-12 rerun PASS ngày 15/11. Không ghi rằng nguyên lệnh verify
56 ca đã PASS trong một lần. Code backend không đổi giữa hai lần này.

## Luồng thật đã kiểm chứng

1. Mentor chọn Case từ nhóm được phân công, đọc rubric version 1, nhập trọng số
   40/60 và điểm 5/3. Preview/server cùng 76.00; tạo DRAFT và nộp.
2. HR và School cùng bật nhưng DRAFT/SUBMITTED chưa publish: Student nhận đúng
   `HIDDEN, evaluation=null, reasonCode=NOT_AVAILABLE`; raw response không có total100.
3. School publish: Student thấy 76.00, reload vẫn đúng. Request Student không gọi
   API evaluations/rubric/policy để lấy điểm.
4. School tạo revision; Mentor sửa 1/1 thành 20.00. Trong DRAFT và SUBMITTED mới,
   Student vẫn thấy 76.00. Sau publish mới chuyển thành 20.00.
5. HR CASE=false version0 ghi đè org/term=true, Student HIDDEN. CASE=true và
   School bật thì VISIBLE; School tắt/bật cũng ẩn/hiện đúng.
6. Mentor đọc history version1 vẫn 76.00, không có nút sửa bản lịch sử.
7. Revision tiếp theo upload PDF thật, đúng EVALUATION/EVIDENCE, giữ PENDING;
   lưu draft được nhưng submit trả FILE_NOT_CLEAN. Student vẫn chỉ thấy bản
   published 20.00, không thấy file của draft.
8. Student ngoài scope nhận 404 không chứa total100; logout xóa dữ liệu riêng tư.
9. Java truy DB xác nhận chỉ một evaluation root, snapshot v1 vẫn 76.00,
   published pointer là 20.00 và current revision mới là DRAFT.

Fixture UI bổ sung: 409 giữ nội dung và dùng version mới sau refresh; retry đúng
payload/key khi mất phản hồi; không sửa khi kết quả request chưa chắc chắn; DTO
numeric/CSRF/context; history sai version bị đóng; không lộ payload DRAFT/HIDDEN;
đổi Case, session bị thu hồi và phân công hết hạn xóa dữ liệu; HR nhóm chỉ CASE;
School căn cứ 1–120 ký tự; bố cục 320px và điều hướng bàn phím.

Backend DEV05/DEV06 được chạy lại để kiểm guards, policy triple gate, version,
idempotency, rollback/audit và schema. Không dùng fixture UI thay bằng chứng backend.

## Lỗi trong quá trình làm và xác nhận lại

- Kết nối Docker ban đầu thất bại trong Java. Dùng Docker Desktop đang chạy và
  `DOCKER_HOST=npipe:////./pipe/docker_engine` cho tiến trình test; không sửa DB chung.
- Một số locator test dùng label exact cho select chứa các option hoặc dùng text
  xuất hiện ở cả textarea và phần chỉ đọc. Chuyển sang combobox theo accessible
  name và khoanh region đúng. Không hạ expected score/status/quyền để làm PASS.
- Tách đồng bộ lưu policy khỏi thao tác click: đợi PUT và dữ liệu đã lưu. Sửa UI
  để policy CASE=false chưa tồn tại có thể lưu với expectedVersion=0.
- Lượt `target/t3-12-verify.log` ngày 14/11 kết thúc FAILURE do locator T3-12;
  55 ca integration còn lại PASS. Giữ log đỏ, sửa test rồi chạy lại T312 harness
  trong `target/t3-12-runtime-final.log`: 3 browser PASS và BUILD SUCCESS.
- Lượt frontend trước có 73 PASS/1 FAIL do text locator trùng. Lượt cuối
  `target/t3-12-frontend-final.log` đã PASS 74/74; không dùng report cũ làm kết quả cuối.

## Minh chứng và tái chạy

- [Manifest nguồn, hash và số liệu](../evidence/T3_12_VERIFICATION.json).
- [Java unit](../evidence/T3_12_JAVA_UNIT_RESULTS.xml),
  [integration](../evidence/T3_12_INTEGRATION_RESULTS.xml),
  [frontend](../evidence/T3_12_FRONTEND_RESULTS.xml),
  [runtime browser](../evidence/T3_12_RUNTIME_BROWSER_RESULTS.xml).
- [Contract](../evidence/T3_12_CONTRACT_RESULTS.json).
- [Student VISIBLE](../evidence/T3_12_STUDENT_VISIBLE.png),
  [Student HIDDEN](../evidence/T3_12_STUDENT_HIDDEN.png),
  [Mentor file PENDING](../evidence/T3_12_MENTOR_PENDING_EVIDENCE.png).
- [Lệnh chạy lại và API](../guides/T3_12_EVALUATION_UI.md),
  [QA cases và lệnh commit/push](T3_12_QA_HANDOFF.md).

JUnit lưu tên test/count/result nhưng loại properties, stdout/stderr và stack trace
để không đưa environment vào Git. Raw log/JUnit nằm trong target (gitignored);
manifest giữ hash nguồn chuẩn hóa LF và hash raw report/log để đối chiếu.

## Giới hạn cần bàn giao

- Scanner T1 worker chưa có trong baseline tuần 4 này. V40 hội tụ schema, không
  chạy antivirus. Test PENDING thật không được thay bằng SQL gán CLEAN. Luồng
  submit không đính kèm file đã chạy; luồng file CLEAN end-to-end cần T1/QA kiểm tiếp.
- Tài khoản, session, quyền và MySQL thật; prerequisite Placement ACTIVE/Case là
  dữ liệu synthetic có nhãn. Không chứng minh lại quy trình nghiệp vụ T5 hoặc tải
  400/5.000 sinh viên. Không đụng `utbridora-local:8088` hoặc secret `.env.local-demo`.
- Không chạy toàn bộ integration của 5 team/CI trong checkpoint này; phạm vi
  backend 9 class nêu trên. Main/DEV tests được dùng; không đọc/merge nhánh QA.
- T2/QA liên team cần xác nhận consumer kết quả, scanner và rollout/migration
  trên dữ liệu được phép. DEV07 không tự ký nghiệm thu cho các owner khác.
