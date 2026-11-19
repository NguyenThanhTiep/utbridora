# DEV06 — Frontend School/HR, đợt 1

Ngày: 19/11/2026. Nhánh: `feature/t3-frontend-uiux`, base DEV05 `96de49e`.
Phạm vi gồm T3-UI-05 của School/HR và các màn T3-UI-06 có API trên nhánh. Bản local đã sẵn sàng cho QA03 kiểm thử.

**Đối chiếu gói UI/UX:** DEV06 phụ trách **T3-UI-05 phần School/HR** và **T3-UI-06**. T3-UI-05 phần Mentor/Student thuộc DEV07. T3-UI-01/02 thuộc DEV05. Frame `1433:17415` của T3-UI-06 là màn **Theo dõi Case của HR**. Bản cập nhật thêm mục này dưới **Không gian thực tập** và có màn tra cứu riêng cho School. Dashboard tổng hợp và đóng Case của School thuộc các luồng khác trong T3-UI-06.

## 1. Đã triển khai

| Phần | Trước | Hiện tại |
|---|---|---|
| Danh sách School | Danh sách nút trạng thái thô | Thẻ đánh giá, nhãn trạng thái tiếng Việt, lọc trạng thái và Case ID qua API; loading/error/empty; số lượng được ghi rõ là đánh giá trong bộ lọc, không phải KPI toàn bộ Case |
| Chi tiết/công bố | Đã có publish/revision nhưng tải lại làm mất form | Giữ căn cứ và lý do khi reload/version conflict trong cùng hồ sơ; lấy expectedVersion mới; phản hồi thành công; bản DRAFT không có nút publish |
| Policy School/HR | Đã có GET/PUT, reload ghi đè checkbox chưa lưu | Giữ lựa chọn chưa lưu khi reload/409; thông báo trạng thái đã lưu; giải thích CASE override và CASE version 0 chưa tạo ngoại lệ |
| HR · Cấu hình hiển thị kết quả | Ba điều kiện chỉ được mô tả bằng văn bản | Hiện từng điều kiện. Hai trạng thái công bố và chính sách trường ghi “Chưa xác nhận” vì API của HR chỉ trả chính sách doanh nghiệp. Dòng HR hiện giá trị đã lưu hoặc “Kế thừa doanh nghiệp/kỳ” khi chưa có chính sách riêng cho Case. |
| Phạm vi | Đã có role/assignment/delegation từ API | Reset draft khi đổi school/term/policy/context; khóa chọn hồ sơ/bộ lọc khi mutation chưa có kết quả chắc chắn; giữ payload/idempotency key khi retry |
| Chọn kỳ | Có chọn kỳ/nhập mã thủ công | Bổ sung loading/empty; 401 đóng phạm vi. GET `/terms` đòi quyền T5 riêng nên 403/404 vẫn cho nhập mã kỳ; quyền T3 được kiểm tra ở API grade, không suy diễn mất grade delegation từ lỗi danh mục |
| Responsive | Style chung T3 | CSS riêng School/HR; kiểm tra 1440/768/375px |

API nghiệp vụ giữ nguyên: `GET /t3/school/evaluations`, `POST /t3/evaluations/{id}/publish`, `POST /t3/evaluations/{id}/revisions`, GET/PUT School `result-visibility`, GET/PUT enterprise `grade-visibility`. Client giữ session, CSRF, idempotency và context hiện có.

School list cần cả `t3:workspace:read` và `t3:grades:publish` cùng delegation đúng trường/kỳ; School policy cần `t3:result_policy:manage`. HR policy cần `t3:grade_visibility:manage` và assignment còn hiệu lực. Backend tiếp tục quyết định quyền; frontend không tạo permission hoặc tính lại điểm chính thức.

## 2. File và phối hợp DEV07

- `web-demo/src/modules/internship/pages/SchoolGradePanels.tsx`: phần School/HR của DEV06.
- `pages/school-grades.css`: style riêng, không chỉnh `App.tsx` hay `internship/styles.css` của owner tích hợp.
- `pages/EvaluationView.tsx`: tách nguyên phần hiển thị chung; không đổi logic Mentor/Student.
- `pages/EvaluationPanels.tsx`: giữ exports tương thích cho caller/test cũ, Mentor/Student giữ nguyên nghiệp vụ.
- `pages/EvaluationsPage.tsx`: chỉ bổ sung trạng thái tải kỳ và xử lý 401 School.
- `web-demo/src/App.tsx`: đặt đường dẫn Đánh giá thực tập dưới menu Không gian thực tập của Enterprise và School có quyền đánh giá. School có quyền đọc workspace thấy thêm Theo dõi hồ sơ thực tập.
- `pages/SchoolCaseLookup.tsx`: tra cứu Case theo mã qua `GET /t3/cases/{caseId}`. Backend kiểm tra ủy quyền trường/kỳ. Frontend xác nhận trường trả về, hủy yêu cầu và xóa chi tiết cũ khi đổi phạm vi hoặc mã.
- `pages/HrCaseTracker.tsx` và `pages/InternshipWorkspace.tsx` nối màn **Theo dõi Case** cho HR. Người dùng chọn đợt và hồ sơ đã xếp nhóm. API nhận phân công đang chọn. Màn hiển thị trạng thái và phiên bản hiện hành.
- `web-demo/tests/internship-week4.ui.spec.ts`, `tests/fixtures/internship-week4-harness.tsx`: hồi quy và ca riêng DEV06, toàn bộ dữ liệu synthetic.

## 3. Test thủ công lần lượt

Frontend mới và API local: **http://localhost:8088/**.
Theo yêu cầu DEV06, đã copy bundle `web-demo/dist` đã build/test vào container `utbridora-local-frontend-1` đang phục vụ cổng 8088 (assets trước, index sau). Nginx giữ proxy `/api/` tới backend hiện có; không rebuild backend, seed hoặc thay database. Nếu còn thấy giao diện cũ, nhấn **Ctrl+F5**.

Đây là cập nhật file trong container local, chưa rebuild image: restart container vẫn giữ bản này, nhưng recreate container từ image cũ sẽ mất cập nhật. Khi sửa code tiếp, cần build rồi cập nhật bundle hoặc build image frontend theo quy trình local. Không đổi Vite sang 8088 vì cổng này đang do Nginx phục vụ.

1. Đăng nhập tài khoản School có quyền công bố → **Không gian thực tập → Đánh giá thực tập** → chọn trường/kỳ. Nếu không có quyền xem danh mục kỳ T5, mở **Nhập mã kỳ được phân công** và nhập UUID kỳ đúng delegation.
2. **Danh sách:** chọn Chờ công bố, lọc theo mã Case hợp lệ, bỏ lọc bằng cách xóa mã rồi bấm Áp dụng; mở thẻ và kiểm tra điểm, revision/version. Danh sách trống có thể do chưa có đánh giá trong kỳ, không tự tạo dữ liệu.
3. **Công bố:** trên đánh giá SUBMITTED, nhập căn cứ 1–120 ký tự → Công bố điểm. Danh sách chuyển PUBLISHED nếu API thành công; bộ lọc Chờ công bố có thể làm hồ sơ vừa công bố biến mất, chuyển Tất cả/Đã công bố để xem lại.
4. **Revision:** chọn PUBLISHED (hoặc SUBMITTED), nhập căn cứ và mã lý do như `CORRECTION` → Tạo revision. Bản mới DRAFT chờ Mentor sửa/nộp. Student vẫn nhận published cũ nếu các policy còn cho phép.
5. **Policy School:** thay checkbox → Lưu quyền xem điểm → tải lại kiểm tra trạng thái/version đã lưu.
6. **Policy HR:** đăng nhập HR → Không gian thực tập → Đánh giá thực tập → chọn assignment còn hiệu lực. Chọn doanh nghiệp/kỳ hoặc hồ sơ nếu assignment cho phép. Lưu CASE=false phải chặn xem dù org/term=true; School không có điều khiển HR và HR không có publish.
7. **Student:** đăng nhập Student có Case tương ứng. Chỉ nhìn thấy kết quả khi PUBLISHED + School enabled + HR effective enabled. Thiếu một điều kiện thì HIDDEN.
8. **409:** dùng hai tab trong cùng phạm vi; tab A lưu/công bố trước, tab B gửi version cũ. Tab B thấy lỗi, bấm tải lại đánh giá/chính sách; nội dung đang nhập vẫn còn, gửi lại dùng version mới. Nếu trạng thái mới không cho phép thao tác nữa, không có nút ghi tương ứng.
9. **Đổi phạm vi:** đổi kỳ/trường/assignment hoặc hồ sơ khác, không được mang căn cứ/điểm của phạm vi cũ sang.
10. **T3-UI-06:** dùng `school@example.test`, mở **Không gian thực tập → Theo dõi hồ sơ thực tập**, nhập mã Case local `76638346-4ecb-4cac-b101-b39a8c02daba` rồi bấm **Tra cứu hồ sơ**. Kết quả của bộ seed hiện tại là **Đang thực tập**. Đổi sang mã không có trong phạm vi để kiểm tra chi tiết cũ biến mất và API trả lỗi truy cập.
11. **T3-UI-06, frame HR `1433:17415`:** dùng `hr@example.test` → **Không gian thực tập → Theo dõi Case** → chọn đợt `DEMO-W1` → chọn hồ sơ của nhóm `DEMO-G1`. Màn hiện **Đang thực tập**. Chọn lại phân công hoặc đợt để xác minh màn xóa chi tiết cũ. Danh sách lấy từ các nhóm của đợt nên chưa bao gồm hồ sơ chưa xếp nhóm.

Chỉ khi cần chế độ sửa code tự cập nhật, có thể chạy Vite ở cổng riêng (không cần cho việc test bản đã đưa lên 8088):

```powershell
cd D:\TeamCodeHKDN\utbridora\web-demo
$env:API_PROXY_TARGET='http://127.0.0.1:8088'
npm.cmd run dev -- --port 5173 --strictPort
```

**Tài khoản demo hiện tại:** đã chạy `seed-demo` trên Docker local. Dùng `hr@example.test`, `mentor@example.test` hoặc `school@example.test` với `DEMO_PASSWORD` trong `.env.local-demo`. HR đọc được chính sách xem điểm, Mentor có nhóm `DEMO-G1` và một Case, School có ủy quyền tra cứu Case. Bộ seed hiện tại thiếu rubric và ủy quyền công bố/quản lý chính sách điểm của School. Các thao tác ghi điểm chưa được kiểm thử trên bộ demo local này.

## 4. P1 và API còn thiếu

Đối chiếu controller và service trong source checkout, không suy diễn contract thành endpoint đã chạy:

| Khoảng trống | API/DTO liên quan | Owner cần phối hợp | Hành động hiện tại |
|---|---|---|---|
| School đọc lịch sử đánh giá | GET evaluation/history hiện gọi `EvaluationAccess.mentor`; `GradeService.schoolRead` chưa có route School history | T3 backend/DEV06, Lead duyệt contract quyền | Chỉ hiện revision/version hiện tại qua School list; không gọi API Mentor bằng School |
| Dashboard/KPI School | Contract CP-WS5: GET `/t3/school/dashboard`, `WorkspaceSummary`; chưa có controller | T3 backend, School owner | Chưa dựng dashboard hoặc đếm list đánh giá thành tổng Case |
| NORMAL/EARLY close | Contract CP-WS4: POST `/t3/cases/{caseId}/close`, `CloseCase` → `CaseRef`; chưa có controller | T3 backend + T5 consumer, School owner | Chưa dựng nút đóng hoặc tự đổi trạng thái Case/Placement |
| Chất lượng doanh nghiệp | CP-WS5: GET `/t3/enterprise-quality/templates`, POST `/t3/school/enterprise-feedback`; chưa có controller | T3 backend + School owner | Chưa dựng form ghi |
| Coordination issue | CP-WS5: POST/PATCH `/t3/school/issues`; chưa có controller | T3 backend + School/T5 owner | Chưa dựng action giả |
| Case read | GET `/t3/cases/{id}`, School delegation hoặc HR assignment với `t3:workspace:read` | T3 frontend | HR có mục **Theo dõi Case**. School tra cứu theo mã. Hai màn đọc trạng thái/phiên bản hiện hành. Timeline và danh sách mọi Case của đợt còn thiếu API. |

Backend thiếu chỉ được ghi blocker, không tự mở rộng contract/quyền trong PR frontend này.

## 5. Bằng chứng và giới hạn

- `npm.cmd ci`: thành công từ lockfile, không đổi dependency.
- `npm.cmd run build`: thành công; còn cảnh báo bundle lớn và static/dynamic import chung hiện hữu.
- `npm.cmd run test:unit`: 15/15 PASS.
- `npx.cmd playwright test internship-week4.ui.spec.ts`: **22/22 PASS** ở lượt cuối (26,7 giây), gồm 12 hồi quy T3-12 và 10 ca DEV06; có đổi kỳ, 401 khi tải kỳ và T5 term permission fallback. Test đổi kỳ ban đầu dùng selector label không khớp; đã sửa sang combobox theo accessible name và chạy lại đạt.
- `git diff --check`: PASS.
- Bản cập nhật điều hướng và Case: build đạt, **43/43 UI test** đạt qua `internship.ui.spec.ts` và `internship-week4.ui.spec.ts`. Kiểm thử trình duyệt trên Docker 8088 xác nhận Mentor thấy đánh giá và hồ sơ được giao, HR mở được chính sách, School tra được trạng thái Case demo.
- Bổ sung **Theo dõi Case của HR**: build đạt, **22/22 test** trong `internship.ui.spec.ts` đạt. Test kiểm tra yêu cầu đọc gửi đúng assignment và xóa chi tiết khi quyền bị thu hồi. Trình duyệt trên Docker 8088 hiển thị **Đang thực tập** cho Case mẫu của HR.
- Ảnh synthetic desktop/tablet/mobile do test `DEV06 School desktop and mobile layout...` tạo dưới `web-demo/test-results/default/`; không phải ảnh staging, không chứa tài khoản thật.
- Chưa có before/after screenshot của staging, chưa QA03 PASS, chưa push/merge/deploy staging. Đã cập nhật bundle vào container local 8088 theo yêu cầu người dùng. Đợt 1 để DEV06 test màn School/HR trước khi chuyển các phần P1.

### Lượt kiểm tra cuối trên nhánh DEV06

- Trên Docker local cổng 8088, đăng nhập HR rồi mở **Không gian thực tập → Đánh giá thực tập → Phân công đánh giá → HR**. Đã kiểm tra bảng ba điều kiện xuất hiện ở cả phạm vi doanh nghiệp/kỳ và hồ sơ mẫu. Bộ demo 8088 vẫn cần rubric, đánh giá đã nộp và ủy quyền School phù hợp để trình diễn thao tác công bố.
- `T312EvaluationBrowserIT` chạy Spring, MySQL tạm và bốn ca Playwright với dữ liệu riêng. Luồng HR lưu và đọc lại policy qua GET ở cả hai phạm vi, kiểm tra giá trị và version. School công bố, tạo revision và đổi chính sách. Student giữ điểm công bố cũ trong lúc sửa revision, chỉ thấy điểm mới sau lần công bố tiếp theo và mất quyền xem khi tắt một điều kiện. Bộ test cũng kiểm tra tài khoản ngoài phạm vi và minh chứng đang chờ quét.
- Báo cáo runtime và ảnh desktop/mobile nằm tại `target/t3-12-runtime-browser.xml`, `target/dev06-hr-visibility-desktop.png` và `target/dev06-hr-visibility-mobile.png` sau khi chạy test. Dữ liệu trong ảnh là dữ liệu kiểm thử.
- T3-UI-06 sẵn sàng test hai luồng đọc Case của HR và School. Các mục School dashboard, lịch sử Case, chất lượng doanh nghiệp, phối hợp và đóng Case chờ backend theo bảng ở mục 4.
