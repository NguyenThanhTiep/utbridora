# Bàn giao T4 — kết quả tuần 2 và hướng dẫn tuần 3

Ngày lập: 09/11/2026. Người nhận: Team Matching, DEV08/DEV09 và QA04. Phạm vi tiếp theo: protected aggregate cho doanh nghiệp và UI kết quả trường trên engine/provider đã có.

## 1. Phiên bản và căn cứ

- Bản phát hành tuần 2 trên `main` chứa code, tests và tài liệu bàn giao này; base `6687a940cd2f42a6351fb6c3511286c763c81f6b`, nhánh triển khai `feature/week2-implementation`. Nhận đúng commit tuần 2 bằng fetch/pull trên checkout sạch của team và kiểm Git log.
- Worktree tạo bản bàn giao: `D:/HKDN-TTTV-UTH/.work/week2-20261109/repo`. Kiểm các lớp mục 3, migrations đến V27 và evidence tuần 2 trước khi làm tiếp; kế thừa engine hiện có.
- Hash snapshot source chuẩn hóa LF: `77e7dda5c8e9e5d6f035609e4f2ac06b8ac6d8f7ca557c9c47e5ca9a5f932e63`, không phải commit Git.
- [Plan](../plans/UTBridora_Plan.xlsx): CAP NHAT CHUNG tuần 2 dòng 32–33; tuần 3 dòng 48–49, QA04 dòng 56. T4-05 DEV08; T4-06 DEV09.
- [SRS v1.2.4](../srs/UTBridora_SRS_Tong_The_v1.2.4.docx): §16.3 S1–S11; US-T4-01/02/03/04/07/10; §24.2; §25.8.2 và G10. Ghi nhận k=3 trong phần nguồn cũ đã được quyết định tổng thể thay bằng **k=5 + protected release**.
- [Contract v1.0](../contracts/UTBridora_Contract_Pack_B01_B06_v1.0_Candidate.md): BC11, BC31/32, BC24, registry D/E; [quyết định chốt](../contracts/README.md). Không chờ ký lại contract đã được người dùng chốt; v1.1 chỉ là bổ sung kỹ thuật cần đối chiếu.
- [Tuần 2 acceptance](../testing/WEEK2_ACCEPTANCE.md), [evidence](../testing/evidence/WEEK2_DEV_VERIFICATION.json), [runtime](../guides/WEEK2_RUNTIME.md) ưu tiên hơn báo cáo lịch sử chưa có classifier.

Evidence giữ nguyên trạng thái tại thời điểm kiểm chứng trước commit (`uncommitted=true`, CI chưa chạy khi ghi nhận); xem Git log và GitHub Actions của commit nhận được để biết trạng thái phát hành/CI mới nhất.

## 2. Tuần 2 đã triển khai

| Task/owner | Đã có | Evidence/giới hạn |
|---|---|---|
| T4-03 — DEV08/QA04 | Strict classifier thật, Required AND, FAIL ưu tiên UNKNOWN; undeclared khác declared empty; exact alias, level/years, union ngày inclusive | StrictClassifierTest chạy đủ expected S1–S11 độc lập và các ca Preferred/date boundary |
| T4-04 — DEV09/QA04 | MatchRun/results persisted với criteria/dataset/profile/rule/taxonomy/scoring refs; score Preferred HALF_UP hai chữ số chỉ MATCH, tie theo studentUserId; school evaluate/details API | Week2WorkflowIT gọi T5 DemandCriteria và T2 snapshots thật rồi nominate; engine không dùng fixture result giả |
| Phần policy phụ thuộc T2-04 | Pure CompletenessPolicy và CompletenessPort/Service đúng sáu check | T2 gọi policy thật khi read/save profile; không coi complete là MATCH hoặc schoolApproved |
| Integration dùng lại | Taxonomy/alias/reference T4, T2 immutable snapshot, T5 criteria validation và canonical nomination path | School results có ID/criterion evaluation; enterprise aggregate chưa được mở |

T4-05 privacy release/ledger/bands và T4-06 UI kết quả trường **chưa triển khai**. Chỉ có engine/backend đủ để bắt đầu, không gọi synthetic fixture hoặc schema ProtectedAggregate là privacy runtime đã PASS.

Kết quả chung đã ghi: 485 unit PASS; 125 integration PASS theo report mới nhất từng class; 87 Playwright PASS + 4 skipped theo fixture; build PASS; 868 contract checks PASS. Full run có một lỗi helper login/CSRF trong Week2 browser; sửa test rồi recheck riêng thành công. Chưa có human QA/Lead signoff hoặc xác nhận truy cập từ 5 team.

## 3. Source và behavior phải giữ

| Source | Vai trò |
|---|---|
| [StrictClassifier](../../src/main/java/vn/utbridora/auth/modules/matching/domain/StrictClassifier.java) | Pure strict policy; RULE_VERSION=strict-v1, SCORING_VERSION=preferred-v1 |
| [CompletenessPolicy](../../src/main/java/vn/utbridora/auth/modules/matching/domain/CompletenessPolicy.java), [CompletenessPort](../../src/main/java/vn/utbridora/auth/modules/matching/port/CompletenessPort.java) | Sáu checks, policy demo-v1; không thêm CV/GPA |
| [MatchingService](../../src/main/java/vn/utbridora/auth/modules/matching/application/MatchingService.java), [MatchingController](../../src/main/java/vn/utbridora/auth/modules/matching/api/MatchingController.java) | School runtime; evaluate trả audience=SCHOOL; details phân trang và ghi audit |
| [StudentProfileSnapshots](../../src/main/java/vn/utbridora/auth/modules/student/application/StudentProfileSnapshots.java), [snapshot port](../../src/main/java/vn/utbridora/auth/modules/student/port/StudentProfileSnapshotPort.java) | T2 owner; frozen refs/hash/current check; capture hiện từ dữ liệu liên kết active |
| [DemandCriteriaPort](../../src/main/java/vn/utbridora/auth/modules/cooperation/integration/DemandCriteriaPort.java), [facade](../../src/main/java/vn/utbridora/auth/modules/cooperation/integration/DemandCriteriaFacade.java) | Provider T5 thật; server-owned auth/context; không lấy criteria từ browser |
| [ProposalService](../../src/main/java/vn/utbridora/auth/modules/cooperation/application/ProposalService.java), [ProposalController](../../src/main/java/vn/utbridora/auth/modules/cooperation/api/ProposalController.java) | T5 sở hữu nomination/batch/quota và đang giữ mapping REST nominate |
| [V23](../../src/main/resources/db/migration/base/V23__student_matching_snapshots.sql) | student_snapshot_sets, student_profile_snapshots, match_runs, match_results; không tạo bảng run trùng |
| [Oracle S1–S11](../testing/fixtures/t4-s1-s11.json), [StrictClassifierTest](../../src/test/java/vn/utbridora/auth/modules/matching/StrictClassifierTest.java) | Expected độc lập, runtime assertions; nhãn NOT_RUN trong fixture là lịch sử của artifact tuần 1 |

Giữ các invariant sau khi làm UI/privacy:

- CT-01 dùng canonical skills; note/custom không tự thành alias hoặc implies. “Spring Boot” không tự chứng minh “Java”.
- `skillsDeclared=false` → UNKNOWN; `true + []` thiếu required skill → FAIL. Required không có skills vẫn kiểm major/availability.
- FAIL thắng UNKNOWN. Chỉ MATCH có fitScore; MISSING_DATA/NOT_MATCH phải null. Preferred UNKNOWN tính 0; không Preferred thì MATCH score 0.00.
- availablePeriods hợp theo ngày inclusive phải phủ toàn required range. Threshold level/years là ≥ khi có ngưỡng.
- Kết quả run cũ bất biến. Thay profile/criteria cần current check hoặc run mới; sửa phone/note có relevantHash không đổi không tự ép rerun.

## 4. API runtime và target tuần 3

| Trạng thái | Route |
|---|---|
| Đã có, nhánh school | POST `/api/v1/matching/evaluate` |
| Đã có | GET `/api/v1/matching/runs/{id}/details?overallStatus=MATCH&page=0&size=25`; status còn MISSING_DATA, NOT_MATCH |
| Đã có, controller hiện thuộc T5 | POST `/api/v1/matching/runs/{id}/nominate` |
| Cần bổ sung cho T4-05 | Enterprise branch của evaluate và GET `/api/v1/matching/runs/{id}/summary` theo BC32 |
| Chưa mở | Enterprise breakdown nhiều ô; phải giữ PRIVACY_BREAKDOWN_DISABLED nếu có target được gọi |

Evaluate body hiện tại, không thêm audience/counters từ browser:

```json
{"demandId":"55555555-5555-4555-8555-555555555555","expectedDemandVersion":2,"expectedCriteriaVersion":1}
```

ID/version là ví dụ; lấy giá trị từ T5 thật. Audience do server quyết định từ role/permission/scope. Enterprise có role ceiling matching:run:execute trong V22 vẫn không được dùng nhánh school; MatchingService hiện gọi readForSchool nên enterprise đang bị chặn.

## 5. T4-05 — triển khai privacy gate theo thứ tự

### Bước 1 — Tách school flow và protected release flow

Giữ school summary/details đang hoạt động. Enterprise dùng own enterprise/school/term từ provider và permission hiện hành, không tin X-Organization-Id hoặc audience trong body để chọn quyền. Kiểm thêm `matching:enterprise:aggregate_read` theo registry; role ceiling không thay scoped ownership.

Thêm feature gate mặc định đóng cho enterprise aggregate cho đến khi QA04 privacy tests PASS. Tên cấu hình là lựa chọn implementation, chưa có property runtime được cam kết trong snapshot này.

### Bước 2 — Frozen dataset và demoBatch do Ops kiểm soát

- Tạo mô hình server-side chọn datasetSnapshotId đã đóng băng và demoBatchId; enterprise không gửi/thay/reset batch để làm release mới.
- Không dùng lại school evaluate hiện tại một cách nguyên trạng: method đó capture dataset mới mỗi lần. Enterprise release phải dùng snapshot đã đóng băng, không query live rồi công bố số mới.
- Demo seed receipt không thay cho privacy batch/review. T1/Ops chọn đợt mới sau privacy review T4+QA; test có thể setup đợt synthetic riêng và phải ghi rõ.
- Không gộp actor hoặc demandId vào scope ledger để vô tình cho công bố thêm.

### Bước 3 — Ledger và fingerprint

Thêm migration mới sau V27 được phối hợp số GLOBAL với T1. Ledger cần UNIQUE:

```text
(enterpriseOrgId, schoolOrgId, termId, demoBatchId)
```

Fingerprint theo SRS §25.8.2:

```text
SHA-256(canonical JSON {
  enterpriseOrgId, schoolOrgId, termId, datasetSnapshotId,
  criteriaHash, profileSnapshotVersion, ruleVersion, taxonomyVersion
})
```

Sort canonical object keys, dùng refs/version thật. Không thêm actor, timestamp hoặc demandId để cho phép query gần giống vượt guard. Không chỉ dựa Idempotency-Key: client đổi key vẫn phải gặp cùng business ledger.

Trong một transaction, khóa scope ledger, kiểm current authority và refs, tạo/reuse run/release, lưu **DTO đã bảo vệ** và audit:

1. Chưa có release: tính band, insert ledger/release dưới unique constraint.
2. Cùng fingerprint: replay đúng DTO đã phát hành, không tính lại theo database live.
3. Khác fingerprint trong cùng batch: 409 PRIVACY_RELEASE_LOCKED, không release thứ hai, không counters hoặc số chênh trong error.
4. Race hai request đầu: unique+lock đảm bảo một release; request còn lại replay hoặc bị khóa đúng fingerprint.

### Bước 4 — Chỉ serialize DTO bảo vệ

| Số MATCH nội bộ | eligibleBand duy nhất được công bố |
|---|---|
| 0, 1, 2, 3, 4 | SUPPRESSED |
| 5–9 | 5-9 |
| 10–19 | 10-19 |
| ≥20 | 20+ |

ProtectedAggregate có đúng `releaseId,matchRunId,eligibleBand`; evaluate enterprise trả `{audience:"ENTERPRISE",release:...}`. Không serialize total, rawCount, profileCount, %, page, student IDs, tên/MSSV/email, fingerprint hoặc criterion breakdown. Không tái dùng SchoolMatchSummary rồi xóa vài cột ở frontend.

CSV/chart nếu có phải dùng chính DTO protected, không chạy SQL/export raw riêng. Error/debug payload cũng không được làm lộ counts/PII. Đây là hạn chế suy diễn, không phải cam kết ẩn danh tuyệt đối.

### Bước 5 — Audit/guard và rollout

Đối chiếu E cho PRIVACY_RELEASE_CREATED và PRIVACY_RELEASE_BLOCKED. WorkflowAuditRegistry hiện chỉ có subset tuần 2; action BLOCKED có result DENIED, không ghi nhầm SUCCESS bằng helper hardcode success. Phối hợp mở rộng AuditService của T1 đúng cơ chế, không tạo service audit thứ hai; lỗi bảo vệ vẫn deny.

Thêm route/permission/error-envelope vào SecurityConfig/DemandSecurityErrors đúng phạm vi nếu cần; không mở wildcard authenticated để thay domain guards. Chỉ bật enterprise gate sau khi có kết quả ma trận QA04 ở mục 7.

## 6. T4-06 — UI kết quả trường và nominate

1. Thêm màn trong cùng React App/SchoolWorkspace, dùng transport `web-demo/src/api.ts`. Provider T2/T5 đã có; không viết lại classifier hoặc gắn fixture outcomes cố định vào UI.
2. Chọn demand/version có scope từ T5, gọi evaluate, hiển thị run refs/rule/taxonomy/version và ba tab Đáp ứng/Thiếu dữ liệu/Không đáp ứng.
3. GET details theo overallStatus và page/size. Giải thích từng criterion PASS/FAIL/UNKNOWN; chỉ xếp hạng MATCH.
4. Nếu cần tên/MSSV/major/skills, compose qua ProfileAccess với purpose=SCHOOL_REVIEW và explicit `student_profile:school:read` + profileReadFields. Matching detail permission không tự cho đọc email/phone; không mirror PII vào match_results hoặc mở ADMIN bypass.
5. Chỉ chọn MATCH. Lấy capacity T5; “chọn tất cả” trong trang đang hiển thị không vượt openForNomination và không tự submit.
6. POST canonical nominate với expectedDemandVersion, expectedCriteriaVersion, expectedCooperationVersion, expectedBatchVersion. Lần tạo batch đầu dùng null; append cùng batch dùng version thật.
7. Hiện stale/profile/eligibility/capacity lỗi rõ; giữ lựa chọn hợp lý và reload current state, không tự bỏ gate hoặc đổi criteria.
8. Đổi skill/criteria qua API owner, chạy lại và reload UI để chứng minh kết quả thay đổi thật.

**Tránh duplicate route/vòng phụ thuộc:** nominate hiện nằm ở ProposalController của T5, ProposalService đọc MatchingService. Không thêm cùng @PostMapping ở MatchingController. Nếu dựng BC31 typed nomination seam cho T4, tách read-run/candidate port thích hợp trước khi nối T4→T5 để không tạo circular bean MatchingService↔ProposalService.

## 7. QA04 — bảng kiểm bắt buộc

| Ca | Expected |
|---|---|
| S1–S11, undeclared/empty, level/years, ngày liền kề | Khớp oracle độc lập; FAIL thắng UNKNOWN; không implies |
| Preferred 2/3, không Preferred, non-MATCH, tie | 66.67; 0.00; null; tie studentUserId ASC |
| Count 0/1/4/5/9/10/19/20 | Bands đúng, DTO/schema không có raw count/PII |
| Cùng fingerprint, khác key và actor | Trả cùng protected release, không row/effect thứ hai |
| Đổi demand/filter/criteria/actor trong cùng org-school-term-batch | Không bypass; fingerprint khác trả PRIVACY_RELEASE_LOCKED |
| Hai enterprise hoặc school khác nhau | Scope tách biệt; không xem release/IDs của bên khác |
| 20 request tạo release cạnh tranh; lỗi audit/engine | Một release hợp lệ hoặc rollback; không partial counters |
| JSON/chart/CSV/error/browser network | Không rawCount/total/%/fingerprint/profileCount/PII, kể cả payload ẩn |
| Enterprise cố details hoặc chọn audience SCHOOL | Bị chặn; ADMIN không mặc nhiên có hồ sơ |
| School UI sửa kỹ năng/criteria rồi rerun/nominate | Provider thật, kết quả/version mới; stale run bị chặn tại T5 |
| Snapshot/batch đóng băng, sửa DB live sau release | Replay DTO cũ, không tính/release mới |

Kế thừa `StrictClassifierTest`, `T4MatchingFixtureIT`, `Week2WorkflowIT`; fixture IT chứng minh fixture load/reapply, không thay privacy runtime tests. Test mới đề xuất: `ProtectedAggregateIT`, `matching-runtime.spec.ts` (hiện chưa có).

```powershell
.\mvnw.cmd '-Dtest=StrictClassifierTest' test
.\mvnw.cmd '-Dit.test=T4MatchingFixtureIT,Week2WorkflowIT' '-Dfrontend.e2e=true' verify
Push-Location web-demo
npm.cmd run build
Pop-Location
```

Sau khi có privacy test, chạy thêm test mới và contract/consumer checks. Full verify khi hội tụ; không tự đổi expected oracle để tạo PASS. Playwright chạy tuần tự ở 5178 trên DB riêng; ghi rõ benchmark nào chưa chạy, không suy KPI 5.000 profiles từ fixture hoặc unit test.

## 8. Bàn giao lại và bảo toàn môi trường

Gửi branch/SHA/snapshot, source/migration/DTO đổi, schema enterprise actual JSON, bảng privacy PASS/FAIL/NOT RUN, evidence replay/lock/race, screenshot school UI và kết quả consumer T5. Nếu privacy chưa PASS, enterprise gate tiếp tục đóng; không trì hoãn UI school độc lập.

Máy mới dùng [LOCAL_DEMO](../guides/LOCAL_DEMO.md) với DB riêng. Stack chung utbridora-local:8088 vẫn tuần 1; không seed/import/reset volume, không in secret, không reset/clean repo gốc hoặc phục hồi f606b90. Không commit/push/merge khi chưa được yêu cầu. Human QA/Lead và 5 team access theo dõi riêng.

Phối hợp theo [T2](T2_TUAN2_BAN_GIAO_TUAN3.md), [T5](T5_TUAN2_BAN_GIAO_TUAN3.md), [T3](T3_TUAN2_BAN_GIAO_TUAN3.md).
