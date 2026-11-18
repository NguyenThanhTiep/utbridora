# Rà soát tích hợp DEV tuần 4 — 18/11/2026

Phạm vi: 12 task DEV tại `CAP NHAT CHUNG` hàng 58–69 và các dòng chi tiết
được gắn với chúng trong Plan người dùng. Không nhập nhánh QA/test, không nhập
lại task tuần 3 đã có trên main, không tự triển khai các Must được xếp sau ngày 35.
Đây là review và kiểm chứng DEV; không thay kết quả QA/Lead/nhà trường.

Căn cứ: SRS tổng thể v1.2.4 đã duyệt (ưu tiên §25.8/§25.9), Contract Pack
B01–B06 v1.1 đã duyệt. Giữ nguyên bytes/hash hai tài liệu đóng băng. Plan hiện
có thay đổi riêng của người dùng, chỉ đọc để đối chiếu, không sửa trạng thái.

| Task | Nhánh DEV | Điểm đối chiếu và regression |
|---|---|---|
| T1-07 | feature/t1-07-file-scanning | ClamD thật, CLEAN/owner/binding/Range, timeout/retry/lease/audit; FileScanningIT và private-file tests |
| T2-09 | feature/t2-week4 | CV/project/certificate liên kết file CLEAN đúng chủ và purpose; StudentProfileIT, student browser |
| T2-10 | feature/t2-week4 | Task, URL submission, weekly report qua API T3; chỉ render PUBLISHED khi đủ ba cổng; student browser, T3 runtime |
| T2-11 | feature/t2-week4 | Consent revoke/expiry, share/policy narrowing, school/enterprise scope, replay và file Range; EnterpriseProposalIT |
| T3-10 | feature/t3-week4 | Rubric trọng số tổng 100, score 0–5, BigDecimal/HALF_UP; phiên bản bất biến, evidence CLEAN; T310 runtime/schema, EvaluationPolicyTest |
| T3-11 | feature/t3-week4 | School publish/revision, policy school/term và HR org/case, tám tổ hợp gate tại GET; T311 runtime/schema |
| T3-12 | feature/t3-week4 | UI thật Mentor/School/HR/Student, revision/republish, hidden payload và logout; T312EvaluationBrowserIT |
| T4-07 | feature/t4-week4 | Fingerprint/replay, actor/key/filter/demand differencing, 10/10/10 với 9/10/11; EnterpriseProposalIT; smoke classifier 400 hồ sơ |
| T4-08 | feature/t4-week4 | JSON/CSV/chart chỉ eligibleBand, SUPPRESSED dưới 5; fixture 0/1/4/5/9/10/19/20; không danh tính hoặc nomination cho enterprise |
| T5-10 | feature/t5-week4 | Placement list/search/filter/count trước SQL page theo school/term; target snapshot APPROVED, DISTINCT placed, pending creation và remaining |
| T5-11 | feature/t5-week4 | Demand/batch lock, idempotency, capacity riêng với pending creation, không double count; race 20, outbox và cancel regressions |
| T5-12 | feature/t5-week4 | Placement/progress UI, refresh, 409 giữ/đồng bộ dữ liệu, đọc đánh giá từ T3 đúng case/term; placement browser và T311 API |

## Sửa trong lúc tích hợp

1. Hai nhánh dùng V38 khác nhau. Giữ SQL scanner nguyên bản ở
   `db/legacy/t1-week4`, giữ SQL T3 V38/V39 ở `db/migration/week4-t3`, dùng V40
   hội tụ đã có từ T3. Đối chiếu byte-identical trước khi bỏ bản scanner trùng
   trong base. Không sửa SQL/checksum/history cũ. Xem [runtime migration](../guides/WEEK4_MIGRATION_RUNTIME.md).
2. School evaluation list và policy GET chạy song song có thể deadlock khi
   kiểm tra ủy quyền nâng khóa Core SHARE thành UPDATE. Authority read dùng
   SHARE để vẫn fence revocation tới commit. Regression ép hai giao dịch cùng
   giữ Core SHARE trước khi kiểm chuỗi; ca riêng xác minh revoke vẫn phải chờ.
   Policy writer lấy term UPDATE trước các authority read có thể giữ term SHARE,
   tránh hai writer cùng nâng khóa. Giữ kỳ vọng CAS đúng một 200 và một 409.
   Giữ nguyên assertion luồng browser thật từng bị lỗi ở nhánh T3.
3. PLC-10 `pending` là DISTINCT ứng viên ENTERPRISE_ACCEPTED, lời mời ACCEPTED,
   còn reservation/consent/share và chưa có Placement. Không cộng RESERVED,
   INVITED hoặc Placement PENDING_APPROVAL vào số này. Capacity G09/R09 vẫn giữ
   bộ đếm reservation riêng; school progress có `reservations`/`pendingApproval`
   riêng, enterprise chỉ nhận bốn bộ đếm vận hành. Demand chưa APPROVED không
   đóng góp chỉ tiêu. UI phân biệt “chờ tạo” và “còn thiếu” với “chỗ có thể đề cử”.
4. T5 follow-up gửi `caseId`/`placementId` nhưng T3 từng từ chối query này.
   GET `/api/v1/t3/school/evaluations` bổ sung hai bộ lọc tùy chọn, giữ các
   tham số cũ. Xác minh case/placement thuộc đúng school/term, và khớp nhau
   khi cùng có mặt, trước SQL count/page; sai scope trả 404. DTO không đổi;
   không sửa Contract Pack đóng băng. Có HTTP regression đúng/sai scope/page.
5. Rebuild machine-readable contract manifest theo source hợp nhất; không lấy
   manifest riêng của một team làm bằng chứng cho toàn bộ source.
6. Race 20 approvals phát hiện vòng khóa giữa Placement và eligibility khi quota
   dùng locking join qua proposal backlink. Approve dùng READ_COMMITTED, vẫn giữ
   demand mutex và eligibility owner lock; đếm DISTINCT Placement trực tiếp theo
   cooperation/demand sau khi lấy mutex, không khóa các pending row không liên quan.
   Kiểm active student/term sau owner lock và giữ unique index làm guard cuối.
   Không nới test để chấp nhận 503 hoặc bỏ assertion một outbox/active key.
7. Báo cáo InnoDB từ runner chỉ ra reference validation nâng term SHARE lên
   PESSIMISTIC_WRITE sau khi giữ demand mutex. Các adapter chỉ đọc dùng
   PESSIMISTIC_READ; đường ghi giữ phương thức UPDATE riêng. Regression ép hai
   validation cùng giữ SHARE rồi chạy song song, và xác minh writer vẫn phải
   chờ reader commit. Không bỏ guard trạng thái/kỳ hoặc hạ thành read không khóa.
8. Writer của SchoolSharingPolicy cũng lấy term UPDATE trước authority SHARE,
   theo cùng thứ tự với policy điểm; hai cập nhật expectedVersion=0 phải có
   đúng một 200 và một 409. Giữ việc thu hẹp policy chặn share ở lần đọc kế tiếp.

## Kiểm chứng và giới hạn

Full workflow giữ contracts, unit/build và toàn bộ IT/browser trên ba runner
riêng, kiểm inventory suite mỗi shard. Không chạy Testcontainers trên Docker
phục vụ demo. Chỉ merge main khi workflow của đúng SHA tích hợp đã SUCCESS;
lưu SHA, log, JUnit counts/digests và artifact tại checkpoint của lần tích hợp.

Smoke classifier không phải phép đo NFR API/DB hoặc chứng nhận p95 toàn hệ thống.
School audit viewer, hậu kiểm đối tác/kết thúc case, import và mail theo các mốc
riêng còn được UI thể hiện là chưa có provider; không giả API/quyền hoặc trạng
thái nghiệm thu. Kết quả sinh viên dùng case từ phân công task hiện có; UI T3
cũng nhận mã case để đọc kết quả bằng guard owner. Không coi đây là tính năng
khám phá toàn bộ case sinh viên khi chưa có task.

Merge source không nâng DB hay deploy 8088. Mỗi lineage phải backup và kiểm
chứng clone trước khi upgrade; giữ cấu hình local, Plan và stash của người dùng.
