# Kiểm tra tích hợp DEV07 với nhánh chung — 20/11/2026

Push commit DEV07 `1243e34` bị từ chối `fetch first`: remote đã thêm bốn commit,
đến `608e21e`, gồm UI DEV06 `314900a` và sửa điều hướng/fixture của DEV05.
Đã tạo nhánh dự phòng `backup/dev07-before-uiux-sync-1243e34`, fetch nhánh chung
và merge bằng `--no-commit --no-ff`. Người dùng tự commit và push.

Hai conflict đã giải quyết:

- `InternshipWorkspace.tsx`: giữ điều kiện mở board HR ở màn nhóm của remote,
  đồng thời giữ initialView tổng quan Mentor/board HR của DEV07.
- `internship-week3-runtime.spec.ts`: chọn menu con Mentor/HR theo điều hướng mới,
  sau đó chọn Kanban. Giữ ca tạo nhiệm vụ và các kiểm tra lịch sử/quyền của DEV07.

Các thay đổi School/HR/Case và tách EvaluationView/SchoolGradePanels từ remote
được giữ. Không sửa backend/migration. Hai log TXT cũ được chuẩn hóa xuống dòng,
xóa whitespace cuối dòng và cập nhật hash; không thay kết quả test lịch sử.

Kết quả bản ghép:

- Build TypeScript/Vite PASS; 15 unit PASS.
- 76 UI PASS, không skip (gồm cả UI DEV06).
- 5 workflow runtime PASS; 4 evaluation runtime PASS, không skip.
- Maven hai wrapper integration PASS, BUILD SUCCESS. Runtime chạy MySQL riêng.
- Không có conflict còn mở. Cần commit/push rồi kiểm CI và QA trên SHA mới.

Bằng chứng mới tách khỏi gói trước merge:

- [Build](evidence/MERGE_BUILD.txt), [unit](evidence/MERGE_UNIT.txt).
- [UI](evidence/MERGE_UI_RESULTS.xml), [workflow](evidence/MERGE_WORKFLOW_RESULTS.xml), [evaluation](evidence/MERGE_EVALUATION_RESULTS.xml).
- [Hash source bản ghép và bằng chứng](evidence/MERGE_MANIFEST.json).

Lệnh Git đã thực hiện:

```bash
git fetch origin feature/t3-frontend-uiux
git branch backup/dev07-before-uiux-sync-1243e34
git merge --no-commit --no-ff origin/feature/t3-frontend-uiux
```

Sau khi sửa conflict, đã stage đúng các file liên quan. Không cần stash vì các
thay đổi DEV07 đã nằm trong commit 1243e34; thư mục UIUX untracked được giữ nguyên.
Không force push và không tạo commit thay người dùng. Giới hạn scanner CLEAN,
staging, QA03 và T2 owner review vẫn như hồ sơ bàn giao; chưa phải nghiệm thu CI/QA.
