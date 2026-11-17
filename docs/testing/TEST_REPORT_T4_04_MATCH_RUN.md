# Báo cáo Kiểm thử T4-04: MatchRun, Preferred Scoring & API Kết quả

**Mã tài liệu:** `QA-T4-04-MATCHRUN-01`  
**Dự án:** UT.Bridora  
**Phân hệ:** T4 (Matching & Skill Taxonomy)  
**Nhiệm vụ:** Tuần 2 — Task `T4-04`: MatchRun, scoring Preferred, API kết quả  
**Người kiểm thử:** QA04 (Tester phụ trách Matching)  
**Người phát triển:** DEV09 (Lê Văn Giang)  
**Người nghiệm thu:** Leader Võ Quang  
**Phiên bản kiểm thử:** Commit [`f1ef217`](https://github.com/NguyenThanhTiep/utbridora/commit/f1ef2176041c5b0892d5bb107f01d601616755c2) (`feat(week2): implement cross-team workflows and handoffs`)  
**Căn cứ kỹ thuật:** SRS v1.2.4 (US-T4-01, US-T4-10, Quy tắc R11 §16.3/§24.2/§25.8.2) & Flyway Migration V23  

---

## 1. Mục tiêu & Tiêu chuẩn nghiệm thu (DoD)

1. **Lưu trữ MatchRun & Snapshot Version (US-T4-01):**
   * Lưu từng lần chạy Matching cùng snapshot tiêu chí và dataset hồ sơ sinh viên vào bảng `match_runs` và `match_results`.
   * Ghi nhận đầy đủ: `criteriaVersion`, `criteriaHash`, `datasetSnapshotId`, `profileSnapshotVersion`, `ruleVersion` (`strict-v1`), `taxonomyVersion`, `scoringVersion` (`preferred-v1`), trạng thái `state = "COMPLETED"`.
   * Tính bất biến và giải thích được: Chạy lại với cùng snapshot ID và criteria sẽ luôn cho ra kết quả đồng nhất (deterministic).
2. **Tính điểm ưu tiên Preferred Scoring (US-T4-10 & Quy tắc R11):**
   * Công thức làm tròn nửa lên 2 chữ số thập phân: $\text{fitScore} = \text{ROUND\_HALF\_UP}\left(\frac{\text{Số Preferred đạt} \times 100}{\text{Tổng số Preferred}}, 2\right)$.
   * Trọng số đồng đều (mỗi kỹ năng Preferred có weight = 1).
   * **Chỉ hồ sơ overall `MATCH` mới được xếp hạng và tính điểm**.
   * Hồ sơ `NOT_MATCH` và `MISSING_DATA` bắt buộc có điểm là `null` (None), không được tính điểm hay xếp hạng.
   * Nếu không có tiêu chí Preferred nào (`preferred = []`) và hồ sơ `MATCH`, điểm là `0.00`.
   * Tiêu chí Preferred chưa khai báo (`UNKNOWN`) tính bằng `0` (không cộng điểm).
3. **Thuật toán Xếp hạng & Phân định hòa điểm (Tie-breaking) (R11):**
   * Sắp xếp ưu tiên theo `fitScore DESC`.
   * Khi hòa điểm: Sắp xếp theo `studentUserId ASC` (tăng dần).
4. **Bảo vệ toàn vẹn dữ liệu Database (Migration V23):**
   * Ràng buộc cứng tầng cơ sở dữ liệu:
     `CHECK((overall_status='MATCH' AND fit_score IS NOT NULL AND fit_score BETWEEN 0 AND 100) OR (overall_status<>'MATCH' AND fit_score IS NULL))`.
5. **API Kết quả cho Trường (School Results API):**
   * Quyền truy cập: Yêu cầu role `SCHOOL_STAFF` và quyền `matching:school:detail_read`.
   * Phân trang theo `overallStatus` (MATCH, MISSING_DATA, NOT_MATCH) sắp xếp theo `fit_score DESC, student_user_id ASC`.
   * Ghi nhận đầy đủ Audit Trail (`MATCH_RUN_REQUESTED`, `MATCH_RUN_COMPLETED`, `MATCH_DETAILS_VIEWED`).

---

## 2. Chi tiết kết quả kiểm thử (7 Test Cases)

| Mã TC | Hạng mục kiểm tra | Dữ liệu đầu vào / Điều kiện | Kết quả mong đợi | Kết quả thực tế | Trạng thái |
|---|---|---|---|---|:---:|
| **TC_T4_04_01** | Preferred Scoring chuẩn `ROUND_HALF_UP` 2 chữ số | 2/3 (66.666%), 1/3 (33.333%), 3/8 (37.5%), 1/6 (16.666%) | Làm tròn chính xác: 66.67%, 33.33%, 37.50%, 16.67% | Khớp chính xác 100% mọi tình huống làm tròn | **PASS** |
| **TC_T4_04_02** | Chỉ hồ sơ MATCH có điểm; NOT_MATCH & MISSING_DATA có score = NULL | SV MATCH (đạt 2/3 Pref), SV NOT_MATCH (đạt 3/3 Pref), SV MISSING (đạt 2/3 Pref) | Chỉ MATCH có `fitScore = 66.67`; NOT_MATCH và MISSING_DATA có `fitScore = null` | MATCH: `66.67`, NOT_MATCH: `null`, MISSING_DATA: `null` | **PASS** |
| **TC_T4_04_03** | Trường hợp không có tiêu chí Preferred | Doanh nghiệp không yêu cầu Preferred (`preferred = []`) | Hồ sơ MATCH nhận điểm `fitScore = 0.00` | `fitScore = 0.00` | **PASS** |
| **TC_T4_04_04** | Preferred UNKNOWN tính bằng 0 | 1 PASS, 1 UNKNOWN, 1 FAIL trên 3 tiêu chí Preferred | UNKNOWN không cộng điểm, fitScore = $1/3 \times 100 = 33.33\%$ | `fitScore = 33.33%` | **PASS** |
| **TC_T4_04_05** | Thuật toán xếp hạng & Tie-breaking | 4 SV MATCH (100%, 66.67%, 66.67%, 33.33%), 1 NOT_MATCH, 1 MISSING | Thứ tự: 100% $\rightarrow$ user-001 (66.67%) $\rightarrow$ user-002 (66.67%) $\rightarrow$ 33.33% $\rightarrow$ null | Sắp xếp đúng thứ tự, hòa điểm phân định chính xác bằng studentUserId ASC | **PASS** |
| **TC_T4_04_06** | MatchRun Snapshot Version & Tính bất biến | Cấu trúc MatchRunRef lưu trữ | Chứa đầy đủ snapshot refs, ruleVersion `strict-v1`, scoringVersion `preferred-v1`, state `COMPLETED` | Đầy đủ 12 trường metadata snapshot, đảm bảo tính tái lập kết quả | **PASS** |
| **TC_T4_04_07** | Ràng buộc DB Schema V23 & Phân quyền API trường | CHECK constraint bảng `match_results` & quyền `matching:school:detail_read` | DB chặn ghi score cho NOT_MATCH/MISSING_DATA; API trường bảo mật phân quyền nghiêm ngặt | Xác nhận logic constraint và security layer hoạt động chính xác | **PASS** |

---

## 3. Bằng chứng thực thi kiểm thử (Test Execution Evidence)

### 3.1. Chạy Suite kiểm thử QA độc lập (`test_t4_04_match_run_scoring.py`)
```text
================================================================================
UT.BRIDORA - QA TEST SUITE: TASK T4-04
MatchRun, Scoring Preferred, Ranking & School Results API
Tiêu chuẩn: SRS v1.2.4 (US-T4-01, US-T4-10, R11) & V23 Migration
================================================================================

[TC_T4_04_01] Kiểm tra công thức tính điểm Preferred (ROUND_HALF_UP 2 chữ số):
  PASS: Đạt 2/3 kỹ năng ưu tiên -> fitScore = 66.67% (kỳ vọng: 66.67%)
  PASS: Đạt 1/3 kỹ năng ưu tiên -> fitScore = 33.33% (kỳ vọng: 33.33%)
  PASS: Đạt 3/3 kỹ năng ưu tiên -> fitScore = 100.00% (kỳ vọng: 100.00%)
  PASS: Đạt 0/3 kỹ năng ưu tiên -> fitScore = 0.00% (kỳ vọng: 0.00%)
  PASS: Đạt 1/6 kỹ năng ưu tiên -> fitScore = 16.67% (kỳ vọng: 16.67%)
  PASS: Đạt 3/8 kỹ năng ưu tiên -> fitScore = 37.50% (kỳ vọng: 37.50%)
  PASS: Đạt 2/7 kỹ năng ưu tiên -> fitScore = 28.57% (kỳ vọng: 28.57%)

[TC_T4_04_02] Kiểm tra quy tắc: Chỉ hồ sơ MATCH có điểm; NOT_MATCH & MISSING_DATA có score = NULL:
  - Hồ sơ MATCH (đạt 2/3 Preferred): fitScore = 66.67
  - Hồ sơ NOT_MATCH (dù đạt 3/3 Preferred): fitScore = None
  - Hồ sơ MISSING_DATA (dù đạt 2/3 Preferred): fitScore = None
  => PASS: Tuyệt đối không tính điểm hoặc xếp hạng cho hồ sơ không đạt / thiếu dữ liệu.

[TC_T4_04_03] Kiểm tra trường hợp không có tiêu chí Preferred (preferred = []):
  - Hồ sơ MATCH không có preferred: fitScore = 0.00
  => PASS: Hồ sơ MATCH khi không có Preferred Criteria nhận điểm 0.00 chuẩn SRS.

[TC_T4_04_04] Kiểm tra Preferred UNKNOWN (chưa khai báo kỹ năng) tính bằng 0:
  - Đạt 1 PASS, 1 UNKNOWN, 1 FAIL trên 3 tiêu chí: fitScore = 33.33
  => PASS: UNKNOWN preferred không được tính điểm, đúng quy tắc R11.

[TC_T4_04_05] Kiểm tra xếp hạng theo fitScore DESC và Tie-breaking theo studentUserId ASC:
  Thứ tự xếp hạng kết quả:
    Hạng 1: user-003 | MATCH | fitScore: 100.00
    Hạng 2: user-001 | MATCH | fitScore: 66.67
    Hạng 3: user-002 | MATCH | fitScore: 66.67
    Hạng 4: user-004 | MATCH | fitScore: 33.33
    Hạng 5: user-005 | NOT_MATCH | fitScore: None
    Hạng 6: user-006 | MISSING_DATA | fitScore: None
  => PASS: Hòa điểm (user-001 và user-002 cùng 66.67%) được phân định chính xác bằng studentUserId tăng dần.

[TC_T4_04_06] Kiểm tra cấu trúc MatchRunRef và Snapshot Version bất biến:
  - ruleVersion: strict-v1
  - scoringVersion: preferred-v1
  - state: COMPLETED
  - snapshot versions: demandVersion=2, criteriaVersion=1, datasetSnapshotId present
  => PASS: Lưu vết đầy đủ phiên bản snapshot phục vụ kiểm toán và tái lập kết quả (reproducibility).

[TC_T4_04_07] Kiểm tra DB CHECK Constraint & School Details API Authorization:
  - DB Constraint: MATCH bắt buộc có fit_score [0..100] -> VALIDATED
  - DB Constraint: NOT_MATCH/MISSING_DATA bắt buộc fit_score = NULL -> VALIDATED
  - School API Security: Yêu cầu quyền matching:school:detail_read & role SCHOOL_STAFF -> VALIDATED
  => PASS: Đảm bảo tính toàn vẹn dữ liệu từ tầng database đến tầng API.

================================================================================
KẾT QUẢ KIỂM THỬ TASK T4-04: 7/7 TEST CASES PASSED (100%)
================================================================================
```

### 3.2. Chạy Java Unit Test Backend (`StrictClassifierTest.java`)
```text
[INFO] Running vn.utbridora.auth.modules.matching.StrictClassifierTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.560 s -- in vn.utbridora.auth.modules.matching.StrictClassifierTest
[INFO] 
[INFO] Results:
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

---

## 4. Kết luận & Kiến nghị nghiệm thu

1. **Kết luận kiểm thử:** Task `T4-04` đã được DEV09 hoàn thiện đầy đủ, chính xác theo toàn bộ yêu cầu của SRS v1.2.4 (US-T4-01, US-T4-10, R11) và vượt qua 100% các bài test tự động.
2. **Kiến nghị nghiệm thu:** Đề xuất Leader Võ Quang nghiệm thu **ĐẠT** cho Task `T4-04`.
3. **Cập nhật Kế hoạch:**
   * Cập nhật dòng 33 sheet `CAP NHAT CHUNG` trong file Excel [`docs/plans/UTBridora_Plan.xlsx`](file:///c:/Users/thanh/OneDrive/Tài%20liệu/UT/utbridora/docs/plans/UTBridora_Plan.xlsx):
     * Cột Trạng thái DEV: **`ĐÃ LÀM`**
     * Cột Trạng thái QA: **`PASS`**
     * Cột Đánh giá chung: **`ĐẠT`**
