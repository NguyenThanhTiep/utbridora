# Bàn giao T2 từ tuần 3 sang tuần 4

Ngày bàn giao: 13/11/2026. Đây là bàn giao DEV sau tích hợp; QA, Lead và nghiệm thu SRS là các xác nhận riêng, chưa được thay mặt ký.

## 1. Nhận đúng base và nguồn

- Commit code tích hợp đã kiểm chứng trước commit tài liệu: `450e78d1fbd27a2da46462d3899239185cb7b946`. Nhận từ `origin/main` bằng fetch/pull trên checkout sạch; đối chiếu SHA và evidence bên dưới. Không lấy SHA của chính commit tài liệu để tạo vòng lặp sửa commit.
- Base main trước tích hợp: `e22596ac9b40ecaf7b4896ec9199ccf583b6a36c`. T1-06 nguồn: `065c5be15f19df2fe8100c42248c5bd6d9c4a956`. Không làm lại delegation, ActorContext, ledger/audit hay storage đã có.
- Nguồn yêu cầu: [Plan](../plans/UTBridora_Plan.xlsx), sheet **CAP NHAT CHUNG**; [SRS v1.2.4](../srs/UTBridora_SRS_Tong_The_v1.2.4.docx), ưu tiên §25.8; [Contract v1.0 đã chốt 05/11](../contracts/README.md). v1.1 Candidate và các GET bổ sung của T3 là diff kỹ thuật, không tự thành quyết định phê duyệt thay v1.0.
- [Ma trận tuần 3](../testing/WEEK3_INTEGRATION_ACCEPTANCE.md), [evidence đúng source](../testing/evidence/WEEK3_INTEGRATION_VERIFICATION.json), [runtime và nâng cấp DB](../guides/WEEK3_INTEGRATION_RUNTIME.md). Đọc báo cáo mới trước các ghi nhận lịch sử “chờ T1/team khác”.
- [CI đúng code SHA](https://github.com/NguyenThanhTiep/utbridora/actions/runs/43352236892) PASS: 1270 unit; 223 integration; 164 browser PASS + 16 SKIP theo context harness, không có FAIL/ERROR. Các luồng runtime thật có harness riêng. Contract 868 PASS, 0 FAIL; build/typecheck frontend PASS. Đây là bằng chứng DEV.
- Giữ các nhánh nguồn sau merge. Không phục hồi QA `f606b90`, không đọc/merge nhánh tester/QA. Tests đã có trong main/DEV vẫn thuộc regression.

## 2. Trạng thái tuần 3 và bằng chứng

- Nguồn T2: `64af78d1c6c4e4546df5f77185d95b731cbc86f7`; T5 Opportunity/eligibility/provider được tích hợp từ nhánh T5, không còn dùng fallback ở production.
- T2-07: Preference submit/withdraw/review, root/current intent + history; `opportunityId=cooperationId`; cùng key replay, intent đã review bất biến. Invitation ACCEPT/DECLINE vẫn do T5 sở hữu, consent riêng tại T2, Placement riêng T5.
- T2-08: Opportunities, S06 tabs invitation/preference, policy/consent, loading/error/retry và reload dùng API thật. Test fixture-only cũ vẫn được ghi nhãn; `EnterpriseProposalIT.preferencesUseRealT5PublicationWithHistoryReplayAndIndependentDecisions` và `week3-integration-runtime.spec.ts` kiểm seam thật.
- Đã thêm current eligibility/snapshot guard cho consumer Placement; không tạo một bản schoolApproved khác ở T5. Shared CV nối adapter T1, từng GET/Range kiểm binding, attachment hiện hành, policy∩consent∩share và CLEAN.
- Chưa nghiệm thu QA02/Lead; scanner và kết quả điểm chưa công bố là phần tuần 4 chưa có, không dùng dữ liệu giả để mở UI.

## 3. Công việc tuần 4 đúng Plan

| Mã / dòng Plan | Thực hiện | QA | Công việc nguyên văn | Acceptance nguyên văn | Phụ thuộc Plan |
|---|---|---|---|---|---|
| T2-09 / 59 | DEV02 | QA02 | CV/dự án/chứng chỉ và binding file theo khả năng T1 | Lưu CV/chứng chỉ rồi xem lại được, tệp chỉ tải khi an toàn và có quyền. | T1-07; T2-01 |
| T2-10 / 60 | DEV03 | QA02 | UI sinh viên: Task → nộp URL → báo cáo → xem điểm | Điểm chưa công bố không xuất hiện; nộp URL và báo cáo gọi API T3 thật. | T3-05; T3-08 |
| T2-11 / 61 | DEV04 | QA02 | Kiểm tra consent revoke/expiry, school policy và scope | Tài khoản doanh nghiệp/nhà trường sai phạm vi phải bị từ chối. | T2-06; T5-05 |


## 4. Trình tự triển khai và source kế thừa

1. DEV02 đọc `modules/student/api/StudentProfileDtos`, `StudentProfileService`, `StudentProfileRepository` và `StudentProfileFilePolicy`. DB/API project, certificate, cvFileId đã tồn tại trong full profile PUT; kiểm thiếu cụ thể trước khi thêm schema. Không dựng owner CV hoặc bảng profile thứ hai.
2. Hoàn thiện UI S04 cho project/certificate/CV bằng cùng full ProfileInput và expectedProfileVersion; giữ skills/availability/codes không liên quan khi save. Resource STUDENT_PROFILE dùng studentUserId từ principal. Thêm root trước, upload private bằng multipart transport T1; chỉ giữ fileId. Hiện PENDING/REJECTED rõ, không cho tải/submit yêu cầu CLEAN khi chưa có scanner.
3. DEV03 kế thừa `TaskBoard`, `SubmissionWorkspace`, `WeeklyReports`, `workflow-api.ts`, `useWorkflowCommand` của T3 đã nối API thật. Bổ sung điểm vào luồng sinh viên sau khi T3-10/11 cung cấp endpoint CP-WS4; không tạo Evaluation canonical trong T2 và không dựng điểm frontend.
4. DEV04 mở rộng matrix consent/policy/scope trên provider thật; tái dùng `EnterpriseProposalIT`, `StudentPreferenceIT`, `Week2WorkflowIT`, `PrivateFileServiceIT`. Revoke có hiệu lực lần đọc mới sau commit; regrant không hồi sinh share cũ, không tự cancel Placement.
5. Khi T1-07 sẵn, chỉ thêm scanner-output/runtime tests đang thiếu. Không viết lại upload/storage/binding hoặc xin lại quyết định contract đã chốt.

## 5. API, DTO, quyền và migration

| Phần | Dùng hiện có / target tuần 4 |
|---|---|
| Profile/CV | GET/PUT `/api/v1/students/me/profile`, ProfileInput đầy đủ; `student_profile:self:read/write`, principal SELF và school/term links |
| File | POST `/api/v1/files` multipart; GET `/{id}/metadata`, `/{id}/content`; resourceType=STUDENT_PROFILE, resourceId=studentUserId, purpose=CV/EVIDENCE. JSON helper không được làm hỏng FormData |
| Consent | GET/POST `/students/me/consents`; POST `/{id}/revoke`; policy/version/lineage hiện hành, không pre-check consent |
| T3 hiện có | `/t3/assignments`, submission versions/reviews/comments và report/revision/review APIs; owner T3 và current Case/Placement gate |
| Điểm target chưa có | GET `/api/v1/t3/me/results?caseId=...`, StudentResultDto; chỉ khi PUBLISHED + school visibility + effective HR visibility. HIDDEN phải evaluation=null ở backend |

Không thêm migration nếu chỉ hoàn thiện form trên schema hiện có. Nếu thật sự cần DDL, lấy số global sau HEAD main mới nhất, không tự dùng V28/V29. Port `StudentOpportunityPort` đã nối T5 `OpportunityPort` qua ActorContext server-only; không đọc bảng T5 trong code T2.

## 6. Acceptance và negative cases

- CV/project/certificate save→reload không mất data; file khác owner/binding/resource/purpose, chưa CLEAN, deleted hoặc không còn attachment phải bị chặn, kể cả GET/Range trực tiếp.
- Dùng school/term A và B, enterprise khác recipient, current policy giảm whitelist, consent revoked/expired/replaced. Không lộ dữ liệu ngoài fields hoặc tên file gốc qua metadata/error.
- Hai lần sửa profile cùng version chỉ một effect; retry key/body giống nhau một effect, khác body báo conflict. Audit lỗi rollback profile/ref/ledger.
- Student A không đọc task/submission/report/result của B; Case chưa ACTIVE hoặc source đã CANCELLED chặn ghi dù event chưa đến.
- Điểm: kiểm 8 tổ hợp PUBLISHED × school enabled × HR enabled; chỉ tổ hợp đủ điều kiện trả score. HR case override org/term, thiếu policy là false; đổi frontend không được làm API lộ điểm.
- Targeted: `.\mvnw.cmd -B '-Dit.test=StudentProfileIT,StudentPreferenceIT,EnterpriseProposalIT,PrivateFileServiceIT,T309WorkflowBrowserIT' '-Dfrontend.e2e=true' '-Dt5.week3.browser=true' verify`.

## 7. Demo và giới hạn

Trên DB riêng: school công bố Cooperation APPROVED → student xem cơ hội/gửi rồi rút nguyện vọng → nhận lời mời và consent riêng → school share → enterprise đọc đúng whitelist → revoke và thử URL file/Range cũ. Demo task URL và report dùng T3 hiện có; bước xem điểm chờ provider tuần 4. File PENDING là trạng thái hợp lệ, không đổi thành CLEAN để trình diễn. Chụp desktop/mobile và kiểm sau reload.

## 8. Môi trường và phụ thuộc thực sự

- Chỉ dùng MySQL/Testcontainers hoặc stack dev riêng. Không seed/import, đổi password/membership, rebuild/deploy hay sửa volume/database của `utbridora-local:8088`. `.env.local-demo` là secret, không in/commit/đưa vào build context.
- Không deploy trong lượt tích hợp này; UI đang chạy tại 8088 có thể chưa phản ánh main mới. Xem runtime guide trước khi tự dựng bản riêng.
- Sự cố đã khắc phục trong lượt test local: tài nguyên Docker cạn làm backend cũ bị OOM-kill. Đã dừng local IT, chỉ dọn container test và start lại đúng container/image backend cũ; bốn container shared đã healthy. Xem runtime/evidence; không coi đây là lần deploy source mới.
- File mới luôn PENDING. Storage/upload/binding/CLEAN guard đã có; **scanner T1-07 chưa có**. CLEAN trong IT là fixture đầu ra scanner và không chứng minh antivirus chạy. Không fake CLEAN trong production.
- Số migration là toàn repo: default `db/migration/base` (V1–V27, V33) + `db/migration/main` (V28/T1, V29/T3, V31/report), Java V32 hội tụ. V1–V31 gốc không đổi checksum. DB đã dùng nhánh T2/T4/T5 phải dùng đúng locations theo runtime guide; không đổi history, repair, reset hay bật out-of-order.
- Giữ cột GPA/dữ liệu lịch sử của DB T4 nếu đã có; API mới theo contract v1.0 không mở GPA. Legacy minGpa=null đọc tương thích trong bộ đọc persistence; minGpa khác null cần owner review, không âm thầm bỏ predicate. Chạy preflight và thử trên bản sao trước nâng cấp.
- Location GPA `t4-v30`/`t4-v31` chỉ được thêm khi DB đã áp dụng script tương ứng. DB chưa chạy V31 không mới backfill JSON revision/snapshot; giữ immutable JSON/hash. Bảng locations trong runtime guide là nguồn chuẩn cho từng lịch sử.
- Chưa có bằng chứng human QA/Lead, dữ liệu thật được phép, 5 team truy cập hoặc benchmark 400/5.000 SV. 1.000 cặp fixture không phải 1.000 sinh viên và không thay workload 400.

## 9. Cách chạy kiểm tra và bàn giao lại

Chạy ở root với JDK21, Docker và Node/npm. Mọi IT bên dưới tự tạo DB riêng; không trỏ chúng vào DB chung. Browser chạy tuần tự ở 5178; không chạy nhiều harness đồng thời. Lệnh đầy đủ giống CI:

```powershell
Push-Location web-demo
npm.cmd ci
npm.cmd run build
Pop-Location
.\mvnw.cmd -B '-Dfrontend.e2e=true' '-Dt5.week3.browser=true' verify
```

Contracts dùng virtualenv có `scripts/contracts/requirements.txt`:

```text
python scripts/contracts/build_contract_bundle.py --check
python scripts/contracts/verify_contracts.py --output target/contract-verification.json
python scripts/contracts/test_audit_registry.py
python scripts/contracts/test_bundle_portability.py
python scripts/fixtures/t4/verify_s1_s11_fixture.py
```

Sau khi thay DTO/provider, kiểm cả consumer, lỗi 400/401/403/404/409/422, current permission, CSRF, idempotency và audit rollback. Không sửa assertion/expected để ép PASS; giữ report đỏ và chạy lại phần bị ảnh hưởng trước regression cuối.

Checklist gửi lại:

- [ ] Branch/SHA, base main, diff và nguồn kế thừa; không commit secret/file tạm hoặc xóa nhánh.
- [ ] Task nào code đã có, còn thiếu hoặc chưa nghiệm thu; phân biệt fixture, unit, API, browser và QA.
- [ ] API/DTO/port, permission, migration mới và provider/consumer cùng version; không trùng owner/route/table.
- [ ] Lệnh, môi trường, SHA/source hash, số PASS/FAIL/SKIP/NOT RUN; log/JUnit/browser screenshot đúng lần chạy.
- [ ] Cross-org/scope, stale/version, replay, concurrency, rollback audit và file/consent negative cases.
- [ ] Giới hạn, phụ thuộc còn mở; QA/Lead xác nhận riêng; fetch main lại trước push và theo CI đúng SHA.
