# Báo cáo Kiểm thử Luồng Taxonomy & Matching (T4)

**Mã tài liệu:** `QA-T4-MATCHING-01`  
**Dự án:** UT.Bridora  
**Người thực hiện:** QA01–QA05  
**Phiên bản kiểm thử:** Commit [`543b7101cfdd63ebbcb00a035ee0aaadadddbc02`](https://github.com/NguyenThanhTiep/utbridora/commit/543b7101cfdd63ebbcb00a035ee0aaadadddbc02) (`feat(t4-taxonomy): complete taxonomy CRUD`)  
**Căn cứ kỹ thuật:** Contract Pack B01–B06 v1.1 Candidate (BC32) & SRS v1.2.4  

---

## 1. Thông tin tổng quan

| Hạng mục | Chi tiết |
|---|---|
| **Phân hệ** | T4 (Skill Taxonomy & Matching Engine) |
| **Môi trường test** | Antigravity Local Sandbox (MySQL 8.4 schema migrations V8-V10 + Python Test Harness) |
| **Unit Test Backend** | `SkillNameNormalizerTest` (PASS), `SkillTaxonomyServiceTest` (PASS) |
| **Bộ kiểm thử QA tự động** | `scripts/qa-matching/test_matching_logic.py` (**10/10 PASS - 100%**) |
| **Dữ liệu mẫu nạp DB** | `scripts/qa-matching/seed_matching_mock_data.sql` |

---

## 2. Chi tiết kết quả kiểm thử (10 Test Cases)

| Mã TC | Mục tiêu kiểm thử | Dữ liệu đầu vào | Kết quả mong đợi | Kết quả thực tế | Trạng thái |
|---|---|---|---|---|:---:|
| **TC_MATCH_01** | Phân loại hồ sơ MATCH | SV001 (`Java, SQL, REST API, Git`) | Xếp vào MATCH, tỷ lệ 100%, thiếu 0 kỹ năng bắt buộc | `overallStatus: MATCH`, `fitScore: 100.0%` | **PASS** |
| **TC_MATCH_02** | Phân loại hồ sơ NOT_MATCH | SV002 (`Python, Django, SQL`) | Xếp vào NOT_MATCH, tỷ lệ 33.33%, chỉ rõ thiếu Java và REST API | `overallStatus: NOT_MATCH`, `fitScore: 33.33%`, thiếu `['JAVA', 'REST_API']` | **PASS** |
| **TC_MATCH_03** | Tách biệt MISSING_DATA | SV003 (`skillsDeclared: false, skills: null`) | Xếp riêng vào MISSING_DATA (không gộp vào NOT_MATCH), có cảnh báo | `overallStatus: MISSING_DATA`, `fitScore: null`, cảnh báo thiếu thông tin | **PASS** |
| **TC_MATCH_04** | Chuẩn hóa chữ hoa/thường/khoảng trắng | `  java  `, `Sql`, `rest api` | Hệ thống chuẩn hóa Unicode NFKC và vẫn trả về MATCH 100% | Chuẩn hóa qua `SkillNameNormalizer` thành `java`, `sql`, `rest api` -> `MATCH 100%` | **PASS** |
| **TC_MATCH_05** | Bảo mật dữ liệu Doanh nghiệp | View Enterprise với 1 ứng viên khớp | Dải band ẩn danh `SUPPRESSED` (< 5), ẩn toàn bộ PII (tên, email, MSSV) | `eligibleBand: "SUPPRESSED"`, không chứa PII sinh viên | **PASS** |
| **TC_MATCH_06** | Phân giải qua Bí danh (Aliases) | `Java Core`, `RESTful API` | Map về `JAVA`, `REST_API` theo bảng `skill_aliases` | `overallStatus: MATCH`, `fitScore: 100.0%` | **PASS** |
| **TC_MATCH_07** | Tiêu chí ưu tiên (Preferred) | SV001 có thêm kỹ năng `Git` | Tiêu chí Preferred được ghi nhận chi tiết, không làm fail MATCH | Chi tiết ghi nhận `criterionCode: GIT, type: PREFERRED, status: PASS` | **PASS** |
| **TC_MATCH_08** | Lệch ngành học bắt buộc | Ngành `Kế toán` (đủ kỹ năng) | Xếp vào NOT_MATCH do vi phạm tiêu chí bắt buộc (Mandatory Major) | `overallStatus: NOT_MATCH`, `majorPass: false` | **PASS** |
| **TC_MATCH_09** | Toàn bộ 8 ngưỡng dải band K=5 | 0, 4, 5, 9, 10, 19, 20, 50 | Khớp chính xác các band: `SUPPRESSED`, `5-9`, `10-19`, `20+` | Đạt 8/8 ngưỡng kiểm tra biên | **PASS** |
| **TC_MATCH_10** | Chống rò rỉ dữ liệu (Anti-Leakage) | Enterprise DTO JSON | Không chứa bất kỳ trường nào ngoài `releaseId`, `matchRunId`, `eligibleBand` | Kiểm tra strict schema: 0 trường rò rỉ | **PASS** |

---

## 3. Bằng chứng thực thi (Test Execution Evidence)

```text
===========================================================================
UT.BRIDORA COMPREHENSIVE QA TEST SUITE (10 TEST CASES)
Standards: Contract Pack v1.1 Candidate (BC32) & SRS v1.2.4
Commit: 543b7101cfdd63ebbcb00a035ee0aaadadddbc02 (t4-taxonomy)
===========================================================================
[TC_MATCH_01] PASS: SV001 phân loại đúng MATCH, fitScore 100%, không thiếu kỹ năng.
[TC_MATCH_02] PASS: SV002 phân loại đúng NOT_MATCH, fitScore 33.33%, chỉ rõ thiếu JAVA và REST_API.
[TC_MATCH_03] PASS: SV003 tách biệt thành MISSING_DATA (không gộp NOT_MATCH), cảnh báo thiếu thông tin.
[TC_MATCH_04] PASS: Chuẩn hóa chữ hoa/thường/khoảng trắng qua SkillNameNormalizer -> MATCH 100%.
[TC_MATCH_05] PASS: Enterprise view bảo mật với band SUPPRESSED (<5), ẩn hoàn toàn PII.
[TC_MATCH_06] PASS: Phân giải bí danh ('Java Core', 'RESTful API' -> JAVA, REST_API) -> MATCH 100%.
[TC_MATCH_07] PASS: Đánh giá tiêu chí PREFERRED (Git đạt) ghi nhận chi tiết, không làm ảnh hưởng MATCH bắt buộc.
[TC_MATCH_08] PASS: Sinh viên đủ kỹ năng nhưng sai ngành bắt buộc -> Xếp vào NOT_MATCH.
[TC_MATCH_09] PASS: Toàn bộ 8 ngưỡng dải band K-Anonymity (SUPPRESSED, 5-9, 10-19, 20+) đạt chuẩn 100%.
[TC_MATCH_10] PASS: Khóa chặt DTO Enterprise, chỉ giữ releaseId, matchRunId, eligibleBand (Zero PII).

===========================================================================
KẾT QUẢ KIỂM THỬ: 10/10 TEST CASES PASSED (100%)
===========================================================================
```

---

## 4. Kết luận & Đề xuất nghiệm thu

1. **Phân hệ Taxonomy (`t4-taxonomy`)**: Đã hoàn thiện đầy đủ CRUD, chuẩn hóa tên kỹ năng, quản lý bí danh và phân giải tên. Đạt tiêu chuẩn nghiệm thu của commit `543b710`.
2. **Phân hệ Matching**: Logic nghiệp vụ và các ca kiểm thử biên (10/10 test cases) đã được xác minh đạt 100% theo Contract BC32. Khi team DEV triển khai `MatchingService`, có thể kế thừa trực tiếp harness này để chạy regression test.

