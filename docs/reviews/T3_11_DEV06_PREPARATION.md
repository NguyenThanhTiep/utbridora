# DEV06 — Chuẩn bị T3-11 tuần 4

Ngày đối chiếu: 14/11/2026. Owner: Lê Nguyễn Nhật Duy (DEV06), QA03.
Trạng thái: chuẩn bị phạm vi và kế hoạch; chưa triển khai hoặc kiểm thử T3-11.
Chỉ đạo mới nhất: chỉ dùng CAP NHAT CHUNG để xác định owner/task; không mở rộng
sang FR-24/T3-12 theo sheet chi tiết. Thực hiện trên nhánh chung tuần 4 DEV05 tạo.

## Đối chiếu bàn giao trên main ngày 14/11/2026

- Đã đọc `docs/handoffs/T3_TUAN3_BAN_GIAO_TUAN4.md`: DEV06 làm school publish/revision,
  school policy, HR org/term và Case visibility, student result triple gate. Không làm Case closure.
- Main đã cập nhật; fetch remote chưa thấy nhánh chung T3 tuần 4. Chưa triển khai code trên
  main hoặc tự tạo nhánh trái chỉ đạo. Đã hỏi người dùng tên nhánh hoặc quyền tạo nhánh chung.
- `docs/contracts/README.md` ghi quyết định 13/11: SRS v1.2.4 và Contract v1.1 đã được
  duyệt làm căn cứ triển khai. Nhãn PROPOSED cũ không phải blocker hoặc yêu cầu duyệt lại.
- Local chưa thấy Evaluation runtime. Cần tích hợp port/persistence từ T3-10, không tự
  nhận phần rubric/score calculation. Có thể triển khai độc lập visibility policy và test
  khi nhánh làm việc được xác định, rồi nối published snapshot của DEV05.
- T1 có SchoolDelegationAccess/AuthorizationPolicy, WorkspaceAuthority và common ledger;
  kiểm riêng seed/ceiling ba quyền grade và registry audit khi triển khai, không dựng lại Core.
- Migration hiện dùng base/main locations và Java V32 hội tụ, V33 thuộc baseline; không
  suy số migration hoặc đặt SQL theo layout cũ của tuần 3.

Các đối chiếu sheet chi tiết và nhận định baseline bên dưới là snapshot chuẩn bị trước
khi nhận main/bàn giao; mục này và CAP NHAT CHUNG là căn cứ công việc hiện tại.
Nguồn: workbook local UTBridora_Plan.xlsx và Contract Pack v1.1 Candidate CP-WS4.
Không sửa Plan, SRS hoặc tự xác nhận QA/Leader.

## Đối chiếu phân công

| Nguồn | Nội dung | Phạm vi |
|---|---|---|
| CAP NHAT CHUNG dòng 63 | T3-11, DEV06/QA03: School publish + HR visibility + chính sách xem điểm | FR-21, FR-22, R24; dependency T3-10 và T1-04 |
| T3 - THUC TAP dòng 22 | FR-21: Mentor DRAFT → SUBMITTED; trường PUBLISHED; student không xem draft/submitted | DEV06; phối hợp transition submit với evaluation owner DEV05 |
| T3 - THUC TAP dòng 25 | FR-22: HR bật/tắt xem kết quả, không sửa điểm hoặc vượt policy trường | DEV06/T3-11 |
| T3 - THUC TAP dòng 39 | R24: PUBLISHED AND school+term policy AND effective HR visibility | DEV06/T3-11; kiểm mỗi GET |
| T3 - THUC TAP dòng 57 | NFR-02: không công bố đánh giá nháp, chỉ trả dữ liệu đúng quyền/mục đích | DEV06/T3-11, QA03 kiểm riêng |
| T3 - THUC TAP dòng 23 | FR-24 danh sách điểm trường ghi DEV06 nhưng mã T3-12 | Cần leader làm rõ backend danh sách/owner; không tự nhận toàn bộ T3-12 |
| CAP NHAT CHUNG dòng 62/64 | T3-10 DEV05 rubric/evaluation; T3-12 DEV07 UI | Không viết lại rubric/tính điểm hoặc làm UI trong T3-11 |

## Công việc DEV06

1. School publish: chỉ staff có delegation và t3:grades:publish đúng Case/school/term;
   current SUBMITTED → PUBLISHED, expectedVersion, decisionRef, ledger và audit.
   Không cho HR/Mentor tự công bố, không ghi đè snapshot đã publish.
2. SchoolResultVisibilityPolicy theo school+term: mặc định false; đọc record chưa tồn tại
   trả enabled=false/version0; lần tạo đầu expectedVersion0; staff được ủy quyền quản lý.
3. HR visibility theo enterprise+term hoặc Case: CASE override ORGANIZATION_TERM;
   thiếu cả hai là false. HR group chỉ ghi CASE trong group được giao; enterprise-wide
   HR mới được ghi ORGANIZATION_TERM. Không thay điểm và không override school policy.
4. Student GET self result: chỉ trả published snapshot khi đủ cả ba điều kiện;
   thiếu bất kỳ điều kiện nào trả HIDDEN, evaluation=null, reasonCode=NOT_AVAILABLE.
   Kiểm quyền hiện hành mỗi request; không gửi điểm về browser rồi mới ẩn bằng UI.
5. Phối hợp evaluation revision với DEV05: bản đã published tiếp tục là kết quả công bố
   đến khi revision mới được publish; không vô tình trả current DRAFT/SUBMITTED.

> Cập nhật sau chuẩn bị: đã pull feature/t3-week4 (DEV05 e480abc) và triển khai T3-11.
> Xem [bàn giao hiện hành](T3_11_IMPLEMENTATION_HANDOFF.md). Các ghi chú branch, PROPOSED
> và dependency chưa có bên dưới chỉ phản ánh thời điểm chuẩn bị, đã được giải quyết.

## API theo CP-WS4 để chuẩn bị tích hợp

| Method/path | Mục đích |
|---|---|
| POST /api/v1/t3/evaluations/{evaluationId}/publish | PublishEvaluation → EvaluationDto |
| GET/PUT /api/v1/t3/schools/{schoolOrgId}/terms/{termId}/result-visibility | SchoolResultVisibilityDto / SetVisibility |
| GET/PUT /api/v1/t3/organizations/{enterpriseOrgId}/grade-visibility | GradeVisibilityQuery / SetVisibility / GradeVisibilityDto |
| GET /api/v1/t3/me/results?caseId=... | StudentResultDto |

POST evaluation revisions, Mentor submit và GET school/evaluations phải thống nhất ranh giới
với DEV05/DEV07 trước khi tích hợp; không triển khai trùng owner. Close Case không tự nằm
trong T3-11 chỉ vì cùng mục CP-WS4.

## Baseline và dependency

- Checkout đang ở feature/t3-week3. Đây là trạng thái local, chưa fetch remote trong lượt
  chuẩn bị này; không kết luận DEV05 chưa làm T3-10 trên nhánh khác.
- T3-08 đã có Case/Placement guard, transaction, common ledger/audit, JDBC và ví dụ kiểm thử
  HTTP/MySQL để tham khảo; không tái sử dụng bảng Report làm bảng Evaluation.
- Tìm kiếm source local chưa thấy runtime Evaluation/GradeVisibility/StudentResult tương ứng.
- T1 delegation và nền tảng authorization đã có source; cần kiểm đủ exact permissions,
  role ceilings, grade purpose, scope và revoke trước khi coi T1-04 đáp ứng T3-11.
- Cần nhận interface/snapshot/lock/version/published pointer từ T3-10, không tự sửa công thức
  rubric hoặc tạo aggregate Evaluation cạnh tranh. Có thể chuẩn bị policy thuần, DTO và
  truth-table tests ngay, không cần dừng toàn bộ công việc để chờ dependency.
- CP-WS4 hiện có nhãn PROPOSED; đây là trạng thái tài liệu, không tự coi đã ký duyệt hoặc
  tự mở rộng API. Ghi rõ diff/schema cần thống nhất khi bắt đầu triển khai.
- Chưa chọn migration number: phải đối chiếu toàn bộ refs khi thực sự viết migration;
  không mặc định V32 chỉ vì T3-08 dùng V31.

## Trình tự triển khai đề xuất

1. Kiểm tra branch được leader giao cho tuần 4, fetch và đối chiếu T3-10/T1-04 mới nhất.
2. Chốt owner publish/revision/read list, Evaluation port và published snapshot contract.
3. Viết policy visibility thuần + 8 tổ hợp truth table; DTO strict và validation version/scope.
4. Thêm persistence policy và adapter evaluation, guards delegation/HR, transaction/CAS,
   audit/ledger; migration tiến lên không sửa migration đã phát hành.
5. Nối HTTP theo CP-WS4; trả HIDDEN từ backend; kiểm mọi đường đọc để tránh lộ điểm nháp.
6. Test HTTP/MySQL thật, cập nhật contract/evidence và bàn giao API cho DEV07, QA03.

## Checklist kiểm thử

- Cả 8 tổ hợp ba điều kiện: chỉ true/true/true trả VISIBLE; không policy mặc định HIDDEN.
- CASE=false thắng ORGANIZATION_TERM=true; CASE=true vẫn không vượt school=false.
- Student khác/khác trường/khác enterprise không đọc được; anonymous/CSRF đúng lỗi Core.
- Mentor/HR không publish; HR không sửa policy trường; staff không delegation bị từ chối.
- Delegation/membership/HR assignment bị revoke: request và cached replay kiểm lại quyền.
- Stale version, duplicate request, concurrent update/publish và audit failure: không partial
  write, không double audit, retry đúng; kiểm thu hồi visibility trên GET tiếp theo.
- DRAFT/SUBMITTED không lộ score/template/comment/file IDs qua result payload hoặc lỗi.
- Revision mới chưa publish vẫn trả bản published cũ; publish mới chuyển pointer nguyên tử.
- Regression T3-08 và Task tuần 2; test pass của task khác không thay QA T3-11.

Đầu ra cần bàn giao: source/migration, API và DTO cho DEV07, truth-table và DB evidence,
quyền theo actor, giới hạn còn lại và commit chính xác để QA03 kiểm. Chưa commit/push lượt này.
