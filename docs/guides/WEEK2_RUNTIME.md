# Chạy và kiểm tra các luồng tuần 2

Đọc cùng [Docker local](LOCAL_DEMO.md) và [phạm vi kiểm chứng tuần 2](../testing/WEEK2_ACCEPTANCE.md). Worktree tuần 2 chưa được merge/deploy vào Docker dùng hằng ngày. Giữ database, mật khẩu và volume hiện có; không bật seed/importer trên database chung.

## Kiểm chứng tự động bằng môi trường riêng

Yêu cầu Java 21, Docker, Node/npm, Chromium của Playwright và Python dependencies theo scripts/contracts/requirements.txt. Từ repository:

```powershell
.\mvnw.cmd '-Dfrontend.e2e=true' verify
Push-Location web-demo
npm.cmd run build
Pop-Location
python scripts/contracts/build_contract_bundle.py --check
python scripts/contracts/verify_contracts.py --output target/contract-verification.json
python scripts/contracts/test_bundle_portability.py
python scripts/contracts/test_audit_registry.py
```

Maven tạo MySQL Testcontainers port động; MentorOnboardingIT tạo Mailpit riêng. Không cấu hình datasource trỏ database chung khi chạy test. Browser suites chạy tuần tự do cùng dùng Vite port 5178. Không chạy thêm Playwright song song với Maven. CorePlatformIT tự truyền fixture cho các test Student/activation/reset; gọi toàn bộ `npm run test:e2e` độc lập thiếu fixture sẽ thất bại.

Chạy riêng tuần 2 khi cần:

```powershell
.\mvnw.cmd '-Dit.test=Week2WorkflowIT,MentorOnboardingIT' '-Dfrontend.e2e=true' verify
```

Log JUnit ở target/surefire-reports và target/failsafe-reports. Chromium tuần 2 ghi target/week2-browser.log và target/week2-browser-results.xml. Đây là dữ liệu synthetic test, không là acceptance của người dùng thật.

## Luồng và nơi bắt đầu

1. **School delegation:** admin bootstrap operational có decisionRef bắt đầu DEMO-; cấp cho SCHOOL_STAFF hiện hành, không tự cấp cho mình. Grant con phải là subset permission/field/time của một parent hợp lệ. Revoke parent làm mất quyền descendants dù row con còn active; session cũ bị invalidation.
2. **Student:** Thông tin chung → Kỹ năng và mức hoàn thiện. Canonical skill lấy từ taxonomy; custom skill không được suy thành canonical MATCH. Lưu rồi reload để đọc dữ liệu backend.
3. **Matching:** trường có delegation gọi evaluate bằng demandId và versions. Server đọc criteria T5 và dataset T2, không nhận kết quả MATCH từ browser. Chỉ nhánh school được mở; enterprise aggregate giữ đóng.
4. **Cooperation:** doanh nghiệp lưu Demand nháp → Gửi đề nghị hợp tác. Một transaction tạo Cooperation, validate/READY Demand và SUBMITTED. Trường mở Hợp tác doanh nghiệp, xem nội dung rồi approve/request-info/reject; doanh nghiệp sửa/resubmit hoặc reopen hồ sơ rejected.
5. **Proposal/invitation:** trường nominate từ run hiện hành và student đã schoolApproved. Gửi lời mời cho reserved item; student vào Lời mời & nguyện vọng (tab Lời mời) để ACCEPT/DECLINE. Trường không phản hồi thay sinh viên.
6. **Consent/share:** student chọn fields/expiry độc lập, revoke hoặc regrant dùng lineageVersion. Share cần invitation ACCEPTED và consent hiện hành. Enterprise chỉ nhận whitelist giao giữa policy/share/consent/request; revoke chặn lần đọc tiếp theo. Regrant không phục hồi share cũ.
7. **Mentor:** HR chọn phân công/nhóm rồi mời email. Tài khoản ACTIVE tự chấp nhận tại Lời mời hướng dẫn; người mới nhận link T1 qua email để tự tạo tài khoản và password. T3 không giữ password/token. Các quyền được sinh sau owner acceptance và scope check lại tại thời điểm đó.
8. **Task:** Mentor chọn phân công, đợt và nhóm trong Không gian thực tập rồi giao việc. Student vào Nhiệm vụ & sản phẩm để xem board/lịch và bắt đầu làm; báo cáo tuần ở Báo cáo & đánh giá. Case phải IN_PROGRESS và Placement ACTIVE. SUBMITTED/COMPLETED được điều khiển bởi submission/review ở tuần 3, không PATCH tùy ý.

## Các API mới chính

Mọi mutation dùng session+CSRF và Idempotency-Key UUID; request body đóng, expectedVersion để CAS. Organization header/context assignment dùng đúng API hiện có; ID trong body không chứng minh quyền.

| Nhóm | Route |
|---|---|
| Delegation | POST /api/v1/admin/school-delegations; POST /api/v1/school/terms/{termId}/delegations; DELETE /api/v1/school/delegations/{id} |
| Matching | POST /api/v1/matching/evaluate; GET /api/v1/matching/runs/{id}/details; POST /api/v1/matching/runs/{id}/nominate |
| Cooperation | POST /api/v1/enterprise/demands/submit-with-cooperation; POST /api/v1/enterprise/cooperations; PATCH /api/v1/{school\|enterprise}/cooperations/{id}/... |
| Detail UI | GET /api/v1/{school\|enterprise}/cooperations/{id} — projection bổ sung gồm demand/cooperation/feedback; không đổi mutation contract |
| Proposal | GET /api/v1/school/cooperations/{id}/capacity hoặc /proposals; POST /api/v1/school/proposals/{id}/invite |
| Student invitation | GET /api/v1/invitations/me; GET /api/v1/invitations/{id}; POST .../{id}/decision hoặc /withdraw |
| Consent | GET/POST /api/v1/students/me/consents; POST .../{id}/revoke |
| Assignment/Mentor | POST /api/v1/t3/coordinators/confirm; POST /api/v1/t3/hr-assignments; POST /api/v1/t3/groups/{groupId}/mentors; DELETE /api/v1/t3/context-assignments/{id}; POST /api/v1/t3/mentor-invitations; GET .../me; POST .../{id}/accept hoặc /revoke |
| Task | PATCH /api/v1/t3/assignments/{id}/status; GET /api/v1/t3/calendar?from=...&until=... (khoảng [from,until), tối đa 62 ngày) |

Verification UI chưa upload file mới: File Service T1-06 ở tuần 3. Không submit nếu thiếu evidence hợp lệ. Không có expiry worker mới; guard từ chối lời mời PENDING khi serverNow ≥ expiresAt dù state lưu chưa chuyển EXPIRED. Invitation đã ACCEPTED không tự hết hạn theo deadline phản hồi; proposal reservation có TTL riêng 24 giờ.
