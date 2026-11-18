# Runtime tích hợp tuần 3 và đường nâng cấp database

> Cập nhật tuần 4: V38 scanner T1 và V38/V39 T3 được hội tụ bằng V40.
> Xem [locations, preflight và kiểm thử tuần 4](WEEK4_MIGRATION_RUNTIME.md).
> Các bảng V28–V33 bên dưới mô tả lịch sử tuần 3; không dùng chúng để tự chọn đường nâng cấp DB đã chạy V38.

Ngày 13/11/2026. Source tích hợp T1/T2/T3/T4/T5; [ma trận task và evidence](../testing/WEEK3_INTEGRATION_ACCEPTANCE.md). Chỉ dựng môi trường test/dev riêng trong lần kiểm chứng này. Không deploy/rebuild `utbridora-local:8088`, không seed/import hoặc đổi dữ liệu/tài khoản/volume hiện có. UI 8088 có thể chưa phản ánh main mới.

Sự cố trong lượt full local đầu: container MySQL test tích lũy làm cạn bộ nhớ Docker; backend shared cũ bị OOM-kill (exit137). Đã dừng local IT, chỉ dọn các container test của lượt đó và start lại **đúng container/image backend cũ**; bốn container shared đã healthy. Không rebuild, thay source/config/image hay chạy seed/import. Các lượt full tiếp theo chạy trên GitHub CI riêng. Failsafe nay chạy tuần tự, mỗi class IT một JVM để giải phóng Testcontainers sau class; vẫn phải theo dõi tài nguyên khi chạy local.

## Nhận code và chạy kiểm tra

Fetch/pull main trên checkout sạch, kiểm SHA ở [evidence](../testing/evidence/WEEK3_INTEGRATION_VERIFICATION.json). JDK21, Docker, Node/npm theo lockfile; không chép `.env.local-demo` vào repo/worktree test. Contracts dùng Python virtualenv có requirements của repo.

```powershell
Push-Location web-demo
npm.cmd ci
npm.cmd run build
Pop-Location
.\mvnw.cmd -B '-Dfrontend.e2e=true' '-Dt5.week3.browser=true' verify
```

Testcontainers tạo database/port riêng; browser chạy tuần tự ở 5178. Không chạy thêm một Vite/browser harness song song. CI dùng cùng hai cờ; các harness có fixture riêng không được gọi thẳng toàn bộ Playwright mà thiếu backend/prerequisites. `CorePlatformIT` bao gồm UI fixture tests và các ca Core thật; T2/T3/T5 harness kiểm API/database thật riêng. Fixture CLEAN không chứng minh scanner.

Khi di chuyển tài nguyên migration, chạy một lần `mvn clean ... verify` trong checkout test để xóa SQL cũ trong target; SQL cũ không phải lịch sử database. Không `git clean/reset` checkout người dùng và không reset database để vượt Flyway.

## Một chuỗi tiến tới V33, giữ nguyên lịch sử đã áp dụng

Các nhánh dùng cùng số cho DDL khác nhau. Chỉ đổi tên chúng thành số mới sẽ làm mất đường validate cho DB đã chạy feature. Bản tích hợp tách **locations** theo lịch sử, giữ tên/nội dung/checksum lịch sử và thêm migration tiến tới chung.

| Location | Nội dung |
|---|---|
| `classpath:db/migration/base` | V1–V27 nguyên nội dung; Java V32 hội tụ; V33 mới cho SYSTEM activation history |
| `classpath:db/migration/main` | V28 private files T1; V29 Submission/Review/Comment T3; V31 Weekly Report T3, nguyên nội dung nguồn |
| `classpath:db/legacy/t2` | V28 student_preferences nguyên bản T2 |
| `classpath:db/legacy/t4` | V28 protected release, V29 matching audit nguyên bản T4 |
| `classpath:db/legacy/t4-v30`, `classpath:db/legacy/t4-v31` | V30 GPA column và V31 GPA JSON backfill nguyên bản; chỉ thêm location tương ứng khi lịch sử DB **đã áp dụng** script đó |
| `classpath:db/legacy/t5` | V28 placement permission/active key, V29 placement history, V30 proposal/publication nguyên bản T5 |

Default Spring/Flyway location `classpath:db/migration` quét base và main đệ quy. Không có hai V28/V29 trong default. Khoảng số V30 không xuất hiện ở lineage main là chủ ý, không bật out-of-order để lấp số. V32 tạo các family còn thiếu theo thứ tự file → submission/comment → report → preference → placement/history/publication → protected release/audit. Các family đã có từ đúng lineage được giữ. V33 thêm actor_kind USER/SYSTEM, cho actor_id null đúng SYSTEM, giữ actor cũ và FK/history.

Java V32 có checksum từ các SQL nguyên bản chuẩn hóa LF. Không sửa source migration này hoặc các script nó tham chiếu sau phát hành; thay đổi sau phải là migration version mới. Builder manifest theo dõi cả SQL archived và Java. `Week3MigrationIT` so sánh cột sau hội tụ, giữ nguyên mọi dòng history/checksum cũ và migrate lại no-op; các schema IT T3 kiểm constraint, dữ liệu/metadata/binding cũ.

## Preflight bắt buộc trước khi chọn locations

Người vận hành phải sao lưu và thử trên bản sao riêng trước khi nâng DB đang sử dụng. Đọc lịch sử thật; không suy từ tên nhánh checkout, version lớn nhất hoặc báo cáo lịch sử. Không in password/connection secret trong lệnh hay log.

```sql
SELECT version,description,script,checksum,success
FROM flyway_schema_history ORDER BY installed_rank;
```

1. Xác nhận V1–V27 đúng bản gốc, không failed migration và không DDL thủ công ngoài hồ sơ.
2. Đối chiếu **script và checksum**, không chỉ số V28/V29, với các file lịch sử đã lưu. Chọn đúng hàng dưới đây.
3. Trên DB T4 đã dùng GPA, kiểm current/immutable JSON có `required.minGpa` khác null và snapshot có GPA thật. Contract v1.0 chưa mở nghiệp vụ này. Không âm thầm bỏ predicate, xóa cột, sửa immutable revision/hash hoặc gán phê duyệt mới.
4. Nếu có criteria GPA khác null, owner phải rà soát bằng phiên bản cũ và quyết định có truy vết trước nâng ứng dụng; giữ DB/runtime cũ cho tới khi xử lý xong. Persistence reader mới chỉ bỏ **null dự phòng** trong bộ nhớ, giữ JSON lịch sử; non-null trả `LEGACY_CRITERIA_REVIEW_REQUIRED`. Snapshot/run `strict-gpa-v1` không được dùng để nominate/Placement dưới `strict-v1`; capture/rerun theo contract được duyệt. Đây là giới hạn dữ liệu legacy, không phải dữ liệu GPA đã được chấp thuận.
5. Với T4, chỉ chọn thêm `t4-v30` nếu V30 GPA đã có trong lịch sử, và chỉ chọn thêm `t4-v31` nếu V31 GPA đã có. Không mới chạy V31 backfill lên revision/snapshot bất biến; location tách ra để validate checksum script đã chạy, không tái phê duyệt GPA.
6. Nếu lịch sử là hybrid/không khớp checksum hoặc có migration failed, dừng và phân tích trên bản sao. Không `repair`, sửa `flyway_schema_history`, reset DB hoặc tự đổi tên/checksum file để làm validation xanh.

## Chọn đường nâng cấp theo lịch sử đã xác minh

| Trạng thái DB | Locations cho **bản ứng dụng mới** | Kết quả |
|---|---|---|
| DB sạch; main ≤V27 | `classpath:db/migration` (default) | V1–27→V28/T1→V29/T3→V31/report→V32→V33 |
| T1 V28 private files | default | Validate V28 cũ, bổ sung T3 rồi V32/V33 |
| T3 V28 private files + V29 submission, có/không V31 report | default | Validate đúng các script đã có, áp phần còn thiếu rồi V32/V33 |
| T2 V28 student_preferences | `classpath:db/migration/base,classpath:db/legacy/t2` | Validate V28/T2, V32 tạo các family khác, V33 |
| T4 V28 protected hoặc V29 audit | `classpath:db/migration/base,classpath:db/legacy/t4` | Validate V28/V29, bổ sung audit nếu pending, V32/V33; không thêm cột GPA hoặc chạy backfill V31 |
| T4 đã có V30 GPA, chưa có V31 | `classpath:db/migration/base,classpath:db/legacy/t4,classpath:db/legacy/t4-v30` | Validate V30/cột đã có, V32/V33; không chạy mới V31 và không sửa JSON/revision/hash |
| T4 đã có V31 GPA backfill | `classpath:db/migration/base,classpath:db/legacy/t4,classpath:db/legacy/t4-v30,classpath:db/legacy/t4-v31` | Validate toàn bộ script/checksum đã có, V32/V33; giữ JSON/GPA/history hiện hữu và áp giới hạn GPA nêu trên |
| T5 V28 placement, tới V29/V30 | `classpath:db/migration/base,classpath:db/legacy/t5` | Validate T5, áp phần còn pending, V32/V33 |

Đặt bằng cấu hình môi trường của **stack riêng**, ví dụ `SPRING_FLYWAY_LOCATIONS` với giá trị trong bảng; giữ cấu hình lineage này cho các lần start sau. Không trộn thêm location main vào legacy vì sẽ có version collision. Khi thêm migration chung sau V33, đặt trong base để mọi lineage nhận được. Không sửa môi trường 8088 trong lượt tích hợp.

Các đường có kiểm thử: clean, main V27, T1 V28, T3 V29/V31; T2 V28; T4 V28/V29/V30/V31; T5 V28/V29/V30. Test dùng DB/schema riêng, đối chiếu shape cột (ngoại trừ cột GPA reserved legacy), giữ nguyên history và validate/reapply no-op. Fixture T4 lưu JSON current/revision/snapshot cùng criteria hash trước upgrade rồi đối chiếu nguyên vẹn sau upgrade; V31 chưa áp dụng không được mới chạy. Đây không phải xác nhận nâng một DB thật cụ thể đã thành công. Chạy lại bộ dưới đây trên source nhận được và thử bản sao dữ liệu môi trường thực trước vận hành.

```powershell
.\mvnw.cmd -B '-Dit.test=Week3MigrationIT,MigrationUpgradeIT,T307SchemaUpgradeIT,T308SchemaUpgradeIT' verify
```

## Các điểm nối runtime đã có

- T2 `StudentOpportunityPort` → T5 `OpportunityPort.readForPreference` với ActorContext từ session; giữ approved/publication ổn định tới commit. Window đóng trả OPPORTUNITY_CLOSED; sai school/term hoặc chưa publish trả404. Không có bảng Opportunity thứ hai.
- T5 Placement dùng T2 eligibility và profile snapshots hiện hành, T5 criteria/verification/quota; create PENDING không chiếm key, approve chiếm UNIQUE(student,term), activate chỉ APPROVED và due UTC. Permission **đúng term** được kiểm trước cả replay ledger. Không lấy quyền của term khác để trả response cũ.
- `PlacementActivationWorker` SYSTEM chỉ chuyển due APPROVED→ACTIVE qua cùng guard/transaction với manual. `app.t5.placement-activation.enabled` bật mặc định; IT tắt timer và gọi owner/worker rõ. History SYSTEM không giả actor người dùng. Không có public SYSTEM endpoint.
- `PlacementOutboxWorker` giữ delivery at-least-once, T3 inbox/projection đọc lại nguồn, ACK sau consumer commit; retry 1/5/30/300/1800 rồi DLQ. Task/report/submission vẫn đọc source khi mutation, không chờ event cancel đến mới khóa.
- T5 profile projection → T1 `ProfileAccessFacade` + T2 consent/profile → T1 FileRead adapter. Shared file chỉ current attachment, current policy/consent/share/permission, đúng binding/CLEAN và neutral filename; từng GET/Range kiểm lại. Core ADMIN không có bypass.
- T3 Submission/review/comment/report/revision/history và UI đã có; URL HTTPS không fetch. File mới PENDING, report submit/file download buộc CLEAN. Scanner T1-07 chưa được triển khai.
- Gate Submission nhận baseline T1/T3 ở locations đệ quy hoặc successful V32 của legacy, rồi kiểm cột thật; Flyway vẫn validate checksum khi start. Cooperation list/detail là transaction chỉ đọc, tránh chu kỳ khóa giữa list và các detail GET song song của Matching; mutation vẫn giữ khóa hiện hành.

## Protected release T4

Mặc định `matching_privacy_release_settings.enabled=false`. Chỉ Ops chuẩn bị frozen snapshot và đăng ký batch/active batch sau privacy review. Không có endpoint cho enterprise tự chọn/reset batch. Test bật gate bằng fixture riêng không cho phép rollout shared stack và không phải QA04 signoff.

Scope ledger `(enterpriseOrgId,schoolOrgId,termId,demoBatchId)`; fingerprint canonical §25.8.2. Cùng fingerprint replay DTO đã lưu, khác fingerprint trả409 PRIVACY_RELEASE_LOCKED. EligibleBand 0–4 SUPPRESSED, 5–9, 10–19, 20+. Evaluate/summary/CSV/chart enterprise không có raw count/total/%/breakdown/PII; GET cũng kiểm gate/current own authority. School detail/PII access là các guard riêng. Audit denial E.2 không chứa fingerprint/filter/count; lỗi audit vẫn giữ deny.

Không có benchmark 400/5.000 SV hay QA04/Lead signoff trong việc chạy fixtures. 1.000 cặp fixture không tương đương 1.000 SV. Xem các bàn giao [T2](../handoffs/T2_TUAN3_BAN_GIAO_TUAN4.md), [T3](../handoffs/T3_TUAN3_BAN_GIAO_TUAN4.md), [T4](../handoffs/T4_TUAN3_BAN_GIAO_TUAN4.md), [T5](../handoffs/T5_TUAN3_BAN_GIAO_TUAN4.md) cho phần tuần 4.
