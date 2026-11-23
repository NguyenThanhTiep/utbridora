# Chính sách xếp hạng và giao diện advisory v1

Policy ID **semantic-desc-canonical-tie-v1**. Đây là phương án cố định để triển khai/đánh giá, chưa có kết quả chứng minh tốt hơn baseline. Không trộn cosine với fitScore, không ngưỡng phần trăm phù hợp.

## Tập và thứ tự

1. Đọc toàn bộ `overallStatus=MATCH` của đúng school run COMPLETED. Giữ một row/ứng viên, kể cả NO_CONTENT/INPUT_TOO_LARGE. Không gồm NOT_MATCH/MISSING_DATA; không thêm hồ sơ mới vào run cũ.
2. Với item có text hợp lệ, yêu cầu đúng model/revision/template/aggregation, hữu hạn và cosine thuộc [-1,1]. Lỗi metadata, vector, khóa hoặc thiếu response của bất kỳ item cần chấm làm toàn job FAILED; không publish batch thành công riêng.
3. Tạo `scoreKey = roundHalfUp(BigDecimal.valueOf(cosine) * 1000000)`; âm làm tròn ra xa 0 khi đúng nửa. Public semanticScore = scoreKey/1000000. Không sort bằng raw float khác với điểm đã hiển thị.
4. Nhóm SCORED trước, scoreKey giảm dần; hòa điểm sort fitScore canonical giảm dần, tiếp theo studentUserId lowercase UUID tăng dần theo ASCII. Không cộng fitScore vào cosine. Trong nhóm thiếu nội dung/quá lớn, sort fitScore rồi student UUID như canonical.
5. `position` liên tục 1..eligibleCount trên toàn snapshot; `rank=position` chỉ với SCORED. Item còn lại có rank=null, semanticScore=null và scoreStatus NO_CONTENT hoặc INPUT_TOO_LARGE. Null không có nghĩa điểm thấp.

`eligibleCount = scoredCount + noContentCount + inputTooLargeCount`; `coverage = scoredCount/eligibleCount`, bằng 0 khi eligibleCount=0. READY có scoredCount>0; NO_DATA có scoredCount=0. Nếu JD rỗng, không gọi AI: scoredCount=0, toàn bộ row NO_CONTENT với reason DEMAND_TEXT_EMPTY. Không diễn giải quá lớn thành kém năng lực.

READY và NO_DATA đều có danh sách đầy đủ để người dùng không mất ứng viên. NO_DATA dùng thứ tự canonical và UI ghi chưa có xếp hạng AI; không gọi đây là kết quả AI thành công. Các trạng thái job khác không có trang items.

## Ví dụ định hướng

| Ứng viên | Strict | fitScore | Cosine giả định | Kết quả trong advisory |
|---|---|---|---|---|
| A | MATCH | 90.00 | 0.600000 | Sau B |
| B | MATCH | 70.00 | 0.800000 | Trước A |
| C | NOT_MATCH | null | 0.990000 | Không thuộc advisory |
| D | MATCH | 100.00 | null, thiếu nội dung | Sau nhóm SCORED, rank=null |

Ví dụ kiểm policy dùng điểm giả, không phải bằng chứng model chọn B tốt hơn A. Nếu lượng điểm hòa tăng do lượng tử hóa, báo trong evaluation; đổi precision/tie-break cần policy version mới và đo lại.

## Snapshot và phân trang

Job chỉ publish một snapshot bất biến, lưu position bằng transaction có fencing token. GET items nhận page 0-based, size 1..100 (default 25), `page<=10000`; totalElements=eligibleCount và totalPages=ceil(totalElements/size), bằng 0 khi rỗng. Page vượt cuối trả content=[] cùng totals. Không nhận sort/filter để đổi tập/thứ tự trong v1. Client giữ advisoryId qua các trang, không ghép trang từ hai job. Source/quyền thay đổi thì không trả một trang lọc bớt; fail closed theo lifecycle.

## Giao diện nhà trường

Tab/section Gợi ý AI được bật theo server capability và scope; danh sách canonical vẫn có đường truy cập rõ. Hiển thị coverage/scored/eligible, thời điểm, hạn dùng, model/policy/template, scoreStatus và nhãn nội dung bị giới hạn. Score ghi Điểm tương đồng, không phần trăm phù hợp/trúng tuyển. Có NO_DATA/STALE/FAILED/EXPIRED thì hướng dẫn xem canonical hoặc tạo lại, không giữ lựa chọn từ ranking cũ trên màn hình.

V1 không tạo giải thích bằng LLM. Evidence chỉ là trích đoạn **đầu vào thực sự đã được embed**, từ đúng field snapshot được phép, có source SELF_DECLARED/nhãn nguồn đã lưu và cờ limited. Không khẳng định đó là lời giải thích nhân quả cho điểm. Nếu UI chưa có cơ chế map span vào phần text đã embed, v1 không hiển thị trích đoạn; API v1 chỉ trả ref/score/status, không raw text.

Không tự tick top-K hoặc tự nominate. Cán bộ lựa chọn; trước submit UI tải lại nomination-candidates hiện hành để lấy expected demand/criteria/cooperation/batch versions, kiểm selectable, rồi gọi `POST /api/v1/matching/runs/{id}/nominate` như cũ. Endpoint này vẫn tự kiểm quyền/refs/eligibility/quota; không tin rank hay token advisory từ browser. API hiện hành không nhận expectedSemanticVersion: sau thay đổi chỉ văn bản, UI phải refresh advisory, còn request nominate trực tiếp vẫn được xử lý theo guard canonical hiện hành. Không tuyên bố endpoint cũ có guard semantic mới.

UI clear dữ liệu/chọn ứng viên khi đổi session/school/term/demand/run, focus/visibility refresh thấy version/quyền đổi hoặc server từ chối. V1 không thêm log hành vi/feedback cá nhân; pilot thu số liệu thao tác theo evaluation plan và phạm vi được phép.
