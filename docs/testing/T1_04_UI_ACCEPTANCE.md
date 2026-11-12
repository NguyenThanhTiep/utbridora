# T1-04-UI: kết quả kiểm chứng DEV

Ngày 12/11/2026. Base `origin/main` tại `9ba090d`. Phạm vi bổ sung tuần 2: giao diện cấp/thu hồi quyền cán bộ theo trường/kỳ, dùng lại mutation CP-AUTHZ1 và bổ sung API đọc đúng phạm vi.

## Kết quả triển khai

- Menu **Phân công quyền cán bộ** cho ADMIN có `membership:admin:write` và SCHOOL_STAFF có role ceiling quản lý; quyền nghiệp vụ thực tế vẫn phải qua delegation hiện hành.
- Chọn trường/kỳ, tìm cán bộ thuộc trường, chọn quyền/mask/thời hạn/căn cứ; danh sách, chi tiết, quyền còn hiệu lực và thu hồi theo version.
- Tách bootstrap DEMO của admin khỏi grant nghiệp vụ từ một parent đủ quyền; không cấp quyền hồ sơ sinh viên cho admin, không gộp quyền từ nhiều parent.
- API đọc không trả credentials hoặc dữ liệu sinh viên; ngoài scope bị chặn. Mutation giữ nguyên service T1, CSRF, OperationLedger, audit và invalidation phiên trong transaction.
- Task `T1-04-UI` ở `CAP NHAT CHUNG!92`, DEV01/QA01, trạng thái **Chờ QA / Chưa test / Chưa duyệt / Chưa nghiệm thu SRS**. Bổ sung ghi chú truy vết R07 và công thức tổng quan; giữ nguyên nội dung/trạng thái 87 task cũ.

## Kiểm chứng đã quan sát

| Kiểm tra | Kết quả |
|---|---|
| Frontend production build và typecheck tests | PASS |
| Unit suite trong lượt verify đầu | 485 PASS, 0 fail/error/skip |
| Hồi quy unit security ở các lượt targeted | AuthorizationPolicyTest 7 PASS, DemandSecurityErrorsTest 2 PASS |
| SchoolDelegationManagementIT, MySQL riêng + HTTP/session/CSRF | 2 PASS, gồm một ca chạy Chromium thật |
| Chromium riêng task này | 1 PASS: admin cấp root, trường cấp child, cán bộ đọc đề nghị và reload, admin thu hồi parent, phiên cũ 401 và phiên mới 404; mobile 390px không tràn ngang |
| CorePlatformIT | 50 PASS, 0 fail/error/skip; Chromium Core 83 PASS, 5 skip theo điều kiện |
| Week2WorkflowIT | 1 PASS; Chromium tuần 2 riêng 1 PASS |
| Contract DEV checker | 868/868 PASS; bundle portability 1 PASS; audit registry 3 PASS |
| Workbook | 88 task, tuần 2 có 19; bộ lọc/validation/conditional formatting mở rộng đến dòng 92; công thức ĐẠT vẫn đòi test/review và không tự nghiệm thu SRS |

Năm skip trong lượt Core là các suite live cần fixture/môi trường riêng, gồm suite delegation mới; suite delegation và tuần 2 đã được chạy riêng như các dòng trên. Không cộng các lượt targeted thành một full verify xanh.

`SchoolDelegationManagementIT` còn kiểm: thiếu CSRF; bootstrap không có DEMO-; tự cấp; sai trường/kỳ; cán bộ không có membership đúng trường; vượt quyền/thời hạn parent; replay cùng key/body và đổi body cùng key; stale revoke; admin không thu hồi child do cán bộ khác cấp; parent revoke làm quyền con rỗng dù row con còn active. Ràng buộc audit cố ý gây lỗi chỉ tồn tại trong MySQL thử; lỗi rollback delegation và securityVersion, sau gỡ ràng buộc cùng quyền được cấp thành công.

Lượt đầu thất bại do fixture thiếu SecurityContext khi gọi MembershipService và locator combobox chưa phù hợp. Các lượt sau phát hiện thời hạn child tạo sau parent nên vượt hạn vài giây; đã chỉnh fixture. Test audit dùng HTTP 500 theo nhánh lỗi thực tế. Một lượt browser tuần 2 chưa tải được danh sách; thêm kiểm tra API đọc trước thao tác UI và lượt chạy lại PASS. Kiểm tra mobile sau khi tải dữ liệu xong bắt được bảng làm tràn trang; đã giới hạn cuộn ngang trong bảng và ẩn nhãn phụ dành cho trình đọc màn hình. Không nới scope guard hoặc bỏ assertion nghiệp vụ để làm xanh.

Evidence có số liệu/hash/phạm vi ở [receipt](evidence/T1_04_UI_VERIFICATION.json). Lệnh tái hiện và hướng dẫn vận hành ở [guide](../guides/T1_04_UI_SCHOOL_DELEGATIONS.md). JUnit/browser reports và ảnh sinh trong `target`; CI xuất report theo workflow hiện có.

## Giới hạn và môi trường

Đây là kiểm chứng DEV tự động; chưa có nghiệm thu QA01/Lead. Không thay nội dung SRS/Contract Pack đã chốt hoặc trạng thái các task lịch sử. Không tạo migration mới, không chạy seed/importer vào DB chung, không đổi tài khoản/mật khẩu/volume và không triển khai lại Docker 8088. Tài khoản trường trên database dùng hằng ngày chỉ có thêm quyền khi người có thẩm quyền cấp qua luồng vận hành.

Local main cũ chứa lịch sử tài liệu riêng được giữ ở `backup/local-main-before-t1-04-ui-20261112` và Git bundle ngoài repo. Stash `before-switch-t5-20261112-b2525d01` giữ nguyên; không đưa lịch sử riêng hoặc stash vào thay đổi này.
