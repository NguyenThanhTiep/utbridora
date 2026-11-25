# BE–FE lượt 2: nguyện vọng, lời mời và theo dõi sau bàn giao

Nền triển khai: lượt 1 `855be104112014107f8cdbedb90143842421e53f`. Không thay SRS/Contract Pack đã đóng băng, SQL hiện hữu, ranking Matching hoặc cấu hình AI/scanner/worker mặc định. DEV verification riêng với nghiệm thu QA/Lead/nhà trường.

## Luồng nhà trường rà soát nguyện vọng

Mở **Rà soát nguyện vọng**, chọn trường–kỳ từ `/school/preferences/scopes`, lọc trạng thái và xem chi tiết. Quyền `student:preference:school:review` cần chuỗi ủy nhiệm hiện hành; role chỉ giúp khám phá menu. Danh sách dùng `X-Organization-Id` và kỳ, phân trang máy chủ. Chi tiết chỉ hiển thị ID tham chiếu và nội dung ý định do API cung cấp, không tự truy vấn tên/email/hồ sơ sinh viên.

Chỉ `SUBMITTED` có thể rà soát, cần mã lý do và xác nhận. POST review dùng phiên bản đọc mới nhất, CSRF và Idempotency-Key. Kết quả REVIEWED không tự tạo đề xuất, invitation, Placement hoặc thay Matching. Xung đột 409 khóa gửi lại cho tới khi tải phiên bản hiện hành; kết quả chưa chắc chắn giữ nguyên body/key và khóa đổi phạm vi. Mất quyền hoặc chuyển tài khoản/phạm vi hủy đọc và xóa dữ liệu màn hình.

## HR quản lý lời mời Mentor đã gửi

Mở **Lời mời Mentor đã gửi**, chọn phân công HR toàn trường–doanh nghiệp–kỳ đang hiệu lực; HR giới hạn nhóm/Case không được chọn làm người mời. GET sent/sent detail chỉ trả lời mời do chính HR đó gửi trong phạm vi máy chủ xác minh. Có phân trang, thời hạn, trạng thái lưu và phiên bản; không trả token/link kích hoạt, mật khẩu hoặc dữ liệu của issuer khác.

Gửi mới/chủ động chuẩn bị gửi lại dùng POST issue hiện hữu; nhóm lấy qua API đọc đã kiểm quyền, có tải thêm trang, nhập lại căn cứ và xác nhận thao tác gửi. Backend kiểm claim/reissue/cooldown/trạng thái; UI không tự kích hoạt hoặc cấp Mentor. Trạng thái lưu không bị sửa thành EXPIRED cục bộ: UI chỉ ghi chú đã qua thời hạn. Thu hồi dùng phiên bản và mã lý do cùng xác nhận ảnh hưởng; ACCEPTED có thể kéo theo thu hồi các phân công phát sinh theo quy tắc máy chủ. Xử lý 409/uncertain retry giống review. Không coi thu hồi invitation là xóa tài khoản/membership.

## Adapter roster theo kỳ và trường dữ liệu

`SchoolRosterVisibilityAdapter` là projection chuyên biệt cho `/school/student-rosters`. Hợp đồng boolean `ProfileVisibilityPort` không mang kỳ/mặt nạ dữ liệu, nên không thể dùng để mở cả hồ sơ khi chỉ cần roster. Adapter toàn cục fail-closed vẫn giữ nguyên cho các caller legacy; enterprise vẫn phải qua canonical ProfileAccessPort/share/consent, không có quyền mới từ roster.

Adapter đọc các scope `student_profile:school:read` của actor hiện hành, kiểm đúng trường và kỳ ACTIVE, toàn bộ chuỗi ủy nhiệm/role ceiling/account/membership, rồi áp dụng `profileFields`. Chỉ các dòng đã liên kết roster–eligibility–user–school–term authoritative và sinh viên ACTIVE/có membership STUDENT còn hiệu lực được trả. Hàng pending/unbound không xuất hiện ở roster; theo dõi tại import job theo quyền riêng. Kỳ hiển thị lấy từ eligibility/Term authoritative thay cho `roster.term_code` legacy. Các trường `studentCode`, `displayName`→fullName, `email`, `majorCode` bị null khi không có trong mask; chỉ cấp skills/CV không làm lộ dòng roster. ID tham chiếu, mã kỳ và createdAt là metadata của projection. TermCode filter hiện hữu vẫn giữ; mặc định chỉ các kỳ ACTIVE được ủy nhiệm, không mở lịch sử kỳ khác.

Đọc trong transaction thường vì MySQL dùng FOR SHARE để giữ authority/row tới khi dựng xong projection. Private/no-store giữ nguyên. Không có thay đổi dữ liệu, consent hoặc import/account provisioning khi đọc roster. Quyền đọc roster của trường dựa ủy nhiệm SCHOOL_REVIEW; consent của sinh viên là điều kiện riêng cho chia sẻ doanh nghiệp, không bị bỏ qua hoặc dùng thay ủy nhiệm trường.

## T5 theo dõi tiếp nhận → T3

Trong chi tiết bàn giao, ba mục chính sách điểm/chất lượng phối hợp/đóng Case mở `/workspace/cases/{caseId}?followup=...`. Link chỉ là tham chiếu; T3 đọc Case và tái kiểm quyền. CaseEntry lấy school/term/enterprise từ Case máy chủ, ghép đúng grant SCHOOL_STAFF và mở ScopeOperations hiện hữu. Trường có thể quản lý kết quả/chính sách kỳ (enabled, không tự tạo SUMMARY_ONLY/DETAILED), quality theo đúng doanh nghiệp của Case, issues và closure theo các quyền riêng. `followup=policy` mở sẵn phần đánh giá/chính sách. Rubric quality được cấp theo trường/kỳ; enterprise không sửa được trong luồng Case. Case đã kết thúc giữ nội dung chỉ đọc cho thao tác Case; quality là đánh giá doanh nghiệp riêng.

Không tự công bố/đóng Case khi theo link. Mọi lệnh dùng CSRF/idempotency/current version và guard của T3. Closure kiểm đầy đủ gates hoặc quyết định kết thúc sớm; Placement đồng bộ qua outbox, không bị FE tự sửa. Chưa có school audit REST nên không giả kết nối Admin audit. Các nhãn chính sách từ DTO đánh giá legacy không dùng thay chính sách thật.

## Kiểm chứng

`SchoolFollowUpManagementIT` chạy MySQL thật và Chromium thật trên runner riêng: mask/scope/ancestor/membership/term, review conflict/CSRF/replay, sent invitation isolation/revoke/reload, UI review/send/revoke và Case policy/quality/issue/early closure. Browser fixtures bổ sung kiểm tra 409→reload, exact body/key retry sau 503, khóa phạm vi, paging, mất quyền xóa PII và mobile. Những fixture HTTP này không thay bằng chứng API/MySQL thật. Giữ toàn bộ regression và CI inventory, không chạy container integration trên Docker demo local.

Lượt 3 còn lịch sử hợp tác, thống kê Matching và chốt hồi quy/merge theo yêu cầu tiếp theo.
