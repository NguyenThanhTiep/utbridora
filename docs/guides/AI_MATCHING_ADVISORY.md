# AI xếp hạng gợi ý cho nhà trường

Lượt 3 ngày 21/11/2026 đã bổ sung code backend/API/UI theo [extension ai-advisory-v1](../contracts/extensions/ai-advisory-v1/README.md) và [quyết định triển khai](../contracts/extensions/AI_ADVISORY_IMPLEMENTATION_DECISION.md). Tính năng **mặc định OFF**, chưa bật trên demo hoặc merge/deploy. Giai đoạn 6 có code và kiểm chứng DEV ở các tầng bên dưới; kiểm model thật, MySQL runtime và chất lượng đề xuất vẫn thuộc giai đoạn 7.

## Luồng đã triển khai trong code

Nhà trường chạy Matching như hiện hành, sau đó chủ động tạo gợi ý AI. Endpoint mới lấy toàn bộ MATCH của school-origin run, capture nội dung qua owner T2/T5, tạo job bền vững và trả ACK. Worker xử lý sau commit, công bố một snapshot xếp hạng hoàn chỉnh. Cán bộ chọn sinh viên trên giao diện và gọi luồng đề cử hiện hành.

- POST `/api/v1/matching/runs/{runId}/advisories`
- GET `/api/v1/matching/advisories/{advisoryId}`
- GET `/api/v1/matching/advisories/{advisoryId}/items?page=0&size=25`

Các route có session/CSRF theo phương thức, current scope/field rights và no-store. Không có enterprise endpoint mới hoặc quyền ADMIN bypass. Các DTO canonical, strict classifier, fitScore/canonical order và protected enterprise release giữ nguyên.

## Dữ liệu và bảo vệ kết quả

T2 snapshot chỉ lấy skills/project được phép, giữ SELF_DECLARED/SCHOOL_VERIFIED theo nguồn. T5 snapshot lấy title/JD từ đúng immutable demand revision và lưu cooperation ID/version/state làm ngữ cảnh current check. Không gửi tên/email/điện thoại/URL/file identifiers vào Python. Free text vẫn có thể chứa thông tin do người dùng tự gõ; không tuyên bố đã ẩn danh hoàn toàn.

Thay đổi chỉ project/note cũng làm advisory cũ stale vì kiểm full profileVersion, không chỉ relevantHash. Thay demand/cooperation/catalog hoặc thu hẹp quyền nguồn chặn cả score/rank. Exact delegation stamps gồm các grant cung cấp field mask và toàn bộ ancestry/version; job không chuyển sang grant khác sau revocation. Current role/membership/user/organization và owner sources được fence trong transaction trước khi đọc/trước publish.

Migration mới **V44__school_ai_advisory.sql** thêm origin sidecar, owner snapshots, job/row/control và hai role capability. Migration không tự cấp delegation và không backfill school-origin cho run cũ. Muốn dùng gợi ý cần evaluate một school run mới; enterprise/legacy-unknown run bị từ chối.

## Worker và chính sách ranking

Default một worker, DB queue tối đa 32 active toàn hệ thống/2 mỗi trường/1 mỗi run, tối đa 10 lần tạo/run/24h. Admission dùng global guard và locking current reads để tránh snapshot REPEATABLE READ cũ sau khi chờ khóa. Lease 90s, heartbeat 15s, tối đa 3 attempts, backoff 5s/30s; attempt budget 600s. Timer chỉ đánh thức daemon, không chờ inference. Lease token/current-source checks bảo vệ publish; batch lỗi không có READY một phần.

Policy `semantic-desc-canonical-tie-v1`: cosine HALF_UP sáu chữ số giảm dần, hòa theo fitScore rồi UUID. Hồ sơ thiếu nội dung/quá lớn vẫn hiện đủ ở đuôi theo canonical, rank/score null. UI hiển thị coverage, status, expiry và limited; không gắn cosine thành xác suất trúng tuyển, không tự tick top-K. Khi phản hồi đề cử bị mất, người dùng có thể kiểm tra lại đúng command/key cũ.

Template mới `school-advisory-text-v1` được Java/Python hỗ trợ cùng canonical-v1. Code-point/UTF-8/surrogate và JSON-byte batching được kiểm riêng; giới hạn text 8.192 code points/32KiB, request 262.144 bytes và <=16 passages. T2 còn giới hạn tổng serialized source capture 32MiB trước khi giữ trong bộ nhớ, đồng thời T4 kiểm tổng rendered texts; không cắt population. Decimal practice có thể hợp lệ với hồ sơ canonical nhưng quá lớn để render sẽ từ chối capture advisory có kiểm soát, không sửa dữ liệu nguồn.

Query và từng passage có independent input hash lưu nội bộ; binding gồm owner snapshot/source hash, run/dataset/criteria/catalog/model/template/policy. DB snapshot được dùng lại trong cùng advisory; v1 không bật cross-job score/embedding cache. TTL tối đa 24h, không gia hạn khi GET/replay/retry. Maintenance DB riêng chạy theo lô mỗi 10s, kể cả khi flag inference/view OFF, chọn purge ngay khi expiry và có hold fence; không tạo AI client hoặc inference thread. Mục tiêu purge trong vòng 1h vẫn cần đo throughput/giám sát backlog ở môi trường riêng. R30/copy inventory/audit retention vẫn áp dụng riêng. Không khởi động ứng dụng hay chạy purge trên shared DB trong công việc này.

## Chuẩn bị chạy trong môi trường riêng

1. Chọn VM/host và Docker context tách biệt daemon demo 8088; xác minh tài nguyên trước khi cài/chạy model. Backup/clone đúng quy trình trước mọi migration của môi trường đó. Không chạy `mvn verify` hoặc Compose trên workstation shared hiện tại.
2. Dùng source worktree này và database/files riêng. Áp V44 qua Flyway cùng locations đúng lineage sau khi kiểm chứng bản sao; không repair hoặc sửa history. Chạy real MySQL regression bao gồm `AIAdvisoryRepositoryIT` ở môi trường riêng, không skip test để báo đủ.
3. Admin/nhà trường cấp delegation đúng trường/kỳ với `matching:school:advisory_read`, và `matching:school:advisory_manage` cho người được tạo gợi ý; kèm matching detail/run execute/profile read phù hợp, field mask skills+projects. Đề cử vẫn cần matching nominate + t5 proposal manage hiện hành. Không dùng trực tiếp SQL seed quyền hoặc sửa dữ liệu demo chung để vượt điều kiện.
4. Chuẩn bị E5 local theo [setup lượt 1](AI_MATCHING_LOCAL_SETUP.md), pin revision/checksum, private token, CPU một model worker. Chạy smoke thật và blocked-network proof trước khi khẳng định offline hoạt động.
5. Recipe AI service: `ai-service/Dockerfile` và `ai-service/compose.isolated.yaml`. Build dependencies cần mạng trong môi trường riêng; runtime dùng model mount read-only và internal network. Cấp AI_MODEL_DIRECTORY/AI_INTERNAL_TOKEN riêng; không in `compose config` chứa token hoặc commit secrets/weights. Image build/digest/peak RAM chưa được kiểm chứng ở đây; 2 CPU/2GiB trong recipe là cấu hình khởi đầu để đo, không cam kết phần cứng.
6. Backend riêng đặt AI_MATCHING_BASE_URL và AI_INTERNAL_TOKEN tới service riêng, `AI_ADVISORY_ENABLED=true`, `AI_ADVISORY_WORKER_ENABLED=true`. Flag shadow AI_MATCHING_ENABLED độc lập, có thể tiếp tục false. Không bật các flag này trên demo chung từ hướng dẫn này.
7. Đăng nhập trường có quyền, chạy Matching mới, tạo gợi ý, xem trạng thái/danh sách và chủ động chọn đề cử. Kiểm revoke/stale/expiry/queue/timeout/lease takeover/hold/rollback theo handoff. Khi tắt flag hoặc dừng context, worker không publish mới và UI giữ đường canonical.

## Bằng chứng DEV và giới hạn

| Tầng | Bằng chứng hiện có |
|---|---|
| Java unit | 1.478 PASS, 1 Windows symlink conditional SKIP, 0 failure/error; gồm real SecurityConfig + MVC và worker/retention transaction orchestration với mocked owner/client |
| Python | 43 PASS, fake encoder; enriched template/echo/schema/auth/resource boundaries |
| Extension schema/bindings | 63/63 PASS; gồm 50 spec examples, production DTO/policy serialization và static permission/audit/security registration |
| Canonical contract | 878/878 PASS; generator chỉ refresh source provenance, frozen SRS/Contract giữ bytes |
| Frontend | TypeScript/tests typecheck + build; Chromium component scenarios với API giả lập, ảnh desktop/mobile trong checkpoint |
| MySQL | IT mới đã compile, chưa chạy; migration/transaction/concurrency/DB runtime chưa được báo PASS |
| Real model/quality/offline/R31 | NOT RUN; cần bộ nhãn chuyên môn, môi trường riêng và điều kiện đo đã chốt |
| Docker build/deploy | NOT RUN; chỉ chuẩn bị recipe |

Checkpoint `phase3/` có XML/log/ảnh, exact working-source manifest, bản ghi authorization và bảo toàn checkout gốc. Đây là DEV verification; không thay QA/Lead/nhà trường nghiệm thu. Source chưa commit/push/merge, source gốc và shared demo chưa đổi.

## Lệnh kiểm tra nhẹ

Trong worktree với dependencies task-local đã chuẩn bị:

```text
python scripts/contracts/verify_ai_advisory_runtime.py --output /private/evidence/advisory-contract.json
python scripts/contracts/verify_contracts.py --output /private/evidence/canonical-contract.json
```

Runtime checker dùng file DTO `target/advisory-wire-values.json` sinh bởi Java `AdvisorySchemaExportTest`, kiểm độc lập theo schema đóng của extension. `check_ai_advisory.py` lượt 2 vẫn được giữ nguyên bytes: source-map của nó là snapshot khảo sát lịch sử, không còn là source đang triển khai, nên dùng runtime checker mới khi đối chiếu lượt 3. Không sửa hash lịch sử để giả rằng code cũ và code mới là cùng snapshot.

Test giao diện dùng config `web-demo/playwright.advisory.config.ts`; harness chỉ là fixture synthetic ở dev tests, không được bundle vào production dist. Trên Windows có thể khởi động Vite test ở 127.0.0.1:15174 trong session riêng, đặt ADVISORY_EXTERNAL_PREVIEW=1 khi chạy Playwright, rồi đóng đúng server test bằng q; không dùng cổng/môi trường demo. Full application browser với backend/MySQL thật vẫn là bằng chứng riêng.

## Lượt 4 — bộ chạy chuẩn bị ngày 23/11/2026

Người dùng chưa có VPS và dự định thuê máy riêng Xeon 8171M, 8 vCPU, RAM 16 GB, NVMe 160 GB. Hiện **PREPARED / WAITING_FOR_ISOLATED_VPS_AND_REVIEW_DATA**; chưa SSH, tải weights, build image, chạy MySQL/ClamAV hoặc model thật. Không tuyên bố hoàn tất giai đoạn 7–8 từ việc chuẩn bị công cụ. Máy này không tự đáp ứng điều kiện R31 4 vCPU/8 GB SSD; số đo trên cấu hình khác phải ghi đúng phần cứng.

### Gói source và điều kiện máy chạy

`scripts/ai_validation/package_source.py` đóng gói cả thay đổi AI chưa commit; archive không chứa `.git`, Plan Excel, local env, weights, dependency caches hoặc báo cáo cũ. Sidecar ghi SHA-256 archive và source-content riêng theo bảng hash từng file; đây là định danh source trong gói, khác định danh manifest lượt 3 có cấu trúc khác. Không gắn HEAD c58a8ee thành commit đã chứa AI.

```text
python scripts/ai_validation/package_source.py --output /private/utbridora-ai-validation.tar.gz
```

Trên VPS mới, dùng Ubuntu/Linux riêng tương thích môi trường CI (CI hiện dùng Ubuntu 24.04), Python 3.12, Java 21, Maven, Node 24/npm, Docker Linux và Chromium/system libraries. Chuẩn bị công cụ ở bước cấp máy, không tự cài Docker hoặc sửa firewall qua runner. Không dùng daemon đang có workload khác. Tài khoản chạy phải có quyền Docker trên VPS này; không cấu hình Docker TCP công khai.

Tạo thư mục source/evidence/models/venv riêng thuộc tài khoản chạy, quyền private. Kiểm hash archive với sidecar **trước** giải nén vào thư mục trống. Đối chiếu machine ID và Docker ID trên chính VPS dự định dùng, ghi vào hồ sơ môi trường; truyền các giá trị đã kiểm tra vào runner, không tự lấy ID của một máy bất kỳ rồi coi đó là bằng chứng tách biệt.

```sh
cat /etc/machine-id
docker --host unix:///var/run/docker.sock info --format '{{.ID}}'
```

Runner yêu cầu Linux, đúng machine ID, tối thiểu 7 GiB RAM tổng/4 GiB RAM còn trống/12 GiB disk trống tại lúc bắt đầu. Đây là preflight để từ chối máy thiếu tài nguyên, không phải sizing hoặc hiệu năng đã chứng nhận. Nhánh regression còn kiểm đúng daemon ID, không có container hiện hữu, từ chối remote Docker/context override. Không chạy runner trên workstation demo và không chuyển Docker context của workstation.

### Chạy regression thật trên VPS

Từ source đã giải nén, tạo/activate Python 3.12 venv nằm ngoài source. Cài dependencies test bằng requirements đang pin trong gói. Chuẩn bị Chromium host libraries trước khi chạy; nếu browser preflight lỗi, sửa môi trường rồi chạy lại bằng source/evidence directory mới, không bỏ browser.

```sh
python -m pip install -r ai-service/requirements-test.txt -r scripts/contracts/requirements.txt -r scripts/ops/requirements.txt
python scripts/ai_validation/run_isolated.py --ack-isolated-host \
  --expected-machine-id "$VPS_MACHINE_ID" --expected-docker-id "$VPS_DOCKER_ID" \
  --stage regression --evidence /private/evidence/regression-01
```

Runner giữ unit/CI-tools/ops-tools/validation-tools, FE build/typecheck, toàn bộ inventory Failsafe (gồm `AIAdvisoryRepositoryIT`), MySQL/ClamAV và browser thật theo harness hiện hành, canonical/extension contracts, Python fake-encoder và advisory component/mock-HTTP. Testcontainers chỉ tạo database/container synthetic của từng IT; runner không kết nối DB demo hoặc chạy seed/repair. Maven/test forks được giới hạn heap; test chạy tuần tự như cấu hình hiện có. Hỏng test hoặc thiếu XML/inventory không được ghi PASS. Browser conditional SKIP được ghi riêng, không tính là PASS. XML/log/ảnh và hash được giữ cả khi test hỏng; không thay lịch hoặc required checks của GitHub CI.

Advisory component report dùng `ADVISORY_EVIDENCE_DIRECTORY`; config full-application loại `advisory.ui.spec.ts` để không trộn kiểm thử HTTP giả vào mỗi browser harness thật. Component vẫn có config/chạy riêng và không thay bằng chứng backend AI end-to-end.

### E5 thật, offline smoke và HTTP benchmark

Theo phần setup model ở `ai-service/README.md`, dùng venv model riêng ngoài source, cài CPU Torch trước các model requirements rồi `pip check`. Chuẩn bị weights ở thư mục mới ngoài source bằng `prepare_model.py`; tải cần mạng, runtime offline. Dependencies/model runtime chưa được chứng minh tương thích cho tới khi bước này thành công; không tự nới version để bỏ qua lỗi.

Đặt `AI_MODEL_PATH` đến model đã kiểm manifest. Tạo `AI_INTERNAL_TOKEN` riêng trong environment/private terminal; không đưa vào argv, Git, chat hoặc log. Hai ID môi trường lấy từ VPS đã xác minh ở trên. Chạy sau regression hoặc trên bản giải nén mới có cùng source hash:

```sh
python scripts/ai_validation/run_isolated.py --ack-isolated-host \
  --expected-machine-id "$VPS_MACHINE_ID" \
  --stage model --evidence /private/evidence/model-01
```

Runner bật offline env, kiểm dependencies, lưu runtime freeze, chạy real CPU model test không cho Python socket/DNS ra ngoài; sau đó mở **một server loopback do chính nó tạo** để đo `/internal/v1/similarity`. Dùng 100/400/5.000 passages synthetic, 3 trials mỗi cỡ, batch <=16, một model worker. Report có cold-ready, p50/p95 (p95 chỉ ước lượng mẫu nhỏ), batch p95, lỗi và peak RSS của server Python; server được dừng khi xong hoặc hỏng.

Report ghi **REAL_MODEL_HTTP_MICROBENCHMARK_SYNTHETIC**. Nó không đo Java/MySQL peak RSS, queue/job latency, API/page latency hay hành vi source/grant stale của toàn backend. Không lấy synthetic text ngắn làm bằng chứng 5.000 hồ sơ thật nằm trong 600s. Chứng minh chặn outbound ở cấp OS/container, actual model-backed advisory API/UI, revoke/expiry/hold/fallback và tải toàn hệ thống vẫn cần thực hiện riêng trên VPS; không suy ra từ smoke hoặc cấu hình Compose.

### Nhãn độc lập và phép đo chất lượng

`scripts/ai_validation/evaluate_quality.py` nhận **hai file private**, không sinh nhãn chuyên môn. Chốt protocol trước đo theo [evaluation plan](../contracts/extensions/ai-advisory-v1/EVALUATION_PLAN.md), có ít nhất 10 nhu cầu tuning/30 held-out độc lập và hai người chấm. Các ngưỡng còn là đề xuất review trước đo; công cụ không tự phê duyệt nghiệp vụ.

- Dataset `schemaVersion=ai-advisory-labels-v1`: `dataKind=HUMAN_REVIEWED` (fixture test chỉ `SYNTHETIC`), hai `reviewerIds` dạng mã, `protocolReviewRef`, `frozenAtUtc`, `demands`.
- Mỗi demand: UUID `demandId`, `split=tuning|heldout`, `duplicateGroup`, ít nhất 10 `candidates`. Mỗi candidate: UUID `studentUserId`, `overallStatus=MATCH`, `fitScore`, `renderedSourceSha256`, `duplicateGroup`, hai `reviewerLabels` 0–3, `relevance` đã thống nhất; bất đồng cần `adjudicationRef`. Group không được giao giữa tuning và held-out; số demand group độc lập cũng phải đủ mẫu.
- Scores `schemaVersion=ai-advisory-scores-v1`: `datasetSha256` khớp raw dataset đã đóng băng, `sourceContentSha256`, model revision trong lock, `templateVersion=school-advisory-text-v1`, `policyVersion=semantic-desc-canonical-tie-v1`, `measuredAtUtc` sau freeze, `demands`. Mỗi demand có `demandId`, `items` gồm `studentUserId`, `scoreStatus=SCORED|NO_CONTENT|INPUT_TOO_LARGE`, `semanticScore` (null đúng trường hợp unscored). Lấy kết quả từ model-backed advisory đã kiểm nguồn/quyền; không tự đặt score để đủ gate.

```sh
python scripts/ai_validation/evaluate_quality.py \
  --dataset /private/labels-frozen.json --scores /private/measured-scores.json \
  --output /private/evidence/quality-01.json
```

Công cụ từ chối thiếu/thừa/trùng population, NOT_MATCH, số không hữu hạn, sai phiên bản, giao split và thiếu giải quyết bất đồng. Nó tái tạo đúng HALF_UP sáu chữ số/tie-break canonical/UUID, giữ unscored ở đuôi, tính NDCG/P@10 và paired bootstrap 10.000 lần seed 20261121. IDCG=0 được công bố và không đủ mẫu không được đạt gate. Thiếu nhãn hoặc dùng synthetic thì không được báo chất lượng đạt. `qualityAcceptance` luôn false; `eligibleForQualityReview` chỉ chỉ ra kết quả số học đủ đưa ra review, không bật feature hoặc thay QA/school sign-off.

Sau các cổng kỹ thuật và chất lượng mới xem xét pilot với cán bộ theo protocol/cỡ mẫu đã chốt. Giữ feature OFF trên demo hiện tại; rollback pilot gồm tắt cờ trên môi trường pilot và xác minh fallback canonical, không tự hạ migration hay xóa dữ liệu. Chưa triển khai pilot, đo thời gian shortlist hoặc ghi nhận nghiệm thu trong phần chuẩn bị này.
