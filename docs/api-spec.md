# FixLog Server API 명세

> **Base URL**: `/api` (별도 표기 없는 경우)
> **인증**: `Authorization: Bearer {accessToken}` 헤더 필수
> **워크스페이스 지정**: `X-Workspace-Id: {workspaceId}` 헤더로 현재 워크스페이스 지정. 생략 시 개인 워크스페이스 자동 사용

---

## 공통 응답 구조

```json
// 데이터 있음
{ "code": "SUCCESS", "result": { ... } }

// 데이터 없음 (삭제, 나가기 등)
{ "code": "SUCCESS", "message": "..." }

// 오류
{ "code": "UNAUTHORIZED|FORBIDDEN|NOT_FOUND|INVALID_REQUEST", "message": "..." }
```

---

## Enum 정의

| Enum | 값 |
|------|----|
| `WorkspaceRole` | `OWNER` \| `ADMIN` \| `MEMBER` |
| `PermissionType` | `ALLOW` \| `DENY` |
| `PermissionSource` | `DIRECT` \| `INHERITED` \| `WORKSPACE_DEFAULT` |
| `PrincipalType` | `USER` \| `GROUP` |
| `ResourceType` | `FOLDER` \| `DOCUMENT` |
| `InvitationStatus` | `PENDING` \| `ACCEPTED` \| `DECLINED` \| `EXPIRED` |
| `UserStatus` | `ACTIVE` \| `INACTIVE` \| `BANNED` \| `WITHDRAW` |
| `AuditAction` | `VIEW` \| `DOWNLOAD` \| `EDIT` \| `DELETE` \| `SHARE` \| `RESTORE` |
| `AuditResult` | `ALLOWED` \| `DENIED` |
| `AIMessageRole` | `USER` \| `ASSISTANT` |
| `AIMessageStatus` | `PENDING` \| `COMPLETED` \| `FAILED` |

---

## 1. 인증 (Auth)

### `POST /auth/token/refresh` — 토큰 갱신
인증 불필요

**Request**
```json
{ "refreshToken": "string" }
```
**Response**
```json
{ "accessToken": "string" }
```

### `GET /auth/token` — 현재 세션 조회
**Response**
```json
{ "userId": "uuid", "userName": "string", "email": "string" }
```

---

## 2. 워크스페이스 (Workspace)

### `POST /api/workspaces` — 생성
**Request**
```json
{ "workspaceName": "string" }
```
**Response** — `WorkspaceDto`
```json
{
  "workspaceId": "uuid",
  "workspaceName": "string",
  "personal": false,
  "role": "OWNER",
  "createAt": "2025-01-01T00:00:00Z"
}
```

### `GET /api/workspaces` — 내 워크스페이스 목록
**Response** — `WorkspaceDto[]`

### `GET /api/workspaces/{workspaceId}` — 단건 조회
**Response** — `WorkspaceDto`

### `PATCH /api/workspaces/{workspaceId}` — 이름 수정 _(Admin+)_
**Request**
```json
{ "name": "string" }
```
**Response** — `WorkspaceDto`

### `DELETE /api/workspaces/{workspaceId}` — 삭제 _(Owner 전용)_
- 개인 워크스페이스는 삭제 불가
- 내부 폴더·문서 soft-delete, 구성원·초대·권한 cascade 삭제

**Response** — 성공 메시지

### `GET /api/workspaces/{workspaceId}/members` — 구성원 목록
**Response** — `WorkspaceMemberDto[]`
```json
[{
  "userId": "uuid",
  "userName": "string",
  "email": "string",
  "role": "OWNER|ADMIN|MEMBER",
  "joinedAt": "2025-01-01T00:00:00Z"
}]
```

### `POST /api/workspaces/{workspaceId}/members` — 구성원 초대 _(Admin+)_
기존 가입 유저를 이메일로 즉시 추가

**Request**
```json
{ "email": "string" }
```
**Response** — `WorkspaceMemberDto`

### `PATCH /api/workspaces/{workspaceId}/members/{userId}` — 역할 변경
- OWNER 역할 부여/해제는 현재 OWNER만 가능
- 마지막 OWNER·마지막 관리자는 강등 불가

**Request**
```json
{ "role": "OWNER|ADMIN|MEMBER" }
```
**Response** — `WorkspaceMemberDto`

### `DELETE /api/workspaces/{workspaceId}/members/{userId}` — 구성원 제거 _(Admin+)_
**Response** — 성공 메시지

### `POST /api/workspaces/{workspaceId}/leave` — 나가기
마지막 Owner·마지막 관리자는 나가기 불가

**Response** — 성공 메시지

---

## 3. 이메일 초대 (Invitation)

### `GET /api/workspaces/{workspaceId}/admin/invitations` — 초대 목록 _(Admin+)_
**Response** — `InvitationDto[]`
```json
[{
  "id": "uuid",
  "workspaceId": "uuid",
  "email": "string",
  "role": "MEMBER",
  "status": "PENDING|ACCEPTED|DECLINED|EXPIRED",
  "expiresAt": "2025-01-08T00:00:00Z",
  "createAt": "2025-01-01T00:00:00Z",
  "invitedByName": "string|null"
}]
```

### `POST /api/workspaces/{workspaceId}/admin/invitations` — 초대 발송 _(Admin+)_
토큰 발급 후 DB 저장 (1차: 이메일 발송은 로그로 대체). 유효기간 7일.

**Request**
```json
{ "email": "string", "role": "MEMBER" }
```
**Response** — `InvitationDto`

### `DELETE /api/workspaces/{workspaceId}/admin/invitations/{invitationId}` — 초대 취소 _(Admin+)_
PENDING 상태 초대만 취소 가능

**Response** — 성공 메시지

### `POST /api/workspaces/invitations/{token}/accept` — 초대 수락
인증 필요. 로그인 이메일 ≠ 초대 이메일이면 403

**Response** — `InvitationDto`

### `POST /api/workspaces/invitations/{token}/decline` — 초대 거절
**Response** — `InvitationDto`

---

## 4. 그룹 (Group)

### `GET /api/workspaces/{workspaceId}/groups` — 그룹 목록
**Response** — `GroupDto[]`
```json
[{ "groupId": "uuid", "workspaceId": "uuid", "groupName": "string", "createAt": "..." }]
```

### `POST /api/workspaces/{workspaceId}/groups` — 그룹 생성 _(Admin+)_
**Request** `{ "groupName": "string" }`

### `PATCH /api/workspaces/{workspaceId}/groups/{groupId}` — 이름 변경 _(Admin+)_
**Request** `{ "groupName": "string" }`

### `DELETE /api/workspaces/{workspaceId}/groups/{groupId}` — 삭제 _(Admin+)_

### `GET /api/workspaces/{workspaceId}/groups/{groupId}/members` — 그룹 구성원 목록
**Response** — `GroupMemberDto[]`
```json
[{ "userId": "uuid", "userName": "string", "email": "string" }]
```

### `POST /api/workspaces/{workspaceId}/groups/{groupId}/members` — 구성원 추가 _(Admin+)_
**Request** `{ "userId": "uuid" }`

### `DELETE /api/workspaces/{workspaceId}/groups/{groupId}/members/{userId}` — 구성원 제거 _(Admin+)_

---

## 5. 폴더 (Folder)

### `POST /api/folders` — 생성
**Request**
```json
{ "parentId": "string|null", "folderName": "string" }
```
**Response** — `FolderDto`
```json
{
  "folderId": "string",
  "parentId": "string|null",
  "folderName": "string",
  "ordinal": 0,
  "createUser": "string",
  "createTime": "...",
  "updateUser": "string",
  "updateTime": "..."
}
```

### `GET /api/folders` — 루트 폴더 내용 조회
**Response** — `FolderContentsDto`
```json
{ "folders": [ FolderDto ], "documents": [ DocumentDto ] }
```

### `GET /api/folders/tree` — 폴더 트리 (사이드바용)
**Response** — `FolderTreeDto[]`
```json
[{
  "folderId": "string",
  "parentId": "string|null",
  "folderName": "string",
  "ordinal": 0,
  "documentCount": 3,
  "children": [ ... ]
}]
```

### `GET /api/folders/{folderId}` — 단건 조회
**Response** — `FolderDto`

### `GET /api/folders/{folderId}/contents` — 폴더 내용 조회
**Response** — `FolderContentsDto`

### `PUT /api/folders/{folderId}` — 업데이트
**Request** `{ "parentId": "string|null", "folderName": "string" }`

### `PATCH /api/folders/reorder` — 순서 변경
**Request** `{ "parentId": "string|null", "folderIds": ["string"] }`

### `PATCH /api/folders/{folderId}/move` — 이동
**Request** `{ "parentId": "string|null" }`

### `DELETE /api/folders/{folderId}` — 삭제 (휴지통)
하위 폴더·문서도 함께 휴지통으로 이동

---

## 6. 문서 (Document)

### `POST /api/documents` — 생성
**Request**
```json
{ "folderId": "string|null", "title": "string" }
```
**Response** — `DocumentDto`
```json
{
  "documentId": "string",
  "folderId": "string|null",
  "title": "string",
  "blocks": "{}",
  "plainText": "",
  "contentHash": "string",
  "ordinal": 0,
  "createUser": "string",
  "createTime": "...",
  "updateUser": "string",
  "updateTime": "..."
}
```

### `GET /api/documents` — 목록 조회 (페이지네이션)
**Query** `folderId` (선택), `page` (기본 0), `size` (기본 20)

**Response** — `DocumentPageDto`
```json
{
  "items": [ DocumentDto ],
  "page": 0, "size": 20,
  "totalElements": 100, "totalPages": 5,
  "hasNext": true
}
```

### `GET /api/documents/{documentId}` — 상세 조회
**Response** — `DocumentDto`

### `PUT /api/documents/{documentId}` — 내용 저장
**Request**
```json
{ "title": "string", "blocks": { /* BlockNote JSON */ } }
```
**Response** — `DocumentDto`

### `PATCH /api/documents/{documentId}/title` — 제목 변경
**Request** `{ "title": "string" }`
**Response** — `DocumentDto`

### `GET /api/documents/{documentId}/save-state` — 저장 상태 조회
**Response**
```json
{ "lastSavedAt": "...", "contentHash": "string" }
```

### `POST /api/documents/{documentId}/duplicate` — 복제
**Response**
```json
{ "newDocumentId": "string", "folderId": "string|null", "title": "string" }
```

### `DELETE /api/documents/{documentId}` — 삭제 (휴지통)

### `PATCH /api/documents/reorder` — 순서 변경
**Request** `{ "folderId": "string|null", "documentIds": ["string"] }`

### `PATCH /api/documents/{documentId}/move` — 이동
**Request** `{ "folderId": "string|null" }`

### `GET /api/documents/{documentId}/download` — PDF 다운로드
**Response** — PDF binary (`Content-Type: application/pdf`)

---

## 7. 문서 히스토리 (History)

### `GET /api/documents/{documentId}/history` — 목록 (페이지네이션)
**Query** `page`, `size`

**Response**
```json
{
  "items": [{
    "historyId": "string",
    "title": "string",
    "source": "MANUAL|RESTORE",
    "createUser": "string",
    "createTime": "..."
  }],
  "page": 0, "size": 20, "totalElements": 5, "totalPages": 1, "hasNext": false
}
```

### `GET /api/documents/{documentId}/history/{historyId}` — 상세
**Response**
```json
{
  "historyId": "string", "documentId": "string",
  "title": "string", "blocks": "string",
  "contentHash": "string", "source": "MANUAL",
  "createUser": "string", "createTime": "..."
}
```

### `POST /api/documents/{documentId}/history/{historyId}/restore` — 복원
**Response** — `DocumentDto`

---

## 8. 문서 리비전 (Revision)

### `GET /api/documents/{documentId}/revisions` — 목록
**Response** — `DocumentRevisionDto[]`
```json
[{
  "revisionId": "uuid",
  "revisionNo": 3,
  "title": "string",
  "contentHash": "string",
  "restoredFromNo": null,
  "createUser": "string",
  "createAt": "..."
}]
```

### `GET /api/documents/{documentId}/revisions/{revisionNo}` — 상세

### `POST /api/documents/{documentId}/revisions/{revisionNo}/restore` — 복원
**Response** — `DocumentDto`

---

## 9. 권한 (Sharing & My Permission)

### `GET /api/documents/{documentId}/my-permission` — 내 문서 권한 조회
현재 로그인 유저가 이 문서에 대해 갖는 유효 권한 (UI 버튼 표시 제어용)

**Response** — `MyPermissionDto`
```json
{
  "access": true,
  "canDownload": true,
  "source": "DIRECT|INHERITED|WORKSPACE_DEFAULT",
  "sourceDetail": "직접 부여|폴더 '전공'에서 상속|null"
}
```

### `GET /api/folders/{folderId}/my-permission` — 내 폴더 권한 조회
**Response** — `MyPermissionDto`

### `GET /api/documents/shared-with-me` — 공유받은 문서 목록
**Response** — `DocumentDto[]`

### `GET /api/documents/{documentId}/permissions` — 문서 권한 목록 _(Admin+ 또는 직접 SHARE 권한 보유자)_
**Response** — `PermissionDto[]`
```json
[{
  "permissionId": "uuid",
  "principalType": "USER|GROUP",
  "principalId": "uuid",
  "principalName": "string",
  "permissionType": "ALLOW|DENY",
  "canDownload": true,
  "createAt": "..."
}]
```

### `POST /api/documents/{documentId}/permissions` — 문서 공유 _(Admin+)_
`principalId`와 `email` 중 하나만 있어도 됨. 둘 다 있으면 `principalId` 우선.

**Request**
```json
{
  "principalType": "USER",
  "principalId": "uuid|null",
  "email": "string|null",
  "permissionType": "ALLOW",
  "canDownload": true
}
```
**Response** — `PermissionDto`

### `DELETE /api/documents/{documentId}/permissions/{permissionId}` — 공유 회수 _(Admin+)_

### `GET /api/folders/{folderId}/permissions` — 폴더 권한 목록 _(Admin+)_
**Response** — `PermissionDto[]`

### `POST /api/folders/{folderId}/permissions` — 폴더 공유 _(Admin+)_
**Request** — 문서 공유와 동일

### `DELETE /api/folders/{folderId}/permissions/{permissionId}` — 폴더 공유 회수 _(Admin+)_

---

## 10. 휴지통 (Trash)

### `GET /api/trash` — 목록
**Response** — `TrashItemDto[]`
```json
[{
  "resourceType": "FOLDER|DOCUMENT",
  "resourceId": "string",
  "name": "string",
  "deletedBy": "string",
  "deletedAt": "..."
}]
```

### `POST /api/trash/{resourceType}/{resourceId}/restore` — 복원

### `DELETE /api/trash/{resourceType}/{resourceId}` — 영구 삭제

---

## 11. 라벨 (Label)

### `GET /api/labels` — 라벨 목록
**Response** — `LabelDto[]`
```json
[{ "labelId": "uuid", "labelName": "string" }]
```

### `GET /api/labels/{labelId}/documents` — 라벨 지정 문서 목록
**Response** — `DocumentDto[]`

### `GET /api/documents/{documentId}/labels` — 문서의 라벨 목록
**Response** — `LabelDto[]`

### `POST /api/documents/{documentId}/labels` — 라벨 추가
**Request** `{ "labelName": "string" }`
**Response** — `LabelDto`

### `DELETE /api/documents/{documentId}/labels/{labelId}` — 라벨 제거

---

## 12. 검색 (Search)

### `POST /search` — 의미 기반 검색
**Request**
```json
{ "query": "string", "topK": 5 }
```
**Response** — `SearchResultDto[]`
```json
[{
  "documentId": "string",
  "title": "string",
  "folderId": "string",
  "excerpt": "string",
  "score": 0.92
}]
```

---

## 13. AI (Document AI)

### `POST /ai/analyze` — 문서 분석 (요약 + 태그 + 임베딩)
**Request** `{ "content": "string (최대 50,000자)" }`

**Response**
```json
{
  "summary": "string",
  "tags": ["string"],
  "embedding": [0.1, 0.2, ...]
}
```

### `POST /ai/summarize` — 요약만
**Request** `{ "content": "string" }`
**Response** `{ "result": "string" }`

### `POST /ai/documents/{documentId}/summarize` — 문서 ID로 요약
**Response** `{ "result": "string" }`

### `POST /ai/tags` — 태그 생성
**Request** `{ "content": "string" }`
**Response** `{ "result": ["tag1", "tag2"] }`

### `POST /ai/embedding` — 임베딩 벡터 생성
**Request** `{ "content": "string" }`
**Response** `{ "result": [0.1, 0.2, ...] }`

### `POST /ai/ask` — Q&A
**Request** `{ "question": "string (최대 2,000자)", "topK": 5 }`

**Response**
```json
{
  "answer": "string",
  "references": [ SearchResultDto ]
}
```

---

## 14. AI 대화방 (AI Conversation)

### `POST /api/ai/conversations` — 대화방 생성
**Request** `{ "title": "string|null" }` (생략 시 '새 대화')

**Response** — `AIConversationDto`
```json
{
  "conversationId": "uuid",
  "title": "string",
  "createTime": "...",
  "updateTime": "..."
}
```

### `GET /api/ai/conversations` — 목록 (페이지네이션)
**Query** `page`, `size`

**Response**
```json
{
  "items": [ AIConversationDto ],
  "page": 0, "size": 20,
  "totalElements": 10, "totalPages": 1, "hasNext": false
}
```

### `GET /api/ai/conversations/{conversationId}` — 상세
**Response** — `AIConversationDto`

### `DELETE /api/ai/conversations/{conversationId}` — 삭제

---

## 15. AI 메시지 (AI Message)

### `POST /api/ai/conversations/{conversationId}/messages` — 메시지 전송
**Request** `{ "content": "string (최대 20,000자)" }`

**Response** — `AIChatResponse`
```json
{
  "userMessage": {
    "messageId": "uuid",
    "conversationId": "uuid",
    "messageSequence": 1,
    "role": "USER",
    "content": "string",
    "status": "COMPLETED",
    "createTime": "...",
    "completeTime": "...",
    "references": null
  },
  "assistantMessage": {
    "messageId": "uuid",
    "conversationId": "uuid",
    "messageSequence": 2,
    "role": "ASSISTANT",
    "content": "string",
    "status": "COMPLETED|PENDING|FAILED",
    "createTime": "...",
    "completeTime": "...|null",
    "references": null
  },
  "references": [ SearchResultDto ]
}
```

### `GET /api/ai/conversations/{conversationId}/messages` — 메시지 기록 (커서 기반)
**Query** `beforeSequence` (선택, 이전 조회의 커서), `size` (기본 20)

**Response**
```json
{
  "items": [ AIMessageDto ],
  "nextBeforeSequence": 5,
  "hasNext": true
}
```

---

## 16. 보안 정책 (Security Policy)

### `GET /api/workspaces/{workspaceId}/security-policy` — 조회
**Response** — `SecurityPolicyDto`
```json
{
  "workspaceId": "uuid",
  "allowSharing": true,
  "allowDownload": true,
  "enforceWatermark": false,
  "auditRetentionDays": 90,
  "trashRetentionDays": 30
}
```

### `PATCH /api/workspaces/{workspaceId}/security-policy` — 변경 _(Admin+)_
**Request** (변경할 항목만 포함, null인 항목은 유지)
```json
{
  "allowSharing": true,
  "allowDownload": false,
  "enforceWatermark": true,
  "auditRetentionDays": 180,
  "trashRetentionDays": 14
}
```
**Response** — `SecurityPolicyDto`

---

## 17. Admin 콘솔 (Admin Console)

> 모든 Admin API는 `Admin` 또는 `Owner` 역할 필요

### `GET /api/workspaces/{workspaceId}/admin/permissions` — 권한 전체 현황
**Response** — `AdminPermissionDto[]`
```json
[{
  "permissionId": "uuid",
  "resourceType": "FOLDER|DOCUMENT",
  "resourceId": "string",
  "resourceName": "string",
  "principalType": "USER|GROUP",
  "principalId": "uuid",
  "principalName": "string",
  "permissionType": "ALLOW|DENY",
  "canDownload": true,
  "createAt": "..."
}]
```

### `GET /api/workspaces/{workspaceId}/admin/shares` — 공유 현황 (자동 소유권 제외)
**Response** — `AdminPermissionDto[]`

### `GET /api/workspaces/{workspaceId}/admin/audit-logs` — 감사 로그
**Query**
- `actorUserId` (uuid, 선택)
- `action` (AuditAction, 선택)
- `result` (AuditResult, 선택)
- `from`, `to` (ISO 8601 DateTime, 선택)

**Response** — `AuditLogDto[]`
```json
[{
  "logId": "uuid",
  "actorUserId": "uuid",
  "actorName": "string",
  "action": "VIEW|DOWNLOAD|EDIT|DELETE|SHARE|RESTORE",
  "resourceType": "FOLDER|DOCUMENT",
  "resourceId": "string",
  "result": "ALLOWED|DENIED",
  "viaAdmin": false,
  "createAt": "..."
}]
```

### `GET /api/workspaces/{workspaceId}/admin/stats` — 통계
**Response** — `WorkspaceStatsDto`
```json
{
  "documentCount": 120,
  "folderCount": 15,
  "trashedDocumentCount": 5,
  "trashedFolderCount": 2,
  "documentCountByUser": { "홍길동": 40, "이순신": 80 }
}
```

### `GET /api/workspaces/{workspaceId}/admin/users` — 구성원 목록
**Response** — `AdminUserDto[]`
```json
[{
  "userId": "uuid",
  "userName": "string",
  "email": "string",
  "role": "OWNER|ADMIN|MEMBER",
  "userStatus": "ACTIVE|INACTIVE|BANNED|WITHDRAW",
  "joinedAt": "...",
  "lastLoginAt": "...|null"
}]
```

### `GET /api/workspaces/{workspaceId}/admin/users/{userId}` — 구성원 상세
**Response** — `AdminUserDto`

### `GET /api/workspaces/{workspaceId}/admin/users/{userId}/access` — 사용자 유효 권한 목록
**Response** — `EffectivePermissionDto[]`
```json
[{
  "resourceType": "FOLDER|DOCUMENT",
  "resourceId": "string",
  "resourceName": "string",
  "access": true,
  "source": "DIRECT|INHERITED|WORKSPACE_DEFAULT",
  "sourceDetail": "string|null",
  "canDownload": true
}]
```

### `GET /api/workspaces/{workspaceId}/admin/permissions/effective` — 리소스 단건 유효 권한 계산
**Query** `userId` (uuid, 필수), `resourceType` (ResourceType, 필수), `resourceId` (string, 필수)

**Response** — `EffectivePermissionDto`

---

## 18. Admin 권한 관리

### `GET /api/workspaces/{workspaceId}/admin/permissions/resources/{resourceType}/{resourceId}` — 리소스 권한 목록 + 폴더 설정
**Response** — `ResourcePermissionsDto`
```json
{
  "permissions": [ AdminPermissionDto ],
  "inheritFromParent": true,
  "baseAccess": "ALLOW|DENY"
}
```
> `inheritFromParent`, `baseAccess`는 `resourceType=FOLDER`일 때만 반환, 문서는 `null`

### `PATCH /api/workspaces/{workspaceId}/admin/permissions/resources/folders/{folderId}/settings` — 폴더 상속 설정 변경
**Request**
```json
{ "inheritFromParent": true, "baseAccess": "ALLOW|DENY" }
```
**Response** — 성공 메시지

### `POST /api/workspaces/{workspaceId}/admin/permissions` — 권한 부여
이미 존재하면 덮어씀

**Request**
```json
{
  "resourceType": "FOLDER|DOCUMENT",
  "resourceId": "string",
  "principalType": "USER|GROUP",
  "principalId": "uuid",
  "permissionType": "ALLOW",
  "canDownload": true
}
```
**Response** — `AdminPermissionDto`

### `PUT /api/workspaces/{workspaceId}/admin/permissions/{permissionId}` — 권한 수정
**Request**
```json
{ "permissionType": "ALLOW|DENY", "canDownload": true }
```
**Response** — `AdminPermissionDto`

### `DELETE /api/workspaces/{workspaceId}/admin/permissions/{permissionId}` — 권한 삭제
**Response** — 성공 메시지

---

## 권한 판정 우선순위 (참고)

1. **워크스페이스 구성원 여부** → 구성원이 아니면 404
2. **OWNER/ADMIN** → 전권 허용
3. **리소스에 직접 부여된 권한** (USER > GROUP)
   - DENY → 즉시 403
   - ALLOW → 허용
4. **상위 폴더 순회** (가까운 폴더 → 먼 폴더)
   - `inheritFromParent=false` 폴더에서 체인 중단
   - DENY → 즉시 403, ALLOW → 허용
5. **Base Access** (가장 가까운 독립 폴더의 `baseAccess`)
6. **워크스페이스 기본** → ALLOW

`my-permission` API의 `source` 필드로 어느 단계에서 판정됐는지 확인 가능.
