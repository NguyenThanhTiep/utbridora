# T1-04-UI: API đọc quản lý phân công cán bộ

Bổ sung theo task T1-04-UI ngày 12/11/2026. Contract Pack v1.0 và CP-AUTHZ1 tiếp tục là nền cho các lệnh cấp/thu hồi; tài liệu này mô tả API đọc mới phục vụ màn hình. Không thay thế bản Contract Pack đã chốt, không thêm role hay permission code.

## Phạm vi và quyền

Hai prefix: `/api/v1/admin/school-delegations` và `/api/v1/school/school-delegations`.

- Nhánh admin đòi ADMIN global hiện hành có `membership:admin:write`; chỉ đọc metadata phân công và cán bộ. Không cấp quyền đọc hồ sơ sinh viên.
- Nhánh school đòi membership SCHOOL_STAFF tại trường VERIFIED và chuỗi delegation hiện hành có `school:delegation:manage` theo đúng term. Scope suy từ TermReference trong DB; không tin school ID từ client.
- `/scopes` chỉ liệt kê kỳ được quản lý. Không có delegation quản lý hợp lệ trả page rỗng. Các API đọc trong kỳ không được quản lý trả 404 `RESOURCE_NOT_FOUND`.
- Danh sách cán bộ chỉ gồm tài khoản ACTIVE, membership SCHOOL_STAFF active tại trường đó; loại chính người cấp. Không trả mật khẩu, securityVersion, token hay hồ sơ sinh viên.

## Các endpoint GET

| Suffix | Query | Response 200 |
|---|---|---|
| `/scopes` | `page=0,size=25` | `Page<Scope>` |
| `/options` | `termId` bắt buộc | `Options` |
| `/candidates` | `termId`, `search` tối đa 150 ký tự, `page,size` | `Page<Candidate>` |
| collection (không suffix) | `termId`, `page,size` | `Page<Detail>` |
| `/{id}` | UUID delegation | `Detail` |

`page`: 0..10000; `size`: 1..100. Page có `content,page,size,totalElements,totalPages`. Scopes sort schoolName/termCode/id; candidates sort displayName/id; delegations sort id, đều ASC. Search tên/email case-insensitive, escape wildcard SQL.

```text
Scope { schoolOrgId, schoolName, termId, termCode, termStatus }
Options { scope:Scope, authorities:SchoolDelegation[], permissionCodes:string[], profileReadFields:string[] }
Candidate { userId, displayName, email }
Detail { delegation:SchoolDelegation, parentId:UUID|null, displayName, email, grantorName,
         effectivePermissionCodes:string[], canRevoke:boolean }
```

`SchoolDelegation` giữ DTO của `SchoolDelegationService`: id/userId/schoolOrgId/termId, permissionCodes, profileReadFields, validFrom/validUntil (UTC ISO), active/version/grantedBy/decisionRef. `active=true` không đủ chứng minh còn quyền: `effectivePermissionCodes` kiểm cả account, membership, ceiling, thời hạn và ancestry hiện hành. Parent bị thu hồi có thể để row con active nhưng danh sách quyền hiệu lực rỗng.

`authorities` trong nhánh school chỉ chứa các delegation quản lý của chính actor còn hiệu lực. Màn hình chọn một authority; các quyền/mask/thời hạn gửi xuống vẫn được owner service kiểm lại. `canRevoke` chỉ hướng dẫn hiển thị nút, không thay kiểm quyền khi mutation.

## Mutation giữ nguyên CP-AUTHZ1

- POST `/api/v1/admin/school-delegations`: bootstrap demo, decisionRef bắt đầu `DEMO-`, không tự cấp.
- POST `/api/v1/school/terms/{termId}/delegations`: grant từ một parent đủ quyền/mask/thời hạn, không tự cấp.
- DELETE `/api/v1/school/delegations/{id}`: expectedVersion và reasonCode; đúng người đã cấp và còn thẩm quyền. ADMIN chỉ có đường thu hồi bootstrap DEMO do chính mình cấp.

Session + CSRF + UUID Idempotency-Key; reuse key/body khi retry cùng command. Mutation, audit, OperationLedger và invalidation phiên cùng transaction. Phiên cán bộ nhận/bị thu hồi quyền phải đăng nhập lại. 400 schema/pagination; 401 phiên; 403 từ chối quyền; 404 ngoài scope; 409 stale/key conflict; 422 thời hạn/căn cứ demo theo service hiện có.
