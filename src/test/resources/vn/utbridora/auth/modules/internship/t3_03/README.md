# T3-03 / DEV07 — tài liệu và bằng chứng kiểm thử

Owner: DEV07 — Lại Thái Thuận. Sắp xếp lại ngày **10/11/2026**.

Thư mục này tập trung tài nguyên của task dưới `src/test/resources`. Báo cáo,
ảnh và kết quả kiểm thử không phải class Java; đặt ở đây giúp giữ chúng trong
phạm vi test. Logic API và các test thực thi giữ nguyên vị trí/cấu hình hiện có.

## Các file đã chuyển từ docs

- `docs/guides/T3_03_HR_MENTOR_WORKSPACE.md` →
  [guides/T3_03_HR_MENTOR_WORKSPACE.md](guides/T3_03_HR_MENTOR_WORKSPACE.md).
- `docs/testing/T3_03_TEST_REPORT.md` →
  [testing/T3_03_TEST_REPORT.md](testing/T3_03_TEST_REPORT.md).
- `docs/testing/T3_03_QA_HANDOFF.md` →
  [testing/T3_03_QA_HANDOFF.md](testing/T3_03_QA_HANDOFF.md).
- Bốn JSON và một XML trước ở `docs/testing/evidence/` → `evidence/`:
  [T3_03_DEV_VERIFICATION.json](evidence/T3_03_DEV_VERIFICATION.json),
  [T3_03_FULL_VERIFICATION_2026_11_06.json](evidence/T3_03_FULL_VERIFICATION_2026_11_06.json),
  [T3_03_DEPENDENCY_VERIFICATION_2026_11_06.json](evidence/T3_03_DEPENDENCY_VERIFICATION_2026_11_06.json),
  [T3_03_QA_VERIFICATION_2026_11_07.json](evidence/T3_03_QA_VERIFICATION_2026_11_07.json),
  [T3_03_UI_UNIT_RESULTS.xml](evidence/T3_03_UI_UNIT_RESULTS.xml).
- Hai ảnh trước ở `docs/testing/evidence/t3-03/` → `evidence/t3-03/`:
  [groups-1280.png](evidence/t3-03/groups-1280.png),
  [groups-320.png](evidence/t3-03/groups-320.png).

Mỗi file có một bản ở vị trí mới. Các liên kết trong mục lục `docs/README.md`
đã dẫn tới đây; liên kết tương đối và đường dẫn hướng dẫn trong ba Markdown
đã được cập nhật theo vị trí mới.

## Vị trí code và test thực thi

- API backend: `src/main/java/vn/utbridora/auth/modules/internship/api/`.
- Test Java của module: `src/test/java/vn/utbridora/auth/modules/internship/`.
  Test consumer do DEV07 thêm:
  `contract/WorkspaceFrontendContractTest.java` trong thư mục Java test trên.
- UI và client API của DEV07: `web-demo/src/modules/internship/`.
- Test frontend: `web-demo/tests/internship.unit.spec.ts`,
  `web-demo/tests/internship.ui.spec.ts`; test runtime hiện hành:
  `web-demo/tests/internship-runtime.spec.ts`.
- Fixture mà Java/frontend cùng sử dụng: `web-demo/tests/fixtures/internship*`.
  Đây là input của test đang chạy, khác với các báo cáo/ảnh lưu tại đây.

## Giữ nguyên bằng chứng lịch sử

Cả 10 file đã được đối chiếu SHA-256 ngay sau khi chuyển. Sau đó chỉ cập nhật
liên kết/đường dẫn trong Markdown. Bảy file JSON/XML/PNG vẫn giữ nguyên từng byte
và SHA-256; các path, trạng thái, ngày, hash source bên trong phản ánh checkpoint
lúc kiểm thử. Khi cần tìm tài liệu được snapshot trỏ tới bằng path `docs/...` cũ,
dùng danh sách di chuyển phía trên. Không sửa snapshot cũ thành kết quả mới.

Các lệnh Git, thông báo pending và kết quả trong báo cáo cũ thuộc thời điểm ghi
nhận; việc di chuyển không chạy lại test, tạo QA signoff hoặc thay tiến độ task.
Xem tài liệu runtime/acceptance hiện hành của tuần tương ứng trong `docs/` khi
đánh giá phiên bản ứng dụng mới.
