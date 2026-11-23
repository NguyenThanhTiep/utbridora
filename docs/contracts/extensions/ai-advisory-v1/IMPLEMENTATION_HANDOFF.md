# Bàn giao triển khai lượt 3 và ma trận kiểm chứng

Giai đoạn 5 bàn giao đặc tả ở trạng thái SPEC_READY_FOR_REVIEW. Các mã/schema/giới hạn mới là thiết kế của extension, chưa đăng ký runtime hoặc thay frozen contract. Bảng dưới là kế hoạch triển khai, không phải phân công đã được các team ký nhận.

## Thứ tự triển khai

| Bước | Owner và thay đổi cụ thể | Điều kiện/đầu ra |
|---|---|---|
| 1 | T1: đăng ký capability, purpose SCHOOL_AI_ADVISORY, current authority/field masks và exact stamps; thêm error routing nếu cần | Quyền score không vượt quyền nội dung; test enterprise/admin/foreign/revoked/securityVersion |
| 2 | T2/T5: owner semantic ports, filtered immutable source snapshots, full-version/current checks | Cùng run refs; không live page trộn version; hash/template unit fixtures |
| 3 | T4: forward migrations cho lineage, school-origin provenance, durable jobs/rows/admission | Không sửa migration cũ hoặc canonical DTO; enterprise-origin/legacy unknown bị chặn |
| 4 | T4: create/GET metadata/GET items, OperationLedger và audit allowlist | Theo schemas/API; authority trước replay; không public raw sources/embedding |
| 5 | T4/Python: bổ sung template literal enriched, bounded worker/fencing/retry/atomic publish | Giữ shadow canonical-v1 hoạt động; flags độc lập OFF; cả set trước paging |
| 6 | FE: view canonical/gợi ý, status/coverage, chọn và gọi luồng nominate hiện hành | Không tự chọn top-K; clear stale/session; không mở profile field vượt mask |
| 7 | DevOps: recipe CPU/offline, pin/checksum, private config/secret và limits, isolated DB/files | Chuẩn bị file cấu hình không đồng nghĩa chạy Docker shared |
| 8 | DEV: kiểm unit/schema/contract/build và integration/browser tương ứng trên môi trường riêng | Bằng chứng đúng source manifest/commit; gap thật ghi NOT RUN |

T1 hiện có `SchoolDelegationAccess.capture/requireStamp` và `profileFields`, nhưng capture một permission không tự chụp đủ các grant cung cấp field mask. Lượt 3 phải capture/validate tập exact grant IDs/versions thực sự cho phép skills+projects và từng capability cần dùng. Không lấy grant khác sau revocation để tiếp tục job cũ. Không truyền SYSTEM/auth object vào port để bỏ kiểm current.

Provenance school-origin là yêu cầu mới quan trọng: StoredRun hiện không có audience discriminator. Thêm marker sidecar do server ghi cho school run; không tự sửa JSON canonical của MatchRunRef. Legacy runs chỉ được backfill bằng quy tắc chứng minh nguồn có kiểm tra, hoặc yêu cầu evaluate mới; tuyệt đối không mặc định mọi run không biết nguồn đều là school. Privacy release run bị chặn trước capture.

## Ma trận test cần có khi triển khai

| ID | Kịch bản và kết quả bắt buộc | Tầng bằng chứng |
|---|---|---|
| ADV-01 | Canonical ON/OFF giống DTO/order/count/paging; enterprise release fingerprint/bands/replay giữ nguyên | Unit + MySQL contract/runtime |
| ADV-02 | Chỉ MATCH; toàn tập, không prefilter; NO_CONTENT không bị mất; READY/NO_DATA đúng coverage | Unit + MySQL |
| ADV-03 | Score khác → đảo thứ tự canonical; hòa/âm/ROUND_HALF_UP sáu số; stable UUID; không tự đổi fitScore | Unit policy + synthetic fixtures |
| ADV-04 | Nhiều trang giữ global order/position, không trùng/bỏ; request page cuối và quá cuối | MySQL + browser |
| ADV-05 | Chỉ sửa project/note không đổi relevantHash nhưng full profileVersion đổi → stale, không lấy live text ghép ref cũ | Owner-port integration |
| ADV-06 | JD/demand/criteria/catalog/policy đổi hoặc deleted → stale; dependency down không dùng cache cũ | Owner-port integration |
| ADV-07 | Enterprise run/privacy-release dataset/legacy unknown không tạo advisory dù actor có quyền trường | MySQL + authorization |
| ADV-08 | Đủ role nhưng thiếu delegation/fields; parent revoke, field shrink, securityVersion/session đổi, foreign school | T1/T2 integration + browser |
| ADV-09 | Worker exact stamps bị revoke trước claim/giữa batch/trước publish; không grant fallback | Concurrent MySQL |
| ADV-10 | Create rollback/replay/lost ACK/same key changed body/expired response; không double capture/infer | Ledger integration |
| ADV-11 | Sau commit chết trước notify, lease takeover/heartbeat/stale worker/token fencing, bounded admission race | Concurrent MySQL |
| ADV-12 | Một batch lỗi/metadata sai/missing ordinal/busy/timeout, hết attempt: không READY một phần | Protocol + MySQL |
| ADV-13 | Unicode/NFC/tiếng Việt/ký hiệu technical/long text/4 chunks/8192 codepoints/UTF-8 bytes; flags limited đúng | Python + Java + real tokenizer |
| ADV-14 | Lỗi/null không thành 0; query rỗng/quá lớn/profile thiếu/quá lớn/empty MATCH; limit32MiB/5000 | Unit + MySQL |
| ADV-15 | ExpiresAt không gia hạn replay/cache/retry; current toàn set, purge/hold/copy inventory/restore | MySQL + operational môi trường riêng |
| ADV-16 | Advisory flags OFF: không create/read/publish, UI về canonical; shadow flag độc lập | Unit + browser |
| ADV-17 | UI cán bộ chọn, lấy context T5 hiện hành, nominate vẫn quota/eligibility/stale guards; consent/share không đổi | Real MySQL/browser full chain |
| ADV-18 | Schema đóng, lỗi C.0/security/CSRF, no-store, không PII/raw content/score/token vào audit/log; không public internal auth | Schema + HTTP/security/runtime |
| ADV-19 | E5 thật/offline, source/model manifests, quality baseline/cỡ mẫu/CI/coverage theo plan | Môi trường riêng, lượt 4 |
| ADV-20 | Full regression unit/IT/browser/contract/build, MySQL/ClamAV thật khi test tương ứng; CPU/RAM/cold start/job load | Môi trường riêng, lượt 4 |

`check_ai_advisory.py` hiện chỉ lint schema, ví dụ synthetic, một số invariant count/order/paging/timestamp, CSV metadata, link và hash. Không coi các case trong file examples là ADV-01..20 đã chạy runtime. Exact full suite/inventory bắt buộc tiếp tục giữ theo dự án; không bỏ test hoặc dùng mock thay integration thật.

## Cách kiểm đặc tả độc lập

Trong repo, dùng môi trường Python đã có dependencies từ `scripts/contracts/requirements.txt`:

```text
python scripts/contracts/check_ai_advisory.py --output /private/evidence/advisory-spec.json
```

Đường dẫn output chỉ là ví dụ; chọn thư mục evidence cục bộ được phép. Checker không gọi mạng/model/DB/Docker. Bộ canonical checker vẫn chạy riêng bằng `scripts/contracts/verify_contracts.py`; không thêm extension chưa review vào generator/oracle canonical để giả bộ đã được duyệt.

## Điều kiện bàn giao giữa các lượt

Lượt 2 hoàn thành khi có tài liệu/schema/examples/permission/audit/owner-port/policy/storage/evaluation cụ thể, lint đạt và chứng minh runtime/frozen snapshots không thay đổi. Thông tin còn thiếu để đo chất lượng gồm bộ nhãn nghiệp vụ, reviewers và môi trường riêng; không cản việc viết đặc tả nhưng cản kết luận QUALITY PASS.

Lượt 3 triển khai dựa trên extension đã được quyết định trong phạm vi yêu cầu tiếp theo; giữ records quyết định tách bytes baseline. Lượt 4 mới làm real-model/regression/quality/pilot theo kết quả và môi trường được giao. Lượt 2 không commit/push/merge/deploy, không chạy Docker/DB/shared demo và không đổi Plan nghiệm thu.
