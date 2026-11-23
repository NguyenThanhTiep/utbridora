# Job, lưu trữ và thời hạn advisory v1

## Dữ liệu lưu trữ đề xuất

Lượt 2 không tạo migration. Lượt 3 chọn số migration forward kế tiếp sau khi đối chiếu main và các lineage, không giữ chỗ bằng số giả định hoặc sửa SQL cũ.

| Owner/table mới đề xuất | Trường và ràng buộc chính |
|---|---|
| T4 matching_advisory_jobs | UUID PK; run/school/term/demand refs; initiator/securityVersion/exact grant stamps; T2/T5 semantic refs; model/template/policy/catalog bindings; state, counts nullable, UTC times/expiry, safe failure; attempt/lease owner/token/until/heartbeat; row version |
| T4 matching_advisory_rows | PK(job_id,student_user_id); strict refs/fitScore; scoreStatus, nullable scoreKey/rank/limited; position UNIQUE(job_id,position); semantic snapshot ref; không raw text/embedding |
| T4 matching_advisory_admission | Một guard scope global/school/run để serialize bounded admission; exact active run key unique và counter được kiểm trong transaction, không COUNT rồi INSERT không khóa |
| T2 student_semantic_sets/snapshots | Owner snapshot bất biến, field set/purpose/hash/version/current-check metadata, filtered rendered sources; FK về semantic set; không sửa canonical snapshot |
| T5 demand_semantic_snapshots | Owner snapshot title/description/hash và đúng demand/criteria revision; không sửa current demand hoặc frozen criteria |

Đánh index school/term/run + createdAt, state + nextAttemptAt, leaseUntil, expiresAt và owner source refs. T4 không SQL join bảng nguồn T2/T5; chỉ gọi port. Run → job → row là lineage một chiều; không thêm current pointer FK vòng từ canonical run về job. Durable request ACK trong OperationLedger chỉ chứa refs, không raw nội dung. V1 không lưu embeddings; cache score tùy chọn ở T4 là cache riêng cùng job, không chia sẻ cross-school/actor/source.

Tối đa 1 active job/run, 2 active/school, 32 active toàn hệ thống và 10 lần tạo/run trong rolling 24h. Active gồm QUEUED/RUNNING kể cả đang chờ retry. Các giá trị là giới hạn thiết kế để review, không bằng chứng chịu tải. Capture batch 100, inference batch <=16, chỉ một worker model; memory không gom toàn bộ vector/32MiB sources thành list lớn nếu có thể đọc tuần tự qua owner port.

## Trạng thái và xử lý bền vững

```text
POST transaction → QUEUED
QUEUED → RUNNING → READY | NO_DATA | FAILED
RUNNING --transient + còn attempt/TTL--> QUEUED
QUEUED/RUNNING/READY/NO_DATA/FAILED → REVOKED | STALE | EXPIRED
```

Worker poll DB sau commit; after-commit notification chỉ đánh thức, không phải nguồn duy nhất. Crash giữa commit và callback vẫn có job để poll. Claim trong transaction bằng lease/fencing token tăng đơn điệu, attempt tối đa 3; lease 90s, heartbeat mỗi 15s, budget mỗi attempt 600s, HTTP tối đa 12s/call; tối đa 1.800s nếu sau này cấu hình thay đổi, vẫn không vượt expiresAt. Timeout không chứng minh CPU Python đã dừng: giữ slot busy theo service hiện có, không tạo worker thứ hai.

Chỉ retry lỗi kết nối/timeout/busy/transient, delays 5s rồi 30s; metadata/schema/model revision/score/key sai là FAILED ngay. Claim job hết lease dùng token mới và bỏ staging token cũ. Worker cũ không heartbeat/publish được nếu token không còn current. Retry cùng captured sources, không recapture live. Mọi partial rows nằm trong staging gắn attempt token; publish transaction kiểm exact token/state/expiry/authority/current owner sources và đầy đủ tập, insert/promote rows + counts + state + audit cùng commit. Không trạng thái READY trước khi đủ tất cả row.

Authentication sống không đi cùng job; T1 dựng current subject, kiểm securityVersion và exact delegation stamps mỗi lần đọc nguồn và trước publish. Không tìm grant khác để âm thầm tiếp tục job có initiator grant bị thu hồi. Đổi quyền đọc field cũng invalidate toàn job; current mask của reader khác phải đủ field set snapshot. Đừng kéo owner lock trong suốt HTTP inference; chụp nguồn trong transaction riêng, revalidate khi publish.

`AI_ADVISORY_ENABLED=false` và `AI_ADVISORY_WORKER_ENABLED=false` là flag mới, độc lập AI_MATCHING_ENABLED shadow hiện có. Advisory flag OFF chặn create/read sau scope check và dừng claim/publish; UI xóa cache khi nhận disabled. In-flight không được publish READY sau lúc OFF; ngừng heartbeat, để lease recover khi bật lại và qua current/TTL checks. Bật worker riêng không vượt advisory flag. Không tự bật lại job đã EXPIRED/STALE/REVOKED; tạo mới bằng lệnh có quyền.

## Hết hiệu lực và xóa

expiresAt = min(createdAt + 24h, thời hạn authority/source policy capture). Thời gian UTC thực, không gia hạn khi GET/replay/retry/cache hit. Cache tối đa 300s và không vượt expiresAt; vẫn current checks toàn set mỗi read. Full profileVersion/demand version/catalog/policy/model allowlist bị đổi hoặc source removed: STALE; initiator stamp/security/field mask không còn hợp lệ: REVOKED. Lỗi dependency không được coi là current. Event invalidation giúp dọn nhanh, không thay read-time gate.

Nguồn semantic và derived rows hết hạn bị purge trong vòng 1h sau expiry nếu không có hold được phê duyệt. Job receipt tối thiểu chứa ID/scope/state/times/model/policy/counts an toàn được quản theo policy dữ liệu dự án, không giữ raw sources trong tombstone. Audit theo retention hiện hành (90 ngày từ creation trong recipe R30); dataset synthetic/các bản sao tuân R30 30 ngày từ nghiệm thu thực tế khi áp dụng. TTL advisory là giới hạn ngắn hơn cho dữ liệu tính toán, không kéo dài quyền đọc hoặc thời gian giữ toàn dataset.

Hold có approvalRef/reason/time và chặn purge có liên quan; không kéo dài quyền đọc, job vẫn EXPIRED. Bản sao DB/files/backup/export/cache/QA artifacts phải được inventory để xóa hoặc lưu bằng chứng hủy theo phạm vi. Không tuyên bố retention toàn hệ thống đạt khi còn bản sao ngoài quản lý. Purge phải theo FK owner đúng thứ tự, không tắt FK/CHECK hoặc sửa frozen SQL; giữ audit in-window/hold và receipt riêng. Không chạy purge trên shared DB trong lượt 2–3.

## Khôi phục và audit

Khôi phục DB phải kèm snapshot owner tương ứng và model manifest; chưa kiểm refs/current/TTL thì không phục vụ advisory. Backup/restore vào môi trường riêng; poller không auto publish job hết TTL từ bản backup. Test crash sau ACK, giữa batches, trước publish, lease takeover, stale worker/clock/hold và lỗi purge nằm trong bàn giao lượt 3–4. Audit codes/metadata allowlist ở [audit.csv](audit.csv); không ghi văn bản, score, hash nội dung hoặc danh sách sinh viên trong audit.
