# DEV07 — Rà soát cuối T3 frontend UI/UX, 21/11/2026

Owner: Lại Thái Thuận (DEV07). Branch: `feature/t3-frontend-uiux`.
Source được kiểm tra: `e3de97db0d78dac083d2bd3f1ac1e68fa1f2f0e9`.

Kết luận: các luồng DEV07 và smoke liên kết DEV05/DEV06 dưới đây PASS trên bản này.
Chưa phát hiện lỗi chức năng cần sửa trong phạm vi đã chạy. Không sửa source của
DEV05/DEV06, không commit/push, không merge main. Đây là kiểm chứng DEV bằng
Playwright, API thật và kiểm tra ảnh; không thay nghiệm thu thủ công QA03/Lead.

## Bản cập nhật và dữ liệu local

Working tree ban đầu không có thay đổi tracked; chỉ có folder thiết kế untracked
`docs/plans/UTBridora_UIUX_Dev_Handoff_2026-11-10/`. Không cần stash. Đã giữ nguyên folder này.

```powershell
git fetch origin feature/t3-frontend-uiux
git pull --ff-only origin feature/t3-frontend-uiux
```

Fast-forward từ `574a8a0` tới `e3de97d`; không tạo merge commit. `fetch` cập nhật
tham chiếu remote, `pull --ff-only` đưa nhánh hiện tại tới bản remote khi lịch sử
cho phép tiến thẳng, từ chối tự ghép khi hai nhánh phân kỳ.

Người dùng cho phép backup/reset demo. Đã thử `run-local.cmd reset-demo`; launcher
dừng trước khi sửa DB vì chưa có `.env.local`. Kiểm tra không có volume của project
`utbridora-local`, sau đó chạy `run-local.cmd start`. Launcher tạo cấu hình riêng,
build Docker, migrate tới V43 và nạp 406 tài khoản fixture vào DB mới. Không có DB
local cũ để backup; không tuyên bố đã tạo backup thành công. Ứng dụng ở
`http://localhost:8088`, Mailpit ở `http://localhost:8026`.

Khi người dùng tiếp tục phiên chat, stack local đã dừng. Đã chạy lại
`run-local.cmd start` để khởi động và giữ nguyên dữ liệu, không reset lần nữa.
Kiểm tra cuối: cả 4 container healthy, frontend trả HTTP 200. Lệnh được capture
bằng PowerShell `*>` báo exit 1/NativeCommandError từ dòng tiến độ Docker trên
stderr, trong khi log launcher đi tới thông báo hoàn tất; không lấy riêng exit
của lớp capture này làm bằng chứng build thành công. Trạng thái container và HTTP
đã được xác nhận độc lập; log nằm ở `target/dev07-final-20261121-restart.log`.

Mật khẩu dùng `DEMO_PASSWORD` trong `.env.local`; không đưa env, mật khẩu, Excel
PRIVATE hoặc backup database vào hồ sơ Git. Không dùng mật khẩu snapshot trong Excel.

## Kết quả tự động trên source e3de97d

- `npm run build`: PASS, gồm typecheck source/test và Vite build. Có cảnh báo chunk
  lớn và import tĩnh/động; build exit 0, không phải lỗi chức năng.
- `npm run test:unit`: **15/15 PASS**.
- UI với fixture/API mô phỏng: **76/76 PASS**, không skip: DEV07 UIUX 5,
  week3 UI 27, week4 UI 22, internship UI 22.
- Maven `T309WorkflowBrowserIT,T312EvaluationBrowserIT,T3OperationsIT`:
  **13/13 Java test PASS**, 0 failure/error/skip. Hai test đầu là wrapper chạy
  trình duyệt; T3OperationsIT có 11 test.
- Bên trong các wrapper: **10/10 browser runtime PASS** với Spring/MySQL thật,
  không mock API: T3-09 5, T3-12 4, vận hành 1. Không cộng 10 này thành Java test mới.
- Runtime bao gồm submission v1/revision/v2/ACCEPT, trao đổi, report/revision/review
  sau reload, file PENDING bị chặn, outsider, HR không được review, rubric,
  ba cổng hiển thị, revision giữ published cũ, CASE override, logout, thu hồi quyền
  xem trên focus; vận hành nhận 40 Placement/chia 4 nhóm, phối hợp, chất lượng DN,
  đóng Case trong DB test cô lập.
- CI đúng SHA: [run 44876445587](https://github.com/NguyenThanhTiep/utbridora/actions/runs/44876445587)
  **6/6 success**: contracts, fast, integration (1), (2), (3), verify.
  Đây là CI của commit nguồn, không phải một lần chạy CI mới cho hồ sơ rà soát này.

Lệnh chạy lại (cần Java 21, Node/npm và Docker khả dụng trong PATH):

```powershell
cd web-demo
npm run build
npm run test:unit
npm run test:e2e -- internship-uiux-dev07.spec.ts internship-week3.ui.spec.ts internship-week4.ui.spec.ts internship.ui.spec.ts
cd ..
.\mvnw.cmd -B "-Dit.test=T309WorkflowBrowserIT,T312EvaluationBrowserIT,T3OperationsIT" "-Dfrontend.e2e=true" test-compile failsafe:integration-test failsafe:verify
```

## Walkthrough tự động trên bộ demo local thật

Không dùng route interception. Đăng nhập bằng tài khoản demo trong Excel và mật
khẩu của máy. Các bước sau thao tác UI, trừ GET đối chiếu trạng thái/quyền ghi rõ.

1. **T3-UI-03:** Mentor `cycle.e1.mentor` mở đợt `DEMO-CYCLE-E1`, nhóm
   `8692debe-95fc-41ce-8cdf-0ce47a8512c3` (10 Case). Giao một nhiệm vụ cho student08
   và student09 bằng UI; POST trả 201, form reset. Hai sinh viên nhận đúng nhiệm vụ.
   Lọc Case trên Kanban/lịch, cuộn lịch mobile tới thứ Bảy và mở chi tiết: PASS.
2. **T3-UI-04:** student08 nộp URL v1; Mentor yêu cầu sửa; reload sinh viên vẫn đọc
   nhận xét; nộp v2; Mentor ACCEPT. Reload vẫn có cả URL v1/v2 và nhận xét v1.
   student08 COMPLETED trong khi student09 NEW: hai assignment độc lập.
3. **Báo cáo tuần:** student08 mở báo cáo 16/11/2026 REVIEWED, đọc nhận xét Mentor,
   reload và đọc lại cùng nội dung/người nhận xét/thời điểm: PASS. Thử tạo báo cáo
   23/11 bị từ chối `REPORT_WEEK_INVALID` (ngoài tuần hợp lệ của Case), nội dung form
   được giữ. Không sửa guard hoặc dữ liệu để ép tạo. Luồng tạo/nộp/review/revision
   mới đã PASS trong runtime test cô lập, không tính thử tạo bị từ chối là PASS tạo mới.
4. **T3-UI-05:** đúng Case student09 ban đầu DRAFT 87/100. Sửa rubric chất lượng
   4.5 → 4, lưu ra 83/100; Mentor submit. Sinh viên HIDDEN trước công bố. School
   cùng trường/kỳ công bố, sinh viên thấy 83/100. School tạo revision DRAFT mới,
   sinh viên vẫn thấy published 83. HR CASE policy Tắt → HIDDEN, Bật → thấy lại 83.
   GET sinh viên09 vào kết quả Case student08 trả 404, không có `total100`.
   Đăng xuất xóa nội dung đánh giá khỏi màn hình.
5. **DEV05 smoke:** bốn menu Tổng quan HR, Đợt thực tập, Nhóm thực tập, Người hướng
   dẫn mở được; thanh phân công hiển thị đúng vai trò/kỳ. Không tạo hoặc thu hồi
   lời mời Mentor trên DB demo dùng chung trong lượt này.
6. **DEV06 smoke:** School tra cứu Case student09 → Mở quản lý hồ sơ và kỳ thực tập.
   Đọc dashboard, phối hợp, lịch, quan tâm tuyển dụng, form chất lượng DN và đóng
   Case. Đổi mã Case xóa màn vận hành cũ trước khi tải mới. Không đóng Case demo
   hoặc tạo quyết định giả trong walkthrough; nghiệp vụ ghi vận hành được test
   trong container cô lập.
7. **Responsive:** Kanban và School operations ở 1440/768/375; rubric và HR ở
   desktop/mobile. Không tràn ngang toàn trang. Kanban/lịch có vùng cuộn ngang
   riêng theo thiết kế triển khai. Đã kiểm tra ảnh; full-page screenshot có thể
   đặt thanh fixed tại vị trí cuộn lúc chụp, không dùng ảnh đó để suy ra lỗi sticky.

## Điểm cần gửi lại DEV05/DEV06/Lead

- **Sai ví dụ tài liệu demo:** `docs/guides/PORTABLE_DEMO.md` mô tả student08
  PUBLISHED, student09 SUBMITTED, student10 DRAFT. Excel PRIVATE và GET API ngay
  sau import lại khớp nhau: **student08 SUBMITTED, student09 DRAFT, student10
  PUBLISHED**. Nhờ owner demo sửa ví dụ hoặc nêu rõ bộ snapshot. Không tự sửa
  tài liệu/source của owner. QA chọn theo email + Case + trạng thái thực tế.
- Tài khoản dùng hậu tố `@demo.utbridora.test`. Case ban đầu đối chiếu:
  student08 `0e1a8ef0-6608-43b5-b2d5-94bf6ed7b0a3`;
  student09 `0fe7b1e3-4fb8-42e5-9367-dd95e5e0304d`;
  student10 `c39d7e99-1284-4035-aac7-411f01600abe`.
- Demo không có file đính kèm sẵn; không chứng nhận upload → scanner CLEAN trên
  stack demo mặc định. PENDING bị chặn đã được test. Luồng scanner CLEAN cần môi
  trường T1 bật scanner và QA xác nhận, không gán CLEAN giả.
- Chưa kiểm staging, không chứng nhận pixel-perfect theo Figma PARTIAL hoặc mọi
  tổ hợp tài khoản/thiết bị. QA03 cần thao tác thủ công bốn vai trò, kiểm breakpoint,
  quyền/phân công hết hạn và nghiệm thu trước khi Lead merge.

## Dữ liệu demo đã thay đổi do kiểm thử

- Tạo task `114083d7-a643-4288-a86b-4b9fce8f23f7`, tên
  `DEV07 final demo 2026-11-21`, hạn 28/11 18:00 giờ Việt Nam; giao student08/09.
- student08: bài v1/v2 và review đã lưu, assignment COMPLETED. student09: NEW.
- student09: published 83/100; current revision 2 DRAFT, version 5, vẫn 83/100.
  HR CASE policy cuối cùng Bật. Không thay đổi các hồ sơ thành viên khác.
- Không reset lại sau kiểm thử để giữ dữ liệu và chứng cứ cho người dùng xem.
  Lần sau muốn trở về snapshot mới dùng `run-local.cmd reset-demo` có backup.

## Minh chứng và trạng thái Git

Thư mục này chứa 17 PNG demo, `responsive-checks.json`, JUnit UI/runtime gọn,
log build/unit, tổng hợp Java và `SHA256SUMS.txt`. Không copy log debug Spring,
session/token, env hoặc workbook PRIVATE. Minh chứng các phiên trước giữ nguyên.

Chỉ thêm hồ sơ rà soát và đường dẫn từ README. Chưa stage/commit/push. Có thể gửi
SHA nguồn và CI trên cho QA03, nhưng cần commit/push riêng hồ sơ này nếu muốn QA
đọc minh chứng trực tiếp trên GitHub.
