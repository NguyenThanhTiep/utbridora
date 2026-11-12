# Bàn giao T5 — kết quả tuần 2 và hướng dẫn tuần 3

**Cập nhật T1-06 ngày 10/11/2026:** bản DEV trên nhánh bàn giao `feature/t1-06-private-files` có private upload cho verification draft, owner read và FileReadPort tối thiểu; chưa deploy. Chưa có scanner, school/shared read hay consent/share authorizer cho CV/evidence; nhánh ProfileShare tương ứng vẫn fail closed. Xem [boundary tích hợp mới](../guides/T1_06_PRIVATE_FILES.md); không hiểu FileReadPort đã tồn tại là shared file flow đã hoàn thành.

Ngày lập: 09/11/2026. Người nhận: Team Doanh nghiệp/Hợp tác, DEV10/DEV11/DEV12 và QA05. Tuần 3 tiếp tục Share/Enterprise decision → Placement → Case T3 và UI, sử dụng Cooperation/nomination/invitation đã triển khai.

## 1. Nhận đúng source và căn cứ

- Bản phát hành tuần 2 trên `main` chứa code, tests và tài liệu bàn giao này; base `6687a940cd2f42a6351fb6c3511286c763c81f6b`, nhánh triển khai `feature/week2-implementation`. Nhận đúng commit tuần 2 bằng fetch/pull trên checkout sạch của team và kiểm Git log.
- Worktree tạo bản bàn giao: `D:/HKDN-TTTV-UTH/.work/week2-20261109/repo`. Kiểm source ở mục 3 và migrations đến V27. T5 tiếp tục trong cùng backend/auth và schema canonical; mỗi máy dev vẫn dùng database môi trường riêng theo LOCAL_DEMO.
- Hash snapshot source LF: `77e7dda5c8e9e5d6f035609e4f2ac06b8ac6d8f7ca557c9c47e5ca9a5f932e63`, không phải commit SHA.
- [Plan](../plans/UTBridora_Plan.xlsx), CAP NHAT CHUNG: tuần 2 dòng 34–36; tuần 3 dòng 50–52, QA05 dòng 57. T5-06 DEV11; T5-07 DEV10; T5-09 DEV12.
- [SRS v1.2.4](../srs/UTBridora_SRS_Tong_The_v1.2.4.docx): COOP, PROP-01–16, PLC-01/03/04/05, §25.8.1, G08/G09/G14/G18.
- [Contract v1.0](../contracts/UTBridora_Contract_Pack_B01_B06_v1.0_Candidate.md): BC21/22/23/24/31/41/42, CP-T5UI1, CP-FILE1 và registry D/E. [Quyết định chốt](../contracts/README.md) đã có; không yêu cầu các team ký lại chỉ vì nhãn PROPOSED trong tài liệu lịch sử.
- [Tuần 2 acceptance](../testing/WEEK2_ACCEPTANCE.md), [evidence](../testing/evidence/WEEK2_DEV_VERIFICATION.json), [runtime](../guides/WEEK2_RUNTIME.md) là trạng thái mới, ưu tiên hơn mô tả T5 chỉ có schema/shell trong báo cáo cũ.

Evidence giữ nguyên trạng thái tại thời điểm kiểm chứng trước commit (`uncommitted=true`, CI chưa chạy khi ghi nhận); xem Git log và GitHub Actions của commit nhận được để biết trạng thái phát hành/CI mới nhất.

## 2. Tuần 2 đã làm được gì

| Task/owner | Implementation | Evidence và giới hạn |
|---|---|---|
| T5-04 — DEV11/QA05 | Cooperation DRAFT, atomic submit Demand DRAFT→READY + Cooperation SUBMITTED; approve/request-info/reject/resubmit/reopen; history snapshot; kiểm verification hiện hành | Week2WorkflowIT: replay, stale, request-info/resubmit/reject/reopen, history bất biến, lỗi audit rollback cả Demand/Cooperation |
| T5-05 — DEV10/QA05 | Nominate từ run MATCH/current, authoritative schoolApproved, một batch/cooperation, quota/reservation, invitation owner T5, student accept/decline/withdraw/version/history/expiry | 20 ACCEPT race và 20 mixed ACCEPT/DECLINE chỉ một winner; school không nhận thay; expired bị chặn; không auto consent/Placement |
| T5-08 — DEV12/QA05 | UI Verification/Demand/Cooperation nối API; trường review thật, doanh nghiệp gửi/sửa/gửi lại; dùng lại DemandEditor | Browser Cooperation approve/reload và Demand create/edit/reload; chưa có upload/scan mới để đi KYC từ đầu |
| Phần tối thiểu T5-06 kéo trước | SchoolSharingPolicy, grant/revoke share, whitelist ProfileProjection, current consent check khi đọc | Grant consent/share thật, enterprise đọc đúng fields rồi bị chặn ngay sau revoke; chưa có enterprise proposal decision/Placement workflow đầy đủ |
| Nền BC41 từ tuần 1 | PlacementReadPort/JdbcPlacementRead, PlacementLifecyclePublisher/outbox worker, T3 inbox/consumer và Case guard | Integration source/event/duplicate/stale/rollback đã có; không coi publisher hoặc bảng placements là API approve đã xong |

Kết quả tự động chung: 485 unit PASS, 125 integration PASS theo báo cáo mới nhất từng class, 87 Playwright PASS + 4 skipped theo fixture, build và 868 contract checks PASS. Full run có một failure helper login/CSRF của browser Week2; sửa test, chạy lại riêng thành công. Chưa có human QA/Lead signoff hoặc xác nhận truy cập từ 5 team.

## 3. Source cần kế thừa

| Source | Vai trò |
|---|---|
| [CooperationService](../../src/main/java/vn/utbridora/auth/modules/cooperation/application/CooperationService.java), [controller](../../src/main/java/vn/utbridora/auth/modules/cooperation/api/CooperationController.java) | Canonical submit/review/history; composed detail UI đã có |
| [DemandService](../../src/main/java/vn/utbridora/auth/modules/cooperation/application/DemandService.java), [DemandCriteriaFacade](../../src/main/java/vn/utbridora/auth/modules/cooperation/integration/DemandCriteriaFacade.java) | Criteria/revisions/hash, current catalogs và scope; không nhận criteria của browser làm authoritative |
| [VerificationService](../../src/main/java/vn/utbridora/auth/modules/cooperation/application/VerificationService.java) | School verification độc lập core org VERIFIED; source current cho coordinator/Placement |
| [ProposalService](../../src/main/java/vn/utbridora/auth/modules/cooperation/application/ProposalService.java), [ProposalController](../../src/main/java/vn/utbridora/auth/modules/cooperation/api/ProposalController.java) | Batch/item/reservation, canonical nominate route và invitation T5 |
| [ProfileShareService](../../src/main/java/vn/utbridora/auth/modules/cooperation/application/ProfileShareService.java), [SchoolSharingPolicyService](../../src/main/java/vn/utbridora/auth/modules/cooperation/application/SchoolSharingPolicyService.java) | Lát cắt BC24 đã chạy; tiếp tục hoàn thiện trên chính owner này |
| [StudentConsentPort](../../src/main/java/vn/utbridora/auth/modules/student/port/StudentConsentPort.java), [eligibility port](../../src/main/java/vn/utbridora/auth/modules/student/port/StudentEligibilityPort.java) | T2 owner; lock/check consent và eligibility thật, không tạo bản sao approved/consent tại T5 |
| [PlacementLifecyclePublisher](../../src/main/java/vn/utbridora/auth/modules/cooperation/application/PlacementLifecyclePublisher.java), [worker](../../src/main/java/vn/utbridora/auth/modules/cooperation/application/PlacementOutboxWorker.java), [JdbcPlacementRead](../../src/main/java/vn/utbridora/auth/modules/cooperation/integration/JdbcPlacementRead.java) | Hook cùng transaction, private source read và at-least-once delivery |
| [V20](../../src/main/resources/db/migration/V20__placement_read_and_delivery.sql), [V24](../../src/main/resources/db/migration/V24__cooperation_proposals_invitations.sql), [V25](../../src/main/resources/db/migration/V25__consent_and_profile_sharing.sql) | placements/outbox, batch/item/invitation, policy/consent/share đã có |
| [WorkflowViews](../../web-demo/src/modules/cooperation/WorkflowViews.tsx), [SchoolWorkspace](../../web-demo/src/modules/cooperation/SchoolWorkspace.tsx), [DemandWorkspace](../../web-demo/src/modules/cooperation/DemandWorkspace.tsx) | UI thật cần mở rộng; không quay về shell hoặc mock approval |

## 4. API đã có và phần chưa có

| Trạng thái | Route/behavior |
|---|---|
| Đã có | POST `/api/v1/enterprise/demands/submit-with-cooperation` và lifecycle Cooperation theo CP-T5UI1 |
| Đã có | GET `/api/v1/{school\|enterprise}/cooperations`, GET detail `.../{id}`, school submission-history |
| Đã có | POST `/api/v1/matching/runs/{id}/nominate` — mapping hiện ở ProposalController T5, không tạo route batch thứ hai |
| Đã có | GET `/api/v1/school/cooperations/{id}/capacity`, `.../proposals`; POST `/api/v1/school/proposals/{id}/invite` |
| Đã có | Student GET `/api/v1/invitations/me`, detail, decision và withdraw qua owner T5 |
| Đã có | GET/PUT school term profile-sharing-policy; POST school proposal shares; POST profile-share revoke |
| Đã có | GET `/api/v1/students/{studentUserId}/profile?termId=...&shareId=...&fields=majorCode,skills&purpose=INTERNSHIP_SELECTION` |
| Chưa có runtime | Enterprise proposal list/decision, public Placement create/approve/activate/cancel/list/summary/link |
| Chưa có runtime | Opportunity publication/projection phục vụ T2-07/08; enterprise Matching aggregate thuộc T4-05 |

Đối với Cooperation, gửi X-Organization-Id là enterprise khi thao tác enterprise, school khi school review. Mutation dùng cookie/CSRF + Idempotency-Key UUID; owner/scope/current authority vẫn do server xác minh.

## 5. Các khoảng trống cần giải quyết trước luồng Placement tuần 3

Đây là đối chiếu implementation với các quyết định đã chốt, không phải yêu cầu chốt lại:

| Phát hiện tại snapshot | Hướng xử lý |
|---|---|
| ProposalService đặt reservationUntil=min(now+24h,deadline) khi RESERVED; invite/ACCEPT hiện chưa cập nhật TTL theo từng phase | Hoàn thiện G09: RESERVED tối đa 24h; INVITED đến invitation.expiresAt; ACCEPTED/SHARED/ENTERPRISE_ACCEPTED tối đa min(studentAcceptedAt+7 ngày, consent expiry nếu có, deadline). Expire/release đúng một lần, giữ history |
| capacityOf hiện đếm placed bằng proposal_items.state=PLACED | T5-07 phải tính DISTINCT placements của demand ở APPROVED/ACTIVE/COMPLETED, loại CANCELLED; tránh đếm đôi item đã Placement còn reservation |
| V20 chưa có active-key UNIQUE(student,term) cho APPROVED/ACTIVE | Thêm business lock + DB constraint/key phù hợp; chỉ index/service check không đủ chống race |
| StudentEligibilityPort chỉ có assertEligibleForNomination | Cùng T2 bổ sung BC21 assertEligibleForPlacement với expectedEligibilityVersion và lock tới commit |
| ProfileShareService tích hợp guard/projection trực tiếp, chưa có toàn bộ façade/typed ports BC24 | Hoàn thiện boundary ProfileAccess với T1/T2, dùng cùng nguồn; không bỏ endpoint/read check đã có hoặc mở Maps chứa PII tùy ý |
| Share revoke không hoàn thiện toàn bộ vòng cấp share mới sau consent regrant; grant hiện đòi item ACCEPTED | Thêm ca cấp share mới theo state/version/history hợp lệ BC24; giữ nguyên nguyên tắc regrant không hồi sinh share cũ, không sửa student decision để né gate |
| cv/evidence còn thiếu FileRead provider | Với dữ liệu file thật đang fail-closed/DEPENDENCY_UNAVAILABLE; nối T1 CLEAN/binding/consent trước khi công bố file flow PASS |
| Invitation DTO đang lấy opportunityId từ demand_id | Sửa mapping khi ghép BC22: opportunityId=cooperationId; test T2 consumer, không đổi request decision owner/version |

Giữ nguyên V1–V27 đã áp dụng. Migration mới phải kiểm dữ liệu legacy/demo trong V20 trước khi thêm FK/constraint; các ref synthetic cũ không chứng minh đã có Cooperation/approval thật. Không “sửa” bằng reset hoặc xóa database chung.

## 6. T5-06 — share whitelist và phản hồi doanh nghiệp

### 6.1. Hoàn thiện share/projection

1. Reuse SchoolSharingPolicyService, StudentConsentPort.lockAndCheckForShare và ProfileShareService. Policy mặc định version0 [majorCode,skills,projects] chỉ là trần fields, không tự cấp consent/share.
2. Grant share phải có proposal ACCEPTED và invitation ACCEPTED, current consent đúng student/school/term/recipient/purpose, field subset, expiry không vượt consent và capacity/reservation hợp lệ; khóa cùng transaction.
3. GET profile/GET file tính effective fields = policy ∩ consent ∩ share ∩ requestedFields. Ngoài whitelist phải omit, không serialize null gợi lộ. Sai enterprise/term/purpose hoặc grant revoked/expired phải deny.
4. cv/evidence gọi FileReadPort với CLEAN/binding/current authorizer tại từng GET/Range. gpa/transcript chưa có nguồn MVP trả PROFILE_FIELD_UNSUPPORTED.
5. Revoke có hiệu lực cho request đọc mới sau commit; không hủy Placement đã có và không hứa thu lại bytes đã tải.

### 6.2. Thêm enterprise proposal read và decision

Các target CP-T5UI1 **chưa triển khai**:

- GET `/api/v1/enterprise/cooperations/{id}/proposals`.
- PATCH `/api/v1/enterprise/proposals/{id}/decision`.

Chỉ enterprise owner có proposal:enterprise:decide; chỉ item SHARED với share/consent hiện hành. List/filter/pagination tính sau visibility guard, không lộ studentUserId hoặc hidden item qua response chưa share. Decision input gồm expectedVersion, decision=ACCEPT|DECLINE, reasonCode; DECLINE bắt buộc reason.

ACCEPT→ENTERPRISE_ACCEPTED, DECLINE→DECLINED theo history/version/audit; không tạo Placement trong decision. Giữ distinct student ACCEPTED, consent grant, school share và enterprise ACCEPT. Dùng lại OperationLedger; không dựng thêm idempotency table.

### 6.3. Cung cấp phần Opportunity tối thiểu cho T2

BC22/G14 đã xác định T5 sở hữu GET opportunities, GET detail và PUT school cooperation publication. Implement projection trên Cooperation APPROVED, publishedAt≤now<closedAt, đúng school/student-term. opportunityId=cooperationId; không tạo demand/cooperation thứ hai.

T2 sở hữu Preference và school preference review API; UI T5 gọi T2. Đây là dependency thực cho T2-07/08 dù tên task T5-06 không nhắc publication; không chờ “bàn giao lại” phần contract đã có.

## 7. T5-07 — Placement state machine và event T3

### 7.1. Bổ sung service/REST trên bảng placements hiện có

| Target CP-T5UI1 cần triển khai | Điều kiện chính |
|---|---|
| POST `/api/v1/school/placements` | CreatePlacement →201 PENDING_APPROVAL, chưa chiếm active-key |
| POST `/api/v1/placements/{id}/approve` | School delegated, recheck gates/quota → APPROVED |
| POST `/api/v1/placements/{id}/activate` | APPROVED, effectiveAt đã tới và current guards hợp lệ → ACTIVE |
| POST `/api/v1/placements/{id}/cancel` | School delegated, reason/version → CANCELLED |
| GET `/api/v1/{school\|enterprise}/placements`, `.../summary`, `.../{id}/internship-link` | Own scope, chuẩn Page/summary/link, không public profile |

CreatePlacement target (IDs/dates là ví dụ; dùng dữ liệu và kỳ thực của môi trường test):

```json
{"proposalItemId":"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee","expectedProposalVersion":5,"expectedEligibilityVersion":2,"effectiveAt":"2026-11-23T00:00:00Z","startDate":"2026-11-23","endDate":"2027-02-23"}
```

Approve/activate/cancel dùng `{expectedVersion,decisionRef,reasonCode}` theo contract. Không nhận student/school/org/“approved=true” của browser làm bằng chứng; derive từ proposal/cooperation và principal.

### 7.2. Gate tạo và duyệt

1. Account/membership hiện hành; SchoolStaff có placement:school:write và delegation school/term còn hiệu lực.
2. Cooperation APPROVED, core org VERIFIED và school verification T5 APPROVED. Hai trạng thái xác minh độc lập.
3. Invitation ACCEPTED; consent/share còn hiệu lực đúng fields/purpose/recipient; enterprise item ENTERPRISE_ACCEPTED.
4. Eligibility T2 đúng student/school/term và schoolApproved, expected version hiện hành; profile/criteria current qua owner ports, không chỉ so blob cũ.
5. Dates/effectiveAt hợp lệ trong policy kỳ, quota và chống duplicate item/active student-term.
6. Cùng transaction: Placement/history/item link + audit + outbox khi có transition phát event; lỗi bất kỳ rollback toàn bộ.

Create chưa approve không chiếm active-key hoặc placedCount. Tại approve, khóa quota và student-term key theo thứ tự ổn định, dùng DB uniqueness cho APPROVED/ACTIVE; không dựa “SELECT COUNT rồi INSERT” đơn lẻ. Hai Placement khác nhau cùng student/term cạnh tranh phải tối đa một APPROVED; request còn lại 409 ACTIVE_PLACEMENT_EXISTS, không vượt quota.

### 7.3. State/time/event đúng ranh giới

| Placement | Tác động T3 |
|---|---|
| APPROVED trước effectiveAt | Publish PlacementApproved; T3 có thể tạo WAITING_START, chưa giao/nộp Task |
| ACTIVE khi serverNow≥effectiveAt | Publish PlacementActivated; T3 re-read source rồi chuyển IN_PROGRESS |
| CANCELLED từ APPROVED/ACTIVE | Release active-key một lần, publish PlacementCancelled; T3 chặn ghi, WAITING_START→CANCELLED hoặc IN_PROGRESS→TERMINATED |
| COMPLETED | Chỉ qua closure proof/authority/gates riêng; không thêm nút Complete để kết thúc demo tuần 3 |

Scheduler UTC và activate thủ công phải gọi cùng service/guard/CAS, không có shortcut SQL. Consent revoke sau Placement chỉ thu quyền đọc, không tự cancel.

PlacementLifecyclePublisher là hook MANDATORY đã có, **không tự cấp quyền hoặc approve**. Gọi nó sau mutation được phép trong cùng transaction. Hiện publisher hỗ trợ APPROVED/ACTIVE/CANCELLED, không giả hỗ trợ COMPLETED khi closure chưa có.

### 7.4. Dùng lại outbox/consumer

- Reuse PlacementOutboxWorker và T3 PlacementInbox; không tạo public REST callback nhận trusted event context từ browser.
- Producer mutation+audit+outbox atomic, consumer inbox+Case+audit atomic. Dedup eventId và (placementId,aggregateVersion,eventType).
- Worker hiện bật mặc định theo `app.t5.placement-delivery.enabled`, retry 1/5/30/300/1800 giây rồi DLQ. Test có thể tắt scheduler và gọi delivery nội bộ có kiểm soát; E2E cần chứng minh delivery thật. Không đổi cấu hình stack chung.
- Activated tới trước Approved phải áp source ACTIVE hợp lệ; event cũ ACK_STALE, không tạo Case thứ hai. Nếu source CANCELLED mà event chưa đến, T3 mutation vẫn phải deny qua current source.
- GET internship-link trả WAITING_HANDOFF khi projection chưa sẵn, không route giả; AVAILABLE khi có Case có quyền. Không bypass guard khi click link.

## 8. T5-09 — UI theo vai trò và trạng thái

1. Mở rộng SchoolWorkspace/DemandWorkspace hiện tại bằng proposal list, capacity và invitation status từ backend. Canonical nominate ở path Matching; không tạo form batch mới ghi trực tiếp DB.
2. School theo dõi student decision, consent evidence, share và enterprise decision thành các cột riêng. Không có nút “SV đồng ý” dưới session trường.
3. Enterprise chỉ thấy proposal đã có quyền share; field picker/projection không tự yêu cầu toàn bộ hồ sơ. Ẩn dữ liệu khi revoke/expiry/401 hoặc đổi org.
4. Thêm enterprise ACCEPT/DECLINE; school create PENDING_APPROVAL rồi approve riêng; activate theo effectiveAt; cancel có reason/version. Không gộp create+approve+activate thành một nút.
5. Hiển thị quantity, reservations, placed/pending/remaining từ summary owner. Không lấy endpoint capacity của school đưa cho enterprise hoặc dùng nó thay protected aggregate T4.
6. Giữ input/key khi timeout, phân biệt schema/permission/version/dependency errors, reload sau mutation và kiểm lại khi đổi tab/scope. Mọi trạng thái phải tồn tại sau browser reload.
7. Verification upload nối T1 File API khi sẵn; api<T> hiện dùng JSON, multipart cần transport đúng với session/CSRF. Không bỏ evidence/CLEAN gate để “demo được”.

## 9. QA05 phối hợp QA02/QA03 — ca bắt buộc

| Ca | Expected |
|---|---|
| Request-info/resubmit/reopen Cooperation | Giữ ID/history và snapshot từng submission; state/version đúng |
| Thiếu school verification hoặc org chưa VERIFIED | Không submit/approve/activate trái gate |
| Không có consent/share, sai field/purpose/time/recipient | Không có PII, enterprise decision/Placement bị chặn |
| Consent revoke/regrant sau share | Lần đọc sau revoke deny; share cũ không sống lại; grant mới có lifecycle/version đúng |
| TTL RESERVED, INVITED, ACCEPTED/SHARED/ENTERPRISE_ACCEPTED | Theo G09; expire/release một lần; không overbook sau retry |
| 1 placed + 1 active reservation trong quantity=2 | openForNomination=0; COMPLETED tính placed, CANCELLED không tính |
| 20 nomination cạnh tranh còn một slot | Một candidate giữ slot, không batch thứ hai hoặc partial items |
| 20 approve cùng student/term trên nhiều placements | Một APPROVED, phần còn lại conflict; không mồ côi history/outbox |
| APPROVED tương lai, tại effectiveAt, scheduler/manual race | WAITING_START trước hạn, ACTIVE đúng biên, một effect/event đúng version |
| Cancel trước event approve tới, Activated tới trước Approved, duplicate/stale | Source authoritative, một Case và lastAppliedVersion đơn điệu |
| Lỗi audit/outbox hoặc crash delivery | Mutation rollback hoặc replay idempotent, không phantom ACTIVE |
| Enterprise A đổi ID sang B, school B thao tác A, ADMIN PII | 403/404 và không lộ dữ liệu |
| Browser trường→SV→enterprise→trường→T3 | Backend thật: invite, student accept, consent riêng, share, enterprise accept, create/approve/activate; Task/Submission chỉ mở sau ACTIVE |
| Opportunity/Preference consumer T2 | ID mapping đúng; publication/window/school guards, không auto invitation/Placement |

Các test hiện có để reuse: BusinessModulesIT, DemandIT, InternshipRuntimeIT, Week2WorkflowIT. Test mới đề xuất: `ProfileSharingIT`, `PlacementWorkflowIT`, `proposal-placement-runtime.spec.ts` (chưa có); không gọi tên test mới là PASS trước khi chạy.

```powershell
.\mvnw.cmd '-Dit.test=BusinessModulesIT,DemandIT,InternshipRuntimeIT,Week2WorkflowIT' '-Dfrontend.e2e=true' verify
Push-Location web-demo
npm.cmd run build
Pop-Location
```

Chạy thêm các test mới sau khi tạo, rồi full verify/contract checks theo [runtime guide](../guides/WEEK2_RUNTIME.md). DB Testcontainers riêng; browser 5178 chạy tuần tự, chờ login hoàn tất trước khi lấy CSRF.

## 10. Điều kiện bàn giao lại

Gửi branch/SHA/snapshot, DTO/routes/migration đổi, state/event matrix thực tế, consumer test T2/T3/T4, report QA05 từng ca và phần NOT RUN. Đối chiếu source trước khi nói “chờ team khác”: T2 consent/snapshot, T4 classifier/MatchRun và T3 consumer đã có. Phần cần bổ sung mới là port Placement versioned, publication, enterprise decision và các gate nêu trên.

Migrations hiện cuối V27, số mới sau đó do các team phối hợp T1 để tránh trùng. Không sửa migration cũ, không tạo placements/batch/consent table thứ hai. Shared SecurityConfig/AuditService/OperationLedger/App cần review diff theo mức ảnh hưởng, không mở ADMIN hay SYSTEM bypass.

Stack dùng hằng ngày utbridora-local:8088 và dữ liệu/password/volume phải giữ nguyên. Dev mới dùng [LOCAL_DEMO](../guides/LOCAL_DEMO.md), database các máy độc lập. Không seed/import/reset DB chung, in secret, reset/clean repo gốc hoặc phục hồi f606b90. Chỉ commit/push/merge khi được yêu cầu. Chưa có human QA/Lead signoff hoặc 5 team access.

Đọc cùng [T2](T2_TUAN2_BAN_GIAO_TUAN3.md), [T3](T3_TUAN2_BAN_GIAO_TUAN3.md), [T4](T4_TUAN2_BAN_GIAO_TUAN3.md).
