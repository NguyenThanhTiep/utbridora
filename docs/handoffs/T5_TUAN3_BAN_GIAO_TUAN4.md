# Bàn giao T5 từ tuần 3 sang tuần 4

Ngày bàn giao: 13/11/2026. Đây là bàn giao DEV sau tích hợp; QA, Lead và nghiệm thu SRS là các xác nhận riêng, chưa được thay mặt ký.

## 1. Nhận đúng base và nguồn

- Commit code tích hợp đã kiểm chứng trước commit tài liệu: `450e78d1fbd27a2da46462d3899239185cb7b946`. Nhận từ `origin/main` bằng fetch/pull trên checkout sạch; đối chiếu SHA và evidence bên dưới. Không lấy SHA của chính commit tài liệu để tạo vòng lặp sửa commit.
- Base main trước tích hợp: `e22596ac9b40ecaf7b4896ec9199ccf583b6a36c`. T1-06 nguồn: `065c5be15f19df2fe8100c42248c5bd6d9c4a956`. Không làm lại delegation, ActorContext, ledger/audit hay storage đã có.
- Nguồn yêu cầu: [Plan](../plans/UTBridora_Plan.xlsx), sheet **CAP NHAT CHUNG**; [SRS v1.2.4](../srs/UTBridora_SRS_Tong_The_v1.2.4.docx), ưu tiên §25.8; [Contract v1.0 đã chốt 05/11](../contracts/README.md). v1.1 Candidate và các GET bổ sung của T3 là diff kỹ thuật, không tự thành quyết định phê duyệt thay v1.0.
- [Ma trận tuần 3](../testing/WEEK3_INTEGRATION_ACCEPTANCE.md), [evidence đúng source](../testing/evidence/WEEK3_INTEGRATION_VERIFICATION.json), [runtime và nâng cấp DB](../guides/WEEK3_INTEGRATION_RUNTIME.md). Đọc báo cáo mới trước các ghi nhận lịch sử “chờ T1/team khác”.
- [CI đúng code SHA](https://github.com/NguyenThanhTiep/utbridora/actions/runs/43352236892) PASS: 1270 unit; 223 integration; 164 browser PASS + 16 SKIP theo context harness, không có FAIL/ERROR. Các luồng runtime thật có harness riêng. Contract 868 PASS, 0 FAIL; build/typecheck frontend PASS. Đây là bằng chứng DEV.
- Giữ các nhánh nguồn sau merge. Không phục hồi QA `f606b90`, không đọc/merge nhánh tester/QA. Tests đã có trong main/DEV vẫn thuộc regression.

## 2. Trạng thái tuần 3 và bằng chứng

- Nguồn T5: `850a41c027f75660e4643b9c59d09141a8d0c51e`. T5-06 có whitelist share/revoke/regrant, enterprise list/decision/projection; T5-07 create PENDING_APPROVAL→APPROVED→ACTIVE/cancel + outbox/history; T5-09 UI school/enterprise thật.
- T2 Opportunity consumer đã nối port T5, opportunityId=cooperationId; publication/window/school-term là authoritative T5. Consent/profile/eligibility vẫn T2; FileService T1 và case/projection T3.
- Sửa tích hợp: current eligibility/profile/criteria ở create/approve; serialize demand/quota, active-key DB; scheduler UTC dùng cùng activation guard với manual, SYSTEM actor rõ trong V33 history. Current term/org/school verification/effectiveAt/version được kiểm trong transaction, audit/outbox lỗi rollback.
- Đã kiểm race20 approve, scheduler/manual boundary, duplicate/Activated-before-Approved/cancel projection và shared CV/Range. Existing ACTIVE/verification/CLEAN prerequisites trong fixtures không bị gọi là workflow được nghiệm thu. QA05/QA03/Lead chưa signoff.

## 3. Công việc tuần 4 đúng Plan

| Mã / dòng Plan | Thực hiện | QA | Công việc nguyên văn | Acceptance nguyên văn | Phụ thuộc Plan |
|---|---|---|---|---|---|
| T5-10 / 67 | DEV11 | QA05 | Danh sách/tìm kiếm Placement và summary đúng scope | Tài khoản trường A không thấy Placement trường B; bộ lọc và số liệu phù hợp. | T5-07 |
| T5-11 / 68 | DEV10 | QA05 | Race/capacity/cancel/outbox idempotency cho Placement | 20 yêu cầu đồng thời không tạo hai Placement chiếm chỗ; sự kiện trùng không chạy lại sai. | T5-07 |
| T5-12 / 69 | DEV12 | QA05 | Hoàn thiện UI Placement và thông báo trạng thái | Tải lại trang không mất trạng thái; phiên bản cũ bị từ chối đúng cách. | T5-09; T5-10 |


## 4. Trình tự triển khai và source kế thừa

1. DEV11 đọc `PlacementService`, `PlacementController`, `ProposalService`, `ProposalConsentSummary`, `DemandRepository`, `PlacementReadPort`, `PlacementOutboxWorker`. Danh sách/filter term/state/cooperation, summary và internship-link đã có; chỉ bổ sung search/phân trang/filter còn thiếu theo T5-10. Không viết service/table Placement thứ hai.
2. Với school bỏ term filter, tính total/page sau scoped term guard; summary phải require delegation đúng demand.term. Enterprise own-org, không nhận school counters để né privacy T4. Tách tuyển dụng protected aggregate với operational Placement summary theo contract.
3. DEV10 T5-11 kế thừa race20 trong EnterpriseProposalIT và consumer tests InternshipRuntimeIT: thêm remaining-slot quota race giữa nhiều student, manual/scheduler/cancel interleavings, delivery retry/DLQ/reconciliation. Dùng demand lock trước Placement/item và giữ current read, không COUNT từ snapshot cũ.
4. PENDING không chiếm active key; APPROVED/ACTIVE chiếm, COMPLETED/CANCELLED không. Cancel không đổi thành “xóa row”; giữ history/outbox và release một lần. Consent revoke không tự cancel Placement.
5. DEV12 T5-12 kế thừa `cooperation/proposals/PlacementWorkspace`, `useWorkflowCommand`, `SchoolProposals`, `EnterpriseProposals`, `CaseEntry`. Hoàn thiện refresh/conflict giữ input/key, chặn rời khi request chưa rõ kết quả; không làm bản UI khác trùng route hoặc chép fixture state vào App.
6. Chưa triển khai Complete/CaseClosed shortcut. Normal closure cần T3 published evaluation/reports/tasks + school decision và port proof riêng; không vượt sang task tuần sau.

## 5. Contract/provider/consumer và migration

| Route/seam | Giữ nguyên owner/guard |
|---|---|
| POST `/school/placements` | CreatePlacement theo CP-T5UI1; derive student/scope từ proposal; expectedProposalVersion/expectedEligibilityVersion; state PENDING_APPROVAL |
| POST `/placements/{id}/approve` | PlacementCommand đủ expectedVersion/decisionRef/reasonCode; school delegated đúng term, current gates/quota, UNIQUE active key |
| POST `/placements/{id}/activate` | Chỉ APPROVED, serverNow≥effectiveAt; cùng owner guard với `PlacementActivationWorker` SYSTEM purpose, không tự approve |
| POST `/placements/{id}/cancel` | Open states, reason/version; history+audit+outbox nguyên tử, T3 read current source |
| GET school/enterprise placements, summary, internship-link | Own scoped list; WAITING_HANDOFF/null route tới khi có Case; AVAILABLE relative `/workspace/cases/{caseId}`; T3 tiếp tục kiểm assignment khi mở |
| Profile/consent | T1 ProfileAccessFacade + T2 projection/consent + T5 share/policy; effective fields intersection, revoke/expiry/current authority từng lần đọc |
| Events | PlacementApproved/Activated/Cancelled; schemaVersion1, aggregateVersion tăng; trusted internal T5 outbox context, không REST callback từ browser |

Prefix `/api/v1`; legacy path-variable aliases đang tồn tại, không tạo thêm. Scheduler flag `app.t5.placement-activation.enabled`, delivery flag `app.t5.placement-delivery.enabled`; test tắt scheduling activation và gọi owner/worker rõ để kiểm Clock. History actor_kind USER/SYSTEM từ V33; không impersonate user cho scheduled activation. SQL T5 V28–30 lưu nguyên trong `db/legacy/t5`, convergence V32; locations đúng lineage khi nâng DB đã dùng feature, không repair/history edit.

## 6. Acceptance và dữ liệu tiền điều kiện

- Tạo opportunity APPROVED/published, linked student, invitation ACCEPTED, current consent + school share + enterprise ACCEPT, eligibility schoolApproved/version, criteria/profile current. Không seed vào DB chung hoặc coi fixture các trạng thái này là nghiệp vụ đã nghiệm thu.
- Cùng key replay một effect; changed payload conflict; stale create/approve/activate/cancel version; thiếu decision/reason/unknown/coercion/malformed date trả schema error có requestId.
- School A/B và hai term delegated khác nhau: list/total/summary/search/link không lộ term/resource ngoài scope. Enterprise sai org, revoked account/membership, expired delegation bị chặn trước replay.
- 20 approvals cùng student/term một winner; thêm nhiều student/cùng quantity1 để kiểm quota lock. PENDING không placed; APPROVED/ACTIVE/COMPLETED tính placed; CANCELLED không; reservation+placed không overbook.
- now=effectiveAt đúng ACTIVE, now−1ns deny; term CLOSED, org/school verification revoked, cancel race deny/atomic. SYSTEM history actorId=null, actor_kind=SYSTEM, không tạo fake user.
- Audit/outbox fault rollback state/history/ledger; Activated đến trước Approved, duplicate/stale/cancel trước delivery; retry 1/5/30/300/1800 rồi DLQ, no orphan Case; test reconciliation phần chưa có phải ghi rõ.
- Targeted: `.\mvnw.cmd -B '-Dit.test=EnterpriseProposalIT,InternshipRuntimeIT,Week2WorkflowIT,Week3MigrationIT' '-Dfrontend.e2e=true' '-Dt5.week3.browser=true' verify`.

## 7. Demo và phụ thuộc còn mở

Trên DB riêng, đi school→student→enterprise→school: nominate/invite → student ACCEPT và consent riêng → share → enterprise ACCEPT → create PENDING → approve tương lai thấy T3 WAITING_START → tới effectiveAt activate và delivery thấy IN_PROGRESS → cancel thấy TERMINATED. Reload mỗi bước, revoke consent và thử profile/file cũ; Placement không tự hủy. Demo hiện có kiểm future APPROVED/cancel, test Clock kiểm activation boundary; không đánh đồng ACTIVE fixture cũ với toàn bộ workflow mới. T1 core, T2 Opportunity/eligibility/profile/consent và T3 lifecycle đã có; chỉ phối hợp scanner, tuần 4 evaluation và các test/load/DLQ còn thiếu theo evidence, không chờ lại phần đã tích hợp.

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
