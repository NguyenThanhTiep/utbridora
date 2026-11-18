# Hội tụ migration tuần 4: T1 scanner và T3 evaluation/visibility

Ngày 14/11/2026. Triển khai DEV trên `feature/t3-week4`, chưa deploy DB chung,
chưa QA/CI/merge. [Bằng chứng kiểm thử](../reviews/T3_V40_CONVERGENCE_VERIFICATION.md).

## Cấu trúc và lịch sử

- Giữ nguyên SQL V38 rubric và V39 visibility, chuyển từ `db/migration/base` sang
  `db/migration/week4-t3`. Không đổi tên script/checksum hay sửa dòng history cũ.
- V38 scanner nguyên bản từ T1 commit `32478e6b4a7431b33989a8ac318a5cc46fd9eb4b`
  nằm ở `db/legacy/t1-week4`, ngoài default scan.
- V40 Java trong base kiểm tra schema/history rồi bổ sung family còn thiếu.
  T1: tạo evaluation trước visibility. T3: bổ sung scanner. Legacy tuần 3: bổ sung
  cả ba family. V39 không còn trong base để tránh chạy trước bảng evaluation ở DB T1.
- V1–V37, Java V32 và các path SQL V32 tham chiếu được giữ nguyên.
- V40 đã được đối chiếu với các refs DEV sau fetch ngày 14/11: chưa thấy số 40
  khác ở thời điểm triển khai. Trước merge vẫn fetch lại để phát hiện phát sinh mới.

## Preflight và locations

Trước khi nâng DB cần bảo toàn: sao lưu, thử bản sao riêng, đọc toàn bộ
`flyway_schema_history` gồm version/script/checksum/success. Không suy lịch sử từ
tên nhánh. Giữ các yêu cầu preflight GPA/legacy của [runtime tuần 3](WEEK3_INTEGRATION_RUNTIME.md).

| Lịch sử đã xác minh | `SPRING_FLYWAY_LOCATIONS` |
|---|---|
| DB sạch, main ≤V37, T3 V38 hoặc T3 V38/V39 | `classpath:db/migration` (default) |
| T1 V38 scanner, kế thừa main tuần 3 | `classpath:db/migration/base,classpath:db/migration/main,classpath:db/legacy/t1-week4` |
| T2/T4/T5 legacy tuần 3, chưa có V38 tuần 4 | Giữ đúng locations đã xác minh trong runtime tuần 3; V40 trong base bổ sung schema tuần 4 |
| Lịch sử hybrid khác, checksum sai, failed migration hoặc DDL ngoài hồ sơ | Dừng, phân tích bản sao; không tự chọn cấu hình gần giống |

Không kết hợp `week4-t3` với `legacy/t1-week4`: chúng cùng có V38.
Không trộn main vào các legacy T2/T4/T5. Giữ locations của database ở các lần start
sau nâng cấp; V40 không viết giả V38/V39 của team khác vào history.

Chỉ đặt biến cấu hình trên stack riêng được chọn. Không tự thay cấu hình hoặc
database của `utbridora-local:8088`. Sau khi di chuyển tài nguyên, chạy build
`clean` để SQL cũ trong target không gây duplicate version.

## Điều kiện V40 kiểm tra

Schema reference `db/convergence/week4-shapes.txt` được tạo từ các SQL bất biến
trên MySQL 8.4 và được kiểm chứng lại độc lập bằng `Week4SchemaReferenceIT`.
So sánh cột/type/default/nullability/collation/engine, index, FK/CHECK và exact
permission/role ceilings. Family thiếu một phần, schema lạ hoặc history không
khớp đều bị từ chối trước DDL của V40. Cấu hình collation/engine khác reference
cũng cần review; không bỏ kiểm tra để ép chạy. Reference hiện dùng utf8mb4_0900_ai_ci.

Không rewrite snapshot điểm, policy version, file hash/binding/scan state. DDL
scanner giữ CLEAN/REJECTED/PENDING; không fake CLEAN. Không repair/reset/out-of-order
hay sửa history. MySQL DDL không rollback nguyên chuỗi: nếu lỗi giữa chừng, giữ
log và phân tích trên bản sao/khôi phục theo quy trình backup; không tự rerun bằng
cách xóa dòng failed.

Sau phát hành, Java V40, helper `db.convergence.Week4Schema`, reference và SQL nguồn
là bất biến. Thay đổi tiếp theo phải dùng migration version mới. Manifest theo dõi
SQL, Java và schema reference; checksum V40 gồm SQL nguồn + reference chuẩn hóa LF.

## Kiểm tra trên source này

```powershell
.\mvnw.cmd -B '-Dit.test=Week4ConvergenceIT,Week4SchemaReferenceIT,Week4MigrationCollisionIT,Week3MigrationIT,T311SchemaUpgradeIT,MigrationUpgradeIT,T308SchemaUpgradeIT,T310EvaluationRuntimeIT,T311GradeRuntimeIT' clean verify
py -3.11 scripts/contracts/build_contract_bundle.py --check
py -3.11 scripts/contracts/verify_contracts.py --output target/contract-verification.json
```

Testcontainers tạo MySQL riêng. Các ca collision vẫn phải PASS bằng cách bắt đúng
lỗi khi cố dùng locations sai. Các ca convergence phải nâng thành công, history/data
giữ nguyên, validate thành công và migrate lại no-op.

V40 hội tụ **schema**, không tự nhập code worker scanner T1. Runtime scanner T1 vẫn
cần tích hợp code và kiểm thử cùng nhánh T1; không dùng migration PASS làm bằng chứng
antivirus hoặc QA nghiệp vụ toàn hệ thống đã đạt.

Khi ghép nhánh scanner T1, giữ SQL scanner duy nhất tại `db/legacy/t1-week4`.
Nhánh T1 hiện vẫn chứa bản `db/migration/base/V38__durable_private_file_scans.sql`;
không để thao tác merge đưa bản đó trở lại default scan. Đối chiếu nội dung rồi
giữ vị trí mới (T1 có thể nhận cùng thay đổi di chuyển trước khi tích hợp).
Đây là xử lý file trong source, không xóa migration đã áp dụng khỏi history DB.
Chạy clean verify sau merge; kiểm thử collision không thay cho kiểm thử worker.
