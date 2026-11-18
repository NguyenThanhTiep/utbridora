# Kiểm thử xung đột V38 giữa T1 và T3

> Báo cáo chẩn đoán trước khi sửa. Nay có đường nâng cấp V40 với locations đúng:
> [triển khai và kiểm thử hội tụ](T3_V40_CONVERGENCE_VERIFICATION.md).
> Gộp hai V38 vào cùng locations vẫn bị từ chối có chủ đích.

Ngày 14/11/2026, kết thúc 11:54:12 +07. Nhánh `feature/t3-week4`, base
`e480abc2bf0a7dd474487dbdff46c857b0cb40b5`, cùng thay đổi T3-11 local.
Nguồn scanner: remote-tracking ref `origin/feature/t1-07-file-scanning` tại
`32478e6b4a7431b33989a8ac318a5cc46fd9eb4b`; đây là snapshot local đã fetch trước,
không khẳng định HEAD mới nhất trên GitHub.

## Kết quả thực chạy

Maven **BUILD SUCCESS**, 4 tests, 0 failures/errors/skips, MySQL 8.4 Testcontainers
riêng. Không sửa database dùng chung, production migration hoặc Flyway history.

| Ca | Kết quả quan sát |
|---|---|
| T311SchemaUpgradeIT | V38 T3 có dữ liệu nâng lên V39 thành công; giữ snapshot/pointer, kiểm constraints |
| Hai V38 cùng locations trên DB mới | Flyway từ chối với `Found more than one migration with version 38`; chưa tạo bảng nghiệp vụ files/evaluation |
| DB đã chạy V38 scanner T1, dùng default T3 | Validate thất bại tại V38; toàn bộ version/script/checksum/success trong history giữ nguyên |
| DB đã chạy V38/V39 T3, dùng locations của T1 | Validate thất bại tại V38; toàn bộ version/script/checksum/success trong history giữ nguyên |

Hai cấu hình độc lập đều được migrate, validate và migrate lại no-op trước khi thử
đổi cấu hình. Các test âm tính PASS vì **bắt đúng lỗi mong đợi**, không có nghĩa
hai nhánh đã tích hợp hoặc có thể triển khai chung.

## Liên hệ runtime guide

[WEEK3_INTEGRATION_RUNTIME.md](../guides/WEEK3_INTEGRATION_RUNTIME.md) đã mô tả cách
tách locations và thêm migration hội tụ cho lịch sử V28–V31. Kết quả này xác nhận
default/locations hiện tại chưa giải quyết hai lịch sử V38. Nếu phải giữ cả hai
lịch sử đã áp dụng, cần bổ sung đường hội tụ tuần 4 và test dữ liệu cho cả hai;
không dùng nguyên bảng locations tuần 3 hoặc chỉ đổi tên migration đã chạy.

Chưa triển khai/test migration hội tụ V38 mới, chưa quyết định phân số global,
chưa merge code scanner với T3, chưa chạy nghiệp vụ scanner tích hợp. Đây là
kiểm thử chẩn đoán theo yêu cầu, không thay QA hay kiểm tra database thật.

## Chạy lại

```powershell
.\mvnw.cmd -B '-Dit.test=Week4MigrationCollisionIT,T311SchemaUpgradeIT' test-compile failsafe:integration-test failsafe:verify
```

- Test: `src/test/java/vn/utbridora/auth/Week4MigrationCollisionIT.java`.
- SQL T1 được sao chép nguyên nội dung vào `src/test/resources/migration-collision/t1`;
  nằm ngoài default `db/migration`, chỉ test chẩn đoán nạp rõ location này.
- JUnit: `target/failsafe-reports/TEST-vn.utbridora.auth.Week4MigrationCollisionIT.xml`
  và `TEST-vn.utbridora.auth.T311SchemaUpgradeIT.xml`.
- Log local: `D:\TeamCodeHKDN\t3-v38-collision-verify.log`.
