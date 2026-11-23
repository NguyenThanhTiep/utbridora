# QA05 · Kết quả kiểm thử tuần 1–5

Phạm vi gồm 20 dòng được giao QA05. Các kết quả tuần 1–4 dưới đây phản ánh phiên bản DEV bàn giao và các lần chạy tích hợp đã ghi nhận. Bản chạy tuần 5 dùng `c58a8ee`. Nhánh nộp bài được tạo từ `8bc2481` và có một lượt kiểm chứng riêng trước khi push.

## Tuần 1–4

- **Tuần 1.** QA-05 và T5-03 PASS. T5-01 FAIL trên `c299c4f` vì tạo hồ sơ xác minh hợp lệ trả HTTP 409. MySQL báo `from_state` không nhận null. T5-02 còn `Chưa test` ở mức nghiệm thu trên `7f208e8`, dù các ca Demand đã chạy đều PASS. Commit đó thiếu Matching consumer. Cả Verification và Matching đã chạy PASS trên bản tích hợp mới hơn.
- **Tuần 2.** QA-05, T5-04, T5-05 và T5-08 PASS. Đã kiểm gửi, yêu cầu bổ sung, nộp lại, từ chối và duyệt Cooperation, kiểm điều kiện xác minh trường đích, kiểm nhận hoặc từ chối lời mời và lưu trạng thái sau khi tải lại trang.
- **Tuần 3.** QA-05, T5-06 và T5-09 PASS. Đã kiểm phạm vi chia sẻ, thu hồi Consent và luồng trường–sinh viên–doanh nghiệp. T5-07 còn `Chưa test` ở mức nghiệm thu trên `850a41c`. Ca duyệt, bàn giao T3 và hủy đã PASS trên bản đó. Biên kích hoạt và 20 lượt duyệt cạnh tranh đã PASS trên bản tích hợp.
- **Tuần 4.** QA-05, T5-10, T5-11 và T5-12 PASS. Đã kiểm lọc và tổng hợp Placement, giới hạn chỉ tiêu, một bản ghi outbox khi duyệt đồng thời, tải lại dữ liệu và xử lý HTTP 409 do phiên bản cũ.

## Tuần 5

- **T5-13 PASS.** Đã kiểm xác minh doanh nghiệp, 20 yêu cầu duyệt đồng thời, quota, hủy Placement và sự kiện T3 trùng hoặc đến sai thứ tự.
- **T5-14 PASS.** Đã kiểm Demand, phạm vi Cooperation, Consent và quyền đọc CV qua HTTP Range. Lượt đầu có 77 ca EnterpriseProposalIT PASS và một ca CV dừng lúc khởi động Docker. Ca CV chạy lại PASS với ClamAV 1.4.6 điều khiển qua loopback.
- **T5-15 và QA-05 PASS.** Chromium tạo Demand, gửi Cooperation, dùng Matching thật để đề xuất, xác nhận Consent rồi duyệt và hủy Placement. Sau HTTP 409 do phiên bản cũ, UI hiện thông báo và lượt hủy bằng phiên bản mới thành công. MySQL ghi đúng một Placement đã từng được duyệt và hiện ở trạng thái `CANCELLED`. Có 74 ca UI dùng API mô phỏng PASS, gồm ca xóa release thống kê cũ khi Demand đổi phiên bản.

## Kiểm tra nhánh trước khi push

Ngày 23/11/2026, frontend build và typecheck PASS trên nhánh dựa trên `8bc2481`. Sáu ca Verification và bốn ca EnterpriseProposalIT PASS qua Spring/MySQL 8.4.13, gồm hai ca duyệt cạnh tranh, ca trình duyệt hiện có và ca QA05 mới. Các trình chạy backend khởi động Chromium và ghi nhận ba ca trình duyệt thật PASS. Bốn ca Chromium dành cho harness T3 riêng được bỏ qua trong lượt chạy này.

## Chạy lại ca E2E của QA05

Cần Java 21, Docker cho MySQL Testcontainers, Node và Chromium của Playwright. Từ thư mục gốc repo chạy:

```sh
npm --prefix web-demo ci
cd web-demo
npx playwright install chromium
cd ..
./mvnw -B "-Dit.test=EnterpriseProposalIT#qa05BrowserCreatesDemandAndCompletesPlacement" -Dqa05.week5.browser=true verify
```

Trên Windows dùng `mvnw.cmd` thay cho `./mvnw`. Test dùng các tài khoản và hồ sơ thử nghiệm do `EnterpriseProposalIT` tạo. Kết quả Chromium nằm ở `target/qa05-week5-browser-results.xml`, log ở `target/qa05-week5-browser.log`.
