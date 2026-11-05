# Bộ dữ liệu giả lập Matching T4-02

Thư mục này chứa các sản phẩm bàn giao cho nhiệm vụ tuần 1 T4-02, tương ứng US-T4-08 và AC-08.1/AC-08.2.

## Quy mô dữ liệu

- 100 hồ sơ sinh viên giả lập.
- 10 tổ chức doanh nghiệp giả lập và 10 nhu cầu tuyển dụng.
- 1 tổ chức trường đại học giả lập.
- 1.000 cặp sinh viên–nhu cầu: mỗi nhu cầu được đối chiếu với cả 100 sinh viên.
- Với mỗi nhu cầu có 34 ca `MATCH`, 33 ca `MISSING_DATA` và 33 ca `NOT_MATCH`. Tổng cộng lần lượt là 340, 330 và 330 ca.
- `student_code` là MSSV dạng chuỗi, giữ số 0 ở đầu. Tên, email, doanh nghiệp và tiêu chí đều là dữ liệu giả; email dùng miền dành riêng `example.test`.

Ca `MATCH` khai báo kỹ năng chuẩn `JAVA` đạt cấp độ và số năm kinh nghiệm yêu cầu, đúng ngành `SE`, đồng thời có lịch phù hợp. Ca `MISSING_DATA` đặt `skillsDeclared=false` và không có kỹ năng, nên chưa thể biết hồ sơ có đạt yêu cầu kỹ năng hay không. Ca `NOT_MATCH` đã khai báo đầy đủ kỹ năng nhưng không có `JAVA` bắt buộc. Nhờ vậy, thiếu thông tin được phân biệt với việc đã biết là không đạt.

## Các tệp

- `T4-02_seed.sql`: nạp các bản ghi định danh/tổ chức T1 giả lập và toàn bộ ca kiểm thử. Bảng kiểm thử có khóa ngoại từ `student_user_id` tới `users.id`, và từ `school_org_id`, `enterprise_org_id` tới `organizations.id`.
- `T4-02_matching_fixtures.csv`: cùng bộ ca ở định dạng CSV UTF-8 có BOM để tương thích với phần mềm bảng tính; gồm thông tin doanh nghiệp và nhu cầu để QA xem xét.
- `generate_t4_02_fixtures.mjs`: chương trình sinh lại hai tệp SQL và CSV với dữ liệu ổn định.

Tạo lại các tệp từ thư mục gốc dự án bằng lệnh:

```powershell
node scripts/fixtures/t4/generate_t4_02_fixtures.mjs
```

## Quyền sở hữu schema và cách chạy

Mã nguồn hiện tại có migration T1 cho `users` và `organizations`, nhưng chưa triển khai bảng hồ sơ T2 hoặc bảng nhu cầu T5. Vì vậy, `t4_fixture_cases` là **bảng trung gian chỉ phục vụ kiểm thử**, không phải bảng ứng dụng dùng khi vận hành hay migration Flyway. JSON trong bảng chứa dữ liệu giả lập cần cho Matching cùng ngữ cảnh doanh nghiệp/nhu cầu. Khi schema T2/T5 được cung cấp, hãy ánh xạ dữ liệu kiểm thử vào đúng bảng do các nhóm đó sở hữu; không đưa bảng trung gian này vào migration của ứng dụng.

Seed luôn ghi rõ database đích là `utbridora_t4_fixture`, không phụ thuộc database đang được chọn trong connection. Hãy áp dụng V1–V7 trên schema này trước khi chạy. Không chạy seed trên database dùng chung hoặc môi trường thật. Nếu tài khoản kết nối không có quyền với schema fixture, MySQL sẽ từ chối lệnh thay vì ghi dữ liệu vào database khác. Có thể chạy lại seed với các ID kiểm thử ổn định: script xóa ba ID của bộ mẫu ban đầu rồi thêm hoặc cập nhật các ca đã sinh.

Các khóa ngoại được khai báo trong SQL và MySQL sẽ kiểm tra khi nạp dữ liệu. QA có thể kiểm tra số lượng theo kết quả và tính toàn vẹn tham chiếu bằng các truy vấn sau:

```sql
SELECT expected_outcome, COUNT(*) AS case_count
FROM t4_fixture_cases
GROUP BY expected_outcome
ORDER BY expected_outcome;

SELECT
  COUNT(*) AS total_cases,
  SUM(u.id IS NULL) AS missing_students,
  SUM(s.id IS NULL) AS missing_schools,
  SUM(e.id IS NULL) AS missing_enterprises
FROM t4_fixture_cases c
LEFT JOIN users u ON u.id = c.student_user_id
LEFT JOIN organizations s ON s.id = c.school_org_id
LEFT JOIN organizations e ON e.id = c.enterprise_org_id;
```

Kết quả mong đợi: 330 ca `MISSING_DATA`, 340 ca `MATCH`, 330 ca `NOT_MATCH`; tổng `missing_*` đều bằng 0.
