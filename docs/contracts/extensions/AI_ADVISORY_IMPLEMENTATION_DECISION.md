# Quyết định triển khai lượt 3 ngày 21/11/2026

Người dùng chọn nội dung “Tiếp theo là lượt 3: triển khai backend, API và giao diện gợi ý theo đặc tả này…” và yêu cầu “triển khai giúp tôi”. Đây là căn cứ triển khai extension ai-advisory-v1 đã bàn giao ở lượt 2. Không yêu cầu người dùng duyệt lại SRS v1.2.4 hoặc Contract v1.1 đã duyệt.

Phạm vi được giao: code owner ports/snapshot, quyền school-only, API/job/ranking, giao diện và recipe Docker cho môi trường riêng; mặc định OFF, bảo toàn canonical và enterprise release. Chất lượng model, full MySQL/ClamAV/browser runtime, pilot và nghiệm thu vẫn cần bằng chứng/phạm vi riêng; không ghi thay sign-off của T1/T2/T5/QA/nhà trường. Các ngưỡng quality/pilot trong EVALUATION_PLAN.md vẫn là đề xuất cần chốt trước đo, không tự coi đã nghiệm thu.

Giữ nguyên bytes package đặc tả lượt 2 tại `ai-advisory-v1/`; `source-map.json` là snapshot khảo sát trước triển khai, không hash source hiện tại. Hash manifest/package chính xác của quyết định này được lưu riêng trong checkpoint `phase3/IMPLEMENTATION_AUTHORIZATION.json`. API/new audit/capability được đăng ký riêng theo extension, không sửa frozen SRS/Contract; canonical bundle chỉ refresh source provenance khi kiểm chứng.

Không commit/push/merge/PR/deploy, chạy Docker shared, đổi DB demo, bật flag hoặc đổi Plan nghiệm thu từ quyết định triển khai code này.
