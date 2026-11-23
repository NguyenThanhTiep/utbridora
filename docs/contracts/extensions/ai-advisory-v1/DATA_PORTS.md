# Dữ liệu và owner ports cho advisory v1

## Dữ liệu hiện có trong source

| Nguồn | Nội dung hiện có | Cách sử dụng v1 |
|---|---|---|
| T2 `StudentProfileSnapshotPort.ProfileSnapshot` | ID/version/relevantHash, major, canonical skills/source, availability và catalog versions | Giữ nguyên làm strict refs; relevantHash không chứng minh văn bản dự án còn hiện hành |
| T2 `StudentProfileDtos.SelfSkillView`, `CustomSkillView`, `Project` | Skill note, custom name/category/note; project title/description/role/skillCodes | Capture qua port T2 mới, giữ SELF_DECLARED và version; không thêm vào canonical DTO |
| T2 `StudentProfileProjectionPort` + T1 `ProfileAccessFacade` | Projection kiểm quyền theo requestedFields/purpose hiện hành | Kế thừa cách kiểm quyền; bổ sung purpose mới, không đọc repository T2 từ T4 |
| T5 `DemandInput`/`DemandRepository` | Title, description tối đa 10.000 ký tự, demand version và immutable revision | Port T5 mới cung cấp đúng revision cùng run; không đọc bảng demands/revisions trực tiếp từ T4 |
| T5 `DemandCriteriaPort` | CriteriaHash/versions, scope trường/kỳ, catalogs | Giữ làm ràng buộc strict; không suy ra JD từ criteriaHash |

Đường dẫn bắt đầu tại `src/main/java/vn/utbridora/auth/`: `modules/student/{port,api,application,persistence}`, `modules/cooperation/{integration,domain,persistence}`, `authorization/ProfileAccessFacade.java`. Các bản ghi trên là khảo sát code, không phải port mới đã tồn tại.

## Quyền dùng nội dung

Purpose mới **SCHOOL_AI_ADVISORY** là xử lý nội bộ cho nhà trường, dùng scope school/term/demand và field set cố định `skills,projects`. T1 kiểm SCHOOL_STAFF hiện hành, tổ chức VERIFIED, securityVersion, exact delegation/ancestry và các permission trong [permissions.csv](permissions.csv). T2 gọi quyết định T1 mới trong transaction capture và mỗi lần kiểm current. Không tái sử dụng một allow flag từ browser, token consent doanh nghiệp hoặc SYSTEM bypass.

Initiator và reader phải đang được đọc **cả hai** trường skills và projects, kể cả khi trường đó rỗng. Có quyền xem strict MatchResult không tự được xem score suy ra từ nội dung bị cấm. School review nội bộ không tạo enterprise consent/share; doanh nghiệp chỉ đọc qua luồng chia sẻ hiện hành. Nếu field mask bị thu hẹp, chặn cả score/rank lẫn trích đoạn; không chỉ ẩn đoạn văn.

## Hợp đồng T2 đề xuất

Port mới `StudentSemanticSnapshotPort`, đặt trong owner T2 và triển khai ở lượt 3:

```text
capture(actor, commandKey, schoolOrgId, termId, datasetSnapshotId,
        refs: List<DecisionProfileRef>, purpose=SCHOOL_AI_ADVISORY)
  -> SemanticSet {setId, sourceSetHash, allowedFields, capturedAt, validUntil, count}
readCapturedPage(serverJobContext, setId, afterStudentId, size[1..100])
  -> {items: List<SemanticProfile>, nextStudentId|null}
assertCurrent(actorOrValidatedJobContext, setId) -> Current | SourceStale | AccessRevoked

SemanticProfile {
 studentUserId, profileSnapshotId, profileVersion, relevantHash,
 semanticSnapshotId, semanticSourceHash, sourceStatus,
 skills: [{sourceId, kind, codeOrName, categoryCode|null, level, yearsPractice, note, source}],
 projects: [{sourceId, title, description, role, skillCodes, source=SELF_DECLARED}]
}
```

Actor/serverJobContext không bind từ request công khai. Background context chứa subject/securityVersion/exact delegation stamps/purpose/scope, không giữ Authentication sống; T1 dựng lại và kiểm quyền trước mỗi owner operation. `validUntil` là min của giới hạn job và thời hạn quyền/policy; không null.

Capture chỉ nhận refs MATCH do T4 dựng từ run đã lưu; đối chiếu đúng student/snapshot/school/term và relevantHash. Quan trọng: **current full profileVersion phải bằng profileVersion trong canonical ref**, kể cả sửa riêng mô tả dự án không đổi relevantHash. Không thỏa thì 409 AI_ADVISORY_SOURCE_STALE, yêu cầu chạy canonical mới. Lock parent profile theo student UUID tăng dần trong cùng transaction; mọi thay đổi child phải được fence bởi parent version/lock. Không dựa vào nhiều live page có thể thay đổi giữa lúc capture.

Set bất biến có đúng một item cho mỗi MATCH, thứ tự student UUID tăng dần. Mọi trường không có trong allowlist đều bị loại: tên, email, điện thoại, studentCode, certificates, CV/file bytes/ID/URL, project URL/evidence IDs. T2 giữ sourceId để truy vết nội bộ: canonical skill dùng `canonical:<skillCode>`, custom dùng `custom:<customSkillId>`, project dùng projectId; không gửi các ID đó vào Python. Không tạo tên kỹ năng từ alias chưa có trong taxonomy cùng version. Custom skill/project là tự khai; model không biến thành kỹ năng canonical hoặc xác minh bằng chứng. Semantic capture giới hạn 200 skills và 100 projects/item; vượt giới hạn thì trả AI_ADVISORY_LIMIT_EXCEEDED, không cắt danh sách hoặc thay giới hạn nhập hồ sơ canonical.

## Hợp đồng T5 đề xuất

Port mới `DemandSemanticSnapshotPort` trong owner T5:

```text
captureForSchool(actor, commandKey, runRef)
 -> DemandSemanticSnapshot {
   id, demandId, schoolOrgId, termId, demandVersion, criteriaVersion,
   criteriaHash, title, description, semanticSourceHash, capturedAt, validUntil
 }
assertCurrent(actorOrValidatedJobContext, snapshotId) -> Current | SourceStale | AccessRevoked
```

Khóa demand owner, kiểm current school/term/demand, feature/cooperation/source visibility hiện hành; version/criteriaHash phải bằng run. Title/description lấy immutable revision tương ứng, đối chiếu current version trước capture; soft delete/đổi version làm stale. Không dùng note lợi ích, địa chỉ, contact hay free-form khác trong v1. Port mới không tự biến `DemandCriteriaPort.Purpose.NOMINATE` thành đã được hỗ trợ.

## Chuẩn hóa và template enriched-v1

Chọn template **school-advisory-text-v1**, khác canonical-v1. Internal protocol hiện tại chỉ nhận canonical-v1; lượt 3 phải bổ sung literal/template cho Java, Python và internal OpenAPI cùng lúc, giữ canonical-v1 cho shadow. Revision model giữ theo model-lock lượt 1, aggregation token-weighted-mean-v1.

Client/schema Java hiện kiểm `String.length()` theo UTF-16, còn Python kiểm code points. Khi thêm template mới phải thống nhất `codePointCount`/byte limits, kiểm cả emoji và surrogate không hợp lệ; không vô tình đổi bound canonical-v1 đã kiểm chứng. Response phải echo đúng template request, không gắn nhãn enriched lên điểm canonical-v1.

Chuẩn hóa Unicode NFC, CRLF thành LF, mỗi dãy Unicode whitespace thành một dấu cách và trim từng giá trị; giữ dấu tiếng Việt, ký hiệu kỹ thuật và case. Note nullable của nguồn được chuẩn hóa thành chuỗi rỗng. Không dịch, không thêm nội dung suy diễn. Escape giá trị bằng JSON string escaping với Unicode giữ nguyên; labels và thứ tự dòng cố định như sau:

```text
Query:
Internship demand
Title: <escaped normalized title>
Description: <escaped normalized description>

Passage:
Student-declared skills and projects
Skill: <compact JSON object, keys kind,codeOrName,categoryCode,level,yearsPractice,note,source>
Project: <compact JSON object, keys title,description,role,skillCodes,source>
```

Skills canonical trước custom, sort theo code/name rồi sourceId; projects sort theo sourceId; skillCodes sort tăng dần và giữ dạng mã nếu chưa có nhãn cùng version. Giá trị yearsPractice là chuỗi decimal chuẩn không số mũ, bỏ zero thừa; không dùng float để biểu diễn nguồn. Header query/passage của E5 do encoder thêm đúng một lần, không đưa vào persisted template text. V1 không dùng nội dung như chỉ dẫn để gọi tool hoặc làm thay đổi policy.

Ít nhất một project title/description/role không rỗng hoặc một skill note/custom skill name không rỗng mới tính là có nội dung ngữ nghĩa của sinh viên. Chỉ có canonical skill codes thì sourceStatus NO_CONTENT; skill source vẫn theo nguồn đã lưu. JD description trống thì job NO_DATA với reason DEMAND_TEXT_EMPTY. Không lấy title đơn lẻ để quảng bá là phân tích JD.

Mỗi rendered text tối đa 8.192 Unicode code points và 32 KiB UTF-8; source limit lớn hơn không bị sửa lại. Query quá giới hạn: lệnh tạo trả 409 AI_ADVISORY_INPUT_TOO_LARGE. Passage quá giới hạn: lưu item INPUT_TOO_LARGE, không âm thầm cắt theo ký tự. Encoder vẫn giới hạn 512 token/chunk, tối đa 4 chunk; score từ nội dung bị giới hạn token có `limited=true` và UI ghi rõ. Tập capture tối đa 5.000 MATCH và tổng rendered UTF-8 tối đa 32 MiB; quá giới hạn trả 409 AI_ADVISORY_LIMIT_EXCEEDED, rollback toàn bộ lệnh advisory, không ảnh hưởng canonical run.

Batch không chỉ giới hạn 16 passages: Java phải đo **bytes JSON đã serialize**, gồm query lặp lại, escaping và metadata, để mỗi request <=262.144 bytes như client/Python mặc định. Tách batch nhỏ hơn khi cần; không bỏ bớt passage để vừa payload. Giới hạn response 131.072 bytes hiện hành tiếp tục giữ. Nếu một cặp hợp lệ vẫn không serialize trong bound thì fail protocol rõ ràng, không âm thầm tăng cấu hình phía service.

Allowlist loại PII có cấu trúc, không bảo đảm free text không chứa PII người dùng tự gõ. Chỉ đưa trường đã được phép vào dịch vụ tự host nội bộ; không gửi ra nhà cung cấp bên ngoài, log hoặc telemetry. Không tuyên bố đã tự động ẩn danh hoàn toàn.

## Hash và kiểm hiện hành

`semanticSourceHash` là SHA-256 của UTF-8 JSON tuple theo thứ tự cố định gồm schema/purpose/field set, owner versions và các giá trị được capture; encoding compact, Unicode giữ nguyên, không số mũ cho decimal. SetHash tính từ danh sách `[studentId,semanticSourceHash]` theo UUID. `inputHash` = SHA-256 của UTF-8 JSON array `[templateVersion,role,renderedText]`; role query/passage. Hash raw input không trả public và không đưa vào audit thường lệ.

Binding nội bộ gồm run/dataset/profile-set/criteria/catalog versions, T2 semantic set hash, T5 semantic hash, template/model/aggregation/policy. Mỗi read kiểm current toàn set, không chỉ trang sắp trả; một profile thay version làm cả advisory STALE để không lộ thứ hạng suy ra từ tập nguồn đã đổi. Port không sẵn sàng thì fail closed 503, không phục vụ cache cũ. TTL/event invalidation chỉ hỗ trợ; không thay kiểm owner hiện hành.
