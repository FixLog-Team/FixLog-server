# FixLog 풀테스트 시나리오

## 테스트 환경

- Swagger UI: `https://fixlog.art/fixlog/swagger-ui.html`
- Base URL: `https://fixlog.art/fixlog`
- 테스트 전 Google OAuth 로그인으로 accessToken을 발급받으세요.

---

## 사전 준비

- [ ] 서버 정상 기동 확인 (Swagger UI 접속)
- [ ] Google OAuth 로그인 → accessToken 확보
- [ ] `GET /api/workspaces` 로 개인 워크스페이스 ID 확인

---

## 1. 인증 (4건)

- [ ] 1-1. 로그인 후 내 정보 확인 — `GET /auth/token` → 200, email·userName·userId
- [ ] 1-2. 토큰 갱신 — `POST /auth/token/refresh` `{"refreshToken":"..."}` → 새 accessToken
- [ ] 1-3. 토큰 없이 요청 — `GET /api/folders` (Authorization 헤더 제거) → 401
- [ ] 1-4. 잘못된 토큰으로 요청 — 아무 토큰 → 401

---

## 2. 워크스페이스 (8건)

- [ ] 2-1. 목록 조회 — `GET /api/workspaces` → 개인 WS(personal=true) 포함
- [ ] 2-2. 팀 워크스페이스 생성 — `POST /api/workspaces` `{"workspaceName":"테스트팀"}` → role=OWNER
- [ ] 2-3. 상세 조회 — `GET /api/workspaces/{id}` → 이름, role, baseAccess
- [ ] 2-4. 이름 변경 — `PATCH /api/workspaces/{id}` `{"name":"이름변경"}` → 200
- [ ] 2-5. 기본 접근 정책 변경 — `PATCH /api/workspaces/{id}/base-access` `{"baseAccess":"ALLOW"}` → 200
- [ ] 2-6. 멤버 목록 — `GET /api/workspaces/{id}/members` → 본인 OWNER
- [ ] 2-7. 마지막 OWNER 탈퇴 시도 — `POST /api/workspaces/{id}/leave` → 400
- [ ] 2-8. 개인 WS 삭제 시도 — `DELETE /api/workspaces/{개인WS id}` → 400

---

## 3. 폴더 (10건)

> `X-Workspace-Id` 헤더에 개인 워크스페이스 ID를 넣고 테스트

- [ ] 3-1. 루트 폴더 생성 — `POST /api/folders` `{"folderName":"프로젝트A"}` → 200
- [ ] 3-2. 하위 폴더 생성 — `POST /api/folders` `{"folderName":"하위폴더","parentId":"{3-1 ID}"}` → parentId 설정
- [ ] 3-3. 루트 폴더 2개째 — `POST /api/folders` `{"folderName":"프로젝트B"}` → 200
- [ ] 3-4. 폴더 목록 — `GET /api/folders` → 루트 폴더들 + 루트 문서
- [ ] 3-5. 폴더 트리 — `GET /api/folders/tree` → children 계층 구조
- [ ] 3-6. 폴더 상세 — `GET /api/folders/{id}` → 200
- [ ] 3-7. 폴더 내용 — `GET /api/folders/{id}/contents` → 하위 폴더 + 문서
- [ ] 3-8. 폴더 이름 변경 — `PUT /api/folders/{id}` `{"folderName":"이름변경"}` → 200
- [ ] 3-9. 폴더 순서 변경 — `PATCH /api/folders/reorder` `{"parentId":null,"folderIds":["{B}","{A}"]}` → ordinal 변경
- [ ] 3-10. 폴더 이동 — `PATCH /api/folders/{B}/move` `{"targetParentId":"{A}"}` → 200

---

## 4. 문서 (11건)

- [ ] 4-1. 문서 생성 — `POST /api/documents` `{"title":"트러블슈팅 기록","folderId":"{폴더ID}"}` → documentId
- [ ] 4-2. 문서 2개째 생성 — `POST /api/documents` `{"title":"두번째 문서","folderId":"{폴더ID}"}` → 200
- [ ] 4-3. 문서 목록 — `GET /api/documents?page=0&size=10` → 페이지 정보 포함
- [ ] 4-4. 문서 상세 — `GET /api/documents/{id}` → blocks, contentHash
- [ ] 4-5. 문서 저장 — `PUT /api/documents/{id}` `{"title":"트러블슈팅","blocks":[{"type":"paragraph","content":"NPE 해결"}]}` → contentHash 변경
- [ ] 4-6. 제목 변경 — `PATCH /api/documents/{id}/title` `{"title":"NPE 해결기"}` → 200
- [ ] 4-7. 마지막 저장 상태 — `GET /api/documents/{id}/save-state` → lastSavedAt, contentHash
- [ ] 4-8. 문서 복제 — `POST /api/documents/{id}/duplicate` → 새 ID, 제목에 "(1)"
- [ ] 4-9. 문서 순서 변경 — `PATCH /api/documents/reorder` `{"folderId":"{폴더}","documentIds":["{두번째}","{첫번째}"]}` → ordinal 변경
- [ ] 4-10. 문서 이동 — `PATCH /api/documents/{id}/move` `{"targetFolderId":"{다른폴더}"}` → 200
- [ ] 4-11. 문서 다운로드 — `GET /api/documents/{id}/download` → PDF 파일

---

## 5. 문서 히스토리 (4건)

> 4-5에서 저장했으므로 히스토리가 1개 이상 있어야 합니다.

- [ ] 5-1. 히스토리 목록 — `GET /api/documents/{id}/history?page=0&size=10` → 이전 버전 존재
- [ ] 5-2. 히스토리 상세 — `GET /api/documents/{id}/history/{historyId}` → 저장 전 blocks
- [ ] 5-3. 히스토리 복원 — `POST /api/documents/{id}/history/{historyId}/restore` → 200
- [ ] 5-4. 복원 후 확인 — `GET /api/documents/{id}` → 이전 내용으로 돌아감

---

## 6. 라벨 (7건)

- [ ] 6-1. 라벨 붙이기 — `POST /api/documents/{id}/labels` `{"labelName":"NullPointer"}` → labelId
- [ ] 6-2. 다른 문서에 같은 라벨 — 두번째 문서에 `{"labelName":"NullPointer"}` → 같은 labelId
- [ ] 6-3. 문서 라벨 조회 — `GET /api/documents/{id}/labels` → 붙인 라벨
- [ ] 6-4. 전체 라벨 목록 — `GET /api/labels` → WS 내 라벨
- [ ] 6-5. 라벨별 문서 검색 — `GET /api/labels/{labelId}/documents` → 2개 문서
- [ ] 6-6. 라벨 제거 — `DELETE /api/documents/{id}/labels/{labelId}` → 200
- [ ] 6-7. 제거 후 확인 — `GET /api/documents/{id}/labels` → 빈 배열

---

## 7. 휴지통 (6건)

- [ ] 7-1. 문서 삭제 — `DELETE /api/documents/{복제된문서}` → 200
- [ ] 7-2. 휴지통 목록 — `GET /api/trash` → 삭제된 문서 보임
- [ ] 7-3. 복원 — `POST /api/trash/DOCUMENT/{id}/restore` → 200
- [ ] 7-4. 복원 후 확인 — `GET /api/documents?page=0&size=10` → 다시 보임
- [ ] 7-5. 다시 삭제 → 영구 삭제 — `DELETE /api/trash/DOCUMENT/{id}` → 200
- [ ] 7-6. 영구 삭제 후 확인 — `GET /api/trash` → 해당 문서 없음

---

## 8. 공유 & 권한 (12건)

> ⚠️ 유저 2명 필요 (유저A: 소유자, 유저B: 공유 대상)

- [ ] 8-1. [유저B] 유저A 문서 접근 → **403**
- [ ] 8-2. [유저A] 문서 공유 — `POST /api/documents/{id}/permissions` `{"principalType":"USER","principalId":"{유저B}","permissionType":"ALLOW","canDownload":true,"canEdit":false}` → 200
- [ ] 8-3. [유저B] 내 권한 확인 — `GET /api/documents/{id}/my-permission` → access:true, canEdit:false, canDownload:true
- [ ] 8-4. [유저B] 문서 조회 → 200, 내용 보임
- [ ] 8-5. [유저B] 문서 수정 시도 — `PUT /api/documents/{id}` → **403** (canEdit=false)
- [ ] 8-6. [유저B] 공유받은 문서 목록 — `GET /api/documents/shared-with-me` → 유저A 문서 보임
- [ ] 8-7. [유저A] 권한 목록 — `GET /api/documents/{id}/permissions` → 유저B 보임
- [ ] 8-8. [유저A] 공유 해제 — `DELETE /api/documents/{id}/permissions/{permissionId}` → 200
- [ ] 8-9. [유저B] 해제 후 접근 → **403**
- [ ] 8-10. [유저A] 폴더 공유 — `POST /api/folders/{id}/permissions` → 200
- [ ] 8-11. [유저B] 폴더 권한 확인 — `GET /api/folders/{id}/my-permission` → access:true
- [ ] 8-12. [유저B] 폴더 내 문서 접근 → 200 (권한 상속)

---

## 9. 그룹 (10건)

> ⚠️ 유저 2명 필요, 팀 워크스페이스에서 테스트

- [ ] 9-1. 그룹 생성 — `POST /api/workspaces/{ws}/groups` `{"groupName":"백엔드팀"}` → groupId
- [ ] 9-2. 그룹 목록 — `GET /api/workspaces/{ws}/groups` → 백엔드팀
- [ ] 9-3. 그룹에 멤버 추가 — `POST .../groups/{grp}/members` `{"userId":"{유저B}"}` → 200
- [ ] 9-4. 그룹 멤버 목록 — `GET .../groups/{grp}/members` → 유저B
- [ ] 9-5. 그룹에 문서 공유 — `POST /api/documents/{id}/permissions` `{"principalType":"GROUP","principalId":"{grp}","permissionType":"ALLOW"}` → 200
- [ ] 9-6. [유저B] 접근 확인 — `GET /api/documents/{id}/my-permission` → access:true (그룹 경유)
- [ ] 9-7. 그룹 이름 변경 — `PATCH .../groups/{grp}` `{"groupName":"서버팀"}` → 200
- [ ] 9-8. 그룹 멤버 제거 — `DELETE .../groups/{grp}/members/{유저B}` → 200
- [ ] 9-9. [유저B] 제거 후 접근 → **403**
- [ ] 9-10. 그룹 삭제 — `DELETE .../groups/{grp}` → 200

---

## 10. 워크스페이스 초대 (9건)

> ⚠️ 유저 2명 필요, 팀 워크스페이스에서 테스트

- [ ] 10-1. 이메일 초대 — `POST .../admin/invitations` `{"email":"유저B@gmail.com","role":"MEMBER"}` → PENDING
- [ ] 10-2. 초대 목록 — `GET .../admin/invitations` → 보낸 초대
- [ ] 10-3. 중복 초대 — 같은 이메일 → 400
- [ ] 10-4. [유저B] 초대 수락 — `POST /api/workspaces/invitations/{token}/accept` → 멤버 등록
- [ ] 10-5. 수락 후 멤버 목록 — `GET /api/workspaces/{ws}/members` → 유저B MEMBER
- [ ] 10-6. 역할 변경 — `PATCH /api/workspaces/{ws}/members/{유저B}` `{"role":"ADMIN"}` → 200
- [ ] 10-7. 멤버 추방 — `DELETE /api/workspaces/{ws}/members/{유저B}` → 200
- [ ] 10-8. [유저B] 새 초대 후 거절 — `POST .../invitations/{token}/decline` → 200
- [ ] 10-9. 초대 취소(관리자) — `DELETE .../admin/invitations/{invitationId}` → 200

---

## 11. 보안 정책 (5건)

- [ ] 11-1. 보안 정책 조회 — `GET /api/workspaces/{ws}/security-policy` → 기본값
- [ ] 11-2. 다운로드 금지 — `PATCH .../security-policy` `{"allowDownload":false, ...}` → 200
- [ ] 11-3. 다운로드 시도 — `GET /api/documents/{id}/download` → **403**
- [ ] 11-4. 다운로드 다시 허용 — `PATCH` `{"allowDownload":true}` → 200
- [ ] 11-5. 공유 금지 후 공유 시도 — `{"allowSharing":false}` 후 공유 → **403**

---

## 12. 관리자 콘솔 (13건)

> OWNER 또는 ADMIN으로 테스트

- [ ] 12-1. 통계 — `GET .../admin/stats` → 문서/폴더 수
- [ ] 12-2. 사용자 목록 — `GET .../admin/users` → 멤버 목록
- [ ] 12-3. 사용자 상세 — `GET .../admin/users/{userId}` → 역할, 상태, 마지막 로그인
- [ ] 12-4. 사용자 접근 이력 — `GET .../admin/users/{userId}/access` → 200
- [ ] 12-5. 전체 권한 목록 — `GET .../admin/permissions` → 200
- [ ] 12-6. 유효 권한 확인 — `GET .../admin/permissions/effective?userId={id}&resourceType=DOCUMENT&resourceId={docId}` → 판정 결과
- [ ] 12-7. 공유 현황 — `GET .../admin/shares` → 200
- [ ] 12-8. 감사 로그 (전체) — `GET .../admin/audit-logs` → 로그 목록
- [ ] 12-9. 감사 로그 (필터) — `?actorUserId={id}` → 해당 유저만
- [ ] 12-10. 권한 부여 — `POST .../admin/permissions` → 200
- [ ] 12-11. 권한 수정 — `PUT .../admin/permissions/{id}` → 200
- [ ] 12-12. 권한 삭제 — `DELETE .../admin/permissions/{id}` → 200
- [ ] 12-13. [MEMBER] admin API 접근 → **403**

---

## 13. AI 기능 (11건)

- [ ] 13-1. 대화방 생성 — `POST /api/ai/conversations` `{"title":"NPE 질문"}` → conversationId
- [ ] 13-2. 대화방 목록 — `GET /api/ai/conversations?page=0&size=10` → 200
- [ ] 13-3. 대화방 상세 — `GET /api/ai/conversations/{id}` → 200
- [ ] 13-4. 메시지 전송 — `POST .../messages` `{"content":"NullPointerException이 뭐야?"}` → AI 응답 포함
- [ ] 13-5. 메시지 목록 — `GET .../messages?size=20` → USER + ASSISTANT
- [ ] 13-6. 추가 질문 — `{"content":"해결 방법은?"}` → 맥락 유지된 답변
- [ ] 13-7. 대화방 삭제 — `DELETE /api/ai/conversations/{id}` → 200
- [ ] 13-8. 삭제 후 조회 — `GET /api/ai/conversations/{id}` → 404
- [ ] 13-9. 문서 요약 — `POST /ai/documents/{id}/summarize` → 요약 결과
- [ ] 13-10. 질의응답(RAG) — `POST /ai/ask` `{"question":"NPE 해결법","topK":5}` → 문서 기반 답변
- [ ] 13-11. 의미 기반 검색 — `POST /search` `{"query":"NullPointer","topK":5}` → 관련 문서

---

## 14. 정리 (3건)

- [ ] 14-1. 팀 워크스페이스 삭제 — `DELETE /api/workspaces/{팀WS}` → 200
- [ ] 14-2. 삭제 후 목록 — `GET /api/workspaces` → 개인 WS만
- [ ] 14-3. 테스트 폴더 삭제 — `DELETE /api/folders/{id}` → 200

---

## 테스트 결과 요약

| Phase | 항목수 | 통과 | 실패 | 비고 |
|---|---|---|---|---|
| 1. 인증 | 4 | | | |
| 2. 워크스페이스 | 8 | | | |
| 3. 폴더 | 10 | | | |
| 4. 문서 | 11 | | | |
| 5. 히스토리 | 4 | | | |
| 6. 라벨 | 7 | | | |
| 7. 휴지통 | 6 | | | |
| 8. 공유 & 권한 | 12 | | | 유저 2명 |
| 9. 그룹 | 10 | | | 유저 2명 |
| 10. 초대 | 9 | | | 유저 2명 |
| 11. 보안 정책 | 5 | | | |
| 12. 관리자 콘솔 | 13 | | | |
| 13. AI | 11 | | | |
| 14. 정리 | 3 | | | |
| **합계** | **113** | | | |

---

## 발견된 이슈

| # | Phase | 시나리오 | 현상 | 심각도 |
|---|---|---|---|---|
| | | | | |
