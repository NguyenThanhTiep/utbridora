# Tài liệu UT.Bridora

Mục lục tài liệu dùng chung cho năm team. Cập nhật cách sắp xếp ngày 04/11/2026; việc di chuyển tài liệu không thay đổi yêu cầu nghiệp vụ, mã nguồn hoặc kết quả kiểm thử.

## Đọc từ đâu

| Nhu cầu | Tài liệu bắt đầu |
|---|---|
| Hiểu yêu cầu toàn hệ thống | [SRS tổng thể v1.2.4](srs/UTBridora_SRS_Tong_The_v1.2.4.docx) và [trạng thái SRS](srs/README.md) |
| Thống nhất giao điểm giữa các team | [Contract Pack B01–B06 v1.0 Candidate](contracts/UTBridora_Contract_Pack_B01_B06_v1.0_Candidate.md) và [quy tắc sử dụng contract](contracts/README.md) |
| Rà soát kỹ thuật T1-01 và schema | [Review T1-01](contracts/T1_01_TECHNICAL_REVIEW.md), [v1.1 Candidate](contracts/UTBridora_Contract_Pack_B01_B06_v1.1_Candidate.md), [checker DEV](../scripts/contracts/README.md) |
| Biết source hiện đã có gì | [Trạng thái triển khai](status/IMPLEMENTATION_STATUS.md) và [báo cáo kiểm thử](testing/TEST_REPORT.md) |
| Xem kế hoạch và phân công công việc | [Workbook kế hoạch UT.Bridora](plans/UTBridora_Plan.xlsx) |
| Nhận kết quả tuần 2 và tiếp tục tuần 3 | [T2](handoffs/T2_TUAN2_BAN_GIAO_TUAN3.md), [T3](handoffs/T3_TUAN2_BAN_GIAO_TUAN3.md), [T4](handoffs/T4_TUAN2_BAN_GIAO_TUAN3.md), [T5](handoffs/T5_TUAN2_BAN_GIAO_TUAN3.md) |
| Đưa module của team vào repository | [Hướng dẫn BE/FE cho các team và AI](guides/HUONG_DAN_PHAT_TRIEN_BE_FE_CHO_CAC_TEAM.md) |
| Làm việc với GitHub | [Nhánh, commit theo chức năng, PR, review và xử lý xung đột](guides/HUONG_DAN_LAM_VIEC_VOI_GITHUB.md) |
| Chạy local hoặc chuẩn bị staging | [Hướng dẫn triển khai](guides/DEPLOYMENT_GUIDE.md) |
| Xem sơ đồ | [Use case, activity và cách render](diagrams/README.md) |

**Phân biệt trạng thái:** người dùng đã chốt Contract Pack v1.0 ngày 05/11/2026, không chờ vòng ký riêng T2–T5; xem [quyết định và hash phiên bản](contracts/README.md#quyết-định-chốt-contract--05112026). SRS và nội dung Candidate gốc giữ nguyên để truy vết; rà soát kỹ thuật vẫn còn, việc chốt không đồng nghĩa chức năng mới đã triển khai hoặc nghiệm thu. Tài liệu trong `core` mô tả nền tảng đã có tại thời điểm ghi nhận; báo cáo trong `testing` chỉ chứng minh các lượt chạy được ghi rõ ngày. Kết quả test DEV và xác nhận Tester được theo dõi riêng.

## Cấu trúc thư mục

```text
docs/
├── README.md                  # Mục lục và cách sử dụng
├── openapi.json               # OpenAPI core; giữ đường dẫn công bố hiện có
├── srs/                       # SRS tổng thể
├── contracts/                 # Contract liên team và ma trận quyền
├── core/                      # Kiến trúc, API, database, bảo mật của core
├── guides/                    # Hướng dẫn tích hợp, chạy và làm việc
├── handoffs/                  # Bàn giao kết quả tuần 2 và hướng dẫn tuần 3 theo team
├── plans/                     # Workbook UTBridora_Plan.xlsx do nhóm cung cấp
├── status/                    # Trạng thái triển khai và bàn giao
├── testing/                   # Báo cáo và evidence kiểm thử
├── diagrams/                  # Mã nguồn sơ đồ và ảnh đã render
├── reviews/                   # Báo cáo kiểm định, đề xuất và checkpoint
└── archive/                    # Khảo sát/lịch sử UI và bộ thiết kế t1-baseline
```

## Tài liệu core

- [Kiến trúc](core/ARCHITECTURE.md).
- [API nền tảng](core/API_DOCUMENTATION.md) và [OpenAPI 3.1](openapi.json).
- [Database và migration](core/DATABASE.md).
- [Phân quyền](core/AUTHORIZATION.md).
- [Vòng đời tài khoản](core/ACCOUNT_LIFECYCLE.md).
- [Audit Log](core/AUDIT_LOG.md).

## Hướng dẫn làm việc

- [Phát triển backend, frontend và migration cho các team và AI](guides/HUONG_DAN_PHAT_TRIEN_BE_FE_CHO_CAC_TEAM.md).
- [Chạy local, chuẩn bị staging và backup](guides/DEPLOYMENT_GUIDE.md).
- [Làm việc với GitHub: nhánh, commit, pull request, review và xử lý xung đột](guides/HUONG_DAN_LAM_VIEC_VOI_GITHUB.md).
- [T3-03: khung UI HR/Mentor, nhóm thực tập và điểm chờ tích hợp](guides/T3_03_HR_MENTOR_WORKSPACE.md).
- [Docker local: tạo dữ liệu mẫu và hướng dẫn demo Student/HR/Mentor cho dev](guides/LOCAL_DEMO.md).
- [Tuần 2: luồng runtime, API và cách kiểm tra trên môi trường riêng](guides/WEEK2_RUNTIME.md).
- [T1-04-UI: cấp và thu hồi quyền cán bộ theo trường/kỳ](guides/T1_04_UI_SCHOOL_DELEGATIONS.md).

## Bàn giao tuần 2 → tuần 3

- [T2 — Sinh viên: profile/skills/consent → Opportunity/Preference và UI](handoffs/T2_TUAN2_BAN_GIAO_TUAN3.md).
- [T3 — Thực tập: assignment/Task/calendar → Submission/Review/Weekly Report](handoffs/T3_TUAN2_BAN_GIAO_TUAN3.md).
- [T4 — Matching: classifier/MatchRun → protected aggregate và UI kết quả trường](handoffs/T4_TUAN2_BAN_GIAO_TUAN3.md).
- [T5 — Hợp tác: Cooperation/proposal/invitation → share/enterprise decision/Placement](handoffs/T5_TUAN2_BAN_GIAO_TUAN3.md).

Code, tests và bốn tài liệu bàn giao nằm trong cùng bản phát hành tuần 2 trên main. Mỗi tài liệu chỉ rõ phần đã triển khai, dependency còn thiếu, thứ tự làm và ca QA; không thay human QA/Lead signoff. Evidence DEV giữ nguyên trạng thái tại thời điểm chạy trước commit. Docker dùng hằng ngày chưa được triển khai lại.

## Trạng thái và kiểm thử

- [Trạng thái triển khai](status/IMPLEMENTATION_STATUS.md).
- [Checklist bàn giao](status/HANDOFF_CHECKLIST.md).
- [Lịch sử nâng cấp giao diện và kiểm chứng đã ghi nhận](archive/UI_UPGRADE.md).
- [Báo cáo kiểm thử](testing/TEST_REPORT.md).
- [Tuần 2: task, implementation, kiểm chứng DEV và giới hạn nghiệm thu](testing/WEEK2_ACCEPTANCE.md).
- [T3-03: kiểm thử DEV và daily report](testing/T3_03_TEST_REPORT.md).
- [T3-03: bàn giao QA03, phạm vi kiểm thử và dependency](testing/T3_03_QA_HANDOFF.md).
- Evidence gốc: [backend và tích hợp](testing/evidence/TEST_EVIDENCE.json), [giao diện](testing/evidence/UI_TEST_EVIDENCE.json), [trước push](testing/evidence/PREPUSH_TEST_EVIDENCE.json).

Evidence JSON được giữ nguyên nội dung. Đường dẫn source/log hoặc checksum bên trong phản ánh snapshot lúc chạy, không phải kết quả được chạy lại khi tổ chức tài liệu.

## Kiểm định và lịch sử

- [Danh mục các lần kiểm định và đề xuất](reviews/README.md).
- [Mục lục tài liệu lưu trữ](archive/README.md): lịch sử UI, khảo sát đồng bộ dữ liệu và thiết kế ban đầu.
- [Bộ thiết kế nền tảng cũ](archive/t1-baseline/README.md).

Tài liệu đánh số `00`–`10`, `API_EXAMPLES`, `HANDOFF_T1`, `STATE_DIAGRAMS` và `TEST_PLAN` được chuyển vào `archive/t1-baseline`; vẫn giữ để truy vết. Trạng thái triển khai phải đọc từ `status` và `testing`, không suy từ các câu “chưa triển khai” của baseline cũ.

## Quy tắc cập nhật

1. SRS đặt trong `srs`; contract đặt trong `contracts`; nhận xét và kết quả kiểm định đặt trong `reviews`.
2. Giữ một bản nội dung cho mỗi tài liệu. Mục lục liên kết đến bản đó, không sao chép toàn bộ sang thư mục khác.
3. Contract Candidate đang được SRS tham chiếu bằng SHA-256. Muốn sửa nội dung phải phát hành phiên bản và cập nhật tham chiếu/signoff tương ứng; việc sắp xếp này giữ nguyên hai file.
4. Giữ `docs/openapi.json` và `docs/contracts/permission_matrix.csv` ở đường dẫn hiện có. OpenAPI core hiện hữu và contract tính năng mới phải được phân biệt rõ trạng thái.
5. Khi di chuyển tài liệu, cập nhật cả liên kết Markdown và đường dẫn trong hướng dẫn. Snapshot kiểm định/contract đã đóng băng được giữ nguyên; dùng mục lục hoặc liên kết tương thích để dẫn đến vị trí mới.
