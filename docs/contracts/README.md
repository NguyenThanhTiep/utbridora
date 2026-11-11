# Contract và ma trận quyền

| Tài liệu | Mục đích | Trạng thái |
|---|---|---|
| [Contract Pack B01–B06 v1.1 Candidate](UTBridora_Contract_Pack_B01_B06_v1.1_Candidate.md) | Bản hiệu chỉnh kỹ thuật, Data Dictionary và schema kiểm được bằng máy | DEV review hoàn tất trong phạm vi báo cáo; diff v1.1 chưa tự được phê duyệt |
| [Contract Pack B01–B06 v1.0 Candidate](UTBridora_Contract_Pack_B01_B06_v1.0_Candidate.md) | DTO/port, API, permission, audit, import và mail recovery liên team | Người dùng chốt ngày 05/11/2026; rà soát kỹ thuật còn tiếp tục, xem quyết định bên dưới |
| [Permission matrix](permission_matrix.csv) | Ma trận quyền nền tảng | CSV còn nhãn DRAFT_NEEDS_SIGNOFF; cần đối chiếu với registry D của Contract Pack đã chốt |
| [OpenAPI core](../openapi.json) | API core đã được mô tả theo source hiện hữu | Tách biệt với các API mới trong Candidate |
| [Báo cáo T1-01](T1_01_TECHNICAL_REVIEW.md) | Phát hiện/diff, mapping code, truy vết và checklist từng team | 868/868 kiểm tra DEV PASS; runtime module mới và Tester theo dõi riêng |
| [Bundle schema](v1.1/schemas.json), [manifest](v1.1/manifest.json), [ví dụ](v1.1/examples.json) | DTO/Input/Value, registry và locator ví dụ | 374 schema, 62 port/consumer, 69 ví dụ |
| [Permission CSV v1.1](v1.1/permission_matrix.csv), [Audit CSV v1.1](v1.1/audit_registry.csv) | Bản xuất từ registry D/E để team đối chiếu | 18+33 quyền, 30+63 action; không thay runtime catalog/enum |

Contract Candidate đi kèm [SRS v1.2.4](../srs/UTBridora_SRS_Tong_The_v1.2.4.docx). SHA-256 đang được SRS viện dẫn:

```text
9c62f229908f3c66f300455e33bdde6aef127f8e6edd99b286def0687ff9f2ef
```

## Quyết định chốt Contract — 05/11/2026

- **Người xác nhận:** người dùng trong phiên làm việc với Codex, qua yêu cầu “cái này tôi chốt luôn” đối với mục ký nhận contract T2–T5.
- **Nội dung:** người dùng chốt sử dụng Contract Pack B01–B06 v1.0 có SHA-256 nêu trên làm căn cứ kỹ thuật liên team. Theo chỉ đạo này, không chờ vòng ký riêng của T2–T5 để tiếp tục công việc T1-01.
- **Phạm vi xác nhận:** quyết định của người dùng; không ghi nhận thay rằng đại diện T2–T5 đã review hoặc ký. Quyết định này không phải nghiệm thu chức năng, xác nhận QA PASS hoặc phê duyệt chính sách UTH/dữ liệu thật.
- **Cập nhật phần kỹ thuật:** đã xuất v1.1 với Data Dictionary bổ sung, schema/ví dụ và kiểm tra registry/tương thích core; xem báo cáo T1-01. Bản v1.1 là diff riêng để review, không áp dụng quyết định v1.0 cho nội dung sửa sau đó. Những phần implementation/module mới vẫn có trạng thái riêng.
- **Truy vết phiên bản:** giữ nguyên nội dung và tên file Candidate v1.0 vì SRS đang viện dẫn hash của file này. Các nhãn PROPOSED/PENDING bên trong là trạng thái tại checkpoint 03/11/2026; riêng việc chờ ký liên team được thay bằng quyết định ngày 05/11/2026 tại đây. Không diễn giải quyết định này thành việc đã hoàn tất các mục rà soát kỹ thuật ở checkpoint.
- **Nếu sửa nội dung contract:** phát hành phiên bản mới, ghi diff so với bản đã chốt và cập nhật tham chiếu tương ứng. Quyết định hôm nay gắn với đúng hash v1.0, không tự áp dụng cho nội dung sửa sau đó.

Giữ phân biệt contract đã được người dùng chốt, API đã triển khai và test đã thực thi. Các schema mới chưa tự trở thành API có trong source; consumer contract tests và kiểm tra tích hợp vẫn phải có kết quả thực tế. Trạng thái Tester được theo dõi riêng với kết quả test DEV.

Lệnh build/verify và cách cập nhật bundle: [hướng dẫn checker DEV](../../scripts/contracts/README.md). CI đã có job contracts; kết quả GitHub của thay đổi mới chưa được xác minh vì chưa commit/push.

Nguồn checkpoint được lưu tại [bản đề xuất B01–B06](../reviews/UTBridora_SRS_v1.2.3_Blocker_Resolution_Proposal.md). File [điều hướng báo cáo v1.2.3](UTBridora_SRS_v1.2.3_Final_Verification.md) chỉ duy trì liên kết tương đối vốn có trong Candidate; không sao chép nội dung kiểm định.

[Về mục lục tài liệu](../README.md)
