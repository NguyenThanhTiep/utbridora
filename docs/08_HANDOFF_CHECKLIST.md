> Tài liệu baseline được giữ để truy vết thiết kế ban đầu. Code và kết quả kiểm thử sau triển khai ngày 29/10/2026 xem [HANDOFF_CHECKLIST.md](HANDOFF_CHECKLIST.md) và [báo cáo thực thi](TEST_REPORT.md); các câu “chưa triển khai/chưa test” bên dưới mô tả baseline, không phải trạng thái mới nhất.

# Checklist bàn giao T1 cho PM và T2–T5

## 1. Gói bàn giao

- [x] Nối với code Authentication #1, không thay đổi ID/role/session.
- [x] Authorization policy tổng quát + scope/permission + deny-default profile integration port.
- [x] Bảng phân quyền `contracts/permission_matrix.csv` dự thảo cho 4 vai trò.
- [x] SQL migration V3–V6 + metadata files + status history.
- [x] Sơ đồ account lifecycle và bảng ai được chuyển.
- [x] Audit writer và quy tắc event, request id, global paged audit read.
- [x] Tài liệu API, user stories, test plan.
- [ ] PM/T2/T3/T4/T5 duyệt quyền nghiệp vụ chi tiết.
- [ ] Build Maven trên JDK 21 có internet/dependency cache và chạy MySQL integration test.
- [ ] T2/T5 triển khai ProfileVisibilityPort hợp lệ.
- [ ] T3 xác định placement/mentor/evaluation access policies.
- [ ] Test môi trường stage HTTPS reverse proxy + SameSite/CSRF.
- [ ] Trước production: locking concurrent last-admin; upload/AV; monitoring/rate limits; tamper-resistant audit; retention & DPA; MFA admin.

## 2. Nội dung demo 10 phút thứ Bảy

| Phút | Nội dung | Bằng chứng |
|---|---|---|
| 0–1 | 4 vai trò, 1 identity, membership theo tổ chức | sơ đồ/auth me |
| 1–3 | Tạo doanh nghiệp A, login A, thử truy cập B | 403 và scope guard |
| 3–4 | Trang tổng hợp permission matrix | CSV |
| 4–5 | Giải thích schema V1–V6, FK/index | migration/ERD |
| 5–7 | Khóa/mở account, revoke phiên | 401 trên cookie cũ |
| 7–9 | Audit trail ai làm gì, request_id | API `/admin/audit-logs` |
| 9–10 | Điểm cần team khác chốt + demo limitations | handoff checklist |

Nếu build chưa hoàn thành trong lịch 2 ngày, trình bày mô phỏng bằng sơ đồ/API contract; **không nói chạy được backend khi chưa có bằng chứng**.

## 3. Ranh giới quyền sở hữu chính thức đề xuất

| Tài nguyên | Owner chính | T1 hỗ trợ |
|---|---|---|
| identity/users/session | T1 | toàn bộ |
| membership/permissions | T1 | chính |
| organization identity | T1 | ID + loại + status tổng quan |
| enterprise KYC/verification | T5 | adapter/identity/scope, không duyệt thật |
| student profiles/certificates | T2 | owner permission, file metadata |
| matching engine/catalog | T4 | authz aggregate, không expose rows |
| cooperation/placement/profile share | T5 | scoped auth, audit contract |
| task/report/evaluation | T3 | user/context, audit contract |
| audit infrastructure | T1 | event registry + access guard |

## 4. Câu hỏi chốt trong kênh chung

1. T2: Ai là owner hồ sơ SV? Trường nào được xem những field nào? Có quy tắc immutable snapshot khi gửi lời mời không?
2. T5: Share được quyết định bởi ai; có consent của SV không; share có expire/revoke thế nào? Ai xác thực tổ chức?
3. T3: Mentor nào có quyền thao tác trên Placement nào; đánh giá có `draft/final/published` không?
4. T4: Mức ẩn danh aggregate, dữ liệu thiếu, export số liệu; masking nếu mẫu quá nhỏ?
5. PM: Cơ chế phê duyệt thay đổi schema, permission matrix, thời hạn lưu log, production owner?
