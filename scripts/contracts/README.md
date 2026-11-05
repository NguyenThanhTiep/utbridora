# Kiểm tra kỹ thuật Contract Pack

Công cụ DEV cho T1-01. Build bundle từ bản [v1.1 Candidate](../../docs/contracts/UTBridora_Contract_Pack_B01_B06_v1.1_Candidate.md), kiểm tra schema, ví dụ, registry và tương thích tĩnh với core. Không kết nối database, gọi API, gửi mail hoặc thay migration.

## Chạy trên Windows

Chạy ở thư mục chứa pom.xml, dùng Python 3.12. Môi trường kiểm tra đặt ngoài repository, không cài package vào Python hệ thống:

```powershell
$contractVenv = Join-Path $env:TEMP 'utbridora-contracts-venv'
py -3.12 -m venv $contractVenv
& "$contractVenv\Scripts\python.exe" -m pip install -r scripts/contracts/requirements.txt
& "$contractVenv\Scripts\python.exe" scripts/contracts/build_contract_bundle.py --check
& "$contractVenv\Scripts\python.exe" scripts/contracts/verify_contracts.py --output target/contract-verification.json
```

Linux/CI dùng cùng hai script sau khi cài requirements trong virtualenv/Python runner. File requirements khóa phiên bản của validator và các dependency. Python chỉ là công cụ DEV/CI, không thay công nghệ backend Java.

## Khi cập nhật contract có chủ đích

```text
python scripts/contracts/build_contract_bundle.py
python scripts/contracts/build_contract_bundle.py --check
python scripts/contracts/verify_contracts.py --output target/contract-verification.json
```

Lệnh đầu tái sinh JSON/CSV trong docs/contracts/v1.1. Review diff trước khi nhận thay đổi: không dùng việc tái sinh sourceHashes để che thay đổi API hoặc registry ngoài hợp đồng. --check chỉ so sánh, không sửa file. Các hash source text được chuẩn hóa LF để không báo khác giả giữa Windows/Linux; DOCX và Contract v1.0 dùng raw hash bất biến.

## Ý nghĩa kết quả

- Build không chấp nhận type chưa phân giải; các $ref chỉ nội bộ, không tải schema từ mạng khi verify.
- JSON Schema Draft 2020-12 kiểm required/optional/null, enum, array, số, object đóng và format. Custom formats utb-utc-instant, utb-https-url, utb-java-password-input, utb-new-password được đăng ký trong verifier. Consumer dùng validator khác phải triển khai cùng format; không mặc định format tự được kiểm.
- Format email dùng parser addr-spec của thư viện chuẩn để không chấp nhận trường hợp thiếu local/domain; không kiểm DNS hoặc khả năng nhận mail. JSON được đọc bằng Decimal để kiểm scale chính xác; type integer chấp nhận số có phần lẻ bằng 0 (như 1.0), vẫn từ chối boolean. Backend Java tiếp tục chịu trách nhiệm xác thực dữ liệu và kết quả HTTP thực tế.
- Bộ fixture kiểm password UTF-8/codepoint, legacy/new activation shape, field mask, counter và event version. Đây là oracle tĩnh, không phải guard runtime của các module.
- AuditEventInput dùng allowlist action/resource/result/metadata của producer mới; không áp schema này để phủ nhận log lịch sử cũ. 63 action mới và 33 permission mới chưa được thêm vào DB/source runtime.
- Các port Input/Value là mô hình cho tham số nghiệp vụ và giá trị Success. Actor/mutation/event context là server-only; không tự sinh endpoint hay JSON success wrapper.
- Báo cáo tạo từ lệnh này là kết quả DEV; không sửa cột Tester hay xác nhận nghiệm thu SRS.

GitHub workflow có job contracts độc lập. Kết quả local không chứng minh job mới đã chạy trên GitHub; phải xem run của commit sau push. Bộ runtime Java/Chromium tiếp tục chạy trong job verify hiện có.

## Bảo trì

Các khai báo một dòng trong code fence text được compile bởi build_contract_bundle.py. Kiểu mới không hỗ trợ phải được bổ sung vào compiler và kiểm tra bằng fixture; không thay bằng object mở hoặc bỏ qua ví dụ để làm PASS. Mỗi ví dụ JSON có ID/section/line và schema cụ thể. Các field server-only, quyền và việc đọc dữ liệu đúng tenant vẫn phải kiểm ở implementation backend.

Fixture chứa UUID, email example.test và chuỗi synthetic; không dùng làm credential seed. Báo cáo không xuất payload thất bại hoặc giá trị token/password.

Nguồn kỹ thuật: [JSON Schema Draft 2020-12](https://json-schema.org/draft/2020-12/json-schema-validation), [python-jsonschema validation và FormatChecker](https://python-jsonschema.readthedocs.io/en/stable/validate/), [jsonschema 4.25.1](https://pypi.org/project/jsonschema/4.25.1/), [actions/setup-python](https://github.com/actions/setup-python).
