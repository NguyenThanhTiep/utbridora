# Bàn giao frontend T3 — DEV05 → DEV06 và DEV07

Ngày ghi: 19/11/2026. Chủ bàn giao: DEV05 (Đoàn Phan Vĩnh Phú). Đây là tình trạng code DEV ở checkout hiện tại; không phải QA PASS hay nghiệm thu. Nguồn phân công: [plan frontend T3](../plans/T3_FRONTEND_UIUX_PLAN_2026-11-19.md). Nguồn hành vi: [SRS v1.2.4](../srs/UTBridora_SRS_Tong_The_v1.2.4.docx), [Contract Pack v1.1](../contracts/UTBridora_Contract_Pack_B01_B06_v1.1_Candidate.md) và [trạng thái quyết định contract](../contracts/README.md). Bản bàn giao UI/UX có nhãn **PARTIAL**; bốn màn HR tương ứng T3-UI-01/02 là `1424:17091`, `1424:17161`, `1424:17208`, `1424:17271`.

## 1. Trạng thái nhận code

- Branch bàn giao: `feature/t3-frontend-uiux`; base trước thay đổi DEV05 là `bc9104f`. DEV06/DEV07 kiểm tra commit SHA và trạng thái remote thật bằng `git fetch origin`, `git log -1 origin/feature/t3-frontend-uiux` trước khi nhận. Tài liệu được soạn trước thao tác Git; dòng này không thay kết quả commit/push thực tế.
- Các file cá nhân có sẵn trong working tree như `.work/`, `docs/AI_*.md`, `docs/PROMPT_SETUP_AI.md` và root `package-lock.json` không thuộc gói frontend T3. Không đưa `.env.local`, `.env.local-demo`, mật khẩu hoặc dữ liệu local vào commit/tài liệu/ảnh.
- Gói frontend nằm trong `web-demo` React/TypeScript/Vite; dùng `web-demo/src/api.ts` cho session, CSRF và lỗi; scope T3 lấy từ context assignment đang hiệu lực. Không tạo app hoặc auth riêng.

## 2. DEV05 đã triển khai gì

| Khu vực | Trạng thái code hiện tại | Điểm vào |
|---|---|---|
| Điều hướng HR | Mục `Không gian thực tập` có mũi tên mở bốn màn con: Tổng quan HR, Đợt thực tập, Nhóm thực tập, Người hướng dẫn. Mentor chỉ thấy lối vào nhóm được giao sau khi xác định assignment. | `web-demo/src/App.tsx`, `src/style.css` |
| Phạm vi | Đọc assignment HR/Mentor, chọn phạm vi; một assignment thì tự chọn, thông tin UUID phạm vi thu gọn trong `details`. Đổi context/expiry làm mất dữ liệu không còn quyền. | `web-demo/src/modules/internship/pages/InternshipWorkspace.tsx` |
| T3-UI-01 | Tổng quan số đợt; sau khi chọn đợt, tính số nhóm, Case **đã xếp nhóm**, Mentor từ GET trong scope. Danh sách đợt dạng thẻ; tạo Cohort bằng `POST /t3/cohorts`. Nhận trực tiếp 1–1000 Placement UUID từ nguồn T5 bằng `POST /t3/cohorts/{id}/members:batch`, hiện kết quả ADDED/ALREADY_PRESENT/INVALID. | `pages/InternshipWorkspace.tsx`, `pages/HrCohortActions.tsx` |
| T3-UI-02 nhóm | Preview/confirm chia nhóm tự động qua `POST /t3/cohorts/{id}/groups:auto`; thêm Case vào **nhóm đã có** và chuyển Case sang nhóm khác bằng version Case/nhóm hiện hành; xem Case ID và đọc trạng thái từng Case qua `GET /t3/cases/{id}`. | `pages/HrCohortActions.tsx`, `pages/HrGroupManagement.tsx` |
| T3-UI-02 Mentor | Mời Mentor cho một hoặc nhiều nhóm qua API hiện có, hiển thị trạng thái/ID lời mời; có thể thu hồi **lời mời vừa gửi trong phiên hiện tại** theo version. Luồng Mentor tự chấp thuận lời mời đã có trước đó được giữ lại. | `pages/MentorInvitations.tsx` |
| Hiển thị | Nội dung HR được chia theo menu con, thẻ đợt, chỉ số và khu vực thao tác. Giữ shell/cách điều hướng của web demo; ảnh Figma là tham chiếu chức năng, không dùng dữ liệu số hay người minh họa làm dữ liệu thật. | `web-demo/src/modules/internship/styles.css`, `src/style.css` |

Các mutation DEV05 gửi `X-Context-Assignment-Id`, `Idempotency-Key` và expected version theo DTO; backend vẫn là nơi quyết định quyền, trạng thái và nguồn Placement. T3 không tạo hoặc phê duyệt Placement.

## 3. Kiểm tra đã chạy và giới hạn

- `cd web-demo; npm.cmd run build`: PASS, gồm TypeScript/test typecheck và Vite build. Vite còn cảnh báo bundle lớn và import `api.ts` tĩnh/động; build không fail.
- `npm.cmd run test:unit`: 15 PASS. `npm.cmd run test:e2e -- internship.ui.spec.ts`: 20 PASS sau khi tách điều hướng. Hai ca DEV05 mới kiểm request tạo đợt/intake/preview/confirm và version khi thêm/chuyển Case. `git diff --check`: PASS. Thay đổi nhỏ cuối về reset menu khi đăng xuất được typecheck sau lượt UI test.
- Đây là **UI test với mock API** và DEV build; chưa chứng minh luồng ghi vào backend local với HR/Placement thật, test 401/403/409 đầy đủ, mobile visual review, CI trên commit, QA03 hoặc staging. Sau khi push, kiểm CI trên SHA của commit bàn giao.
- Tổng quan hiện chỉ đếm Case trong nhóm đọc được, **không phải** tổng số Case của đợt. Backend hiện có GET từng Case, chưa có GET danh sách toàn đợt để lọc trạng thái chính xác. Import frontend chỉ nhận danh sách UUID trực tiếp; backend hiện không có luồng upload CSV/XLSX + preview theo file như ảnh minh họa. Không thêm nút tải file giả.
- Backend chưa có GET danh sách lời mời Mentor do HR đã gửi. UI chỉ thu hồi lời mời vừa gửi khi còn DTO trong phiên; sau reload chưa có danh sách để chọn. Việc tạo nhóm trống thủ công cũng chưa có API; form thủ công chỉ thêm/chuyển Case giữa **những nhóm đã tồn tại**. Cần owner backend/Lead xác nhận API trước khi mở rộng contract.

## 4. DEV06 — việc cần làm

### P0: T3-UI-05, phần School và HR policy

1. Đọc `pages/EvaluationsPage.tsx`, `pages/EvaluationPanels.tsx`, `evaluation.ts`, `workflow-api.ts` và `useWorkflowCommand.tsx`. Code hiện **đã có** School publish/revision/policy và HR visibility ở mức cơ bản; audit khoảng trống UX trước khi thêm code, không dựng lại luồng đánh giá.
2. Màn School: danh sách đánh giá theo school/term, lọc trạng thái, chi tiết version/revision, căn cứ publish, lỗi loading/empty/403/409. Chỉ School có quyền `t3:grades:publish` và delegation hợp lệ được publish/tạo revision. Revision DRAFT không thay bản PUBLISHED đang hiển thị.
3. Màn School policy và HR toggle: School điều khiển policy riêng; HR điều khiển visibility theo doanh nghiệp/kỳ hoặc Case đúng assignment. CASE policy ưu tiên org/term. Làm rõ 3 điều kiện hiển thị kết quả: đánh giá PUBLISHED, School enabled, HR enabled. HR không có nút publish; School không chỉnh HR toggle.
4. Đối chiếu màn UI/UX T3-UI-05 (`1433:17305`, `415:17815`, `415:17945`), SRS FR-21/22/24 và CP-WS4; dùng dữ liệu server, không tự tính hoặc lộ điểm HIDDEN. Test cả reload, 409 và đổi context.

### P1: T3-UI-06, School dashboard/Case

1. Kiểm backend/contract hiện có cho School KPI, danh sách Case, chất lượng doanh nghiệp, yêu cầu phối hợp và close Case (NORMAL/EARLY) **trước khi thêm action**. Ghi từng khoảng trống theo route/DTO/owner, không dùng nút giả.
2. Nếu API đủ: làm view School theo school/term delegation, trạng thái Case và điều kiện đóng từ backend; không tự đổi Case hoặc Placement phía frontend. Tham chiếu `pages/CaseEntry.tsx`, UI/UX `1433:17415`, `415:18073`, `415:18203`, SRS FR-23–27.
3. Tách section School/HR policy thành component riêng nếu cần tránh cùng sửa `EvaluationPanels.tsx` với DEV07. Thống nhất interface props/DTO với DEV07 trước khi cùng chạm file.

**Bàn giao DEV06 cần trả:** danh sách màn Đã có/Cần chỉnh/Chặn API, file diff, API/role/scope mỗi action, ảnh desktop/mobile, test build + UI/error 403/409 + kết quả backend thật. Không ghi QA PASS từ self-test.

## 5. DEV07 — việc cần làm

### P0: T3-UI-03/04, Mentor và Student workflow

1. Kiểm `pages/TaskBoard.tsx`, `pages/SubmissionWorkspace.tsx`, `pages/WeeklyReports.tsx`, `pages/ReportEvidence.tsx` và `workflow-api.ts`. Code hiện đã có board/calendar, lịch sử submission, GET nhận xét và báo cáo/revision; làm gap audit so với UI/UX (`1432:17091`, `1432:17284`, `1432:17369`, `1432:17441`, `1433:17091`) rồi cải thiện luồng, không thay API hiện có tùy ý.
2. Mentor đúng nhóm: dashboard, lọc board/calendar/task, giao nhiệm vụ, chi tiết bài nộp v1/v2, nhận xét/verdict và báo cáo tuần. Student: task/submit/report tương ứng, thấy nhận xét sau reload. Mỗi view có loading, empty, error, 409 giữ draft và không rò dữ liệu khi đổi account/context/Case.
3. T3-UI-05 phần Mentor/Student: phối hợp DEV06 ở `EvaluationsPage.tsx`/`EvaluationPanels.tsx`; giữ rubric score/evidence/history, DRAFT/SUBMITTED/PUBLISHED, Student HIDDEN/VISIBLE. Student chỉ dùng `/t3/me/results`; không gọi endpoint Mentor để đọc điểm ẩn. Kiểm phép tính mẫu 40/60 với 5/3 = 76.00 theo server.

### Tích hợp giao diện chung sau DEV06

1. Nhận `App.tsx` và `src/style.css` từ DEV05 làm base menu T3. Giữ mục cha có mũi tên, bốn màn HR và lối vào Mentor theo assignment; nếu thêm màn Mentor riêng, dùng mục con phù hợp thay vì nhét lại tất cả form vào một trang.
2. DEV07 là owner tích hợp file chung `App.tsx`, `web-demo/src/modules/internship/styles.css` và component dùng chung sau review chéo. Kiểm giao diện ở 1440/768/375 px, focus/keyboard, menu mobile, nhãn tiếng Việt, avatar/session và đổi context. Đối chiếu web demo đang deploy với commit/image thật; local code không tự xuất hiện trên URL staging.
3. Phối hợp T2 owner cho Student result và DEV06 cho School/HR policy. QA03 test bốn vai trò trên bản tích hợp, lỗi quyền, lịch sử sau reload, file PENDING/CLEAN và API backend thật.

**Bàn giao DEV07 cần trả:** bảng gap UI/UX → route/component/API, ảnh desktop/mobile, danh sách file chung đã tích hợp, build/unit/UI test và ca backend thật; nêu rõ blocker của owner khác.

## 6. Quy trình nhận bàn giao

1. DEV05 review diff, self-test, commit/push chỉ các file frontend và tài liệu liên quan lên nhánh được team thống nhất; DEV06/DEV07 xác minh kết quả trên remote trước khi pull.
2. DEV06/DEV07 xác nhận commit SHA rồi mới pull hoặc tạo nhánh con từ cùng base. Giữ nguyên code HR DEV05 khi sửa Mentor/School; nếu cần đổi `App.tsx`, `InternshipWorkspace.tsx`, `EvaluationPanels.tsx` hay CSS chung, báo vùng file cho người còn lại và review chéo.
3. Mỗi phần tạo PR theo quy trình team; DEV review, QA03 và CI xác nhận trước khi người có quyền merge. Không push `main`, không tự sửa migration/permission/contract hoặc dữ liệu thật để làm đẹp demo.

## 7. Cách mở local để kiểm tra UI

Tại root chạy `.\run-local.cmd start` trong PowerShell, mở `http://127.0.0.1:8088`. Lệnh `start` build lại frontend/backend từ checkout hiện tại; local database được giữ. Đăng nhập tài khoản thử đúng môi trường, vào `Không gian thực tập` và mở menu con. Khi sửa giao diện cần hot reload: trong `web-demo`, chạy `$env:API_PROXY_TARGET='http://localhost:8088'` rồi `npm.cmd run dev`, mở `http://127.0.0.1:5173`. Không chép mật khẩu từ file env vào issue/ảnh/tài liệu.
