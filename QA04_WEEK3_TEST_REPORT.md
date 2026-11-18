# QA04 – WEEK 3 TEST REPORT

**Role:** QA04  
**Scope:** T4 Matching Engine, k=5 Anonymity, Privacy Bands, Zero PII, Anti-differencing & Strict Classifier

---

## 1. Test execution results

### Test 1: QA04MatchingPrivacyTest
Command:
```powershell
.\mvnw.cmd "-Dtest=QA04MatchingPrivacyTest" test
```

Result:
- Tests run: 5
- Failures: 0
- Errors: 0
- Skipped: 0
- Status: **BUILD SUCCESS** (0.786s)

### Test 2: StrictClassifierTest
Command:
```powershell
.\mvnw.cmd "-Dtest=StrictClassifierTest" test
```

Result:
- Tests run: 2
- Failures: 0
- Errors: 0
- Skipped: 0
- Status: **BUILD SUCCESS** (0.814s)

### Test 3: Independent SRS Oracle S1–S11 Verification
Command:
```powershell
python scripts/fixtures/t4/verify_s1_s11_fixture.py
```

Result:
- 11 independent SRS expectations verified against frozen SRS text.
- Status: **SUCCESS**

---

## 2. Verified scenarios

- **Nhóm 0 sinh viên:** MATCH = 0 → `eligibleBand` là `SUPPRESSED` (không lộ số 0).
- **Nhóm 1 sinh viên:** MATCH = 1 → `eligibleBand` là `SUPPRESSED` (ẩn số 1).
- **Nhóm 4 sinh viên:** MATCH = 4 → `eligibleBand` là `SUPPRESSED` (cận kề $k=5$, ẩn số 4).
- **Nhóm 5 sinh viên:** MATCH = 5 → `eligibleBand` là `5-9` (đạt ngưỡng $k=5$, công bố dạng dải).
- **Các dải mở rộng:** 9 SV → `5-9`, 10 và 19 SV → `10-19`, $\ge 20$ SV → `20+`.
- **Replay truy vấn:** Gửi lặp lại cùng fingerprint trong cùng đợt trả về đúng release cũ (Idempotent).
- **Chống suy diễn chênh lệch (Anti-Differencing):** Cố tình thay đổi tiêu chí/bộ lọc để trừ số chênh lệch bị chặn với mã lỗi `409 PRIVACY_RELEASE_LOCKED`.
- **Zero PII (Bảo mật dữ liệu cá nhân):** Payload trả về cho Doanh nghiệp chỉ gồm 3 trường (`releaseId`, `matchRunId`, `eligibleBand`); tuyệt đối không rò rỉ họ tên, email, MSSV, phone, `rawCount`, `total` hay `%`.
- **Phân quyền & Lỗi Adapter:** Doanh nghiệp gọi Details bị chặn `403 ACCESS_DENIED`; lệch version tiêu chí T5 trả lỗi `409 CRITERIA_STALE`.
- **Kế thừa T4-03:** Cả 11 kịch bản Oracle độc lập S1–S11 từ SRS §16.3 đều PASS 100% với bộ phân loại `StrictClassifier`.

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
- **Frontend Nginx (`/`)**: 200 concurrent clients $\rightarrow$ **697.6 req/s**, P95: **124.2 ms**, 0% errors (high throughput static delivery).
- **Backend Spring Boot API**: Reaches thread pool saturation at ~200 concurrent clients, latency increases to ~3.5s.
- **CSRF Defense Layer**: Blind brute-force attempts on `POST /api/v1/auth/login` are blocked immediately with `403 Forbidden` in ~100ms, protecting BCrypt (strength 12) from CPU starvation.

### 3.3. Test Scripts Added
- `scripts/qa-matching/load_stress_benchmark.py`: Multi-stage concurrency load benchmark.
- `scripts/qa-matching/nginx_stress_test.py`: Frontend throughput test.
- `scripts/qa-matching/bcrypt_stress_test.py`: CPU-bound authentication stress test.

---

## 4. Overall result

**PASS – All functional privacy requirements (T4-05) and non-functional performance benchmarks completed successfully.**

**Tester:** QA04  
**Date:** 19/11/2026
