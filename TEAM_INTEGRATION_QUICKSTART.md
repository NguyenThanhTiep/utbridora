# Hướng dẫn nhanh tích hợp code các team

Các team đưa code nghiệp vụ vào cùng dự án hiện tại theo module, dùng chung một backend Spring Boot, một frontend React và database của môi trường đó. Kiến trúc tiếp tục là **Modular Monolith**.

Ví dụ phân quyền, audit, migration và quy trình merge chi tiết nằm trong [TEAM_IMPLEMENTATION_GUIDE.md](docs/TEAM_IMPLEMENTATION_GUIDE.md).

## 1. Mỗi team tạo nhánh theo chức năng

Người mới nhận source:

```powershell
git clone https://github.com/NguyenThanhTiep/utbridora.git
cd utbridora
git switch -c feature/student-profile
```

Nếu đã clone, cập nhật `main` trước khi tạo nhánh:

```powershell
git switch main
git pull --ff-only origin main
git switch -c feature/student-profile
```

Các team khác có thể dùng tên nhánh `feature/internship-tasks`, `feature/matching-report`, `feature/cooperation-request`.

Repo Public cho phép đọc/clone; người muốn push vào repo gốc cần được cấp quyền cộng tác. Tạo pull request để review trước khi merge vào `main`.

## 2. Ghép backend vào đúng package

Cấu trúc đề xuất:

```text
src/main/java/vn/utbridora/auth/modules/
├── student/       # T2: hồ sơ sinh viên
├── internship/    # T3: task, submission, evaluation
├── matching/      # T4: tổng hợp, thống kê
└── cooperation/   # T5: hợp tác, xác thực doanh nghiệp
```

Mỗi module có thể chia thành `controller`, `service`, `repository`, `entity`, `dto`.

**Package mới phải nằm dưới `vn.utbridora.auth`** để cấu hình scan hiện tại phát hiện được. Chuyển các lớp nghiệp vụ cần thiết từ project riêng sang đây; giữ main application, cấu hình đăng nhập và `pom.xml` chung.

Các team sử dụng lại identity, tổ chức, membership, session, CSRF và AuditService của core. Dependency mới cần được kiểm tra tương thích với Java 21/Spring Boot hiện tại.

## 3. Thêm bảng bằng migration

Đặt migration mới trong:

```text
src/main/resources/db/migration/
```

Hiện đã có V1–V7. Người phụ trách tích hợp điều phối version tiếp theo và thứ tự merge.

- Dùng lại `users.id`, `organizations.id` và membership của core.
- Giữ UUID và thời gian UTC.
- Không sửa migration đã áp dụng.
- Không để các team cùng tự dùng một version hoặc bổ sung version thấp sau khi version cao đã chạy ở môi trường chung.
- T3/T5 phải thống nhất module sở hữu Placement, tránh tạo hai mô hình trùng nhau.
- Kiểm tra cả database trống và nâng cấp database có dữ liệu.

## 4. Gắn API với cơ chế bảo mật chung

Mỗi API mới cần:

- Đăng ký đúng method/path trong `SecurityConfig`, trước `denyAll`.
- Gọi `AuthorizationPolicy` để kiểm tra role, permission và tổ chức.
- Kiểm tra thêm owner, org thật của record, assignment và trạng thái nghiệp vụ.
- Lấy actor từ `AppPrincipal`, không lấy từ request body.
- Ghi `AuditService` với event hợp lệ trong transaction nghiệp vụ.

Quyền mới cần có dữ liệu tương ứng trong `permissions` và `role_permissions`. ADMIN không tự động có mọi quyền nghiệp vụ.

Với hồ sơ sinh viên, T2/T5 phải triển khai quyết định share/consent thật và lọc trường dữ liệu được phép trả về. Giữ mặc định từ chối khi chưa có quyết định hợp lệ.

## 5. Ghép frontend vào shell hiện tại

Cấu trúc đề xuất:

```text
web-demo/src/modules/
├── student/
├── internship/
├── matching/
└── cooperation/
```

Tái sử dụng `api.ts`, `components.tsx`, session và CSRF. Bổ sung màn hình/menu vào `App.tsx`.

Lưu ý:

- Menu hiện chỉ lọc quyền **ADMIN toàn cục**. Menu nghiệp vụ cần kiểm tra grant theo role và organization đang chọn.
- `api.ts` tự thêm `/api/v1`; module truyền đường dẫn như `/student-profiles/me` khi endpoint đó đã được triển khai.
- Dùng URL tương đối để Vite/Nginx chuyển tiếp API.
- Upload `FormData` và export Blob/CSV cần hỗ trợ transport riêng phù hợp; wrapper hiện dành cho JSON.
- Giữ một `package.json` và lockfile chung, bổ sung dependency có review.

## 6. Test, tạo pull request rồi merge

Kiểm tra cả luồng hợp lệ và các trường hợp:

- Sai role, organization hoặc owner.
- Đổi org/record ID trong URL hoặc body để truy cập dữ liệu khác.
- Membership đã thu hồi, tổ chức chưa được xác thực.
- Thiếu/sai CSRF hoặc assignment không hợp lệ.
- Share/consent hết hạn hoặc đã thu hồi.
- Audit lỗi làm transaction nghiệp vụ rollback.
- Migration nâng cấp vẫn giữ dữ liệu và constraint cũ.

Khi thêm migration V8+, cập nhật bài `MigrationUpgradeIT` đang kiểm tra cố định số migration, đồng thời giữ các kiểm tra dữ liệu và constraint.

Từ thư mục chứa `pom.xml`, chạy các bước kiểm thử phù hợp:

```powershell
.\mvnw.cmd test
cd web-demo
npm ci
npm run build
npx playwright install chromium
cd ..
.\mvnw.cmd '-Dfrontend.e2e=true' verify
```

Docker Linux engine cần hoạt động cho MySQL Testcontainers. Ghi kết quả thực tế PASS/FAIL/NOT RUN; không dùng số test của snapshot trước để khẳng định module mới đã đạt.

Sau khi pull request được review, CI PASS và merge:

```powershell
git switch main
git pull --ff-only origin main
.\run-local.cmd start
```

Launcher build lại ứng dụng chứa các module đã ghép và áp dụng migration. **Chạy launcher thành công không thay thế việc chạy bộ test.**

Ứng dụng Docker mở tại `http://localhost:8088`. Workbench kết nối MySQL Docker qua `127.0.0.1:3307`, schema `utbridora`, user `bridora`, mật khẩu trong `.env.local` của từng máy.

Mỗi máy phát triển vẫn có database local riêng; source và schema được thống nhất qua Git/Flyway. Các module và endpoint minh họa trong hướng dẫn cần được bổ sung bằng code thực tế của từng team.
