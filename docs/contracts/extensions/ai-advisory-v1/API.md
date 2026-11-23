# REST API advisory v1

Các route dưới đây là contract mới để triển khai ở lượt 3, chưa được đăng ký trong controller hiện hành. DTO chính xác nằm trong [schemas.json](schemas.json); schema đóng, unknown fields/type coercion bị từ chối. Không sửa `/matching/evaluate`, `/runs/{id}/details`, protected summary/export hoặc nominate.

## Phiên và phạm vi

Dùng session cookie SESSION hiện hành. Mutation dùng CSRF lấy từ `/api/v1/auth/csrf`, tên header/token đúng response server; không hardcode token hoặc dùng AI_INTERNAL_TOKEN trong trình duyệt. Idempotency-Key là UUID. `schoolOrgId,termId,demandId` suy ra từ run trên server, không nhận ActorContext/role/permission hay raw text từ client.

Mọi route: xác thực user ACTIVE/securityVersion/session và role SCHOOL_STAFF/org VERIFIED; yêu cầu delegation đúng trường/kỳ, capabilities trong permissions.csv và quyền đọc skills/projects hiện hành. Tài nguyên khác scope, enterprise/student/admin không có school delegation hợp lệ: 404 RESOURCE_NOT_FOUND. ADMIN role riêng không phải bypass; người có nhiều vai trò chỉ được dùng đường SCHOOL_STAFF hợp lệ.

Sau authentication/schema và scope, kiểm feature flag, rồi current owner sources. Không cache response HTTP: `Cache-Control: no-store`; không trả raw texts, input hashes, embedding hoặc internal token. Error body dùng envelope C.0, `error=errorCode`, X-Request-ID trùng requestId; fieldErrors không echo nội dung nguồn. Xử lý security filter theo envelope này trên route mới.

## Tạo advisory

**POST `/api/v1/matching/runs/{runId}/advisories`**

- Header: Idempotency-Key UUID, CSRF; JSON CreateAdvisoryRequest gồm expectedDemandVersion, expectedCriteriaVersion, expectedProfileSnapshotVersion (hash phiên bản dataset từ canonical run).
- Điều kiện: school-origin run COMPLETED, cùng current criteria/demand/dataset/catalog; không dùng enterprise run hay privacy-release dataset dù cùng trường. Run storage hiện chưa có audience discriminator đủ mạnh: lượt 3 phải thêm provenance do server quản lý, nhận diện/loại run gắn privacy release; legacy run chưa chứng minh school-origin bị từ chối. Không suy origin chỉ từ actor đang gọi.
- Trình tự transaction: current authority → OperationLedger `AI_ADVISORY_CREATE` → lock/admission/run → T5 capture → T2 capture theo UUID → persist job/source refs + audit → commit. Chỉ sau commit worker đọc job. Không HTTP/model inference trong transaction.
- **202 Accepted**, AdvisoryAccepted `{advisoryId,matchRunId,acceptedAt}`; Location `/api/v1/matching/advisories/{advisoryId}`. ACK không có job state động để replay không giả rằng job vẫn QUEUED.
- Cùng actor/org/operation/key/payload replay lại ACK và không capture/infer lần hai; auth/scope/quyền/feature vẫn kiểm trước ledger. Replay ACK không trả score, không làm mới expiry và không khẳng định source còn hiện hành; GET sẽ kiểm hiện hành. Changed payload 409 IDEMPOTENCY_KEY_REUSED; ledger response hết hạn 409 IDEMPOTENCY_RESPONSE_EXPIRED. Scope hash payload gồm runId và body, không chỉ body.
- Key mới trong khi cùng run đang QUEUED/RUNNING: 409 AI_ADVISORY_JOB_ACTIVE. Job terminal có thể tạo lại với key mới, vẫn current checks và giới hạn 10 job/run/24h. Không auto retry POST với key mới khi mất ACK.

## Đọc metadata

**GET `/api/v1/matching/advisories/{advisoryId}`**

**200**, AdvisoryMetadata; state QUEUED/RUNNING/READY/NO_DATA/FAILED/STALE/REVOKED/EXPIRED. `counts` chỉ khác null khi READY/NO_DATA; failureCode là enum an toàn hoặc null; completedAt có thể còn timestamp đã hoàn tất trước khi invalidated. Model/version lấy từ job, không lấy cấu hình hiện tại để đổi nhãn job cũ.

Reader có quyền hiện tại có thể thấy metadata REVOKED khi initiator mất quyền; người vừa mất quyền bản thân nhận 404, không được biết trạng thái. STALE/EXPIRED vẫn chỉ là metadata cho người có quyền, không nội dung nguồn hay rows. Khi owner port không sẵn sàng: 503 DEPENDENCY_UNAVAILABLE, không trả cached metadata READY.

## Đọc danh sách

**GET `/api/v1/matching/advisories/{advisoryId}/items?page=0&size=25`**

**200**, AdvisoryPage, chỉ state READY hoặc NO_DATA. Items gồm strict refs/fitScore và score/rank/status riêng; giới hạn paging và global order theo policy. Không có filter/sort query khác; tham số lặp/không biết bị 400. Không bổ sung studentCode/name hoặc nội dung nguồn trong contract v1. UI có thể lấy danh tính qua projection được phép hiện hành.

Mỗi lần đọc tái kiểm quyền reader, exact initiator stamps, current owner set và expiry trước khi trả rows. Kiểm cả set; nếu cần write trạng thái invalidation thì commit transaction riêng/outer result có kiểm soát, tránh throw làm rollback mất dấu STALE/REVOKED. Không đọc item theo ordinal rồi lọc quyền từng dòng để vẫn trả partial page.

## Lỗi và thứ tự áp dụng

| HTTP | errorCode | Ý nghĩa |
|---|---|---|
| 400 | SCHEMA_VALIDATION / INVALID_PAGINATION | UUID/body/header/query/limits sai; không ép string thành number |
| 401 | SESSION_EXPIRED | Không có phiên hiện hành |
| 403 | CSRF_INVALID | Mutation sai CSRF; auth security layer xử lý |
| 404 | RESOURCE_NOT_FOUND | Không có tài nguyên hoặc scope/quyền đọc không hợp lệ; items job REVOKED |
| 409 | AI_ADVISORY_SOURCE_STALE | Expected versions/catalog/full profile/source đã đổi; run mới cần thiết |
| 409 | AI_ADVISORY_RUN_NOT_ELIGIBLE | Trong scope nhưng run không phải school-origin COMPLETED |
| 409 | AI_ADVISORY_LIMIT_EXCEEDED / AI_ADVISORY_INPUT_TOO_LARGE | Capture quá số/bytes hoặc query quá lớn; không tạo job |
| 409 | AI_ADVISORY_JOB_ACTIVE | Đã có active job cùng run |
| 409 | AI_ADVISORY_NOT_READY | Items job QUEUED/RUNNING/FAILED; không trả partial score |
| 409 | IDEMPOTENCY_KEY_REUSED / IDEMPOTENCY_RESPONSE_EXPIRED / REQUEST_IN_PROGRESS | Theo OperationLedger hiện hành; REQUEST_IN_PROGRESS retryAfterSeconds=1 |
| 410 | AI_ADVISORY_EXPIRED | Items đã hết TTL; GET metadata vẫn 200 nếu có quyền |
| 429 | AI_ADVISORY_CAPACITY_EXCEEDED | Admission queue/quota ngày đầy; Retry-After và retryAfterSeconds=30 |
| 503 | AI_ADVISORY_DISABLED / DEPENDENCY_UNAVAILABLE / TRANSIENT_FAILURE | Feature OFF hoặc dependency/lock tạm không sẵn sàng; canonical vẫn dùng được |

Với reader có quyền, ưu tiên invalidation REVOKED → EXPIRED → STALE → trạng thái xử lý; không trả stale rows trong lúc invalidation chưa lưu DB. Scope thất bại luôn trả 404 trước thông tin state/feature. T2/T5 current check thất bại kỹ thuật trả 503 thay vì giả kết quả stale hoặc hợp lệ. Không có endpoint mới cho enterprise, export, tự nominate, thay model hoặc retry worker từ browser.
