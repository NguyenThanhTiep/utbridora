# Bàn giao T4 từ tuần 3 sang tuần 4

Ngày bàn giao: 13/11/2026. Đây là bàn giao DEV sau tích hợp; QA, Lead và nghiệm thu SRS là các xác nhận riêng, chưa được thay mặt ký.

## 1. Nhận đúng base và nguồn

- Commit code tích hợp đã kiểm chứng trước commit tài liệu: `450e78d1fbd27a2da46462d3899239185cb7b946`. Nhận từ `origin/main` bằng fetch/pull trên checkout sạch; đối chiếu SHA và evidence bên dưới. Không lấy SHA của chính commit tài liệu để tạo vòng lặp sửa commit.
- Base main trước tích hợp: `e22596ac9b40ecaf7b4896ec9199ccf583b6a36c`. T1-06 nguồn: `065c5be15f19df2fe8100c42248c5bd6d9c4a956`. Không làm lại delegation, ActorContext, ledger/audit hay storage đã có.
- Nguồn yêu cầu: [Plan](../plans/UTBridora_Plan.xlsx), sheet **CAP NHAT CHUNG**; [SRS v1.2.4](../srs/UTBridora_SRS_Tong_The_v1.2.4.docx), ưu tiên §25.8; [Contract v1.0 đã chốt 05/11](../contracts/README.md). v1.1 Candidate và các GET bổ sung của T3 là diff kỹ thuật, không tự thành quyết định phê duyệt thay v1.0.
- [Ma trận tuần 3](../testing/WEEK3_INTEGRATION_ACCEPTANCE.md), [evidence đúng source](../testing/evidence/WEEK3_INTEGRATION_VERIFICATION.json), [runtime và nâng cấp DB](../guides/WEEK3_INTEGRATION_RUNTIME.md). Đọc báo cáo mới trước các ghi nhận lịch sử “chờ T1/team khác”.
- [CI đúng code SHA](https://github.com/NguyenThanhTiep/utbridora/actions/runs/43352236892) PASS: 1270 unit; 223 integration; 164 browser PASS + 16 SKIP theo context harness, không có FAIL/ERROR. Các luồng runtime thật có harness riêng. Contract 868 PASS, 0 FAIL; build/typecheck frontend PASS. Đây là bằng chứng DEV.
- Giữ các nhánh nguồn sau merge. Không phục hồi QA `f606b90`, không đọc/merge nhánh tester/QA. Tests đã có trong main/DEV vẫn thuộc regression.

## 2. Trạng thái tuần 3 và bằng chứng

- Nguồn T4: `9420f33becbea209e87b60164787db828630b27e`; đã tích hợp T4-05 protected release k=5 và T4-06 UI trường nhận T2 snapshots/T5 criteria thật. T1/T2/T5 providers có sẵn, không còn lý do dựng mock kết quả production.
- GPA mở rộng trong commit `9420f33` được loại khỏi API/rule tích hợp vì contract v1.0 quy định gpa/transcript chưa có data source MVP. Giữ `strict-v1`, sáu completeness checks và canonical skill semantics. Lịch sử nguồn và SQL T4 V30/V31 vẫn được lưu để upgrade DB từng lineage, không xóa dữ liệu đã có.
- Release scope UNIQUE(enterprise,school,term,demoBatch); frozen dataset do Ops chọn. Same fingerprint replay, khác fingerprint→409 PRIVACY_RELEASE_LOCKED; gate mặc định đóng. JSON/CSV/chart chỉ protected DTO; GET cũng kiểm feature gate và current authority.
- DEV tests: bands 0/1/4/5/9/10/19/20 qua HTTP, schema/no PII, denied detail/export, audit rollback, race20; browser trường rerun sau sửa skill qua T2 thật. Đây chưa phải QA04 privacy signoff hoặc benchmark.

## 3. Công việc tuần 4 đúng Plan

| Mã / dòng Plan | Thực hiện | QA | Công việc nguyên văn | Acceptance nguyên văn | Phụ thuộc Plan |
|---|---|---|---|---|---|
| T4-07 / 65 | DEV08 | QA04 | Negative/security test, query privacy + performance nhỏ | Test fixture 0/1/4/5/10/20; thử hai truy vấn 10/10/10 và 9/10/11, kiểm tra HTTP 409 và không tiết lộ tổng/phần trăm. | T4-05 |
| T4-08 / 66 | DEV09 | QA04 | UI doanh nghiệp chỉ hiện băng thống kê và đề xuất | QA04 thử số 0/1/4/5/10/20 và truy vấn fingerprint khác phải 409, không rò qua biểu đồ hay export. | T4-05; T4-06 |


## 4. Trình tự triển khai và source kế thừa

1. DEV08 tái dùng `StrictClassifier`, `ProtectedAggregatePolicy`, `MatchingService`, `MatchingController`, snapshot provider T2 và criteria provider T5; không viết engine/ledger/privacy owner mới.
2. T4-07 mở rộng adversarial matrix theo QA04: cùng scope đổi actor/demand/key/export không tạo release mới; hai fingerprint với 10/10/10 và 9/10/11 phải khóa query thứ hai, không “trả count đã ẩn trên UI”. Thử cross enterprise/school/term và batch do Ops kiểm soát.
3. Kiểm snapshot/batch đã freeze, live profile/criteria thay đổi và replay cũ; đổi criteriaHash hợp lệ trong cùng batch phải 409, không ghi run/release mồ côi. Giữ one-release ledger ngoài Idempotency-Key.
4. DEV09 kế thừa `cooperation/proposals/EnterpriseMatchingSummary.tsx` đã là consumer protected DTO trong enterprise flow; T4-08 hoàn thiện UX/loading/retry/accessibility ở đó, không tạo panel GPA cũ hoặc endpoint raw export. UI trường `MatchingWorkspace.tsx` tách audience SCHOOL và scope field PII riêng.
5. Query performance nhỏ: ghi môi trường, số **distinct student profiles**, tiêu chí, cold/warm, concurrency, p95/error/timeout và SHA/checksum. Không gọi 1.000 cặp fixture là 1.000 SV hay claim 400/5.000 NFR chưa chạy.
6. Chỉ đề nghị Ops bật gate sau matrix QA04 được con người xác nhận. Bản tích hợp để gate đóng; test enable trong DB riêng không cấp quyền rollout.

## 5. API/DTO, guard và migration

| Phần | Cần giữ |
|---|---|
| POST `/matching/evaluate` | Evaluate chỉ demandId + expectedDemandVersion + expectedCriteriaVersion; audience server-owned; UUID Idempotency-Key |
| ENTERPRISE | `{audience:"ENTERPRISE",release:{releaseId,matchRunId,eligibleBand}}`, bands SUPPRESSED/5-9/10-19/20+; không total/rawCount/%/fingerprint/PII |
| GET `/matching/runs/{id}/summary[.csv\|.chart]` | Cùng feature/current own-enterprise guard và protected DTO. School details không trở thành enterprise route |
| SCHOOL | summary/details 3 nhóm, Preferred score chỉ MATCH, paging/ranking; danh tính qua BC24 + field-specific delegation, không mirror PII trong match_results |
| Nominate | Route canonical vẫn ở T5 ProposalController; dùng current version/capacity/snapshots/eligibility; không thêm route duplicate ở MatchingController |
| Privacy migrations | SQL T4 V28/V29 trong `db/legacy/t4`; default tạo qua Java V32. V33 history scheduler thuộc tích hợp chung, không dùng lại số này |

`matching:enterprise:aggregate_read` không cấp quyền detail. ADMIN không tự có hồ sơ. Audit create/blocked chỉ fields E.2, result DENIED đúng; lỗi audit denial vẫn deny, không xuất input/filter/count vào error. Fingerprint canonical các field §25.8.2; không thêm demand/actor/time để bypass.

## 6. Acceptance và test commands

- Giữ S1–S11, FAIL>UNKNOWN, declared empty khác undeclared, alias exact/no implies, date inclusive union, level/years ≥, Preferred HALF_UP/tie stable.
- Bands đúng tại 0/1/4/5/9/10/19/20. Mọi output JSON/chart/CSV/error/network không có hidden raw series hoặc count trên tooltip; không chạy SQL export riêng.
- Same fingerprint khác key/actor/demand replay đúng release. Different fingerprint trong scope khóa; 20 request tạo lần đầu một release/effect; audit/engine lỗi rollback toàn bộ; current scope revoke không replay data đã mất quyền.
- UI enterprise chỉ eligibleBand và proposal được share riêng; proposal API có whitelist không được ghép với matching thành PII bypass. UI trường sửa skill/criteria ở provider, rerun thay đổi kết quả thật và stale nomination bị chặn.
- Targeted: `.\mvnw.cmd -B '-Dtest=StrictClassifierTest,ProtectedAggregatePolicyTest' '-Dit.test=EnterpriseProposalIT,Week2WorkflowIT,T4MatchingFixtureIT' '-Dfrontend.e2e=true' '-Dt5.week3.browser=true' verify`.
- DEV privacy regression có trong EnterpriseProposalIT; test source fixture bands có nhãn, không thay human QA04. Performance chưa đo phải ghi NOT RUN.

## 7. Demo

Trên DB riêng: Ops fixture freeze dataset và chọn batch → enterprise query một lần → thấy band → actor khác/same criteria demand khác nhận cùng release → đổi fingerprint nhận409 → thử JSON/CSV/chart/detail/network. Sau đó school chạy matching từ T2/T5 thật, thay skill và rerun. Chụp desktop/mobile. Giữ gate shared/demo production đóng nếu chưa có QA04 signoff.

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
