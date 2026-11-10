# Báo cáo kỹ thuật T1-01

Ngày đối chiếu **05/11/2026**, source HEAD **151f16e**. Đã hoàn thiện bộ contract có schema, registry, ví dụ và lệnh kiểm tra DEV tái lập được. Bản v1.0 đã được người dùng chốt; [v1.1 Candidate](UTBridora_Contract_Pack_B01_B06_v1.1_Candidate.md) là bản hiệu chỉnh kỹ thuật riêng để review diff, chưa tự được phê duyệt. Không đưa lại vòng ký riêng T2–T5 làm điều kiện chặn phần DEV.

## Phạm vi và bằng chứng

Đối chiếu tập trung SRS §6.1–6.8, §7, §20, §22–23, §25.8 và §25.9 với contract A–J và source core liên quan. DOCX được trích xuất đủ 486 paragraph body, 98 bảng; ba ảnh kỹ thuật đã được xem. SRS gốc và Contract v1.0 giữ nguyên raw SHA-256. Không chạy đánh giá lại toàn bộ năm phân hệ hoặc tự sửa chính sách học vụ.

| Nguồn | Vai trò |
|---|---|
| [SRS v1.2.4](../srs/UTBridora_SRS_Tong_The_v1.2.4.docx) | Yêu cầu và baseline Placement/privacy; §25.9 là nguồn B01–B06 |
| [Contract v1.0](UTBridora_Contract_Pack_B01_B06_v1.0_Candidate.md) | Bản bất biến đã được người dùng chốt, hash tại [quyết định](README.md#quyết-định-chốt-contract--05112026) |
| [ApiDtos](../../src/main/java/vn/utbridora/auth/api/ApiDtos.java), [AuthController](../../src/main/java/vn/utbridora/auth/api/AuthController.java), [OpenAPI](../openapi.json) | Body/method/status legacy đã có |
| [V3](../../src/main/resources/db/migration/V3__permissions_and_membership_integrity.sql), [PermissionCatalog](../../src/main/java/vn/utbridora/auth/authorization/PermissionCatalog.java), [AuthorizationPolicy](../../src/main/java/vn/utbridora/auth/authorization/AuthorizationPolicy.java) | 18 permission core và guard hiện hữu |
| [AuditAction](../../src/main/java/vn/utbridora/auth/audit/AuditAction.java), [AuditService](../../src/main/java/vn/utbridora/auth/audit/AuditService.java) | 30 enum và cơ chế audit hiện hữu |
| [Token service](../../src/main/java/vn/utbridora/auth/tokens/AccountTokenService.java), [PasswordPolicy](../../src/main/java/vn/utbridora/auth/core/PasswordPolicy.java), [SecurityConfig](../../src/main/java/vn/utbridora/auth/security/SecurityConfig.java) | Invite 48h, reset 30 phút, password/session/CSRF hiện có |

## Kết quả kiểm tra DEV

| Hạng mục | Kết quả và giới hạn |
|---|---|
| Schema | 374 schema gồm DTO/enum/primitives/container và Input/Value của 62 port/consumer; kiểm meta-schema và mọi $ref nội bộ |
| Ví dụ công bố | 69/69 khớp schema; không chỉ parse JSON |
| Regression và boundary | 795 fixture, gồm missing/unknown field, enum/UUID/version, 0/1/5.000/5.001 row IDs, password UTF-8, email sai cấu trúc, Decimal, file-vs-URL, field mask, privacy projection và audit allowlist |
| Tổng lượt kiểm tra contract | **868/868 PASS**, fail=0; gồm 4 kiểm tra tĩnh, 69 ví dụ và 795 fixture. Đây không phải 868 integration test |
| Registry | 51 permission = 18 core + 33 chưa triển khai; 93 audit action = 30 enum hiện có + 63 chưa triển khai; giữ alias/restriction và sentinel tách biệt |
| Tương thích core | 21 operation OpenAPI hiện hữu vẫn tìm được ở controller; 62 file nguồn/baseline được chụp hash. Không đổi BE/FE runtime, API đang chạy hoặc migration V1–V7 |
| CI | Đã thêm job contracts và khóa dependency checker. Job mới trên GitHub **NOT RUN** vì thay đổi chưa commit/push |
| Runtime nghiệp vụ mới | Import/SMTP outbox, scanner, capability, T2–T5 real adapters, transaction/race/load **NOT RUN** |
| Tester | Không tự xác nhận QA PASS. Kết quả DEV và trạng thái Tester riêng |

Bằng chứng máy đọc: [T1_01_CONTRACT_REVIEW.json](../testing/evidence/T1_01_CONTRACT_REVIEW.json). Công cụ và lệnh chạy: [scripts/contracts](../../scripts/contracts/README.md). Schema/manifest/CSV được tái sinh có kiểm soát, không sinh runtime code hoặc migration.

Core runtime đã được chạy thật trước đó trong cùng phiên, kết thúc 08:44:18 (UTC+7): 28 unit, 49 Failsafe và 14 Chromium PASS; 3 Chromium chỉ UI mock. Bản kiểm tra contract không chạy lại các test đó; source ứng dụng vẫn giữ nguyên, đã đối chiếu hash. [Bằng chứng core cùng phiên](../testing/evidence/T1_01_CORE_REGRESSION.json) được thu từ JUnit/Playwright XML, tách khỏi 868 kiểm tra schema/oracle.

## Phát hiện và phần đã xử lý

| ID | Vị trí nguồn | Kết quả rà soát / xử lý | Trạng thái thực tế |
|---|---|---|---|
| CR-01 | BC11 và G.1 v1.0 | G thiếu note/custom skill và quan hệ giữ dữ liệu khi đổi ngành; K.2 bổ sung field, nullability, ownership, key và projection tương ứng | SPEC CORRECTED trong v1.1; DB T2 chưa triển khai |
| CR-02 | BC24 | ProfileProjection/union skill trước nằm trong văn xuôi; K.1 đặt named schema đóng, kiểm property bằng allowedFields và cấm field dự phòng MVP | Schema/oracle PASS; consent/share guard thật NOT RUN |
| CR-03 | C.0 và CP-AUTH1 | Đặt named schema cho ErrorEnvelope/DomainError, activation opt-in, request capture/eligibility/cooperation; phân biệt lỗi nội bộ với HTTP adapter | Schema PASS; adapter envelope mới chưa triển khai |
| CR-04 | D.1–D.3, CSV nền tảng | Xuất 18+33 quyền, exact alias và restriction; CSV gốc giữ nguyên, manifest kiểm mapping và sentinel | Static PASS; không seed 33 quyền mới |
| CR-05 | E.1–E.4 | Xuất 30+63 action và allowlist từng producer; thêm kiểu rõ cho versionNo/reviewedVersion và schema AuditEventInput | Static/schema PASS; enum runtime vẫn 30, metadata migration mới chưa có |
| CR-06 | CP-AUTH1 / ApiDtos / PasswordPolicy | Legacy password/displayName giữ nguyên; opt-in newPassword có schema riêng; UTF-8 72 bytes và 12 codepoint được kiểm bằng fixture | Legacy code không đổi; activation profile 1.1 chưa có adapter runtime |
| CR-07 | 69 khối JSON A–J | Mỗi ví dụ gắn schema/section/line, có ca thiếu field/field lạ và các boundary riêng | DEV PASS; không phải consumer/producer chạy thật |
| CR-08 | Chữ ký port C/F | Xuất schema tham số nghiệp vụ và Success value cho 62 port/consumer; context được đánh dấu SERVER_ONLY | SPEC / schema PASS; chưa có 62 Java implementation |
| CR-09 | CP-IMPORT1 F.4–5 / G.1 | Làm rõ observedAt là read projection, rowVersion khác claim/invitationVersion, NOT_READY/NOT_REQUIRED không phải outbox state; kiểm counter trên fixture | Oracle PASS; worker và DB race NOT RUN |
| CR-10 | SRS hình 3 và §25.8.1 | Hình rút gọn bỏ ACTIVE/WAITING_START; K.4 chỉ rõ dùng state machine hiệu chỉnh bằng văn bản | Không sửa SRS gốc, không đổi nghiệp vụ |
| CR-11 | T2 physical schema | DECIMAL precision, phân bổ dữ liệu profile theo term và DDL chi tiết phải hoàn thiện trong thiết kế module T2, giữ wire/rule BC11 | Chưa khẳng định Database T2 đã thiết kế/kiểm thử xong |
| CR-12 | R33 / AdminReadController.users | Code vẫn query membership cho từng user; task sửa truy vấn/batch và đo hiệu năng là việc DEV riêng, không nằm trong bộ thay đổi contract này | Chưa sửa; không khẳng định hệ thống chậm khi chưa đo |
| CR-13 | Checker DEV mới | Kiểm tra biên phát hiện format email mặc định nhận a@ và Decimal(1.0) bị từ chối như non-integer. Đã bổ sung parser addr-spec, hỗ trợ integer JSON có phần lẻ bằng 0 và fixture decimal scale | Đã sửa checker; hồi quy PASS, không sửa API hoặc nghiệp vụ để làm test pass |

## Mapping cần giữ khi triển khai code

| Giao điểm | Hiện có | Contract target / công việc implementation còn lại |
|---|---|---|
| Auth/self | ApiDtos, session JDBC, CSRF, /api/v1/auth/me và /me/permissions | Giữ route/body legacy. CurrentIdentityPort/AccountSecurityPort mới và profile activation opt-in cần triển khai trong task riêng |
| Permission | V3 + PermissionCatalog + org/owner guard | Giữ 18 mã; 33 mã mới qua migration mới, capability/delegation phải có backend guard; role ENTERPRISE không tự thành HR/Mentor |
| Audit | Enum 30 mã, transaction audit, request ID và API đọc | 63 action và metadata schema mới cần enum/migration/adapter; không emit action target vào enum hiện tại rồi mong thành công |
| Profile visibility | ProfileVisibilityPort hiện trả boolean, studentId được policy so với principal.userId | studentId legacy là user identity trong guard hiện hữu. BC24 cần facade/decision theo field/time/purpose; bool port cũ chưa chứng minh consent projection thật |
| File | FileMetadata entity/repository | CP-FILE1 upload/scan/download/Range chưa có controller/service; schema không tạo quyền tải file |
| Import | Token invitation/reset trực tiếp và EmailService hiện hữu | Job/claim/encrypted outbox/roster port/resend mới chưa triển khai; mail của integration test core được mock, không chứng minh SMTP outbox |
| Error | Core trả error/message theo ApiErrorHandler | Legacy giữ response/status; API mới bổ sung errorCode/requestId/fieldErrors/retryAfterSeconds qua adapter theo family. Không thay toàn bộ lỗi cũ trong task tài liệu |
| Flyway | V1–V7 | Không cấp trùng V8 cho các team; allocate version tuần tự khi thực sự triển khai, không dùng ddl-auto=update |

## Truy vết nhiệm vụ và B01–B06

| Yêu cầu | Artifact / kiểm tra DEV | Phần chưa chứng minh |
|---|---|---|
| B01 / R27 / CONTRACT-01 | v1.1 K.1/K.3; schemas + typedPorts; SCHEMA-REFS/TYPED-PORTS/EX-* | Guards/DTO/adapters runtime của module mới |
| B02 / AZ-11 / PERMISSION-01 | v1.1 D; permission_matrix.csv mới; REGISTRIES/PERMISSION-* | Migration và scoped capability implementation |
| B03 / AUDIT-01 | v1.1 E; audit_registry.csv; AUDIT-* | Transaction/rollback của producer mới |
| B04 | v1.1 F.1–F.3; EX-64..68; CONFIRM-* | Đọc XLSX/CSV, scan, preview TOCTOU, 5.000 rows runtime |
| B05 | F.4; COUNTERS-* | Durable transaction, crash/resume/lease và concurrency thật |
| B06 | F.5; ResendActivation/ResendAccepted schema | SMTP at-least-once, khóa/token race, quota và quản lý khóa outbox |
| R29 | ID ví dụ/ca kiểm tra, source/bundle/tool hash và run timestamp trong evidence | RTM nghiệm thu toàn bộ Must do QA theo dõi riêng |
| R32/R34 | Bản gốc bất biến; v1.1 K; typed port/context và manifest ownership | Dependency/architecture test của các module khi có source |

## Checklist bàn giao theo team

Các checklist này chuẩn bị cho review/triển khai; dấu trống không phải điều kiện chờ ký lại bản v1.0 đã được người dùng chốt.

**T1**

- [x] Giữ hash SRS/v1.0, registry 18/30 và API core.
- [x] Xuất bundle, CSV mapping và checker DEV có bằng chứng.
- [ ] Review diff v1.1 như phiên bản mới; source port/adapter/migration triển khai trong task được giao.
- [ ] Triển khai File/capability/import/mail theo thứ tự phụ thuộc, không coi checker là guard backend.

**T2**

- [ ] Review BC11/BC21/BC24 và F.2: userId khác MSSV, roster khác identity, note/custom skills khác CT-01.
- [ ] Thiết kế profile theo term/DECIMAL precision và các relation; giữ dữ liệu khi đổi ngành.
- [ ] Cung cấp StudentRosterProvisionPort/eligibility/consent projection đúng owner; không tự cấp account hoặc schoolApproved.

**T3**

- [ ] Review CP-AUTHZ1/MENTOR1/WS1–5 và BC41; HR/Mentor là assignment, không role mới.
- [ ] APPROVED chỉ WAITING_START, ACTIVE/effectiveAt mới IN_PROGRESS; consumer re-read source và giữ version/inbox.
- [ ] Submission nhiều file, review version, report/rubric/close giữ transaction và permission riêng.

**T4**

- [ ] Review BC11/BC12/BC31/BC32: snapshot canonical không note/custom skills, completeness đúng sáu check.
- [ ] Enterprise chỉ ProtectedAggregate band, không rawCount/total/%/identity; release ledger theo baseline k=5.
- [ ] Không dùng schema PASS để thay privacy adversarial/load test hoặc tự suy diễn Required skills.

**T5**

- [ ] Review TermReference, verification theo trường, invitation thực tập và Placement; khác account/org core T1.
- [ ] UI import dùng importJobId/rowId/version/hash mới; không chọn bằng rowNo hoặc gửi schoolApproved.
- [ ] Approve/quota/eligibility/consent/share/active-key cùng transaction, bàn giao T3 qua BC41.

Mẫu ghi nhận review từng phần:

```text
Người review / Team:
Contract ID / phiên bản / SHA hoặc commit:
Phạm vi DTO / port / permission / audit:
Kết quả: đồng ý / cần sửa
Vị trí và nội dung cần sửa:
Bằng chứng DEV / phần runtime NOT RUN:
Ngày xác nhận:
```

## Tiến độ DEV và các giới hạn còn lại

Workbook được cập nhật 36 ô thuộc tiến độ/bằng chứng/ghi chú DEV của T1-01 và các dòng contract liên quan. T1-01 tổng vẫn **Đang làm** để phản ánh diff v1.1 còn cần review và phạm vi tích hợp chưa được kiểm chứng; CONTRACT-01 ghi hoàn thành phần DEV chuẩn bị typed contract. Không sửa ô Tester, QA link, Leader hoặc nghiệm thu SRS. Bằng chứng: [T1_01_WORKBOOK_UPDATE.json](../testing/evidence/T1_01_WORKBOOK_UPDATE.json).

Artifact Tool xuất workbook và ảnh trước/sau, nhưng tiến trình trả exit 1 sau export mà không kèm chẩn đoán. Chỉ nhận file sau kiểm tra độc lập toàn bộ ô, công thức, style, validation, conditional formatting, sheet order, dimensions và ZIP CRC: chỉ đúng 36 ô DEV thay đổi, ô tổng hợp Đang làm tăng 0→1, QA giữ nguyên. Chưa kiểm thử trong phiên Excel/Google Sheets tương tác; không báo native application PASS.

Không commit/push/deploy hoặc gửi thông báo cho team. Không thay SRS, core source, dữ liệu hay migration. Các việc lập trình riêng còn gồm R33, các port/adapter thật và tính năng mới theo kế hoạch; không nâng toàn bộ T1-01 hoặc Demo/MVP thành nghiệm thu chỉ từ kết quả này.
