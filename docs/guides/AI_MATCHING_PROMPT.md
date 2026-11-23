# Prompt triển khai AI hỗ trợ đề xuất sinh viên

Phiên bản định hướng cập nhật ngày 21/11/2026. Dùng cùng [lộ trình tám giai đoạn và bốn lượt](AI_MATCHING_ROADMAP.md). Khi giao việc, nêu lượt cần thực hiện; không suy ra quyền triển khai/publish từ việc đọc prompt.

Lượt 2 đã có [đặc tả mở rộng ai-advisory-v1](../contracts/extensions/ai-advisory-v1/README.md), làm căn cứ cụ thể cho code lượt 3. Package đặc tả giữ trạng thái lịch sử trước triển khai; khi tiếp tục, đối chiếu quyết định riêng và trạng thái code hiện tại, không quay lại viết đặc tả chung từ đầu.

Người dùng đã tiếp tục giao lượt 3; [code và bằng chứng DEV của advisory](AI_MATCHING_ADVISORY.md) hiện đã có trong worktree, mặc định OFF/chưa deploy. Phần model-quality và kiểm chứng MySQL/full-stack chưa được suy ra từ kết quả unit/protocol/component.

## Mục tiêu sản phẩm

Triển khai AI Matching tự host cho Team 4 UTBridora để hỗ trợ nhà trường lựa chọn sinh viên phù hợp nhu cầu doanh nghiệp. Luồng đích là: **Strict Matching → tập MATCH → AI xếp hạng gợi ý riêng cho nhà trường → cán bộ xem và chọn → đề xuất/duyệt → consent/chia sẻ hiện hành**.

AI phải tham gia thứ tự danh sách gợi ý mới mà cán bộ có thể sử dụng. Shadow semanticScore chỉ là bước xây nền. Không tuyên bố hoàn thành mục tiêu đề xuất chỉ vì dịch vụ trả điểm hoặc protocol test thành công.

## Căn cứ và giới hạn triển khai

1. Đọc AGENTS.md và trạng thái workspace thực tế trước khi sửa. SRS v1.2.4 và Contract Pack v1.1 đã được duyệt ở đúng hash ghi trong AGENTS.md; giữ thứ tự ưu tiên SRS §25.8. Giữ bytes tài liệu đóng băng. Phần API/data/ranking mở rộng phải có đặc tả và quyết định riêng, không sửa âm thầm baseline.
2. Tiếp tục worktree `feature/team4-ai-matching-selfhosted` hiện có; bảo toàn phần code chưa commit và checkpoint lượt 1. Chụp trạng thái trước/sau, giữ source gốc/Plan/config/secret/stash. Không reset/clean/force hoặc apply/drop stash.
3. Không tự commit/push/merge/PR/deploy, rebuild/restart shared Docker, chạy container integration trên daemon dùng chung, migration/seed/repair DB chung. Thay Compose project trên cùng daemon không phải môi trường tách biệt. Kiểm model thật/full regression trên môi trường riêng có đủ tài nguyên.

## Phân biệt kết quả canonical và gợi ý AI

Giữ nguyên strict rules, PASS/FAIL/UNKNOWN, SELF_DECLARED, `overallStatus`, cách tính/round `fitScore`, ranking/tie-break/phân trang canonical, counters và idempotency. Giữ enterprise aggregate/release/fingerprint/privacy budget hiện hành. Không thêm semantic field vào DTO canonical hoặc thay thứ tự endpoint cũ.

Cho phép ranking mới trong **view/API gợi ý school-only có contract mở rộng**. Chỉ xếp các ứng viên `overallStatus=MATCH` trong run/snapshot hợp lệ. AI không đưa NOT_MATCH/MISSING_DATA qua điều kiện bắt buộc, không tự đề xuất/duyệt/chia sẻ hoặc thay consent/quota/Placement. Thao tác đề xuất từ UI AI vẫn gọi quy trình hiện hành và kiểm lại quyền/stale/điều kiện theo guard nghiệp vụ.

## Đầu ra giai đoạn đặc tả

Trước khi code tính năng mới, lập extension cụ thể gồm schema endpoint/request/response/errors, quyền theo trường/kỳ/nhu cầu, audit event, trạng thái job/ranking, idempotency, stale/revoke/retention và phân trang snapshot ổn định. Giữ extension riêng, truy vết baseline đã duyệt và nêu rõ điểm bổ sung.

Chốt policy xếp hạng: tập MATCH đầy đủ, score finite và trạng thái null, tie-break ổn định, xử lý thiếu dữ liệu/lỗi/toàn bộ job vượt giới hạn, model/template/policy version và công tắc bật/tắt. Dùng semantic giảm dần làm phương án ban đầu để đánh giá; không tự coi policy đó đã đạt chất lượng. Không tự đặt công thức trộn fitScore/cosine hay ngưỡng phần trăm phù hợp khi chưa có hiệu chỉnh và kết quả đánh giá.

Đầu ra phải đủ cụ thể để reviewer quyết định phần mở rộng; không hỏi lại quyền duyệt hai tài liệu baseline. Nếu việc triển khai đã được người dùng giao đủ rõ trong phiên, tiếp tục phần được giao và chỉ dừng ở quyết định phụ thuộc chưa được giải quyết.

## Dữ liệu ngữ nghĩa

Tận dụng snapshot canonical đã có cho strict matching. Bổ sung qua owner ports T2/T5 những nội dung được phép như mô tả dự án/kinh nghiệm/kỹ năng và JD/nhiệm vụ; snapshot bất biến có nguồn/version/purpose và quyền. Không tự đọc bảng module khác, CV/file hoặc suy diễn mô tả từ mã không có nguồn.

Giữ nguồn tự khai/xác minh và tình trạng thiếu dữ liệu. Tách hash nội dung ngữ nghĩa với relevantHash của strict matching. Bind kết quả với run/profile/criteria/JD snapshot, taxonomy, template, model revision và ranking policy. Không gửi tên/email/điện thoại/định danh nghiệp vụ hoặc file URL vào Python nếu không cần; dùng khóa ordinal và ánh xạ nội bộ.

Nếu chưa có dữ liệu mô tả phù hợp, báo giới hạn canonical-only và coverage; không quảng bá là phân tích CV/JD đầy đủ. Trích xuất CV chỉ triển khai khi có phạm vi/contract riêng và tuân quét file/quyền truy cập hiện hành.

## AI service và tích hợp backend

Kế thừa FastAPI và `intfloat/multilingual-e5-small` CPU từ lượt 1: revision/checksum lock hiện có, 384 chiều, query/passage prefixes, giới hạn tokenizer 512 token/chunk, aggregation có version, vector/score hữu hạn. Cosine không phải xác suất. Runtime dùng model local/offline, không tải weights hoặc gửi dữ liệu ra dịch vụ ngoài; không commit weights/secret/cache.

Không suy luận trong transaction canonical. Kế thừa after-commit processing nhưng thiết kế cơ chế job bền vững/recovery nếu view người dùng cần tồn tại sau restart; không biến bộ nhớ package-private lượt 1 thành public endpoint. Giới hạn queue/concurrency/payload/chunk/time và số hồ sơ. Cache theo exact input hash/model/template/aggregation, có kiểm quyền/thời hạn và vô hiệu hóa phù hợp.

Strict matching xét toàn bộ population hiện hành trước. Ranking AI cần toàn bộ tập MATCH của snapshot; không semantic top-K trước strict và không sắp độc lập từng trang canonical. Chỉ công bố ranking READY khi đủ kết quả theo policy; không dùng điểm 0 thay lỗi/null. Quá giới hạn hoặc lỗi phải có trạng thái rõ và đường quay về canonical.

Default OFF, tách trạng thái bật shadow và bật view gợi ý trong thiết kế mở rộng. Tắt gợi ý phải khôi phục trải nghiệm canonical; backend canonical không phụ thuộc availability AI. Không ghi raw content/embedding/token/PII vào log, error hoặc telemetry. Cấp quyền đọc kiểm tra tại thời điểm request, không chỉ khi tạo job.

## Giao diện và giải thích

Nhà trường có lựa chọn xem danh sách canonical và gợi ý AI. Hiển thị trạng thái, coverage, phiên bản/thời điểm, dữ liệu thiếu hoặc hết hiệu lực và bằng chứng nguồn được phép. Điểm AI không mang nhãn phần trăm đạt/trúng tuyển. Chỉ nêu lý do có thể truy vết từ nội dung nguồn; E5 không tự sinh lời giải thích có kiểm chứng.

Cho phép cán bộ xem toàn bộ ứng viên đủ điều kiện và tự chọn theo quy trình; không tự tick/top-K đề xuất hàng loạt chỉ vì có thứ hạng. Kiểm quyền và điều kiện nghiệp vụ phía server cho mọi thao tác, kể cả request thủ công và session/quyền đã đổi. Doanh nghiệp chỉ thấy dữ liệu theo luồng chia sẻ được phép.

## Kiểm thử và đánh giá chất lượng

Giữ đủ unit/integration/browser/contract/build phù hợp. Kiểm canonical ON/OFF, thứ tự gợi ý mới, chỉ MATCH, paging toàn cục, thiếu dữ liệu, rollback/replay, crash/restart, stale/revoke, IDOR/cross-school, overload/timeout, flag OFF và đường đề xuất hiện hành. MySQL/ClamAV và browser thật chạy trong môi trường riêng cho test tương ứng.

Chạy E5 thật và kiểm offline có chặn mạng, lưu environment/dependency freeze/model manifest. Test fake encoder chỉ xác nhận logic/giao thức. Không coi giả lập là chất lượng model, offline proof hoặc tải phần cứng thật.

Trước đánh giá chốt bộ nhãn relevance, baseline canonical trên cùng tập MATCH, cách tách tập hiệu chỉnh/kiểm tra, k, NDCG@k/Precision@k, coverage và chỉ số thao tác cán bộ; xác định tiêu chí đạt trước đo. Báo cỡ mẫu, độ bất định, các nhóm hồ sơ ít dữ liệu/ngôn ngữ phù hợp. Chất lượng không đạt thì giữ shadow/OFF và nêu việc cần cải thiện.

## Trình tự và báo cáo mỗi lượt

Thực hiện theo bảng giai đoạn trong roadmap. Lượt 1 giai đoạn 1–4 đã có code; lượt 2 giai đoạn 5; lượt 3 giai đoạn 6; lượt 4 giai đoạn 7–8. Chuẩn bị Docker recipe không đồng nghĩa được chạy trên shared daemon; pilot/merge/deploy chỉ làm trong phạm vi được giao.

Mỗi lượt bàn giao: phần đã làm, file thay đổi, contract/decision liên quan, test PASS/FAIL/SKIP/NOT RUN đúng môi trường, bằng chứng model thật tách mock, gap và bước kế tiếp. Với code chưa commit ghi base HEAD và exact source manifest; khi sửa tài liệu sau kiểm thử, giữ liên kết lịch sử để không gắn bằng chứng cũ cho source mới. Kết quả DEV không tự thay QA/Lead/nhà trường nghiệm thu.
