# Nâng cấp giao diện UT.Bridora

Bản nâng cấp ngày 29/10/2026 hoàn thiện giao diện nền tảng theo cùng một hệ thống thiết kế: màu teal, nền sáng, typography có phân cấp, khoảng cách nhất quán và các trạng thái phản hồi rõ ràng. Phạm vi là giao diện Core Platform đang có; không tạo số liệu nghiệp vụ hoặc chức năng của T2–T5 để minh họa.

## Phạm vi màn hình

| Màn hình | Thay đổi chính |
|---|---|
| Đăng nhập | Bố cục hai vùng trên desktop, nhận diện thương hiệu SVG, minh họa kết nối, biểu mẫu có icon và điều khiển hiện/ẩn mật khẩu |
| Quên mật khẩu | Biểu mẫu email, trạng thái xử lý và phản hồi chung theo contract hiện có |
| Đặt lại mật khẩu | Xác nhận mật khẩu, hướng dẫn chính sách mật khẩu, thông báo thiếu/hết hiệu lực mã và đường quay lại đăng nhập |
| Kích hoạt tài khoản | Họ tên, mật khẩu, xác nhận và trạng thái liên kết; hỗ trợ chuyển phiên khi mở email trong trình duyệt đã đăng nhập |
| Dashboard tài khoản | Hero, thẻ thống kê lấy từ danh tính hiện tại, thông tin tài khoản, vai trò theo tổ chức, quyền theo phạm vi và biểu mẫu đổi mật khẩu |
| Tài khoản và phân quyền | Toolbar tìm kiếm/lọc, avatar chữ cái, badge trạng thái, bảng dữ liệu, phân trang, chi tiết tài khoản đóng được và nhóm thao tác quản trị |
| Membership và trạng thái | Hiển thị phạm vi vai trò, cấp/thu hồi, lý do thay đổi trạng thái, thu hồi phiên và lịch sử biến động |
| Tổ chức và lời mời | Bảng tổ chức có loại/trạng thái, biểu mẫu tạo tổ chức và mời thành viên với mô tả ngữ cảnh |
| Audit log | Bộ lọc có bố cục thống nhất, kiểm tra khoảng thời gian ở giao diện, nút đặt lại, badge kết quả và metadata truy vết có phân cấp |
| Lỗi/tải/rỗng | Spinner và trạng thái chờ, thông báo theo mức độ, hướng dẫn khi không có dữ liệu, nút thử lại khi truy vấn thất bại |

## Hệ thống thiết kế dùng chung

Các token nằm trong `web-demo/src/style.css`. Màu sắc, viền và khoảng cách được chia sẻ giữa các màn hình thay vì đặt lại riêng cho từng biểu mẫu.

| Token | Giá trị hiện tại | Mục đích |
|---|---|---|
| `--brand` | `#087f8c` | CTA, trạng thái đang chọn, điểm nhấn |
| `--brand-dark` | `#056670` | Hover và chữ nhấn |
| `--brand-light` | `#eaf7f5` | Nền nhấn nhẹ |
| `--ink` | `#183243` | Nội dung chính |
| `--muted` | `#5e7382` | Mô tả và metadata |
| `--line` | `#e5ebef` | Viền và đường phân cách |
| `--surface` | `#ffffff` | Panel và biểu mẫu |
| `--canvas` | `#f4f7f9` | Nền không gian làm việc |
| `--radius` | `16px` | Bo góc panel chính |
| `--shadow` | `0 4px 24px rgba(20,48,64,.035)` | Độ nổi nhẹ của panel |

Font dùng chuỗi Inter/Segoe UI và font hệ thống có hỗ trợ tiếng Việt. Không thêm yêu cầu tải font hoặc ảnh từ bên ngoài. Logo và icon là SVG trong source, minh họa trang đăng nhập và hero dùng SVG/CSS; không thêm thư viện UI hoặc đổi framework.

`management.css` bổ sung quy tắc riêng cho bảng quản trị, avatar, toolbar, chi tiết, lịch sử và audit. File này được import sau `style.css` để các quy tắc chuyên biệt không ghi đè ngược hệ thống điều khiển chung.

## Điều chỉnh cỡ chữ theo phản hồi

Đã tăng chữ đồng bộ trong cả hai stylesheet, bao gồm các quy tắc responsive. Hệ thống cỡ chữ dùng `rem` để tôn trọng cỡ font mặc định của trình duyệt:

| Nội dung | Cỡ mặc định |
|---|---|
| Nội dung chung, input và select | 16px |
| Menu, nút chính và nội dung bảng | 15px |
| Nhãn, mô tả và hướng dẫn bảo mật | 14px |
| Badge và metadata bảng | 13px |
| Chú thích nhận diện và nhãn trang trí | 12px |
| Tiêu đề khu vực chính | 22px |

Sidebar rộng 280px trên desktop và 264px ở vùng màn hình hẹp hơn; menu di động có thể cuộn khi chiều cao hạn chế. Dưới 700px, ba thẻ thống kê xếp dọc để giữ chữ đủ lớn. Nút hiện/ẩn mật khẩu căn giữa theo chiều cao input.

Lượt hồi quy typography kết thúc **19:34:29 ngày 29/10/2026 (UTC+7)**: **28 unit, 1 Failsafe và 14/14 Chromium PASS**, không skip/retry. Sau đó tăng bổ sung select chọn phạm vi lên 16px và ba nhóm chữ hướng dẫn/lưu ý lên 14px. Bản CSS cuối đã build lại, cập nhật riêng Docker frontend và kiểm tra trên Chromium production local: cỡ chữ thực tế đúng 16/15/14px, màn tài khoản không tràn trang ở 320/390/901/1051/1280px, chi tiết tài khoản không tràn ở 320/901/1051px, icon mật khẩu căn giữa và không có lỗi runtime/CSP. Không chạy lại toàn bộ 49 backend integration test trong lượt này.

Ảnh kiểm tra bổ sung nằm tại `web-demo/test-results/typography-account-*.png`, `typography-detail-*.png`, `typography-login-320.png` và `login-local.png`. Tài khoản dùng cho smoke local đã đăng xuất sau kiểm tra; không ghi mật khẩu vào ảnh hoặc báo cáo.

## Component và hành vi tái sử dụng

Các component tại `web-demo/src/components.tsx` gồm:

- `Brand`: nhận diện mặc định và phiên bản dùng trên nền đậm.
- `Icon`: bộ icon SVG thống nhất, ẩn khỏi tên truy cập khi chỉ có vai trò trang trí.
- `Field`: nhãn gắn với input, icon tùy chọn, hướng dẫn, autocomplete và nút hiện/ẩn cho mật khẩu.
- `Notice`: trạng thái thông tin, thành công hoặc lỗi; thông báo qua vùng `role="status"` và `aria-live`.
- `LoadingState`, `EmptyState`: cùng một cách biểu diễn trạng thái tải/rỗng và hành động tiếp theo.
- `StatusBadge`: account/organization status và audit SUCCESS/FAILURE/DENIED, có nhãn chữ đi cùng màu sắc.
- `Pagination`: tổng kết quả, trang hiện tại, Trước/Sau và trạng thái vô hiệu hóa phù hợp.
- `useAction`: loading, lỗi/thành công, bảo vệ cập nhật state sau unmount và đếm tác vụ đang chờ.

Các truy vấn phân trang trong `management.tsx` dùng trạng thái tải/lỗi tách khỏi tác vụ mutation. Response đã cũ được bỏ qua sau khi người dùng đổi truy vấn; các hàng của bộ lọc trước không tiếp tục hiển thị như kết quả mới. Lỗi đọc có nút **Thử lại** và thông báo có thể được trình đọc màn hình nhận biết.

## Responsive và truy cập bằng bàn phím

Desktop có sidebar sáng và topbar, nội dung được giới hạn chiều rộng hợp lý. Tại chiều rộng từ 900px trở xuống, menu chuyển thành offcanvas. Trên màn hình nhỏ, các panel và biểu mẫu xếp dọc; bảng rộng chỉ cuộn trong `.table-wrap`, không kéo toàn trang rộng hơn viewport. UUID và email có thể xuống dòng trong vùng nội dung phù hợp.

Menu di động có nút mở/đóng, `aria-expanded`, backdrop và hỗ trợ Escape. Khi mở, focus được đưa vào menu, Tab/Shift+Tab giữ trong các điều khiển của menu; khi đóng, focus trở về nút mở. Scroll nền bị khóa trong thời gian menu hiển thị. Khi chọn chức năng, menu đóng và tiêu đề nội dung nhận focus. Có liên kết bỏ qua điều hướng và trạng thái focus nhìn thấy được.

Biểu mẫu dùng HTML label/input, submit bằng bàn phím, trạng thái disabled/aria-busy trong lúc xử lý và ngăn gửi trùng khi đăng nhập hoặc đổi mật khẩu. Nút hiện/ẩn mật khẩu có nhãn tiếng Việt và `aria-pressed`, không lưu mật khẩu ở nơi khác để phục vụ hiển thị. CSS tôn trọng `prefers-reduced-motion`.

Đây là các cải thiện truy cập đã triển khai trong source, không phải tuyên bố đã đạt chứng nhận WCAG hoặc đã qua audit độc lập.

## Dữ liệu và bảo mật

Các thẻ thống kê dashboard tính từ response `/auth/me` và `/auth/me/permissions`: số membership, số tổ chức khác nhau và số permission khác nhau. Bảng quản trị dùng dữ liệu cùng tổng số bản ghi của API phân trang. Không có số liệu tăng trưởng, biểu đồ hoạt động, thông báo hoặc danh sách sinh viên giả được thêm vào sản phẩm.

Menu và thao tác hiển thị theo permission hiện tại để người dùng dễ điều hướng. Backend vẫn là nơi quyết định quyền. Bản nâng cấp không thay API contract, authentication manager, CSRF, cookie/session, organization scope, lifecycle hoặc database migration. Không ghi mật khẩu, token, cookie hoặc nội dung `.env.local` vào tài liệu hay ảnh minh họa.

Liên kết email giữ mã trong bộ nhớ và bỏ fragment khỏi URL sau khi đọc. Khi trình duyệt đã đăng nhập, giao diện giải thích tài khoản hiện tại và cho chọn đăng xuất để tiếp tục liên kết hoặc trở lại tài khoản. Không tự sử dụng liên kết để thay đổi tài khoản đang đăng nhập.

## File thay đổi trong giao diện

| File | Trách nhiệm |
|---|---|
| `web-demo/src/App.tsx` | Shell, auth, dashboard, tài khoản, menu di động và quản lý focus |
| `web-demo/src/components.tsx` | Nhận diện, icon, input, feedback, trạng thái, phân trang và hook action |
| `web-demo/src/management.tsx` | Bảng và biểu mẫu quản trị, loading/error độc lập, hủy kết quả truy vấn cũ |
| `web-demo/src/style.css` | Token, typography, layout, controls, responsive và giảm chuyển động |
| `web-demo/src/management.css` | Styling riêng bảng, toolbar, chi tiết, membership, lịch sử và audit |
| `web-demo/src/main.tsx` | Import stylesheet chung rồi stylesheet quản trị |
| `web-demo/tests/ux.spec.ts` | Kịch bản UX bổ sung, có phân biệt rõ test UI mock |
| `web-demo/index.html`, `web-demo/public/favicon.svg` | Tiêu đề, metadata và favicon cùng nhận diện thương hiệu |
| `docs/UI_UPGRADE.md` | Phạm vi, cách tái sử dụng và bằng chứng kiểm thử bản nâng cấp |

Bộ luồng Must tại `web-demo/tests/core.spec.ts` tiếp tục được dùng để kiểm tra hồi quy với backend/MySQL thật. Phần source backend và secret local nằm ngoài phạm vi nâng cấp giao diện này.

## Kiểm thử và trạng thái bàn giao

| Kiểm tra | Trạng thái tại thời điểm viết tài liệu |
|---|---|
| TypeScript + Vite production build | **PASS** sau khi gộp giao diện và stylesheet |
| 7 luồng Must trong `core.spec.ts` | **PASS** trên giao diện mới, không đổi selector/luồng nghiệp vụ |
| Lượt hồi quy typography 14 kịch bản Chromium | **PASS 14/14**, không retry, không skip; kết thúc 19:34:29 ngày 29/10/2026 (UTC+7); kiểm tra CSS bổ sung cuối cùng được mô tả ở phần cỡ chữ |
| Unit test và test điều phối | **PASS 28 unit + 1 Failsafe** điều phối 14 kịch bản; không chạy lại toàn bộ 49 database/backend test trong lượt UI |
| Visual desktop/mobile | Đã xem ảnh login, dashboard desktop/mobile và users/organizations/audit desktop/mobile có dữ liệu; không phát hiện lỗi bố cục chặn bàn giao |
| Responsive | **PASS** bốn màn ở 320px/390px; bảng cuộn bên trong panel |
| Frontend Docker local | **PASS** build, cập nhật riêng container frontend và health check; giữ backend/database đang chạy |
| Smoke production tại localhost:8088 | **PASS** Chromium render login, JS/CSS/favicon tải được, không lỗi runtime/CSP |

7 kịch bản UX bổ sung kiểm tra bàn phím/hiện mật khẩu, menu di động, bốn màn ở chiều rộng 320px và 390px, trạng thái liên kết thiếu mã và ba trường hợp UI mock sau:

1. Response tìm kiếm cũ đến sau response mới: route mock đảo thứ tự để kiểm tra state của giao diện.
2. API danh sách trả lỗi rồi dữ liệu rỗng: route mock kiểm tra thông báo, thử lại và empty state.
3. Login trả lời chậm rồi lỗi: route mock kiểm tra disabled/loading, chặn submit lặp và khôi phục thao tác.

Ba test **UI mock** chỉ chứng minh hành vi frontend. Không dùng chúng làm bằng chứng cho authentication, authorization, CSRF hoặc database integration ở backend. Kết quả cuối và tên bài thực thi cần đối chiếu với `target/frontend-e2e.log`, `web-demo/test-results/e2e.xml` và báo cáo bàn giao do root cập nhật. Không ghi PASS cho test còn đang chạy hoặc chỉ được kiểm tra tĩnh.

Lệnh xác minh: `./mvnw.cmd -Dfrontend.e2e=true -Dit.test=CorePlatformIT#frontendMustFlowsInChromium verify -B`. Machine-readable summary được lưu tại `docs/UI_TEST_EVIDENCE.json`. Bản build production có CSS khoảng 39,68kB (gzip 8,94kB), JavaScript khoảng 281,40kB (gzip 85,17kB); không thêm package runtime hoặc font/image từ bên ngoài.

Các lượt phát triển đã sửa lỗi dấu sao trong label ảnh hưởng exact label lookup. Hai lần test mock recovery cũng được sửa để chờ trạng thái đúng dưới React StrictMode và chọn chính xác nút thử lại. Bộ kiểm thử cuối không bỏ bài hoặc đổi API để đạt PASS.

Giới hạn kiểm chứng: Chromium desktop/mobile emulation, chưa chạy Firefox/Safari, thiết bị vật lý hoặc audit WCAG/screen reader đầy đủ. Luồng reset/kích hoạt đã kiểm thử tự động; chưa có review ảnh riêng cho mọi trạng thái token. Các giới hạn nghiệp vụ/backend trong HANDOFF_CHECKLIST giữ nguyên.

Giao diện mới đã có tại http://localhost:8088. Nếu tab đang giữ bản cũ, tải lại bằng Ctrl+F5. Ảnh QA sinh tại `web-demo/test-results`, gồm login-local, dashboard, mobile và users/organizations/audit desktop/mobile; dữ liệu trong ảnh kiểm thử dùng fixture @example.test.
