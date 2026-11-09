# Phạm vi và kiểm chứng tuần 1

Đối chiếu 19 dòng tuần 1 trong Plan và SRS v1.2.4. Bảng này ánh xạ output với phép kiểm; kết quả thực thi phải lấy từ CI của **đúng SHA** đang xem. Bảng không phải chữ ký QA/Lead và không tự đánh dấu nghiệm thu thay người phụ trách. Không sửa trạng thái trong workbook cá nhân chỉ để hợp thức hóa tiến độ.

| Task | Output trong phạm vi tuần 1 | Bằng chứng thực thi |
|---|---|---|
| T1-01 | Contract, schema/version/error dùng chung; giữ tương thích Core | `scripts/contracts/build_contract_bundle.py --check`, `verify_contracts.py`, portability/audit-registry regression |
| T1-02 | Auth/session/CSRF, quyền và scope Core | `CorePlatformIT`, unit Core; N+1 admin được kiểm bằng số truy vấn MySQL/Hibernate thực tế |
| T1-03 | Docker, CI, môi trường demo trên máy hiện tại | `compose.demo.yaml`, `Week1DemoIT`, probe HTTPS/cookie/scope, [hướng dẫn vận hành](../guides/WEEK1_RUNTIME.md); client từ máy khác ghi riêng |
| T2-01 | Lưu/tải hồ sơ, ngành/kỳ/trường, version và quyền | `StudentProfileIT`: term link thật, catalog T2/T4 thật, idempotency, rollback audit, trường khác/revoke, mã sai/deprecated; transaction snapshot cũ phải thấy mã vừa bị ngừng sử dụng |
| T2-02 | UI tài khoản/hồ sơ, ngành, điện thoại/thời gian, lỗi theo trường | `student-runtime.spec.ts` chạy từ StudentProfileIT: tạo → chọn ngành → lưu → reload; mock UX là bộ riêng |
| T2-03 | Schema/DTO/port roster và eligibility cơ bản | `StudentDataConstraintsIT`, `LinkedStudentTermScope`, V10; MSSV chuỗi, FK school/term/user và unique student-term. Import 5.000 dòng không phải gate tuần 1 |
| T3-01 | Service Port tiếp nhận message, Case đúng trạng thái, chặn Task sớm | `InternshipRuntimeIT`: producer/source/audit/outbox cùng TX; inbox/Case/history cùng TX; duplicate/race/stale/out-of-order, rollback, scope sai, retry/DLQ, nguồn hủy chưa truyền event |
| T3-02 | Entity/API cohort/nhóm/task sơ bộ, scope/state | `WorkspaceServiceTest`, `WorkspaceConcurrencyTest`, `InternshipRuntimeIT`: intake ID, preview/confirm nhóm, version/CAS, Task và scope; migration V21 |
| T3-03 | UI HR/Mentor thấy phân công/nhóm thật | `internship-runtime.spec.ts` chạy từ InternshipRuntimeIT; `internship.ui.spec.ts` kiểm UX riêng bằng mock |
| T4-01 | Taxonomy chuẩn, alias và version; nguồn dùng chung | `BusinessModulesIT`, unit taxonomy, `TaxonomyReferencePort`, profile validation qua provider thật |
| T4-02 | Fixture MATCH/MISSING_DATA/NOT_MATCH tổng hợp | `T4MatchingFixtureIT` nạp/reapply 1.000 ca trong MySQL riêng; 100 sinh viên, không PII thật. Không gọi fixture là classifier production |
| T5-01 | TermReference và xác minh doanh nghiệp theo trường | `BusinessModulesIT`: term/scope/delegation, verification draft/submit/decision, file binding và history; `StudentTermReferenceAdapter` dùng nguồn thật |
| T5-02 | Demand criteria, snapshot/revision bất biến, port cho T4 | `DemandIT`: list/create/edit/replay, version/catalog, history, current enterprise, `DemandCriteriaPort`; provider học vụ thật |
| T5-03 | UI shell doanh nghiệp/trường + form Demand đúng schema | `demand-runtime.spec.ts` qua API thật, UI mock riêng; phần workflow về sau vẫn ghi trạng thái chờ rõ |
| QA-01 | Case/smoke Core và danh sách chưa chạy | `qa01-week1.spec.ts`, CorePlatformIT và danh sách bên dưới; không gắn nhãn QA01 đã ký |
| QA-02 | Positive/negative hồ sơ, MSSV và school scope | StudentProfileIT, StudentDataConstraintsIT, browser profile và unit parser/schema |
| QA-03 | Fixture nhóm/task, WAITING/ACTIVE, phân công, T3 không tạo Placement | InternshipRuntimeIT và browser HR/Mentor; tiền điều kiện Placement/verification/invitation tổng hợp được khai báo rõ |
| QA-04 | Expected S1–S11 độc lập với classifier DEV | [oracle thủ công](fixtures/t4-s1-s11.json), `scripts/fixtures/t4/verify_s1_s11_fixture.py` đối chiếu SRS §16.3; Strict S9 NOT_MATCH, S10 alias, S11 FAIL thắng UNKNOWN |
| QA-05 | Case/dữ liệu doanh nghiệp–trường, từ chối ngoài scope | BusinessModulesIT, DemandIT và fixture tách biệt; kiểm version, delegation, evidence binding, immutable history |

## Những gì không được gộp thành PASS tuần 1

- Classifier/MatchRun tuần 2 chưa được coi là đã chạy chỉ vì fixture hoặc expected S1–S11 hợp lệ. Oracle ghi `NOT_RUN_WEEK2_TASK`; cần thực thi trên classifier khi task đó có implementation.
- Completeness đầy đủ, UI kỹ năng/consent, Mentor assignment workflow, submission/review, cooperation/proposal/Placement approval, file upload/scan là các task tiếp theo. Những gate hiện tại thiếu nguồn giữ fail-closed; không mặc định PASS.
- Verification/Placement/SD/invitation trong demo là tiền điều kiện synthetic, không phải phê duyệt thật hoặc bằng chứng hoàn thành toàn bộ workflow tuần 2/3. Không có file CLEAN giả trong demo.
- UI shell trường không được báo thành UI xét duyệt hoàn chỉnh. Tuần 1 T5-03 cho phép màn chờ ở phần chưa thuộc output đó.
- Human QA/Lead signoff và truy cập từ máy thật của 5 team chỉ ghi khi có bằng chứng tương ứng. Probe 5 tài khoản từ máy chủ không thay cho thử nghiệm từ 5 máy khác.

Migration cũ V1–V18 được giữ nguyên. V19–V21 được thêm tuần tự; MigrationUpgradeIT giữ các assertion dữ liệu/constraint cũ và cập nhật số migration mới. Database/demo dùng volume riêng, không chỉnh dữ liệu của stack 8088/3307.
