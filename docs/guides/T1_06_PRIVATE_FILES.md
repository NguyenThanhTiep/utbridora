# T1-06 — File Service tối thiểu, tuần 3

Ngày 10/11/2026. Nhánh bàn giao `feature/t1-06-private-files`, base `f1ef2176041c5b0892d5bb107f01d601616755c2` (main đã hoàn thành tuần 2). Code, tests và tài liệu này được đóng gói cùng bản DEV T1-06; chưa triển khai lại Docker dùng hằng ngày. Xem [kết quả kiểm chứng](../testing/T1_06_ACCEPTANCE.md). Evidence giữ trạng thái tại snapshot kiểm chứng trước commit; trạng thái nhánh/commit hiện hành xem trong Git.

## Phạm vi và căn cứ

Plan, sheet công việc tuần 3, T1-06 (DEV01/QA01): private upload, metadata và owner/download guard; **không đồng nghĩa hoàn tất an toàn file**. Đối chiếu [CP-FILE1, ma trận D và audit E](../contracts/UTBridora_Contract_Pack_B01_B06_v1.0_Candidate.md), SRS R15/G15, R16/G16 và §25.8.3. Bộ quét, retry và mở đầy đủ loại file là T1-07 tuần 4; contract v1.0 giữ nguyên.

Đã có đường API và storage riêng, binding bất biến, kiểm quyền hiện hành mỗi request, ledger/audit dùng chung. Binary nằm trong private filesystem; MySQL chỉ giữ metadata và binding. File mới luôn `PENDING`; không có endpoint hay tác vụ tự gán `CLEAN`.

## API hiện có

| API | Request / response | Guard |
|---|---|---|
| `POST /api/v1/files` | Multipart đúng bốn trường `file`, `resourceType`, `resourceId`, `purpose`; `202 FileDto` | Session + CSRF + UUID `Idempotency-Key`; `file:upload` và resource WRITE |
| `GET /api/v1/files/{fileId}/metadata` | `200 FileDto`, owner thấy cả `PENDING`/`REJECTED` | `file:download` và resource READ; ngoài scope `404` |
| `GET /api/v1/files/{fileId}/content` | `200` stream hoặc `206` cho một byte Range | Kiểm quyền trước scan/Range/storage; chỉ `CLEAN` được trả bytes |

`FileDto` gồm `fileId`, `resource:{resourceType,resourceId}`, `purpose`, `originalName`, `contentType`, `sizeBytes`, `sha256`, `scanStatus`, `createdAt`, `deletedAt`. Không trả storage key/path/URL trực tiếp. File của enterprise dùng tên tải trung tính `fileId.pdf/png/jpg`. Các response từ controller có `Cache-Control: private, no-store` và `X-Content-Type-Options: nosniff`; bytes được tải dạng attachment.

Tải metadata không cần CSRF; upload cần token từ `/api/v1/auth/csrf` và cookie session. Dùng lại cùng Idempotency-Key chỉ khi retry cùng binary, binding, purpose, tên và MIME; payload khác trả `409`. Retry vẫn kiểm quyền hiện hành trước khi đọc ledger. Một upload thành công chỉ tạo một file, binding và `FILE_UPLOADED` dù gửi đồng thời nhiều lần.

| Resource/purpose đã nối | Điều kiện hiện tại | Định dạng mở ở T1-06 |
|---|---|---|
| `STUDENT_PROFILE` / `CV`, `EVIDENCE` | `resourceId=studentUserId`; chính sinh viên, profile tồn tại, roster/term liên kết đúng và membership hiện hành. Upload cần kỳ ACTIVE; đọc lịch sử không ép kỳ ACTIVE | PDF, PNG, JPEG; tối đa 10 MiB |
| `TASK_ASSIGNMENT` / `SUBMISSION` | `resourceId=assignmentId`; chính sinh viên được giao. Upload cần IN_PROGRESS và gate Placement/Case hiện hành của T3; đọc lại file của mình dùng quyền đọc lịch sử | PDF; tối đa 20 MiB |
| `ENTERPRISE_VERIFICATION` / `VERIFICATION` | `resourceId=verificationId`; enterprise manager đúng tổ chức. Upload chỉ DRAFT/NEEDS_CHANGES; đọc còn yêu cầu chính người đã upload | PDF, PNG, JPEG; tối đa 10 MiB |

Quyền `file:*` chỉ là trần permission. ADMIN, người cùng enterprise, school reviewer và Mentor **không được tự động đọc file của người khác**. `WEEKLY_REPORT`, `EVALUATION`, `IMPORT_JOB`, `COHORT` chưa có policy provider nên từ chối. Chưa có nhánh đọc CV/evidence theo consent/share.

Kiểm tra chữ ký định dạng sơ bộ cùng MIME khai báo và extension; sai trả `422 BUSINESS_VALIDATION`. Đây chưa phải parser nội dung đầy đủ hay antivirus. ZIP cho Task hiện trả `503 DEPENDENCY_UNAVAILABLE`; CSV/XLSX cũng chưa mở. Không tự bỏ các kiểm tra zip-slip, symlink, decompression, macro/formula hoặc giới hạn import mà CP-FILE1 yêu cầu để cho các định dạng đó chạy.

Các lỗi chính: chưa đăng nhập/session revoked `401`; thiếu trần quyền upload `403`; đọc thiếu quyền, resource/file ngoài scope hoặc đã xóa `404`; chưa CLEAN `409 FILE_NOT_CLEAN`; quá lớn `413 FILE_TOO_LARGE`; Range sai/đa range `416 INVALID_RANGE` sau kiểm quyền; storage thiếu hoặc bytes lệch hash `503 FILE_STORAGE_UNAVAILABLE`. Response lỗi dùng envelope C.0, không đưa SQL/path ra client.

## Cách gọi từ frontend đã đăng nhập

Ví dụ này dùng `File` đã chọn bởi người dùng, `studentUserId` lấy từ session/profile; không gửi actorId, ownerId hay organizationId để tự cấp quyền:

```javascript
const csrf = await fetch('/api/v1/auth/csrf').then(r => r.json());
const form = new FormData();
form.set('file', selectedFile);
form.set('resourceType', 'STUDENT_PROFILE');
form.set('resourceId', studentUserId);
form.set('purpose', 'CV');
const uploadKey = crypto.randomUUID(); // giữ lại khi retry cùng request
const response = await fetch('/api/v1/files', {
  method: 'POST',
  headers: { [csrf.headerName]: csrf.token, 'Idempotency-Key': uploadKey },
  body: form
}); // browser tự đặt multipart Content-Type và boundary
const result = await response.json();
if (!response.ok) throw new Error(result.errorCode);
const metadata = await fetch(`/api/v1/files/${result.fileId}/metadata`).then(r => r.json());
// T1-06 trả PENDING; hiển thị trạng thái, không tự chuyển sang CLEAN hoặc gắn vào profile.
```

Khi có scanner thật ở T1-07, client tải bằng cùng session qua `/content`. Hỗ trợ `Range: bytes=0-1023`, `bytes=1024-`, `bytes=-1024`; mỗi request kiểm quyền lại. Thu hồi quyền chặn request tiếp theo, không thể lấy lại bytes đã trả trước đó hay hủy một stream đang chạy chỉ bằng đổi membership.

## Boundary để các team tích hợp

- [FileStorage](../../src/main/java/vn/utbridora/auth/files/FileStorage.java): port stage/promote/open/delete; adapter local là `PrivateFileStorage`. Thay bằng object storage phải giữ private access, hash/size, cleanup và streaming; không công khai URL.
- [FileResourceAuthorizer](../../src/main/java/vn/utbridora/auth/files/FileResourceAuthorizer.java): ActorContext do server tạo, không bind từ JSON; trả FileAuthorization với resource version. Provider dùng giờ server, không tin timestamp client.
- [FileResourcePolicy](../../src/main/java/vn/utbridora/auth/files/FileResourcePolicy.java): một adapter cho mỗi resource type; trùng provider làm startup thất bại, thiếu provider thì deny. `check` xác thực resource, purpose, action và version từ nguồn owner; `mayReadFile` mặc định chỉ người upload. Shared adapter phải xác thực file còn được gắn vào đúng field/resource và consent/share/assignment hiện hành.
- [FileReadPort.requireCleanBound](../../src/main/java/vn/utbridora/auth/files/FileReadPort.java): dùng trong transaction lưu/nộp của owner; kiểm current read authority, đúng resource/purpose, CLEAN và binary còn nguyên. Dùng convention Java hiện có: trả `FileDto`, lỗi `OperationFailure`; đây không phải HTTP route. Caller vẫn phải kiểm quyền WRITE, version và trạng thái nghiệp vụ của thao tác gắn/nộp. Không chỉ nhìn scanStatus từ browser.

T2: API upload SELF đã có nhưng `StudentProfileService` vẫn từ chối file reference mới; gắn CV/dự án/chứng chỉ thuộc T2-09. T3: Task assignment upload PDF đã có, nhưng chưa tạo submission/review/weekly report thay T3-06/T3-07; URL mode vẫn là đường tuần 3 có thể làm độc lập. T5: verification draft upload đã có; shared profile CV/evidence vẫn fail closed. `FileBindingChecks` kiểm refs xác minh doanh nghiệp hiện có được giữ nguyên; không coi fixture CLEAN cũ là binary đã tồn tại trong storage mới.

## Cấu hình và dữ liệu

Host JVM: đặt `FILE_STORAGE_ROOT` là đường dẫn tuyệt đối ngoài repository, web root và thư mục chia sẻ công khai. Để trống/relative/symlink root thì upload và tải binary thất bại an toàn. Process OS phải có quyền riêng trên thư mục; Windows cần ACL theo service account. Trên filesystem POSIX, adapter đặt directory 0700/file 0600. Tên client chỉ dùng để hiển thị; key binary là UUID do server tạo.

Dockerfile chuẩn bị `/data/files` cho UID/GID 10001. `compose.local.yaml` dùng volume `local_files`, staging dùng `staging_files`; backend read-only trừ volume và `/tmp`. Proxy chỉ nâng body limit lên 21 MiB cho đúng `/api/v1/files`; giới hạn các API khác giữ nguyên. Spring nhận file tối đa 20 MiB, request 21 MiB, rồi kiểm giới hạn theo purpose.

Tomcat drain tối đa 22 MiB cho request bị hủy để client nhận được `413` ở biên multipart; request lớn hơn nhiều có thể bị đóng kết nối. Không đặt drain vô hạn. Căn cứ hành vi [maxSwallowSize của Tomcat](https://tomcat.apache.org/tomcat-11.0-doc/config/http.html#Standard_Implementation).

Migration V28 chỉ thêm permission ceilings và index tìm binding. Giữ nguyên `files`/`file_bindings` cũ, owner, tên, scan state và key; không importer, backfill CLEAN hay sửa volume/database dùng chung. Legacy key không trỏ đến binary private hợp lệ sẽ không tải được; cần kế hoạch chuyển dữ liệu riêng nếu có dữ liệu thật cũ.

**Phiên này chỉ sửa cấu hình source, chưa rebuild/restart `utbridora-local` ở 8088.** Dev mới theo [LOCAL_DEMO](LOCAL_DEMO.md) để có database riêng. Môi trường hiện có không chạy lại seed/importer. Trước khi deploy thật, backup cả MySQL và volume file ở cùng mốc nhất quán; `backup-staging.ps1` hiện chỉ dump DB. Không dùng `docker compose down -v` để nâng cấp.

Rollback transaction xóa object vừa promote và rollback metadata/binding/ledger/audit cùng nhau. Process bị kill/mất điện giữa filesystem và DB có thể để lại object không có metadata hoặc `.part`; chúng không truy cập được qua API. Chưa có job dọn orphan/crash recovery hoặc quota tổng; cần bổ sung trước vận hành quy mô lớn. Không xóa object chỉ dựa vào timestamp khi chưa đối chiếu DB/transaction đang chạy.

## Phần tiếp theo

T1-07 cần adapter quét thật, timeout 60 giây/lần, lần đầu + ba retry 5/30/120 giây, hết retry chuyển REJECTED và audit chuẩn; validators cho archive/import; race/revoke/IDOR mở rộng và kiểm thử an toàn file. Chỉ scanner tin cậy được chuyển CLEAN, không thêm endpoint DEV mở quyền ghi scan state. Shared authorizer T2/T3/T5 và workflow attachment phải có integration test của owner trước khi công bố file flow hoàn thành. Human QA01/Lead vẫn xác nhận riêng.
