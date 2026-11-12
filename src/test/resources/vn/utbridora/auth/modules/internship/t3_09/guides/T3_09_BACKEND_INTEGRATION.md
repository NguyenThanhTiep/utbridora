# T3-09 — tích hợp backend ngày 12/11/2026

Baseline hiện tại từ `origin/feature/t3-week3`: `4d187ab` (GET Review), kế thừa DEV05 `000c8e9`,
DEV06 `59ef6d2` và các commit/merge tổ tiên. Main `c3d0dbc` được ghép lại vì là
thay đổi đã có trước đó. Không lấy phần main mới hơn `9ba090d` vào task này.

## DEV05 / T3-07

- `POST/GET /api/v1/t3/assignments/{id}/submissions`: URL v1/v2, Page history.
- `POST /api/v1/t3/submissions/{id}/reviews`: ACCEPT/REQUEST_REVISION, trả ReviewDto.
- Student start vẫn dùng `PATCH /api/v1/t3/assignments/{id}/status` với IN_PROGRESS.
- T1-06/V28, V29, current Placement/Case guard, immutable version, CAS, idempotency
  và audit đã có trong source. UI không tự chuyển SUBMITTED/COMPLETED.
- **Đã có GET `/api/v1/t3/submissions/{id}/reviews`** tại `4d187ab`: 200 ReviewDto
  hoặc 204 chưa review, không phải array/Page. UI đã đọc dữ liệu lưu và giữ đúng
  submissionId/reviewedVersionNo, bỏ phản hồi cũ, đóng scope khi 401/404.
  Không dùng assignmentVersion lịch sử để ghi mới. Xem [handoff GET](T3_09_REVIEW_GET_HANDOFF.md).
- Đây là endpoint additive do DEV05 giao; không tự phê duyệt/sửa frozen v1.0
  hoặc Candidate v1.1. Không thêm permission, migration hoặc backend alias.

## DEV06 / T3-08

- `GET/POST /api/v1/t3/assignments/{id}/comments`: Page comment append-only.
- `POST /api/v1/t3/reports`, `PATCH /reports/{id}`, POST `/{id}/submit`,
  `/{id}/reviews`, `/{id}/revisions`: ReportDto theo lifecycle hiện có.
- `GET /reports?caseId=...`, `GET /reports/{id}?revisionNo=N`: list/current/history.
- **Mới:** `GET /reports/{id}/reviews?revisionNo=N`, trả
  `reportId, revisionNo, reviewerUserId, comment, reviewedAt`; query bỏ trống là
  current; revision chưa review trả404. UI chỉ gọi khi bản đọc có state REVIEWED,
  kiểm reportId/revisionNo và bỏ response cũ sau đổi bản/phạm vi.
- API gate `app.t3.week3.http.enabled` mặc định true, có thể tắt bằng
  `T3_WEEK3_HTTP_ENABLED=false`. Không tạo fallback dữ liệu giả khi gate tắt.
- V31 tạo report root/revision/review/file refs và quyền Comment. Không sửa
  migration hiện hữu hoặc chiếm số V30 của T5.

## Minh chứng báo cáo qua T1

Tạo root rỗng evidenceFileIds trước, sau đó dùng multipart POST `/api/v1/files`
với đúng file/resourceType=WEEKLY_REPORT/resourceId=reportId/purpose=WEEKLY_REPORT.
Browser tự đặt boundary; transport vẫn dùng cookie, CSRF và Idempotency-Key.
Retry giữ nguyên File, binding và key, không cho chọn file khác khi kết quả chưa rõ.

Upload thành công đưa fileId vào nội dung chưa lưu; người dùng **Lưu bản nháp** để
PATCH refs. Gỡ minh chứng chỉ sửa refs bản nháp, không xóa binary/version cũ.
Tối đa 20 tệp, mỗi tệp PDF/PNG/JPEG 10 MiB. GET metadata xác nhận binding và trạng
thái; GET content qua transport có selected context, tạo Blob download, không lộ
storage path. Chỉ CLEAN có nút tải; quyền được server kiểm lại mỗi request.
PENDING lưu draft được nhưng server từ chối submit. Không có scanner giả trong UI.

## Git lượt tích hợp trước (59ef6d2)

```powershell
git fetch origin
git stash push -u -m "wip: DEV07 T3-09 and T3-03 relocation before team backend update 2026-11-12"
git pull --ff-only origin feature/t3-week3
git merge --no-commit c3d0dbc
git stash apply 'stash@{0}'
```

- `fetch`: lấy commit/ref mới, chưa thay source đang mở.
- `stash -u`: cất cả file tracked và file mới; resolved merge cũ được lưu cùng
  nội dung, sau stash working tree sạch. Backup ZIP/patch giữ thêm trạng thái trước đó.
- `pull --ff-only`: cập nhật nhánh tới remote khi không cần commit merge mới.
- `merge --no-commit`: ghép lại đúng bản main đã có, dừng trước commit.
- `stash apply`: khôi phục DEV07 nhưng giữ stash dự phòng. Không dùng pop/drop.
- Conflict duy nhất ở docs/README.md đã được ghép thủ công. Sau đó
  `git restore --staged -- <các đường dẫn DEV07/T3-03>` chỉ trả index các file đó
  về HEAD, giữ nội dung working tree; phần main vẫn staged. Không reset hoặc xóa code.

Git đang chờ người dùng commit merge. Chưa push. Sau khi xem diff, nên commit phần
merge main riêng, rồi phần dời tài liệu T3-03, rồi T3-09. Trước push nhánh chung
phải fetch và kiểm tra có commit đồng đội mới; không force-push.
