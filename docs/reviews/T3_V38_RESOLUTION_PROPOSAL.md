# Phương án xử lý V38 T1/T3

> Đã triển khai phương án bằng V40; xem [hướng dẫn hiện hành](../guides/WEEK4_MIGRATION_RUNTIME.md)
> và [bằng chứng](T3_V40_CONVERGENCE_VERIFICATION.md). Nội dung bên dưới giữ làm đề xuất tại thời điểm trước triển khai.

Ngày 14/11/2026. Trạng thái: **đề xuất kỹ thuật, chưa triển khai migration hội tụ**.
Dựa trên [kiểm thử xung đột](T3_V38_COLLISION_VERIFICATION.md), runtime guide tuần 3,
Java V32 hiện hữu và dependency FK V39 T3 → V38 T3.

## Phương án khuyến nghị: giữ lịch sử, hội tụ tiến tới

Áp dụng khi cần hỗ trợ cả database đã chạy V38 scanner T1 và database đã chạy
V38 rubric/V39 visibility T3. Không yêu cầu xóa database hoặc giả định các database
đã dùng nhánh feature đều có thể bỏ.

1. Giữ nguyên tên, bytes/nội dung và checksum các SQL V38 T1, V38/V39 T3.
2. Di chuyển V38/V39 T3 khỏi `db/migration/base` sang
   `db/migration/week4-t3`. Default `classpath:db/migration` vẫn tìm thấy bộ này.
   Không chuyển các file V28/V29/V31 trong `main` vì Java V32 tham chiếu các path đó.
3. Lưu V38 scanner nguyên bản tại `db/legacy/t1-week4`, ngoài default scan.
4. Thêm Java migration hội tụ mới trong `db/migration/base`, số global lớn hơn 39
   và mọi migration đã phát hành cần hỗ trợ. V40 chỉ là ví dụ; phải fetch/đối chiếu
   refs DEV và chốt số trước khi triển khai. Không sửa V32 đã phát hành.
5. Migration hội tụ kiểm tra lịch sử/schema trước khi chạy DDL, bổ sung family còn
   thiếu: scanner; rubric/evaluation; revision/visibility/permissions. Nếu thiếu cả
   hai family T3 thì tạo rubric/evaluation trước visibility. Các SQL nguồn được
   giữ nguyên và đóng góp vào checksum migration Java mới, tương tự cơ chế V32.

V39 **không được tiếp tục nằm trong base** trong thiết kế này: DB T1 chưa có
`internship_evaluation_versions`, nên chạy V39 trước migration hội tụ sẽ lỗi FK.

## Locations dự kiến sau triển khai

| Lịch sử đã xác minh | Locations | Đường nâng cấp |
|---|---|---|
| DB mới hoặc main tới V37 | `classpath:db/migration` | T3 V38 → T3 V39 → hội tụ bổ sung scanner |
| T3 V38 hoặc V38/V39 | default | Validate checksum cũ, áp V39 nếu thiếu, hội tụ bổ sung scanner |
| T1 V38 scanner | `classpath:db/migration/base,classpath:db/migration/main,classpath:db/legacy/t1-week4` | Validate scanner cũ; hội tụ tạo rubric rồi visibility |
| Legacy T2/T4/T5 tuần 3 | Giữ locations theo runtime guide, không thêm `week4-t3` | Hội tụ mới trong base bổ sung scanner + rubric + visibility |
| Hybrid, checksum khác, failed migration hoặc schema thiếu một phần | Không tự chọn locations | Phân tích trên bản sao trước khi nâng cấp |

Đây là cấu hình **dự kiến**, không áp vào ứng dụng hiện tại khi các file chưa được
di chuyển và migration hội tụ chưa tồn tại. Giữ cấu hình phù hợp lịch sử ở các lần
khởi động sau. Không quét đồng thời `week4-t3` và `legacy/t1-week4`.

DB T1 không cần thêm dòng history giả cho V38/V39 T3: DDL bổ sung được ghi nhận
bằng version của migration hội tụ mới. Các dòng history cũ giữ nguyên.

## Yêu cầu implementation để không làm hỏng dữ liệu

- Preflight phải phân biệt family đầy đủ, hoàn toàn chưa có và tồn tại một phần.
  Không chỉ thấy một bảng/cột là bỏ qua toàn bộ family; kiểm cột, type, nullability,
  index, FK/CHECK và permission ceilings cần thiết. Schema lạ phải dừng trước DDL.
- Không dùng repair, sửa history, reset, out-of-order hoặc INSERT IGNORE để che sai lệch.
- Không sửa evaluation snapshots, published pointer, file hashes/bindings hoặc trạng
  thái CLEAN/REJECTED/PENDING hiện có; không tự đánh dấu file CLEAN.
- MySQL DDL không có rollback toàn bộ chuỗi như DML. Sao lưu/thử bản sao trước
  nâng môi trường cần giữ dữ liệu; lỗi giữa chừng không tự chạy sửa lịch sử.
- Sau di chuyển resources phải `clean` build để SQL cũ không còn trong target.
- Cập nhật manifest, runtime guide, test fixtures và locations của các test liên quan.

## Bộ kiểm chứng bắt buộc trước khi merge phương án

1. DB sạch; T3 V38 có evaluation; T3 V39 có policy/revision; T1 V38 có file scanner.
2. Mọi nhánh trên hội tụ cùng cấu trúc cuối, validate thành công, migrate lại no-op.
3. Đối chiếu nguyên vẹn version/script/checksum/success của mọi dòng history cũ.
4. Đối chiếu dữ liệu snapshots, total100, published pointer, policy/version,
   file/binding/hash và scan status/attempt/lease trước và sau nâng cấp.
5. Chạy lại 13 đường lịch sử tuần 3; test schema một phần/checksum lạ phải bị từ chối.
6. Hồi quy runtime T1 scanner và T3 evaluation/visibility trên source tích hợp;
   test migration đơn thuần không chứng minh scanner runtime đã tích hợp.

## Trường hợp đơn giản hơn

Nếu đã xác minh không có database cần bảo toàn nào chạy migration cần đổi số,
có thể thống nhất số global duy nhất cho các migration chưa phát hành, cập nhật
test/manifest và kiểm lại nâng cấp. Đây chỉ là phương án có điều kiện; chưa có
bằng chứng để mặc định chọn đổi tên V38 scanner sang V40 trong phiên này.

## Phân công tích hợp

- T1 và T3 xác nhận snapshot SQL, dependency và lịch sử database cần hỗ trợ.
- Người tích hợp triển khai locations/migration chung và cập nhật hướng dẫn.
- QA kiểm bốn đường tuần 4 cùng hồi quy, leader chốt merge sau bằng chứng.
- DEV06 T3-11 vẫn giữ trên `feature/t3-week4`; đề xuất này không thay quyền quyết
  định triển khai database chung và chưa đánh dấu QA PASS.
