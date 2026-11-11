# Module T5 — Cooperation

Theo [TEAM_IMPLEMENTATION_GUIDE](../../../../docs/guides/TEAM_IMPLEMENTATION_GUIDE.md), T5 dùng chung React shell, session, CSRF và component nền tảng với các team. Không tạo app, router hoặc cơ chế đăng nhập riêng.

Người dùng đảm nhiệm Dev11 **T5-02** và Dev12 **T5-03** tuần 1. T5-03 yêu cầu khung doanh nghiệp/nhà trường và form Demand, phần chưa có backend hiển thị đang chờ; không bao gồm triển khai thay các workflow xác minh/hợp tác/Placement. Xem [bàn giao hai nhiệm vụ](../../../../docs/guides/T5_DEMAND_HANDOFF.md).

## Frontend

| File | Trách nhiệm |
|---|---|
| `index.ts` | Điểm import công khai cho `App.tsx` |
| `DemandWorkspace.tsx` | Kiểm grant và chọn doanh nghiệp; đổi scope tạo lại trạng thái màn hình |
| `demand/ScopedDemands.tsx` | Tải danh sách, phân trang, mở/đóng editor, cập nhật sau lưu |
| `demand/DemandEditor.tsx` | Form tạo/sửa, lưu, retry, lỗi field, bảo vệ dữ liệu chưa lưu |
| `demand/SkillFields.tsx` | Nhóm kỹ năng bắt buộc/ưu tiên |
| `demand/form.ts` | Dữ liệu nhập, khởi tạo form và chuyển thành DemandInput |
| `demand/errors.ts` | Thông báo lỗi nghiệp vụ Demand |
| `api.ts`, `types.ts` | API và DTO của Demand; dùng transport `src/api.ts` chung |
| `SchoolWorkspace.tsx` | Khung màn hình nhà trường |
| `navigation.ts` | Giao tiếp với shell khi rời bản nhập |
| `styles.css` | Style có prefix `cooperation-`, tránh ảnh hưởng module khác |

Tính năng T5 tiếp theo nên có thư mục riêng như `verification/`, `cooperation-requests/`. Khi chúng có API/DTO riêng, đặt ngay trong thư mục tính năng; không thêm tất cả vào API/type của Demand. Component chỉ dùng trong T5 giữ ở T5; component dùng chung toàn sản phẩm mới đưa lên cấp `src` sau review. `App.tsx` chỉ ghép màn hình và điều hướng, không chứa form hay nghiệp vụ T5.

T2 phát triển tại `modules/student`, T3 tại `modules/internship`, T4 tại `modules/matching`. Không tạo thư mục rỗng thay cho tính năng chưa triển khai. Không import component nội bộ của team khác để truy cập dữ liệu; dùng API/contract được công bố.

## Backend tương ứng

`src/main/java/vn/utbridora/auth/modules/cooperation` đã chia:

- `api`: controller và chuyển lỗi HTTP.
- `application`: use case, quyền, transaction, idempotency và audit.
- `domain`: DTO/value, validator, hash/version, lỗi nghiệp vụ.
- `persistence`: lưu Demand, revision, command và đọc kỳ.
- `integration`: kết nối taxonomy T4, kỳ Dev10, catalog T2 và snapshot BC31.

Giữ cấu trúc này; `api/application/domain/persistence` tương ứng vai trò controller/service/entity/repository trong guide, không cần đổi tên chỉ để giống ví dụ. Dữ liệu kỹ năng thuộc module `taxonomy` của T4. T5 gọi `CanonicalSkillPort`, không truy cập repository T4 trực tiếp.

## Trạng thái ghép Dev11 — 06/11/2026

Demand DRAFT, criteria, version/snapshot, guard và audit đã có code. Đã lấy `Term`, `TermStatus`, `TermRepository` từ nhánh Dev10 `feature/t5-term-verification` (7353971); chỉ tách DDL bảng `terms` thành V11 vì V8 của checkout đã dành cho Demand. Không copy migration V8 trùng version hoặc nhận thay workflow xác minh của Dev10. Khi hợp nhất toàn nhánh Dev10 cần điều phối migration để không tạo `terms` hai lần.

`TermAcademicReferences` kiểm kỳ ACTIVE và đúng trường, rồi gọi `DemandMajorCatalogPort`. Catalog phải trả đúng school/term, version hiện tại và tập mã ngành ACTIVE chuẩn trong cùng snapshot. Provider cần giữ tính nhất quán đến khi transaction Demand hoàn tất (ví dụ khóa catalog version cùng cơ chế mutation của owner). Thiếu provider hoặc nguồn không sẵn sàng trả 503; phiên bản thay đổi trả 409; mã không hợp lệ trả 422. Không tạo danh mục ngành production thay T2.

**Còn phụ thuộc:** nguồn ngành/version chính thức của T2 (Dev04 theo workbook R12), consumer T4/kiểu context dùng chung cho BC31, review tích hợp và QA05. Test dùng fixture catalog ngành không thay thế các bước này. UI tra cứu tên trường/kỳ/ngành vẫn cần API đọc được phân quyền; nhập mã thủ công hiện tại chỉ là giao diện thô.
