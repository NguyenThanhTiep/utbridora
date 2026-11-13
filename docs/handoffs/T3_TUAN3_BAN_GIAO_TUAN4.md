# Bàn giao T3 từ tuần 3 sang tuần 4

Ngày bàn giao: 13/11/2026. Đây là bàn giao DEV sau tích hợp; QA, Lead và nghiệm thu SRS là các xác nhận riêng, chưa được thay mặt ký.

## 1. Nhận đúng base và nguồn

- Commit code tích hợp đã kiểm chứng trước commit tài liệu: `450e78d1fbd27a2da46462d3899239185cb7b946`. Nhận từ `origin/main` bằng fetch/pull trên checkout sạch; đối chiếu SHA và evidence bên dưới. Không lấy SHA của chính commit tài liệu để tạo vòng lặp sửa commit.
- Base main trước tích hợp: `e22596ac9b40ecaf7b4896ec9199ccf583b6a36c`. T1-06 nguồn: `065c5be15f19df2fe8100c42248c5bd6d9c4a956`. Không làm lại delegation, ActorContext, ledger/audit hay storage đã có.
- Nguồn yêu cầu: [Plan](../plans/UTBridora_Plan.xlsx), sheet **CAP NHAT CHUNG**; [SRS v1.2.4](../srs/UTBridora_SRS_Tong_The_v1.2.4.docx), ưu tiên §25.8; [Contract v1.0 đã chốt 05/11](../contracts/README.md). v1.1 Candidate và các GET bổ sung của T3 là diff kỹ thuật, không tự thành quyết định phê duyệt thay v1.0.
- [Ma trận tuần 3](../testing/WEEK3_INTEGRATION_ACCEPTANCE.md), [evidence đúng source](../testing/evidence/WEEK3_INTEGRATION_VERIFICATION.json), [runtime và nâng cấp DB](../guides/WEEK3_INTEGRATION_RUNTIME.md). Đọc báo cáo mới trước các ghi nhận lịch sử “chờ T1/team khác”.
- [CI đúng code SHA](https://github.com/NguyenThanhTiep/utbridora/actions/runs/43352236892) PASS: 1270 unit; 223 integration; 164 browser PASS + 16 SKIP theo context harness, không có FAIL/ERROR. Các luồng runtime thật có harness riêng. Contract 868 PASS, 0 FAIL; build/typecheck frontend PASS. Đây là bằng chứng DEV.
- Giữ các nhánh nguồn sau merge. Không phục hồi QA `f606b90`, không đọc/merge nhánh tester/QA. Tests đã có trong main/DEV vẫn thuộc regression.

## 2. Trạng thái tuần 3 và bằng chứng

- Nguồn T3: `965678ae59094b6c9a58d75d8cd107d33d956a89`; đã có T1 V28 trong lịch sử nguồn. T3-07 URL v1/v2, immutable artifacts/reviews và GET reviews; T3-08 Comment + Weekly Report/revisions/reviews; T3-09 UI submission/report/feedback đã nối API thật.
- T3 schema V29 và V31 được giữ nguyên nội dung trong lineage main. URL backend không fetch; report draft giữ file PENDING, submit buộc CLEAN/binding; không fake scanner.
- Kế thừa `T307SubmissionRuntimeIT`, `T308CommentRuntimeIT`, `T308ReportRuntimeIT`, schema upgrade tests và `T309WorkflowBrowserIT`; báo cáo/evidence cũ là snapshot DEV, evidence tích hợp chung liên kết mục 1 mới là kết quả source hiện tại.
- T5 APPROVED→WAITING_START; chỉ ACTIVE/effectiveAt hợp lệ mới IN_PROGRESS; duplicate/late/cancel qua inbox/current source. Test tích hợp mới còn kiểm Activated tới trước Approved và cancel→TERMINATED.
- Chưa có rubric/evaluation/publish/grade visibility runtime tuần 4; không suy các model domain cũ hoặc fixture thành endpoint đã chạy. QA03/Lead chưa nghiệm thu.

## 3. Công việc tuần 4 đúng Plan

| Mã / dòng Plan | Thực hiện | QA | Công việc nguyên văn | Acceptance nguyên văn | Phụ thuộc Plan |
|---|---|---|---|---|---|
| T3-10 / 62 | DEV05 | QA03 | Rubric và đánh giá phiên bản bất biến | Tính điểm đúng quy tắc, chỉnh/sửa giữ lịch sử, thử sai dữ liệu phải báo lỗi. | T3-07 |
| T3-11 / 63 | DEV06 | QA03 | School publish + HR visibility + chính sách xem điểm | Kiểm tra cả ba điều kiện trước khi trả điểm, không chỉ ẩn trên giao diện. | T3-10; T1-04 |
| T3-12 / 64 | DEV07 | QA03 | UI rubric, publish và kết quả sinh viên | Diễn thử chấm và công bố bằng tài khoản thật; trường hợp chưa công bố không lộ điểm. | T3-10; T3-11 |


## 4. Trình tự triển khai và source kế thừa

1. DEV05 đối chiếu CP-WS4 với domain evaluation/rubric sẵn có và `WorkspaceService`, `WorkspaceAuthority`, `CaseRuntime`, `JdbcWorkspaceSession`, `OperationLedger`, `AuditService`. Bổ sung persistence/runtime thiếu, không dựng project hoặc Case owner khác.
2. T3-10: fixture rubric versioned read-only; đúng mỗi criterion một score, weight>0, sum=100, score∈[0,5]. Tính total100=ROUND_HALF_UP(Σ(weight×score/5),2); 40/60 và 5/3 phải 76.00. Snapshot template trong evaluation; DRAFT sửa version/CAS, SUBMITTED/PUBLISHED không ghi đè.
3. DEV06 làm T3-11 sau khi DTO/persistence evaluation đã ổn: school publish/revision; school result policy độc lập; HR org/term và case visibility. Revision mới giữ published pointer cũ cho đến publish mới. Không cho HR/Mentor publish hoặc quản school policy.
4. GET student result chỉ trả published revision nếu ba điều kiện cùng đúng; mọi trường hợp HIDDEN trả evaluation=null, reasonCode=NOT_AVAILABLE. Thiếu record visibility là false. T2 chỉ tiêu thụ DTO này.
5. DEV07 T3-12 dùng App/transport/workflow command hiện có: Mentor chấm/submit, school publish/revision/policy, HR visibility, Student xem kết quả. Loading/error/409 giữ draft, không chỉ ẩn điểm bằng CSS.
6. Chưa kéo normal Case closure/PlacementCompleted vào tuần 4 chỉ để kết thúc demo: đó là gate/event riêng cần kế hoạch và proof đầy đủ. Giữ Case/Placement owner và outbox/inbox hiện tại.

## 5. API target tuần 4 và quyền

Các route sau là **contract target cần triển khai**, không được gọi là đã có ở base:

| Route / DTO | Guard |
|---|---|
| GET `/t3/rubric-templates?termId=...` | t3:workspace:read + đúng scope; fixture versioned, không thêm template CRUD MVP |
| POST `/t3/evaluations`; PUT `/{id}`; POST `/{id}/submit` | EvaluationInput/UpdateEvaluation/SubmitEvaluation; internship:enterprise:evaluate + active Mentor đúng case; current ACTIVE source |
| POST `/t3/evaluations/{id}/publish`, `/revisions` | PublishEvaluation/CreateEvaluationRevision; t3:grades:publish + school delegation, decisionRef/reason/version |
| GET/PUT `/t3/schools/{school}/terms/{term}/result-visibility` | t3:result_policy:manage + school delegation; SetVisibility, expectedVersion0 cho create |
| GET/PUT `/t3/organizations/{enterprise}/grade-visibility` | t3:grade_visibility:manage + HR; HR group chỉ CASE thuộc nhóm, HR enterprise-wide mới ORGANIZATION_TERM |
| GET `/t3/me/results?caseId=...` | Student owner; StudentResultDto, backend triple gate |
| GET `/t3/school/evaluations` | EvaluationQuery, scoped school read purpose; paging chuẩn |

Prefix đầy đủ `/api/v1`. DTO chính xác, nullability, errors, permission/action dùng CP-WS4/C.0/D/E v1.0. Migration mới lấy số global sau HEAD; không sửa V29/V31. Domain+version/history+ledger+audit trong cùng TX. Evaluation evidence dùng authorizer EVALUATION riêng khi có root thật; hiện thiếu adapter này, giữ fail closed tới khi owner T3 cung cấp, phối hợp scanner T1-07.

## 6. Acceptance và negative cases

- Rubric thiếu/lặp criterion, sai version, weight không tổng100, score âm/>5, coercion/missing/unknown field; score và total precision HALF_UP đúng.
- Concurrent update/submit cùng expectedVersion; replay cùng key/hash; key đổi body; audit fault rollback cả revision/pointer/ledger.
- Mentor sai nhóm, HR giả Mentor, revoked parent/handover, cross-school/cohort/case; source CANCELLED trước khi event đến. Đọc lịch sử v1/v2 không mất review cũ.
- PUBLISHED × school × HR đủ 8 tổ hợp; case override org/term, thiếu policy false, student khác case denied; API/error/network không chứa điểm DRAFT/SUBMITTED hoặc published chưa được phép.
- Browser thật đủ Mentor/HR/School/Student, reload, đổi scope/session, mobile; T2 consumer phải dùng API T3 thật.
- Targeted baseline: `.\mvnw.cmd -B '-Dit.test=T307SubmissionRuntimeIT,T308CommentRuntimeIT,T308ReportRuntimeIT,T309WorkflowBrowserIT,InternshipRuntimeIT,EnterpriseProposalIT' '-Dfrontend.e2e=true' '-Dt5.week3.browser=true' verify`; thêm các IT evaluation/visibility mới khi thực sự đã viết.

## 7. Demo và phụ thuộc

Kế thừa DB riêng có Placement ACTIVE và Case/group/Mentor thật từ fixture có nhãn. Student nộp URL v1 → Mentor yêu cầu sửa → Student start/nộp v2 → đọc lại v1; report draft/submit/review/revision → Mentor chấm rubric → HR bật visibility nhưng school chưa publish, student vẫn HIDDEN → school publish và bật policy → Student thấy đúng published revision. T1 identity/delegation/ledger/files và T5 Placement đã có; chỉ phối hợp scanner/evaluation file policy và consumer kết quả tuần 4 còn thiếu. Không chờ ký lại contract hoặc dựng lại workflow tuần 3.

## 8. Môi trường và phụ thuộc thực sự

- Chỉ dùng MySQL/Testcontainers hoặc stack dev riêng. Không seed/import, đổi password/membership, rebuild/deploy hay sửa volume/database của `utbridora-local:8088`. `.env.local-demo` là secret, không in/commit/đưa vào build context.
- Không deploy trong lượt tích hợp này; UI đang chạy tại 8088 có thể chưa phản ánh main mới. Xem runtime guide trước khi tự dựng bản riêng.
- Sự cố đã khắc phục trong lượt test local: tài nguyên Docker cạn làm backend cũ bị OOM-kill. Đã dừng local IT, chỉ dọn container test và start lại đúng container/image backend cũ; bốn container shared đã healthy. Xem runtime/evidence; không coi đây là lần deploy source mới.
- File mới luôn PENDING. Storage/upload/binding/CLEAN guard đã có; **scanner T1-07 chưa có**. CLEAN trong IT là fixture đầu ra scanner và không chứng minh antivirus chạy. Không fake CLEAN trong production.
- Số migration là toàn repo: default `db/migration/base` (V1–V27, V33) + `db/migration/main` (V28/T1, V29/T3, V31/report), Java V32 hội tụ. V1–V31 gốc không đổi checksum. DB đã dùng nhánh T2/T4/T5 phải dùng đúng locations theo runtime guide; không đổi history, repair, reset hay bật out-of-order.
- Giữ cột GPA/dữ liệu lịch sử của DB T4 nếu đã có; API mới theo contract v1.0 không mở GPA. Legacy minGpa=null đọc tương thích trong bộ đọc persistence; minGpa khác null cần owner review, không âm thầm bỏ predicate. Chạy preflight và thử trên bản sao trước nâng cấp.
- Location GPA `t4-v30`/`t4-v31` chỉ được thêm khi DB đã áp dụng script tương ứng. DB chưa chạy V31 không mới backfill JSON revision/snapshot; giữ immutable JSON/hash. Bảng locations trong runtime guide là nguồn chuẩn cho từng lịch sử.
- Chưa có bằng chứng human QA/Lead, dữ liệu thật được phép, 5 team truy cập hoặc benchmark 400/5.000 SV. 1.000 cặp fixture không phải 1.000 sinh viên và không thay workload 400.

## 9. Cách chạy kiểm tra và bàn giao lại

Chạy ở root với JDK21, Docker và Node/npm. Mọi IT bên dưới tự tạo DB riêng; không trỏ chúng vào DB chung. Browser chạy tuần tự ở 5178; không chạy nhiều harness đồng thời. Lệnh đầy đủ giống CI:

```powershell
Push-Location web-demo
npm.cmd ci
npm.cmd run build
Pop-Location
.\mvnw.cmd -B '-Dfrontend.e2e=true' '-Dt5.week3.browser=true' verify
```

Contracts dùng virtualenv có `scripts/contracts/requirements.txt`:

```text
python scripts/contracts/build_contract_bundle.py --check
python scripts/contracts/verify_contracts.py --output target/contract-verification.json
python scripts/contracts/test_audit_registry.py
python scripts/contracts/test_bundle_portability.py
python scripts/fixtures/t4/verify_s1_s11_fixture.py
```

Sau khi thay DTO/provider, kiểm cả consumer, lỗi 400/401/403/404/409/422, current permission, CSRF, idempotency và audit rollback. Không sửa assertion/expected để ép PASS; giữ report đỏ và chạy lại phần bị ảnh hưởng trước regression cuối.

Checklist gửi lại:

- [ ] Branch/SHA, base main, diff và nguồn kế thừa; không commit secret/file tạm hoặc xóa nhánh.
- [ ] Task nào code đã có, còn thiếu hoặc chưa nghiệm thu; phân biệt fixture, unit, API, browser và QA.
- [ ] API/DTO/port, permission, migration mới và provider/consumer cùng version; không trùng owner/route/table.
- [ ] Lệnh, môi trường, SHA/source hash, số PASS/FAIL/SKIP/NOT RUN; log/JUnit/browser screenshot đúng lần chạy.
- [ ] Cross-org/scope, stale/version, replay, concurrency, rollback audit và file/consent negative cases.
- [ ] Giới hạn, phụ thuộc còn mở; QA/Lead xác nhận riêng; fetch main lại trước push và theo CI đúng SHA.
