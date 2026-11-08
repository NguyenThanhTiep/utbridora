# Bản tích hợp các nhánh ngày 08/11/2026

Phạm vi và guard đối chiếu SRS `UTBridora_SRS_Tong_The_v1.2.4.docx`, Contract Pack nền v1.0 và hiệu chỉnh kỹ thuật v1.1. Phân biệt code đã tích hợp, dữ liệu/provider đã sẵn sàng và nghiệm thu nghiệp vụ toàn luồng.

## Phần dùng chung đã nối

- `OperationLedger` dùng `operation_records`, cùng transaction với domain/audit; auth hiện hành trước replay, version/business trong callback; cache 24 giờ, expiry không tạo lại effect. Wait trên ledger tối đa 5 giây; response bí mật/token/password bị từ chối cache và rollback.
- Student self dùng UUID principal, scope từ Term T5 + quan hệ roster/eligibility T2 thật; term giả không tạo hồ sơ. Một aggregate/profileVersion, các phần theo kỳ riêng. Roster read phải qua verified school và delegation/visibility gate; membership đơn thuần không mở PII.
- Taxonomy đọc theo quyền; ghi qua named TAX assignment hiện hành, đúng role ceiling, không seed write cho mọi ADMIN. Category/skill/alias dùng ledger chung, version, audit metadata canonical và rollback khi audit lỗi.
- Demand dùng một registry Term, taxonomy/version thật, ledger chung và revision append-only. Không còn `demand_commands` hoặc catalog mẫu bật bằng cờ production. Công tắc triển khai `T5_DEMAND_ENABLED` không phải quyết định phê duyệt contract.
- Verification theo CP-T5UI1: DTO lồng `data`, trạng thái `DRAFT/SUBMITTED/NEEDS_CHANGES/APPROVED/REJECTED`, key/version, attempt mới nối REJECTED cũ, history snapshot bất biến và audit canonical. Contact chỉ enterprise owner hoặc reviewer có SD hiện hành; ADMIN global không vượt scope.
- `SchoolDelegationAccess` là read gate T1: current account/membership, school/term, quyền, thời hạn và chuỗi parent còn hiệu lực. Schema/read gate này không thay cho bộ API cấp/thu hồi delegation đầy đủ.
- Minh chứng verification kiểm metadata CLEAN, đúng enterprise/resource/purpose VERIFICATION, PDF/PNG/JPEG ≤10MiB, không bị xóa; khóa theo UUID trong transaction. Submit và APPROVE đều kiểm lại. Một file chỉ được ghi một binding; không có API rebind.
- BC42 bỏ HTTP guard đọc vô điều kiện. Port nội bộ có context do workflow server tạo, purpose và school/term scope; không có SYSTEM bypass.

## TermReference theo SRS

SRS T34/R3, P106/P454/P469 giao T5 sở hữu TermReference. BC21 công bố đọc reference; SRS/registry hiện hành chưa xác định API ghi Term hay audit `TERM_CREATED`/`TERM_STATUS_CHANGED`. Bản tích hợp giữ một model/repository và GET có kiểm scope, không đưa thêm các route ghi ngoài contract. Dữ liệu kỳ được cấp qua fixture/nguồn quản trị đã review; không tự sinh term UUID ở consumer T1/T2/T3.

## Migration của bản tích hợp

| Version | Nội dung |
|---|---|
| V1–V7 | Core đã có; giữ nguyên |
| V8 | Common operation ledger |
| V9 | Canonical T5 terms |
| V10 | T2 roster và eligibility có liên kết UUID authoritative |
| V11 | Student profile |
| V12–V14 | Taxonomy, named TAX read gate, alias/implies |
| V15 | Audit metadata |
| V16 | Demand + revisions; FK school/term |
| V17 | Delegation read gate và file binding |
| V18 | Enterprise-school verification + history |

Các số feature cũ V8/V9/V10 là tạm. Bản tích hợp được kiểm trên database trống và upgrade từ V2, bằng tài khoản DB thông thường. Chưa áp DDL vào database local/staging của người dùng. Với database feature đã chạy số tạm, dùng database phát triển riêng; không sửa checksum, repair hoặc clean database có dữ liệu dùng chung. Sau khi đổi tên migration nguồn, chạy Maven `clean` một lần để bỏ SQL cũ trong `target/classes`.

## Giới hạn còn phải theo dõi đúng task

- Chưa có nguồn catalog học vụ T2 có version trong các nhánh đã kiểm. Profile từ chối lựa chọn catalog chưa xác minh; Demand trả `503 DEPENDENCY_UNAVAILABLE`. Không lấy boolean/mock/danh mục tĩnh làm authoritative. Student completeness vẫn là policy interim fail closed; chưa nghiệm thu T2-04/T4 completeness đầy đủ.
- TAX/SD không được tự cấp cho tài khoản demo. Manifest/fixture quyền phải có quyết định, actor và scope cụ thể. Workflow cấp/thu hồi phải bump securityVersion, history/audit trong cùng transaction; các API quản lý assignment đầy đủ vẫn là phạm vi riêng.
- Core hiện có metadata/binding gate; chưa có uploader/storage/scanner hoàn chỉnh trong milestone này. Test CLEAN dùng metadata fixture được đánh dấu, không chứng minh đã quét file thật hay đạt acceptance CP-FILE.
- T3-01 là contracts/policies; T3-02 là domain/service boundary; T3-03 là frontend shell có trạng thái chờ backend. Chưa nghiệm thu inbox/outbox/Case/Placement thực tế hoặc I11 chỉ từ unit/mock PASS.
- T4 matching đã tích hợp generator/fixture, chưa phải engine matching production. Không chạy seed synthetic vào database dùng chung.

## Kiểm chứng

`mvn -Dfrontend.e2e=true clean verify` kiểm Java/MySQL và các luồng browser Core, Student, Demand. Browser XML tách tại `target/*browser-results.xml`; UI mock/pure tests được phân biệt với các ca API thật. `BusinessModulesIT` kiểm named TAX, SD/verification, rollback, replay và binding qua filter production; `OperationLedgerIT` kiểm race/wait/expiry/cache purge/rollback.

Contract checks: `build_contract_bundle.py --check`, `verify_contracts.py`, `test_bundle_portability.py`, `test_audit_registry.py`. Enum vẫn giữ toàn bộ 30 mã audit Core cũ và chỉ mở rộng bằng mã đã có trong registry 93 action. Bundle sort/source digest ổn định Windows/Linux. Test/CI PASS phải gắn đúng SHA; không dùng báo cáo DEV cũ thay kết quả của bản tích hợp.
