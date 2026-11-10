# Hướng dẫn làm việc với Git và GitHub trong dự án UT.Bridora

Cập nhật: **04/11/2026**. Dành cho thành viên T1–T5, QA và AI hỗ trợ phát triển. Các lệnh Windows dùng PowerShell tại thư mục chứa `pom.xml`.

Repository: [NguyenThanhTiep/utbridora](https://github.com/NguyenThanhTiep/utbridora). Remote HTTPS đã thấy trong cấu hình local là `https://github.com/NguyenThanhTiep/utbridora.git`; nhánh đang sử dụng là `main`. Chưa xác minh visibility, collaborator, branch protection hoặc kết quả Actions hiện tại trên GitHub.

**Quy trình đề xuất cho nhóm:** cập nhật `main` → tạo nhánh chức năng → sửa và kiểm thử → commit theo chức năng → push nhánh → mở Pull Request → review và CI → người phụ trách merge → các thành viên cập nhật lại.

Tài liệu này không thực hiện commit, push, tạo PR, thay quyền hoặc cài branch protection. Đây là hướng dẫn để áp dụng; các quy tắc quản trị mới cần người phụ trách repo thống nhất và cấu hình.

## Mục lục

1. [Hiểu Git và GitHub](#1-hiểu-git-và-github)
2. [Quy ước làm việc của dự án](#2-quy-ước-làm-việc-của-dự-án)
3. [Chuẩn bị tài khoản và lấy source](#3-chuẩn-bị-tài-khoản-và-lấy-source)
4. [Tạo nhánh và bắt đầu công việc](#4-tạo-nhánh-và-bắt-đầu-công-việc)
5. [Commit theo chức năng](#5-commit-theo-chức-năng)
6. [Push nhánh lên GitHub](#6-push-nhánh-lên-github)
7. [Tạo Pull Request trên web](#7-tạo-pull-request-trên-web)
8. [Review, CI và merge](#8-review-ci-và-merge)
9. [Cập nhật source mỗi ngày](#9-cập-nhật-source-mỗi-ngày)
10. [Xử lý xung đột](#10-xử-lý-xung-đột)
11. [Các file nhiều team cùng sửa](#11-các-file-nhiều-team-cùng-sửa)
12. [Thao tác bằng VS Code](#12-thao-tác-bằng-vs-code)
13. [Lỗi thường gặp và khôi phục an toàn](#13-lỗi-thường-gặp-và-khôi-phục-an-toàn)
14. [Secret, database và dữ liệu demo](#14-secret-database-và-dữ-liệu-demo)
15. [Gợi ý quản trị repository](#15-gợi-ý-quản-trị-repository)
16. [Checklist và mẫu báo cáo](#16-checklist-và-mẫu-báo-cáo)
17. [Mẫu giao việc Git cho AI](#17-mẫu-giao-việc-git-cho-ai)
18. [Tài liệu đối chiếu](#18-tài-liệu-đối-chiếu)

## 1. Hiểu Git và GitHub

| Khái niệm | Hiểu đơn giản |
|---|---|
| Repository | Thư mục dự án cùng lịch sử thay đổi |
| Git | Công cụ quản lý phiên bản trên máy |
| GitHub | Nơi lưu repo từ xa, cộng tác, review và chạy CI |
| Working tree | Các file đang sửa trên máy |
| Stage / index | Những thay đổi đã chọn cho commit tiếp theo |
| Commit | Một mốc thay đổi lưu trong Git local |
| Branch | Nhánh phát triển có lịch sử commit |
| `main` | Nhánh tích hợp của sản phẩm theo cách làm hiện tại |
| `origin` | Tên remote repo; không phải tên người dùng |
| Push | Gửi commit từ nhánh local lên remote |
| Fetch | Cập nhật thông tin nhánh remote về máy, chưa merge vào nhánh đang làm |
| Pull | Fetch rồi tích hợp theo tùy chọn/configuration; hướng dẫn dùng `--ff-only` khi cập nhật main |
| Pull Request / PR | Đề nghị review và đưa thay đổi từ nhánh nguồn vào nhánh đích |
| Merge | Hợp nhất lịch sử/thay đổi giữa các nhánh |
| Conflict | Git cần người quyết định cách kết hợp các thay đổi chồng nhau |

**Commit chưa có nghĩa đã lên GitHub; push nhánh chưa có nghĩa đã vào `main`; merge chưa chứng minh ứng dụng đã deploy.** Git cũng không đồng bộ MySQL hay Docker volume giữa các máy.

## 2. Quy ước làm việc của dự án

### 2.1. Một repo, nhánh theo chức năng

Đề xuất dùng một repo chung cho Modular Monolith, với nhánh ngắn hạn theo việc:

| Team / việc | Ví dụ nhánh |
|---|---|
| T1 | `feature/t1-file-access`, `fix/t1-session-revocation` |
| T2 | `feature/t2-student-profile` |
| T3 | `feature/t3-task-submission` |
| T4 | `feature/t4-strict-matching` |
| T5 | `feature/t5-cooperation-review` |
| QA | `test/student-profile-scope` |
| Tài liệu | `docs/github-workflow` |

Tên dùng chữ thường, tiếng Anh và dấu `-`, không khoảng trắng. Không cần nhánh cố định cho từng người hoặc từng team rồi để lệch `main` nhiều tuần. Nếu tính năng lớn, chia các PR có thể review và tích hợp độc lập; có thể mở Draft PR để người khác thấy sớm dependency.

Một nhánh có thể có nhiều commit. Một chức năng BE/FE có thể cần schema, service, controller, UI và test trong cùng commit/PR; không bắt buộc tách theo loại file nếu làm commit mất khả năng build.

### 2.2. Những điều cần giữ

- Không push trực tiếp `main` trong quy trình cộng tác thông thường; đưa qua PR và review theo quy ước nhóm.
- Không force-push, reset lịch sử chung hoặc xóa nhánh người khác để giải quyết lỗi.
- Không tự tạo repo/project mới thay source nền tảng.
- Không sửa/xóa migration đã áp dụng, giảm bảo mật hoặc bỏ test để merge được.
- Không dùng một tài khoản GitHub/PAT chung cho cả nhóm. Tài khoản admin **ứng dụng** khác tài khoản GitHub.
- Không xem trạng thái “Public” là quyền push; repo public vẫn cần quyền ghi hoặc quy trình fork/PR phù hợp.

Đọc [hướng dẫn BE/FE](HUONG_DAN_PHAT_TRIEN_BE_FE_CHO_CAC_TEAM.md) trước khi sửa source. Repo hiện đã có lịch sử; không chạy lại `git init`, bỏ theo dõi toàn repo hay push toàn bộ vào main theo quy trình khởi tạo dự án mới. Mục 5 của tài liệu này hướng dẫn commit theo chức năng trong công việc hiện tại.

## 3. Chuẩn bị tài khoản và lấy source

### 3.1. Quyền truy cập và xác thực

Mỗi thành viên dùng tài khoản cá nhân, nhận lời mời collaborator nếu nhóm cấp quyền. Clone repo private yêu cầu quyền đọc; push vào repo gốc yêu cầu quyền ghi. Visibility/quyền thực tế cần owner kiểm tra trên GitHub.

Dùng Git for Windows và cơ chế đăng nhập GitHub qua credential manager/browser nếu đã có. HTTPS Git không nhận mật khẩu tài khoản GitHub theo cách cũ; khi dùng token phải cấp quyền tối thiểu cho đúng repo và lưu trong credential manager, hoặc dùng SSH key theo chính sách nhóm. Không đặt token trong URL remote, file source, terminal được ghi log hoặc chat. Xem [hướng dẫn xác thực remote của GitHub](https://docs.github.com/en/get-started/git-basics/about-remote-repositories).

### 3.2. Nếu chưa clone

Tại thư mục cha muốn chứa dự án, chạy:

```powershell
git clone https://github.com/NguyenThanhTiep/utbridora.git
cd utbridora
git status
git remote -v
```

Clone bằng Git, không tải ZIP làm quy trình làm việc hằng ngày vì ZIP không có lịch sử và cấu hình remote của clone.

### 3.3. Nếu đã có repo trên máy

Mở đúng thư mục đang làm; trên máy hiện tại là:

```powershell
cd D:\HKDN-TTTV-UTH\UTBridora_SourceCode\utbridora-t1-full-foundation
git rev-parse --show-toplevel
git status --short
git branch --show-current
git remote -v
```

Thành viên khác dùng đường dẫn clone của mình, không cần giống ổ D. Không clone đè hoặc tạo `.git` lồng trong repo hiện có.

### 3.4. Thông tin tác giả commit

Đặt thông tin của chính mình cho repo, thay hai giá trị mẫu trước khi dùng:

```powershell
git config --local user.name "HO_TEN_CUA_BAN"
git config --local user.email "EMAIL_COMMIT_CUA_BAN"
```

Dùng email đã xác minh hoặc địa chỉ noreply GitHub được cấp cho tài khoản của bạn. Đây là danh tính ghi trên commit, không phải mật khẩu và không thay thao tác đăng nhập remote. Không ghi tên/email của trưởng nhóm cho tất cả thành viên.

## 4. Tạo nhánh và bắt đầu công việc

### 4.1. Khi working tree đã sạch

Chạy từng bước và kiểm tra lỗi; nếu một bước thất bại thì xử lý trước khi chạy tiếp:

```powershell
git status --short
git switch main
git fetch origin
git pull --ff-only origin main
git switch -c feature/t2-student-profile
```

`git status --short` không có output nghĩa là không có thay đổi được Git liệt kê, **không** nghĩa là file ignored như `.env.local` không tồn tại. Nhánh ví dụ thay bằng chức năng được phân công. Không dùng `git pull` để cập nhật main nếu đang đứng nhầm ở nhánh feature.

### 4.2. Nếu đã sửa code khi đang ở main

Nếu thay đổi chưa commit, thường có thể tạo nhánh mới tại chỗ để giữ nguyên chúng:

```powershell
git status --short
git switch -c feature/t2-student-profile
git status --short
```

Sau đó review, commit đúng phạm vi và cập nhật `origin/main` trên nhánh như mục 9. Không bỏ thay đổi để làm working tree sạch. Nếu đã commit nhầm main, xem mục 13; không tự reset main.

### 4.3. Chốt công việc trước khi viết

Trong issue hoặc PR, ghi mã US/FR, owner, phạm vi BE/FE/database, contract version, dependency và AC. T1 cấp version migration mới; owner/consumer chốt API liên team trước khi công bố. Không đợi đến conflict mới quyết định ai sở hữu dữ liệu.

## 5. Commit theo chức năng

### 5.1. Review trước khi stage

```powershell
git status --short
git diff --stat
git diff
```

`git diff` mặc định chỉ cho thay đổi chưa stage của file tracked; file mới `??` phải mở đọc, sau stage xem bằng `git diff --cached`. Không đưa diff có secret lên chat/log công khai.

Một số ký hiệu thường gặp:

| Ký hiệu | Ý nghĩa |
|---|---|
| `??` | File mới chưa tracked |
| ` M` | Sửa file, chưa stage |
| `M ` | Sửa file, đã stage |
| `MM` | Có phần đã stage, sau đó còn sửa thêm chưa stage |
| ` D` / `D ` | Xóa file chưa stage / đã stage |
| `UU` | Hai phía sửa và còn conflict |

### 5.2. Chọn đúng file hoặc hunk

Ví dụ commit tài liệu này, khi thay đổi đã được giao và kiểm tra:

```powershell
git add -- docs/guides/HUONG_DAN_LAM_VIEC_VOI_GITHUB.md
```

Nếu cùng chức năng có cập nhật mục lục, xem diff rồi stage thêm `docs/README.md`. Với source, thay đường dẫn bằng những file thật của chức năng gồm code/test/migration liên quan; không stage toàn bộ các việc đang làm dở.

Chọn một phần thay đổi trong file tracked:

```powershell
git add -p
```

Không dùng `git add .` mặc định khi working tree có thay đổi của nhiều việc hoặc nhiều người. Không stage toàn thư mục migration nếu có file đang chờ phiên bản/review của team khác.

### 5.3. Kiểm tra phần sắp commit

```powershell
git diff --cached --stat
git diff --cached --name-status
git diff --cached
git diff --cached --check
git ls-files -- .env .env.local
```

Lệnh cuối không nên liệt kê file secret; đây chỉ là kiểm tra hai tên phổ biến, không phải máy quét secret đầy đủ. Kiểm tra cả file config, tài liệu, SQL, JSON và các tên `.env.*` khác trong staged diff. Xóa file có chủ đích phải xuất hiện `D`; đừng bỏ qua deletion khi review.

### 5.4. Thông điệp commit

Quy ước đề xuất: `<type>(<scope>): <mô tả hành vi>`.

| Type | Dùng khi | Ví dụ |
|---|---|---|
| `feat` | Thêm chức năng | `feat(student): add own profile editing` |
| `fix` | Sửa lỗi | `fix(auth): reject revoked sessions` |
| `test` | Bổ sung kiểm thử | `test(matching): cover missing-data precedence` |
| `docs` | Tài liệu | `docs: add GitHub collaboration guide` |
| `refactor` | Đổi cấu trúc, giữ hành vi | `refactor(cooperation): extract approval policy` |
| `chore` | Bảo trì công cụ/cấu hình | `chore(ci): update test artifact retention` |

Tránh “update”, “fix all”, “done” vì khó review. Test và tài liệu của tính năng có thể đi cùng commit code; không tạo lịch sử giả bằng cách sửa source/migration chỉ để chia commit đẹp.

Ví dụ sau review và kiểm tra phù hợp:

```powershell
git commit -m "docs: add GitHub collaboration guide"
git log -1 --oneline
git status --short
```

Commit chỉ chứa phần đã stage. File còn lại trong working tree không tự mất và không được coi đã bàn giao.

### 5.5. Bỏ stage khi chọn nhầm

```powershell
git restore --staged -- docs/README.md
```

Lệnh này bỏ thay đổi file khỏi stage và giữ nội dung working tree. **Không bỏ `--staged` tùy tiện:** `git restore <file>` có thể loại bỏ chỉnh sửa trên máy. Xem [Git restore](https://git-scm.com/docs/git-restore).

## 6. Push nhánh lên GitHub

Lần đầu với nhánh ví dụ:

```powershell
git branch --show-current
git push -u origin feature/t2-student-profile
```

Các lần sau, sau khi xác nhận upstream đúng nhánh feature:

```powershell
git branch -vv
git push
```

Đang làm nhánh tài liệu thì dùng tên nhánh tài liệu, không chép nguyên tên nhánh T2. Không dùng `git push origin main` để gửi thay đổi feature. Push yêu cầu quyền đúng tài khoản; `git config user.name` không cấp quyền ghi.

Nếu không có quyền push, yêu cầu owner cấp quyền hoặc dùng fork theo quy trình được nhóm chấp nhận. Không tự đổi remote sang repo người khác hoặc dùng credential của trưởng nhóm để vượt lỗi.

Push gửi **commit**, không gửi file chưa commit và không đưa thay đổi vào nhánh đích PR ngay. Sau push, kiểm tra đúng nhánh trên GitHub, không chỉ nhìn trang main.

## 7. Tạo Pull Request trên web

1. Mở [repo UT.Bridora](https://github.com/NguyenThanhTiep/utbridora).
2. Chọn **Pull requests → New pull request**, hoặc **Compare & pull request** nếu GitHub hiện gợi ý sau push.
3. Chọn **base: `main`**, **compare: nhánh chức năng của bạn**. Với fork, kiểm tra cả base/head repository.
4. Xem **Files changed**: đúng phạm vi, không secret, không file build, không mất source hoặc sửa migration cũ.
5. Viết title và mô tả; chọn reviewer, liên kết issue nếu có.
6. Nếu còn dependency hoặc chưa sẵn sàng review, mở **Draft pull request**; ghi rõ phần còn thiếu. Nếu đã sẵn sàng, tạo PR thông thường.
7. Theo dõi review và checks. Sửa trên chính nhánh, commit và push tiếp; PR cập nhật theo nhánh đó.

Các tên nút có thể thay đổi theo giao diện/quyền. Quy trình chính thức: [tạo Pull Request trên GitHub](https://docs.github.com/en/pull-requests/how-tos/create-pull-requests/creating-a-pull-request).

Mẫu nội dung PR:

```markdown
## Vấn đề và kết quả
- Trước thay đổi:
- Sau thay đổi:
- US/FR và contract/version:

## Phạm vi
- Backend/API:
- Frontend:
- Database/migration được cấp version:
- Permission/audit/ownership và team chịu ảnh hưởng:

## Kiểm thử thực tế
| Lệnh / ca kiểm thử | PASS / FAIL / NOT RUN | Evidence / nguyên nhân |
|---|---|---|
| Unit backend | | |
| Migration/API/authorization | | |
| Frontend build | | |
| E2E luồng liên quan | | |

## Cách kiểm tra
1. Chuẩn bị môi trường/fixture:
2. Thao tác:
3. Kết quả mong đợi:

## Phụ thuộc và giới hạn
- Owner/consumer cần review:
- Chức năng hoặc adapter chưa có:
- Không đưa dữ liệu thật, mật khẩu hoặc token vào PR.
```

Không ghi “đã test” chung chung hoặc lấy PASS của commit cũ làm bằng chứng cho PR mới. Thay đổi tài liệu thuần túy có thể kiểm tra link/diff; không cần khởi động DB chỉ để tạo bằng chứng không liên quan.

## 8. Review, CI và merge

### 8.1. Reviewer kiểm tra gì?

Reviewer domain kiểm yêu cầu/state/AC; reviewer core kiểm ảnh hưởng nền tảng khi có. Nội dung cần xem:

- Code đúng module, tái sử dụng identity/session/guard, không tạo hệ thống tài khoản khác.
- Scope theo org/owner/assignment, chống IDOR, CSRF và default deny được giữ.
- API/DTO/permission/audit đúng contract đã chấp nhận, Candidate không bị tự phê duyệt.
- Migration mới đúng thứ tự, không sửa V1–V7 đã chia sẻ; upgrade có dữ liệu không mất identity.
- Transaction/concurrency/rollback phù hợp; DTO không lộ PII/secret.
- UI dùng shell/client chung, có loading/error/empty và xử lý scope/session.
- Test phù hợp, có evidence, còn thiếu ghi rõ.

### 8.2. CI hiện có trong repository

File `.github/workflows/verify.yml` khai báo workflow **Core platform verification**, job `verify`, chạy khi push và pull request:

1. Checkout source, JDK 21 và Node 24.
2. `npm ci` và `npm run build` trong `web-demo`.
3. Cài Chromium cho Playwright.
4. `mvn -B -Dfrontend.e2e=true verify`.
5. Upload artifact `test-results` khi có, gồm Surefire/Failsafe và browser results.

Mở tab **Checks** của PR hoặc **Actions** của repo, chọn đúng workflow/commit, đọc bước lỗi đầu tiên có ý nghĩa. Không tắt test/CSRF/authorization hoặc bỏ job chỉ để có dấu xanh. Việc có workflow trong source không chứng minh Actions đã bật hoặc check đã PASS trên server.

### 8.3. Ai merge và merge thế nào?

Đề xuất người phụ trách tích hợp merge sau khi review, dependency và kiểm thử đạt yêu cầu. Nếu muốn giữ các commit theo chức năng như cách nhóm đã yêu cầu, dùng **Create a merge commit** khi repo cho phép; **Squash and merge** sẽ gộp các commit của PR thành một commit mới. Nhóm cần thống nhất chiến lược, không coi một lựa chọn là đã cấu hình sẵn.

Không merge Draft PR, PR còn lỗi quan trọng hoặc PR schema chưa được cấp version. Với module chưa đủ adapter thật, ghi rõ giới hạn và không nghiệm thu full flow chỉ từ unit mock.

Sau merge có thể xóa nhánh feature trên GitHub nếu nhóm đồng ý và nhánh không còn việc tiếp tục. Không xóa nhánh chung hoặc nhánh người khác. Nếu dùng squash, việc local branch không được Git xem là fully merged không phải lý do dùng `-D` vội vàng.

## 9. Cập nhật source mỗi ngày

### 9.1. Cập nhật main khi working tree sạch

```powershell
git status --short
git switch main
git pull --ff-only origin main
```

`--ff-only` dừng nếu main local đã phân nhánh, giúp tránh một merge ngoài ý muốn. Nếu báo diverged, đọc lịch sử và xử lý với người tích hợp, không reset hoặc force-push.

### 9.2. Đưa thay đổi main vào nhánh đang làm

Sau khi đã bảo toàn/commit công việc đúng phạm vi, đứng trên nhánh feature:

```powershell
git switch feature/t2-student-profile
git fetch origin
git merge origin/main
```

Nếu có conflict, xử lý theo mục 10. Sau merge, chạy lại kiểm tra phù hợp và push nhánh. Hướng dẫn ưu tiên merge để không viết lại lịch sử nhánh đã chia sẻ; không tự rebase nhánh người khác.

Nếu hai người cố ý dùng chung một nhánh feature và remote đã có commit mới của người còn lại, sau khi working tree sạch cần tích hợp đúng remote feature trước:

```powershell
git fetch origin
git merge origin/feature/t2-student-profile
```

Không nhầm `origin/main` với remote feature khi xử lý push bị từ chối. Tốt hơn là chia việc/nhánh nhỏ để tránh cùng sửa chung một nhánh.

### 9.3. Có việc dở nhưng cần chuyển nhánh

Ưu tiên commit phần hợp lệ vào nhánh riêng. Khi chỉ cần cất tạm local và đã kiểm tra không có secret trong file được stash:

```powershell
git stash push -u -m "wip: student profile before branch switch"
git stash list
```

Khi quay về đúng nhánh, kiểm tra danh sách rồi khôi phục stash được chọn:

```powershell
git stash apply 'stash@{0}'
git status --short
```

`apply` giữ bản stash để kiểm tra trước khi tự xóa. `-u` bao gồm untracked, không bao gồm ignored như `.env.local`; đừng dùng `-a` chỉ để cất cả thư mục build/secret. Stash chỉ ở máy hiện tại, không phải bàn giao/backup lên GitHub; có thể phát sinh conflict khi apply. Xem [Git stash](https://git-scm.com/docs/git-stash).

### 9.4. Sau khi kéo code mới

- Dependency FE đổi: chạy `npm ci`, không tự regenerate lockfile.
- API/schema đổi: đọc PR, cập nhật client/fixture theo contract.
- Migration mới: chỉ chạy app/migrate vào DB phát triển đúng môi trường; không tác động DB production. Giữ migration đã áp dụng.
- Muốn cập nhật bản demo Docker: chạy launcher build lại sau khi cấu hình đúng; Git pull không tự cập nhật container/image đang chạy.
- Git pull không sao chép tài khoản/MySQL data hoặc `.env.local` từ máy trưởng nhóm.

## 10. Xử lý xung đột

### 10.1. Xung đột khi merge

```powershell
git status
git diff --name-only --diff-filter=U
```

Mở từng file. Ba dòng đánh dấu thường gặp:

- `<<<<<<< HEAD`: bắt đầu phần nội dung của nhánh hiện tại.
- `=======`: ngăn cách nội dung hai phía.
- `>>>>>>> origin/main`: kết thúc phần nội dung từ nhánh đang merge vào.

Đọc cả hai phía và mục tiêu chức năng, giữ kết quả hợp nhất đúng rồi xóa các marker. Trong VS Code có thể dùng Merge Editor; không chọn “Accept Current” hay “Accept Incoming” cho toàn bộ chỉ để hết đỏ. Conflict hết marker chưa có nghĩa logic/API/schema đúng.

Stage từng file đã giải quyết, ví dụ nếu conflict nằm ở shell:

```powershell
git add -- web-demo/src/App.tsx
git diff --cached
git status
```

Chạy kiểm tra phù hợp. Khi đang ở merge conflict, tất cả file đã giải quyết và kết quả đã được review:

```powershell
git merge --continue
```

Sau commit merge, chạy lại kiểm tra cần thiết và push nhánh. Đây là quy trình cho merge; conflict khi stash apply không được xử lý bằng `merge --continue`.

### 10.2. Các conflict phải nhờ owner review

- `SecurityConfig`/permission/audit: tránh mất matcher/guard, mở quyền hoặc đổi mã đã công bố.
- Flyway: không sửa checksum migration đã apply hoặc tự đổi số version đã chạy; T1 điều phối thay đổi dựa trên lịch sử DB và HEAD.
- `package-lock.json`: thống nhất dependency trong `package.json`, dùng toolchain chuẩn để tạo lock phù hợp rồi review; không xóa lockfile để bỏ conflict.
- SRS DOCX/Contract Candidate/review có checksum: không nối hai bản hoặc dùng trình soạn thảo thay bytes tùy ý; owner phát hành phiên bản/tham chiếu đúng.

Nếu chưa thể giải quyết, bảo toàn phần đã sửa và nhờ reviewer. Chỉ dùng `git merge --abort` khi hiểu trạng thái và có thể phục hồi công việc; đặc biệt khi có sửa dở từ trước merge, đừng giả định abort luôn khôi phục mọi thứ. Không dùng `reset --hard`/`clean` để chữa conflict.

Tham khảo [quy trình giải quyết merge conflict của GitHub](https://docs.github.com/en/pull-requests/how-tos/merge-and-close-pull-requests/resolving-a-merge-conflict-using-the-command-line).

## 11. Các file nhiều team cùng sửa

| File / nhóm | Cách phối hợp |
|---|---|
| `pom.xml`, framework version | Chỉ thêm dependency cần thiết; owner core review, không chép đè POM team riêng |
| `application.yml`, Compose, Dockerfile | Cấu hình mặc định dùng chung; khác biệt cá nhân dùng env ngoài Git, không hardcode password |
| `SecurityConfig.java` | Matcher method/path chính xác, giữ denyAll/CSRF và service guard |
| `PermissionCatalog`, permission migration, `AuditAction` | Exact code + grant/capability + backward compatibility; T1 review |
| `src/main/resources/db/migration/` | Một chuỗi version, một Flyway history; cấp số theo thứ tự merge, không ai cũng dùng V8 |
| `web-demo/src/App.tsx`, `main.tsx` | Phối hợp entry/menu/render, giữ login và session handling |
| `web-demo/src/api.ts`, `components.tsx`, CSS chung | Tái sử dụng, thêm tương thích; không phá transport/UI của team khác |
| `package.json`, `package-lock.json` | Dependency chung một app, giữ lockfile khớp |
| `.github/workflows/verify.yml` | Chỉ đổi khi có mục tiêu CI rõ, không vô hiệu test để merge |
| SRS/Contract/reviews | Giữ quản lý version/checksum; source mới không tự phê duyệt proposal |

Đăng ký người phụ trách phần dùng chung trong issue/PR và tích hợp sớm. Một người có thể phối hợp merge, nhưng không phải tự viết thay mọi module.

## 12. Thao tác bằng VS Code

1. Mở đúng folder repo, xem tên branch ở thanh trạng thái.
2. Mở **Source Control** bằng `Ctrl+Shift+G` và xem từng file thay đổi.
3. Tạo nhánh qua tên branch hoặc Command Palette với **Git: Create Branch**; tránh làm việc hằng ngày trực tiếp main.
4. Bấm file để xem diff. Dùng nút `+` của từng file/hunk đã review để Stage; không Stage All khi có nhiều việc chưa tách.
5. Nhập message rồi **Commit**. Nếu VS Code đề nghị stage toàn bộ vì chưa chọn file, quay lại kiểm tra thay vì chấp nhận theo thói quen.
6. Dùng **Publish Branch** hoặc **Push** để gửi nhánh. Kiểm tra remote/branch khi được hỏi.
7. Tạo PR trên web theo mục 7; không cần cài extension PR chỉ để thực hiện hướng dẫn này.

Nút **Sync Changes** có thể thực hiện cả nhận và gửi thay đổi. Khi chưa quen, dùng fetch/pull/push tách riêng để hiểu rõ trạng thái. Không dùng **Discard Changes** thay cho Unstage; discard có thể bỏ sửa đổi.

Tên lệnh có thể khác theo phiên bản/ngôn ngữ VS Code. Xem [hướng dẫn Source Control chính thức](https://code.visualstudio.com/docs/sourcecontrol/quickstart).

## 13. Lỗi thường gặp và khôi phục an toàn

| Hiện tượng | Hướng xử lý |
|---|---|
| `not a git repository` | `cd` vào thư mục clone có `.git`; không tạo repo mới ở thư mục cha để né lỗi |
| `Author identity unknown` | Cấu hình `user.name`/`user.email` của chính mình theo mục 3.4 |
| `Authentication failed` / 403 / `Repository not found` | Kiểm URL, đúng tài khoản, invitation/quyền và credential; không chia sẻ PAT/password |
| Branch đã tồn tại | Dùng `git branch --list` và chuyển sang nhánh đúng, hoặc đặt tên mới; không ghi đè nhánh |
| `nothing to commit` | Kiểm file đã lưu, đúng repo, trạng thái stage/ignore; không tạo sửa đổi giả để có commit |
| Push `non-fast-forward` | Fetch, xem remote feature có commit ai; merge đúng remote branch và test; không force-push |
| Pull main báo không fast-forward | Main local có lịch sử riêng; bảo toàn, xem graph và xử lý với người tích hợp |
| Push bị branch protection chặn | Push feature rồi mở PR; không tắt protection để bỏ quy trình |
| File bị ignored không hiện | Dùng `git check-ignore -v <đường-dẫn-thật>` khi cần; secret bị ignore là đúng, không `git add -f` |
| LF/CRLF warning | Đối chiếu `.gitattributes`, giữ file checksum; không đổi line ending toàn repo vì một warning |
| UI GitHub vẫn chưa có thay đổi | Kiểm đã commit/push chưa, đang xem đúng branch chưa, PR đã merge chưa |
| CI đỏ | Đọc log đúng commit và bước fail, sửa nguyên nhân; không giả PASS/skip để vượt |

### 13.1. Đã commit nhầm trên main nhưng chưa push

Không reset main vội. Tạo nhánh bảo toàn commit hiện tại bằng tên nhánh mới, sau khi kiểm tra trạng thái:

```powershell
git status --short
git switch -c rescue/local-main-work
git log --oneline -5
```

Sau đó nhờ người tích hợp chuyển đúng các commit cần sang feature trên `origin/main` và phục hồi main local theo cách được review. Nhánh rescue chỉ bảo toàn commit; phải giữ thêm các sửa đổi chưa commit, và không push rescue nếu trong đó có secret. Không coi ba lệnh trên là đã sửa xong main.

### 13.2. Commit sai đã được chia sẻ

Với lỗi code thường, ưu tiên commit sửa tiếp. Nếu cần hoàn tác, xác định đúng commit và ảnh hưởng phụ thuộc rồi tạo revert qua nhánh/PR; không viết lại lịch sử chung. Revert merge commit cần hiểu parent và có owner review, không chọn tham số tùy ý.

Nếu secret đã vào lịch sử, xem mục 14: chỉ revert/xóa file ở commit mới không làm secret biến mất khỏi lịch sử.

## 14. Secret, database và dữ liệu demo

### 14.1. Những gì không đưa lên Git

- `.env`, `.env.local`, `.env.staging` hoặc file chứa credential tương tự; `.env.example` chỉ chứa tên biến và mẫu an toàn, không secret thật.
- Password, hash password, PAT/API key, private key, raw session/CSRF/reset/invitation token.
- MySQL dump, Docker volume, dữ liệu sinh viên thật, email/MSSV/CV thật hoặc ảnh màn hình lộ dữ liệu.
- `node_modules`, `target`, `dist`, build/test output bị ignore và log chứa dữ liệu nhạy cảm.

`.gitignore` hiện bỏ qua `.env`, `.env.*` trừ `.env.example` và các build output phổ biến. Không giả định mọi file SQL dump hoặc file secret tên khác đều được ignore; kiểm tra diff và vị trí lưu trước stage. `.gitignore` không tự untrack file đã commit.

Kiểm tra cục bộ, không in nội dung secret:

```powershell
git check-ignore -v .env .env.local
git ls-files -- .env .env.local
```

Nếu file secret đã được tracked, phối hợp xử lý đúng phạm vi và lịch sử; không chạy `git rm -r --cached .` trên toàn repo để chữa một file.

### 14.2. Nếu lỡ push secret

Thu hồi/đổi secret bị lộ trước, thông báo riêng cho owner bảo mật, xác định nơi sử dụng và cập nhật cấu hình ngoài Git. Owner điều phối việc loại bỏ khỏi lịch sử/cache và các clone liên quan nếu cần. Không tự force-push rewrite cả repo; không đưa secret gốc vào issue công khai hoặc chat để báo lỗi. Xóa file mới nhất không đủ vì commit cũ vẫn có thể chứa dữ liệu. Xem [hướng dẫn xử lý dữ liệu nhạy cảm của GitHub](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository).

### 14.3. Git không đồng bộ database

Push migration giúp thành viên nhận được **cấu trúc schema** sau khi cập nhật code và chạy migration đúng môi trường. Tài khoản/dữ liệu tạo trên Docker máy A vẫn ở volume máy A. Thay password admin ứng dụng không tự đổi trên máy B.

Muốn cùng dữ liệu test ban đầu, dùng seed synthetic đã review và hướng dẫn nhập riêng; không push volume/dump của DB đang dùng. Muốn cùng dữ liệu tích hợp đang thay đổi, dùng môi trường staging chung được quản lý. Không tự thêm replication hoặc triển khai server trong một nhiệm vụ Git.

## 15. Gợi ý quản trị repository

Các mục dưới đây là **đề xuất**, chưa xác minh đã thiết lập trên repo. Người có quyền quản trị thực hiện sau khi nhóm thống nhất:

1. Cấp mỗi người quyền phù hợp; chỉ người tích hợp cần quyền quản trị/merge tương ứng, tránh dùng tài khoản chung.
2. Bảo vệ `main` bằng ruleset/branch protection nếu repo và gói tài khoản hỗ trợ; yêu cầu PR, review, giải quyết conversation và checks phù hợp trước merge.
3. Không cho force-push/xóa main, rà soát quyền bypass; cấu hình có hiệu lực với đối tượng nào phải kiểm tra cụ thể.
4. Chọn đúng check/job mà Actions đã thực sự tạo, hiện source khai báo job `verify`; không chọn tên tưởng tượng. Nếu yêu cầu nhánh cập nhật main trước merge, phản ánh điều đó trong quy trình nhóm.
5. Thống nhất **merge commit** nếu muốn giữ chuỗi commit theo chức năng; không giả định squash bảo toàn từng commit gốc trên main.
6. Có thể bổ sung CODEOWNERS/PR template/labels khi được giao. Hiện checkout được kiểm tra chỉ có workflow trong `.github`, chưa thấy các file quản trị này. CODEOWNERS một mình không thay thế việc bật yêu cầu review tương ứng.
7. Ghi owner theo vùng code: T1 nền tảng/security/schema registry, T2 student, T3 internship, T4 matching, T5 cooperation; file chung cần review chéo.

Khả năng branch protection phụ thuộc visibility và gói GitHub; kiểm tra giao diện Settings và [tài liệu protected branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches). Tài liệu này không đổi visibility hay quyền remote.

## 16. Checklist và mẫu báo cáo

### Trước commit/push

- [ ] Đúng repo, remote và nhánh chức năng; đã bảo toàn thay đổi khác.
- [ ] Staged diff đúng một phạm vi rõ ràng, gồm test/tài liệu cần thiết.
- [ ] Không secret/PII/dump/build output; đã xem cả file mới và deletion.
- [ ] Không sửa migration cũ, không đổi contract/checksum ngoài phạm vi.
- [ ] Kiểm tra phù hợp đã chạy, PASS/FAIL/NOT RUN được ghi trung thực.
- [ ] Commit message mô tả hành vi; không đẩy việc dở chưa được phép.

### Trước merge

- [ ] PR đúng base/head; đã xem Files changed và dependency.
- [ ] Owner/consumer review API/permission/schema liên team.
- [ ] CI và các test quan trọng có kết quả thật của source cần merge.
- [ ] Không còn conflict, lỗi quan trọng hoặc quyết định nghiệp vụ chưa chốt trong phạm vi nghiệm thu.
- [ ] Biết chiến lược merge và cách cập nhật các máy sau đó.

Mẫu báo cáo ngắn khi bàn giao:

```text
Nhánh / PR:
Commit đã gửi:
Chức năng / US / FR:
File và API/schema bị ảnh hưởng:
Test đã chạy + evidence:
Test chưa chạy + nguyên nhân:
Dependency / phần chưa hoàn thành:
Reviewer cần xem:
```

## 17. Mẫu giao việc Git cho AI

```text
Làm việc trong repository UT.Bridora hiện tại và tuân thủ các quy tắc trong
docs/guides/HUONG_DAN_PHAT_TRIEN_BE_FE_CHO_CAC_TEAM.md.
Đọc docs/guides/HUONG_DAN_LAM_VIEC_VOI_GITHUB.md.

Yêu cầu của tôi: [chỉ review diff / commit các file được chỉ định /
push nhánh đã xác định / tạo PR khi được ủy quyền rõ ràng].
Phạm vi được đưa vào commit: [chức năng và danh sách file].
Nhánh nguồn: [tên nhánh]; nhánh đích PR nếu có: [main].

Trước thao tác, kiểm tra git status, branch, remote, staged/unstaged diff,
file mới/deletion và dependency. Giữ nguyên thay đổi không thuộc nhiệm vụ.
Không in .env, token, credential hoặc PII. Không stage tất cả mặc định.
Kiểm tra phù hợp và báo PASS/FAIL/NOT RUN; đừng coi evidence cũ là test mới.
Chỉ commit/push/tạo PR đúng phần tôi đã ủy quyền. Không force-push/reset/
clean, sửa migration cũ, đổi visibility/quyền hoặc deploy.
Nếu có secret hoặc nhầm remote/branch, dừng thao tác phụ thuộc và nêu rõ
vấn đề; không tự sửa lịch sử toàn repo hoặc dùng credential người khác.
Báo lại file đã đưa vào commit, SHA, nhánh đã push/URL PR nếu thực sự có,
những thay đổi còn giữ trên máy và các phần chưa hoàn thành.
```

Chỉ gửi prompt này không tự cấp quyền thực hiện mọi bước; phần “Yêu cầu của tôi” phải xác định hành động cụ thể. Lệnh trong tài liệu là hướng dẫn, không phải lệnh AI tự chạy khi chỉ được yêu cầu đọc.

## 18. Tài liệu đối chiếu

- [Trạng thái triển khai](../status/IMPLEMENTATION_STATUS.md), [phát triển BE/FE](HUONG_DAN_PHAT_TRIEN_BE_FE_CHO_CAC_TEAM.md), [triển khai](DEPLOYMENT_GUIDE.md).
- Source kiểm tra: `.github/workflows/verify.yml`, `.gitignore`, `.gitattributes`, Git status/branch/remote/log tại máy. Không fetch/push hay truy cập trạng thái đăng nhập của repo trong lượt viết hướng dẫn này.
- Các liên kết Git/GitHub/VS Code chính thức trong từng mục hỗ trợ thao tác công cụ; chúng không chứng minh repo UT.Bridora đã bật bảo vệ nhánh, phân quyền hoặc CI thành công.
