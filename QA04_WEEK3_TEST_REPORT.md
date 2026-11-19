# QA04 – BÁO CÁO KIỂM THỬ & BẢNG TEST CASE PHÂN HỆ T4 (MATCHING & PRIVACY k=5)

**Vai trò:** QA04 (`Ngô Thanh Giàu` / `Thanh-giau`)  
**Phạm vi & Mã task:** `T4-03`, `T4-04`, `T4-05`, `T2-04`, `T5-02` — Thống kê doanh nghiệp `k=5`, Suppression, Zero PII, Chống suy luận chênh lệch (Anti-Differencing), Chống bypass Export/Chart, Ghép dữ liệu thật T2/T5 + UI kết quả & Hiệu năng nhỏ  
**Commits đối chiếu:**
- `0929556266a3735a4f7dd4dc5792d555f03c120a` (`feat(t4): add school matching workspace` — `T4-04; T2-04; T5-02`)
- `9420f33becbea209e87b60164787db828630b27e` (và nền SRS R03/R11 `29ee262e52b4b1eb665d83e699e032c8c784f5ab`)
- `b37fd11ab69e84b8916e14609127732bcfbcf2b6` (`test(t4): add adversarial privacy coverage`)

**Ngày thực thi:** 19–20/11/2026  
**Kết luận cổng phát hành (Release Gate):** **PASS TOÀN BỘ — Đủ điều kiện giữ mở / duyệt tính năng thống kê được bảo vệ cho doanh nghiệp.**

---

## 1. Bộ Test Tự Động Độc Lập Của QA04 & Kết Quả Thực Thi Thực Tế

Tất cả test tự động của QA04 đều gọi **trực tiếp vào mã nguồn production thật** (`ProtectedAggregatePolicy`, `StrictClassifier`, `MatchingController`, `MatchingService`, Spring Security, MySQL 8.4 Testcontainers qua HTTP thật) — **không tự giả lập logic**.

| # | Bộ kiểm thử | Lệnh chạy | Kết quả | Thời gian / Chỉ số |
| :-: | :--- | :--- | :---: | :--- |
| 1 | `QA04MatchingPrivacyTest` *(Unit + Oracle + Smoke Perf của QA04)* | `.\mvnw.cmd -B -o "-Dtest=QA04MatchingPrivacyTest" test` | **5/5 PASS** | `1.875 s` · 400 hồ sơ × 5 kỹ năng: **cold = 26 ms, p95 = 8.490 ms** |
| 2 | `QA04MatchingPrivacyIT` *(Integration thật MySQL 8.4 + HTTP của QA04)* | `.\mvnw.cmd -B -o failsafe:integration-test failsafe:verify "-Dit.test=QA04MatchingPrivacyIT"` | **4/4 PASS** | `79.40 s` · Fixture `0/1/4/5/10/20`, `10/10/10` vs `9/10/11`, Export CSV/Chart, Security & Adapter, Ghép động T2/T5 (`IT04`) |
| 3 | `verify_s1_s11_fixture.py` *(SRS Oracle độc lập)* | `python scripts/fixtures/t4/verify_s1_s11_fixture.py` | **PASS** | 11/11 kỳ vọng SRS §16.3 S1–S11 |
| 4 | `ProtectedAggregatePolicyTest` + `StrictClassifierTest` + `MatchingPerformanceSmokeTest` | `.\mvnw.cmd -B "-Dtest=ProtectedAggregatePolicyTest,StrictClassifierTest,MatchingPerformanceSmokeTest" test` | **7/7 PASS** | `cold = 31 ms, p95 = 8.702 ms` |
| 5 | `EnterpriseProposalIT` *(Privacy/Differencing/Concurrency)* | `.\mvnw.cmd -B failsafe:integration-test failsafe:verify "-Dit.test=EnterpriseProposalIT#protectedReleasesEnforceBandsFrozenReplayGateAndAllHttpRepresentations+privacyReleaseBlocksNearIdenticalDifferencingAcrossActorsAndKeys+protectedReleaseRollsBackAuditFailureAndSerializesTwentyRequests"` | **3/3 PASS** | `95.45 s` · 20 request đồng thời = 1 release; audit rollback sạch |
| 6 | `T4MatchingFixtureIT` *(Ghép dữ liệu thật 1.000 ca)* | `.\mvnw.cmd -B failsafe:integration-test failsafe:verify "-Dit.test=T4MatchingFixtureIT"` | **1/1 PASS** | `70.13 s` · 1.000 ca (`340/330/330`), 0 orphan FK, re-apply idempotent |
| 7 | `Week2WorkflowIT` *(Default-closed, Replay, Adapter, Delegation)* | `.\mvnw.cmd -B -o failsafe:integration-test failsafe:verify "-Dit.test=Week2WorkflowIT"` | **1/1 PASS** | `95.64 s` · Default-closed `503`, replay khác actor/demand, CSV/Chart |
| 8 | `EnterpriseProposalIT` *(Chromium E2E thật `week3-integration-runtime.spec.ts`)* | `.\mvnw.cmd -B -o failsafe:integration-test failsafe:verify "-Dit.test=EnterpriseProposalIT#browserUsesRealSchoolStudentEnterpriseSharingAndPlacementHandoff" "-Dt5.week3.browser=true"` | **1/1 PASS** | `155.4 s` · Chromium thật: UI trường chạy Matching từ T5/T2, SV xóa kỹ năng T2 → chạy lại `MATCH 1 → 0`, `NOT_MATCH 0 → 1` |

---

## 2. Bảng Test Case Chi Tiết (QA04 Test Case Matrix)

### 2.1. Nhóm A — Thống kê doanh nghiệp `k=5`, Suppression & Zero PII (`T4-03`, `T4-04`)

| Mã TC | Mã Task | Hạng mục kiểm thử | Dữ liệu / Điều kiện đầu vào | Các bước thực hiện | Kết quả mong đợi | Kết quả thực tế | Trạng thái |
| :--- | :---: | :--- | :--- | :--- | :--- | :--- | :---: |
| **TC-QA04-01** | T4-03, T4-04 | Nhóm 0 sinh viên `MATCH` | Batch đóng băng có `0` SV đạt `MATCH` | DN (`hr`) gọi `POST /api/v1/matching/evaluate` | HTTP `200`, `eligibleBand = "SUPPRESSED"`, không lộ số `0` | HTTP `200`, `eligibleBand = "SUPPRESSED"`, `summary = null` | **PASS** |
| **TC-QA04-02** | T4-03, T4-04 | Nhóm 1 sinh viên `MATCH` | Batch đóng băng có `1` SV thật đạt `MATCH` (có họ tên + email) | DN gọi `POST /api/v1/matching/evaluate` | HTTP `200`, `eligibleBand = "SUPPRESSED"`, ẩn số `1` | HTTP `200`, `eligibleBand = "SUPPRESSED"` | **PASS** |
| **TC-QA04-03** | T4-03, T4-04 | Nhóm 4 sinh viên `MATCH` (cận dưới `k=5`) | Batch đóng băng có `4` SV thật đạt `MATCH` | DN gọi `POST /api/v1/matching/evaluate` | HTTP `200`, `eligibleBand = "SUPPRESSED"`, ẩn số `4` | HTTP `200`, `eligibleBand = "SUPPRESSED"` | **PASS** |
| **TC-QA04-04** | T4-03, T4-04 | Nhóm 5 sinh viên `MATCH` (đạt ngưỡng `k=5`) | Batch đóng băng có `5` SV thật đạt `MATCH` | DN gọi `POST /api/v1/matching/evaluate` | HTTP `200`, `eligibleBand = "5-9"` | HTTP `200`, `eligibleBand = "5-9"` | **PASS** |
| **TC-QA04-05** | T4-03, T4-04 | Nhóm 9, 10, 19, 20 sinh viên `MATCH` | Fixture `9`, `10`, `19`, `20` SV thật | Gọi `ProtectedAggregatePolicy.eligibleBand` + HTTP `evaluate` (`10`, `20`) | `9 → 5-9`, `10/19 → 10-19`, `20 → 20+`; số âm (`-1`) bị từ chối | Khớp chính xác 100% mọi mốc biên | **PASS** |
| **TC-QA04-06** | T4-04 | Zero PII trên API JSON (`/evaluate`, `/summary`) | SV có tên `Nguyen Van QA04 ...`, email `qa04.sv...@example.test` | Kiểm tra JSON trả về từ `/evaluate` và `GET /matching/runs/{id}/summary` | Root chỉ có `audience`, `release`; `release` chỉ có `releaseId`, `matchRunId`, `eligibleBand`; không có tên, email, `@`, `studentUserId`, `rawCount`, `total`, `%` | Khớp đúng 2 khóa root & 3 khóa `release`; 0 rò rỉ PII/số thô | **PASS** |
| **TC-QA04-07** | T4-04 | Zero PII trên Export CSV (`/summary.csv`) | Release đã phát hành hợp lệ | DN gọi `GET /api/v1/matching/runs/{id}/summary.csv` | HTTP `200`, `text/csv`, đúng 2 dòng, 3 cột `releaseId,matchRunId,eligibleBand`, không chứa PII/`rawCount`/tổng/`%` | Đúng 2 dòng CSV chuẩn, 0 rò rỉ PII/số thô | **PASS** |
| **TC-QA04-08** | T4-04 | Zero PII trên Biểu đồ (`/summary.chart`) | Release đã phát hành hợp lệ | DN gọi `GET /api/v1/matching/runs/{id}/summary.chart` | HTTP `200`, chỉ có `{"eligibleBands":["<band>"]}`, không có tổng/`%`/PII | Chỉ trả đúng khóa `eligibleBands`, 0 rò rỉ | **PASS** |

---

### 2.2. Nhóm B — Negative / Security, Anti-Differencing (`10/10/10` vs `9/10/11`) & Non-Bypass (`T4-05`)

| Mã TC | Mã Task | Hạng mục kiểm thử | Dữ liệu / Điều kiện đầu vào | Các bước thực hiện | Kết quả mong đợi | Kết quả thực tế | Trạng thái |
| :--- | :---: | :--- | :--- | :--- | :--- | :--- | :---: |
| **TC-QA04-09** | T4-05 | Replay truy vấn khi **đổi actor** trong cùng DN | Batch 30 SV (`10 MATCH / 10 MISSING_DATA / 10 NOT_MATCH`) đã phát hành bởi `hr` | `coordinator` (cùng DN, `Idempotency-Key` mới) gọi lại `POST /evaluate` cùng tiêu chí | HTTP `200`, phát lại nguyên `releaseId` & `matchRunId` cũ; không sinh thêm `match_runs` hay dòng ledger | Trả đúng `release` cũ; `matching_privacy_releases = 1` | **PASS** |
| **TC-QA04-10** | T4-05 | Replay truy vấn khi **đổi demand** nhưng cùng tiêu chí | Tạo `demand` mới có cùng `required`/`preferred` trong cùng đợt | `coordinator` gọi `POST /evaluate` trên `demand` mới | Cùng fingerprint canonical → HTTP `200`, phát lại nguyên `release` cũ | Phát lại đúng `releaseId` cũ; ledger giữ `1` dòng | **PASS** |
| **TC-QA04-11** | T4-05 | **Differencing Attack `10/10/10` → `9/10/11`** (đổi bộ lọc) | Batch 30 SV đang khóa ở `10/10/10` (`10-19`); tăng `minYearsPractice +0.5` để tách 1 SV thành `9/10/11` (`5-9`) | Cả `hr` và `coordinator` gọi `POST /evaluate` trên demand đã đổi bộ lọc trong cùng batch | HTTP **`409 PRIVACY_RELEASE_LOCKED`**; body lỗi không chứa `%`, băng mới/cũ hay khóa `total`/`rawCount`/`count`/`matched`/`percent`/`difference`/`fingerprint`; không tạo thêm run; ghi audit `PRIVACY_RELEASE_BLOCKED` (`DENIED`) | Cả 2 actor đều nhận HTTP `409 PRIVACY_RELEASE_LOCKED`; 0 rò rỉ số thực/tổng/`%`; ledger giữ `1` dòng; audit metadata sạch (`5` khóa chuẩn) | **PASS** |
| **TC-QA04-12** | T4-05 | Chống bypass Export/Chart bởi **Ẩn danh, SV, Trường, DN ngoài phạm vi** | Release hợp lệ của DN `id(12)` | Gọi `GET /summary`, `/summary.csv`, `/summary.chart` bằng: Ẩn danh, `student`, `school`, `foreignOrg` | Ẩn danh → `401`; `student`/`school`/`foreignOrg` → `404`, body không chứa `releaseId`/`eligibleBand` | Chặn đủ `401`/`404` trên cả 3 đường dẫn, không lộ dữ liệu | **PASS** |
| **TC-QA04-13** | T4-05 | Chống bypass khi **tắt cổng bảo vệ (Kill-Switch)** | `matching_privacy_release_settings.enabled = false` (mặc định DB là `b'0'`) | DN gọi `POST /evaluate` và `GET /summary`, `/summary.csv`, `/summary.chart` | Tất cả trả **`503 PRIVACY_RELEASE_UNAVAILABLE`** | Trả `503` đồng bộ trên `evaluate`, JSON, CSV và Chart | **PASS** |
| **TC-QA04-14** | T4-05 | Chống DN truy cập chi tiết hồ sơ SV (`/details`) | DN (`hr`) có `runId` hợp lệ | DN gọi `GET /matching/runs/{id}/details?overallStatus=MATCH` | HTTP **`403 ACCESS_DENIED`** | HTTP `403`, bị chặn tại tầng quyền `SCHOOL_STAFF` | **PASS** |
| **TC-QA04-15** | T4-05 | Ràng buộc đồng thời (20 request) & Rollback lỗi Audit | 20 request đồng thời trên cùng batch; tiêm lỗi ghi `audit_logs` | Chạy `EnterpriseProposalIT.protectedReleaseRollsBackAuditFailureAndSerializesTwentyRequests` | Lỗi audit → `500 AUDIT_WRITE_FAILED` & rollback 0 dòng; 20 request đồng thời → đúng 1 `releaseId` / 1 dòng ledger | PASS 100% (`PRIMARY KEY` DB + `FOR UPDATE`) | **PASS** |

---

### 2.3. Nhóm C — Ghép dữ liệu thật, Lỗi Matching / Adapter & Hiệu năng nhỏ (`T4-03`, `T4-05`)

| Mã TC | Mã Task | Hạng mục kiểm thử | Dữ liệu / Điều kiện đầu vào | Các bước thực hiện | Kết quả mong đợi | Kết quả thực tế | Trạng thái |
| :--- | :---: | :--- | :--- | :--- | :--- | :--- | :---: |
| **TC-QA04-16** | T4-03 | Bộ 11 kịch bản Oracle độc lập S1–S11 & Xếp hạng R11 | `docs/testing/fixtures/t4-s1-s11.json` + alias `Java Core → JAVA` | Chạy `QA04MatchingPrivacyTest.verifiesIndependentSrsOracleS1ThroughS11AndR11Ranking` + `verify_s1_s11_fixture.py` | 11/11 khớp `MATCH`/`MISSING_DATA`/`NOT_MATCH`; nhóm không đạt `MATCH` có `fitScore = null` | 11/11 PASS, đúng quy tắc R11 | **PASS** |
| **TC-QA04-17** | T4-03 | Ghép dữ liệu thật 1.000 ca (`T4-02_seed.sql`) | MySQL 8.4 mới migrate Flyway v40 | Chạy `T4MatchingFixtureIT` (nạp `T4-02_seed.sql` 2 lần liên tiếp) | Đúng `340 MATCH / 330 MISSING_DATA / 330 NOT_MATCH`, `0` orphan FK, `100` user `t4.student*@example.test`, idempotent | PASS 100% (`70.13 s`) | **PASS** |
| **TC-QA04-18** | T4-04, T4-05 | Lỗi Adapter T5 (`CRITERIA_STALE`) & thiếu active batch | Gửi `expectedDemandVersion` lệch; xóa `matching_active_demo_batches` | Gọi `POST /matching/evaluate` trong `QA04MatchingPrivacyIT` | Lệch version → HTTP **`409 CRITERIA_STALE`**; thiếu batch → HTTP **`503 PRIVACY_RELEASE_UNAVAILABLE`** | Khớp đúng `409 CRITERIA_STALE` và `503 PRIVACY_RELEASE_UNAVAILABLE` | **PASS** |
| **TC-QA04-19** | T4-05 | Hiệu năng nhỏ (`StrictClassifier` 400 hồ sơ × 5 kỹ năng) | 400 hồ sơ khác biệt, 5 kỹ năng bắt buộc, 20 warmup, 100 lần đo | Chạy `QA04MatchingPrivacyTest.verifiesSmallScaleMatchingClassifierPerformance` & `MatchingPerformanceSmokeTest` | 400/400 `MATCH`, `p95 < 250 ms` | **cold = 26 ms, p95 = 8.490 ms** (`QA04`); **cold = 31 ms, p95 = 8.702 ms** (`Smoke`) | **PASS** |

> **Lưu ý đối chiếu commit `9420f33`:** Commit `9420f33` (`feat(matching): add employer GPA filtering`) là tip nhánh `feature/t4-week3` nằm ngay sau `29ee262` (`feat(matching): complete SRS R03 and R11 requirements`). Khi hội tụ nhánh Tuần 3 lên `main` (`7f36a7d`), phần lọc `minGpa` (`strict-gpa-v1`) đã được đưa về `strict-v1`; toàn bộ cơ chế thống kê bảo vệ `k=5`, suppression, Zero PII và anti-differencing từ `29ee262` + `b37fd11` được giữ nguyên và đã PASS toàn bộ.

---

### 2.4. Nhóm D — Ghép dữ liệu thật T2/T5 + UI kết quả trên màn hình trường (`T4-04`, `T2-04`, `T5-02`, commit `0929556`)

| Mã TC | Mã Task | Hạng mục kiểm thử | Dữ liệu / Điều kiện đầu vào | Các bước thực hiện | Kết quả mong đợi | Kết quả thực tế | Trạng thái |
| :--- | :---: | :--- | :--- | :--- | :--- | :--- | :---: |
| **TC-QA04-20** | T4-04, T5-02 | Nạp nhu cầu thật từ T5 & hiển thị kết quả thật trên UI trường | Hợp tác đã duyệt trên T5 (`GET /school/cooperations?state=APPROVED`), hồ sơ SV đủ điều kiện trên T2 | Cán bộ trường mở *Đối chiếu hồ sơ* (`MatchingWorkspace.tsx`), chọn nhu cầu và bấm `Chạy Matching` (Chromium thật + `TC-QA04-IT04`) | UI gọi API thật `/matching/evaluate` & `/matching/runs/{id}/details`, hiển thị đúng `ruleVersion`, `taxonomyVersion`, số đếm `Đáp ứng`/`Thiếu dữ liệu`/`Không đáp ứng` và đọc được `displayName`/`studentCode` thật từ T2 khi có field mask | PASS trên cả Chromium thật (`week3-matching-school.png`) và `QA04MatchingPrivacyIT.IT04` | **PASS** |
| **TC-QA04-21** | T4-04, T2-04 | Đổi kỹ năng sinh viên trên API thật T2 (`MATCH → NOT_MATCH`) | SV `id(6)` đang đạt `MATCH` (`JAVA`) | SV gọi `PUT /api/v1/students/me/profile` xóa danh sách kỹ năng (`skills: []`); cán bộ trường bấm `Chạy lại Matching` | Số `Đáp ứng` giảm `1`, số `Không đáp ứng` tăng `1`; chi tiết hiển thị `Không đạt · Kỹ năng JAVA (REQUIRED_NOT_SATISFIED)` | Khớp 100% trên cả trình duyệt thật (`week3-integration-runtime.spec.ts`) và `QA04MatchingPrivacyIT.IT04` | **PASS** |
| **TC-QA04-22** | T4-04, T2-04 | Đổi trạng thái chưa khai báo kỹ năng trên T2 (`NOT_MATCH → MISSING_DATA`) | SV `id(6)` đang ở trạng thái `NOT_MATCH` | SV gọi `PUT /api/v1/students/me/profile` với `skillsDeclared = false`; cán bộ trường chạy lại Matching | Số `Thiếu dữ liệu` tăng `1`, chi tiết hiển thị `DATA_NOT_DECLARED`; khi khôi phục kỹ năng gốc, SV quay lại `MATCH` | PASS 100% trong `QA04MatchingPrivacyIT.IT04` | **PASS** |
| **TC-QA04-23** | T4-04, T5-02 | Đổi tiêu chí nhu cầu thật trên T5 (`majorCodes: ["AI"]`) | Nhu cầu mới trên T5 yêu cầu ngành `AI` trong khi SV `id(6)` học ngành `SE` | Duyệt nhu cầu mới trên T5 và cán bộ trường chạy Matching | `matched = 0`, SV `id(6)` chuyển sang `NOT_MATCH` với lý do `criterionCode = "MAJOR", status = "FAIL"` (không phụ thuộc dữ liệu giả cố định) | PASS 100% trong `QA04MatchingPrivacyIT.IT04` | **PASS** |

---

## 3. Performance & Capacity Stress Testing (Non-Functional QA)

### 3.1. Capacity Matrix (Spring Boot Backend API via Nginx Proxy)
Target endpoint: `/api/v1/auth/csrf` (Spring Security + Tomcat thread pool)

| Phase | Concurrency | Total Requests | RPS (Throughput) | P95 Latency (ms) | Success Rate | Evaluation |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **Normal Load** | 10 | 100 | **65.3** req/s | 534.1 ms | **100%** | Optimal |
| **High Load** | 50 | 250 | **57.3** req/s | 1,517.8 ms | **100%** | Acceptable |
| **Stress Load** | 100 | 500 | **91.8** req/s | 1,604.7 ms | **100%** | Increasing Latency |
| **Saturation Test** | 200 | 800 | **76.2** req/s | **3,538.7 ms** | **99.6%** | Thread Pool Bottleneck |
| **Breaking Point** | 350 | 1,000 | **169.4** req/s | **2,302.1 ms** | **100%** | Timeout Risk at Peak |

### 3.2. Architecture Comparison (Frontend vs Backend)
- **Frontend Nginx (`/`)**: 200 concurrent clients → **697.6 req/s**, P95: **124.2 ms**, 0% errors (high throughput static delivery).
- **Backend Spring Boot API**: Reaches thread pool saturation at ~200 concurrent clients, latency increases to ~3.5s.
- **CSRF Defense Layer**: Blind brute-force attempts on `POST /api/v1/auth/login` are blocked immediately with `403 Forbidden` in ~100ms, protecting BCrypt (strength 12) from CPU starvation.

### 3.3. Test Assets Added by QA04
- `src/test/java/vn/utbridora/auth/modules/matching/QA04MatchingPrivacyTest.java` — Unit + SRS Oracle S1–S11 + Smoke Performance (5/5 PASS).
- `src/test/java/vn/utbridora/auth/QA04MatchingPrivacyIT.java` — Full-stack MySQL 8.4 + HTTP Privacy, Anti-Differencing `10/10/10` vs `9/10/11`, Export/Chart Non-Bypass, Adapter & Real T2/T5 Dynamic Integration IT (4/4 PASS).
- `scripts/qa-matching/load_stress_benchmark.py` — Multi-stage concurrency load benchmark.
- `scripts/qa-matching/nginx_stress_test.py` — Frontend throughput test.
- `scripts/qa-matching/bcrypt_stress_test.py` — CPU-bound authentication stress test.

---

## 4. Kết luận tổng thể

**PASS (23/23 Test Case QA04 đạt 100%)** — Toàn bộ yêu cầu bảo vệ riêng tư `k=5`, suppression, Zero PII (JSON/CSV/Chart), chống suy luận chênh lệch `10/10/10` → `9/10/11` (HTTP `409`), ghép dữ liệu thật T2/T5 động trên màn hình trường và hiệu năng nhỏ (`T4-03`, `T4-04`, `T4-05`, `T2-04`, `T5-02`) đều đã được kiểm chứng thực tế trên MySQL 8.4, Spring Boot HTTP và Chromium.

**Người kiểm thử:** QA04  
**Ngày cập nhật:** 20/11/2026
