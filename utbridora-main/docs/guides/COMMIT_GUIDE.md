# Hướng dẫn commit theo chức năng

Hướng dẫn này dùng cho lần đưa source hiện tại lên repo GitHub mới. Thực hiện tại thư mục chứa `pom.xml` bằng PowerShell. Không thay đổi nội dung source hoặc Flyway migration để tạo lịch sử commit.

Source hiện đã có nhiều module phụ thuộc lẫn nhau. Các nhóm dưới đây giúp review theo phạm vi, không phải bằng chứng mỗi commit trung gian đều đã build độc lập. Kiểm thử toàn bộ sau khi hoàn tất các nhóm, rồi push một lần; GitHub vẫn hiển thị từng commit riêng.

## 1. Bỏ stage toàn bộ source đang chọn

Tại thời điểm viết hướng dẫn, repo báo `No commits yet` và các file đều đang được stage. Chạy:

```powershell
git rm -r --cached .
git status --short
```

`--cached` chỉ bỏ file khỏi vùng stage, giữ nguyên file trên máy. Chỉ dùng bước này cho trạng thái chưa có commit nói trên. Nếu repo đã có commit và chỉ muốn bỏ stage thay đổi mới, dùng `git restore --staged .`.

Kiểm tra `.env.local` được loại khỏi Git:

```powershell
git check-ignore -v .env.local
```

Giữ mật khẩu trong `.env.local`; `application.yml` chỉ tham chiếu `${DB_PASSWORD}`. `.env.example` được commit với các trường mật khẩu để trống. Với mỗi nhóm sau, xem `git diff --cached --stat` và `git diff --cached --name-only` trước khi commit. Không dùng `git add .` giữa các nhóm.

## 2. Cấu hình build và môi trường phát triển

```powershell
git add .gitignore .dockerignore .env.example .mvn mvnw mvnw.cmd pom.xml
git add Dockerfile compose.yaml compose.local.yaml compose.staging.yaml run-local.cmd scripts
git add src/main/java/vn/utbridora/auth/UtBridoraApplication.java src/main/resources/application.yml
git diff --cached --stat
git commit -m "chore: configure build and local development environment"
```

## 3. Database, entity và repository dùng chung

```powershell
git add src/main/resources/db/migration
git add src/main/java/vn/utbridora/auth/user
git add src/main/java/vn/utbridora/auth/organization
git add src/main/java/vn/utbridora/auth/files
git diff --cached --stat
git commit -m "feat(database): add core schema and persistence models"
```

Các enum, trạng thái tài khoản, membership và mô hình file metadata thuộc nhóm nền tảng dùng chung. Giữ nguyên thứ tự và nội dung migration đã áp dụng.

## 4. Audit log dùng chung

```powershell
git add src/main/java/vn/utbridora/auth/audit
git diff --cached --stat
git commit -m "feat(audit): add shared audit service and request correlation"
```

API quản trị tra cứu audit nằm trong `AdminController`, được đưa cùng nhóm API quản trị ở bước 7.

## 5. Xác thực, session và mật khẩu

```powershell
git add src/main/java/vn/utbridora/auth/security
git add src/main/java/vn/utbridora/auth/tokens
git add src/main/java/vn/utbridora/auth/bootstrap
git add src/main/java/vn/utbridora/auth/core/AccountService.java src/main/java/vn/utbridora/auth/core/PasswordPolicy.java
git add src/main/java/vn/utbridora/auth/api/AuthController.java
git add src/main/java/vn/utbridora/auth/api/ApiDtos.java src/main/java/vn/utbridora/auth/api/ApiErrorHandler.java
git diff --cached --stat
git commit -m "feat(auth): add session authentication and password flows"
```

`AuthController` chứa đăng nhập, đăng xuất, thông tin tài khoản và các API mật khẩu. `security` có cả cơ chế kiểm tra phiên/trạng thái; không thể xem toàn bộ thư mục này là riêng một User Story.

## 6. Phân quyền và phạm vi tổ chức

```powershell
git add src/main/java/vn/utbridora/auth/authorization
git add src/main/java/vn/utbridora/auth/api/DemoScopeController.java
git add src/main/java/vn/utbridora/auth/api/DemoOrganizationVerificationController.java
git diff --cached --stat
git commit -m "feat(authz): add membership and organization scoped access control"
```

Bao gồm contract kiểm tra chia sẻ hồ sơ và cơ chế mặc định từ chối khi chưa có quyết định tích hợp hợp lệ.

## 7. Vòng đời tài khoản và API quản trị

```powershell
git add src/main/java/vn/utbridora/auth/core/AccountLifecycleService.java
git add src/main/java/vn/utbridora/auth/api/AdminController.java
git add src/main/java/vn/utbridora/auth/api/AdminReadController.java
git diff --cached --stat
git commit -m "feat(admin): add account lifecycle and administration APIs"
```

`AdminController` đang chứa các API tổ chức, lời mời, membership, trạng thái tài khoản và tra cứu audit. Đây là file tích hợp nhiều chức năng; nhóm commit theo file không tách riêng từng endpoint. Nếu muốn tách nhỏ đến từng endpoint, cần review và stage theo hunk bằng `git add -p`, đồng thời kiểm tra phụ thuộc sau mỗi lần tách.

## 8. Giao diện React

```powershell
git add web-demo/.dockerignore web-demo/Dockerfile web-demo/nginx.conf
git add web-demo/package.json web-demo/package-lock.json web-demo/index.html
git add web-demo/tsconfig.json web-demo/vite.config.ts
git add web-demo/public web-demo/src
git diff --cached --stat
git commit -m "feat(web): add authentication and administration interface"
```

`App.tsx`, `management.tsx` và stylesheet chứa các màn hình dùng chung. Lần import đầu nhóm chúng thành một chức năng UI; các thay đổi sau có thể commit theo màn hình hoặc theo hunk.

## 9. Kiểm thử và CI

```powershell
git add src/test .github
git add web-demo/tests web-demo/playwright.config.ts
git diff --cached --stat
git commit -m "test: add security integration and browser regression coverage"
```

## 10. Tài liệu và bàn giao

```powershell
git add README.md docs
git diff --cached --stat
git commit -m "docs: add setup integration and handoff guides"
```

## 11. Kiểm tra trước khi push

```powershell
git status --short
git log --oneline
git ls-files -- .env .env.local
```

`git status --short` phải không còn file source cần commit; lệnh `git ls-files` cuối không được trả về hai file chứa secret. Kiểm tra các file YAML, SQL, JSON và tài liệu không chứa mật khẩu/token thật; `.gitignore` không che secret đã viết trực tiếp trong một file source được theo dõi.

Chạy kiểm thử phù hợp trước khi đưa cả bộ source lên:

```powershell
.\mvnw.cmd test
cd web-demo
npm ci
npm run build
npx playwright install chromium
cd ..
.\mvnw.cmd '-Dfrontend.e2e=true' verify
```

Lệnh cuối yêu cầu Docker hoạt động và thực thi integration/E2E; ghi đúng lỗi hoặc NOT RUN nếu môi trường thiếu điều kiện. Không dùng kết quả của các lượt trước để khẳng định source hiện tại đã PASS.

## 12. Push toàn bộ các commit

```powershell
git branch -M main
git remote -v
```

Nếu chưa có remote `origin`, thay URL minh họa bằng URL HTTPS repo đã tạo:

```powershell
git remote add origin https://github.com/TEN_TAI_KHOAN/TEN_REPO.git
git push -u origin main
```

Nếu `origin` đã đúng, chỉ chạy lệnh push. Nếu repo trên GitHub đã có lịch sử và push bị từ chối, cần xử lý theo lỗi thực tế và giữ lịch sử trên cả hai phía.

## Các lần phát triển sau

Mỗi chức năng mới: sửa code, thêm/cập nhật test phù hợp, chạy kiểm thử, stage đúng các file/hunk liên quan rồi tạo commit. Có thể đưa test của chức năng vào cùng commit với code. Ví dụ thông điệp: `fix(auth): revoke sessions after password reset`, `feat(web): improve audit filters`.
