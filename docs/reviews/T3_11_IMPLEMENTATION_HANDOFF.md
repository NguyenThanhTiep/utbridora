# T3-11 — DEV06: Công bố và quyền xem kết quả

Ngày 14/11/2026. DEV06 — Lê Nguyễn Nhật Duy. Nhánh `feature/t3-week4`.
Base DEV05 đã pull: `e480abc2bf0a7dd474487dbdff46c857b0cb40b5`.
Đây là bàn giao triển khai DEV, chưa phải kết quả QA03/CI hay chấp thuận merge.

## Phạm vi và nguồn

- Plan, sheet **CAP NHAT CHUNG**, dòng 63, task T3-11; phụ thuộc T3-10 và T1-04.
- FR-21/22, R24; đọc thêm [bàn giao DEV05](T3_10_IMPLEMENTATION_HANDOFF.md)
  và [bàn giao tuần 4](../handoffs/T3_TUAN3_BAN_GIAO_TUAN4.md).
- Dùng CP-WS4/C.0 và quyết định duyệt trong [contracts/README](../contracts/README.md).
  Không sửa snapshot Contract/SRS, công thức rubric của DEV05 hoặc trạng thái QA trong Plan.
- Tái sử dụng evaluation root/snapshot của T3-10, T1 delegation, ledger, audit và File Service.

## Đã triển khai

- School có membership + delegation đúng trường/kỳ công bố bản SUBMITTED. Tạo snapshot
  PUBLISHED, lưu decisionRef/publishedAt và chuyển published pointer trong cùng transaction.
- School tạo revision từ SUBMITTED/PUBLISHED: DRAFT mới, tăng revisionNo/version,
  lưu decisionRef/reasonCode, giữ bản đã công bố cho sinh viên tới khi publish bản tiếp theo.
- Policy trường/kỳ và HR doanh nghiệp/kỳ hoặc Case: mặc định false/version 0,
  cập nhật bằng expectedVersion. CASE ghi đè ORGANIZATION_TERM, kể cả CASE=false.
- Sinh viên chỉ xem Case của mình khi đủ **published + school enabled + HR enabled**.
  Thiếu bất kỳ điều kiện nào trả HIDDEN, evaluation=null, reasonCode=NOT_AVAILABLE;
  không gửi điểm nháp/template/comment/file IDs rồi nhờ UI giấu.
- File minh chứng dùng cùng điều kiện; sinh viên chỉ đọc file gắn trong snapshot đã
  công bố. School kiểm delegation; Mentor giữ quyền hiện tại của T3-10.
- School list có school/term/cohort/state/paging và kiểm quyền grade purpose.
- Mutations có session, CSRF, idempotency, current authorization trước replay,
  CAS/lock và audit cùng transaction. Response riêng tư có `Cache-Control: private, no-store`.
- HR theo nhóm có thể được coordinator cấp qua API hiện hữu, chọn context và quản CASE
  trong nhóm. Đường kiểm quyền này riêng cho policy điểm; không mở quyền HR toàn kỳ
  ở các API workspace/mentor cũ. HR không có quyền School publish.

## API bàn giao DEV07

Các path dưới đây có prefix `/api/v1/t3`. Mutation cần CSRF và `Idempotency-Key` UUID.
HR cần `X-Context-Assignment-Id`; header chỉ chọn assignment đã được cấp.

| API | Input / output |
|---|---|
| POST `/evaluations/{id}/publish` | `{expectedVersion,decisionRef}` → 200 EvaluationDto |
| POST `/evaluations/{id}/revisions` | `{expectedVersion,decisionRef,reasonCode}` → 201 EvaluationDto DRAFT |
| GET/PUT `/schools/{school}/terms/{term}/result-visibility` | PUT `{expectedVersion,enabled}` → SchoolResultVisibilityDto |
| GET/PUT `/organizations/{enterprise}/grade-visibility?termId=…&caseId=…` | caseId optional; PUT `{expectedVersion,enabled}` → GradeVisibilityDto |
| GET `/me/results?caseId=…` | StudentResultDto: VISIBLE/HIDDEN, evaluation, reasonCode |
| GET `/school/evaluations?termId=…` | cohortId/state/page/size optional → Page<EvaluationDto> |

Luồng UI: Mentor submit → School reload danh sách → publish bằng version vừa đọc →
bật policy trường và HR → Student gọi me/results. Không dùng API Mentor cho Student.
Khi 409, reload version rồi yêu cầu người dùng quyết định lại; retry network cùng
payload dùng lại idempotency key. 401 đăng nhập lại; 403/404 không suy diễn có dữ liệu.
Revision mới không làm mất kết quả published cũ và không cho Student đọc file nháp mới.

## Database và kiểm thử

Migration mới `week4-t3/V39__t3_grade_visibility.sql`: hai bảng policy, một bảng quyết định revision,
ba permission/role ceilings; giữ checksum V1–V38. School delegation vẫn phải được cấp
thực tế, seed permission không tự cấp quyền nghiệp vụ cho mọi staff.

Kết quả cuối: **DEV implemented/tested, sẵn sàng QA**, chi tiết trong
[T3_11_DEV_EVIDENCE.json](T3_11_DEV_EVIDENCE.json).

| Kiểm tra | Kết quả |
|---|---|
| Toàn bộ unit | 1.333 PASS, 0 fail/error, 1 skip vì Windows không cho tạo symlink |
| T311GradeRuntimeIT | 20/20 PASS: 8 tổ hợp visibility, revision, scope/revoke/replay, file gate, strict input/CSRF, concurrent CAS/publish, rollback audit |
| T311SchemaUpgradeIT | 1/1 PASS: V38 có dữ liệu → V39, giữ snapshot/pointer, FK/CHECK |
| T310 Evaluation + schema | 16/16 PASS |
| T308 Comment + Report | 15/15 PASS |
| T307 Submission | 20/20 PASS |
| InternshipRuntimeIT | 11 PASS, 1 browser skip do không bật frontend.e2e |
| Week3MigrationIT | 1 PASS bao phủ 13 lịch sử migration, hội tụ V39, checksum/history giữ nguyên |
| Contract bundle + Python audit/portability | 868/868 checks và 4/4 tests PASS |

Lệnh đã chạy, tại repo root (hai lượt đều Maven BUILD SUCCESS):

```powershell
.\mvnw.cmd '-Dit.test=T311GradeRuntimeIT,T311SchemaUpgradeIT,T310EvaluationRuntimeIT,T310SchemaUpgradeIT,Week3MigrationIT,T308CommentRuntimeIT,T308ReportRuntimeIT' clean verify
.\mvnw.cmd '-Dit.test=T311GradeRuntimeIT,T307SubmissionRuntimeIT,InternshipRuntimeIT' verify
py -3.11 scripts/contracts/build_contract_bundle.py --check
py -3.11 scripts/contracts/verify_contracts.py --output target/contract-verification.json
py -3.11 -m unittest discover -s scripts/contracts -p 'test_*.py'
```

Lượt đầu chạy 18 test T3-11; lượt sau bổ sung thành 20 và kiểm cấp HR qua API thật.
Lỗi phát hiện trước đó ở file Mentor (transaction rollback-only khi fallback quyền)
đã sửa bằng chọn nhánh quyền trước service; regression T3-10 và T3-11 đều PASS.

Test MySQL 8.4 Testcontainers riêng, HTTP/session/Core thực. Placement ACTIVE, assignments,
rubric và trạng thái file CLEAN là fixture synthetic; không chứng minh T5 activation
hay antivirus production. Không kết nối/sửa database demo dùng chung ở port 8088.

## Điểm cần biết khi tích hợp

- Xung đột **V38** đã có đường xử lý bằng V40 và locations riêng:
  [runtime tuần 4](../guides/WEEK4_MIGRATION_RUNTIME.md),
  [kiểm thử hội tụ](T3_V40_CONVERGENCE_VERIFICATION.md). Giữ checksum/history cũ.
  Báo cáo test T3-11 V39 phía trên là lượt trước hội tụ; bằng chứng V40 là cập nhật mới.
- Scanner T1 thực chưa có trên checkout này. Luồng không file chạy được; file thật
  vẫn cần scanner trả CLEAN. Không tự đổi PENDING thành CLEAN trong application.
- UI thuộc DEV07/T3-12; close Case không thuộc T3-11. Chưa có kiểm thử browser UI,
  QA03, GitHub CI của thay đổi này. Chưa commit/push/merge lượt T3-11.
- Các file AI context/preparation và dòng chuẩn bị trong DEV06_TASK_PROGRESS đã có
  ở working tree trước khi chuyển nhánh; cập nhật rõ trạng thái mới, giữ phần lịch sử.
