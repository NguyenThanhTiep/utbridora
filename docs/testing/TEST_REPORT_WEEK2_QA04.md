# Báo cáo Kiểm thử QA-04 (Tuần 2): Test bộ phân loại và điểm ưu tiên

**Mã tài liệu:** `QA-WEEK2-T4-CLASSIFIER-01`  
**Dự án:** UT.Bridora  
**Phân hệ:** T4 (Matching & Skill Taxonomy)  
**Nhiệm vụ:** `QA-04 (Tuần 2) — Test bộ phân loại và điểm ưu tiên`  
**Căn cứ kế hoạch:** Dòng 40 sheet `CAP NHAT CHUNG`, file `UTBridora_Plan.xlsx`  
**Người kiểm thử:** QA04 (Tester phụ trách Matching)  
**Người nghiệm thu:** Leader Võ Quang  
**Phiên bản mã nguồn kiểm thử:** Commit [`f1ef217`](https://github.com/NguyenThanhTiep/utbridora/commit/f1ef2176041c5b0892d5bb107f01d601616755c2)  
**Căn cứ kỹ thuật:** SRS v1.2.4 (§16.3 S1–S11, §24.2, §25.8.2), Quy tắc R04, Quy tắc R11  

---

## 1. Yêu cầu kiểm thử & Tiêu chuẩn nghiệm thu (DoD)
* **Mô tả công việc:** Chạy 11 ca chuẩn, thử dữ liệu trống/thiếu, kiểm tra chỉ MATCH được xếp hạng.
* **Tiêu chí hoàn thành (DoD):** Bảng so sánh kết quả thực tế với kết quả mong đợi.
* **Quy tắc bắt buộc:**
  1. Tuyệt đối **không có trường hợp FAIL bị xếp thành MATCH**.
  2. Quy tắc **FAIL ưu tiên UNKNOWN**: Nếu có bất kỳ tiêu chí bắt buộc nào không đạt thì kết luận ngay `NOT_MATCH`, không nhầm lẫn sang `MISSING_DATA`.
  3. **Chỉ hồ sơ MATCH mới được tính điểm ưu tiên (Preferred fitScore) và xếp hạng**; hồ sơ `NOT_MATCH` và `MISSING_DATA` bắt buộc có `fitScore = null`.

---

## 2. Bảng so sánh Kết quả Thực tế với Kết quả Mong đợi (Expected vs Actual)

| Mã Ca | Nhóm tình huống | Tiêu chí Ngành | Tiêu chí Kỹ năng | Tiêu chí Lịch rảnh | Kỳ vọng SRS | Thực tế hệ thống | Điểm fitScore | Kết quả kiểm thử |
|:---:|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **S1** | Oracle Chuẩn | PASS (KTPM) | PASS (Java, SQL, REST) | PASS (Đúng kỳ) | **MATCH** | **MATCH** | 0.00% | **PASS** |
| **S2** | Oracle Chuẩn | PASS (CNTT) | PASS (Java, SQL, REST) | PASS (Đúng kỳ) | **MATCH** | **MATCH** | 0.00% | **PASS** |
| **S3** | Oracle Chuẩn | PASS (CNTT) | FAIL (Thiếu REST_API) | PASS (Đúng kỳ) | **NOT_MATCH** | **NOT_MATCH** | null | **PASS** |
| **S4** | Oracle Chuẩn | PASS (KTPM) | UNKNOWN (Chưa khai báo) | PASS (Đúng kỳ) | **MISSING_DATA** | **MISSING_DATA** | null | **PASS** |
| **S5** | Oracle Chuẩn | PASS (CNTT) | FAIL (Sai kỹ năng) | PASS (Đúng kỳ) | **NOT_MATCH** | **NOT_MATCH** | null | **PASS** |
| **S6** | Oracle Chuẩn | FAIL (LOGISTICS) | PASS (Java, SQL, REST) | PASS (Đúng kỳ) | **NOT_MATCH** | **NOT_MATCH** | null | **PASS** |
| **S7** | Oracle Chuẩn | PASS (CNTT) | PASS (Java, SQL, REST) | UNKNOWN (Chưa khai báo) | **MISSING_DATA** | **MISSING_DATA** | null | **PASS** |
| **S8** | Oracle Chuẩn | PASS (CNTT) | PASS (Java, SQL, REST) | FAIL (Không có lịch rảnh) | **NOT_MATCH** | **NOT_MATCH** | null | **PASS** |
| **S9** | Oracle Chuẩn | PASS (KTPM) | FAIL (Spring Boot $\neq$ Java) | PASS (Đúng kỳ) | **NOT_MATCH** | **NOT_MATCH** | null | **PASS** |
| **S10** | Oracle Chuẩn | PASS (KTPM) | PASS (Java Core $\rightarrow$ Java) | PASS (Đúng kỳ) | **MATCH** | **MATCH** | 0.00% | **PASS** |
| **S11** | Oracle Chuẩn | FAIL (LOGISTICS) | UNKNOWN (Chưa khai báo) | PASS (Đúng kỳ) | **NOT_MATCH** | **NOT_MATCH** | null | **PASS** |
| **E1** | Dữ liệu trống/thiếu | UNKNOWN (majorCode=null) | PASS | PASS | **MISSING_DATA** | **MISSING_DATA** | null | **PASS** |
| **E2** | Dữ liệu trống/thiếu | PASS | UNKNOWN (skillsDeclared=false) | PASS | **MISSING_DATA** | **MISSING_DATA** | null | **PASS** |
| **E3** | Dữ liệu trống/thiếu | PASS | FAIL (skillsDeclared=true, skills=[]) | PASS | **NOT_MATCH** | **NOT_MATCH** | null | **PASS** |
| **E4** | Dữ liệu trống/thiếu | PASS | PASS | UNKNOWN (availDeclared=false) | **MISSING_DATA** | **MISSING_DATA** | null | **PASS** |
| **E5** | Dữ liệu trống/thiếu | PASS | PASS | FAIL (availDeclared=true, periods=[]) | **NOT_MATCH** | **NOT_MATCH** | null | **PASS** |
| **E6** | Dữ liệu trống/thiếu | UNKNOWN | UNKNOWN | UNKNOWN | **MISSING_DATA** | **MISSING_DATA** | null | **PASS** |
| **E7** | Dữ liệu trống/thiếu | FAIL (Ngành sai) | UNKNOWN (Chưa khai kỹ năng) | UNKNOWN (Chưa khai lịch) | **NOT_MATCH** | **NOT_MATCH** | null | **PASS** |

---

## 3. Xác minh tiêu chí nghiệm thu đặc biệt của QA-04

1. **Kiểm tra Zero False MATCH:**
   * Tổng số trường hợp có tiêu chí bắt buộc FAIL: **9 trường hợp** (S3, S5, S6, S8, S9, S11, E3, E5, E7).
   * Số trường hợp FAIL bị phân loại nhầm thành `MATCH`: **0 trường hợp (0%)**.
   * Số trường hợp FAIL bị phân loại nhầm thành `MISSING_DATA`: **0 trường hợp (0%)**.
   * 👉 **ĐẠT:** Tuyệt đối không có trường hợp FAIL nào bị xếp thành MATCH.
2. **Kiểm tra quy tắc xếp hạng & Điểm ưu tiên (R11):**
   * Tổng số trường hợp không đạt (`NOT_MATCH` và `MISSING_DATA`): **15 trường hợp**.
   * Số trường hợp không đạt bị gán điểm fitScore: **0 trường hợp (0%)**.
   * 👉 **ĐẠT:** CHỈ hồ sơ `MATCH` được tính điểm ưu tiên và xếp hạng. Hồ sơ không đạt có `fitScore = null`.

---

## 4. Kết luận
* Đạt **18/18 test cases (100%)**.
* Hoàn thành đầy đủ toàn bộ tiêu chí nghiệm thu của Task `QA-04 (Tuần 2)`.
