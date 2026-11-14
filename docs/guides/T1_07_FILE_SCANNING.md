# T1-07 tuần 4 — quét tệp, quyền tải và audit

Phạm vi Plan sheet task, dòng 58: DEV01/QA01, phụ thuộc T1-06; SRS v1.2.4
§25.8.3 G15/R16 và CP-FILE1 trong Contract v1.1 đã được người dùng duyệt ngày
13/11/2026. Các nhãn Candidate/PROPOSED trong snapshot là lịch sử. Không sửa Plan,
hai snapshot đã duyệt hoặc thay QA01/Lead ký nghiệm thu.

## Luồng thực tế

Upload giữ API `POST /api/v1/files`, trả `202 FileDto` với `scanStatus=PENDING`.
T1 worker lấy claim bền vững trong DB, mở đúng object private và kiểm lại size/hash,
binding/purpose, MIME/signature và cấu trúc archive/import trước khi gửi bytes qua
ClamD `zINSTREAM`. Không gửi đường dẫn storage, không chạy shell hoặc thực thi tệp.
Chỉ câu trả lời chính xác `stream: OK` mới được coi CLEAN. Virus/giới hạn scan bị
báo FOUND thành REJECTED. Lỗi kết nối/protocol/timeout luôn fail closed.

Metadata vẫn dùng ba trạng thái wire `PENDING|CLEAN|REJECTED`. GET/Range giữ session,
scope/owner/binding và consent/share đang hiệu lực, rồi mới kiểm CLEAN/range. Không
có public storage URL, endpoint sửa scan state, ADMIN shortcut hoặc bypass nội bộ
cho consumer. T2/T3/T5 dùng `FileReadPort.requireCleanBound` khi cần nộp/gắn file;
trạng thái hiển thị ở UI không thay kiểm tra server. CLEAN không cấp quyền đọc.

## Retry, crash và transaction

- Tối đa bốn attempt: lần đầu + ba retry sau 5/30/120 giây từ kết thúc attempt lỗi.
- Ngân sách scanner tối đa 60 giây/attempt; socket deadline đóng cả read/write bị
  treo. Lease 75 giây cho phép phục hồi worker chết; lease không phải tăng timeout.
- Claim dùng `FOR UPDATE SKIP LOCKED`, token UUID riêng và attempt tăng trong DB;
  scanner I/O nằm ngoài transaction. Nhiều instance không nhận cùng claim.
- Completion kiểm token/attempt/trạng thái và hạn lease đang khóa. Kết quả cũ hoặc
  bị lặp không đổi state. Kết quả tới tại/sau hạn không được nâng CLEAN.
- Lease quá hạn được ghi timeout, xếp retry đúng lịch; attempt thứ tư lỗi thành
  REJECTED. Không có retry vô hạn hoặc tự reset ngân sách khi restart.
- State và `FILE_SCAN_COMPLETED` commit cùng transaction. Audit lỗi thì không có
  CLEAN được commit. Audit SYSTEM có actorId=null, chỉ `scanStatus,attemptNo,
  reasonCode` theo E.2; không có tên virus, tên file, bytes, path hoặc secret.
- Worker có executor riêng một luồng, tối đa một việc đang chạy/chờ; timer chỉ
  nhận lịch rồi trả về, không giữ scheduler của Placement trong lúc quét.

Migration **V38 trong `db/migration/base`** chỉ bổ sung attempt/reason/next-at/lease/
version/index/check, áp dụng mọi lineage. Không sửa migration/checksum cũ, binary,
binding hoặc trạng thái CLEAN/REJECTED đã có. PENDING legacy được quét lại nếu có
metadata/binding/binary hợp lệ; thiếu object không được tự CLEAN. Không dọn object
quarantine bằng tuổi file; retention và orphan cleanup là công việc vận hành riêng.

## Kiểm tra nội dung và giới hạn

Giữ 10 MiB PDF/PNG/JPEG cho CV/minh chứng, 20 MiB PDF/ZIP cho submission, 5 MiB
CSV/XLSX cho import. Resource/purpose chưa có owner authorizer vẫn bị từ chối.

ZIP/XLSX được đọc trong bộ nhớ có giới hạn, không giải nén ra filesystem; kiểm cả
central directory và entry stream/CRC, tối đa 100 MiB sau giải nén/1.000 entries.
Từ chối traversal/absolute/Windows paths, symlink/special files, entries trùng,
encryption, ZIP64/spanning, archive lồng nhận diện được hoặc archive hỏng. Đây là
các biến thể chưa hỗ trợ; trả 422, không âm thầm bỏ qua để CLEAN. ClamD cũng phải
bật cảnh báo encrypted/scan limits để fail closed với archive bên trong.

XLSX không chạy macro/formula, kiểm XML không DTD/external entities; từ chối macro,
external relationships và embedded objects. CSV giải mã UTF-8 nghiêm ngặt, kiểm
quote/số cột/header; giới hạn roster 5.000 và placement 1.000 data rows. Validators
format không tạo importer/provisioning job; required business columns/catalog và
luồng import vẫn do task import của owner chịu trách nhiệm. Không tuyên bố parser
PDF/ảnh đầy đủ hoặc thay antivirus bằng magic header.

## Cấu hình để vận hành sau này

Source có `FILE_SCAN_ENABLED=false` mặc định, giữ môi trường hiện hành không tự
đổi. Compose local/staging truyền ba biến scan vào backend; không tự tạo daemon
hoặc kết nối khi worker tắt. Khi được triển khai trên môi trường riêng có scanner:

```text
FILE_STORAGE_ROOT=<absolute private directory>
FILE_SCAN_ENABLED=true
FILE_SCAN_HOST=<trusted private ClamD hostname>
FILE_SCAN_PORT=3310
```

Không lấy host/port từ request. ClamD TCP không có xác thực: chỉ cho backend truy
cập qua mạng private/firewall, không publish port cho người dùng/Internet. Cấu hình
tham khảo: [clamd.conf.example](../../deploy/clamav/clamd.conf.example). Dùng ClamAV
đã vá bảo mật, definitions chính thức cập nhật bằng FreshClam, volume definitions
bền vững và đủ RAM. File-service không tự tải definitions hoặc tự khởi động Docker.
Thiếu endpoint/lỗi scanner khi worker đã bật vẫn chỉ retry rồi REJECTED; tắt worker
thì tệp mới ở PENDING, mọi lần tải bị chặn.

**Không deploy/restart/rebuild hoặc sửa dữ liệu/volume/account của stack 8088 trong
task này.** Không dùng test signature của CI làm antivirus database vận hành.

## Kiểm chứng và bàn giao

- Unit: framing/protocol/timeout thực qua socket loopback; ZIP/CSV/XLSX/XXE/size/
  traversal/symlink tests; hồi quy storage/authorization hiện hữu.
- `FileScanningIT`: MySQL và private directory riêng; ClamAV engine thật, signature
  EICAR synthetic riêng để tránh download definitions qua mạng trong CI. Hai luồng
  clean/EICAR đi qua adapter thật; fault tests gọi trusted verdict để kiểm retry,
  lease/crash, concurrency, audit rollback và corruption. Không dùng SQL set CLEAN.
- `EnterpriseProposalIT.sharedCvUsesOneT1ProviderAndChecksBindingCleanConsentAndEveryRange`:
  engine thật trước profile/download/Range; revoke consent chặn request sau commit.
- `PrivateFileServiceIT`: multipart, CSRF, owner/foreign/ADMIN, limits, ZIP upload,
  historical read và membership revoke; CLEAN fixture cũ vẫn được ghi rõ là fixture.
- `MigrationUpgradeIT` và `Week3MigrationIT`: giữ metadata/binding/scan-state cũ,
  lịch sử/checksum/immutable criteria và hội tụ mọi lineage lên V38.

Kết quả thực thi mới và CI phải xem đúng SHA trên nhánh bàn giao; tài liệu này mô
tả implementation/test scope, không tự xác nhận tất cả test đã chạy hoặc QA PASS.
CI chia IT ra ba runner; đọc đủ `integration-results-1..3`, `test-results` và
`contract-results` khi tổng hợp. Đo thời gian thực ở lượt đầu, không dùng timing hint
thay số đo. Xem thêm [cấu hình CI](../testing/CI_PARALLEL_VERIFICATION.md).

Nguồn giao thức/vận hành: [ClamD protocol](https://docs.clamav.net/manual/Usage/ClamdProtocol.html),
[official Docker images](https://docs.clamav.net/manual/Installing/Docker.html),
[ClamAV 1.4.6 security release](https://blog.clamav.net/2026/08/clamav-154-and-146-security-patch.html).
