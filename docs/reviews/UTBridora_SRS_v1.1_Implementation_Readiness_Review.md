# Kiểm định mức độ sẵn sàng triển khai UT.Bridora SRS v1.1

**Ngày kiểm định:** 02/11/2026 (Asia/Bangkok). **Loại công việc:** AUDIT / ANALYSIS ONLY. **Đối tượng:** Demo/MVP synthetic của toàn bộ T1–T5; vận hành với dữ liệu thật là một cổng khác.

## 1. Executive Summary

**Quyết định tổng thể: NO GO — UT.BRIDORA SRS V1.1, nếu hiểu là cho phép giao toàn bộ năm phân hệ để tự chủ lập trình và nghiệm thu mà không cần bổ sung quyết định quan trọng.** Có thể cho **CONDITIONAL GO theo phạm vi** đối với nền tảng T1 hiện hữu, khung module, CRUD đã có contract và các hàm thuần có oracle xác định. Ba nhóm P0 phải được giải quyết trước luồng Placement liên team hoặc mở thống kê doanh nghiệp: active-set/handoff Placement; nguồn student–school–term/eligibility; privacy release/query guard.

SRS có giá trị thực tế: 25 chương, 81 bảng, ba hình kỹ thuật, ownership liên team, 18 quyết định C-01–C-18, 18 MVP-BR, sáu contract giao điểm, NFR định lượng và 16 INT + 8 POL test mục tiêu. Baseline đã chọn consent riêng với lời mời, grant tối đa 30 ngày, strict matching, FAIL thắng UNKNOWN, k=5, school publish cùng HR visibility, dữ liệu synthetic/Mailpit và chuỗi Flyway chung. Không đánh đồng những lựa chọn này với OPEN cũ hoặc yêu cầu phê duyệt production.

Tuy nhiên, nhãn FROZEN/CHỐT trong tài liệu không thay thế việc đặc tả có thể lập trình. Ngay phần ưu tiên còn ghi Placement là “đề xuất mapping”, chưa đặc tả canonical term và record/port/version của bằng chứng student–school–term để đọc schoolApproved, chưa có thuật toán/quy tắc release cho truy vấn suy diễn. Còn các khoảng trống về DTO/null semantics, fit_score, capacity reservation, HR/delegation, file, rubric, school grade policy và lifecycle phụ trợ. Namespace permission/event mới còn khác source T1 đang phát hành.

### 1.1. Nguồn và giới hạn xác minh

| Hạng mục | Kết quả thực tế |
|---|---|
| Đường dẫn chính trong yêu cầu | Không tồn tại ở workspace hoặc project: docs/srs/UTBridora_SRS_Tong_The_CHOT_Demo_MVP_v1.1.docx. Không tự tạo hoặc thay SRS. |
| File đính kèm được người dùng nêu rõ | D:/HKDN-TTTV-UTH/UTBridora_SRS_Tong_The_v1.1.docx; tồn tại, 409.042 byte. Core title là “UT.Bridora – SRS tổng thể v1.1 (Chốt Demo/MVP)”; bảng kiểm soát ghi UTB-SRS-MASTER-001, v1.1, ngày 02/11/2026. Đây là nguồn thực sự được kiểm định. |
| SHA-256 SRS | 2FF8D125720C75FB1B3FEA808571C6830293CAB65C8596DD414A231DC2563349 |
| Phạm vi đọc DOCX | Đã trích và đọc toàn bộ 425 paragraph top-level, 81 bảng/không có bảng lồng, Ch.01–25, Phụ lục A/B, header/footer. Ba PNG tại §20.1,21.1,21.2 được mở và đọc trực tiếp từ ZIP trong bộ nhớ. Không có phần kỹ thuật trong bảng/hình nhúng bị bỏ vì không đọc được. |
| Công thức/đối tượng phụ | Không có OMML m:oMath/m:t, w:sym, textbox, comments, tracked insert/delete, chart/SmartArt hay OLE trong gói. Một số con số/ô nguồn cũ thực sự bị trống hoặc câu bị cắt; không tự khôi phục bằng suy đoán. |
| Trang Word | Không render toàn bộ phân trang; docProps/app.xml ghi Pages=1, Words=0 nên không dùng làm số trang thật. Dẫn chứng theo chương/mã yêu cầu và vị trí XML ổn định, không bịa số trang. Không chứng nhận chất lượng dàn trang. |
| SRS gốc T1–T5 | Không tìm thấy năm DOCX gốc trong workspace/repository. Ch.02 liệt kê chúng nhưng không chứng minh các file đã có. Có một bản trích T1 v1.0 cũ trong .work; không dùng nó thay việc xác minh năm bản gốc. Đối chiếu độc lập với SRS gốc: UNVERIFIED. |
| Repository | Local: D:/HKDN-TTTV-UTH/UTBridora_SourceCode/utbridora-t1-full-foundation. Remote: https://github.com/NguyenThanhTiep/utbridora. |
| Snapshot source | Local HEAD và git ls-remote origin main cùng 3c725e3dc8ed1886a84cff2d64e75b746c75766c. Trước audit đã có file untracked docs/DATA_SYNCHRONIZATION.md; giữ nguyên. Chỉ tạo báo cáo này. |
| Phạm vi source | Maven backend hiện chứa nền tảng T1; chưa tìm thấy implementation Student/Internship/Matching/Cooperation trong checkout này. Không suy ra các team chưa viết ở repository/nhánh khác. |
| Thao tác không thực hiện | Không sửa SRS/source/migration, không chạy build/test mới, không truy vấn/chỉnh DB, không triển khai hoặc tự chốt chính sách UTH. |

**Quy ước dẫn chứng:** P001–P425 là paragraph trực tiếp dưới word/body, kể cả paragraph trống; T01–T81 là bảng theo thứ tự tài liệu; R1 là hàng tiêu đề. Locator này đi kèm § và mã FR/US để người đọc tìm được bằng Navigation/Search. Các câu trích ngắn là bằng chứng của tài liệu; các mệnh đề ở §5 được gắn rõ là đề xuất v1.2, chưa phê duyệt.

### 1.2. Ba tầng bằng chứng được giữ tách biệt

| Tầng | Những gì xác minh được | Những gì không được suy ra |
|---|---|---|
| SRS đã đặc tả | C-01..18, MVP-BR, POL demo, contract target, SYS-NFR, INT/POL test mục tiêu. Ch.19–25 và quy tắc ưu tiên §19/T56 chi phối nội dung cũ. | Tuyên bố “hoàn thiện để giao lập trình” tại T79 không chứng minh contract đầy đủ hoặc test đã pass. |
| Source đã triển khai | Auth/session/CSRF, membership/org guard, lifecycle, audit, V1–V7, UI quản trị; files chỉ metadata; chia sẻ mặc định DENY. | Các endpoint target T2–T5, consent thật, file scan, Placement, matching, outbox hay workspace đã chạy. |
| Kiểm thử thành công trong quá khứ | docs/PREPUSH_TEST_EVIDENCE.json ngày 30/10/2026 ghi 28 unit +49 Failsafe +14 Chromium, 3 Chromium là UI mock; XML hiện có khớp các tổng này. 49 gồm 48 CorePlatformIT và1 MigrationUpgradeIT. | Đây không phải lượt test mới ngày 02/11, không phải 49+14 backend test độc lập, không nghiệm thu baseline mới hoặc toàn bộ năm team. |

Không có benchmark mới, privacy suite mới, race20 Placement, INT-01..16/POL-TC-01..08 đầy đủ hoặc restore drill được xác minh trong audit. Những mục này ghi NOT RUN; hiệu năng, tính toàn vẹn DB staging và kết quả CI mới nhất: UNVERIFIED. Workflow CI có trong source không tự chứng minh một run cụ thể đã thành công.

## 2. SRS Readiness Score

### 2.1. Phương pháp và ý nghĩa

Đây là điểm **mức cụ thể của yêu cầu**, không phải tỷ lệ hoàn thành mã nguồn, xác suất thành công hoặc chứng nhận ISO. Áp dụng các nguyên tắc cần thiết, rõ nghĩa, đầy đủ, nhất quán, khả thi, kiểm chứng và truy vết của kỹ thuật yêu cầu; tham chiếu phạm vi tiêu chuẩn [ISO/IEC/IEEE29148:2018](https://www.iso.org/standard/72089.html) và [IEEE29148-2018](https://standards.ieee.org/ieee/29148/6937/). Không tuyên bố đã kiểm định tuân thủ từng điều khoản của bản tiêu chuẩn có bản quyền.

Bảy tiêu chí hệ thống có năm phép kiểm tra mỗi tiêu chí: 0=thiếu/mâu thuẫn không có quyết định áp dụng, 1=có nhưng chưa đủ để có một kết quả/contract duy nhất, 2=đủ cụ thể trong phạm vi xét. Điểm=100×tổng/10. Năm điểm team lấy từ mẫu các cụm chức năng quan trọng ở §3.2, mỗi cụm dùng mười câu A2 với cùng thang0–2; điểm=100×tổng/(20×số cụm). Vấn đề đã được baseline ưu tiên giải quyết không bị trừ lần nữa vì nguồn cũ khác.

Điểm là nhận định chuyên môn có rubric và bằng chứng để tái chấm; không là ước lượng thống kê. Độ tin cậy cao về nội dung đã đọc và source snapshot, trung bình về calibration điểm/bao phủ mọi tổ hợp nghiệp vụ; đối chiếu SRS gốc và đo runtime ngoài checkout chưa xác minh. P0 là điều kiện chặn riêng, điểm trung bình không bù cho P0.


| Tiêu chí | Điểm /100 | Cơ sở chấm |
|---|---:|---|
|Yêu cầu chức năng|70|Phạm vi/owner (§1–3); Actor/FR/US (§6–10); Input/output exact (G10,G27); Trạng thái/ngoại lệ (G01,G08,G21); AC đủ oracle (G11,G23,G29)|
|Yêu cầu phi chức năng|80|NFR security/data (§15,24); Workload400/5000 (§24.3); p95,concurrency,môi trường (§24.3); Đo/khôi phục/retention (G30,G31); Liên kết test và evidence (G29)|
|Quy tắc nghiệp vụ|60|MVP-BR/consent/publish (§19); Nguồn dữ liệu/ownership (§20); Vòng đời đầy đủ (G01,G08); Capacity/scoring/report (G09,G11,G25); Student-term/eligibility không suy đoán (G02)|
|Phân quyền và bảo mật|70|Session/CSRF/deny/tenant (§4,6); Consent/scope/default PII (§19–20); Delegation/capability (G06,G07); Privacy release cụ thể (G03); File/revocation (G15,G16,G17)|
|Database và dữ liệu|70|Owner/UUID/UTC (§12,20,23); Global Flyway/validate (§23.1); Dictionary/FK/key exact (G02,G22,G27); Concurrency active/reservation (G01,G09); Seed/purge/restore (G30,G31)|
|API và tích hợp|60|Owner/endpoint/CT-01–06 (§22); DTO/type/nullability (G10,G27); Permission/state compatibility (G05,G06); Errors/idempotency (G19,G27); Event/cancel/version (G18)|
|Kiểm thử và nghiệm thu|70|Test catalog/INT/POL (§16,24); Negative/race/privacy gates (§24); Oracle đo được toàn Must (G03,G11,G23); RTM từng Must→test (G29); Recipe/evidence manifest (G31)|
|Khả năng triển khai T1|73.8|118/160, 8 cụm chức năng A2; xem §3.2.|
|Khả năng triển khai T2|59.4|95/160, 8 cụm chức năng A2; xem §3.2.|
|Khả năng triển khai T3|62.2|112/180, 9 cụm chức năng A2; xem §3.2.|
|Khả năng triển khai T4|71.7|86/120, 6 cụm chức năng A2; xem §3.2.|
|Khả năng triển khai T5|70.6|113/160, 8 cụm chức năng A2; xem §3.2.|

**Điểm tổng hợp bảy tiêu chí hệ thống: 68.6/100** (trung bình không trọng số). Không cộng điểm team vào cùng trung bình để tránh đếm trùng.

### 2.2. Rubric chi tiết bảy tiêu chí

| Tiêu chí | Năm phép kiểm tra theo thứ tự | Vector điểm | Tổng |
|---|---|---|---:|
|Yêu cầu chức năng|Phạm vi/owner (§1–3); Actor/FR/US (§6–10); Input/output exact (G10,G27); Trạng thái/ngoại lệ (G01,G08,G21); AC đủ oracle (G11,G23,G29)|2, 2, 1, 1, 1|7/10|
|Yêu cầu phi chức năng|NFR security/data (§15,24); Workload400/5000 (§24.3); p95,concurrency,môi trường (§24.3); Đo/khôi phục/retention (G30,G31); Liên kết test và evidence (G29)|2, 2, 2, 1, 1|8/10|
|Quy tắc nghiệp vụ|MVP-BR/consent/publish (§19); Nguồn dữ liệu/ownership (§20); Vòng đời đầy đủ (G01,G08); Capacity/scoring/report (G09,G11,G25); Student-term/eligibility không suy đoán (G02)|2, 2, 1, 1, 0|6/10|
|Phân quyền và bảo mật|Session/CSRF/deny/tenant (§4,6); Consent/scope/default PII (§19–20); Delegation/capability (G06,G07); Privacy release cụ thể (G03); File/revocation (G15,G16,G17)|2, 2, 1, 1, 1|7/10|
|Database và dữ liệu|Owner/UUID/UTC (§12,20,23); Global Flyway/validate (§23.1); Dictionary/FK/key exact (G02,G22,G27); Concurrency active/reservation (G01,G09); Seed/purge/restore (G30,G31)|2, 2, 1, 1, 1|7/10|
|API và tích hợp|Owner/endpoint/CT-01–06 (§22); DTO/type/nullability (G10,G27); Permission/state compatibility (G05,G06); Errors/idempotency (G19,G27); Event/cancel/version (G18)|2, 1, 1, 1, 1|6/10|
|Kiểm thử và nghiệm thu|Test catalog/INT/POL (§16,24); Negative/race/privacy gates (§24); Oracle đo được toàn Must (G03,G11,G23); RTM từng Must→test (G29); Recipe/evidence manifest (G31)|2, 2, 1, 1, 1|7/10|

## 3. Ma trận kiểm tra từng team và tích hợp

### 3.1. A1 — Chất lượng cấu trúc SRS

Đánh giá “đầy đủ” ở đây là nội dung cho mục đích đang xét, không đồng nghĩa module đã code hoặc kiểm thử.

| Nội dung | Đánh giá | Bằng chứng / phần còn thiếu |
|---|---|---|
| Mục tiêu và phạm vi | Đầy đủ | §1–3; phân biệt T1–T5, ngoài phạm vi, demo và production. |
| Người dùng và vai trò | Chưa đầy đủ | Bốn role/HR/Mentor rõ (§4,20.3); chưa có cơ chế cấp quyền ủy quyền và policy trường đầy đủ (G07,G24). |
| Yêu cầu chức năng | Chưa đầy đủ | AUTH/AZ/DB/ST/AUD, T2-F/US, FR-01–30, T4US, T5VER/DEM/COOP/PROP/PLC; input/rule quan trọng còn thiếu ở G01–G28. |
| NFR | Chưa đầy đủ | §24.3 có workload,p95,concurrency,môi trường; recipe, một số ngưỡng file/query và vận hành chưa đủ (G03,G15,G30,G31). |
| User Story và AC | Chưa đầy đủ | Có nhiều AC/test nguồn; T2 tham chiếu AC ngoài file, scoring/rubric chưa có oracle; có ID AC trùng (G11,G12,G23,G29,G32). |
| Luồng end-to-end | Chưa đầy đủ | §5 và §21.1 có owner từng bước/hình; snapshot VALID và Placement handoff còn nhánh chưa quyết định (G01,G04). |
| Business Rules | Chưa đầy đủ | MVP-BR-01–18/POL demo rõ; active-set, reservation, thời hạn lời mời, group/report/rubric còn thiếu. |
| State machine/guard chuyển trạng thái | Chưa đầy đủ | §21.2 có bảng; Placement vẫn đề xuất; proposal, eligibility, preference/case chưa đủ; task trigger chưa khóa với submission. |
| Phân quyền/bảo mật | Chưa đầy đủ | §4/19/20 có deny,scope,consent; registry mapping, delegation, file URL revocation và privacy release chưa đủ. |
| Mô hình dữ liệu | Chưa đầy đủ | Ownership/UUID/UTC/Flyway rõ; dictionary tối thiểu không phải DDL đầy đủ; thiếu student-term, reserved/active keys, type/null/ON DELETE. |
| API Contracts | Chưa đầy đủ | §22 có URLs/CT-01–06/sample JSON; chưa có OpenAPI mới có type/required/null/error/enum hoàn chỉnh và compatibility mapping (G27). |
| Error handling | Chưa đầy đủ | §22.1 có taxonomy lỗi; khi nào403/404, 409/422 và envelope source chưa thống nhất; file/scan/import thiếu lỗi cụ thể. |
| Logging và Audit | Chưa đầy đủ | §6.5/20.6 có transaction/requestId/minimization; event names mới chưa map source, retention purge còn thiếu (G28,G30). |
| Use Case và giao diện | Chưa đầy đủ | §14 là inventory màn hình/route và UX; không có đầy đủ use-case narrative/field validation/wireframe được tham chiếu từ SRS gốc. Route S04 có ô bị cắt (G32). |
| Kiểm thử nghiệm thu | Chưa đầy đủ | §16/24 có SEC,TC,S1–S11,T5,INT,POL; một số expected result mở hoặc thiếu oracle; kết quả mới NOT RUN. |
| Ma trận truy vết | Chưa đầy đủ | §17/25 mapping theo nhóm, chưa có hàng cho từng Must→API/service→test→evidence (G29). |
| Phụ thuộc liên team | Chưa đầy đủ | §3.2/13.3/20.2/22 đã xác định owner; còn DTO, state, contract đi-về và vòng chờ G01,G02,G04,G18,G20. |
| Điều kiện bàn giao/MVP | Chưa đầy đủ | §24.4–24.6/25.2 có gate; D1 ghi PM xác nhận demo trong khi §25.3 tách production, cần diễn đạt phạm vi sign-off nhất quán; evidence chưa có cho toàn MVP. |

Không có mục “Thiếu hoàn toàn/Không áp dụng” ở cấp chương: tài liệu có đề cập tất cả nhóm nội dung. Điều này không loại trừ các thành phần cụ thể thiếu hoàn toàn, như oracle fit_score, nguồn school policy hoặc API file có schema.

### 3.2. A2 — Mức đủ thông tin cho từng cụm chức năng quan trọng

Thứ tự vector: **I** input, **A** actor, **P** tiền điều kiện, **B** xử lý/business rule, **O** output, **S** trạng thái, **R** quyền, **E** lỗi/invalid, **T** AC đo được, **D** không phải tự chốt nghiệp vụ quan trọng. Điểm0/1/2 được chấm trên tài liệu và contract mục tiêu; không cộng điểm vì source hoặc test hiện hữu. Cụm gộp các FR có cùng đường dữ liệu, không thay việc lập RTM từng Must.

| Team | Phân hệ/khả năng | Kết luận | Có thể bắt đầu | Phải bổ sung trước luồng tương ứng |
|---|---|---|---|---|
| T1 | Auth, invitation/reset, membership, lifecycle, audit, files, core DB, guard | Có thể lập trình một phần | Tái sử dụng source auth/lifecycle/audit/Flyway; contract-first cho phần mới | Permission/event/org mapping; HR/school delegation; typed consent decision; File Contract; envelope mới. Không đánh dấu upload/scan đã xong. |
| T2 | Profile/major/skills/project/CV, completeness, khóa sửa, preference, invitation/consent, task/results UI | Có thể lập trình một phần | Form/schema draft, owner-only CRUD sau khi DTO chốt, UI qua shared auth, consent UI theo policy đã chọn | Cụ thể sáu kiểm tra, catalog owner, term eligibility, availability/missing semantics, snapshot sửa, opportunity/preference states và Task/Result DTO. |
| T3 | Placement handoff, Cohort/Group/Mentor, Task/Submission/Report/Rubric/Publication/Close | Có thể lập trình một phần | Khung module, thiết kế immutable submission storage, guard và contract tests trên fixture cần chuẩn bị | G01/G02 trước tạo workspace thật; assignment/mentor invitation; task transition/late, group move, report week, rubric formula, school visibility và close contract. |
| T4 | Taxonomy/alias, strict REQUIRED, preferred, run/history, school detail, aggregate | Có thể lập trình một phần | Pure classifier FAIL>UNKNOWN, alias exact, snapshot/version storage, taxonomy có contract | Typed CT-01/02, công thức fit_score, canonical snapshot VALID; G03 trước mở aggregate ENTERPRISE; no AI/implies trong MVP. |
| T5 | Verification/Demand/Cooperation/Proposal/Invitation/Share/Placement/Capacity | Có thể lập trình một phần | CRUD và history versioned có schema; canonical invitation/consent integration contract | Canonical term/eligibility, Placement active-set/handoff, verification bổ sung, capacity reservation, deadline, consent race và event cập nhật. |

Đối với mỗi team, các cụm chưa có đủ thông tin phải hoàn thành các yêu cầu bổ sung ở §5 trước khi code hành vi cuối cùng; không dùng mock làm nghiệm thu. Chỉ phần đọc source không chứng minh T2/T3/T4/T5 chưa làm ở nơi khác.


**T1 — phép kiểm tra A2**

| Cụm | I,A,P,B,O,S,R,E,T,D | Tổng /20 | Bằng chứng chính |
|---|---|---:|---|
|Auth/session|2,2,2,2,2,2,2,1,2,2|19|§6.1 AUTH-01–05,§22.1|
|Invitation/reset/change|2,2,2,2,2,2,2,1,2,2|19|§6.1 AUTH-06–10|
|Role/org/delegation|2,2,2,1,1,1,1,1,1,1|13|§4,20.3–20.4;G05–07|
|Consent/resource|1,2,2,2,1,2,1,1,2,1|15|§20.5;G17|
|File|0,1,1,1,0,1,1,0,1,0|6|§20.6,CT file;G15–16|
|Audit|1,2,2,2,1,2,2,1,2,1|16|§6.5,23.3;G28|
|DB/Flyway/lifecycle|2,2,2,2,2,2,2,1,2,2|19|§6.3–6.4,23.1|
|Common API|1,2,1,1,1,1,1,1,1,1|11|§22.1–22.4;G27|

**T2 — phép kiểm tra A2**

| Cụm | I,A,P,B,O,S,R,E,T,D | Tổng /20 | Bằng chứng chính |
|---|---|---:|---|
|Profile/major|1,2,1,1,1,1,2,1,1,0|11|US-T2-01/16/17;G12–13|
|Skills/projects/CV|1,2,1,1,1,0,2,1,1,0|10|US-T2-02/04/05;G10,G15|
|Completeness|1,2,1,0,1,0,2,1,0,0|8|US-T2-06,C07;G12|
|Preferences|1,2,1,1,1,0,2,1,1,0|10|US-T2-07/08,C13;G14|
|Invitation UI|1,2,2,1,1,2,2,1,1,1|14|US-T2-09/10;G08|
|Consent|1,2,2,2,1,2,2,1,2,1|16|MVP-BR-10/11;G17|
|Snapshot/lock|1,2,1,1,1,1,2,1,1,0|11|C08,§21.3;G13|
|Workspace UI|1,2,2,2,1,2,2,1,1,1|15|US-T2-11/12/13;G21,G24–25,G27|

**T3 — phép kiểm tra A2**

| Cụm | I,A,P,B,O,S,R,E,T,D | Tổng /20 | Bằng chứng chính |
|---|---|---:|---|
|Placement import|1,2,1,1,1,1,1,1,2,0|11|FR-01,CT-06;G01–02,G18|
|Cohort/group|1,2,2,1,1,1,1,1,2,1|13|FR-02–04;G22|
|Mentor/invite|1,2,1,1,1,1,1,1,2,0|11|FR-05–06;G07,G20|
|Task/state|1,2,2,1,1,1,2,1,2,1|14|FR-09–14;G21|
|Submission/review|1,2,2,2,1,2,2,2,2,1|17|FR-15–16;§23.4|
|Weekly report|1,2,1,1,1,2,1,1,1,0|11|FR-18–19;G25|
|Rubric|1,2,1,1,1,2,1,1,2,0|12|FR-20,TC-12;G23|
|Publication|1,2,2,1,1,2,1,1,2,1|14|FR-21–22;G24|
|Close case|1,2,1,0,1,1,1,1,1,0|9|FR-27,§19.5;G26|

**T4 — phép kiểm tra A2**

| Cụm | I,A,P,B,O,S,R,E,T,D | Tổng /20 | Bằng chứng chính |
|---|---|---:|---|
|Taxonomy|1,2,2,2,1,2,1,1,2,1|15|US-T4-05/06;§24.1|
|REQUIRED|1,2,1,2,2,2,2,1,2,1|16|US-T4-04,S1–11;G10|
|PREFERRED|0,2,2,0,1,2,2,1,1,0|11|US-T4-10;G11|
|Run/version|1,2,1,2,1,2,2,1,1,1|14|§24.1,CT-01/02;G04,G10|
|Detail/nominate|1,2,2,2,1,2,2,2,2,1|17|US-T4-03/07;G02,G09|
|Privacy|1,2,2,1,1,2,2,1,1,0|13|US-T4-02,§24.2;G03|

**T5 — phép kiểm tra A2**

| Cụm | I,A,P,B,O,S,R,E,T,D | Tổng /20 | Bằng chứng chính |
|---|---|---:|---|
|Verification|1,2,2,1,1,1,2,1,2,0|13|VER-01–14,§21.2;G05,G07|
|Demand|1,2,2,1,1,2,2,1,2,0|14|DEM-01–16;G04|
|Cooperation|1,2,2,2,1,1,2,2,2,1|16|COOP-01–11;G05,G27|
|Proposal/capacity|1,2,2,1,1,1,2,1,2,0|13|PROP-01–16;G09|
|Invitation|1,2,2,1,1,2,2,1,2,0|14|MVP-BR-09,CT-04;G08|
|Share|1,2,2,2,1,2,2,1,2,1|16|§20.5,CT-05;G16–17|
|Placement|1,2,1,1,1,0,2,1,2,0|11|PLC-01–12;G01–02|
|Handoff|1,2,2,2,2,1,2,1,2,1|16|CT-06,§22.5;G18|

### 3.3. C — Ma trận tích hợp chín cặp team

Tên port dưới đây là tên trong SRS nếu có; các tên/field mở rộng mang nhãn ĐX là đề xuất, chưa là API đã chạy. Mỗi hàng bao gồm owner/consumer, DTO/ID, quyền/trạng thái, lỗi/giao dịch/idempotency và oracle test.

| Cặp | Owner → consumer; API/port và DTO/ID | Permission và trạng thái | Lỗi, transaction, concurrency/idempotency | Điều kiện kiểm thử và khoảng trống |
|---|---|---|---|---|
| T1–T2 | T1 Auth/AppPrincipal/File/Audit→T2; T2 StudentConsentPort→T1. /auth/me, /students/me/profile/consents; users.id khác profile.id; consentId/version/fileId UUID. | Self/consent permission; accountACTIVE/current securityVersion; fileCLEAN; shared read cần consent+share. |401/403/404,409 stale,422 business; consent+audit atomic; revoke authoritative ở request kế; mutation keydedup. | INT-01,07,13; self/other, foreign fileId, grant field intersection/revoke. Thiếu File/typed decision/schema (G15–17,G27). |
| T1–T3 | T1 principal/file/audit/MentorInvitationPort(ĐX)→T3; T3 authoritative HR/mentor/group assignments→T1 guard. user/org/case/assignment/file UUID. | ENTERPRISE membership không tự là HR; HRcapability hoặc assigned mentor; delegation theo trường để publish. |401/403/404/409/422; assignment invite/revoke audit; invitation không vượt org; version checks; backend route allowlist. | INT-10–13; HR thường khác HR được ủy quyền; revoked mentor; no globalADMIN bypass; existing/new mentor (G07,G20–26). |
| T1–T4 | T1 principal/catalog/audit→T4; MatchingPort/SafeAggregatePort phục vụ caller có scope. demand/run/student UUID; registry permission/action/version. | matching:run:execute, enterprise aggregate; school detail theo school; ADMIN không mặc định PII; taxonomy write có ủy quyền. |401/403/404/409/429; audit fail-close; query release canonical/atomic; no response leakage qua errors/export. | INT-04,05,16; missingnewcode deny; query actor/demand bypass; registry compatibility (G03,G06–07,G28). |
| T1–T5 | T1 org/membership/school guard/files/audit→T5; T5 school verification→business guard. OrganizationReference/SchoolDelegation(ĐX); enterprise/school/user UUID. | T1 operational state và T5 verification APPROVED đúng school là hai điều kiện độc lập; t5:proposal:manage/t5:placement:approve. |401/403/404/409/422; version/lock/recheck at approval; business+audit+outbox atomic; idempotentcreate. | INT-08,16/POL-05; orgA/B, schoolA/B, suspended afterlogin, wrongrole; org/permissionmapping chưa chốt (G05–07). |
| T2–T3 | T3 StudentTaskViewPort/EvaluationReadPort, Task/Calendar/Report/Evaluation DTO→T2 UI; T2 không mutate bảngT3. user→Placement→case→assignment/evaluation. | Student self, validcase/assignment; PUBLISHED∧schoolPolicy∧HRenabled. Mentor/group review doT3. |401/403/404/409/422; submission và state atomic; versionNo unique; review current version; page/filter scope. | INT-11/12; noothers/draftgrade; duplicatev2/stalereview; typed DTO, reports, visibility, close chưa đủ (G21–27). |
| T2–T4 | StudentProfileSnapshotCT01 T2→T4; SkillTaxonomyPort/completenessPolicy T4→T2. studentUserId/school/term, major, skillId/code, availability, profile/taxonomy/completenessVersion. | T2 owner; School detail theo school; Enterprise chỉ protected aggregate. Skill self-declared không tự là verified. |400 schema,409 stale,422 criteria; explicit undeclared/empty; immutable run; không tạo circular entitywrites. | INT-02/03,S1–11+null/empty/datebounds; thiếu types/completeness/catalog/fieldlock (G10–13). |
| T2–T5 | T5 Invitation/Opportunity→T2; T2 profile/eligibility/consent/intent→T5. CT-04/05, InvitationPort, StudentIntentPort; student/org/term/consent/version/proposal IDs. | T5 canonicalinvitation; Student chỉ tự quyết định; consent khác acceptance; schoolApproved đúng term; share fieldintersection. |409 state/deadline/capacity,422 ineligible; serialize revoke/approve theo authoritative version; key+payload replay; history append. | INT-06–08/POL-01/02/04/05; accept/decline race, grantv2/revoke, wrongschool; gapG02,G08–09,G14,G17. |
| T3–T5 | T5 PlacementPort + CT-06 PlacementApproved→T3; outcomeCase→T5 theo contract được duyệt. eventId/schemaVersion, placementId/version, term/user/org/school,effectiveAt,traceId. | Chỉ Placement thật đúng scope; active-set chưa chốt; T3 không sửa Placement trực tiếp. | Placement+outbox atomic; inboxeventId+case+audit atomic(ĐX); uniqueevent/placement; stale/out-of-order/cancel checked; retry/DLQ bounded. | INT-09/POL-07; fakeID, duplicate, futurestart, cancelbeforelateevent, repairmanual replay. Thiếu activation/cancel/closecontract và retry policy (G01,G18,G26). |
| T4–T5 | DemandCriteriaSnapshotCT02 T5→T4; MatchSummary/DetailsCT03 + nominatedMATCH→T5 proposal. demandVersion/criteriaVersion/profileVersion/ruleVersion/runId/studentIDs. | Enterprise own criteria/aggregate; School nominatedetail đúng scope; T5 tái kiểm eligibility/capacity ở commit. |400/422 invalidcriteria,409 stale/overcap; matching không tự approve Demand/Cooperation; nominationidempotent, snapshot không ghi đè. | INT-03/04/08; matchingprecooperation, preferredoracle, no PII, stalecriteria/race nomination. G02–04,G09–11. |

**Vòng phụ thuộc cần phá bằng contract-first:** T2 cần taxonomy/completeness T4, T4 cần snapshot T2; T4 cần criteria T5, T5 cần matching T4; T3 cần Placement T5, T5 cần trạng thái case T3. Đây là phụ thuộc dữ liệu hai chiều, không bắt buộc là vòng dependency code. Công bố port/DTO versioned nhẹ, wire tại composition root; unit/contract test bằng adapter test được phép trong phát triển, nhưng thay bằng adapter thật khi nghiệm thu. Không import Entity của nhau rồi ghi trực tiếp bảng owner. Các port missing phải được chốt trước khi hai team cùng merge thay đổi public contract.

**Rủi ro nguồn trùng được nhận diện:** invitation cũ§9 nóiT2, catalog identity cũ có thể gán nhầm; C03/§23.4 đã chọnT5 invitation vàT1 identity nên không coi owner này còn OPEN. T2 chỉ intent/decision/reference; T4 không là chủ profile; T3 không tạo Placement. Còn term/eligibility và school grade policy cần owner/DTO thật.

### 3.4. D — Kiến trúc, database và bảo mật

| Kiểm tra | SRS/source đã xác minh | Kết luận/việc cần làm |
|---|---|---|
| Modular Monolith/dependency | §3/20 một backend+DB, port/owner; source một Maven app, @SpringBootApplication trong vn.utbridora.auth, hiện49 Javafile core. | Phù hợp công nghệ tham chiếu; chưa chứng minh boundary năm module hoặc không có vòng package. Tích hợp dưới scan root hoặc cấu hình component/entity/repository scan có test; không tự đổi framework/kiến trúc (G34). |
| Owner bảng | §20.2 và23.2 ownerT1–T5, coreidentityT1; physicalnames được mapping. | Logicalowner rõ; term/eligibility/policy/delegation chưa đủ; chưa cóDDL các module ngoàiT1. |
| PK/FK/unique/index | Core V1–V7 cóUUID/tenantFK, membership scope_key unique, permission resource/actionunique, sessionFK, audit/indexes/mutex. | Kiểm tra source, không kiểm DBstaginglive. ActivePlacement/reservation/groupkeys cầnchốt rồi migration mới; không suy ra đã có từ câu “unique conditional”. |
| Flyway stream | V1–V7 hiện có đúng một location; SRS§23.1 đã thay cách đánh sốT4cũ. | Một đầu mối cấp Vnext; lịch sử DB thực tế UNVERIFIED. MigrationUpgradeIT:21 hardcode5 migration sauV2, phải cậpnhật cóchủý khi thêmV8+. |
| ORM validate | application.yml khai báo ddl-auto:validate và UTC; một sốenum dùng JdbcTypeCode VARCHAR. | Cấu hình phù hợp. Testmigration cũ cóbằng chứng; schema/entityT2–T5 chưa có nên không kết luận validate toànMVP. |
| Locking/transaction/rollback | Core dùng SELECT FORUPDATE/AdministrationGuard +securityVersion, audit jointransaction; business optimisticversion được SRS yêu cầu. | securityVersion không tựlà @Version cho mọi entity. Chưa có optimisticlocking business trongcheckout; cần testsdeadlock/retry/stale vàlock order. |
| Page/sort/filter | Admin sourcepage 0 default 25,max100,sortcreatedAt/id; SRS mới default 20. | Có paging core; chưa đạt hợp đồng thống nhất hoặc chứng minh mọi list endpointbusiness (G27). |
| Query/N+1/JOIN | AdminReadController:40–41 gọi findByUserIdAndActiveTrue cho từng user trong page. | Phát hiện pattern N lượt query phụ từcode (G33); sốquery/thời gian runtime chưa đo. Không tuyên bố NFR fail. |
| Session/CSRF/IDOR/consent | CoreSecurityConfig/AccountGuard/AuthorizationPolicy deny và fallback profile false. Source không có adapter consent/share thật. | Coreguard không thay record/mentor/shareguard củaT2–T5. Cần projectionfield và mỗi HTTPdownloadguard; không tắt CSRF để testpass. |
| Performance/load | SYS-NFR-02:400×5/concurrency10/p95≤2s;03:5000×10/concurrency10/p95≤5s,4vCPU8GBSSD;04:list/detail50session,p95≤800ms. | Mục tiêu định lượng có. Không có benchmark tương ứng đã xác minh, không suyra tốc độ từ số hồ sơ. Recipe cần hoàn thiệnG31. |
| File/backup/log/error | SRS privateCLEAN,restoretest,UTC/requestId; source chỉ file metadata,backupscript/cấu hình,JSON error cũ. | File Service đang chặn CV T2/submission T3/verification thật; restore và HTTPSstaging chưa xác minh; G15–16,G27,G30. |
| Demo/dữ liệu thật | §19.4/25.3.1 đã chọn synthetic+Mailpit+30/90days; productiongate riêng. | Đã có policy demo; chưa có fixture400/5000/purge/releaseevidence và profile Mailpit staging thống nhất. Không xin lại policyUTH chỉ để viết demo; mọi dữ liệu thật vẫn phảiqua25.3. |


## 4. Danh sách GAP và CONFLICT

### 4.1. Phân loại và mức độ

P0 chặn đúng luồng liên quan vì chưa thể chọn một hành vi an toàn/đúng nghiệp vụ. P1 có thể làm phần độc lập nhưng phải chốt trước tích hợp. P2 hoàn thiện trước nghiệm thu/vận hành tương ứng. P3 cải tiến. Loại SRS là thiếu/ambiguity trong đặc tả; Compatibility là chênh giữa target và contract đã phát hành; Implementation/Source là code chưa có hoặc điểm kiểm tra source, không tự làm giảm điểm SRS vì chưa code.

**Tổng 34 phát hiện: 3 P0, 25 P1, 5 P2, 1 P3.** G01–G03 không chặn mọi dòng lập trình; phạm vi chặn được ghi ở từng hàng.

| ID | Mức độ | Team | Vị trí SRS / source | Vấn đề | Ảnh hưởng | Phương án đề xuất |
|---|---|---|---|---|---|---|
|G01|P0 — SRS|T5/T3/T1|§19.3 MVP-BR-14; §21.2 T63/R8; §21.3 P329; CT-06; T68/R14,R18|Active-set Placement và điều kiện bắt đầu workspace vẫn là “đề xuất mapping/T5 xác nhận”. APPROVED, ACTIVE và effectiveAt chưa có một predicate duy nhất.|Hai Placement APPROVED cùng SV/kỳ có thể qua guard khác nhau; T3 nhận Approved nhưng đợi Active không có trigger, hoặc mở workspace quá sớm.|Chốt trạng thái, active-key và canStartWorkspace cùng create/approve/handoff canonical; R01.|
|G02|P0 — SRS|T2/T5/T3/T1|MVP-BR-01/02; §3.2 P72; §13.3; §19.5 P287; POL-01/04; T68/R5; CT-01/06|SchoolApproved và vai trò cung cấp eligibility của T2 đã được quy định (§3.2 P72/§13.3), nhưng chưa có registry term canonical, mapping student–school–term và record/port/version cụ thể cho bằng chứng approval; owner term chưa chỉ rõ.|Hai team tạo term UUID khác cho cùng kỳ hoặc dùng profile.school thay trường chủ quản theo kỳ; approve từ bằng chứng học vụ không cùng nguồn làm vô hiệu invariant toàn dự án.|Canonical TermReference và StudentTermEligibilityPort/version; unique student+term; R02. Chỉ chặn đường đề xuất/Placement thật, không chặn CRUD độc lập.|
|G03|P0 — SRS|T4/T1/T5|§24.2 P382–388; MVP-BR-07; POL-TC-03|k=5 đã chốt; còn thiếu thuật toán secondary suppression/release, rule cho ô0, canonical query key, budget/window và oracle chống differencing.|Hai release10/10/10 và9/10/11 đều không có ô nhỏ nhưng có thể lộ một người khi tập khác biệt đã biết. Đổi actor/demand có thể né guard cục bộ.|Chốt release policy giới hạn theo enterprise+school+term+dataset/criteria version và lịch sử release; fail closed. Ghi nhận fallback tắt breakdown nâng cao tại P388; R03. P0 trước mở aggregate ENTERPRISE.|
|G04|P1 — SRS|T5/T4|§21.1 T62 bước03/04; CT-02 T66/R3; §11.1 Demand; DEM-10/13|CT-02 chỉ nhận APPROVED/VALID nhưng matching chạy trước Cooperation APPROVED; Demand chỉ DRAFT/READY và VALID chưa có định nghĩa.|Có thể tạo vòng chờ matching↔approval hoặc serialize nhầm enum. Không khẳng định vòng chờ chắc chắn vì VALID có thể là ý định tách riêng.|VALID là kết quả validation criteria snapshot, độc lập state Cooperation; R04.|
|G05|P1 — Compatibility|T1/T5|MVP-BR-04; §20.3 T60; §21.1; Organization.java:10; AuthorizationPolicy.java:37|Baseline yêu cầu T1 org ACTIVE/T5 school APPROVED; source T1 chỉ PENDING/VERIFIED/SUSPENDED. Verification baseline bỏ NEEDS_INFO, trong khi VER-13/14 Must yêu cầu vòng bổ sung cùng ID.|Code theo baseline có thể luôn bị chặn bởi guard cũ; các team chọn khác mapping operational/KYC hoặc resubmit/new attempt.|Một bảng enum/mapping và vòng REQUEST_INFO/resubmit được duyệt; tách T1 org với T5 school verification; R05.|
|G06|P1 — Compatibility|T1–T5|§20.4 T61/P303; V3:26; AdminController.java:35; docs/contracts/permission_matrix.csv|Code mới audit:read:global, account:admin:manage, student:profile:self:read_write khác registry hiện có; chưa có mapping exact và các capability mới chưa được grant rõ.|Module gọi đúng tên SRS vẫn bị deny; alias gộp có thể cấp thừa quyền quản trị hoặc nghiệp vụ.|Permission Registry canonical/legacy/actor/scope/action/version; giữ compatibility, không ngầm gộp quyền; R06.|
|G07|P1 — SRS/Compatibility|T1/T3/T4/T5|C06; §20.3 T60/R5–7; T61; §19.2 P273; PermissionCatalog.java:13|HR, mentor, school delegation và ADMIN được ủy quyền PII chưa có đầy đủ owner/record/grant-revoke/validity và authoritative capability contract.|Seed quyền HR cho toàn role ENTERPRISE có thể khiến nhân viên thường thành HR; thu hồi assignment không bảo đảm mất quyền request kế nếu không có guard authoritative.|Assignment/delegation có scope, actor, active/version/validity; typed capability port và grant authority; R07.|
|G08|P1 — SRS|T5/T2|MVP-BR-09; §21.2 invitation; CT-04; §19.5 P288|Canonical invitation đã rõ nhưng chưa có TTL, mốc now==expiresAt, ACCEPT/DECLINE cạnh tranh và rút ACCEPT trước Placement.|Hai UI có thể xử lý terminal khác nhau; quyết định đến đúng hạn hoặc retry tạo kết quả/event khác nhau.|Chốt TTL/bounds/server-time, terminal transition, withdrawal riêng, row lock/version và key replay; R08. Không nhầm invitation T5 với onboarding token T1.|
|G09|P1 — SRS|T5/T4/T2|PROP-07/10/11/14/15; §13.2 T35/R25,R30; §21.3 P330|Thiếu proposal-item state/reservedSet và công thức phân biệt chỗ trống đề xuất với chỉ tiêu chưa có Placement.|quantity−placed bỏ qua lời mời đang giữ chỗ; đếm toàn bộ lịch sử lại giữ slot vĩnh viễn. Hai cán bộ có thể bổ sung vượt capacity.|State/ledger reservation; openForNomination và unfilledTarget riêng; atomic reserve→place/release; R09.|
|G10|P1 — SRS|T2/T4/T5|CT-01/02; T68 student_skills; S3/S4/S7/S8; §24.1|skillCodes/availablePeriods/required/preferred/dateRange chưa có typed schema, declaration/null/empty semantics, toán tử lịch và đơn vị level/years.|[] có thể là UNKNOWN hoặc FAIL; lịch giao nhau một phần có thể MATCH hoặc FAIL dù tất cả S1–S11 đều pass.|DTO/JSON Schema versioned, khai báo tách khỏi dữ liệu rỗng, lịch containment/bounds và skill evidence policy; R10.|
|G11|P1 — SRS|T4/T5|US-T4-10 P137; §24.1 P377; CT-02|Chưa có công thức fit_score/weight/rounding/no-preferred/UNKNOWN/tie-break. “Nhiều kỹ năng hơn luôn cao hơn” không nhất thiết đúng với weight khác nhau.|Không có oracle xếp hạng; count và weighted ratio cho thứ tự khác nhau.|MVP dùng normalized weighted formula với mọi weight=1, có zero/tie/rounding rõ; unequal weights phải đổi version và AC; R11.|
|G12|P1 — SRS|T2/T4|C07 T57/R8; T17/R5,R7,R10; US-T2-03/06/16/17; §13.4|Nói sáu kiểm tra completeness nhưng không liệt kê; owner/catalog ngành, môn, định hướng còn OPEN, schema AC T2 ngoài file chưa có.|T2/T4 tự chọn profile complete, ngành nào hợp lệ hoặc khóa gửi nguyện vọng khi không có CV; khó nghiệm thu khách quan.|CompletenessPolicy v1 sáu predicate/reason + catalog owner/fixture; tách completeness khỏi school eligibility và matching; R12.|
|G13|P1 — SRS|T2/T4/T5|C08; §7.3 P114; PUT profile§22.2; §21.3 P332|Đã chọn khóa snapshot, nhưng chưa nêu field/snapshot lifetime, sửa ngành/skill trong lúc proposal và khi nào phải rerun/revalidate.|Có thể ghi đè snapshot đang được duyệt hoặc khóa toàn bộ profile vô hạn; matching/đề xuất dùng version không khớp.|Profile phiên mới độc lập snapshot; bảng field và revalidation theo content hash/version; R13.|
|G14|P1 — SRS|T2/T5|US-T2-07/08; C13/MVP-BR-08; T65/R11–13; T59|Opportunity “APPROVED và công bố” chưa map resource canonical; thiếu lifecycle/API rút/review preference và liên kết sang proposal.|T2 có thể tạo nguồn opportunity riêng hoặc biến duyệt nguyện vọng thành Placement; trạng thái withdraw không đồng nhất.|Opportunity projection của Cooperation đã approved/published; Preference T2/StudentIntentPort có state và withdrawal; R14.|
|G15|P1 — SRS/Implementation|T1/T2/T3/T5|C10; §20.6 P313–314; SYS-NFR-11; INT-13; FileMetadata.java:7; api.ts:39; nginx.conf:7|File private/scan đã chốt nhưng thiếu API binary/metadata/attach/download, MIME/size, timeout/retry và resource binding. Source chỉ metadata, frontend JSON, proxy64k.|CV/evidence/submission thật bị chặn hoặc dev tự chọn size/transport/guard; scanner lỗi có thể bị xử lý khác nhau.|File Contract multipart/stream, allowlist/size/timeout fail-closed và resource authorizer; R15. Không kết luận source đang rò file vì chưa có download.|
|G16|P1 — SRS|T1/T2/T5|§19.4 P282; §20.5 P308; §20.6 P314|Revoke phải chặn mọi lần đọc mới, nhưng direct presigned URL được phép mà chưa giải thích URL còn hạn sau revoke.|URL được cấp trước có thể bỏ qua guard khi GET storage sau revoke.|MVP dùng T1 streaming/gateway cho file phụ thuộc consent, guard mỗi request; chưa cấp direct storage URL cho PII; R16.|
|G17|P1 — SRS/Compatibility|T1/T2/T5|§20.5; CT-05; T68 consents/shares; ProfileVisibilityPort.java:7; §19.5 P288|Thiếu shape typed decision, thay grant/lineage, grantVersion và thứ tự hiệu lực race revoke–approve. Boolean port hiện tại không trả field/school/purpose.|Sharev1 vẫn đọc sau grantv2 hoặc Placement commit dựa consent cũ sau revoke; field projection khó kiểm chứng.|Một active grant trên tuple, versioned lineage, ProfileAccessDecision typed; lock order và authority tại commit; R17.|
|G18|P1 — SRS|T5/T3/T1|CT-06; §22.5 P355–357; §19.5 P288; §21.3 P334|Approved có outbox/dedup nhưng thiếu lifecycle activation/cancel/complete đi-về, consumer atomic inbox và retry/DLQ/reconcile cụ thể.|Cancelled trước khi Approved tới có thể sinh case hoạt động; đánh dấu event trước rồi lỗi tạo case có thể mất xử lý khi replay.|Event envelope/version/state, inbox+case+audit atomic, authoritative reread và retry/reprocess policy; R18.|
|G19|P1 — SRS|T1–T5|§22.1 P339; §22.5|Đã quy định key+hash/result nhưng chưa có TTL/serialization/request-hash scope, concurrent IN_PROGRESS, failure/retry và behavior sau expiry.|Hai request cùng key vẫn chạy trước khi lưu result; retry sau timeout hoặc expiry có thể sinh bản ghi nghiệp vụ thứ hai.|Atomic operation record, canonical hash/scope, expiry/replay status và business UNIQUE độc lập TTL; R19.|
|G20|P1 — SRS/Compatibility|T1/T3|FR-05; T32/R8; AccountTokenService.java:24|HR cần mời Mentor qua T1 nhưng service hiện chỉ cho global ADMIN và từ chối email đã tồn tại; thiếu scoped trusted port.|Không thể tích hợp bằng cách gọi service cũ hoặc cấp ADMIN cho HR mà vẫn giữ ranh giới quyền.|MentorInvitationPort giới hạn ENTERPRISE/org/group, luồng existing/new user và accept/revoke an toàn; R20.|
|G21|P1 — SRS|T3/T2|FR-14–16; T63/R9; INT-11; PATCH assignment/status|Thiếu bảng actor/trigger đầy đủ và luật quá hạn; SUBMITTED chưa bị ràng buộc chặt với việc commit bản nộp.|SV PATCH SUBMITTED không có artifact; stale review hoàn tất assignment hoặc file/submission và status khác transaction.|Submission service duy nhất chuyển SUBMITTED atomic; bảng transition, current-version guard và late policy; R21.|
|G22|P1 — SRS|T3/T1|FR-03/04/06; T34/R45,R47,R48|UNIQUE(group,case) vẫn cho một case ở hai group trong một cohort; chưa rõ quyền task cũ khi chuyển nhóm/mentor.|Chia nhóm cạnh tranh tạo double-membership; move có thể lộ hoặc làm mất quyền review lịch sử.|Một active group/case/cohort, move atomic/versioned và task-group snapshot + explicit handover; R22.|
|G23|P1 — SRS|T3/T2|FR-20; TC-12; T34/R57–60; T68/R21|Rubric có total weights100 và score 0–5 nhưng thiếu công thức /100, rounding, missing criterion, version template và sửa điểm đã publish.|Hai engine cùng đáp ứng mô tả nhưng cho tổng khác; chỉnh template có thể đổi điểm lịch sử.|Weighted formula, scale/rounding/validation đầy đủ; immutable template/evaluation revision; R23.|
|G24|P1 — SRS|T3/T1/T2|C12/MVP-BR-17; INT-12/POL-06; T68/R22; T32 gradevisibility|Predicate trường cho xem điểm chưa có source/policy scope/default/actor/endpoint; chỉ có enterprise HR toggle.|Có thể ngầm coi publish=schoolAllows hoặc thêm policy riêng; không có cùng oracle ba điều kiện.|SchoolResultVisibilityPolicy school/term defaultfalse, delegated editor, effective checks mỗi read; R24.|
|G25|P1 — SRS|T3/T2|FR-18/19; T34/R54; T33 ProgressReport; TC-11|“UNIQUE tùy policy theo tuần”; chưa định nghĩa tuần/timezone, deadline, edit/resubmit sau SUBMITTED/REVIEWED.|Client khác múi giờ tạo hai báo cáo cùng tuần hoặc sửa nội dung đã được nhận xét.|Business key case/week, cohorttimezone, version lineage và state/edit policy; R25.|
|G26|P1 — SRS|T3/T5|FR-27; T32 cases/close; §19.5 P289; C17/§21.3 P334|“Căn cứ hợp lệ/đủ quyền” để hoàn thành/chấm dứt sớm chưa có điều kiện, reason catalog, role/scope và response/event với Placement.|Hai team tự quyết case có cần đủ task/rubric hay chỉ quyết định school; dữ liệu hoàn thành T3 và Placement T5 lệch.|CloseCase policy/DTO/version, explicit school decision và outcome event; không tự công nhận tín chỉ hoặc sửa Placement; R26.|
|G27|P1 — SRS/Compatibility|T1–T5|§13.4; §22.1–22.4/P342; ApiErrorHandler.java:11; AdminReadController.java:30; docs/openapi.json|Target chưa có full OpenAPI/type/nullability/enum/status cho endpoints/DTO, alias các URL nguồn; source error thay errorCode, requestId header, default page 25 khác20.|Client và QA phải chọn schema/error path; CT-06 mẫu thiếu traceId dù danh sách bắt buộc có; approval/creation aliases không rõ.|Versioned machine-readable contract pack, compatibility map, một error envelope và page default được công bố; R27.|
|G28|P1 — Compatibility|T1–T5|§23.3 T69; AuditAction.java:3; AuditService.java:31; §6.5|LOGIN_SUCCESS/FAILURE,MEMBERSHIP_CHANGED,GRADE_* và một sốevent mới không thuộc enum hiện tại; source dùng LOGIN+result và code khác.|AuditAction.valueOf từ chối code mới, có thể rollback nghiệp vụ; rename thẳng làm đứt lịch sử/filter.|Event/action registry canonical/legacy/result/version và schema metadata, compatibility tests; R28.|
|G29|P2 — SRS|QA/T1–T5|§17/T49–50; §25.1/T73; §24.4–24.6|RTM mới ở mức nhóm; thiếu từng Must→API/service→test/oracle→evidence và coverage cho policy mới.|Không chứng minh 100% Must nghiệm thu, dễ dùng test mock hoặc test cũ thay luồng tích hợp mới.|RTM chi tiết và evidence manifest; tách SPECIFIED/IMPLEMENTED/HISTORICAL_PASS/NOT_RUN; R29.|
|G30|P2 — SRS/Implementation|T1/T2/T3/T4/T5/Ops|§19.4 P284–285; POL-07; V1 audit actorFK; V5/V6|Đã chọn demo30/90 ngày nhưng chưa có purge/anonymize/hold/backup/file-orphan procedure bảo toàn FK và history.|Xóa users ngày30 có thể bị FK chặn khi audit giữ90; cleanup chỉ file metadata vẫn giữ binary/backups.|Retention job/testclock/anonymize tombstone/hold và audit cleanup có thứ tự; R30. Không xin lại mốc đã chốt.|
|G31|P2 — SRS|QA/T1/T3/T4/Ops|SYS-NFR-01–04; §24.3/T70; §25.4D6|Có target định lượng nhưng recipe thiếu endpoint set, tổng runs hay mỗi worker, boundary đo/p95 method, manifestdataset/cache/container limits.|Các báo cáo không so sánh được; số5000 không tự chứng minh p95 đạt.|Benchmark manifest và reproducible script/report, không thay target đã chốt; R31.|
|G32|P2 — SRS|BA/LeaderT1–T5|P012; §8.4US-T4-10; §12.1T34/R28–42; §14.1S04; §15.5; §25.4–25.5|Legacy source có số/câu bị cắt, AC09.1 trùng và routecell hỏng; còn nhãn OPEN/k3/summary1.0 và signoffdemo/production dễ nhầm nếu đọc riêng.|Search/trace có thể dẫn đến giá trị đã supersede hoặc testID sai; không tự coi k3 là policy đang áp dụng.|Gắn SUPERSEDED/cross-reference, sửa lỗi nguyên bản và unique IDs trong v1.2; ghi người ký phạm vi demo/production riêng; R32.|
|G33|P2 — Source|T1/QA|src/main/java/vn/utbridora/auth/api/AdminReadController.java:40–41|Map từng user gọi membership repository riêng: pattern query theo từng dòng trong trang.|Có N truy vấn phụ tiềm năng với page N; chưa có số đo nên không kết luận latency/NFR fail.|Batch load memberships cho userIds của page/projection, đo SQL count và queryplan; R33.|
|G34|P3 — Architecture|T1–T5|§3.2/20.2; UtBridoraApplication.java; docs/TEAM_IMPLEMENTATION_GUIDE.md|Ownership/ports có nhưng chưa có bằng chứng dependency rules/architecture tests cho năm module; code hiện mới core.|Khi merge dễ có vòng entity/service hoặc ghi bảng team khác; chưa phải lý do đổi kiến trúc/framework.|Module public API/package conventions và architecture test boundary; R34.|

### 4.2. Khoảng cách triển khai và kiểm thử — không đánh tráo thành lỗi SRS

| ID | Source/bằng chứng | Trạng thái xác minh | Hệ quả cho kế hoạch |
|---|---|---|---|
| I01 | SecurityConfig,AuthController,AccountGuardFilter,AccountService,AccountTokenService,AccountLifecycleService,MembershipService,AdministrationGuard; V1–V7 | Có implementation core và test lịch sử T1; chưa chạy lại trong audit. | Tái sử dụng; giữ session/CSRF/authz. Nâng hợp đồng có compatibility, không viết lại project. |
| I02 | PermissionCatalog/AuthorizationPolicy, V3, docs/contracts/permission_matrix.csv | Chỉ role/catalog/org hiện hữu; chưa có đủ HR/school-delegation/mentor business guards theo v1.1; matrix vẫn DRAFT_NEEDS_SIGNOFF. | Cần G05–G07 và adapter thật. Không đồng nhất role ENTERPRISE với HR/Mentor. |
| I03 | src/main/java/vn/utbridora/auth/files/FileMetadata.java:7 | Chỉ metadata; không có storage/scanner/upload/download service thực. | C-10/INT-13/SYS-NFR-11 chưa được thực thi; chặn nghiệm thu CV/evidence/submission file thật. |
| I04 | ProfileVisibilityPort:7 và ProfileVisibilityConfiguration/DenyAllProfileVisibility | Boolean port và defaultfalse an toàn; chưa có T2/T5 consent/share validator thật hoặc field projection. | Không coi mock policy test là POL-TC-01/02 đã pass. Không thay DENY bằng giả định share. |
| I05 | Cấu trúc src/main/java hiện có | Chưa tìm thấy implementation Student/Matching/Cooperation/Internship hay endpoint target tương ứng trong checkout. | Phải nhận code các team và review adapter/DTO/migration; không kết luận về repository/nhánh chưa được cung cấp. |
| I06 | AccountTokenService:24, AuditAction/AuditService:31; docs/openapi.json | Core onboarding chỉ globalADMIN; registry action và OpenAPI chưa là baselinebusinessv1.1. | Mentor invite/scoped permission/audit event phải tích hợp qua contract; không bypass guard hoặc tự rename lịch sử. |
| I07 | application.yml; compose.local.yaml; compose.staging.yaml; web-demo/nginx.conf | Local dùng Mailpit; staging yêu cầu SMTP external, cookieSecure=true, frontend chỉ loopback8088; proxybody64k. | Cần profile demo-integration Mailpit, reverseproxyHTTPS được kiểm thử, storage/scan volume và transport limits phù hợp. Không cấu hình SMTP thật cho demo tráiPOL06. |
| I08 | docs/PREPUSH_TEST_EVIDENCE.json và XML test hiện hữu | Historical PASS28 unit/49Failsafe/14Chromium(3UImock), frontendbuildPASS ngày 30/10; các test mới toàn SRS v1.1 NOT RUN trong audit. | Giữ bằng chứng cũ đúng phạm vi; phải chạy INT/POL/load/file/placement/business tests khi module thật có. |
| I09 | MigrationUpgradeIT.java:21; application.yml | Test upgrade hiện khẳng định đúng5 migration từV2đếnV7; config ORMvalidate. Chưa xem flyway_schema_history của DBđích. | Update test cóchủý cùng V8+; migrate clean/upgrade và giữ dữ liệu, không sửaV1–V7 để làm pass. |
| I10 | Tracked source/migrations, deployment/backup docs | Chưa có bằng chứng seed400/5000và privacy/load suite, profile demo release pack, purge30/90/restore rehearsal cho v1.1. | Gói seed/contract/test/env có owner và evidence; không giả báo deploy/backup-restore thành công. |

### 4.3. Những mâu thuẫn đã được giải quyết, không đếm thành blocker

- k=3 ở §8/15 là nguồn cũ; C09/MVP-BR-07/§24.2/POL-08 chọn **k=5**. Khoảng trống còn lại là thuật toán/release/query oracle, không phải “chưa chốt k”.
- PROP cũ có câu T2 sở hữu invitation/account; C03/§23.4 chọn T5 invitation, T1 identity vàT2 decision/consent. Không để dòng cũ tạo lại nguồn canonical thứhai.
- S9 còn ghi “chờchốt implies” ở bảng test nguồn, nhưng C15/§24.1 chọn **Strict, không SpringBoot⇒Java** trong MVP. Kỳ vọng demo S9 là NOT_MATCH.
- Mốc thời gian/đối tượng consent, dữ liệu synthetic/Mailpit, retentiondemo30/90 và điều kiện schoolpublish+HRtoggle đã có quyết định demo. Chưa có chữ ký production không làm các policy đó chưa được chốt.
- V1_T4…V4_T4 là nhãn kế hoạch nguồn; §23.1 chọn một chuỗi Flywayglobal. Không coi hai cách đánh số là hai chiến lược cùng được phép.
- §25.3 yêu cầu phê duyệt trước dữ liệu thật/điều hành học vụthật; audit này không tự chốt quy chếUTH hoặc bắt PM ký production để developer bắtđầu phần demođộc lập.

## 5. Danh sách yêu cầu cần bổ sung cho SRS v1.2

**Nhãn hiệu lực:** Tất cả R01–R34 dưới đây là **ĐỀ XUẤT BỔ SUNG — CHƯA ĐƯỢC PHÊ DUYỆT**. Các con số mới như TTL7ngày, idempotency24giờ, file 10/20 MiB, scan60s, retry hoặc privacy release/rate cap là lựa chọn đề xuất của kiểm định, không phải trích SRS v1.1 hoặc quyết định UTH. Các quyết định demo đã có được giữ làm đầu vào; Leader các owner phải review/change-request nội dungmới, còn PM/nhà trường/chủ dữ liệu ký các cổng dùng thật theo§25.3.

Cách chọn phù hợp Java/Spring/MySQL: DTO/schema versioned và port trong ModularMonolith; transaction/pessimistic/optimisticlock+UNIQUE trong cùng MySQL; outbox/inbox trong DB; private filestream qua guard; scorer Java deterministic. Không thêm Redis/AI/microservices để tránh quyết định nghiệp vụ hoặc thay đổi kiến trúc theo sởthích. Tất cả test RV dưới đây đều là **đề xuất, NOT RUN**, không là bằng chứng đãpass.


### R01 — Placement active-set, phê duyệt và thời điểm T3 được hoạt động

**Vị trí:** Thay dòng Placement §21.2 và hoàn thiện MVP-BR-14, CT-06, §23.2. **Mức:** Must. **Owner:** T5. **Phụ thuộc:** T1/T2/T3. **Truy vết:** G01.

**Nội dung đề xuất đưa vào SRS:** ĐỀ XUẤT DEMO: trạng thái PENDING_APPROVAL→APPROVED→ACTIVE→COMPLETED; PENDING_APPROVAL/APPROVED/ACTIVE có thể CANCELLED bởi SCHOOL_STAFF được ủy quyền, kèm reason/version. APPROVED và ACTIVE là active-set cho invariant một student/term; COMPLETED/CANCELLED không giữ active-key nhưng giữ lịch sử. Tạo pending bằng POST /school/placements, UNIQUE(proposal_item_id); approve là thao tác riêng, tái kiểm mọi gate dưới transaction và cùng commit history/audit/outbox. Dùng generated nullable active_student_key và UNIQUE(active_student_key,term_id), hoặc guard DB tương đương có test race. T3 được tạo một case WAITING_START từ Placement APPROVED thật; chỉ chuyển IN_PROGRESS khi Placement ACTIVE và effectiveAt≤serverNow. T5 phát activation/cancellation event; Task/Submission bị chặn khi chưa IN_PROGRESS. OpenAPI ghi mapping các endpoint tạo trực tiếp cũ sang hai thao tác; chưa có API T5 trong checkout cần bảo toàn nên không tự nói đã migrate.

**Acceptance Criteria — Given–When–Then:** Given hai proposal ở hai doanh nghiệp cho cùng student/term và20 request approve đồng thời; When commit cạnh tranh; Then chỉ một APPROVED/ACTIVE, các request còn lại409, không overbook và chỉ một case theo Placement hợp lệ. Given APPROVED có effectiveAt tương lai; When nhận/replay event; Then một WAITING_START, không có task/submission trước hiệu lực. Cancel trước consumer phải ngăn case hoạt động.

**Test có thể thực thi:** RV-01: MySQL integration với barrier20 request, query unique active-key, inbox/case/audit/outbox; clock cố định cho future start. **Trạng thái: NOT RUN.**

### R02 — Nguồn term và eligibility có thẩm quyền

**Vị trí:** Bổ sung §20.2/23.2 và CT StudentTermEligibility cạnh CT-01. **Mức:** Must. **Owner:** T5 quản lý TermReference; T2 quản lý StudentTermEligibility. **Phụ thuộc:** T1/T3/T4. **Truy vết:** G02.

**Nội dung đề xuất đưa vào SRS:** ĐỀ XUẤT DEMO: một registry TermReference(termId,termCode,startDate,endDate,status) do T5 công bố, termCode canonical toàn dự án; các team không tự sinh termId cho cùng mã kỳ. StudentTermEligibility có studentUserId,termId,schoolOrgId,majorCode,schoolApproved,eligibilityVersion,approvedBy,approvedAt; UNIQUE(studentUserId,termId). T2 cung cấp port getEligibility(student,term), không suy approval từ completeness; người có school delegation mới ghi approval. Demo seed cùng UUID/termCode và version đã review. T5 đọc port, đối chiếu trường của Cooperation và khóa/kiểm version tại approve. Chọn T5 sở hữu kỳ vì đó là phạm vi duyệt Placement; T3 chỉ tham chiếu, tránh dùng Cohort thay term. Quy tắc học vụ thật vẫn thuộc production gate.

**Acceptance Criteria — Given–When–Then:** Given profile đầy đủ nhưng schoolApproved=false, approval ở trường khác/kỳ khác hoặc termCode chưa có; When propose/approve; Then422, không Placement/outbox. Given hai team dùng ID khác cho cùng termCode; When import contract; Then reject/mapping canonical có kiểm soát, không tạo kỳ mới ngầm.

**Test có thể thực thi:** RV-02: contract test port + MySQL FK/unique/version + fixture đa trường, hai kỳ, profile cùng user; kiểm tra cả thời điểm đề xuất và approve. **Trạng thái: NOT RUN.**

### R03 — Privacy release có oracle và giới hạn truy vấn liên tài khoản

**Vị trí:** Bổ sung thuật toán và cấu hình ở §24.2, cụ thể hóa POL-TC-03. **Mức:** Must. **Owner:** T4/T1. **Phụ thuộc:** T5/QA; Leader demo duyệt giới hạn chức năng. **Truy vết:** G03.

**Nội dung đề xuất đưa vào SRS:** ĐỀ XUẤT MVP bảo thủ: công bố tối đa một protected release cho mỗi enterpriseOrgId+schoolOrgId+termId trong một đợt demo; release khóa dataset snapshot, criteria/rule/taxonomy versions và có releaseId. Mọi actor/demand cùng org dùng chung ledger. Replay cùng canonical request trả cùng protected release; request đổi criteria/snapshot sau công bố trả409 PRIVACY_RELEASE_LOCKED, không kèm counts; chỉ mở đợt release mới sau privacy review, không tự reset bằng actor/demand mới. Các ô1–4 luôn suppressed; nếu có ô suppressed thì ẩn thêm ít nhất một ô khác, tổng và mọi tỷ lệ; ô0 dùng nhãn không công bố khi có nguy cơ suy diễn. Không xuất breakdown/export vượt protected release. Đề xuất rate cap30 request/10 phút theo enterprise,429+Retry-After, nhưng rate cap không thay release ledger. Tắt breakdown nâng cao theo fallback P388. Đây là lựa chọn giới hạn cho demo cần LeaderT4/T1/T5 chấp nhận, không tuyên bố k=5 chống tái định danh tuyệt đối; release policy tiếp theo phải có threat model và oracle mới.

**Acceptance Criteria — Given–When–Then:** Given hai actor/hai demand cùng org tạo query gần nhau, có thể cho vector10/10/10 rồi9/10/11; When evaluate/chart/export; Then không nhận hai release số chính xác, query thứ hai bị khóa hoặc cùng protected release. Kiểm tra các bộ0/1/4/5, phép trừ tổng, tỷ lệ và file; mọi field count bị ẩn phải vắng/nullable theo schema, không chứa số thật.

**Test có thể thực thi:** RV-03: unit privacy oracle + adversarial HTTP/JSON/CSV/chart tests + canonical-key bypass/rate window; đóng enterprise aggregate nếu chưa pass. **Trạng thái: NOT RUN.**

### R04 — Snapshot criteria hợp lệ trước hợp tác

**Vị trí:** Sửa CT-02 §22.3 và liên kết §21.1/DEM. **Mức:** Must. **Owner:** T5/T4. **Phụ thuộc:** T2. **Truy vết:** G04.

**Nội dung đề xuất đưa vào SRS:** VALID trong CT-02 là kết quả validation của criteria snapshot, không phải Cooperation APPROVED. T4 được evaluate Demand DRAFT hoặc READY đã validate, đúng owner và schema version. DTO mang demandVersion,criteriaVersion,criteriaHash,validationState=VALID cùng danh sách criteria typed; matching không đổi Demand state. Nomination/approve phải tái kiểm phiên bản/eligibility hiện tại; nếu snapshot cũ khác phiên đang dùng thì409 và rerun rõ ràng. Phương án tách validation khỏi approval giữ được luồng phân tích trước xin hợp tác.

**Acceptance Criteria — Given–When–Then:** Given Demand DRAFT hợp lệ chưa có Cooperation; When evaluate; Then tạo MatchRun snapshot, không tạo/approve Cooperation. Given criteria đã đổi; When nominate bằng run cũ; Then409 theo version policy, run cũ vẫn bất biến.

**Test có thể thực thi:** RV-04: T5→T4 contract và integration draft/ready/invalid/stale; assert không mutation state do matching. **Trạng thái: NOT RUN.**

### R05 — Một bảng trạng thái canonical và mapping legacy

**Vị trí:** Bổ sung phụ bảng §21.2/23.2, thay các alias rời trong Ch.6/9/13. **Mức:** Must. **Owner:** T1/T5. **Phụ thuộc:** T2/T3/T4/QA. **Truy vết:** G05.

**Nội dung đề xuất đưa vào SRS:** ĐỀ XUẤT: T1 operational enum PENDING/ACTIVE/SUSPENDED; legacy VERIFIED chỉ map sang ACTIVE bằng migration mới và mapping client được review, không tạo bất kỳ T5 school approval nào. Báo cáo này đề xuất chọn migration sang ACTIVE để tách thuật ngữ operational khỏi KYC; không đồng thời áp dụng hai mapping khác nhau. Chọn canonical T5 verification DRAFT→SUBMITTED→APPROVED/REJECTED/NEEDS_CHANGES; REQUEST_INFO→NEEDS_CHANGES; resubmit giữ verificationId, tăng submissionNo; chỉ REJECTED xin lại mới tạo attemptId mới có previousId. Cooperation dùng NEEDS_CHANGES tương đương NEEDS_INFO cũ, cùng cooperationId qua resubmit. Mapping phải ghi wire enum, persisted enum, transition, actor và error, không thay migration đã apply.

**Acceptance Criteria — Given–When–Then:** Given T1 org ACTIVE nhưng chưa được schoolA approve; When submit; Then deny. SchoolA APPROVED không cho schoolB. Given verification cần bổ sung; When resubmit; Then cùng ID/history và version mới; REJECTED xin lại là ID mới có lineage.

**Test có thể thực thi:** RV-05: migration upgrade fixture legacy VERIFIED/PENDING/SUSPENDED, guard đa trường, state-matrix tests và API alias compatibility. **Trạng thái: NOT RUN.**

### R06 — Permission Registry v1.1 tương thích và không cấp thừa quyền

**Vị trí:** Bổ sung exact mapping cạnh §20.4 và cập nhật contract matrix sau phê duyệt. **Mức:** Must. **Owner:** T1. **Phụ thuộc:** T2–T5. **Truy vết:** G06.

**Nội dung đề xuất đưa vào SRS:** Mỗi capability ghi canonical code, code cũ, actor, scope, API/action, business guard và owner. ĐỀ XUẤT giữ các code core đã phát hành như audit:global:read, membership:admin:write, account:admin:status và account:admin:revoke; audit:read:global chỉ alias có test. Không biến account:admin:manage thành quyền tự động cho mọi thao tác cũ; read_write phải được tách hoặc map hai action được phê duyệt. Những mã t3:*,taxonomy:*,matching:* mới có role ceiling và capability/assignment bổ sung, không chỉ seed rộng cho ENTERPRISE. Migration mới cấp code/mapping với least privilege; unknown permission vẫn DENY.

**Acceptance Criteria — Given–When–Then:** Given actor chỉ có quyền audit; When gọi membership/status/PII; Then403. Given cùng capability qua canonical/alias được duyệt; When quyết định authorization; Then cùng kết quả, không tự mở rộng action. ENTERPRISE thường không có HR capability.

**Test có thể thực thi:** RV-06: registry completeness/static contract + MockMvc positive/negative từng row + permission migration upgrade. **Trạng thái: NOT RUN.**

### R07 — Grant/revoke HR, mentor và school delegation theo nguồn có thẩm quyền

**Vị trí:** Bổ sung §20.2–20.4, dictionary và port capability. **Mức:** Must. **Owner:** T3 cho HR/mentor assignment; T1 cho delegation framework. **Phụ thuộc:** T5 school authority/T4 taxonomy/T2 consumers. **Truy vết:** G07.

**Nội dung đề xuất đưa vào SRS:** ĐỀ XUẤT: capability assignment có subjectUserId,action,enterprise/school scope,group/case/term nếu áp dụng,validFrom/Until,active,version,grantedBy/reason. T3 sở hữu coordinator/mentor assignments; T1 kiểm qua port authoritative mỗi request, kết hợp membership/role ceiling. Grantor phải có quyền quản lý đúng scope; không tự nâng global role hoặc cấp PII cho chính mình bằng quyền vận hành. School PII/publish/taxonomy delegation cần quyết định của school authority theo fixture demo được review. Revoke ghi audit và làm request kế tiếp không đủ capability, kể cả session còn tồn tại. Quyền ADMIN vận hành không là bypass business.

**Acceptance Criteria — Given–When–Then:** Given hai ENTERPRISE cùng org chỉ một người có HR assignment; When tạo cohort; Then chỉ HR thành công. Given mentor vừa bị revoke; When gọi bằng session cũ vào group; Then403/401 theo contract, không ghi task. Delegation trườngA không dùng ởB.

**Test có thể thực thi:** RV-07: scoped assignment policy test + HTTP/DB grant/revoke/version/race; các trường hợp self-escalation và fake org/actor. **Trạng thái: NOT RUN.**

### R08 — Thời hạn và quyết định lời mời thực tập

**Vị trí:** Bổ sung MVP-BR-09, CT-04, state invitation §21.2. **Mức:** Must. **Owner:** T5. **Phụ thuộc:** T2/T1 audit. **Truy vết:** G08.

**Nội dung đề xuất đưa vào SRS:** ĐỀ XUẤT DEMO: invitation T5 có expiresAt do server cấp, mặc định7 ngày, tối đa30 ngày và không vượt hạn tuyển/term. now≥expiresAt là hết hạn. Chỉ PENDING được ACCEPT/DECLINE dưới lock/version; một terminal decision có hiệu lực. Cùng key/payload replay kết quả; đổi quyết định terminal bằng key khác trả409. Muốn rút ACCEPT trước Placement dùng action WITHDRAW có scope/reason/audit riêng, giải phóng reservation theo R09; sau Placement chuyển workflow có thẩm quyền. T1 onboarding/mentor token là loại khác, không dùng TTL hoặc owner T5 để ghi đè token T1.

**Acceptance Criteria — Given–When–Then:** Given ACCEPT và DECLINE20 request đua trên cùng invitation; When commit; Then một terminal decision/event, không giữ slot hai lần. Given now==expiresAt; When ACCEPT; Then từ chối và trạng thái expired theo contract. Replay không phát event thứ hai.

**Test có thể thực thi:** RV-08: fake Clock boundary, optimistic/pessimistic race, duplicate key/body, wrong student/school/org và withdrawal trước/sau Placement. **Trạng thái: NOT RUN.**

### R09 — Capacity reservation và Placement được đếm một lần

**Vị trí:** Bổ sung proposal-item state ở §21.2, công thức ở §13.2/23.2. **Mức:** Must. **Owner:** T5. **Phụ thuộc:** T2/T4/T3. **Truy vết:** G09.

**Nội dung đề xuất đưa vào SRS:** ĐỀ XUẤT: proposal-item state phân biệt RESERVED,INVITED,STUDENT_ACCEPTED,SHARED,ENTERPRISE_ACCEPTED,PLACED với DECLINED,EXPIRED,REVOKED; invitation/consent vẫn là thực thể riêng. Item mới giữ slot ngay khi batch/item commit ở RESERVED; phát invitation chuyển RESERVED→INVITED trong transaction có reference T5 canonical. RESERVED chưa phát invite có reservationUntil=min(now+24h, hạn tuyển); đã INVITED dùng hạn lời mời, khi SHARE có consent thì không vượt consent expiry/hạn tuyển. Expiry của reservation đổi proposal item EXPIRED, không tự sửa một invitation đã ACCEPTED. WITHDRAW trước Placement ở R08 map REVOKED và release đúng một lần. Sau khi PLACED, revoke consent chỉ chặn đọc theo R17, không tự giải phóng chỉ tiêu đã tiếp nhận. reservedSet là năm trạng thái trước PLACED, chỉ khi chưa có Placement được tính ở placed. openForNomination=quantitySnapshot−placed−activeReservations; unfilledTarget=quantitySnapshot−placed. placed đếm distinct Placement hợp lệ APPROVED/ACTIVE/COMPLETED, không CANCELLED; đây là số tiếp nhận theo demand, khác active-set student/term. Chuyển reservation→Placement là một transaction không cộng kép; decline/expire/revoke trước Placement release slot. Lock capacity/cooperation cùng lock order được chốt; không tin remaining từ UI. Một batch/cooperation và unique student/batch giữ nguyên.

**Acceptance Criteria — Given–When–Then:** Given quantity2, một Placement và một invitation đang giữ chỗ; When query capacity; Then openForNomination0, unfilledTarget1. Hai staff cùng thêm ứng viên không qua được. Sau decline giải phóng đúng một slot; hai request bổ sung chỉ một thành công, cùng batchId.

**Test có thể thực thi:** RV-09: table-driven item transitions + MySQL races20 add/decline/expire/approve/cancel, count distinct và audit rollback. **Trạng thái: NOT RUN.**

### R10 — DTO profile/criteria và oracle FAIL–UNKNOWN

**Vị trí:** Bổ sung JSON Schema/Java DTO cho CT-01/CT-02 và S1–S11. **Mức:** Must. **Owner:** T2/T4/T5. **Phụ thuộc:** T1 DTO/error conventions. **Truy vết:** G10.

**Nội dung đề xuất đưa vào SRS:** ĐỀ XUẤT DEMO: tách skillsDeclared và availabilityDeclared khỏi mảng dữ liệu; false/null biểu thị chưa khai, true+[] là đã khai rỗng. Mỗi skill có canonical code,level1–5,yearsPractice≥0 nếu có,source và evidenceRef tùy chọn; không gắn nhãn VERIFIED cho tự khai. RequiredSkill dùng code/operator rõ; major dùng tập mã được chấp nhận. availablePeriods dùng LocalDate yyyy-MM-dd start/end inclusive; tập hợp khoảng rảnh phải bao phủ toàn demand dateRange, không chỉ giao nhau một phần. Audit/deadline vẫn là Instant UTC. Skills chưa khai→UNKNOWN; đã khai thiếu code required→FAIL; major đúng mã nhưng ngoài tập→FAIL; FAIL luôn thắng UNKNOWN. Skills tự khai canonical được đánh giá theo fixtures S1 nhưng giải thích rõ chưa xác minh; tiêu chí đòi chứng thực chưa được bật ngầm.

**Acceptance Criteria — Given–When–Then:** Given skillsDeclared=false,[]; When evaluate requiredJava; Then UNKNOWN. Given true,[]; Then FAIL. Sai major+undeclared skills→NOT_MATCH. Khoảng rảnh chỉ bao phủ nửa thời gian→FAIL; đúng hai biên→PASS. Alias không tự sinh implies.

**Test có thể thực thi:** RV-10: JSON Schema contract + pure Java table/property tests S1–S11, empty/null, duplicate alias, date boundary, timezone và malformed criteria. **Trạng thái: NOT RUN.**

### R11 — Công thức Preferred Scoring và thứ tự ổn định

**Vị trí:** Thay AC của US-T4-10, bổ sung §24.1/CT-02. **Mức:** Must. **Owner:** T4. **Phụ thuộc:** T5/T2/QA. **Truy vết:** G11.

**Nội dung đề xuất đưa vào SRS:** ĐỀ XUẤT phù hợp AC hiện có: chỉ overall MATCH được xếp hạng; fitScore=roundHalfUp(100×Σ(weight_i×preferredPass_i)/Σweight_i,2), MVP cố định mọi weight_i=1. Không Preferred thì score 0; UNKNOWN Preferred góp0 nhưng không đổi overall status. Các nhóm không MATCH có fitScore=null. Sort score giảm dần rồi studentUserId tăng dần. Lưu scoringVersion, weights và snapshot tham chiếu. Uniform weights bảo đảm người khớp nhiều Preferred hơn có điểm cao hơn như US-T4-10. Nếu muốn weight khác nhau, phải version công thức và sửa AC monotonic-count qua change request; không ngầm gọi thuật toán đếm là weighted tùy ý.

**Acceptance Criteria — Given–When–Then:** Given ba Preferred, A pass2 vàB pass1, mọi Required pass; When score; Then A66.67>B33.33. Không Preferred→0; UNKNOWN Preferred không làm NOT_MATCH; tie theo userId; cùng snapshot/rule trả cùng thứ tự.

**Test có thể thực thi:** RV-11: deterministic scoring oracle, rounding/no-divide-zero/tie tests và snapshot history/replay. **Trạng thái: NOT RUN.**

### R12 — Sáu kiểm tra completeness và catalog demo có owner

**Vị trí:** Bổ sung policy cạnh C07/§7.3, catalog ownership §20.2. **Mức:** Must. **Owner:** T4 cho CompletenessPolicy; T2 cho profile/catalog ngành demo. **Phụ thuộc:** T5/QA. **Truy vết:** G12.

**Nội dung đề xuất đưa vào SRS:** ĐỀ XUẤT demo-v1 gồm đúng6 predicate: (1) majorCode thuộc catalog active; (2) có tham chiếu trường chủ quản/studentTerm đúng context; (3) skillsDeclared=true; (4) mọi skill đã khai resolve canonical code và dữ liệu level/years hợp lệ; (5) availabilityDeclared=true; (6) mọi khoảng rảnh hợp lệ theo R10. Danh sách rỗng được khai rõ vẫn đủ thông tin đánh giá và có thể NOT_MATCH, khác chưa khai. complete=AND sáu predicate, mỗi predicate trả code/reason và completenessVersion; không tự yêu cầu CV/dự án, không đồng nhất schoolApproved hoặc MATCH. T2 sở hữu catalog ngành/định hướng giả lập cùng majorCode ổn định; T4 sở hữu skill/alias và mapping; môn học không trong Mustmatching nếu chưa có owner/data source được chốt.

**Acceptance Criteria — Given–When–Then:** Given cùng profile fixture và policyVersion; When T2 hiển thị/T4 kiểm tra; Then cùng sáu kết quả. Khai rỗng không trở thành chưa khai. Đổi major giữ skill/project, đánh dấu mục ngoài ngành và không xóa lịch sử; profile complete vẫn có thể ineligible/NOT_MATCH.

**Test có thể thực thi:** RV-12: shared policy contract fixtures6×boundary, major/catalog change, negative level/years và no-CV requirement regression. **Trạng thái: NOT RUN.**

### R13 — Sửa profile theo phiên bản, giữ nguyên snapshot đã sử dụng

**Vị trí:** Cụ thể hóa C08/§7.3/PUT profile/§21.3. **Mức:** Must. **Owner:** T2. **Phụ thuộc:** T4/T5. **Truy vết:** G13.

**Nội dung đề xuất đưa vào SRS:** Profile hiện tại được sửa bằng expectedProfileVersion, tạo phiên bản mới; snapshot đã tham gia MatchRun/Cooperation/Proposal không bị thay đổi. ĐỀ XUẤT các field ảnh hưởng matching là majorCode,school/term context,skills/source và availablePeriods; dùng contentHash riêng để nhận biết thay đổi liên quan. Sửa tên hiển thị/project mô tả không tự sửa kết quả run cũ. Khi nội dung criteria-relevant đổi, nomination/approval phải rerun hoặc có bước revalidate ghi version mới; không âm thầm dùng run stale. Không tự khóa toàn hồ sơ vì có invitation; nếu cần khóa field khác phải có bảng policy/trigger/lifetime được chốt.

**Acceptance Criteria — Given–When–Then:** Given run đã COMPLETED và proposal giữ profile v1; When student đổi skill/major thànhv2; Then v1/run cũ không đổi, quyết định dùngv1 bị báo stale nếu hash liên quan đã đổi. Stale PUT trả409, không mất project/skill cũ khi đổi ngành.

**Test có thể thực thi:** RV-13: optimistic update concurrency, snapshot immutability, relevant/unrelated field changes, module contract revalidation. **Trạng thái: NOT RUN.**

### R14 — Opportunity và Preference có nguồn, trạng thái và quyền rút rõ ràng

**Vị trí:** Bổ sung §20.2/22.2 và C13/US-T2-07/08. **Mức:** Must. **Owner:** T5 cho Opportunity projection; T2 cho Preference. **Phụ thuộc:** T1 permission/QA. **Truy vết:** G14.

**Nội dung đề xuất đưa vào SRS:** ĐỀ XUẤT: Opportunity là projection của Cooperation APPROVED đã được SCHOOL_STAFF có school scope công bố, opportunityId=cooperationId; không tạo bản Demand nguồn thứ hai. Publication có publishedAt/closedAt/version và chỉ hiển thị thông tin doanh nghiệp được công bố. Preference thuộc T2, UNIQUE(studentUserId,opportunityId,termId), có SUBMITTED/WITHDRAWN/REVIEWED và expectedVersion. POST rút riêng lấy actor từ session; T5 school review qua StudentIntentPort, ghi proposalRef khi tiếp nhận, không tự sinh invitation acceptance/consent/Placement. Rút preference sau khi đã có invitation không tự đổi invitation hoặc Placement; UI phải chỉ ra hành động cần làm ở resource tương ứng.

**Acceptance Criteria — Given–When–Then:** Given Cooperation draft/unpublished hoặc khác school context; When student list; Then không xuất hiện. Submit lặp không nhân đôi; owner rút trước khi review được ghi lịch sử, staff sai school không review; reviewed intent không tự tạo Placement.

**Test có thể thực thi:** RV-14: projection/list scope, publish/close transitions, preference create/replay/withdraw/review và reference integrity. **Trạng thái: NOT RUN.**

### R15 — File Contract có transport, policy và scan thật

**Vị trí:** Thêm File API/DTO tại §22, policy/limits §20.6 và data binding §23.2. **Mức:** Must. **Owner:** T1. **Phụ thuộc:** T2/T3/T5/Frontend/Ops. **Truy vết:** G15.

**Nội dung đề xuất đưa vào SRS:** ĐỀ XUẤT MVP: private storage volume riêng, tên storage do server sinh; binary không vào MySQL. POST upload multipart/form-data với session+CSRF là ngoại lệ rõ của chuẩn JSON; responseJSON gồm fileId,sha256 server tính,scanStatus,version. CV/evidence cho PDF/PNG/JPEG tối đa10MiB; submission PDF/ZIP tối đa20MiB; importPlacement CSV/XLSX tối đa5MiB/1000dòng. Kiểm MIME bằng nội dung, tên/path an toàn; ZIP giới hạn tổng giải nén100MiB/1000entry, không thực thi. Scanner thật: PENDING_SCAN→CLEAN/REJECTED, timeout60s và tối đa3 retry theo5/30/120s; hết retry vẫn không tải được, reason an toàn. Mapping PENDING metadata cũ phải explicit. ResourceAttachmentPort xác minh quyền owner/consent/assignment trước attach/download; module giữ fileId. Proxy/backend/frontend có FormData/Blob/error handling đồng bộ giới hạn; không công khai blobURL. Nếu chỉ lưu external HTTPS submission link thì không tự fetch URL tùy ý từ backend trong MVP.

**Acceptance Criteria — Given–When–Then:** Given file đúng policy; When upload/scan; Then chỉ CLEAN mới tải được. Oversize413, MIME/unsafe422 hoặc trạng thái rejected theo schema; scanner lỗi không trở thành CLEAN; fakeforeignfileId/attachment/resourceId bị deny. Tệp import quá dòng bị từ chối có báo cáo lỗi, không commit nửa luồng trái contract.

**Test có thể thực thi:** RV-15: API multipart/CSRF/size/MIME/path/archive tests, real scanner fixture an toàn, private storage persistence, authorized download và frontend upload/download E2E. **Trạng thái: NOT RUN.**

### R16 — Thu hồi consent chặn cả URL tải file đã được cấp

**Vị trí:** Bổ sung ràng buộc §20.6, liên kết §20.5. **Mức:** Must. **Owner:** T1. **Phụ thuộc:** T2/T5. **Truy vết:** G16.

**Nội dung đề xuất đưa vào SRS:** ĐỀ XUẤT MVP: mọi download chứa CV/evidence/PII phụ thuộc consent đi qua T1 streaming endpoint hoặc gateway có authorization ở mỗi request, kể cả Range/retry; không trả direct storage presigned bearer URL cho loại file này. Request mới kiểm account/scope/resource/CLEAN và consent/share version trước khi trả bytes. URL/fileId không thay phiên/quyền. Revoke không thu lại bytes đã được người nhận tải hoặc một response đã bắt đầu trước hiệu lực revoke; các request mới sau commit phải deny. Presign cho dữ liệu không phụ thuộc consent chỉ bật sau khi có contract riêng và test tương đương.

**Acceptance Criteria — Given–When–Then:** Given enterprise lấy link khi consent còn hiệu lực; When student revoke commit rồi enterprise GET/Range URL cũ bằng cùng session; Then403/404 và không có bytes nhạy cảm. Link bị sao chép cho org khác cũng deny.

**Test có thể thực thi:** RV-16: revoke-between-link-and-download, expired grant, Range request, forwarded link và file authorization integration. **Trạng thái: NOT RUN.**

### R17 — Consent/share phiên bản, field decision và thứ tự hiệu lực

**Vị trí:** Hoàn thiện CT-05/§20.5/§23.2 và port visibility. **Mức:** Must. **Owner:** T2 consent; T5 share; T1 decision. **Phụ thuộc:** T3 file consumers/QA. **Truy vết:** G17.

**Nội dung đề xuất đưa vào SRS:** ĐỀ XUẤT: một grant active trên tuple student/recipient/school/term/purpose; cấp lại tạo consentVersion mới và vô hiệu lineage grant/share cũ. Share mang consentId+consentVersion+shareVersion; validUntil không muộn hơn consent expiry và policy30ngày. ProfileAccessDecision chứa allowedFields,school,purpose,validUntil,consent/share IDs+versions và reasonCode; allowedFields là school policy∩consent∩share, DTO projection không serialize field ngoài. Owner self là nhánh riêng. Revoke/regrant/approve serialize qua authoritative consent row/version cùng DB; lock order studentTerm→consent→capacity→proposal/placement được công bố. Nếu revoke commit trước approve kiểm tra/commit, approve không dùng version cũ. Sau Placement, revoke chỉ chặn read tiếp theo, không tự xóa Placement; điều chỉnh qua workflow R18/R26.

**Acceptance Criteria — Given–When–Then:** Given consent{major,skills},share{skills,projects}; When read; Then chỉ skills. Regrant v2 còn một field làm share v1 invalid. Race revoke/approve bằng barrier phải có kết quả đúng thứ tự khóa/commit, không Placement dùng consent đã revoke trước đó.

**Test có thể thực thi:** RV-17: typed port contract, cache/version invalidation, JSON field assertion và MySQL concurrent revoke/regrant/approve, including prior valid Placement. **Trạng thái: NOT RUN.**

### R18 — Outbox/inbox và lifecycle handoff không mất dữ liệu

**Vị trí:** Mở rộng §22.5, CT-06 và §23.4/21.3. **Mức:** Must. **Owner:** T5 producer Placement; T3 consumer/case outcome. **Phụ thuộc:** T1 audit/Ops. **Truy vết:** G18.

**Nội dung đề xuất đưa vào SRS:** ĐỀ XUẤT: PlacementApproved,Activated,Cancelled,Completed có eventId,type,schemaVersion,aggregateId/version,effectiveAt,traceId,scope IDs tối thiểu; theo quy tắc compatibilityv1. Producer commit mutation+audit+outbox cùng transaction. Consumer commit inbox eventId unique+case state/version+audit cùng transaction rồi ACK; placementId unique độc lập eventId. Consumer đọc PlacementPort authoritative trước tạo/kích hoạt, từ chối forged/unknown/stale và xử lý cancel tới trước Approved bằng version/tombstone. Retry tối đa5 lần với1/5/30/300/1800s, sau đó DLQ có lý do; reprocess cần quyền vận hành và vẫn kiểm state/scope, không xóa dedup để ép chạy. T3 CaseClosed chỉ thông báo outcome có version; T5 là nơi quyết định cập nhật Placement. Có reconciliation job phát hiện outbox pending/case lệch, không tự giả Placement.

**Acceptance Criteria — Given–When–Then:** Given event xử lý lỗi sau insert inbox trước createcase; When rollback/replay; Then vẫn xử lý được đúng một case. Cancelv4 trước Approvedv3 không tạo case hoạt động. Event duplicate/newID cùng placement không nhân đôi; exhausted retry vàoDLQ, manual replay không bypass current state.

**Test có thể thực thi:** RV-18: fault injection tại các điểm transaction, duplicate/out-of-order/future/unknown event, bounded retry/DLQ/reprocess và reconciliation integration. **Trạng thái: NOT RUN.**

### R19 — Idempotency atomic và retry sau timeout

**Vị trí:** Cụ thể hóa §22.1 P339 và operation schema §23.2. **Mức:** Must. **Owner:** T1 common infrastructure; owner từng mutation thực thi. **Phụ thuộc:** T2–T5. **Truy vết:** G19.

**Nội dung đề xuất đưa vào SRS:** ĐỀ XUẤT: key UUID, scope actorUserId+organization/school context+operation+key; hash SHA256 của payload canonical sau validation, không dựa thứ tự keyJSON. Ghi key/result cùng commit với business mutation và audit; một unique operation record ngăn request cùng key chạy song song. IN_PROGRESS/lock timeout trả409 STATE_CONFLICT kèm lý do REQUEST_IN_PROGRESS và Retry-After; key khác payload409. TTL demo24giờ chỉ áp dụng cache response. Operation fingerprint/requestHash/effectRef giữ bền vững theo tuổi thọ aggregate/workflow và retention đã duyệt, tách khỏi cache; key đã biết nhưng response hết hạn trả409 với reason IDEMPOTENCY_RESPONSE_EXPIRED, không chạy lại. Với consent regrant/reissue invitation là thao tác có thể lặp hợp lệ, mỗi hành động mới có logicalOperationId và expectedAggregateVersion; effect/lineage lưu unique operationId độc lập cache. Retry cũ giữ nguyên operationId/version nên không sinh hành động mới. Sau khi workflow/retention đóng, request cũ bị state/version gate từ chối; không cam kết retry vô hạn qua việc xóa dữ liệu hoặc reset môi trường. Không dựa chỉ vào natural UNIQUE để bảo đảm idempotency. Rollback không để COMPLETED giả; transient retry được kiểm soát. Mỗi replay vẫn kiểm authentication/permission/consent hiện hành trước trả result; không lưu raw token/PII dư trong cache.

**Acceptance Criteria — Given–When–Then:** Given20 request cùng key/body hoặc response đầu bị mất sau commit; When retry; Then một mutation/audit-success/outbox và cùng result cho caller còn quyền. Khác body409; quyền bị revoke trước replay thìdeny; sau TTL không duplicate Placement/invitation.

**Test có thể thực thi:** RV-19: canonical payload equivalence, concurrent unique record, crash/rollback/response-loss/TTL, changed-scope and revoked-caller tests. **Trạng thái: NOT RUN.**

### R20 — Mời Mentor bằng port T1 được giới hạn scope

**Vị trí:** Bổ sung T1↔T3 contract cho FR-05/API mentor-invitations. **Mức:** Must. **Owner:** T1/T3. **Phụ thuộc:** T5 enterprise identity/QA. **Truy vết:** G20.

**Nội dung đề xuất đưa vào SRS:** ĐỀ XUẤT MentorInvitationPort nhận HR principal đã được kiểm, enterpriseOrgId lấy từ context server, email/groupIds có kiểm scope và idempotency. Chỉ có thể mời/đề nghị ENTERPRISE membership cùng org và mentor assignment phù hợp; không cấp ADMIN/SCHOOL_STAFF hoặc org khác. T1 là nơi tạo token/hash/expiry và user; T3 giữ invitation/assignment reference, không copy credential. Email đã có user dùng luồng chấp thuận gắn membership/assignment, không tạo user trùng, không coi existing email là tự động nhận quyền. Token accept một lần; revoke assignment áp dụng request kế. Không gọi bypass @PreAuthorize hoặc cấp globalADMIN cho HR.

**Acceptance Criteria — Given–When–Then:** Given HRorgA; When mời mới/đã có tài khoản làm Mentor; Then đúng một identity và assignment có chấp thuận. PayloadorgB/roleADMIN bị403; tokenreplay không cấp quyền hai lần; revokementor deny request kế.

**Test có thể thực thi:** RV-20: delegated port policy và HTTP/integration new/existing user, wrongorg/role, duplicate email/idempotency, token lifecycle và revoked assignment. **Trạng thái: NOT RUN.**

### R21 — Task state và submission/review atomic, xử lý quá hạn

**Vị trí:** Thay bảng TaskAssignment§21.2, bổ sung FR-14–16/INT-11. **Mức:** Must. **Owner:** T3. **Phụ thuộc:** T2/T1 files. **Truy vết:** G21.

**Nội dung đề xuất đưa vào SRS:** ĐỀ XUẤT: SV PATCH chỉ NEW→IN_PROGRESS và REVISION_REQUIRED→IN_PROGRESS. Chỉ submission service được chuyển IN_PROGRESS→SUBMITTED khi commit một bản nộp mới hợp lệ; mentor review đúng currentSubmissionVersion mới chuyển SUBMITTED→REVISION_REQUIRED(kèm reason) hoặc COMPLETED. Cấm direct PATCH SUBMITTED/COMPLETED và review bản cũ. versionNo cấp atomic, UNIQUE(assignment,versionNo), giữ file/version cũ. Demo lateAllowed mặc định true: nộp sau dueAt ghi late=true; task lateAllowed=false thì serverNow>dueAt trả422, đúng dueAt vẫn nhận. Task/late policy thay đổi có version/audit, không dùng giờ client. Chỉ case đang hoạt động và assignment đúng user/group được thao tác.

**Acceptance Criteria — Given–When–Then:** Given không có artifact; When SV PATCH SUBMITTED; Then422. Submitv2+status commit cùng nhau; audit lỗi rollback cả hai. Reviewv1 sauv2 trả409. At dueAt accepted; after dueAt theo lateAllowed; hai submit không mất version.

**Test có thể thực thi:** RV-21: transition matrix actor/trigger, Clock boundaries, real file CLEAN, current-version review và simultaneous submit/review test. **Trạng thái: NOT RUN.**

### R22 — Một group đang hiệu lực và quyền task khi chuyển nhóm

**Vị trí:** Bổ sung FR-03/04/06 và dictionary group_members/mentor_assignments. **Mức:** Must. **Owner:** T3. **Phụ thuộc:** T1 capability/QA. **Truy vết:** G22.

**Nội dung đề xuất đưa vào SRS:** ĐỀ XUẤT: một case tối đa một active group trong cùng cohort; DB guard tương đương UNIQUE(cohortId,caseId) cho membership active, bên cạnh group/case. Move group là một transaction expectedVersion và audit; không xóa task/submission/history. Task assignment giữ group snapshot lúc giao; review chỉ cho mentor còn active assignment ở snapshot group hoặc người nhận handover đích danh đã được HR được ủy quyền và audit. Mentor bị revoke mất quyền request kế; membershipgroup hiện tại không tự mở task cũ của group khác. Preview auto-group mang sourceVersion/hash và confirm phải revalidate, không dùng danh sách preview stale.

**Acceptance Criteria — Given–When–Then:** Given hai HR đồng thời gán cùng case vàoG1/G2; When commit; Then một activegroup, một409. Move không mất v1/v2; mentor đã revoke không review; mentor group mới chỉ thấy task cũ khi có explicit handover.

**Test có thể thực thi:** RV-22: database uniqueness/transaction races, preview/confirm stalehash, mentor multi-group và handover authorization tests. **Trạng thái: NOT RUN.**

### R23 — Rubric formula, version và sửa điểm có lịch sử

**Vị trí:** Bổ sung FR-20/TC-12, evaluation schema §23.2. **Mức:** Must. **Owner:** T3. **Phụ thuộc:** T2/T1 delegated publish. **Truy vết:** G23.

**Nội dung đề xuất đưa vào SRS:** ĐỀ XUẤT: template có version bất biến sau khi dùng; mọi criterion bắt buộc, weight>0 và tổng weight=100, score ∈[0,5]. total100=roundHalfUp(Σ(weight×score/5),2); lưu templateVersion,formulaVersion và snapshot tiêu chí/trọng số. Điểm sao doanh nghiệp không tham gia công thức. DRAFT sửa được bởi mentor đúng assignment; SUBMITTED trả lại chỉ qua action có reason/ủyquyền; PUBLISHED không ghi đè. Sửa điểm đã publish tạo evaluation revision mới theo quyết định school, có audit/lineage và publish lại rõ ràng, không tự công nhận tín chỉ.

**Acceptance Criteria — Given–When–Then:** Given weights40/60,scores5/3; When tính; Then76.00/100. Thiếu criterion,score ngoài khoảng hoặc weights99/101 bị422. Đổi template không đổi phiếu cũ; sửa published không ghiđè bản đã công bố.

**Test có thể thực thi:** RV-23: decimal formula/rounding/validation oracle, immutable template/revision, unauthorized mentor/publish and stale-version tests. **Trạng thái: NOT RUN.**

### R24 — Nguồn school policy và ba điều kiện xem kết quả

**Vị trí:** Bổ sung §20.3–20.4/23.2/22.2 cho MVP-BR-17. **Mức:** Must. **Owner:** T3 policy service; T1 delegation. **Phụ thuộc:** T2 UI/T5 term reference. **Truy vết:** G24.

**Nội dung đề xuất đưa vào SRS:** ĐỀ XUẤT: SchoolResultVisibilityPolicy(schoolOrgId,termId,enabled,version,updatedBy,updatedAt), mặc định false nếu thiếu record. Chỉ SCHOOL_STAFF có school delegation đúng scope sửa policy; enterprise HR chỉ sửa enterprise gradeVisibility, defaultfalse, không thay score hoặc school policy. Student GET mỗi lần yêu cầu ownerCase∧PUBLISHED∧schoolPolicy.enabled∧enterpriseVisibility.enabled cùng account/session/assignment hợp lệ. Publish không tự coi schoolPolicy=true. Fixture demo tạo policytrue có nguồn và permission rõ; trường thu hồi hiệu lực với request tiếp theo. Khai báo capability read/write cho policy/HRtoggle trong registry.

**Acceptance Criteria — Given–When–Then:** Given PUBLISHED+HRon nhưng schoolfalse; When studentGET; Then không trả điểm. Schooltrue+HRon trả đúng điểm bản thân; missingpolicy hoặc schoolrevoke deny; staff trường B/HRkhông được sửa policy A haytotal.

**Test có thể thực thi:** RV-24: full boolean truth table+scope/version/revocation+JSONno-draft E2E, including teacher access theo delegation riêng. **Trạng thái: NOT RUN.**

### R25 — Báo cáo tuần có khóa nghiệp vụ và lịch sử nộp

**Vị trí:** Bổ sung FR-18/19, report schema và TC-11. **Mức:** Must. **Owner:** T3. **Phụ thuộc:** T2/T1 files. **Truy vết:** G25.

**Nội dung đề xuất đưa vào SRS:** ĐỀ XUẤT: báo cáo theo caseId+weekStart, weekStart là thứ Hai theo timezone cấu hình cohort (demo Asia/Ho_Chi_Minh), UNIQUE(caseId,weekStart). Tuần cắt theo khoảng hiệu lực case khi cần; timestamps sự kiện vẫn UTC. Mỗi revision đi DRAFT→SUBMITTED→REVIEWED; DRAFT sửa được bằng version, SUBMITTED/REVIEWED không ghiđè. Mentor được phân công hoặc trường được ủy quyền có thể yêu cầu correction kèm reason, sinh revision DRAFT mới dưới cùng reportKey và giữ bản cũ. Deadline từ cohort/task policy, demo cho nộp muộn có late flag, không dựa giờ client. Ghi fileId phải CLEAN và đúng owner.

**Acceptance Criteria — Given–When–Then:** Given hai request cùng case/week; When submit cùng key; Then cùng report/revision, không double; khác payload/key gây conflict theo version. Client ở múi giờ khác vẫn cùng weekStart; v1 REVIEWED vẫn còn sau correctionv2; mentor sai group bịdeny.

**Test có thể thực thi:** RV-25: ISO week/year boundary, timezone fake Clock, unique/revision/replay và report review authorization tests. **Trạng thái: NOT RUN.**

### R26 — Điều kiện đóng hoặc chấm dứt sớm case

**Vị trí:** Bổ sung FR-27/close API/§19.5 và CaseClosed contract. **Mức:** Must. **Owner:** T3. **Phụ thuộc:** T5/T1 delegated school authority. **Truy vết:** G26.

**Nội dung đề xuất đưa vào SRS:** ĐỀ XUẤT DEMO: chỉ SCHOOL_STAFF được ủy quyền đúng school/term đóng case bằng caseId,expectedVersion,decision=NORMAL_COMPLETION|EARLY_TERMINATION,reasonCode,decisionRef. NormalCompletion yêu cầu đã tới endDate, mọi task đãgiao COMPLETED, báo cáo tới tuần cuối REVIEWED và evaluation hiện hành PUBLISHED. EarlyTermination không đòi đủ task/điểm nhưng cần lý do không rỗng và quyết định school có thẩm quyền. Mutation+history+audit+CaseClosed outbox atomic; khóa các ghi task/submission mới, giữ lịch sử và quyền đọc hợp lệ. T3 không sửa Placement; T5 nhận outcome và tự quyết định transition tương ứng bằng contract/version. Không có tự động công nhận tín chỉ hoặc quyết định tuyển dụng.

**Acceptance Criteria — Given–When–Then:** Given student/HR hoặc staff trường khác; When close; Then403/404. Given case chưađủ normalgate; Then422; school EarlyTermination có reason hợp lệ được ghi history, không xóa task và không tự patch Placement. Duplicateclose chỉ một outcome.

**Test có thể thực thi:** RV-26: complete/early/invalidscope/staleversion truth table, before/after endDate, transactional rollback và T3→T5 outcome/reconcile. **Trạng thái: NOT RUN.**

### R27 — Contract pack, error envelope và compatibility endpoint

**Vị trí:** Hoàn thiện §22/13.4, các DTO/APIs và bảng mapping. **Mức:** Must. **Owner:** T1 common conventions; từng team công bố API của mình. **Phụ thuộc:** T1–T5/QA/Frontend. **Truy vết:** G27.

**Nội dung đề xuất đưa vào SRS:** Mỗi target có OpenAPI/JSON Schema hoặc Java public port DTO versioned: type/required/nullability/max/pattern/enum, request/response, sort/filter/page allowlist, permission/state/idempotency/error và maintainer. ĐỀ XUẤT giữ default page 25 ở /api/v1 để tương thích core đang dùng; size 1–100,page0..10000; thay mục target20 bằng change request rõ. Error canonical gồm errorCode,message,requestId,fieldErrors; giữ legacy error field của APIcũ trong giai đoạn compatibility, body requestId=header. API business mới:400syntax,422business,409stale/conflict,401session,403role/CSRF,404cross-record concealment,429rate. Các exception status của authcũ ghi mapping exact thay vì đổi ngầm. Filter/controller/service chung envelope. SYS-NFR-05 nghĩa 100% guard áp dụng theo endpoint: public login/forgot không đòi role/org đã xác thực, nhưng vẫn cần CSRF/validation/anti-abuse theo contract. Sửa sample CT-06 có traceId và grant schema có consentVersion/school/term nếu đãchọn R17; source URLs vs targetaliases phải có một đường canonical.

**Acceptance Criteria — Given–When–Then:** Given request qua filter,controller,service cho đủ nhóm lỗi; When client parse; Then cùng schema/an toàn, requestId khớp header, legacy client vẫn đọc được. Default25 và sort stable được test. Schema validation bắt thiếu field traceId và invalidenum/null.

**Test có thể thực thi:** RV-27: OpenAPI lint/schema examples+contract tests, compatibility consumer test, pagination/filter/sort/security error envelope tests. **Trạng thái: NOT RUN.**

### R28 — Audit/Event Registry thống nhất với code hiện hữu

**Vị trí:** Thay bảng T69 bằng registry exact + mapping source. **Mức:** Must. **Owner:** T1 registry; T2–T5 producer xác nhận. **Phụ thuộc:** QA. **Truy vết:** G28.

**Nội dung đề xuất đưa vào SRS:** ĐỀ XUẤT giữ actionLOGIN với resultSUCCESS/FAILURE; mappingLOGIN_SUCCESS/FAILURE chỉ là alias nhập có kiểm soát. Membership dùng MEMBERSHIP_GRANTED/REVOKED; không nhận MEMBERSHIP_CHANGED nếu thiếu changeType. GradeSubmitted/Published map cónghĩa sang EVALUATION_SUBMITTED/PUBLISHED. Event nghiệp vụ chưa có phải đăng ký riêng trước khi producer gọi; không map MATCH_RUN_REQUESTED thành một hành vi đọc và không gộp onboarding inviteT1 với internship invitationT5 chỉ vì cùng tên. Registry ghi action,result,resourceType,metadata allowlist,eventVersion/owner. Audit SUCCESS cùng transaction; failure không được nuốt; denial độc lập vẫndeny khi loghỏng. Không rawsession/token/IP/PII dư nếu chưa có policy minimization rõ.

**Acceptance Criteria — Given–When–Then:** Given từng action của registry được duyệt; When ghi trong businessTX; Then được chấp nhận và queryfilter đúng. Unknownaction từchối và rollback; log lịch sửLOGIN/MEMBERSHIP cũ vẫn đọc. Hai loại invitation được phân biệt resource/action.

**Test có thể thực thi:** RV-28: exhaustive registry/schema+compatibility alias tests, fail-close rollback, denied-audit failure và history/filter tests. **Trạng thái: NOT RUN.**

### R29 — RTM từng Must và evidence không lẫn mock

**Vị trí:** Bổ sung chi tiết §17/25.1 và gói evidence §24.6. **Mức:** Must trước nghiệm thu. **Owner:** QA Lead. **Phụ thuộc:** T1–T5. **Truy vết:** G29.

**Nội dung đề xuất đưa vào SRS:** Mỗi Must có hàng requirementId+effectiveVersion+owner, rule/AC, endpoint/service, implementation status, positive/negative/boundary/concurrency testcase, fixture/seed/commit/environment, executionId/date và kếtquả PASS/FAIL/NOT_RUN/UNVERIFIED kèm link raw report. Tách evidence mock trong phát triển với E2Ebackend thật. VớiMVP-BR/CT/SYS-NFR/INT/POL mới, test T1 lịch sử không tự đánh PASS. Trace chỉ một phần phải ghi đúng phần đó, không dùng group-level mapping làm100%coverage.

**Acceptance Criteria — Given–When–Then:** Given một Must chưa có test run hoặc chỉ có mock; When QA tổng hợp nghiệm thu; Then NOT_RUN/UNVERIFIED, gate không PASS. Có test mới đủ oracle thì evidence truy được commit/fixture/requestDBlog tương ứng.

**Test có thể thực thi:** RV-29: kiểm tra RTM không orphan/duplicateID, link evidence hợp lệ, sample audit 1positive+1negative+1race per luồng nhạy cảm. **Trạng thái: NOT RUN.**

### R30 — Dọn dữ liệu demo bảo toàn FK và audit

**Vị trí:** Bổ sung quy trình triển khai POL-07/§19.4, không đổi mốc30/90. **Mức:** Must trước nghiệm thu demo có vận hành retention. **Owner:** T1/Ops điều phối. **Phụ thuộc:** T2–T5 owner dữ liệu/file. **Truy vết:** G30.

**Nội dung đề xuất đưa vào SRS:** ĐỀ XUẤT giữ mốc đã chốt: ngày 30 sau nghiệm thu dọn dữ liệu demo/file, audit tối đa90 trừ hold được ủy quyền. Ban hành purge plan theoowner/FK: thu hồi session/token; xóa/anonymize PII và binary/metadata theoresource; giữ tombstone UUID tối thiểu khi audit/history còn FK, không giữ email/password dùng được. Holdcó owner/reason/expiry vàaudit. Ngày90 dọn/ẩn danh phầnlog được phép rồi xử lý tombstone; backup/exports cũng có retention và access. Không hard-delete graph thiếu thứtự, không dùng purge đểche audit; fixture DEMO tách account vận hành.

**Acceptance Criteria — Given–When–Then:** Given demo acceptedAt và dữ liệu liên kết audit/files; When testclockday31/day91; Then PII/file đúng hạn, không có FK orphan, audit còn trong cửa sổđược bảo vệ, expired hold không giữ vô hạn, backups/exports được theo dõi.

**Test có thể thực thi:** RV-30: disposable MySQL+storage fixture, fake Clock, FK/purge/hold/rollback và backup/restore verification; không chạy trên DB đang dùng. **Trạng thái: NOT RUN.**

### R31 — Benchmark recipe có thể tái lập

**Vị trí:** Bổ sung phụ lục thử nghiệm SYS-NFR-01–04/D6. **Mức:** Must trước kết luận hiệu năng. **Owner:** QA/T4/T1/Ops. **Phụ thuộc:** T3/T5 fixture owners. **Truy vết:** G31.

**Nội dung đề xuất đưa vào SRS:** Giữ target§24.3. ĐỀ XUẤT recipe: manifest commit,Java/MySQL/container versions,4vCPU8GBSSD/resource limits,seed/checksum/index/cardinality,cache state vàrule/criteria/taxonomyVersion. Mỗi matching workload chạy20warmup tổng rồi100 measuredruns tổng, concurrency10; reportend-to-endHTTP và engine/DBtime tách biệt. APIlist/detail định danhendpoint set,300 measuredrequests/endpoint trên50authenticatedsessions,sort/page/filter cốđịnh. p95 dùng nearest-rankceil(.95N), giữ failures/timeouts trong báo cáo, không loại mẫu lỗi để đẹp số; expected5xx=0 trong lượt pass. Cold/warm tách nhãn; lưu latencyraw,SQLcount/EXPLAIN,CPU/memory và trace mẫu. Khácmôi trường phải báorõ, không tựsuy ra cùngSLA.

**Acceptance Criteria — Given–When–Then:** Given cùng manifest/seed/recipe; When rerun; Then có đủ sample/denominator/rawlatencies vàmáy đo được mô tả, có thể tínhlại p95. Chưađo hoặc chỉ nạp được5000rows không được ghi PASS performance.

**Test có thể thực thi:** RV-31: review recipe/math percentile+load script dry validation và thực thi benchmark có evidence trên môi trường được định danh. **Trạng thái: NOT RUN.**

### R32 — Chuẩn hóa tài liệu và trạng thái phê duyệt

**Vị trí:** Ch.1–18 được bảo toàn, §25.4/25.5 và phụ lục trace. **Mức:** Should trước bàn giao v1.2. **Owner:** BA/LeaderT1–T5. **Phụ thuộc:** PM về phạm vi sign-off. **Truy vết:** G32.

**Nội dung đề xuất đưa vào SRS:** Trong bảnv1.2, gắn SUPERSEDED cạnhk3, latencyblank, enum/API nguồn cũ và link quyết định áp dụng; không xin lạik5/consent30/publish đã chốt demo. Khôi phục các câu bị cắt từtài liệu nguồn nếuđược cungcấp, không tự điền số; sửa routeS04 và mãACUS-T4-10 thành ID duy nhất. TEST_ADMIN phải được định danh là fixture account/capability giới hạn trên synthetic data, không là global role thứ năm. Bảng change log/approval nêu rõ review contract/demo checkpoint khác productiondata/UTHpolicygate. Đính kèm hoặc định danhversion/hash cácSRS/docst2ngoài được viện dẫn; giữ audit trail thay vì xóa nguồn truyvết.

**Acceptance Criteria — Given–When–Then:** Given developer tìm k,permission,activePlacement hoặc AC09.1; When đọc index/RTM; Then chỉ một định nghĩa canonical và các đoạn cũ được gắn link thay thế, không có hai AC cùng ID.

**Test có thể thực thi:** RV-32: document link/ID/term/state consistency lint và peer review theo chapter/table; xác minh SRSgốc khi có file thật. **Trạng thái: NOT RUN.**

### R33 — Batch truy vấn membership trong trang quản trị

**Vị trí:** Source AdminReadController.java:40–41; bổ sung query-quality gate. **Mức:** Should; trở thành việc bắt buộc nếu benchmark vi phạm NFR. **Owner:** T1. **Phụ thuộc:** QA. **Truy vết:** G33.

**Nội dung đề xuất đưa vào SRS:** ĐỀ XUẤT kỹthuật: sau khi lấy pageUserIds, query mộtlần activeMemberships WHERE userId IN(:ids), group theo userId rồi mapDTO; tránh collection fetch-join trực tiếp với pageable gây nhân bản/countsai. Đo SQL count không tăng tuyến tính theo pageSize vì membership lookup; auth/policyqueries tính riêng. Giữ scope,DTO,sắp xếp,pagination và không serialize entity nhạy cảm. Đây là tốiưu nguồn hiệncó, không tuyên bố đã đo hoặc NFR đang fail.

**Acceptance Criteria — Given–When–Then:** Given page 25 và100users có nhiều memberships; When list; Then kết quả đúng và membershipbatch tối đa1query/page; không N query per row. Queryplan/latency đượcđo, empty page không phát batch thừa.

**Test có thể thực thi:** RV-33: repository/SQL-count integration và benchmark trước/sau, tenant/DTO regression. **Trạng thái: NOT RUN.**

### R34 — Bảo vệ ranh giới module và dependency

**Vị trí:** Bổ sung development contract §3/20.2, không đổi Modular Monolith. **Mức:** Could. **Owner:** T1/Tech Leads. **Phụ thuộc:** T2–T5. **Truy vết:** G34.

**Nội dung đề xuất đưa vào SRS:** ĐỀ XUẤT định danh public API/DTO/port package của từng module, implementation/internalEntity không được module khácghi trực tiếp. Compositionroot wireadapters, không tạo cycle entity/service T2↔T4↔T5. Dùng testJUnit/dependencycheck của build đểphát hiện import/ghi chéo bịcấm và smoke component/entity/repository scan. Nếu thêm thư viện architecture test phải review/pin version riêng, không nâng framework. Boundary rule có allowlist những core DTO/services được công bố.

**Acceptance Criteria — Given–When–Then:** Given module khác importinternalrepository hoặc cycle trái allowlist; When buildcheck; Then fail có chỉ rõ dependency; port DTO hợp lệ được pass và mọi bean/entity cần thiết được scan.

**Test có thể thực thi:** RV-34: architecture boundary regression và Spring context smoke sau merge từng module. **Trạng thái: NOT RUN.**

## 6. Implementation Readiness Checklist

Dấu [x] chỉ xác nhận hạng mục khảo sát đã có bằng chứng, không có nghĩa cả chức năng đã nghiệm thu. Dấu [ ] là điều kiện cần thực hiện/xác minh. Các test có cleanup/ALTER vẫn chạy trong DB dùng một lần/Testcontainers; không trỏ vào dữ liệu nhóm đang dùng.

### 6.1. T1 — Core Platform

**Trước bắt đầu phần mở rộng**

- [x] Xác nhận Java21/SpringBoot4.1.2, SpringSecurity/SessionJDBC, MySQL8.4, FlywayV1–V7, React/TypeScript và core hiện hữu; không tạo project thay thế.
- [x] Xác nhận deny-by-default, boolean visibility fallbackDENY và files metadata-only; giữ ranh giới các phần chưa triển khai.
- [ ] Chốt org/permission/event/error compatibility pack R05–R07,R27–R28, kể cả policy/actor delegation.
- [ ] Chốt File Contract/R15–R16, scope resource authorizer và typed access decision/R17.

**Trước tích hợp/nhận nghiệm thu phần T1**

- [ ] Migration mới được review; FK/UNIQUE/type/enum mapping và MigrationUpgradeIT cập nhật có chủ ý.
- [ ] Guard HR/Mentor/SchoolDelegation và MentorInvitationPort tích hợp thật; không cấp globalADMIN để chạy luồng HR.
- [ ] File upload/scan/download thật; proxy/front/API cùng limits; không mock CLEAN.
- [ ] Chạy lại auth/session fixation/CSRF/logout/lockout/reset/revoke/last-admin/rollback cùng mọi regression contract mới.
- [ ] INT-01,05,07,13,14,16 và RV-05–07,15–20,27–30 có evidence ở phạm vi T1.

### 6.2. T2 — Student

**Trước bắt đầu hành vi đầy đủ**

- [ ] Chốt schema profile/skills/projects/certificates/CV, major/catalog owner và sáu predicate R10–R13.
- [ ] Chốt StudentTermEligibilityPort/R02, không tự xác nhận học vụ từ completeness hoặc MATCH.
- [ ] Chốt Opportunity/Preference/R14, T5 InvitationDecision/expiry và consent lineage/R08,R17.
- [ ] Nhận Task/Calendar/Report/Evaluation DTO T3 và File Contract thật từ T1; giữ userId/profileId khác nhau.

**Trước tích hợp/nhận nghiệm thu**

- [ ] Profile owner/current principal và optimistic version; không ghi bảng T3/T5 hoặc tạo identity thứ hai.
- [ ] UI invitation acceptance/consent là hai quyết định; no preselected consent; fields/purpose/expiry rõ.
- [ ] Field projection và revoke đọc mới thật; file CLEAN/authorized; snapshot/major change không mất dữ liệu.
- [ ] UI dùng cookie/CSRF và loading/empty/error/409; mock transport được cách ly khỏi kịch bản nghiệm thu.
- [ ] INT-01,02,06,07,11–13/POL-01,02,05,06 và RV-02,08,10,12–17,21,24–25 có evidence.

### 6.3. T3 — Internship Workspace

**Trước bắt đầu luồng workspace thật**

- [ ] G01/G02 đóng bằng state/active-set/term/eligibility và PlacementPort/CT-06; T3 không tự tạo Placement.
- [ ] Chốt HR/mentor assignment, invite existing/new user, revoke/handover R07,R20,R22.
- [ ] Chốt task/late/version/R21, report tuần/R25, rubric oracle/R23, school policy/R24, closeCase/R26.

**Trước tích hợp/nhận nghiệm thu**

- [ ] Inbox+case+audit atomic và dedup eventId/placementId; fake/stale/cancel/future/out-of-order được xử lý theo R18.
- [ ] Import40 Placement hợp lệ→4group×10; preview/confirm stale và haiHR move/group race không tạo membership sai.
- [ ] Task assignment self-only; v2 không xóa v1; mentor không review version cũ hay group không được giao.
- [ ] PUBLISHED∧schoolPolicy∧HRenabled được kiểm ở backend; HR không sửa điểm, Mentor không publish.
- [ ] Tất cả FR-01–27 có positive/negative oracle/trace, TC-01–20 và INT-09–13/POL-06/07 có evidence; không chỉ hai mươi test mẫu để tuyên bố bao phủ mọi tổ hợp.

### 6.4. T4 — Matching & Data Engine

**Trước bắt đầu hành vi đầy đủ**

- [x] Baseline đã chọn Strict, alias exact, FAIL>UNKNOWN, Preferred không cứu Required, k=5 thay k=3.
- [ ] Chốt CT-01/02 typed/null/empty/date semantics và snapshot VALID/R04,R10; phân biệt học vụ với matching.
- [ ] Chốt scoringVersion/formula/weights/noPreferred/tie/R11, completeness/catalog policy/R12.
- [ ] Chốt R03 trước bất kỳ enterprise aggregate/breakdown/export nào trong bản tích hợp công khai cho doanh nghiệp.

**Trước tích hợp/nhận nghiệm thu**

- [ ] S1–S11 chạy Strict thật, S9 NOT_MATCH; thêm boundary cases typed/empty/calendar/alias/deprecated version.
- [ ] School detail đúng school/purpose; không PII/ID cá nhân ra enterprise summary, errors, chart hoặc export.
- [ ] Privacy release/query ledger chống actor/demand alias và repeated differencing; không chỉ ẩn ô1–4.
- [ ] Snapshot hoàn thành bất biến, rerun versioned; nomination recheck T5 capacity/eligibility.
- [ ] Seed400SV/10DN và load5000/100DN có manifest/UUID/FK thống nhất; benchmark SYS-NFR-01–04 thực thi, báo số đo thay vì suy ra từ volume.
- [ ] INT-02–05,08,15/POL-03,08 và RV-03–04,10–12,29,31 có evidence.

### 6.5. T5 — Enterprise & School

**Trước bắt đầu quyết định nghiệp vụ đầy đủ**

- [ ] Chốt operationalOrg/schoolVerification state mapping/R05; vòng bổ sung khác vòng xin lại sau reject.
- [ ] Chốt TermReference/StudentTermEligibility/R02 và Placement active-set/create/approve/activation/R01.
- [ ] Chốt proposal reservation/R09, invitation TTL/decision/R08, preference/opportunity/R14, consent race/R17.
- [ ] Chốt canonical service/DTO/port và event lifecycle R18–R19; một owner migration/state, không có invitation canonical thứ hai ở T2.

**Trước tích hợp/nhận nghiệm thu**

- [ ] OneCooperation/Demand, oneBatch/Cooperation, submission snapshots/history và idempotency có DB guard.
- [ ] Verification, Demand/Cooperation/Proposal/Placement đều kiểm owner/org/school/assigned staff, không tin actor/org payload.
- [ ] Request20 đồng thời không duplicate active Placement/student/term, không overbook; reserve→place không double count.
- [ ] Consent/share current version và school eligibility recheck tại approval; không tự tạo acceptance/consent thay sinh viên.
- [ ] Approved/Activated/Cancelled/Completed outbox và case outcome/reconciliation chạy thật, không sửa bảng T3 trực tiếp.
- [ ] T5-01..45 được cập nhật theo enum/policy canonical và có RTM; INT-06–09,14,16/POL-01/02/04/05/07 có evidence.

### 6.6. Checklist chung trước merge/release Demo/MVP

- [ ] Mỗi contract có producer, consumer, maintainer, version, fixture và sign-off của Leader owner/consumer; bản SRS và OpenAPI thống nhất.
- [ ] Dữ liệu demo synthetic cùng UUID/term/skill catalogs; script seed có version/checksum/idempotence. DB local từng người không mặc nhiên đồng bộ dữ liệu runtime.
- [ ] Chạy môi trường tích hợp một Modular Monolith/schema chung theo baseline; DB developer/test vẫn tách riêng. Không đưa yêu cầu replication hai chiều DB cá nhân vào MVP nếu không có change request.
- [ ] Demo/integration dùng Mailpit, không gửi cho người thật; HTTPS/session/CSRF qua proxy đúng, private file scan/storage và health/logging kiểm thử thật.
- [ ] Build/test/migrate clean/upgrade, restore rehearsal, authorization/IDOR/consent/file/privacy/concurrency/E2E và benchmark có report gắn commit; không nhầm historical PASS với lần chạy này.
- [ ] Chấp thuận demo/contract không được mô tả là UTH đã phê duyệt học vụ hoặc production; production POL-01–09 là cổng riêng.

## 7. Kế hoạch hành động ưu tiên

| Thứ tự | Công việc | Owner/phụ thuộc | Bằng chứng để đóng | Có thể song song |
|---|---|---|---|---|
| P0-A | Chốt G01 active-set, create/approve, unique key và thời điểm/handoff T3; chốt G02 term/student-school/eligibility authoritative. | T5+T2+T3, T1 review lock/FK | Bảng state/transition, canonical Term/Eligibility DTO, counterexamples/race oracle RV-01/02 được các Leader ký nhận. | T1 core regression planning, UI shell, taxonomy/strict classifier thuần có oracle. Chưa thực hiện Placement/handoff thật dựa trên suy đoán. |
| P0-B | Chốt G03 privacy release/suppression/query policy, scope/window/cache/export và oracle. | T4+T1, T5 consumer/QA | Protected response schema và adversarial fixtures đối chiếu được; quyết định fallback/feature restrictions rõ. | Song song P0-A; chưa bật enterprise aggregate cho đến khi policy và tests đạt. |
| P1-A | Registry org/permission/audit/error và full typed CT-01–06/API pack; HR/delegation/mentor invite. | T1 điều phối; T2–T5 owner | Canonical/legacy map, grant matrix, OpenAPI/DTO/schema, compatibility/negative contract tests. | Từng owner soạn port/fixture; xử lý vòng T2↔T4↔T5 bằng interface và composition root. |
| P1-B | Completeness/catalog/profile-version; opportunity/preference/invitation; reservation/consent race. | T2/T4/T5/T1 | R08–R14,R17,R19 được chốt, schema/index/lock order và test fixture cùng ý nghĩa. | T2 profile cùng T4 deterministic engine; T5 history/CRUD sau schema; chưa approve từ mock. |
| P1-C | FileService/storage/scan/authorization thật và consumer transport. | T1, T2/T3/T5/Frontend | R15–16; scanner fixture, no-public/raw URL, full upload/download và revoke tests. | Bắt đầu sớm với contract đã thống nhất, song song business; đây là dependency trọng yếu cho CV/submission/verification. |
| P1-D | Task/group/report/rubric/visibility/close, outbox/inbox và lifecycle reconciliation. | T3/T5/T2/T1 | R18,R20–R26; MySQL uniqueness/version/fault injection; no fake Placement. | T3 immutable storage và policy implementation sau P0/contract; test adapter trong phát triển được phép nhưng không nghiệm thu mock. |
| P2-A | RTM từng Must, document cleanup, physical dictionary/index/review và migration registry. | QA/BA/T1–T5 | RTM không orphan, mapping versioned, source-vs-target minh bạch; migrate clean/upgrade giữ V1–V7. | Chạy xuyên suốt các giai đoạn, không dồn cuối. |
| P2-B | Seed/demo/staging profile; retention/backup/restore/HTTPS; benchmark và N+1 đo/khắc phục nếu cần. | Ops/QA/T1/T4 | Manifest, restore proof, raw timings/query counts, RV-29–33; không dữ liệu thật. | Song song từ khi schema/fixture ổn; không benchmark trên dữ liệu/branch đang thay đổi. |
| P3 | Boundary rules/architecture tests và cải tiến tài liệu. | Tech Leads | Dependency check/API package conventions, context scan smoke. | Không cản phần độc lập nếu boundary được review thủ công có owner. |

**Nhịp bàn giao theo phụ thuộc:** nhận contract đã ký → merge migration/seed thống nhất → chạy INT-01/02 (identity/profile/taxonomy) → INT-03–08 (matching/privacy/cooperation/invitation/consent) → INT-09–13 (Placement/workspace/file/grade) → INT-14–16 và POL-01–08/load/restore → sign-off demo có evidence. Không gán lịch ngày hoặc cam kết nhân lực khi chưa có kế hoạch nguồn lực. Một failed P0/negative security test giữ cổng tương ứng đóng; không sửa điều kiện test hoặc tắt guard để pass.

## 8. Kết luận cuối cùng

### 8.1. Ba quyết định bắt buộc

| Câu hỏi | Trạng thái | Phạm vi và điều kiện |
|---|---|---|
| Câu1: SRS v1.1 đã đủ để cả5team bắt đầu lập trình chưa? | **CONDITIONAL GO** | Được bắt đầu những phần độc lập và rõ oracle trong §3/6; tái sử dụng T1, khung module/DTO, UI và classifier thuần. Trước triển khai hành vi Placement/eligibility/enterprise privacy phải đóng G01–G03; từng contract P1 phải chốt trước module/luồng tương ứng. Không phải GO trọn năm phân hệ. |
| Câu2: Đủ để Codex lập trình từng module hoàn chỉnh mà không tự suy diễn nghiệp vụ quan trọng? | **NO GO** | Chưa: active-set/eligibility authority/release oracle, completeness, scoring, reservation, delegation, task/report/rubric/grade policy và các DTO còn cần lựa chọn. Codex có thể code một nhiệm vụ đã đóng contract riêng, không được tự chọn những điểm này cho toàn module. |
| Câu3: Đủ làm cơ sở kiểm thử và nghiệm thu toàn bộ Demo/MVP? | **NO GO** | SRS chưa đủ làm oracle nghiệm thu toàn bộ vì RTM chưa tới từng Must và còn các state/contract/expected result quan trọng chưa quyết định. Việc chưa có evidence chạy toàn luồng là một điều kiện nghiệm thu phần mềm riêng, không phải lý do tự thân để hạ chất lượng SRS. |

**Quyết định cuối cùng: NO GO — UT.BRIDORA SRS V1.1 cho việc giao toàn bộ Demo/MVP như một đặc tả có thể tự chủ lập trình và nghiệm thu ngay.** Quyết định này không hủy các lựa chọn demo đã được tài liệu chốt, không cấm phát triển các phần độc lập, và không khẳng định có lỗi bảo mật đang bị khai thác trong source T1.

### 8.2. Điều kiện cụ thể để chuyển sang GO

**Đối với baseline lập trình v1.2:**

1. Đóng G01 bằng bảng Placement/case state, active-set, API create/approve, effectiveAt và unique/race/handoff oracle duy nhất; T5/T3/T1 cùng ký.
2. Đóng G02 bằng canonical term registry và student–school–term/eligibility có owner, version, fixture và port T2/T5/T3 thống nhất; không yêu cầu thêm phê duyệt học vụ thật cho demo synthetic.
3. Đóng G03 bằng release/suppression/query/filter/export policy và threat model/test oracle; enterprise aggregate/breakdown chỉ mở sau validation. Ghi rõ phạm vi không bảo đảm ẩn danh tuyệt đối.
4. Chốt các khoảng trống P1 G04–G28 bằng contract/decision text và GWT đã review, gồm permission/audit/org/error mapping, typed schemas, HR/school/mentor delegation, file/consent, capacity, invitation, scoring/completeness, task/group/report/rubric/visibility/close và outbox/idempotency. R01–R28 trong báo cáo là đề xuất để review, không tự trở thành baseline.
5. Công bố contract pack có version/hash, owner/consumer sign-off, physical data mapping/FK/UNIQUE/index và một Flyway version registry. Cập nhật danh mục Must/RTM và các đoạn legacy superseded để không giao hai oracle khác nhau.

**Đối với GO nghiệm thu phần mềm Demo/MVP:**

6. Thực hiện code/adapter/DB/file/backend thật còn thiếu I02–I10; các module đã merge cùng revision, không dựa mock để chứng minh Must.
7. Chạy lại build/unit/integration/API authorization/migration/E2E, INT-01–16, POL-TC-01–08, các RV áp dụng và load recipe; mọi PASS có raw evidence/commit/fixture/environment. T3 TC-01–20/T4S1–S11/T5TC được mapping vào baseline mới, không chỉ copy expected result cũ.
8. Đóng các P2 trước nghiệm thu trong phạm vi áp dụng: RTM/evidence, retention/file/backup/restore/HTTPS/profile Mailpit, benchmark manifest và lỗi tài liệu; N+1 có số đo và xử lý nếu vi phạm NFR. Không cần ép P3 thành blocker nếu rủi ro được review/chấp thuận rõ.
9. Leader owner/consumer và QA ký nhận demo thực tế theo scope. **GO demo không là GO production**: POL-01–09 và quyền dùng dữ liệu thật/UTH/điểm học vụ vẫn phải được chủ thể có thẩm quyền phê duyệt riêng tại §25.3.

### 8.3. Index nguồn mã và bằng chứng để tái kiểm định

Các tên file rút gọn trong bảng G được hiểu theo đường dẫn dưới đây; locator dòng tính trên HEAD đã ghi ở §1.1.

| Nhóm | Đường dẫn source/bằng chứng |
|---|---|
| Runtime/scan/security | src/main/java/vn/utbridora/auth/UtBridoraApplication.java; security/SecurityConfig.java; security/AccountGuardFilter.java trong cùng rootJava. |
| Role/org/permission | src/main/java/vn/utbridora/auth/organization/Organization.java:10; authorization/AuthorizationPolicy.java:37; authorization/PermissionCatalog.java:13; authorization/EffectivePermissionService.java:19. |
| Scoped mutation/core | src/main/java/vn/utbridora/auth/authorization/AdministrationGuard.java:21; authorization/MembershipService.java:22; core/AccountLifecycleService.java:29. |
| Auth/invitation | src/main/java/vn/utbridora/auth/api/AuthController.java:54; core/AccountService.java:24; tokens/AccountTokenService.java:24; tokens/TokenRepository.java. |
| Profile integration | src/main/java/vn/utbridora/auth/authorization/ProfileVisibilityPort.java:7; ProfileVisibilityConfiguration.java; DenyAllProfileVisibility.java trong authorization. |
| File | src/main/java/vn/utbridora/auth/files/FileMetadata.java:7; FileMetadataRepository.java. Không tìm thấy FileController/StoragePort/scan implementation. |
| Audit | src/main/java/vn/utbridora/auth/audit/AuditAction.java:3; AuditService.java:31; AuditLog.java; RequestIdFilter.java. |
| API/error/query | src/main/java/vn/utbridora/auth/api/ApiErrorHandler.java:11; AdminReadController.java:30,40; AdminController.java; docs/openapi.json. |
| Database | src/main/resources/application.yml; src/main/resources/db/migration/V1__foundation.sql đến V7__administration_lock_and_query_indexes.sql, đặc biệt V2sessionFK/V3permissions/V5files/V7mutex-index. |
| UI/transport/infra | web-demo/src/api.ts:39; web-demo/src/App.tsx; web-demo/src/management.tsx; web-demo/nginx.conf:7; compose.local.yaml; compose.staging.yaml; scripts/backup-staging.ps1. |
| Contract/docs | docs/contracts/permission_matrix.csv; docs/TEAM_IMPLEMENTATION_GUIDE.md; docs/IMPLEMENTATION_STATUS.md; docs/TEST_REPORT.md; docs/DEPLOYMENT_GUIDE.md. Đây là tài liệu source snapshot, nội dung cũ không tự thay v1.1. |
| Test source | src/test/java/vn/utbridora/auth/CorePlatformIT.java; MigrationUpgradeIT.java:21; src/test/java/vn/utbridora/auth/security/*; web-demo/tests/*.spec.ts; .github/workflows/verify.yml. |
| Test evidence lịch sử | docs/PREPUSH_TEST_EVIDENCE.json; docs/TEST_EVIDENCE.json; target/surefire-reports/TEST-*.xml; target/failsafe-reports/TEST-*.xml; web-demo/test-results/e2e.xml. Không dùng timestampmetadata thiếu/nhãn PASS củaSRS để tạo kếtquả mới. |

**Giới hạn còn UNVERIFIED:** bản SRS gốc độc lập T1–T5 và docs/t2/00–08; source/nhánh của các team chưa cung cấp; runtime DB/Flyway history của môi trường đích; staging HTTPS thực, scanner/storage thật, benchmark/load/privacy/race/restore theo v1.1 và run CI mới nhất. Các phần này đã được đưa vào điều kiện/test cần thực hiện, không bị che bằng điểm readiness hoặc test T1 cũ.

Báo cáo chỉ là nhận định kiểm định và đề xuất chuẩn hóa. SRS gốc, source, migration và mọi database được giữ nguyên trong đợt audit.

