# Báo cáo kiểm thử và đối chiếu nghiệm thu

## Kiểm tra trước push GitHub — 30/10/2026

- Frontend `npm run build`: **PASS**, TypeScript/Vite production build, 33 module; CSS 39,68kB và JavaScript 281,40kB.
- `./mvnw.cmd -Dfrontend.e2e=true verify -B`: **BUILD SUCCESS**, kết thúc **11:07:57 (UTC+7)**, thời gian 2 phút 51 giây.
- **28 unit, 49 integration/Failsafe, 14 Chromium PASS**, không failure/error/skip. Lượt này chạy lại toàn bộ suite, bao gồm MySQL 8.4 Testcontainers, migration upgrade và các luồng UI. Ba kịch bản Chromium có nhãn UI mock chỉ kiểm chứng hành vi frontend.
- Kiểm tra danh sách source trước commit không phát hiện secret từ cấu hình local, access token/private key theo các mẫu đã kiểm tra, dump database hoặc file build. Đây không thay thế pentest hoặc kiểm toán độc lập. `.env.local` nằm trong `.gitignore`.
- Bằng chứng máy đọc: `PREPUSH_TEST_EVIDENCE.json`. Log thực thi local: `D:\HKDN-TTTV-UTH\.work\github-prepush-verify.log`; JUnit XML trong `target/surefire-reports`, `target/failsafe-reports`, `web-demo/test-results/e2e.xml`.
- Repo đích: private `NguyenThanhTiep/utbridora`; kết quả GitHub Actions cần kiểm tra sau push, không suy ra từ kết quả local.

## Lịch sử kiểm chứng ngày 29/10/2026

Ngày29/10/2026, múi giờ Asia/Bangkok (UTC+7). Lượt xác minh toàn bộ nền tảng trước nâng cấp UI kết thúc **17:47:08**, lệnh `./mvnw.cmd -Dfrontend.e2e=true verify -B`: **BUILD SUCCESS**. Kiểm thử dùng MySQL8.4 Testcontainers và Chromium thật; không dùng H2 thay MySQL, không bỏ CSRF/authorization, không dùng dữ liệu sinh viên thật.

**Cập nhật giao diện 18:56:43:** production build PASS, **28 unit + 1 Failsafe điều phối 14/14 Chromium PASS**. Lệnh lần này giới hạn `CorePlatformIT#frontendMustFlowsInChromium`; không gọi đây là lần chạy lại toàn bộ49 integration test bên dưới. 7 luồng core giữ nguyên, 7 UX mới có3 bài UI mock được ghi rõ. Xem [UI_UPGRADE.md](UI_UPGRADE.md) và [UI_TEST_EVIDENCE.json](UI_TEST_EVIDENCE.json).

## Kết quả thực thi

| Hạng mục | Kết quả | Bằng chứng |
|---|---|---|
| Unit test | **PASS 28**, fail/error/skip=0 | 18 AccountStatusPolicy, 7 AuthorizationPolicy, 2 TokenCodec, 1 AppPrincipalSerialization |
| Integration/Failsafe | **PASS 49**, fail/error/skip=0 | 48 CorePlatformIT + 1 MigrationUpgradeIT |
| Chromium E2E | **PASS 7/7**, retry=0 | web-demo/tests/core.spec.ts; được gọi trong 1 test Failsafe ở trên |
| Frontend TypeScript + Vite build | **PASS** | npm run build, dist được tạo |
| Docker image backend/frontend | **PASS build local** | compose.staging.yaml build; không phải deploy |
| Compose config | **PASS** | config --quiet với biến kiểm tra tạm, không in secret |
| Nginx config | **PASS** | nginx -t trong container dùng một lần |
| Migration V1–V6 | **PASS giữ nguyên** | SHA-256 khớp snapshot trước sửa |
| OpenAPI | **PASS cú pháp JSON**, 19 paths/21 operations | Chưa chạy bộ validator đầy đủ cho OpenAPI schema |
| npm audit | **PASS**, 0 advisory ở mọi mức | Đã tra registry với package-lock hiện tại; không thay thế backend scan/pentest |
| Maven Wrapper Windows | **PASS -version và 28 unit**, exit0 | Wrapper3.3.4/Maven3.9.15; đã sửa null Target trên thư mục .m2 thường của Windows PowerShell; kiểm tra bổ sung kết thúc17:54:01 |
| Backup script | **PASS cú pháp PowerShell** | Chưa thực thi backup/restore thực tế |
| Staging HTTPS/server và GitHub CI | **NOT RUN** | Chưa có server/certificate/repository GitHub được cung cấp |

49 Failsafe đã bao gồm test điều phối7 E2E; không cộng lại thành56 integration độc lập. Tổng JUnit77 test, cộng7 kịch bản browser ở tầng con. Machine-readable summary và checksum ở [TEST_EVIDENCE.json](TEST_EVIDENCE.json). Raw reports tạo lại tại target/surefire-reports, target/failsafe-reports, target/frontend-e2e.log và web-demo/test-results/e2e.xml.

## Baseline và lỗi đã sửa qua test

Baseline backend FAIL compile: thiếu import AuthenticationException; frontend FAIL vì chưa có dependencies; Docker chưa khởi động. Sau khi resolve được dependency giữ nguyên SpringBoot4.1.2. Không có test baseline nào được báo PASS khi chưa chạy.

Kiểm tra wrapper bổ sung: sandbox không nhìn thấy parent POM trong dependency cache nên `-o test` tại sandbox FAIL trước chạy test. Cùng lệnh được chạy lại với quyền đọc cache ngoài sandbox và PASS28 unit, exit0. Đây là giới hạn môi trường tool; không sửa/hạ framework hoặc bỏ test để xử lý.

Trong các lượt phát triển, test phát hiện và đã xác minh sau sửa:

- Harness khởi động context trước container, rồi đăng ký endpoint test trùng: đã sửa harness; không bỏ test để né lỗi.
- Reset đồng thời nhận200 cả hai do snapshot MySQL: đổi consume sang conditional UPDATE; final chỉ một200, một400.
- Denial audit ghi hai lần do error dispatch: handler trả JSON403 trực tiếp; test đếm đúng một correlation event.
- Health503 khi SMTP vắng: health identity/readiness độc lập SMTP, vẫn kiểm tra DB; delivery cảnh báo riêng.
- Lỗi audit ở database phải rollback password/version: dùng CHECK constraint tạm trong database dùng một lần để inject failure, sau đó gỡ constraint.
- E2E lượt đầu4/6 PASS, hai lỗi chờ logout và locator: đã sửa đồng bộ chờ và dùng accessible combobox selector; lượt cuối7/7 PASS.

## Đối chiếu US-T1-01 đến US-T1-12

PASS dưới đây là **test trong phạm vi nêu rõ**, không tự động là nghiệm thu toàn bộ nghiệp vụ/SRS. Nghiệm thu chính thức vẫn cần review/sign-off và những phần còn chờ.

| US | Code thực tế | Test đã thực thi | Kết quả / trạng thái nghiệm thu |
|---|---|---|---|
| US-T1-01 Đăng nhập | AuthController, SecurityConfig, AccountService, AppUserDetailsService | loginRotatesSessionStoresJdbcContextAndReturnsSafeMe; failedLoginUnknownLockedAndSuspendedReturnSameResponse; fiveFailuresTemporarilyLockCredentialWithoutChangingStatus; allFourRolesUseTheSameAuthenticationAndPermissionApi; E2E login | **PASS local**; chờ HTTPS, timing enumeration và gateway throttling trước công khai |
| US-T1-02 Đăng xuất | AuthController + SecurityContextLogoutHandler + Spring Session | logoutInvalidatesSessionAndMissingCsrfCannotLogout; csrfTokenBeforeLoginIsRejectedAfterAuthentication; E2E logout | **PASS phạm vi T1 local**, sẵn sàng review |
| US-T1-03 Tài khoản hiện tại | me/me-permissions, EffectivePermissionService, DTO | loginRotatesSessionStoresJdbcContextAndReturnsSafeMe; allFourRoles...; AppPrincipalSerializationTest | **PASS phạm vi T1 local**, không lộ hash/version |
| US-T1-04 Kích hoạt/reset | AccountTokenService, TokenRepository conditional consume, EmailService, React AuthForm | invitationCreatesOneAccountAndExpiredInvitationIsRejected; resetTokenIsOneTimeExpiresAndRevokesSessions; concurrentResetCanConsumeTokenOnlyOnce; forgotResponseDoesNotRevealAccountOrSmtpFailure; E2E activate/reset | **PASS invite/reset hiện có; PARTIAL toàn phạm vi**: PENDING provision/activation riêng còn OPEN; outbox/retry và timing chưa xong |
| US-T1-05 Membership | MembershipService, AdministrationGuard, AdminController, React AccountDetail | grantIsIdempotentWrongTypeIsRejectedAndRevokeInvalidatesSessions; concurrentAdminRevocationsRetainOneActiveAdministrator; E2E membership | **PASS phạm vi T1 local**, scope mapping/idempotence/revoke có test |
| US-T1-06 Tài nguyên | AuthorizationPolicy, method security, denyAll fallback | anonymousPrivateApiIs401AndMockMvcCsrfIs403; roleAndOrganizationIdorAreDeniedWithCorrelatedAudit; organizationTamperingInBodyAndAdminBusinessAccessAreDenied; policy unit | **PASS guard T1**, chờ owner/state/Placement tests trong API thật T2–T5 |
| US-T1-07 Cô lập doanh nghiệp | hasOrganization, hasOrganizationForRequest, OrganizationAccess | URL/body tampering; changedOrganizationStateAndMultipleMembershipsAreRechecked; noMembershipAndDefaultProfileSharingAreDeniedIncludingAdmin | **PASS guard và endpoint mẫu**, chưa nghiệm thu tài nguyên riêng thật của T2–T5 |
| US-T1-08 Chia sẻ hồ sơ | ProfileVisibilityPort, DenyAllProfileVisibility, canReadStudentProfile | Default deny HTTP/unit; positive policy với mock adapter | **PASS deny-by-default; CHỜ T2/T5**: consent/field/time/purpose thật NOT RUN |
| US-T1-09 Schema/ID | FlywayV1–V7, entities/repositories, core_locks | MySQL startup+JPA validate; MigrationUpgradeIT; unique/FK/global membership; session JDBC | **PASS schema sạch và upgrade dữ liệu mẫuV2**; clone DB chung/restore drill NOT RUN |
| US-T1-10 Khóa/mở | AccountLifecycleService, AccountStatusPolicy, history, React AccountDetail | 16 cặp lifecycleTransitionMatrixIsEnforcedInDatabase; 16 unit cặp; statusHistoryAndAuditCommitTogetherAndOldSessionDoesNotReturn; self-disable; 2 concurrency tests; E2E status | **PASS phạm vi T1 local**, không mở DEACTIVATED hoặc ép PENDING ACTIVE |
| US-T1-11 Audit | AuditAction, AuditService, RequestIdFilter, Admin audit API, React AuditViewer | adminReadsAreBoundedFilteredAndNeverExposeSecrets; businessRollbackRollsBackAuditAndPasswordChange; failedAuditRollsBackCredentialMutation; correlated denied; E2E audit | **PASS audit T1**, chờ event nghiệp vụ T2–T5, retention/append-only vận hành |
| US-T1-12 UI/API/môi trường | React screens/components, OpenAPI, Dockerfiles, Compose, health, wrapper, CI | TS/Vite build; 7 Chromium scenarios; mobile overflow/cookie/localStorage; image build/nginx config | **PASS local UI/build; PARTIAL bàn giao vận hành**: server HTTPS, CI thật, backup/restore và sign-off NOT RUN |

## Truy vết SEC-01 đến SEC-28 của SRS

| SEC | Tình huống | Kết quả thực thi |
|---|---|---|
|01| Private API anonymous | PASS401, MockMvc + HTTP |
|02| Login + me | PASS4 role; session JDBC và fixation |
|03| Thiếu/sai CSRF | PASS403; token trước login không dùng sau login |
|04| Student gọi admin status | PASS403 |
|05| Enterprise A đổi org URL sang B | PASS403 trên endpoint scope mẫu |
|06| Org PENDING dùng quyền cần VERIFIED | PASS403 |
|07| Profile chưa share | PASS deny HTTP/unit |
|08| Share thật, đúng field/consent/time | **NOT RUN – chờ T2/T5**; chỉ mock allow policy PASS |
|09| Grant role sai org type | PASS400 |
|10| Grant trùng | PASS no-op, một membership, version không tăng |
|11| Revoke membership, phiên cũ | PASS401 |
|12| ACTIVE→SUSPENDED | PASS401 phiên cũ |
|13| SUSPENDED→ACTIVE | PASS login mới, phiên cũ vẫn401 |
|14| Admin ép PENDING→ACTIVE | PASS bị từ chối |
|15| DEACTIVATED→ACTIVE | PASS bị từ chối |
|16| Tự khóa/thu hồi ADMIN | PASS bị từ chối; bảo vệ admin cuối còn test đồng thờiSEC17 |
|17| Hai admin khóa hoặc revoke lẫn nhau đồng thời | PASS, giữ một admin active; actor cũ bị từ chối |
|18| Đổi/reset password | PASS thu hồi phiên và credential mới |
|19| History/audit khi đổi status | PASS actor/from/to/reason/version |
|20| Denial audit | PASS một event/correlation, không URL/body |
|21| Student/enterprise đọc global audit | PASS403 (HTTP/E2E) |
|22| Admin audit paging/filter | PASS; giới hạn size/time; DTO không secret |
|23| Migration schema sạch | PASS MySQL8.4 V1–V7 + JPA validate |
|24| Nâng cấp dữ liệuV2 | PASS dữ liệu mẫu; **NOT RUN clone database chung** |
|25| File chưa scan không download public | **NOT RUN workflow download/AV**; chưa có API download, metadata-only |
|26| Rollback business không để audit SUCCESS giả | PASS transaction nền tảng; **NOT RUN nghiệp vụ T5 thật** |
|27| Nhiều org membership và đổi scope | PASS guard URL/body; business row thực tế chờ team sở hữu |
|28| Org SUSPENDED sau login | PASS403 request sau |

## Giới hạn và việc tiếp tục

Không còn failure/error/skip ở suite cuối. Warning PageImpl serialization còn tồn tại: giữ contract hiện hành; client chỉ dùng các field tài liệu hóa, cần version hóa response khi nâng framework. Warning Mockito dynamic agent trên Java21 và warning màu terminal Playwright không làm test fail. CHECK constraint audit failure và SMTP failure warning trong test là fault injection có chủ đích.

Chưa có durable email outbox/retry. Forgot trả body/status giống nhau cả SMTP lỗi nhưng thời gian đáp ứng có thể khác; chưa chứng minh chống enumeration theo timing. Chưa có limiter IP dùng chung/gateway test, load test, pentest/dependency scan đầy đủ, MFA, WORM, retention đã ký, upload/AV hoặc diễn tập backup/restore. Không đánh dấu các phần này PASS và không tuyên bố production-ready.

## Lệnh tái hiện

Bổ sung launcher local: `run-local.cmd start` đã build và khởi động đủ4 container từ `compose.local.yaml`, chờ health check thành công. Smoke test qua frontend/Nginx PASS: trang React, admin sinh tự động, session HttpOnly, API quản trị, POST thiếu CSRF trả403, logout khiến me trả401, Mailpit truy cập được. Kiểm tra chỉ dùng database local riêng; không in credential/token vào log. Bộ28 unit/49 Failsafe/7 Chromium ở trên là bằng chứng của ứng dụng; smoke launcher được ghi riêng, không cộng thêm vào số test JUnit.

Launcher stop/start cũng PASS: dùng lại volume cũ, SHA-256 `.env.local` không thay đổi. Đã thử khởi động từ shell có credential khác cho cách chạy thủ công; launcher vẫn dùng credential riêng của `.env.local`, cả4 service healthy sau restart. Ứng dụng local được để chạy để người dùng kiểm tra; không triển khai server staging/production.

```powershell
cd web-demo
npm ci
npm run build
npx playwright install chromium
cd ..
./mvnw.cmd -Dfrontend.e2e=true verify
```

Docker Linux engine phải hoạt động và tải được image mysql:8.4/Testcontainers. Không có Docker/mạng: ghi NOT RUN hoặc FAIL đúng nguyên nhân, không tắt security hoặc đổi DB để giả lập PASS. CI tương ứng đã có workflow; Git và remote đã được cấu hình ngày 30/10/2026, kết quả GitHub Actions cần kiểm tra riêng sau push.

## Backend IDE dùng MySQL Docker — 30/10/2026

- `application.yml`: mặc định `127.0.0.1:3307/utbridora`, user `bridora`, import tùy chọn `.env.local` như Java Properties. Mật khẩu vẫn dùng `${DB_PASSWORD}`, không sao chép vào source.
- `./mvnw.cmd package -B`: **BUILD SUCCESS**, **28 unit test PASS**, 0 failure/error/skip; kết thúc 10:19:37 (UTC+7). Log tại `D:\HKDN-TTTV-UTH\.work\docker-ide-config-build.log`.
- Smoke bằng JAR vừa đóng gói: **PASS**. Chạy từ thư mục chứa `pom.xml`, loại các biến datasource/config import trong riêng tiến trình thử để kiểm chứng cấu hình mặc định và file `.env.local`. Backend khởi động trên cổng tạm, startup log xác nhận JDBC URL `127.0.0.1:3307/utbridora`, readiness HTTP 200 với trạng thái `UP`. Tiến trình thử đã được dừng sau kiểm tra.
- Đây là kiểm tra với MySQL Docker local đang chạy. Không chạy lại toàn bộ 49 integration test, 14 Chromium, hoặc kiểm thử gửi SMTP trong lượt này. Script kiểm tra và log nằm trong `D:\HKDN-TTTV-UTH\.work\docker-ide-config-smoke*`; không in credential ra báo cáo.

## Kiểm tra tăng cỡ chữ toàn giao diện — 29/10/2026

- TypeScript/Vite production build: **PASS**, CSS cuối `index-DirwZUWJ.css` khoảng 39,68kB, gzip 8,94kB.
- Lệnh `./mvnw.cmd -Dfrontend.e2e=true -Dit.test=CorePlatformIT#frontendMustFlowsInChromium verify -B`: **PASS 28 unit, 1 Failsafe và 14/14 Chromium**, không failure/error/skip; kết thúc 19:34:29 (UTC+7). Ba kịch bản có nhãn UI mock chỉ chứng minh hành vi frontend. Không chạy lại toàn bộ 49 backend integration test trong lượt này.
- Bốn quy tắc CSS select/hướng dẫn/lưu ý được tăng bổ sung sau lượt hồi quy. Bản cuối đã build lại và kiểm tra bằng Chromium trên Docker local: **PASS** cỡ chữ thực tế body/input 16px, menu/nút/bảng 15px, hướng dẫn/lưu ý 14px; không tràn toàn trang ở màn tài khoản 320/390/901/1051/1280px và chi tiết tài khoản 320/901/1051px; icon mật khẩu căn giữa.
- Đã xem ảnh desktop/mobile có dữ liệu cho dashboard, users, organizations và audit; ảnh bổ sung tại `web-demo/test-results/typography-*.png`. Ảnh local được chụp với `animations: disabled` để loại trạng thái menu giữa chuyển động.
- Chỉ cập nhật Docker frontend local; health **PASS**, JavaScript/CSS/favicon tải được, không lỗi runtime/CSP. Smoke dùng tài khoản demo local, giữ credential trong bộ nhớ và đăng xuất sau kiểm tra.
- Chi tiết và bằng chứng máy đọc: `docs/UI_UPGRADE.md`, `docs/UI_TEST_EVIDENCE.json`. Firefox/Safari, thiết bị vật lý và audit WCAG đầy đủ: **NOT RUN**.
