# T1-06 — kết quả DEV ngày 10/11/2026

Đã hoàn thành phần triển khai DEV của File Service tối thiểu theo Plan tuần 3: private upload, metadata và owner/download guard. Nhánh bàn giao `feature/t1-06-private-files`, base `f1ef2176041c5b0892d5bb107f01d601616755c2`. Code, tests và tài liệu được đóng gói cùng bản DEV T1-06. **Chưa deploy; chưa có QA01/Lead signoff.** Plan/SRS không được sửa thay kết quả nghiệm thu.

Hướng dẫn API, định dạng được mở, cấu hình storage và tích hợp theo team: [T1_06_PRIVATE_FILES](../guides/T1_06_PRIVATE_FILES.md). Evidence có số liệu từng suite, hash report/source và phạm vi từng lượt chạy: [T1_06_VERIFICATION.json](evidence/T1_06_VERIFICATION.json).

Evidence JSON là snapshot trước commit tại `observedAtUtc`; trường `publication` bên trong không phản ánh trạng thái push hiện hành. Trạng thái phát hành và CI sau push phải xem theo commit trong Git/GitHub Actions, không đổi bằng chứng test cũ thành lượt chạy mới.

## Kết quả được quan sát

| Nhóm | Tổng | PASS | Skip | Failure/error |
|---|---:|---:|---:|---:|
| Unit toàn bộ | 508 | 507 | 1 | 0 |
| Integration liên quan, báo cáo mới nhất từng suite | 105 | 102 | 3 | 0 |
| Contract/schema/static fixtures | 868 | 868 | 0 | 0 |

Integration gồm BusinessModulesIT 12 PASS; CorePlatformIT 49 PASS/1 skip; InternshipRuntimeIT 11 PASS/1 skip; StudentProfileIT 17 PASS/1 skip; MigrationUpgradeIT 3 PASS; PrivateFileServiceIT 9 PASS; FileProxyIT 1 PASS. Ngoài 868 checks: audit registry 3/3, bundle portability 1/1 và 11 kỳ vọng SRS T4 độc lập PASS.

Unit skip là ca tạo symlink do quyền Windows. Ba integration skip là browser tests có điều kiện `frontend.e2e=true`; không chạy Chromium trong lượt này. Không đổi TypeScript/UI; Nginx được kiểm tra bằng container và HTTP thật. Kết quả browser/CI tuần 2 ở main là evidence lịch sử, không phải CI của T1-06.

Bảng tổng hợp dùng kết quả mới nhất của từng suite qua các lượt tập trung, **không khẳng định một lượt full browser verify hay remote CI mới đã xanh**. Hồi quy Core/T2/T3/T5/migration đã PASS được dùng lại; sau thay đổi cuối trong File Service/proxy đã chạy lại toàn bộ unit và nhóm File/Proxy. Sau đó chỉ sửa fixture test và chạy lại riêng PrivateFileServiceIT 9/9; không sửa implementation nữa. Gate `failsafe:verify` xác nhận exit 0.

## Bằng chứng hành vi

| Nội dung | Kiểm chứng DEV |
|---|---|
| Multipart qua HTTP/session thật | Đúng bốn fields → 202/PENDING; unknown/duplicate fields, sai key, MIME/extension và binding/purpose bị từ chối; không CSRF → 403, anonymous → 401 |
| Owner/IDOR | Người khác, ADMIN và người cùng enterprise không có nhánh chia sẻ không đọc được metadata/content; Range sai của người ngoài scope vẫn trả 404 |
| Current authority | Membership bị thu hồi chặn GET mới; session securityVersion cũ → 401; account lock chặn retry; kỳ CLOSED chặn retry upload nhưng owner vẫn đọc metadata lịch sử |
| Private bytes | FileDto không có storage key/path; private/no-store, nosniff, attachment; enterprise dùng tên trung tính; file đã xóa → 404 |
| Scan gate và Range | PENDING/REJECTED → 409 trước xử lý Range; CLEAN fixture cho bytes đúng, 206 slice/suffix, single range; multi/invalid range → 416 sau guard |
| Idempotency/concurrency | 20 lời gọi service qua transaction proxy, MySQL và storage thật → một file/binding/operation/audit-success; đổi binary hoặc purpose cùng key → 409; phần stage thừa được dọn |
| Rollback | Ép ghi audit thất bại bằng CHECK constraint trong DB riêng → rollback file metadata/binding/ledger/audit và xóa binary vừa promote; retry sau khi bỏ fault thành công |
| Storage | Kiểm SHA-256/size, cleanup khi quá lớn/stream lỗi/abandon; key traversal bị chặn; bytes bị sửa hoặc thiếu object → 503 |
| T3 owner gate | Tạo Task và start assignment qua service T3 thật, trên Case/Placement prerequisite synthetic; upload chỉ IN_PROGRESS/current Placement; Placement CANCELLED chặn upload; owner vẫn đọc lịch sử |
| Migration | V2→V28 và V27→V28; legacy file metadata/binding/scan state giữ nguyên; Flyway validate/idempotent migrate; không backfill CLEAN |
| HTTP size/proxy | Purpose 10 MiB và multipart 21 MiB vượt ngưỡng → 413; Nginx config thật syntax PASS, file route vượt 21 MiB trả JSON FILE_TOO_LARGE + UUID requestId; API khác giữ 64 KiB |
| Cấu hình | Compose local/staging `config --quiet` exit 0 bằng giá trị synthetic; Dockerfile/private volume được review. Chưa build/deploy image ứng dụng mới |

**CLEAN trong positive download là fixture được đổi trực tiếp trong Testcontainers riêng.** Không có scanner thật hoặc bằng chứng file đã an toàn. Kiểm chữ ký PDF/PNG/JPEG chỉ là kiểm tra định dạng sơ bộ. Test này không chứng minh scan/retry, ZIP/XLSX safety, shared consent/share file access, workflow gắn CV/submission/weekly report hoặc 5 team đã dùng thật.

## Lượt chạy và sửa lỗi

1. Lượt targeted ban đầu phát hiện ký tự điều khiển cần bị chặn trước chuẩn hóa filename; đã sửa. API quá multipart limit làm Tomcat đóng kết nối; đã đặt bounded drain 22 MiB và kiểm lại 413 qua HTTP.
2. Lượt hồi quy rộng: các suite Core/T2/T3/T5/migration PASS; một fixture mới cố gỡ bound roster bị NOT NULL/FK chặn. Không nới constraint. Đổi ca sang kỳ CLOSED hợp lệ, giữ yêu cầu current authority và đọc lịch sử.
3. Unit 508 và FileProxyIT PASS trên implementation cuối. API rerun cuối `PrivateFileServiceIT` 9/9 PASS, `BUILD SUCCESS`. Log tại `target/t1-06-api-final.log`; gate exit 0 tại `target/t1-06-verify-gate.log`. Các log thất bại trước đó vẫn giữ trong target, không đổi oracle để tạo PASS.

Lệnh để tái lập toàn bộ phạm vi liên quan trên database riêng:

```powershell
.\mvnw.cmd -B -ntp '-Ddebug=false' '-Dit.test=PrivateFileServiceIT,FileProxyIT,MigrationUpgradeIT,CorePlatformIT,BusinessModulesIT,StudentProfileIT,InternshipRuntimeIT' verify
```

Lệnh trên là hướng dẫn tái lập tổng hợp, chưa được chạy thành một lượt duy nhất. Các lượt thực tế và report/hash nằm trong evidence JSON. Contract checker dùng quy trình trong [scripts/contracts](../../scripts/contracts/README.md). Manifest chỉ đổi `sourceHashes`; contract v1.0 và SRS DOCX khớp SHA-256 của base, schema/permission/audit CSV không đổi nội dung.

## Phần bàn giao tiếp

T1-07: scanner thật, timeout/retry/rejection audit, validators archive/import và kiểm thử file safety. Các adapter WeeklyReport/Evaluation/import/cohort, shared reader theo consent/share/assignment và workflow attachment của owner vẫn cần triển khai. ZIP chưa mở; không thêm CLEAN bypass để chạy demo. Cần bổ sung orphan/crash reconciliation, quota tổng và backup DB + volume nhất quán trước vận hành rộng.

Docker `utbridora-local`/8088, database, tài khoản, mật khẩu và volume dùng chung được giữ nguyên; không importer/seed/migrate/restart. Chưa khôi phục QA commit `f606b90`. Việc nghiệm thu QA01, Lead và truy cập thực tế của các team theo dõi riêng.
