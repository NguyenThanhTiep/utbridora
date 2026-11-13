# Sửa lỗi sau rà soát và tích hợp portal T5

Nguồn: main `9565c30ab0bc88a9bfc471a8ccfb8e9756203612` và nhánh DEV `feature/t5-week3-implementation` tại `715d5390fd8e9b3679fe0df4b3073cd44313e802`. Giữ lịch sử nguồn; không xóa nhánh. SRS v1.2.4/Contract v1.1 đã được người dùng duyệt, xem bản ghi quyết định trong `docs/contracts/README.md`; không thay bytes của hai snapshot.

## Hành vi được sửa

- R03/T5-05: INVITED giữ chỗ đến hạn lời mời; ACCEPTED bắt đầu cửa sổ tối đa7ngày, giới hạn bởi deadline và consent hiện hành. Cấp share thu hẹp reservation theo consent. Worker SYSTEM hết hạn proposal chưa Placement; giữ mọi PENDING_APPROVAL/APPROVED/ACTIVE/COMPLETED đang được bảo vệ. Expiry có history kỹ thuật, version và replay không lặp effect; lời mời PENDING hết hạn có audit canonical.
- R02/T3-04: cùng pending Mentor claim/scope có thể cấp lại token với trạng thái ACCOUNT_PENDING, giữ invitation reference, thu hồi token cũ. Claim khóa theo canonical email trước user/token; accept/revoke/reissue serialize cùng khóa. Cooldown5phút, tối đa3manual reissue/24h xuyên key/actor của email. Claim khác trả REVIEW_REQUIRED; tài khoản bị khóa không tự ACTIVE. Pending user đã gắn đúng claim giữ user ID khi tự kích hoạt. Legacy activation không thay body/status; không mở profile opt-in hoặc hệ thống import/outbox ngoài task này.
- R01/T2: chấp nhận yearsPractice decimal>=0 như contract, không tự đặt trần999.99/hai chữ số. Parser dùng BigDecimal; UI step=any. V34 chuyển hai cột chỉ được đọc/ghi qua JDBC thành biểu diễn decimal chính xác LONGTEXT, regex bảo đảm dạng số không âm. Không dùng các cột này để lọc/sort số bằng SQL; classifier/snapshot vẫn dùng BigDecimal. Không sửa migration cũ, không round/truncate số hoặc thay kiểu JSON number trên wire.
- Portal mới giữ menu Matching trường và dùng lại API protected release T4 cho thống kê doanh nghiệp. Không quay lại placeholder "chưa có API" hoặc tính raw count từ danh sách trường. Giữ File API/delegation, student tabs, guard và các assertion consent/revoke trước đó khi giải quyết conflict.

## Nâng cấp database

- V34: chuyển chính xác số DECIMAL cũ, giữ giá trị và ràng buộc không âm; không sửa V11/checksum cũ.
- V35: thêm khóa Mentor onboarding claim và lịch manual reissue; chỉ nhận pending reference thực sự thuộc Mentor/token hiện hữu. Không takeover claim roster/org khác.
- V36: history SYSTEM và hiệu chỉnh TTL các proposal mutable chưa Placement từ bản cũ. Ghi lại thời hạn trước/sau và tăng version; không sửa invitation decisions, immutable criteria/revision/snapshot/hash hoặc lịch sử Placement. Client đang giữ version cũ cần tải lại khi gặp VERSION_CONFLICT.
- Chạy các migration bằng Flyway trên đường upgrade bình thường. Không repair/reset, không out-of-order, không sửa flyway_schema_history. Không tự chạy chúng lên stack chung8088 trong nhiệm vụ này.

## Kiểm chứng

Giữ các test DEV/main đã có; thêm boundary parser/HTTP persistence, hạn giữ chỗ vượt24h/accept/consent/expiry replay, token generation/cooldown/quota/old-token/pending-user/concurrent reissue và migration dữ liệu cũ. Browser mới vẫn kiểm API/provider thực, persisted state và quyền. Test UX mock chỉ chứng minh UI consumer, không thay backend integration.

Lệnh chính: `npm ci`, `npm run build`; `mvn -B -Dfrontend.e2e=true -Dt5.week3.browser=true verify`; các checker contract/audit/portability và oracle S1–S11 trong workflow. Báo kết quả theo đúng commit/CI cuối và lưu artifact; không dùng tài liệu này thay QA/Lead signoff. Môi trường8088, tài khoản, mật khẩu, dữ liệu và stash không bị triển khai/thay đổi bởi bản sửa source này.
