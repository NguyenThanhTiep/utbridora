# T5-02/T5-03 – Demand: bàn giao sau tích hợp

Cập nhật 08/11/2026. Phạm vi: Demand DRAFT, tiêu chí/revision và frontend doanh nghiệp; các bước Cooperation/Placement thuộc task tiếp theo. Contract nền v1.0 và hiệu chỉnh v1.1 đã có; không chờ ký lại.

## Backend và nguồn dữ liệu

- `DemandService` dùng `OperationLedger` chung, bảng `operation_records`; không còn `demand_commands` riêng.
- Auth/account/securityVersion, membership và tổ chức VERIFIED được kiểm trước replay. Version và mutation/reference/audit nằm trong cùng transaction; audit lỗi rollback toàn bộ.
- Cùng key/payload replay kết quả trong 24 giờ; đổi payload trả `409 IDEMPOTENCY_KEY_REUSED`; hết cache trả `409 IDEMPOTENCY_RESPONSE_EXPIRED`, không tạo lại effect.
- `TermAcademicReferences` đọc đúng registry Term T5, kiểm school/ACTIVE và catalog version từ `DemandMajorCatalogPort` của T2.
- `DemandTaxonomyReferences` thuộc T4 kiểm taxonomy version và mã kỹ năng ACTIVE từ database; không dùng danh mục tĩnh trong production.
- Chưa tìm thấy provider catalog học vụ T2 có version trong các nhánh đã kiểm tra. Khi cần nguồn này, production trả `503 DEPENDENCY_UNAVAILABLE`; đây là giới hạn tích hợp cụ thể, không phải contract chưa chốt hoặc ledger chưa bàn giao.

Công tắc triển khai là `app.t5.demand.enabled` / `T5_DEMAND_ENABLED`, mặc định true. Tắt công tắc trả `503 FEATURE_DISABLED` sau kiểm quyền. Các cờ `contract-approved` và demo-reference cũ không cấp quyền, không bật catalog mẫu.

## API và frontend

- POST `/api/v1/enterprise/demands`: `DemandInput` → 201 `DemandDto` DRAFT.
- PATCH `/api/v1/enterprise/demands/{id}`: `{expectedVersion,data}` → 200; owner/school/term không đổi; stale trả 409.
- GET cùng collection: `page,size,termId,state,includeDeleted` → Page chuẩn; mặc định 25, tối đa 100; sort createdAt DESC/id ASC.
- Mutation dùng session, CSRF, `X-Organization-Id` và UUID `Idempotency-Key`. ADMIN global không tự có quyền enterprise.
- Frontend giữ bản nhập và key khi kết quả save chưa rõ; đổi body tạo key mới. Không báo thành công khi backend từ chối hoặc dependency chưa sẵn sàng.
- `DemandCriteriaPort` hiện hỗ trợ EVALUATE cho enterprise owner; chưa nghiệm thu consumer Matching thật, NOMINATE hoặc Placement.

Mỗi edit tăng `version`; thay đổi nội dung tiêu chí canonical tăng `criteriaVersion`. Revision append theo `(demand_id,version)`. Các bước đề xuất/Placement sau này vẫn phải kiểm version, thời hạn và nguồn authoritative của workflow đó.

## Migration chính thức của bản tích hợp

V1–V7 giữ nguyên. V8 ledger chung; V9 TermReference; V10 roster/eligibility; V11 profile; V12–V14 taxonomy/quyền TAX; V15 audit metadata; V16 Demand.

Demand dùng `demands`, `demand_revisions`; replay dùng `operation_records`. FK `(term_id,school_org_id)` trỏ đúng Term T5. Không tạo registry Term hoặc identity thứ hai.

Các số V8/V9/V10 trên nhánh feature cũ là tạm. Database feature đã chạy số tạm phải dùng database phát triển riêng để thử bản tích hợp; không sửa checksum, repair hoặc clean database có dữ liệu dùng chung. Kiểm chứng migration chạy trên MySQL Testcontainers; chưa áp vào database local/staging của người dùng.

## Bằng chứng và giới hạn

`DemandIT` dùng HTTP/session/CSRF và MySQL thật, Term/Taxonomy/ledger thật; chỉ nguồn học vụ T2 là fixture test được đánh dấu. Chromium kiểm tạo, tải lại, sửa, tải lại qua API thật. Có ca riêng dùng production provider không fixture để xác minh thiếu catalog trả 503 và không ghi operation.

Bộ test còn kiểm replay/version, changed payload, expiry, audit rollback, concurrency, scope, term giả và taxonomy version cũ. Số PASS cuối cùng phải lấy từ lượt verify/CI gắn đúng commit tích hợp; báo cáo DEV cũ chỉ có giá trị lịch sử.
