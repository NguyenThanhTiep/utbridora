# Kế hoạch đánh giá chất lượng advisory v1

Trạng thái dữ liệu/đo hiện tại: **NOT RUN**. Chưa có bộ nhãn nghiệp vụ, E5 thật/offline hoặc máy đo tách biệt. Các ngưỡng dưới đây là đề xuất cụ thể của đặc tả để review **trước** khi xem kết quả quyết định; không phải ngưỡng SRS đã ký hoặc kết quả đạt.

## Bộ dữ liệu và protocol

1. Tối thiểu 10 nhu cầu cho hiệu chỉnh/policy và 30 nhu cầu held-out để quyết định, mỗi nhu cầu quyết định có ít nhất 10 ứng viên MATCH. Thêm edge cases không có MATCH, thiếu JD, chỉ skill codes, hồ sơ quá lớn và timeout; không ép edge cases thành top-10 metric.
2. Hai người hiểu nghiệp vụ chấm độc lập, không thấy model score/thứ tự nào, nhãn 0 không phù hợp, 1 liên quan yếu, 2 phù hợp, 3 rất phù hợp trên **nội dung đã được phép dùng**, cùng tập MATCH. Giải quyết bất đồng và lưu cả nhãn gốc/quyết định; không dùng model tự sinh nhãn làm ground truth. Phải có ít nhất một ứng viên relevance>0 cho nhu cầu được tính NDCG; báo rõ nhu cầu IDCG=0 và tách khỏi macro NDCG, không loại âm thầm.
3. Chia theo demand/cooperation và nhóm mô tả gần trùng; hồ sơ/dự án gần trùng không được làm rò rỉ hiệu chỉnh sang held-out. Đóng băng split, labels, rendered-source hashes, source manifest, model revision, template, precision/tie-break và hardware trước lần đo quyết định. Nếu không đủ dữ liệu độc lập thì báo giới hạn, không chế tạo nhãn để đủ mẫu.
4. Baseline là canonical fitScore giảm dần/student UUID tăng dần trên cùng tập MATCH. Advisory gồm cả unscored ở đuôi theo policy; không chỉ đo subset đã có text để che coverage thấp. Precision@10 coi nhãn >=2 là relevant; NDCG@10 dùng gain 2^label-1, discount log2(i+1).

## Tiêu chí đề xuất trước đo

| Nhóm | Điều kiện đề xuất |
|---|---|
| Chất lượng chính | Macro NDCG@10 tăng ít nhất 0,03 tuyệt đối so baseline; paired bootstrap 10.000 lần theo nhu cầu, seed 20261121, cận dưới 95% của delta >0 |
| Chất lượng phụ | Macro Precision@10 delta >=0; cận dưới 95% delta >=-0,02 |
| Coverage | Tối thiểu 80% MATCH có SCORED toàn bộ held-out; công bố từng nhu cầu, nhóm/ngôn ngữ/độ đầy đủ có đủ mẫu; thiếu coverage không ghi thành điểm 0 |
| An toàn nghiệp vụ | 0 NOT_MATCH/MISSING_DATA lọt advisory; 0 thay đổi canonical/privacy release; 0 truy cập trái quyền/quá TTL trong tập kiểm thử |
| Hành vi lỗi | Không READY một phần; timeout/revoke/stale/disabled đều có fallback canonical đúng nhãn; không ngầm auto nominate |
| Tính lặp lại | Cùng input/model/policy cho cùng thứ tự sau lượng tử hóa trên môi trường đã đo; báo số cặp đảo nếu hardware khác |

Nếu nhu cầu IDCG=0 hoặc không đủ held-out hợp lệ làm còn dưới 30 nhu cầu quyết định, bổ sung dữ liệu độc lập trước kết luận. Bootstrap không sửa thiên lệch lấy mẫu hoặc thay đánh giá chuyên môn. Không chọn model/template/weight bằng cách nhìn held-out rồi vẫn báo đó là kiểm tra độc lập; thay policy cần bộ kiểm tra mới hoặc ghi đúng trạng thái exploratory.

## Đo hệ thống và thử nghiệm sử dụng

Môi trường riêng, một model worker CPU, không dùng Docker daemon demo 8088. Đo cold start, peak RSS của Java/Python/MySQL, job latency p50/p95 ở 100/400/5.000 MATCH, HTTP error/busy rate, queued time, current-check cost và API/page latency. Giới hạn 600s/attempt là timeout thiết kế, không cam kết đạt 5.000 hồ sơ. Trước load quyết định, chốt phần cứng và ngân sách tài nguyên của môi trường; R31 dùng đúng điều kiện đã được yêu cầu riêng, không lấy số đo máy khác thay.

Pilot sau technical/quality gate: cán bộ xử lý các nhu cầu tương đương theo canonical và advisory, phân bổ thứ tự sử dụng để giảm learning effect, ghi thời gian chọn shortlist và đánh giá hữu ích. Đề xuất mục tiêu median thời gian giảm 15%, không tăng số quyết định phải sửa; cần chốt cỡ mẫu/scope trước pilot. Đây là human-use gate riêng, không suy ra từ NDCG. Feedback/nhật ký chỉ thu theo scope được cho phép, không tự thêm tracking endpoint hoặc lưu free text cá nhân vào audit.

## Bằng chứng bàn giao

Report phải có dataset/labels/split/model/source manifests, reviewers/nhãn được quản lý riêng, script/version metric, baseline/AI per-query + macro + CI/coverage, trường hợp lỗi, thông tin phần cứng và giới hạn. Dữ liệu thật/credential/raw CV không vào Git; fixture trong repo chỉ synthetic và ghi đúng nhãn. Tách REAL_MODEL/OFFLINE/QUALITY/LOAD/BROWSER khỏi MOCK_PROTOCOL. Không đạt hoặc thiếu đầu vào thì giữ OFF/shadow, nêu nguyên nhân; không thay QA/Lead/nhà trường nghiệm thu bằng unit PASS.
