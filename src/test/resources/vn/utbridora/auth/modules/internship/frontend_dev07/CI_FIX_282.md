# Sửa CI run 282 — 20/11/2026

Run: https://github.com/NguyenThanhTiep/utbridora/actions/runs/44765731632
Commit lỗi: `d32292e323ad436a4d43c118b5c7378b8cf48bea`.

Annotations của `integration (2)` xác nhận duy nhất một integration test fail
trong 147 test: `Week2WorkflowIT.actualDelegationPolicyMatchingAndCooperationUseOwnerSourcesAndRollback`.
Playwright `week2-runtime.spec.ts` timeout tại trường Tiêu đề vì form giao nhiệm vụ
đang ẩn ở màn Tổng quan Mentor. `verify` fail do phụ thuộc job integration,
không phải lỗi độc lập. Cảnh báo Node/action deprecated không phải nguyên nhân này.

Thay đổi chỉ ở `web-demo/tests/week2-runtime.spec.ts`:

- Chọn Giao nhiệm vụ mới trước khi nhập form.
- Chọn Bảng Kanban sau khi gửi để xác nhận nhiệm vụ đã tạo.
- Chọn Lịch công việc trước khi kiểm tra hạn công việc.

Giữ toàn bộ assertion API thật, phiên/quyền, lưu/reload và các nghiệp vụ liên team.
Không tăng timeout, skip test, force click hoặc sửa workflow để bỏ qua lỗi.

Xác minh local sau sửa:

- `npm.cmd run typecheck:tests`: PASS.
- `Week2WorkflowIT` bật `-Dfrontend.e2e=true`: 1 PASS, 0 fail/error/skip;
  bên trong chạy ca Playwright tuần 2: 1 PASS. Maven BUILD SUCCESS.
- `git diff --check`: PASS. Dùng MySQL Testcontainers riêng.
- Không chạy lại toàn bộ 3 shard local; 6/6 CI cần xác nhận trên commit mới sau push.

```powershell
.\mvnw.cmd -B '-Dit.test=Week2WorkflowIT' '-Dfrontend.e2e=true' test-compile failsafe:integration-test failsafe:verify
```

Bằng chứng đã bỏ properties/stdout/stderr:
[Java](evidence/CI282_WEEK2_JAVA.xml), [Playwright](evidence/CI282_WEEK2_BROWSER.xml).
Log đầy đủ local: `target/dev07-ci282-fix.log`, `target/week2-browser.log`.
