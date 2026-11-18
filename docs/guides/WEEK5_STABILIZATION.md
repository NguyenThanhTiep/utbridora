# Tuần 5 sửa lỗi và bàn giao DEV

Tuần 5 trong Plan là đợt ổn định 12 task T1–T5, không phải lịch mở thêm các
Must sau ngày 35. Căn cứ là SRS tổng thể v1.2.4 và Contract B01–B06 v1.1 đã
được người dùng duyệt. Không sửa hai snapshot này hay trạng thái QA trong Plan.

## Các sửa đổi

- T1-08: backup staging dùng tên tệp duy nhất mỗi lượt, quyền tạm `umask 077`,
  so SHA-256 trước/sau copy, manifest ảnh MySQL/thời gian UTC, và dọn tệp tạm.
  Tệp `.partial` hoặc bản thiếu manifest không phải backup đã xác minh.
- T2-13/T3-15: S08 cho sinh viên mở hồ sơ bằng mã được cấp qua API Case hiện
  có, kiểm đúng owner trước khi đọc kết quả. Không còn phụ thuộc việc đã được
  giao Task. Không thêm API hay dựng kết quả giả.
- T2-14/T3-14: S08 xóa kết quả đã đọc khi trang bị ẩn, chuyển lại focus hoặc
  nhận thông báo hết phiên; khi trở lại phải đọc lại ba cổng từ máy chủ. API
  401/403 đóng kết quả; không hiển thị lại bản cũ sau khi mất quyền.
- T3-15: danh sách kỳ đánh giá dùng `termCode` đúng DTO T1, tránh ô lựa chọn
  trống vì đọc nhầm `code`.
- T3-13/T5-13: outbox kiểm schema nghiêm ngặt, kể cả trường nullable phải có,
  số nguyên và eventId. Payload hỏng vào DLQ `INVALID_SCHEMA` ngay; ACK khác
  event hoặc thiếu applied version giữ PENDING và đi theo retry đã chốt.
  Không có endpoint replay đặc quyền mới hay tự phát lại DLQ.
- T4-09/T4-10/T5-15: khi org/nhu cầu/version/criteriaVersion đổi, UI thống kê
  bỏ release và yêu cầu đang chờ của phạm vi trước. Lần yêu cầu mới dùng đúng
  version hiện tại; ledger k=5 và anti-differencing của backend vẫn quyết định.

## Ma trận kiểm chứng tuần 5

Các bộ dưới đây chạy trên source main cộng sửa đổi tuần 5; không lấy hoặc
chạy các nhánh QA/test tuần 4. Bộ hồi quy hiện hữu được giữ trong workflow.

| Task | Phạm vi DEV và bằng chứng cần đọc |
|---|---|
| T1-08 | `BackupRestoreIT`, CoreSecurity/Authorization/File tests, Week3/Week4 migration suites, manifest restore bên dưới |
| T2-12 | `StudentProfileIT`: schema/skill precision, CV owner/purpose/CLEAN, stale version và lưu toàn bộ aggregate |
| T2-13 | student runtime + S08 thật trong `T312EvaluationBrowserIT`, mở case không có Task; UI regression |
| T2-14 | `EnterpriseProposalIT`: revoke/expiry/policy/term/scope, đọc hồ sơ và Range; S08 đọc lại khi focus |
| T3-13 | `InternshipRuntimeIT`: trùng/trễ, nguồn hiện hành, cancellation trước lần nhận đầu, terminal guard; `PlacementOutboxWorkerTest` |
| T3-14 | T307/T308/T310/T311 runtime: submission v1/v2, stale review, report revisions, audit rollback, rubric/publication gates |
| T3-15 | T309/T312 browser thật HR/Mentor/School/Student; nhãn kỳ, S08 và thao tác bàn phím trong UI suite |
| T4-09 | `StrictClassifierTest`, oracle S1–S11, privacy boundary/differencing trong `EnterpriseProposalIT` |
| T4-10 | `EnterpriseProposalIT`/`LocalDemoMatchingDelegationIT`: owner T2/T5 thật, snapshot/hash, MatchRun và kết quả ghi MySQL |
| T5-13 | Placement race 20 yêu cầu, quota, cancel, event duplicate/terminal và verification guards trong `EnterpriseProposalIT` |
| T5-14 | Demand/Cooperation/share authorization, Consent/current-policy/term và file gates trong `EnterpriseProposalIT` |
| T5-15 | browser Demand→Cooperation→Proposal→Placement, version409/refresh; UI reset release sau cập nhật nhu cầu |

Một test fixture giao diện có route interception chỉ chứng minh hành vi UI;
không dùng thay bằng chứng owner service/MySQL hay coi dữ liệu demo là mock.
Fixture tổ chức/ủy quyền đã xác minh trong runtime test là điều kiện synthetic,
không phải quyết định nhà trường thật.

## Diễn tập khôi phục DB

`BackupRestoreIT` tạo MySQL 8.4 tạm trên runner riêng, migrate tới latest, nạp
fixture synthetic có FK rồi dump bằng `mysqldump --single-transaction --hex-blob`.
Test restore vào schema mới; so SHA-256 từng hàng có length prefix và null
marker, sắp xếp multiset, đối chiếu **mọi bảng**, kể cả Flyway và bảng rỗng.
Digest `SHOW CREATE TABLE` kiểm cả cấu trúc/index/FK; schema nguồn phải giữ
nguyên sau restore. Flyway validate phải PASS và migrate lần hai phải no-op.

Chạy chỉ trên máy/VM/runner tách biệt tài nguyên khỏi demo dùng chung 8088:

```text
./mvnw -B -Dit.test=BackupRestoreIT test-compile failsafe:integration-test failsafe:verify
```

Workflow chính tự khám phá suite này, không cần bật test trên Docker local.
Artifact integration chứa `week5-restore-verification.json`: commit, UTC,
MySQL/Java, digest dump, số hàng/hash từng bảng, thời gian và giới hạn kiểm chứng.
Dump thô chỉ tồn tại trong container tạm; không upload dump hoặc credential.
Đây là rehearsal DEV, không chứng minh restore staging, lịch backup, RPO/RTO,
storage recovery hay hiệu năng production.

## Khi vận hành staging được cho phép riêng

1. Dừng ghi hoặc tạo điểm nhất quán giữa DB và private file storage theo kế
   hoạch vận hành đã được duyệt. Chọn thư mục backup riêng có ACL hạn chế.
2. Chạy `scripts/backup-staging.ps1 -OutputDirectory <thư mục riêng>` trên
   đúng staging được chỉ định. Script chỉ dump/copy/checksum, không tự restore
   hoặc migrate. Lưu image/commit, lineage, Flyway locations và lịch sử checksum.
3. Sao lưu private file storage cùng metadata binding/hash. DB dump đơn lẻ
   không đủ để khôi phục CV/minh chứng. Không đưa backup chứa PII vào Git/CI.
4. Restore DB và file vào bản sao trên môi trường riêng. Xác minh row digests,
   FK, file SHA-256/quyền truy cập, login/session/CSRF/scope/log, Flyway validate
   và no-op. Không restore đè schema đang phục vụ người dùng để “thử”.
5. Nâng clone theo `WEEK4_MIGRATION_RUNTIME.md` với đúng lineage; SQL/history
   cũ giữ nguyên. Chỉ sau báo cáo đạt và quyền triển khai riêng mới nâng thật.

Không tự gọi repair/reset, không đổi cấu hình local/demo, không khởi động lại
8088. Code tuần 5 không có migration mới vì các sửa này không đổi schema.

## Những phần chưa phải nghiệm thu

QA01–QA05/Lead/nhà trường cần retest và ký phần của mình. R30 giữ mốc xóa demo
sau 30 ngày kể từ nghiệm thu và audit tối đa 90 ngày trừ hold đã duyệt; lượt
code này không đặt ngày nghiệm thu giả hoặc bật cleanup trên dữ liệu hiện có.
R31 cần manifest phần cứng cố định, dữ liệu/tải chuẩn và raw timings gồm lỗi;
smoke classifier không được thay kết quả p95 API hay chứng nhận 5.000 hồ sơ.
Staging deployment, load/retention/storage recovery và các Must sau ngày 35
không được tự ghi PASS từ CI xanh. Báo cáo chính xác theo commit nằm trong
checkpoint bàn giao, trạng thái QA của Plan được giữ nguyên.
