# V40 — hội tụ lịch sử scanner T1 và evaluation/visibility T3

Ngày 14/11/2026. Nhánh `feature/t3-week4`, base DEV05
`e480abc2bf0a7dd474487dbdff46c857b0cb40b5`. Thay đổi local chưa commit/push/merge.
T1 SQL nguồn: `32478e6b4a7431b33989a8ac318a5cc46fd9eb4b`.

## Triển khai

- V38/V39 T3 chuyển nguyên nội dung từ base sang `db/migration/week4-t3`;
  V38 scanner nguyên bản vào `db/legacy/t1-week4`.
- Java V40 trong base bổ sung family thiếu theo thứ tự phụ thuộc. Kiểm schema
  và history trước DDL, hậu kiểm cấu trúc sau DDL; không repair/reset/out-of-order.
- Schema reference có cột/type/default/engine/collation, index, FK/CHECK và exact
  permission ceilings. Reference được tạo từ SQL bất biến và kiểm chứng độc lập.
- Checksum V40 gồm ba SQL nguồn và reference chuẩn hóa LF. Manifest bổ sung hash
  của reference, giữ Contract Pack/SRS đã duyệt nguyên vẹn.
- V1–V37 và Java V32 không đổi. Git blob V38 T3 trước/sau di chuyển đều là
  `bad948bb3173060a9fe0a8dcdee31da126dc693c`.

## Kiểm chứng và phạm vi

Lượt tập trung: **18/18 PASS**, Maven BUILD SUCCESS lúc 12:15:06 +07.
Sau đó bổ sung fixture bố cục thư mục lịch sử thật và kiểm file bindings;
lượt clean verify cuối trên source đầy đủ được ghi ở mục kết quả cuối bên dưới.

- 5 đường: DB mới, main V37, T3 V38, T3 V39, T1 V38. Đường feature dựng SQL trong
  bố cục cũ `base`, rồi đổi sang locations mới để kiểm checksum/history/path thật.
- 7 ca từ chối: thiếu index scanner, sai type scanner, thiếu FK evaluation,
  sai precision điểm, thiếu permission ceiling, mất cả family đã ghi history,
  schema xuất hiện ngoài lịch sử. So sánh schema trước/sau để đảm bảo V40 không
  chạy DDL nghiệp vụ trong các ca preflight lỗi; giữ nguyên mọi dòng history cũ.
- 1 ca chọn nhầm lịch sử vẫn bị Flyway từ chối. Cộng 13 ca Week4ConvergenceIT.
- Kiểm dữ liệu file PENDING/CLEAN/REJECTED, hash/storage key/bindings, scan attempts,
  lease/version; evaluation snapshots/total100/current/published pointer; revision
  decisions, school/HR policy versions. Dữ liệu synthetic, không là antivirus thật.
- Các ca thành công phải validate PASS và migrate lại no-op. Các lỗi mong đợi
  được assert rõ, không đổi expected để coi migration thất bại là nâng cấp thành công.

Lượt đầu có 4 lỗi do kiểm tên history chưa tính đường dẫn tương đối Flyway lưu.
Đã sửa nhận diện basename với suffix path chính xác, không sửa history/checksum.
Lượt kiểm lại 18 ca PASS; log đỏ vẫn giữ ở workspace để truy vết.

Các count migration cũ trong MigrationUpgradeIT/T308SchemaUpgradeIT tăng 2 vì thêm
V39/V40 so với baseline V38. Giữ mọi assertion dữ liệu/constraint; T310/T311 schema
tests pin target 38/39 để tiếp tục kiểm đúng phạm vi owner. Week3MigrationIT kiểm
13 lịch sử cũ đến V40 và giữ checksum/history.

## Kết quả cuối

**BUILD SUCCESS lúc 12:27:51 +07, clean verify 10 phút 59 giây.**
[Bằng chứng JSON, source hashes và log hash](T3_V40_DEV_EVIDENCE.json).

| Bộ kiểm thử | Kết quả |
|---|---|
| Toàn bộ unit | 1.333 PASS, 0 failure/error, 1 skip do Windows không cho tạo symlink |
| Week4ConvergenceIT | 13/13 PASS, gồm bố cục lịch sử cũ và dữ liệu/bindings thật trong fixture |
| Week4MigrationCollisionIT + Week4SchemaReferenceIT | 4/4 PASS |
| Week3MigrationIT | 1/1 PASS, bao phủ 13 lịch sử tuần 3 lên V40 |
| MigrationUpgradeIT + T308SchemaUpgradeIT + T311SchemaUpgradeIT | 6/6 PASS |
| T310EvaluationRuntimeIT + T311GradeRuntimeIT trên V40 | 35/35 PASS |
| Tổng integration của lượt cuối | **59/59 PASS, 0 failure/error/skip** |
| Contract bundle / audit / portability | 868/868 checks và 4/4 Python tests PASS |

Phần triển khai DEV hội tụ đã hoàn tất trong source. Chưa áp dụng database thật
hoặc nghiệm thu toàn bộ runtime scanner; xem giới hạn bên dưới.

## Chạy lại và vận hành

```powershell
.\mvnw.cmd -B '-Dit.test=Week4ConvergenceIT,Week4SchemaReferenceIT,Week4MigrationCollisionIT,Week3MigrationIT,T311SchemaUpgradeIT,MigrationUpgradeIT,T308SchemaUpgradeIT,T310EvaluationRuntimeIT,T311GradeRuntimeIT' clean verify
```

Log: `D:\TeamCodeHKDN\week4-convergence-final.log`; JUnit ở target/failsafe-reports.
Chỉ MySQL 8.4 Testcontainers riêng, JDK21. Không đổi database/config/container demo chung.
Chi tiết chọn locations và preflight: [runtime tuần 4](../guides/WEEK4_MIGRATION_RUNTIME.md).

## Giới hạn bàn giao

- Chưa triển khai DB thật, QA/Lead và GitHub CI của thay đổi local chưa xác nhận.
- Schema scanner được tích hợp; code worker scanner T1 chưa merge/test runtime
  trong lượt này. Không chứng minh file đã quét antivirus thật.
- Khi merge T1, không đưa lại bản scanner V38 trong base; giữ bản tại legacy.
- MySQL DDL có thể commit từng bước. Lỗi I/O/DDL giữa chừng cần xử lý trên bản sao
  theo backup, không tự xóa history failed. DB hybrid/khác reference phải preflight riêng.
