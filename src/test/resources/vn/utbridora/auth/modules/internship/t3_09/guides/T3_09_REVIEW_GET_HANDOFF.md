# T3-09 — hoàn thiện đọc nhận xét bài nộp, 12/11/2026

DEV07 Lại Thái Thuận. Backend mới `4d187ab` của DEV05, nhánh `feature/t3-week3`.
Nguồn API: `docs/reviews/T3_07_REVIEW_READ_HANDOFF.md` và SubmissionReviewController/
SubmissionService hiện có. Không thay production Java/migration/contract.

## Hoàn thành trong lượt này

- GET một ReviewDto theo submissionId, hoặc 204; dùng transport Core hiện có.
- V1/v2 có nút xem riêng, hiện verdict/comment/reviewer/time. Reload student đọc
  yêu cầu sửa; sau ACCEPT đọc đúng nhận xét v2, v1 vẫn giữ nguyên.
- Sau POST review đọc lại GET thay vì dùng receipt trong bộ nhớ làm lịch sử.
- 401/404 đóng workspace, 503 cho retry; response sai submission/version bị chặn.
- Chuyển version/context bỏ kết quả GET cũ. Không lấy assignmentVersion của
  review lịch sử làm version cho mutation hiện tại.
- 8 ca UI mới và mở rộng browser thật kiểm student, Mentor, HR, outsider.

Xem [kết quả kiểm thử cuối](../testing/T3_09_TEST_REPORT.md) và
[bàn giao QA](../testing/T3_09_QA_HANDOFF.md). Minh chứng GET mới có tiền tố
`T3_09_REVIEW_GET_`; evidence các lượt trước giữ nguyên để đối chiếu.

## Git đã thực hiện

Trước thao tác: branch `feature/t3-week3`, HEAD `59ef6d2`, đang merge main `c3d0dbc`
với 12 file staged và các thay đổi DEV07 chưa stage. Đã sao lưu ZIP, binary patches,
HEAD/MERGE_HEAD và danh sách đường dẫn vào `target/t3-09-before-review-get-20261112`.

```powershell
git fetch origin
git stash push -u -m "wip: DEV07 T3-09 before DEV05 review GET integration 2026-11-12"
git pull --ff-only origin feature/t3-week3
git merge --no-commit c3d0dbc
git stash apply 'stash@{0}'
git status --short --branch
```

- `fetch`: lấy ref/commit mới, chưa đổi working tree.
- `stash -u`: cất cả thay đổi file đã theo dõi và file mới.
- `pull --ff-only`: tiến nhánh đến `4d187ab`, không tạo commit merge mới.
- `merge --no-commit`: phục hồi phần main đã ghép trước đó và dừng trước commit.
- `stash apply`: đưa code DEV07 trở lại, giữ stash dự phòng. Lượt này không conflict.

Stash mới nhất đã apply; **không apply lần nữa**. Các stash cũ và backup còn giữ.
Git vẫn có MERGE_HEAD vì người dùng sẽ commit; đây không phải conflict chưa xử lý.

## Người dùng tự commit/push sau review

Chạy từ repo root. Các lệnh dưới là hướng dẫn, AI chưa thực hiện commit/push.
Trước hết kiểm 12 file main staged, rồi commit riêng merge đang chờ:

```powershell
git diff --cached --stat
git diff --cached
git commit -m "merge: sync main baseline into t3-week3"
```

Tiếp theo stage đúng phần DEV07, gồm tài liệu T3-03 đã di chuyển từ trước:

```powershell
git add -- docs/README.md docs/guides/T3_03_HR_MENTOR_WORKSPACE.md docs/testing/T3_03_QA_HANDOFF.md docs/testing/T3_03_TEST_REPORT.md
git add -- docs/testing/evidence/T3_03_DEPENDENCY_VERIFICATION_2026_11_06.json docs/testing/evidence/T3_03_DEV_VERIFICATION.json docs/testing/evidence/T3_03_FULL_VERIFICATION_2026_11_06.json docs/testing/evidence/T3_03_QA_VERIFICATION_2026_11_07.json docs/testing/evidence/T3_03_UI_UNIT_RESULTS.xml docs/testing/evidence/t3-03
git add -- src/test/resources/vn/utbridora/auth/modules/internship/t3_03 src/test/resources/vn/utbridora/auth/modules/internship/t3_09 src/test/java/vn/utbridora/auth/T309WorkflowBrowserIT.java
git add -- web-demo/src/App.tsx web-demo/src/api.ts web-demo/src/modules/internship/pages/TaskBoard.tsx web-demo/src/modules/internship/pages/SubmissionWorkspace.tsx web-demo/src/modules/internship/pages/WeeklyReports.tsx web-demo/src/modules/internship/pages/ReportEvidence.tsx web-demo/src/modules/internship/workflow.ts web-demo/src/modules/internship/workflow-api.ts web-demo/src/modules/internship/useWorkflowCommand.tsx web-demo/src/modules/internship/styles.css
git add -- web-demo/tests/internship-week3.ui.spec.ts web-demo/tests/internship-week3-runtime.spec.ts web-demo/tests/unit/internship-workflow.spec.ts web-demo/tests/fixtures/internship-week3-harness.tsx web-demo/tests/fixtures/internship-week3.html
git diff --cached --check
git diff --cached --stat
git commit -m "feat(t3-09): complete submission feedback and weekly report workspace"
git fetch origin
git log --oneline HEAD..origin/feature/t3-week3
```

Nếu lệnh log cuối không có commit mới từ đồng đội, có thể push:

```powershell
git push origin feature/t3-week3
```

Nếu remote có commit mới, cần tích hợp và kiểm tra lại trước push; không force-push.
Không xóa nhánh chung `feature/t3-week3`. Gửi QA SHA sau push cùng test report;
đánh dấu chờ QA, không tự coi DEV PASS là QA PASS.
