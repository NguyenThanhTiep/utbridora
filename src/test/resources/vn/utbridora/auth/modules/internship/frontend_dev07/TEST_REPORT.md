# DEV07 — báo cáo self-test frontend T3

Ngày chạy UI/unit/build: 19/11/2026; hoàn tất runtime và bàn giao: 20/11/2026,
múi giờ Asia/Bangkok. Base `96de49e`, branch
`feature/t3-frontend-uiux`; thay đổi DEV07 chưa commit/push. SHA-256 source được
ghi trong [MANIFEST.json](evidence/MANIFEST.json) để đối chiếu với code đã kiểm tra.
Không gộp kết quả này vào bằng chứng T3-09/T3-12 lịch sử và không ghi QA/CI PASS.

## Kết quả

- **Build PASS:** TypeScript và Vite; [log build](evidence/BUILD_RESULTS.txt).
- **Unit: 15 PASS:** bộ unit hiện có (access/scope, workflow errors/idempotency,
  rubric và Student terms); [log unit](evidence/UNIT_RESULTS.txt).
- **UI với fixture/mock API: 64 PASS**, không skip: 5 ca DEV07 mới, 27 ca workflow,
  12 ca đánh giá, 20 ca workspace/HR. [JUnit UI](evidence/UI_RESULTS.xml).
- **Backend thật: 5 workflow PASS**, không skip: tạo nhiệm vụ qua UI và đọc sau
  reload/lọc Case; URL v1 → yêu cầu sửa → v2 → ACCEPT; bình luận; lịch sử báo cáo;
  upload PENDING bị chặn; outsider bị từ chối và HR chỉ đọc.
  [JUnit workflow](evidence/WORKFLOW_RUNTIME_RESULTS.xml).
- **Backend thật: 4 đánh giá PASS**, không skip: bốn vai trò Mentor/School/HR/Student,
  điểm 40/60 × 5/3 = 76.00 từ server, submit/publish/revision, ba gate và CASE override,
  lịch sử; PENDING không lộ vào kết quả published; outsider/logout; portal Student
  xem Case không có nhiệm vụ và xóa kết quả bị thu hồi khi focus.
  [JUnit đánh giá](evidence/EVALUATION_RUNTIME_RESULTS.xml).
- Hai lớp Java `T309WorkflowBrowserIT` và `T312EvaluationBrowserIT` khởi chạy 9 ca
  browser ở trên; **không cộng hai Java wrapper thành hai ca nghiệp vụ bổ sung**.
- `git diff --check`: PASS. Không sửa backend, migration, API contract hoặc policy owner khác.

Runtime dùng Spring/session/CSRF/JDBC, MySQL 8.4 Testcontainers riêng và private
storage T1; không intercept HTTP để tạo kết quả PASS. Không seed/sửa DB local chung
8088. Docker Desktop đã được mở để chạy bộ kiểm tra.

## Bao phủ UI/UX

- 1440/768/375 px: Kanban, danh sách, giao nhiệm vụ; không tràn toàn trang.
- Shell ứng dụng thật và rubric ở cả ba kích thước; ảnh dùng dữ liệu synthetic.
- Keyboard Enter chuyển sang lịch; mobile menu mở/đóng bằng Escape và trả focus.
- Form giữ nội dung khi chuyển khu vực, sau 409 và khi nhấn Tải lại bảng việc;
  tạo thành công mới reset form. Đổi scope/session thì đóng dữ liệu cũ.
- Case filter của board/lịch gửi tham số API. Tìm tiêu đề được ghi rõ là trong trang.
- Loading/error/empty của board/lịch được tách; số liệu tổng quan không giả thành KPI toàn kỳ.
- Luồng submission/report/rubric được tái sử dụng và kiểm thử hồi quy; không tạo API giả.

## Cách chạy lại

Trong `web-demo`, dùng Node/npm đã cài, Playwright Chromium có sẵn:

```powershell
npm.cmd run test:unit
npm.cmd run test:e2e -- internship-uiux-dev07.spec.ts internship-week3.ui.spec.ts internship-week4.ui.spec.ts internship.ui.spec.ts
npm.cmd run build
```

Tại root, JDK 21, Docker Desktop và `npm.cmd` phải có trong PATH:

```powershell
.\mvnw.cmd -B '-Dit.test=T309WorkflowBrowserIT,T312EvaluationBrowserIT' test-compile failsafe:integration-test failsafe:verify
git diff --check
```

Không chạy riêng runtime Playwright để kết luận PASS: thiếu server/config do Java
harness cấp thì các ca này skip. Không dùng `mvn clean` khi cần giữ tools/bằng chứng
trong `target`. Khi chạy hai bộ UI/runtime đồng thời, dùng `E2E_PORT=5182` cho bộ
fixture để tránh cổng 5178 của harness. Lượt cuối ghi log ở `target/t3-uiux-*.log`.

## Lỗi đã gặp và giới hạn

- Lượt đầu Docker chưa chạy, Java harness không khởi tạo được container. Đã mở
  Docker Desktop và chạy lại trên MySQL riêng thành công.
- Locator chính xác của select lịch cần dùng role `combobox`; đã sửa test.
- Test kết quả sau reload từng bắt response ban đầu rồi nút refresh hủy response đó.
  Đã chờ lần đọc đầu hoàn tất trước refresh; không bỏ assertion response/điểm/quyền.
- Vite vẫn cảnh báo bundle >500 kB và `api.ts` import tĩnh/động. Một ca HR fixture
  cũ chưa mock tasks/calendar nên log có proxy ECONNREFUSED; kiểm tra workflow HR
  qua backend thật đã PASS. Đây không phải bằng chứng mọi request trong fixture thành công.
- Scanner thật upload → CLEAN chưa chạy end-to-end ở gói này. Runtime xác nhận
  PENDING bị chặn; fixture CLEAN không thay thế nghiệm thu scanner T1.
- Chưa nhận SHA UI mới riêng của DEV06; tích hợp theo base đã nhận, giữ School/HR
  policy hiện hữu. T2 owner cần review Student portal; QA03 xác nhận bản tích hợp.
- Không truy cập/xác minh được staging/image/SHA từ công cụ kiểm tra. Không deploy.
- Ảnh ở đây là **sau thay đổi trên local**; không có ảnh baseline staging trước
  thay đổi. Đối chiếu trước/sau code bằng base 96de49e và diff, không gọi ảnh local là staging.

## Ảnh minh chứng

Ảnh shell/rubric 1440 px chụp viewport để tránh sai vị trí sticky sidebar khi
Chromium ghép ảnh toàn trang; ảnh 768/375 px chụp toàn trang. Kiểm tra vị trí
sidebar desktop và độ rộng trang được thực hiện trực tiếp trước khi chụp.

- Shell thật: [1440](evidence/t3-uiux-shell-1440.png), [768](evidence/t3-uiux-shell-768.png), [375](evidence/t3-uiux-shell-375.png).
- Rubric thật: [1440](evidence/t3-uiux-rubric-1440.png), [768](evidence/t3-uiux-rubric-768.png), [375](evidence/t3-uiux-rubric-375.png).
- Kanban fixture: [1440](evidence/t3-uiux-kanban-1440.png), [768](evidence/t3-uiux-kanban-768.png), [375](evidence/t3-uiux-kanban-375.png).
- Danh sách fixture: [1440](evidence/t3-uiux-list-1440.png), [768](evidence/t3-uiux-list-768.png), [375](evidence/t3-uiux-list-375.png).
- Giao nhiệm vụ fixture: [1440](evidence/t3-uiux-create-1440.png), [768](evidence/t3-uiux-create-768.png), [375](evidence/t3-uiux-create-375.png).
- Runtime: [bài nộp/lịch sử](evidence/t3-09-real-submission.png), [báo cáo/lịch sử](evidence/t3-09-real-report-history.png), [file PENDING](evidence/t3-09-real-pending-evidence.png).
- Runtime: [kết quả VISIBLE](evidence/t3-12-student-visible.png), [HIDDEN](evidence/t3-12-student-hidden.png), [minh chứng đánh giá](evidence/t3-12-mentor-evidence.png).

JUnit được bỏ properties/stdout/stderr trước khi đưa vào hồ sơ; không đưa env,
mật khẩu hay log Spring đầy đủ vào commit. [Hướng dẫn QA](QA_HANDOFF.md).
