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
hoặc kết nối khi worker tắt. Override `compose.scanner.yaml` bên dưới đóng gói daemon
và bật worker khi người vận hành chủ động ghép vào bản triển khai. Nếu dùng scanner
riêng đã được quản lý, các biến tương ứng là:

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

## Bộ triển khai Docker local/VPS

`compose.scanner.yaml` dùng chung với **một** base `compose.local.yaml` hoặc
`compose.staging.yaml`. Base hiện có và môi trường đang chạy không tự thay đổi khi
pull source. Không chạy override độc lập; luôn giữ cùng project name/env-file/base
ở mọi lệnh tiếp theo, kể cả khi restart. Không chạy `reset-demo`, `down -v`, Flyway
repair hoặc đổi volume để bật scanner.

- Image chính thức `clamav/clamav:1.4` được khóa manifest SHA256, kiểm tra ngày
  23/11/2026. Bản đóng gói này dành cho **Linux amd64**; VPS ARM64 cần chọn và kiểm
  chứng image native riêng, không tự chạy giả lập. Trước ngày deploy thực tế,
  kiểm tra lại bản vá/EOL và cập nhật digest sau smoke test; digest không tự cập nhật.
- Chỉ BE và ClamAV dùng mạng `file_scan` nội bộ. Không publish cổng3310, không gắn
  volume file của ứng dụng hay Docker socket vào scanner. Mạng `signature_updates`
  riêng chỉ cho scanner ra DNS/HTTPS tới dịch vụ cập nhật chính thức.
- Named volume `clamav_signatures` giữ chữ ký sau recreate/restart. Wrapper cập nhật
  chữ ký một lần trước khi khởi động ClamD, rồi FreshClam kiểm tra12 lần/ngày; giữ
  kiểm thử database tải xuống. Update đầu lỗi thì scanner không sẵn sàng; không
  retry-loop bỏ qua CDN cooldown. Config/start script bind read-only; daemon hạ
  quyền về user clamav sau init/chown volume.
- Dành4GiB cho scanner,2CPU; preflight yêu cầu toàn Docker host có ít nhất8GB RAM
  và4CPU. Đây là ngân sách triển khai, không chứng nhận R31. Vẫn cần theo dõi RAM
  MySQL/JVM và tải thực tế. Không tự bật trên Docker demo có tài nguyên chưa đủ.
- Health check cần ClamD trả lời và tiến trình FreshClam còn sống. BE đợi scanner
  healthy trước startup. `FailIfCvdOlderThan7` chặn khởi động bằng chữ ký cũ;
  health **không** chứng nhận liên tục độ mới của chữ ký. Người vận hành phải theo
  dõi VERSION/thời gian database và lỗi FreshClam; cập nhật bị CDN chặn/thiếu DNS
  thì xử lý nguyên nhân, không bỏ kiểm tra hay thay bằng test signature.

### Chuẩn bị và kiểm tra, chưa bật worker

Trên VPS mới, lấy `deploy/clamav/staging.env.example` làm mẫu để tạo `.env.staging`
ngoài Git. Nhập DB/SMTP/HTTPS/secret thật; giữ dòng key=value đơn giản. Python3 và
Docker Compose v2 phải có sẵn. Với database đã có, dùng lại env/project/volume và
Flyway locations đã kiểm chứng của chính môi trường đó; kiểm tra source/JAR mới
trên clone và backup SQL + private files trước thay đổi runtime.

```sh
python3 scripts/scanner.py check --base compose.staging.yaml --env-file .env.staging --project utbridora-staging
python3 scripts/scanner.py start --base compose.staging.yaml --env-file .env.staging --project utbridora-staging
```

`check` chỉ đọc config/tài nguyên, không in secret. `start` chỉ pull/start scanner,
đợi tối đa15phút và thử stream một chuỗi sạch + EICAR vô hại; **không** restart BE,
không ghi ứng dụng/DB/kho file. Lần đầu download có thể lâu. Lỗi thì dừng ở bước này.
Kết quả `SCANNER_PROBE_PASS_BACKEND_UNCHANGED` chỉ chứng minh scanner, chưa bật worker.

Windows local dùng cùng các lệnh, thay `python3` bằng Python trên PATH, base bằng
`compose.local.yaml`, env bằng `.env.local`, project bằng `utbridora-local`.
Launcher `run-local.cmd start` hiện không tự ghép override scanner; sau khi bật,
chỉ dùng lệnh Compose có cả hai file để tránh vô tình tắt worker ở lần start sau.

### Áp dụng cho backend trong đợt deploy

Sau khi backup/clone và xác minh scanner, dùng đúng bộ file khi build/start bản
phát hành. Với **VPS mới, DB mới**:

```sh
docker compose --project-name utbridora-staging --env-file .env.staging -f compose.staging.yaml -f compose.scanner.yaml config --quiet
docker compose --project-name utbridora-staging --env-file .env.staging -f compose.staging.yaml -f compose.scanner.yaml build backend frontend
docker compose --project-name utbridora-staging --env-file .env.staging -f compose.staging.yaml -f compose.scanner.yaml up -d --wait --wait-timeout 1200
```

Với **BE hiện hữu và đúng image đã kiểm chứng**, sau backup/clone, chỉ recreate BE,
không recreate MySQL/frontend hoặc đổi schema; không build lại bằng lệnh này:

```sh
docker compose --project-name utbridora-staging --env-file .env.staging -f compose.staging.yaml -f compose.scanner.yaml up -d --no-deps --no-build --wait --wait-timeout 300 backend
```

Worker sẽ bắt đầu xử lý **cả file PENDING cũ** theo guard hiện có; object mất/hỏng
hoặc hết4attempt không tự CLEAN. Kiểm tra số PENDING và lý do REJECTED trước rollout;
không reset attempt hoặc sửa scan_status bằng SQL. Import CSV/XLSX dùng scanner trực
tiếp trong luồng quarantine, còn file upload dùng worker nền. Hai luồng đều cần
ClamD thật và không bỏ qua quyền/binding/consent.

### Theo dõi và khôi phục cấu hình

```sh
python3 scripts/scanner.py status --base compose.staging.yaml --env-file .env.staging --project utbridora-staging
python3 scripts/scanner.py probe --base compose.staging.yaml --env-file .env.staging --project utbridora-staging
docker compose --project-name utbridora-staging --env-file .env.staging -f compose.staging.yaml -f compose.scanner.yaml exec -T clamav clamdscan --version
```

Theo dõi private scanner logs/FreshClam updates/OOM và metadata file PENDING → CLEAN
hoặc REJECTED. Probe chỉ gửi byte synthetic vào ClamD, không thay việc kiểm thử upload
→ worker → tải có quyền của ứng dụng. Chạy luồng này trên staging với tài khoản/file
thử trước khi mở cho người dùng. Không phát tán file EICAR qua UI đang dùng chung.

BE readiness hiện chỉ phản ánh DB, không phản ánh scanner. Nếu scanner chết sau
startup, backend có thể vẫn healthy nhưng file sẽ retry rồi REJECTED; health Docker
không tự restart container chỉ vì unhealthy. Cần cảnh báo để người vận hành xử lý.
Nếu cần rollback cấu hình, dùng lại đúng base/env/project với `FILE_SCAN_ENABLED=false`
và recreate riêng BE sau khi xác nhận; file mới giữ PENDING và bị chặn tải. Giữ volume
chữ ký và kho file; không chạy `down -v`. Đây là dừng xử lý, không bypass bảo vệ.

Nguồn đã kiểm tra: [Docker/RAM](https://docs.clamav.net/manual/Installing/Docker.html),
[vòng đời1.4LTS](https://docs.clamav.net/faq/faq-eol.html),
[config ClamD](https://github.com/Cisco-Talos/clamav/blob/rel/1.4/etc/clamd.conf.sample),
[FreshClam](https://github.com/Cisco-Talos/clamav/blob/rel/1.4/etc/freshclam.conf.sample).

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
