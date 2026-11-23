# Đặc tả AI xếp hạng gợi ý cho nhà trường

**Phiên bản:** ai-advisory-v1, ngày 21/11/2026. **Trạng thái:** đặc tả lượt 2 để review và làm căn cứ lượt 3; chưa có runtime/API/UI tương ứng, chưa nghiệm thu chất lượng AI.

Người dùng đã yêu cầu đưa AI vào danh sách gợi ý và triển khai lượt 2, tức giai đoạn 5. Phạm vi lần này là hoàn thành đặc tả cụ thể. Quyết định đó không được ghi thay thành chữ ký T1/T2/T5, duyệt schema mới, hoặc kết quả QA/nhà trường. Hai tài liệu baseline đã duyệt vẫn giữ nguyên: SRS v1.2.4 SHA-256 `92efaaec4efb66bfe0fa69e5de6a706476f93f58f808575982b9d95662438bd4` và Contract Pack v1.1 SHA-256 `805b2db30c7a9d4acd76c3d4ed93eeefa149d121a9848f846441294101c68012`.

## Phạm vi và quyết định thiết kế

Strict Matching tạo kết quả canonical như hiện nay. Một lệnh riêng tạo danh sách gợi ý AI chỉ từ `MATCH` của school run. AI được đổi thứ tự trong danh sách mới; cán bộ vẫn chọn và gọi endpoint đề xuất hiện hành. Enterprise matching tiếp tục chỉ trả protected aggregate, không có quyền gọi API advisory.

| Nội dung | Quyết định của đặc tả v1 |
|---|---|
| Tạo advisory | Người dùng nhà trường chủ động yêu cầu sau school run COMPLETED; không tự tạo trong transaction canonical |
| Population | Toàn bộ MATCH trong run; không top-K trước strict, không xếp từng trang độc lập |
| Dữ liệu | Nội dung dự án/kỹ năng do T2 sở hữu; title/description nhu cầu do T5 sở hữu; capture qua port mới, version chính xác |
| Quyền | Thêm hai capability school-only; đồng thời giữ delegation và quyền đọc các trường đầu vào hiện hành |
| Policy | Cosine làm tròn sáu chữ số giảm dần; hòa theo canonical; hồ sơ không đủ nội dung nằm sau với rank/score null |
| Công bố | Atomic sau khi hoàn tất toàn bộ tập; lỗi một batch không tạo READY một phần |
| Quyết định đề xuất | Không có endpoint đề xuất mới; guard/quota/consent/sharing hiện hành vẫn áp dụng |
| Thời hạn | Snapshot/advisory tối đa 24 giờ từ lúc tạo, kiểm nguồn/quyền ở mỗi lần đọc; cache không gia hạn |
| Bật tính năng | Default OFF; chỉ môi trường riêng trong lượt 3–4, pilot theo phạm vi được giao |

## Bộ tài liệu

- [Khảo sát nguồn và hợp đồng owner ports](DATA_PORTS.md)
- [Chính sách xếp hạng và UI](RANKING_POLICY.md)
- [REST API và lỗi](API.md)
- [Job, lưu trữ, thu hồi và retention](STORAGE_LIFECYCLE.md)
- [Đánh giá chất lượng và điều kiện đạt](EVALUATION_PLAN.md)
- [Bàn giao triển khai và ma trận kiểm chứng](IMPLEMENTATION_HANDOFF.md)
- [JSON Schema đóng](schemas.json), [ví dụ hợp lệ/không hợp lệ](examples.json)
- [Quyền mở rộng](permissions.csv), [audit mở rộng](audit.csv)

Schema/example kiểm tra hình dạng hợp đồng và các invariant liên trường được nêu trong checker. Chúng không chứng minh code đáp ứng API, model tốt hơn baseline hoặc các team đã ký duyệt.

## Đối chiếu source và phần cần quyết định

Base HEAD `c58a8ee896381e3d50f237b3c4e08ec715a2d836`; worktree có code lượt 1 chưa commit. `MatchingService.details` chỉ xếp MATCH theo fitScore rồi studentUserId; `StudentProfileSnapshotPort.ProfileSnapshot` chưa chứa văn bản dự án/note. `DemandCriteriaSnapshot` chưa chứa JD, dù DemandInput đã có description. `ProfileAccessFacade` hiện chỉ chấp nhận SCHOOL_REVIEW/INTERNSHIP_SELECTION; việc thêm purpose SCHOOL_AI_ADVISORY dưới đây thuộc lượt 3.

Review extension này cần giải quyết một lần: hợp đồng owner ports/purpose và capability mới; các giới hạn/TTL; cách xử lý thiếu nội dung; các ngưỡng đánh giá đề xuất trong evaluation plan. Không hỏi lại phê duyệt hai tài liệu baseline. Nếu scope triển khai sau đã được người dùng xác nhận đủ rõ, tiếp tục phần được giao; không biến review kỹ thuật thành yêu cầu xin phép lại từng file.

Giữ nguyên SRS/Contract đóng băng, schema/audit/permission canonical v1.1 và Plan.xlsx. Không tự đánh dấu giai đoạn 6–8 đã hoàn thành từ bộ đặc tả này.
