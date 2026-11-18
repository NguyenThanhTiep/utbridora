# T2-11 — Consent revoke/expiry, school policy và scope

DEV04 — Lê Văn Chương; tuần 4; QA02. Nguồn: Plan, sheet CAP NHAT CHUNG,
dòng 61; handoff T2/T5 tuần 3→4; SRS v1.2.4; contract v1.0 BC24.
Acceptance: **Tài khoản doanh nghiệp/nhà trường sai phạm vi phải bị từ chối.**

## Ma trận trước triển khai

“Đã có” nghĩa là đã tìm thấy test trong source, không có nghĩa đã chạy PASS ở lượt này.

| Trường hợp | Test kế thừa | Phần bổ sung T2-11 |
|---|---|---|
| Revoke consent chặn profile/decision | EnterpriseProposalIT.revokedConsentImmediatelyHidesProfileAndDeniesDecision | Đối chứng dương, JSON lỗi, replay decision đã thành công |
| Consent expiry | Week2WorkflowIT.actualDelegationPolicyMatchingAndCooperationUseOwnerSourcesAndRollback → consentEdges | HTTP profile/list/decision/replay khi share vẫn còn hạn |
| Regrant không phục hồi share cũ | EnterpriseProposalIT.consentRegrantRequiresExplicitSchoolReshareWithOnlyNewFieldsAndExpiry | Giữ regression |
| Share revoke/expiry | EnterpriseProposalIT.resharingAnExpiredOrRevokedShareKeepsOldUrlsBlockedAndRejectsTerminalStates | Giữ regression |
| Policy thu hẹp sau share | Chưa có matrix HTTP riêng | Whitelist giảm, field bị loại omit; policy rỗng deny; replay deny |
| Enterprise khác recipient | EnterpriseProposalIT.rejectsOtherEnterpriseAndMissingPermissionsAndCsrf | Đối chứng dương, generic profile và JSON lỗi không PII |
| Thiếu quyền shared profile sau lần đọc hợp lệ | Chưa có matrix riêng | Profile/list/decision/replay và file deny |
| School khác/sai term/revoked/expired delegation | Có regression delegation/Placement | School profile, policy và proposal HTTP trên cùng tài nguyên |
| CV GET/Range sau revoke | EnterpriseProposalIT.sharedCvUsesOneT1ProviderAndChecksBindingCleanConsentAndEveryRange | HTTP metadata/content/Range sau expiry, policy hoặc permission loss |
| Placement không tự cancel sau revoke | EnterpriseProposalIT.approvedPlacementAndCooperationRemainReadableAfterConsentRevocation | Giữ regression, không sửa lifecycle T5 |

## Môi trường và giới hạn

- Provider thật T2 consent/projection, T5 share/policy/proposal và T1 file/authorization.
- Tái dùng MySQLContainer mysql:8.4, random HTTP port và storage tạm của EnterpriseProposalIT.
  Datasource lấy trực tiếp từ container; migration chỉ chạy trong DB mới của container.
- Không trỏ test vào DB hiện có; không sửa migration, production T1/T5, UI hoặc task tuần 5.
- CLEAN là fixture đầu ra scanner; expiry riêng consent được tạo bằng cách chỉnh timestamp
  trong DB tạm để cô lập guard consent khỏi guard share/reservation, không chờ sleep.
- Trạng thái ban đầu: chưa chạy test mới. Kết quả cuối ghi bên dưới sau kiểm tra.

## Kết quả lượt triển khai

Đã xác minh trên working tree nhánh `feature/t2-week4`, HEAD nền
`f08c3cb12cef1a01f30778ab12eea4b28006b8f0`, ngày 14/11/2026 (Asia/Bangkok).

| Bộ test, kết quả mới nhất | PASS | FAIL | ERROR | SKIP |
|---|---:|---:|---:|---:|
| EnterpriseProposalIT, chạy lại toàn bộ trên fixture cuối | 54 | 0 | 0 | 1 |
| Week2WorkflowIT, bao gồm sáu check mới trong consentEdges | 1 | 0 | 0 | 0 |
| Tổng kết quả mới nhất từng suite | **55** | **0** | **0** | **1** |

**14/14 ca HTTP mới T2-11 PASS**, nằm trong EnterpriseProposalIT, không cộng
lặp vào tổng. Sáu check Week2 nằm trong một test workflow kế thừa.
Biên dịch test và `git diff --check` PASS. Không phát hiện lỗi production cần sửa
guard/service trong matrix này; thay đổi chỉ ở hai file test, báo cáo và evidence.

SKIP: `browserUsesRealSchoolStudentEnterpriseSharingAndPlacementHandoff`, harness
kế thừa không bật `t5.week3.browser`; không phải ca HTTP T2-11 mới.
Browser/human QA và scanner runtime không được tuyên bố PASS.

Evidence có SHA-256 source, JUnit và log: [T2_11_VERIFICATION.json](evidence/T2_11_VERIFICATION.json).
JUnit bản cuối từng suite được giữ tại `target/t2-11-evidence/final/`;
log Enterprise cuối: `target/t2-11-enterprise-final.log`; log Week2 PASS nằm trong
`target/t2-11-regression.log` (lượt này tổng build đỏ vì fixture Enterprise cũ,
đã được chạy lại toàn bộ và PASS riêng). Kết quả được tổng hợp theo suite,
không tuyên bố lượt regression cũ BUILD SUCCESS.

QA02/Lead nghiệm thu riêng; chưa ký thay QA hoặc cập nhật trạng thái workbook.

## Test bổ sung và đối chứng

| Test | Số invocation | Đối chứng và kiểm tra âm tính |
|---|---:|---|
| EnterpriseProposalIT.t211CurrentAuthorityBlocksProfileProposalAndSuccessfulDecisionReplay | 4 | Profile hai route và list 200; decision/replay 200; sau revoke/expiry/policy rỗng/permission loss: list rỗng, profile deny, decision mới và replay deny; version/audit không tăng |
| EnterpriseProposalIT.t211PolicyNarrowingOmitsRemovedFieldsOnBothProfileRoutes | 1 | Ban đầu ba field; PUT policy thật giảm còn skills; cả hai route chỉ trả skills, request chỉ field đã loại trả 403; stale policy 409 |
| EnterpriseProposalIT.t211ForeignRecipientCannotReadEitherProfileRouteOrReplayDecision | 1 | Own recipient 200; org khác 404 trên list/profile/decision replay; principal chỉ thuộc recipient khác bị generic profile HTTP 404 |
| EnterpriseProposalIT.t211FileHttpRechecksAuthorityOnMetadataContentAndRange | 4 | Metadata/content 200, Range 206 và đúng bytes; sau revoke/expiry/policy loại cv/permission loss: cả ba HTTP 404, không bytes/tên gốc/Content-Range/Content-Disposition |
| EnterpriseProposalIT.t211SchoolScopeBlocksProfilePolicyProposalAndPolicyReplay | 4 | School profile chỉ fields được delegation, policy/proposal 200, policy replay 200; revoke/expiry/sai term/trường khác: cả read và replay 404. Scope thay thế có GET policy 200 bằng session mới |
| Week2WorkflowIT.consentEdges trong workflow hiện có | 6 checks thêm | Consent hợp lệ trả skills/hạn; sai student/school/term/purpose/version/field: valid=false, fields rỗng, không hạn; sai scope không lộ consent version |

Lỗi HTTP còn được kiểm tra không có data/content/allowedFields, student ID, email,
phone, tên file gốc hoặc marker bytes riêng tư. Các thay đổi fixture được khôi phục
trong finally để giữ các test kế thừa độc lập. Không thêm mock thay provider.

## Lệnh kiểm tra

JDK 21, Docker Desktop, cache Maven có sẵn. Lệnh bên dưới chỉ khởi chạy IT
được chỉ định, không khởi chạy browser hoặc ứng dụng trên DB cấu hình local.

```powershell
.\mvnw.cmd -B '-Dit.test=EnterpriseProposalIT#t211*' test-compile failsafe:integration-test failsafe:verify
.\mvnw.cmd -B '-Dit.test=EnterpriseProposalIT,Week2WorkflowIT' test-compile failsafe:integration-test failsafe:verify
```

Lượt local thêm `-o '-Dmaven.repo.local=C:/Users/DELL/.m2/repository'` để dùng
cache hiện có, và `-l target/t2-11-new-it.log` / `-l target/t2-11-regression.log`.
Không thay settings Maven hoặc cấu hình DB của repo.

Lượt đầu: 14 ca mới, **10 PASS / 4 FAIL / 0 ERROR / 0 SKIP**. Bốn FAIL do
fixture chọn toàn bộ delegation rồi giả định có một row; seed còn delegation
T3 riêng. Đã sửa selector theo `decision_ref=DEMO-WEEK3-TEST`, giữ nguyên
assertion quyền/HTTP. Bằng chứng lượt lỗi ở `target/t2-11-evidence/initial/`.

Lượt regression tiếp theo: EnterpriseProposalIT **53 PASS / 0 FAIL / 1 ERROR /
1 SKIP**; Week2WorkflowIT **1 PASS**. ERROR còn lại là fixture trường B dùng
organization type `SCHOOL`, trong khi core yêu cầu `UNIVERSITY`; bootstrap từ chối
đúng trước khi tới kiểm tra âm tính. Đã sửa loại tổ chức của fixture, không sửa guard
hoặc assertion. Bằng chứng ở `target/t2-11-evidence/regression-fixture/`.
Enterprise được chạy lại toàn bộ với `-Dit.test=EnterpriseProposalIT`, log
`target/t2-11-enterprise-final.log`; Week2 không đổi sau lượt PASS.

## Đối chiếu nghiệm thu

- Dòng 61 Plan: evidence phải chứng minh enterprise sai recipient/permission và
  school sai trường/term bị từ chối, cả sau một lần đọc hợp lệ và khi replay.
- T2-06: giữ grant/revoke/version, không trả dữ liệu sau expiry/revoke/replaced.
- T5-05: invitation ACCEPT vẫn độc lập consent; T5 giữ proposal/decision/share.
- QA02 xác nhận nghiệp vụ, tính đầy đủ matrix và lỗi không lộ dữ liệu; Lead/QA
  ký nghiệm thu riêng. Không cập nhật Plan thành “đã nghiệm thu” thay QA.
