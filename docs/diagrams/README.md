# Sơ đồ UML của UT.Bridora

Bộ mã PlantUML gồm **1 Use Case Diagram và 7 Activity Diagram**, đối chiếu với source Core Platform. Mỗi file `.puml` độc lập, dùng nhãn tiếng Việt và có thể sao chép trực tiếp vào công cụ hỗ trợ PlantUML.

| Mã | Nội dung | Mã chỉnh sửa | Ảnh vector | Ảnh PNG |
|---|---|---|---|---|
| UC-01 | Use Case nền tảng dùng chung | [PlantUML](01-core-use-cases.puml) | [SVG](rendered/01-core-use-cases.svg) | [PNG](rendered/01-core-use-cases.png) |
| AD-01 | Đăng nhập | [PlantUML](02-login.puml) | [SVG](rendered/02-login.svg) | [PNG](rendered/02-login.png) |
| AD-02 | Lời mời và thiết lập tài khoản | [PlantUML](03-invitation-activation.puml) | [SVG](rendered/03-invitation-activation.svg) | [PNG](rendered/03-invitation-activation.png) |
| AD-03 | Quên và đặt lại mật khẩu | [PlantUML](04-password-reset.puml) | [SVG](rendered/04-password-reset.svg) | [PNG](rendered/04-password-reset.png) |
| AD-04 | Cấp / thu hồi membership | [PlantUML](05-membership-management.puml) | [SVG](rendered/05-membership-management.svg) | [PNG](rendered/05-membership-management.png) |
| AD-05 | Thay đổi trạng thái tài khoản | [PlantUML](06-account-lifecycle.puml) | [SVG](rendered/06-account-lifecycle.svg) | [PNG](rendered/06-account-lifecycle.png) |
| AD-06 | Quyền truy cập tài nguyên / hồ sơ | [PlantUML](07-resource-authorization.puml) | [SVG](rendered/07-resource-authorization.svg) | [PNG](rendered/07-resource-authorization.png) |
| AD-07 | Tra cứu audit log | [PlantUML](08-audit-log-query.puml) | [SVG](rendered/08-audit-log-query.svg) | [PNG](rendered/08-audit-log-query.png) |

## Cách dùng

1. Mở file `.puml` của luồng cần vẽ.
2. Sao chép toàn bộ từ `@startuml` đến `@enduml` vào trình soạn PlantUML của công cụ.
3. Chọn xem trước và xuất SVG/PNG. Dùng SVG khi cần phóng to; các Activity là luồng chi tiết, nên trình bày riêng từng sơ đồ.

Nếu công cụ chỉ nhận Mermaid hoặc mô tả tự nhiên, cần chuyển đổi định dạng; không dán PlantUML vào ô mã Mermaid.

Các ảnh sẵn có nằm trong `rendered/`. Khi thay đổi `.puml`, cần xuất lại cả SVG và PNG để ảnh đồng bộ với mã.

## Render cục bộ

Cần Java và JAR PlantUML. Kiểm tra bộ này bằng PlantUML **1.2026.9**. JAR công cụ nằm ngoài repository, không được thêm vào source dự án.

Ví dụ PowerShell, chạy từ thư mục gốc dự án; thay đường dẫn JAR bằng vị trí trên máy:

```powershell
java -jar 'C:\Tools\plantuml.jar' -charset UTF-8 --check-syntax 'docs/diagrams/*.puml'
java -jar 'C:\Tools\plantuml.jar' -charset UTF-8 --svg --output-dir rendered 'docs/diagrams/*.puml'
java -jar 'C:\Tools\plantuml.jar' -charset UTF-8 --png --output-dir rendered 'docs/diagrams/*.puml'
```

Use Case khai báo layout Smetana; Activity dùng bộ dựng của PlantUML. Bộ này đã render cục bộ mà không cài Graphviz riêng và không gửi nội dung dự án đến dịch vụ vẽ bên ngoài.

## Phạm vi và cách đọc

- Use Case biểu diễn các mục tiêu người dùng. Đăng nhập là tiền điều kiện của chức năng được bảo vệ; không gắn `include` đăng nhập vào mọi use case.
- ADMIN cần quyền quản trị global cụ thể, không có quyền nghiệp vụ mặc định. Một người có thể có nhiều membership.
- Khóa credential sau 5 lần sai trong 15 phút không đổi trạng thái tài khoản thành SUSPENDED.
- Luồng kích hoạt hiện có là chấp nhận lời mời rồi tạo tài khoản ACTIVE; kích hoạt riêng tài khoản PENDING import sẵn còn thiếu.
- Token lời mời hạn 48 giờ; token reset hạn 30 phút. Token dùng một lần, database chỉ lưu hash.
- Thay đổi mật khẩu, membership hoặc trạng thái làm phiên cũ bị từ chối ở request tiếp theo qua `security_version`; không giả định xóa tức thì mọi dòng session.
- Audit mutation thành công cùng transaction với thay đổi dữ liệu. Đăng nhập ghi LOGIN/SUCCESS trước bước tạo phiên; các bước này không phải một transaction chung.
- SMTP hiện gửi đồng bộ. Outbox/retry và chống enumeration theo timing còn cần hoàn thiện.
- **AD-06 là luồng hợp đồng tích hợp**, chưa phải endpoint nghiệp vụ thật. Guard đã có; record/assignment, adapter chia sẻ, consent và lọc field còn phụ thuộc T2–T5. Nhánh owner được xét trước nhánh tổ chức. Adapter chia sẻ mặc định từ chối.
- Không vẽ các module T2–T5 thành microservice; kiến trúc vẫn là Modular Monolith.

Nguồn đối chiếu: [API](../API_DOCUMENTATION.md), [Authorization](../AUTHORIZATION.md), [Vòng đời tài khoản](../ACCOUNT_LIFECYCLE.md), [Audit](../AUDIT_LOG.md), cùng các controller/service thực tế.

## Kiểm tra ngày 31/10/2026

- PlantUML: kiểm tra cú pháp **8/8 PASS**; xuất **8 SVG + 8 PNG PASS**.
- Đã kiểm tra ảnh để phát hiện lỗi hiển thị và đối chiếu các nhánh quan trọng với source.
- Đây là kiểm tra tài liệu/sơ đồ; không thay thế kết quả test backend, frontend hoặc nghiệm thu nghiệp vụ T2–T5.

Hai file Mermaid có sẵn `access_decision.mmd` và `account_lifecycle.mmd` được giữ nguyên.
