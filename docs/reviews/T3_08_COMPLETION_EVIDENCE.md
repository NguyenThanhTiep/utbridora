# T3-08 — DEV06: báo cáo tuần, comment và revision

Owner: Lê Nguyễn Nhật Duy. Phạm vi: FR-17, FR-18, FR-19. Dependency trực tiếp theo
Plan dòng 46 là T3-05. Không chờ hoàn thành T3-07, không triển khai nghiệp vụ task khác.

## Thay đổi ngày 12/11/2026

- V31 tạo bốn bảng Weekly Report/root/revision/review/file refs và cấp quyền
  `t3:comments:write` cho STUDENT/ENTERPRISE; guard scope vẫn bắt buộc.
- Đã fetch và đối chiếu toàn bộ remote refs: V30 thuộc T5, vì vậy không chiếm V30.
  Không sửa checksum V28/V29, không áp migration vào database chung trong phiên này.
- Comment dùng bảng V29 sẵn có; kiểm author/scope, replay, audit và rollback cùng transaction.
- Report create/update/submit/review/revision và đọc lịch sử dùng JDBC thật, CAS/unique,
  source Placement/Case lock, common ledger/audit và quyền Mentor hiện hành.
- File upload/metadata/download dùng T1 FileService và policy Report thật. Draft cho phép
  PENDING; submit yêu cầu CLEAN và binding đúng; không giả CLEAN trong production.
- CP-WS3 v1.1 thêm `GET /api/v1/t3/reports/{reportId}/reviews?revisionNo=N` trả
  `reportId, revisionNo, reviewerUserId, comment, reviewedAt`. Bỏ query lấy current;
  không có review trả404. Cùng scope guard của Report; không đổi ReportDto v1.0.
- `application.yml` bật API mặc định; `T3_WEEK3_HTTP_ENABLED=false` tắt khi cần.

## Cách sử dụng API

1. Student owner tạo Report với `evidenceFileIds: []`, nhận reportId và version1.
2. Nếu có minh chứng: upload `/api/v1/files` với resourceType/purpose WEEKLY_REPORT,
   resourceId là reportId; PATCH Report gắn fileIds. File phải CLEAN trước submit.
3. PATCH bản DRAFT, POST `/submit` với expectedVersion hiện tại.
4. Mentor đúng Case/group POST `/reviews` với expectedVersion và comment.
5. Student đọc `/reviews`, tạo `/revisions` với reasonCode và expectedVersion rồi sửa
   bản mới. GET Report/reviews với revisionNo đọc lịch sử, không ghi đè bản đã nộp.

Mọi mutation dùng session/CSRF và Idempotency-Key; người gửi/người nhận xét lấy từ principal.

## Kiểm chứng và giới hạn

Bằng chứng hoàn tất cuối phiên được cập nhật bên dưới sau lượt verify cuối.
Các IT dùng MySQL 8.4 Testcontainers riêng, fixture identity/Placement ACTIVE rõ ràng.
Trạng thái scan CLEAN trong test là fixture đầu ra máy quét, không phải bằng chứng chạy antivirus.
UI thuộc T3-09; QA03/Lead signoff và deployment không được tự đánh dấu hoàn tất.

Các snapshot preparation ngày 10/11 và handoff đầu ngày 12/11 là lịch sử, không còn là
điều kiện chờ để triển khai DEV06. Yêu cầu mới của người dùng là hoàn tất T3-08.

## Kết quả cuối — 12/11/2026 12:42 +07

**Hoàn tất phần DEV backend T3-08; chưa QA/Lead signoff hoặc deploy.**

- Maven `verify`: BUILD SUCCESS. Lệnh: `.\mvnw.cmd '-Dit.test=T308*IT,MigrationUpgradeIT,T307SchemaUpgradeIT,T307SubmissionRuntimeIT' verify`.
- Surefire: 1.252 ca, 1.251 PASS, 1 skipped, 0 failures/errors. Ca skip là `PrivateFileStorageTest.rejectsSymlinkObjectAndRoot` do môi trường không hỗ trợ điều kiện symlink; không thuộc logic Report.
- Failsafe: 39/39 PASS, 0 failures/errors/skips: T308CommentRuntimeIT 7, T308ReportRuntimeIT 8, T308SchemaUpgradeIT 1; hồi quy MigrationUpgradeIT 3, T307SchemaUpgradeIT 1 và T307SubmissionRuntimeIT 19.
- Contract builder/check và verifier: 868/868 PASS; 347 model, 375 schema. Contract v1.0 không đổi.
- Các ca T3-08 bao phủ lifecycle/revision/history/review read, quyền và thu hồi quyền, source cancellation, CSRF, unique/CAS, replay/concurrency, audit rollback/retry, deadline+1ns round-trip, upload/binding và PENDING/CLEAN/REJECTED enforcement, nâng cấp schema từ V29.
- Đã đóng gói executable Spring Boot jar. Không chạy toàn bộ IT của năm team, UI hoặc antivirus scanner.
- Log: `D:\TeamCodeHKDN\.work\t3-08-verify-online.log`; bản sao trong `target/t3-08-evidence/verify.log` tại checkout chính.
- Source đã đồng bộ về `D:\TeamCodeHKDN\utbridora`, hash các file đồng bộ khớp bản kiểm thử. Backup trước đồng bộ: `D:\TeamCodeHKDN\.work\t3-08-before-final-sync`.
- Nhánh feature/t3-week3 đang có merge chưa commit với commit dependency `000c8e9d4b2f55818c55089ae9747c3bba0b2d6c`. Không commit/push/main merge. Phần staged từ commit dependency có sẵn, không phải task mới DEV06.
- Migration V31 chỉ chạy trong DB test. Khi triển khai nhánh chung, tích hợp V30 của T5 trước V31 theo thứ tự Flyway; không bật out-of-order để bỏ qua việc tích hợp.