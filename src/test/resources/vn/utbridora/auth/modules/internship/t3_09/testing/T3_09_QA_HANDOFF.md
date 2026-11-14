# Bàn giao QA03 — T3-09

DEV07: Lại Thái Thuận. Cập nhật 12/11/2026 sau GET Review của DEV05 `4d187ab`.
**DEV PASS, chờ QA/CI** cho luồng URL submission/review/comment và weekly report.
Source còn chưa commit trên `feature/t3-week3`; cần bổ sung SHA người dùng push.

## Luồng QA kiểm lại

1. Student gửi URL v1 → Mentor yêu cầu sửa → student reload đọc người nhận xét,
   verdict/comment/time → bắt đầu sửa/nộp v2. V2 chưa review hiển thị đúng trạng thái
   chưa nhận xét, không lẫn v1. Mentor ACCEPT v2 → reload đọc ACCEPT → mở v1 vẫn còn
   yêu cầu sửa cũ. URL/note v1 bất biến, assignment COMPLETED.
2. Mentor chỉ review current SUBMITTED. HR được đọc feedback nhưng không review;
   outsider/sai nhóm/revoked grant bị từ chối. GET404/401 đóng private workspace.
3. Chuyển version/context khi mạng chậm không khôi phục review cũ. Response sai
   submission/version bị chặn. GET503 hiện lỗi với retry, không giả thành GET204.
4. Comment hai chiều append/read; report tạo/sửa/nộp → Mentor review → student
   reload → revision mới → xem report/review revision cũ. Bản lịch sử chỉ đọc.
5. Minh chứng upload → lưu refs; PENDING chặn submit/download; gỡ draft không xóa
   lịch sử/binary. CLEAN cần pipeline T1 thực, không sửa DB để ép nghiệm thu PASS.
6. 409 giữ draft, refresh version; timeout retry cùng payload/key, CSRF/session;
   permission changes, deadline/lateAllowed, invalid URL và layout320px.

Đã chạy 73 frontend PASS, 1.263 unit PASS/1 Windows skip, 41 integration PASS
(gồm một harness với 4 browser thật PASS), 868 contract checks PASS. Maven verify
tổng hợp và frontend build PASS. Xem [test report](T3_09_TEST_REPORT.md) để phân biệt
ca backend, fixture, browser và giới hạn; checklist QA không đồng nghĩa mọi dòng
đã có browser automation riêng.

## Phụ thuộc còn ngoài phạm vi

**GET submission review đã hoàn tất tích hợp**, không còn blocker DEV05.
T1-07 scanner/file-mode Submission, workflow Placement T5 thật và phối hợp thứ tự
V30/V31 vẫn cần nghiệm thu liên team. Test dùng MySQL riêng và ACTIVE Placement
synthetic; không chứng minh quy trình kích hoạt T5 hoặc scanner đã hoàn thành.

## Nhận bàn giao

- [README](../README.md), [UI guide](../guides/T3_09_WORKFLOW_UI.md),
  [API GET và lệnh commit/push](../guides/T3_09_REVIEW_GET_HANDOFF.md).
- Evidence mới có tiền tố `T3_09_REVIEW_GET_` tại `../evidence/`; các snapshot cũ
  giữ nguyên để đối chiếu. Java/React tests nằm đúng vị trí source test hiện hữu.
- Người dùng tự commit/push. Git đang merge main c3d0dbc với 12 file staged;
  DEV07/T3-03 chưa stage. Stash mới nhất đã apply, vẫn giữ dự phòng, không apply lại.
- QA ghi SHA commit nhận được, kết quả PASS/FAIL, lỗi cần sửa và dependency chưa
  chạy. Không xóa nhánh chung feature/t3-week3 hoặc force-push.
