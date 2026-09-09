# FixLog API 가이드 (프론트엔드용)

> 최종 업데이트: 2026-09-08
> Base URL (개발): `http://localhost:8080/fixlog`

---

## 목차

1. [공통 규칙](#1-공통-규칙)
2. [인증 플로우](#2-인증-플로우)
3. [인증 API](#3-인증-api)
4. [문서 API](#4-문서-api)
5. [폴더 API](#5-폴더-api)
6. [AI API](#6-ai-api)
7. [검색 API](#7-검색-api)
8. [워크스페이스 API](#8-워크스페이스-api)
9. [공유 API](#9-공유-api)
10. [문서 히스토리 API](#10-문서-히스토리-api)
11. [휴지통 API](#11-휴지통-api)
12. [라벨 API](#12-라벨-api)
13. [관리자 콘솔 API](#13-관리자-콘솔-api)
14. [보안 정책 API](#14-보안-정책-api)
15. [에러 처리](#15-에러-처리)
16. [Swagger UI](#16-swagger-ui)

---

## 1. 공통 규칙

### Base URL

| 환경 | URL |
|------|-----|
| 개발 | `http://localhost:8080/fixlog` |

### 인증 헤더

로그인 후 발급받은 `accessToken`을 모든 인증 필요 요청에 포함합니다.

```
Authorization: Bearer {accessToken}
```

### 워크스페이스 헤더

문서·폴더는 **워크스페이스에 속합니다.** 어느 워크스페이스를 보고 있는지 헤더로 알립니다.

```
X-Workspace-Id: {workspaceId}
```

- **생략하면 개인 워크스페이스로 봅니다.** 기존 코드는 수정 없이 그대로 동작합니다.
- 워크스페이스 목록은 `GET /api/workspaces`에서 얻고, 전환은 이 헤더만 바꾸면 됩니다.
  토큰을 다시 받을 필요가 없습니다.
- 속하지 않은 워크스페이스를 지정하면 `NOT_FOUND`입니다. 존재 여부를 알리지 않기 위함입니다.

### 접근 제어

모든 문서·폴더 접근은 권한 판정을 지납니다. 응답 코드가 두 가지로 갈립니다.

| 상황 | 응답 |
|---|---|
| 워크스페이스 구성원이 아님 | `NOT_FOUND` — 리소스의 존재 자체를 알리지 않습니다 |
| 구성원이지만 권한이 없음 | `FORBIDDEN` |

**목록도 걸러집니다.** `GET /api/documents`, `GET /api/folders/tree` 등은 볼 수 있는 것만
돌려주며, 개수(`totalElements`)에도 잡히지 않습니다.

### 공통 응답 형식

**성공 (데이터 있음)**
```json
{
  "code": "SUCCESS",
  "message": "",
  "result": { ... }
}
```

**성공 (데이터 없음 / 메시지만)**
```json
{
  "code": "SUCCESS",
  "message": "문서가 삭제되었습니다."
}
```

**실패**
```json
{
  "code": "NOT_FOUND",
  "message": "문서를 찾을 수 없습니다."
}
```

### 응답 코드 및 HTTP 상태

| code | HTTP 상태 | 설명 |
|------|-----------|------|
| `SUCCESS` | 200 | 성공 |
| `UNAUTHORIZED` | 401 | 인증 필요 또는 토큰 만료 |
| `FORBIDDEN` | 403 | 권한 부족 (구성원이지만 이 작업을 할 수 없음) |
| `NOT_FOUND` | 404 | 리소스 없음 또는 접근 권한 자체가 없음 |
| `INVALID_REQUEST` | 400 | 잘못된 요청 (유효성 오류 등) |
| `UNKNOWN` | 500 | 서버 내부 오류 |

### 인증 불필요 경로 (Public)

아래 경로는 `Authorization` 헤더 없이 접근 가능합니다.

```
GET  /login
GET  /oauth2/**
POST /auth/token/refresh
GET  /v3/api-docs/**
GET  /swagger-ui/**
GET  /swagger-ui.html
```

---

## 2. 인증 플로우

### Google OAuth 로그인

```
1. 사용자가 로그인 요청
   → 프론트: window.location.href = "http://localhost:8080/fixlog/login"

2. 서버가 Google 로그인 페이지로 리디렉션

3. Google 인증 완료 후 서버가 프론트로 리디렉션
   → {oauth2.success-redirect-url}?accessToken=...&refreshToken=...
   → 개발 기본값: http://localhost:5173/login/callback?accessToken=...&refreshToken=...

4. 프론트에서 토큰을 저장 (localStorage 또는 메모리)
```

### 토큰 만료 처리

- `accessToken` 유효시간: **1시간** (3,600,000ms)
- `refreshToken` 유효시간: **14일** (1,209,600,000ms)
- API 응답이 `401 UNAUTHORIZED`이면 `refreshToken`으로 재발급 후 재시도

---

## 3. 인증 API

### GET /login
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Auth/login)

Google OAuth 로그인 시작 (페이지 이동)

**인증 불필요**

```
응답: 302 Redirect → Google 로그인 페이지
```

---

### POST /auth/token/refresh
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Auth/refreshToken)

액세스 토큰 재발급

**인증 불필요**

**Request Body**
```json
{
  "refreshToken": "eyJhbGci..."
}
```

**Response**
```json
{
  "code": "SUCCESS",
  "message": "",
  "result": {
    "accessToken": "eyJhbGci..."
  }
}
```

---

### GET /auth/token
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Auth/getAuthSession)

현재 로그인 사용자 정보 조회

**인증 필요**

**Response**
```json
{
  "code": "SUCCESS",
  "message": "",
  "result": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "userName": "홍길동",
    "email": "user@gmail.com"
  }
}
```

---

## 4. 문서 API

> 모든 엔드포인트 **인증 필요**

### POST /api/documents
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Document/createDocument)

문서 생성 (빈 문서)

**Request Body**
```json
{
  "folderId": "target-folder-uuid",
  "title": "새 문서"
}
```

> `folderId`가 `null`이면 루트에 생성한다.
> `title`을 생략하거나 비우면 `"제목 없음"`으로 생성된다.
> 생성 직후 `blocks`는 빈 배열(`"[]"`)이다.

**Response**: `DocumentDto`

---

### GET /api/documents
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Document/listDocuments)

문서 목록 조회 (페이지네이션 / 무한스크롤)

**Query Params**

| 파라미터 | 필수 | 기본값 | 설명 |
|---|---|---|---|
| `folderId` | 아니오 | (전체) | 특정 폴더의 문서만 필터. 생략 시 내 전체 문서 |
| `page` | 아니오 | `0` | 0-based 페이지 번호 |
| `size` | 아니오 | `20` | 페이지 크기 |

> 정렬은 `updateTime` 내림차순(최근 수정 순) 고정.

**Response**
```json
{
  "code": "SUCCESS",
  "message": "",
  "result": {
    "items": [ /* DocumentDto 배열 */ ],
    "page": 0,
    "size": 20,
    "totalElements": 42,
    "totalPages": 3,
    "hasNext": true
  }
}
```

---

### GET /api/documents/{documentId}
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Document/getDocument)

문서 조회

**Response**
```json
{
  "code": "SUCCESS",
  "message": "",
  "result": {
    "documentId": "uuid",
    "folderId": "uuid",
    "title": "문서 제목",
    "blocks": "[{\"type\":\"paragraph\",\"data\":{\"text\":\"내용\"}}]",
    "plainText": "내용",
    "contentHash": "sha256hex",
    "ordinal": 0,
    "createUser": "user-uuid",
    "createTime": "2026-05-19T07:00:00Z",
    "updateUser": "user-uuid",
    "updateTime": "2026-05-19T07:00:00Z"
  }
}
```

---

### PUT /api/documents/{documentId}
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Document/saveDocument)

문서 내용 저장

**Request Body**
```json
{
  "title": "문서 제목",
  "blocks": [
    { "type": "header", "data": { "text": "제목", "level": 1 } },
    { "type": "paragraph", "data": { "text": "본문 내용" } },
    { "type": "list", "data": { "items": ["항목1", "항목2"] } },
    { "type": "code", "data": { "code": "console.log('hello')" } }
  ]
}
```

> `blocks` 허용 type: `paragraph`, `header`, `list`, `code`, `image`, `table`

**Response**: `DocumentDto` (위와 동일)

---

### PATCH /api/documents/{documentId}/title
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Document/updateDocumentTitle)

문서 제목만 변경

**Request Body**
```json
{
  "title": "새 제목"
}
```

**Response**: `DocumentDto`

---

### GET /api/documents/{documentId}/save-state
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Document/getDocumentSaveState)

마지막 저장 상태 조회 (자동저장 확인용)

**Response**
```json
{
  "code": "SUCCESS",
  "message": "",
  "result": {
    "lastSavedAt": "2026-05-19T07:00:00Z",
    "contentHash": "sha256hex"
  }
}
```

---

### POST /api/documents/{documentId}/duplicate
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Document/duplicateDocument)

문서 복제

**Response**
```json
{
  "code": "SUCCESS",
  "message": "",
  "result": {
    "newDocumentId": "new-uuid",
    "folderId": "uuid",
    "title": "원본 제목 (1)"
  }
}
```

---

### DELETE /api/documents/{documentId}
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Document/deleteDocument)

문서 삭제 (소프트 삭제 — 복구 가능)

**Response**
```json
{
  "code": "SUCCESS",
  "message": "문서가 삭제되었습니다."
}
```

---

### PATCH /api/documents/{documentId}/move
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Document/moveDocument)

문서 폴더 이동

**Request Body**
```json
{
  "folderId": "target-folder-uuid"
}
```

> `folderId`를 `null`로 보내면 루트로 이동
> 이동한 문서는 대상 폴더의 **맨 뒤** 순번을 새로 받는다.

**Response**: `DocumentDto`

---

### PATCH /api/documents/reorder
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Document/reorderDocuments)

같은 폴더 안 문서 순서 변경 (드래그 앤 드롭)

**Request Body**
```json
{
  "folderId": "folder-uuid",
  "documentIds": ["uuid-3", "uuid-1", "uuid-2"]
}
```

> `folderId`가 `null`이면 루트 문서들이 대상이다.
> `documentIds`는 **해당 폴더의 활성 문서 전체**를 새 순서대로 빠짐없이 담아야 한다.
> 일부만 보내거나, 중복이 있거나, 다른 폴더의 문서가 섞이면 `INVALID_REQUEST`로 거부된다.
> 순서 변경은 `updateTime`을 갱신하지 않으므로 `GET /api/documents`의 최신순 정렬에 영향을 주지 않는다.

**Response**: `DocumentDto[]` (새 순서대로, `ordinal`은 0부터 재부여됨)

---

### GET /api/documents/{documentId}/download
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Document/downloadDocument)

문서 PDF 다운로드

**Response**: `application/pdf` 바이너리
```
Content-Disposition: attachment; filename*=UTF-8''문서제목.pdf
```

---

## 5. 폴더 API

> 모든 엔드포인트 **인증 필요**

### GET /api/folders
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Folder/getRootContents)

루트 콘텐츠 조회 (루트 폴더 + 루트 문서 목록). 소유자는 로그인 사용자로 자동 결정된다.

**Response**
```json
{
  "code": "SUCCESS",
  "message": "",
  "result": {
    "folders": [
      {
        "folderId": "uuid",
        "parentId": null,
        "folderName": "프로젝트",
        "ordinal": 0,
        "createUser": "user-uuid",
        "createTime": "2026-05-19T07:00:00Z",
        "updateUser": "user-uuid",
        "updateTime": "2026-05-19T07:00:00Z"
      }
    ],
    "documents": []
  }
}
```

---

### GET /api/folders/{folderId}/contents
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Folder/getFolderContents)

폴더 콘텐츠 조회 (하위 폴더 + 문서 목록)

**Response**: 위와 동일한 `FolderContentsDto` 형식

> `folders`와 `documents`는 각각 `ordinal` 오름차순(동률이면 생성순)으로 정렬되어 내려온다.
> 두 목록은 **서로 독립된 순번 공간**을 쓴다. 사이드바에서는 폴더를 먼저, 문서를 그다음에 렌더링하면 된다.

> 사이드바 폴더 트리는 이 엔드포인트를 재귀 호출하지 말고 `GET /api/folders/tree`를 한 번 호출한다.
> 이 엔드포인트는 특정 폴더를 열었을 때 그 안의 문서 목록까지 함께 받는 용도다.

---

### GET /api/folders/tree
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Folder/getFolderTree)

전체 폴더 트리 조회 (사이드바용). 한 번의 요청으로 모든 폴더를 중첩 구조로 받는다.

**Response**
```json
{
  "code": "SUCCESS",
  "message": "",
  "result": [
    {
      "folderId": "uuid",
      "parentId": null,
      "folderName": "Engineering",
      "ordinal": 0,
      "documentCount": 42,
      "children": [
        {
          "folderId": "uuid",
          "parentId": "부모-uuid",
          "folderName": "Backend",
          "ordinal": 0,
          "documentCount": 7,
          "children": []
        }
      ]
    }
  ]
}
```

> `result`는 **루트 폴더 배열**이다. 각 노드의 `children`은 같은 형태로 중첩된다.
> 형제 노드는 `ordinal` 오름차순(동률이면 생성순)으로 정렬되어 내려온다.
> `documentCount`는 **직속 문서 수**다. 하위 폴더 안의 문서는 합산하지 않는다.
> 루트에 바로 놓인 문서(`folderId`가 `null`)는 어떤 폴더의 `documentCount`에도 포함되지 않는다.

---

### GET /api/folders/{folderId}
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Folder/getFolder)

특정 폴더 정보 조회

**Response**: `FolderDto`

---

### POST /api/folders
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Folder/createFolder)

폴더 생성

**Request Body**
```json
{
  "parentId": "parent-folder-uuid",
  "folderName": "새 폴더"
}
```

> `parentId`가 `null`이면 루트에 생성
> `ordinal`은 서버가 형제들의 맨 뒤 순번으로 자동 부여한다.

**Response**: `FolderDto`

---

### PUT /api/folders/{folderId}
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Folder/updateFolder)

폴더 이름 변경

**Request Body**
```json
{
  "folderName": "변경된 폴더명"
}
```

**Response**: `FolderDto`

---

### PATCH /api/folders/reorder
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Folder/reorderFolders)

같은 부모 아래 폴더 순서 변경 (드래그 앤 드롭)

**Request Body**
```json
{
  "parentId": "parent-folder-uuid",
  "folderIds": ["uuid-c", "uuid-a", "uuid-b"]
}
```

> `parentId`가 `null`이면 루트 폴더들이 대상이다.
> `folderIds`는 **해당 부모의 활성 폴더 전체**를 새 순서대로 빠짐없이 담아야 한다.

**Response**: `FolderDto[]` (새 순서대로, `ordinal`은 0부터 재부여됨)

---

### PATCH /api/folders/{folderId}/move
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Folder/moveFolder)

폴더 이동 (부모 폴더 변경)

**Request Body**
```json
{
  "parentId": "target-parent-folder-uuid"
}
```

> `parentId`를 `null`로 보내면 루트로 이동한다.
> 자기 자신 또는 자신의 하위 폴더로 이동하면 `INVALID_REQUEST`로 거부된다(순환 방지).

**Response**: `FolderDto`

---

### DELETE /api/folders/{folderId}
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Folder/deleteFolder)

폴더 삭제 (소프트 삭제)

> 하위 폴더와 폴더 안의 모든 문서가 **함께 소프트 삭제**된다(캐스케이드).

**Response**
```json
{
  "code": "SUCCESS",
  "message": "폴더가 삭제되었습니다."
}
```

---

## 6. AI API

> 모든 엔드포인트 **인증 필요**
> AI 모델: Google Gemini (Chat), OpenAI (Embedding)

### POST /ai/analyze
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/AI/aiAnalyze)

문서 분석 (요약 + 태그 + 임베딩 한 번에). content를 직접 body로 전달.

**Request Body**
```json
{
  "content": "분석할 문서 내용 (최대 50,000자)"
}
```

**Response**
```json
{
  "code": "SUCCESS",
  "message": "문서 분석이 완료되었습니다.",
  "result": {
    "summary": "요약된 내용",
    "tags": ["태그1", "태그2"],
    "embedding": [0.123, -0.456]
  }
}
```

---

### POST /ai/summarize
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/AI/aiSummarize)

문서 요약. content를 직접 body로 전달.

**Request Body**
```json
{ "content": "요약할 내용 (최대 50,000자)" }
```

**Response**
```json
{
  "code": "SUCCESS",
  "message": "요약이 완료되었습니다.",
  "result": "요약된 텍스트"
}
```

---

### POST /ai/documents/{documentId}/summarize
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/AI/aiSummarizeById)

문서 ID 기반 요약. 서버가 DB에서 문서 원문(`plainText`)을 조회해 요약한다.
권한 있는 문서만 대상이며, 요청 body는 없다.

**Response**
```json
{
  "code": "SUCCESS",
  "message": "요약이 완료되었습니다.",
  "result": "요약된 텍스트"
}
```

---

### POST /ai/tags
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/AI/aiGenerateTags)

태그 자동 생성 (5개, content를 직접 body로 전달)

**Request Body**
```json
{ "content": "분석할 문서 내용 (최대 50,000자)" }
```

**Response**
```json
{
  "code": "SUCCESS",
  "message": "태그 생성이 완료되었습니다.",
  "result": ["Spring Boot", "JWT", "OAuth2"]
}
```

> **이 응답은 제안일 뿐 문서에 저장되지 않습니다.** 라벨로 붙이려면 사용자가 고른 것만
> `POST /api/documents/{documentId}/labels`로 보냅니다 (→ [라벨 API](#12-라벨-api)).

---

### POST /ai/embedding
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/AI/aiGenerateEmbedding)

임베딩 벡터 생성

**Request Body**
```json
{ "content": "임베딩할 내용 (최대 50,000자)" }
```

**Response**
```json
{
  "code": "SUCCESS",
  "message": "임베딩 생성이 완료되었습니다.",
  "result": [0.123, -0.456, 0.789]
}
```

---

### POST /ai/ask
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/AI/aiAsk)

질문 기반 AI 답변 생성 (RAG). 접근 가능한 문서 중 질문과 유사한 문서를 검색해 그 내용을 근거로 답변을 생성하고, 참고한 문서 정보를 함께 반환한다.

**Request Body**

| 필드 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| question | String | 예 | - | 질문 (`@NotBlank`, 최대 2,000자) |
| topK | Integer | 아니오 | 5 | 참고할 문서 개수 (1~20) |

```json
{
  "question": "스프링 시큐리티 401 에러가 계속 나는데 원인이 뭐였지?",
  "topK": 5
}
```

**Response**
```json
{
  "code": "SUCCESS",
  "message": "답변 생성이 완료되었습니다.",
  "result": {
    "answer": "문서 1을 참고하면, 401 에러의 원인은 ... 이었습니다.",
    "references": [
      {
        "documentId": "uuid",
        "title": "문서 제목",
        "folderId": "uuid",
        "excerpt": "관련 문장 발췌...",
        "score": 0.87
      }
    ]
  }
}
```

---

## 7. 검색 API

> 모든 엔드포인트 **인증 필요**

### POST /search
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Search/search)

의미 기반(semantic) 문서 검색. 검색 문장을 임베딩한 뒤 벡터 유사도로 접근 가능한 문서 중 상위 N개를 반환한다.

**Request Body**

| 필드 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| query | String | 예 | - | 검색 문장 (`@NotBlank`) |
| topK | Integer | 아니오 | 5 | 반환 개수 (1~20) |

```json
{
  "query": "스프링 시큐리티 401 에러 원인",
  "topK": 5
}
```

**Response**
```json
{
  "code": "SUCCESS",
  "message": "검색이 완료되었습니다.",
  "result": [
    {
      "documentId": "uuid",
      "title": "문서 제목",
      "folderId": "uuid",
      "excerpt": "관련 문장 발췌...",
      "score": 0.87
    }
  ]
}
```

---

## 8. 워크스페이스 API

### GET /api/workspaces
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Workspace/listWorkspaces)

내가 속한 워크스페이스 목록. 여기서 고른 `workspaceId`를 `X-Workspace-Id` 헤더로 보냅니다.

```json
{
  "code": "SUCCESS",
  "result": [
    { "workspaceId": "...", "workspaceName": "홍길동의 워크스페이스", "personal": true, "role": "ADMIN" },
    { "workspaceId": "...", "workspaceName": "OS 스터디", "personal": false, "role": "MEMBER" }
  ]
}
```

가입하면 개인 워크스페이스가 자동으로 생깁니다. 역할은 워크스페이스마다 다릅니다.

---

### POST /api/workspaces
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Workspace/createWorkspace)

워크스페이스 생성

```json
{ "workspaceName": "OS 스터디" }
```

만든 사람이 `ADMIN`이 됩니다.

---

### GET /api/workspaces/{workspaceId}
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Workspace/getWorkspace)

워크스페이스 단건 조회. 구성원만 가능, 비구성원은 `NOT_FOUND`.

**Response**
```json
{
  "code": "SUCCESS",
  "result": {
    "workspaceId": "...",
    "workspaceName": "OS 스터디",
    "personal": false,
    "role": "MEMBER"
  }
}
```

---

### PATCH /api/workspaces/{workspaceId}
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Workspace/renameWorkspace)

워크스페이스 이름 변경. **Admin만.**

```json
{ "name": "새 워크스페이스 이름" }
```

**Response**: `WorkspaceDto`

---

### DELETE /api/workspaces/{workspaceId}
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Workspace/deleteWorkspace)

워크스페이스 삭제. **Admin만. 개인 워크스페이스는 삭제 불가.**

내부 폴더·문서 소프트 삭제, 구성원·초대·권한은 하드 삭제됩니다.

**Response**
```json
{ "code": "SUCCESS", "message": "워크스페이스를 삭제했습니다." }
```

---

### GET /api/workspaces/{workspaceId}/members
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Workspace/listWorkspaceMembers)

구성원 목록 (`userId`, `userName`, `email`, `role`, `joinedAt`).

---

### POST /api/workspaces/{workspaceId}/members
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Workspace/inviteWorkspaceMember)

이미 가입한 사용자를 이메일로 초대합니다. **Admin만.**

```json
{ "email": "friend@fixlog.dev" }
```

| 상황 | 응답 |
|---|---|
| 가입하지 않은 이메일 | `NOT_FOUND` |
| 이미 속한 사용자 | `INVALID_REQUEST` |
| 개인 워크스페이스 | `INVALID_REQUEST` |

---

### PATCH /api/workspaces/{workspaceId}/members/{userId}
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Workspace/changeWorkspaceMemberRole)

역할 변경. **Admin만.** `{ "role": "ADMIN" }`

---

### DELETE /api/workspaces/{workspaceId}/members/{userId}
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Workspace/removeWorkspaceMember)

구성원 제거. **Admin만.**

---

### POST /api/workspaces/{workspaceId}/leave
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Workspace/leaveWorkspace)

스스로 나가기.

> **마지막 관리자는 강등·제거·탈퇴할 수 없습니다** (`INVALID_REQUEST`).

---

### 초대 링크 흐름

관리자가 `POST .../admin/invitations`로 초대하면 서버가 이메일로 토큰을 전송합니다.
수신자는 이메일 링크를 클릭해 수락/거절합니다 — 인증된 사용자만 수락할 수 있습니다.

| 메서드 | 경로 | 설명 | Swagger |
|---|---|---|---|
| `GET` | `/api/workspaces/{workspaceId}/admin/invitations` | 초대 목록 (Admin) | [→](http://localhost:8080/fixlog/swagger-ui.html#/Admin/adminListInvitations) |
| `POST` | `/api/workspaces/{workspaceId}/admin/invitations` | 초대 발송 (Admin) | [→](http://localhost:8080/fixlog/swagger-ui.html#/Admin/adminInvite) |
| `DELETE` | `/api/workspaces/{workspaceId}/admin/invitations/{invitationId}` | 초대 취소 (Admin) | [→](http://localhost:8080/fixlog/swagger-ui.html#/Admin/adminCancelInvitation) |
| `POST` | `/api/workspaces/invitations/{token}/accept` | 초대 수락 (인증 필요) | [→](http://localhost:8080/fixlog/swagger-ui.html#/Invitation/acceptInvitation) |
| `POST` | `/api/workspaces/invitations/{token}/decline` | 초대 거절 | [→](http://localhost:8080/fixlog/swagger-ui.html#/Invitation/declineInvitation) |

초대 발송 Body:
```json
{ "email": "friend@fixlog.dev", "role": "MEMBER" }
```

> `role`을 생략하면 `MEMBER`로 초대됩니다.

초대 응답 (`InvitationDto`):
```json
{
  "id": "uuid",
  "workspaceId": "uuid",
  "email": "friend@fixlog.dev",
  "role": "MEMBER",
  "status": "PENDING",
  "expiresAt": "2026-09-15T00:00:00Z",
  "createAt": "2026-09-08T00:00:00Z",
  "invitedByName": "홍길동"
}
```

---

### 그룹

권한을 사람마다 주지 않고 묶음에 줄 때 씁니다. 그룹은 워크스페이스를 넘지 않습니다.

| 메서드 | 경로 | 비고 | Swagger |
|---|---|---|---|
| `GET` | `/api/workspaces/{workspaceId}/groups` | 구성원이면 조회 가능 | [→](http://localhost:8080/fixlog/swagger-ui.html#/Workspace/listGroups) |
| `POST` | `/api/workspaces/{workspaceId}/groups` | Admin만. `{ "groupName": "백엔드 파트" }` | [→](http://localhost:8080/fixlog/swagger-ui.html#/Workspace/createGroup) |
| `PATCH` | `/api/workspaces/{workspaceId}/groups/{groupId}` | Admin만 | [→](http://localhost:8080/fixlog/swagger-ui.html#/Workspace/renameGroup) |
| `DELETE` | `/api/workspaces/{workspaceId}/groups/{groupId}` | Admin만 | [→](http://localhost:8080/fixlog/swagger-ui.html#/Workspace/deleteGroup) |
| `GET` | `/api/workspaces/{workspaceId}/groups/{groupId}/members` | | [→](http://localhost:8080/fixlog/swagger-ui.html#/Workspace/listGroupMembers) |
| `POST` | `/api/workspaces/{workspaceId}/groups/{groupId}/members` | Admin만. `{ "userId": "..." }` | [→](http://localhost:8080/fixlog/swagger-ui.html#/Workspace/addGroupMember) |
| `DELETE` | `/api/workspaces/{workspaceId}/groups/{groupId}/members/{userId}` | Admin만 | [→](http://localhost:8080/fixlog/swagger-ui.html#/Workspace/removeGroupMember) |

---

## 9. 공유 API

**공유는 권한을 부여하는 일입니다.** 폴더와 문서가 같은 규칙을 씁니다.

### 권한 모델

권한 타입은 `ALLOW` / `DENY` 두 가지입니다. 다운로드는 타입과 별개로 `canDownload`로 제어합니다.

| permissionType | 접근 |
|---|---|
| `ALLOW` | 조회·편집 허용 |
| `DENY` | 명시적 차단 (상속된 ALLOW보다 우선) |

**우선순위**: 직접 부여된 권한 > 가까운 폴더 상속 > 먼 폴더 상속
사용자에게 직접 부여된 권한은 그룹 권한보다 우선합니다.

---

### GET /api/documents/{documentId}/my-permission
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Share/myDocumentPermission)

내가 이 문서에 대해 갖는 유효 권한과 출처. 다운로드 버튼·편집 버튼 표시 여부 결정에 사용합니다.

**Response**
```json
{
  "code": "SUCCESS",
  "result": {
    "access": true,
    "canDownload": true,
    "source": "DIRECT",
    "sourceDetail": "직접 부여"
  }
}
```

| source | 설명 |
|---|---|
| `DIRECT` | 이 리소스에 직접 부여됨 |
| `INHERITED` | 상위 폴더에서 상속됨 |
| `WORKSPACE_DEFAULT` | 워크스페이스 기본값 (Admin) |

---

### GET /api/folders/{folderId}/my-permission
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Share/myFolderPermission)

내가 이 폴더에 대해 갖는 유효 권한. 응답 형식은 위와 동일.

---

### POST /api/documents/{documentId}/permissions
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Share/shareDocument)

문서 권한 부여. 이메일로 사용자에게 줄 때:

```json
{ "email": "friend@fixlog.dev", "permissionType": "ALLOW", "canDownload": false }
```

그룹에 줄 때:

```json
{ "principalType": "GROUP", "principalId": "{groupId}", "permissionType": "ALLOW" }
```

- **공유 설정은 소유자(리소스 생성자)와 워크스페이스 Admin만** 가능합니다 (`FORBIDDEN`).
- 대상은 **같은 워크스페이스의 구성원·그룹**만 가능합니다 (`NOT_FOUND`).
- 같은 대상에 다시 보내면 **덮어씁니다.** 중복 레코드는 생기지 않습니다.
- `permissionType`을 생략하면 `ALLOW`, `canDownload`를 생략하면 `true`입니다.

---

### GET /api/documents/{documentId}/permissions
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Share/listDocumentPermissions)

누구에게 공유돼 있는지. 소유자와 Admin만 볼 수 있습니다.

---

### DELETE /api/documents/{documentId}/permissions/{permissionId}
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Share/revokeDocumentPermission)

공유 회수. 대상자의 접근이 즉시 끊깁니다.

---

### 폴더 공유

경로만 다르고 규칙은 같습니다.

```
GET    /api/folders/{folderId}/permissions
POST   /api/folders/{folderId}/permissions
DELETE /api/folders/{folderId}/permissions/{permissionId}
```

Swagger:
- [GET listFolderPermissions](http://localhost:8080/fixlog/swagger-ui.html#/Share/listFolderPermissions)
- [POST shareFolder](http://localhost:8080/fixlog/swagger-ui.html#/Share/shareFolder)
- [DELETE revokeFolderPermission](http://localhost:8080/fixlog/swagger-ui.html#/Share/revokeFolderPermission)

**폴더 권한은 하위 폴더·문서로 상속됩니다.** 폴더를 공유하면 그 안의 문서까지 열립니다.
문서에 직접 준 권한이 상속보다 우선하고, 가까운 폴더가 먼 폴더를 이깁니다.

---

### GET /api/documents/shared-with-me
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Share/sharedWithMe)

내가 만들지 않았지만 권한을 받은 문서.

---

## 10. 문서 히스토리 API

저장할 때마다 히스토리가 쌓입니다. **내용이 직전과 같으면 만들지 않습니다** — 자동저장이
의미 없는 히스토리를 쌓지 않게 하기 위함입니다.

### GET /api/documents/{documentId}/history
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Document/listDocumentHistory)

히스토리 목록. 최신순. 본문은 빼고 언제 누가 저장했는지만 내려갑니다.

```json
{
  "result": [
    {
      "historyId": "uuid",
      "title": "...",
      "source": "SAVE",
      "restoredFromId": null,
      "createUser": "user-uuid",
      "createAt": "2026-09-08T07:00:00Z"
    }
  ]
}
```

| source | 설명 |
|---|---|
| `SAVE` | 일반 저장 |
| `RESTORE` | 되돌리기로 만들어진 히스토리 |

---

### GET /api/documents/{documentId}/history/{historyId}
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Document/getDocumentHistory)

본문까지 포함한 상세.

---

### POST /api/documents/{documentId}/history/{historyId}/restore
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Document/restoreDocumentHistory)

해당 시점으로 되돌립니다. **편집 권한이 필요합니다.**

> **되돌린 결과도 새 히스토리로 쌓입니다.** 과거를 지우지 않으므로 되돌리기를 다시
> 되돌릴 수 있습니다.

---

## 11. 휴지통 API

삭제는 지우는 것이 아니라 휴지통으로 보내는 것입니다.

### GET /api/trash
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Trash/listTrash)

폴더와 문서를 한 목록에 섞어 최근 삭제순으로 돌려줍니다.

```json
{
  "result": [
    {
      "resourceType": "DOCUMENT",
      "resourceId": "...",
      "name": "지운 문서",
      "deletedBy": "user-uuid",
      "deletedAt": "2026-09-08T07:00:00Z"
    }
  ]
}
```

> **지운 본인과 워크스페이스 Admin만** 보고 되돌릴 수 있습니다.

---

### POST /api/trash/{resourceType}/{resourceId}/restore
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Trash/restoreFromTrash)

`resourceType`은 `DOCUMENT` 또는 `FOLDER`.

**부모 폴더가 아직 휴지통에 있으면 루트로 복원됩니다.** 삭제된 폴더 안으로 되살리면
트리에서 보이지 않는 항목이 되기 때문입니다.

---

### DELETE /api/trash/{resourceType}/{resourceId}
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Trash/purgeFromTrash)

영구 삭제. 문서는 본문·히스토리·라벨·권한이 함께 사라집니다. **되돌릴 수 없습니다.**

---

## 12. 라벨 API

| 메서드 | 경로 | 비고 | Swagger |
|---|---|---|---|
| `GET` | `/api/labels` | 워크스페이스의 라벨 목록 | [→](http://localhost:8080/fixlog/swagger-ui.html#/Trash/listLabels) |
| `GET` | `/api/labels/{labelId}/documents` | 라벨이 붙은 문서. **권한 있는 것만** 나옵니다 | [→](http://localhost:8080/fixlog/swagger-ui.html#/Trash/listDocumentsWithLabel) |
| `GET` | `/api/documents/{documentId}/labels` | 문서에 붙은 라벨 | [→](http://localhost:8080/fixlog/swagger-ui.html#/Trash/listDocumentLabels) |
| `POST` | `/api/documents/{documentId}/labels` | `{ "labelName": "spring" }`. 편집 권한 필요 | [→](http://localhost:8080/fixlog/swagger-ui.html#/Trash/attachLabel) |
| `DELETE` | `/api/documents/{documentId}/labels/{labelId}` | 편집 권한 필요 | [→](http://localhost:8080/fixlog/swagger-ui.html#/Trash/detachLabel) |

같은 이름의 라벨은 워크스페이스에서 하나로 공유되며, 없는 이름을 붙이면 그때 만들어집니다.

### AI 태그를 라벨로 붙이기

`POST /ai/tags`가 돌려주는 태그는 **제안**입니다. 서버는 제안을 저장하지 않으므로 흐름은
이렇습니다.

```
1. POST /ai/tags                          → ["Spring Boot", "JWT", "OAuth2"]
2. 화면에서 사용자에게 보여주고 고르게 함     (수락 전 목록은 프론트가 들고 있음)
3. 고른 것만 하나씩
   POST /api/documents/{documentId}/labels  { "labelName": "Spring Boot" }
```

---

## 13. 관리자 콘솔 API

전부 **워크스페이스 Admin만** 호출할 수 있습니다 (`FORBIDDEN`).
경로에 워크스페이스가 항상 들어가며, 워크스페이스를 가로지르는 조회는 제공하지 않습니다.

### 현황 조회

| 메서드 | 경로 | 내용 | Swagger |
|---|---|---|---|
| `GET` | `/api/workspaces/{id}/admin/permissions` | 권한 현황 (주체·리소스 이름 포함) | [→](http://localhost:8080/fixlog/swagger-ui.html#/Admin/adminListPermissions) |
| `GET` | `/api/workspaces/{id}/admin/shares` | 공유 현황. 생성자 소유 권한은 빠집니다 | [→](http://localhost:8080/fixlog/swagger-ui.html#/Admin/adminListShares) |
| `GET` | `/api/workspaces/{id}/admin/audit-logs` | 감사 로그 | [→](http://localhost:8080/fixlog/swagger-ui.html#/Admin/adminListAuditLogs) |
| `GET` | `/api/workspaces/{id}/admin/stats` | 문서·폴더 총량, 휴지통 현황, 사용자별 분포 | [→](http://localhost:8080/fixlog/swagger-ui.html#/Admin/adminGetStats) |
| `GET` | `/api/workspaces/{id}/admin/users` | 구성원 상세 목록 | [→](http://localhost:8080/fixlog/swagger-ui.html#/Admin/adminListUsers) |
| `GET` | `/api/workspaces/{id}/admin/users/{userId}` | 특정 구성원 상세 | [→](http://localhost:8080/fixlog/swagger-ui.html#/Admin/adminGetUser) |
| `GET` | `/api/workspaces/{id}/admin/users/{userId}/access` | 해당 사용자가 접근 가능한 리소스 목록 | [→](http://localhost:8080/fixlog/swagger-ui.html#/Admin/adminGetUserAccess) |

---

### 감사 로그 필터

```
GET /api/workspaces/{id}/admin/audit-logs?actorUserId=...&action=VIEW&result=DENIED
                                          &from=2026-08-01T00:00:00Z&to=2026-09-01T00:00:00Z
```

| 파라미터 | 값 |
|---|---|
| `action` | `VIEW` `DOWNLOAD` `EDIT` `DELETE` `SHARE` `RESTORE` |
| `result` | `ALLOWED` `DENIED` |

**거부된 접근도 남습니다.** `viaAdmin`이 `true`면 관리자 특권으로 접근한 것입니다.

---

### 유효 권한 조회

```
GET /api/workspaces/{id}/admin/permissions/effective
    ?userId={userId}&resourceType=DOCUMENT&resourceId={documentId}
```

> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Admin/adminGetEffectivePermission)

특정 사용자가 특정 리소스에 대해 갖는 유효 권한을 계산해서 반환합니다.

---

### 리소스별 권한 목록

```
GET /api/workspaces/{id}/admin/permissions/resources/{resourceType}/{resourceId}
```

> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Admin/adminListPermissionsForResource)

특정 리소스에 설정된 모든 권한 레코드 목록.

---

### 권한 직접 부여·수정·회수 (Admin)

Admin은 공유 설정 없이도 직접 권한을 조작할 수 있습니다.

**POST** `/api/workspaces/{id}/admin/permissions` — [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Admin/adminGrantPermission)
```json
{
  "resourceType": "DOCUMENT",
  "resourceId": "doc-uuid",
  "principalType": "USER",
  "principalId": "user-uuid",
  "permissionType": "ALLOW",
  "canDownload": true
}
```

**PUT** `/api/workspaces/{id}/admin/permissions/{permissionId}` — [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Admin/adminUpdatePermission)
```json
{ "permissionType": "DENY", "canDownload": false }
```

**DELETE** `/api/workspaces/{id}/admin/permissions/{permissionId}` — [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Admin/adminDeletePermission)

---

### 폴더 상속 설정

```
PATCH /api/workspaces/{id}/admin/permissions/resources/folders/{folderId}/settings
```

> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/Admin/adminUpdateFolderSettings)

```json
{
  "inheritFromParent": true,
  "baseAccess": "ALLOW"
}
```

| 필드 | 설명 |
|---|---|
| `inheritFromParent` | `true`면 부모 폴더의 권한을 상속합니다 (기본값 `true`) |
| `baseAccess` | 직접 부여된 권한이 없는 워크스페이스 구성원에게 적용되는 기본 접근. `ALLOW` 또는 `DENY` (기본값 `ALLOW`) |

---

### 초대 관리 (Admin)

| 메서드 | 경로 | 설명 | Swagger |
|---|---|---|---|
| `GET` | `/api/workspaces/{id}/admin/invitations` | 초대 목록 (PENDING/ACCEPTED/DECLINED/EXPIRED) | [→](http://localhost:8080/fixlog/swagger-ui.html#/Admin/adminListInvitations) |
| `POST` | `/api/workspaces/{id}/admin/invitations` | 초대 발송. `{ "email": "...", "role": "MEMBER" }` | [→](http://localhost:8080/fixlog/swagger-ui.html#/Admin/adminInvite) |
| `DELETE` | `/api/workspaces/{id}/admin/invitations/{invitationId}` | 초대 취소 | [→](http://localhost:8080/fixlog/swagger-ui.html#/Admin/adminCancelInvitation) |

---

## 14. 보안 정책 API

### GET /api/workspaces/{workspaceId}/security-policy
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/SecurityPolicy/getSecurityPolicy)

구성원이면 볼 수 있습니다. 자기에게 어떤 제약이 걸려 있는지는 알아야 하기 때문입니다.

```json
{
  "result": {
    "allowSharing": true,
    "allowDownload": true,
    "enforceWatermark": false,
    "auditRetentionDays": 365,
    "trashRetentionDays": 30
  }
}
```

---

### PATCH /api/workspaces/{workspaceId}/security-policy
> [Swagger →](http://localhost:8080/fixlog/swagger-ui.html#/SecurityPolicy/updateSecurityPolicy)

**Admin만.** 넘기지 않은 항목은 그대로 둡니다.

```json
{ "allowDownload": false, "enforceWatermark": true }
```

> **정책은 개별 권한보다 위에 있습니다.** 문서에 `canDownload: true` 권한이 있어도
> 정책이 다운로드를 막으면 막힙니다. **Admin에게도 적용됩니다.**

`enforceWatermark`가 켜지면 PDF에 내려받는 사람이 각인됩니다.

---

## 15. 에러 처리

### 401 처리 흐름 (토큰 재발급)

```
API 호출
  → 401 응답 수신
  → POST /auth/token/refresh { refreshToken }
    → 성공: 새 accessToken 저장 후 원래 요청 재시도
    → 실패 (refreshToken도 만료): 로그인 페이지로 이동
```

### 주요 에러 케이스

| 상황 | code | HTTP |
|------|------|------|
| 토큰 없음 / 만료 | `UNAUTHORIZED` | 401 |
| 문서/폴더 없거나 워크스페이스 구성원이 아님 | `NOT_FOUND` | 404 |
| 구성원이지만 해당 작업 권한이 없음 | `FORBIDDEN` | 403 |
| 정책이 다운로드·공유를 금지 | `FORBIDDEN` | 403 |
| blocks 유효성 실패 (type 오류, 크기 초과 등) | `INVALID_REQUEST` | 400 |
| title 빈 값 | `INVALID_REQUEST` | 400 |
| content 빈 값 / 50,000자 초과 (AI API) | `INVALID_REQUEST` | 400 |
| question 빈 값 / 2,000자 초과, topK 범위(1~20) 초과 | `INVALID_REQUEST` | 400 |
| AI 서비스 오류 | `UNKNOWN` | 500 |

### blocks 유효성 규칙

| 항목 | 제한 |
|------|------|
| blocks 배열 최대 크기 | 5,000개 |
| text / code 최대 길이 | 100,000자 |
| list items 최대 개수 | 1,000개 |
| 허용 block type | `paragraph`, `header`, `list`, `code`, `image`, `table` |

---

## 16. Swagger UI

개발 환경에서 브라우저로 API를 직접 테스트할 수 있습니다.

| 경로 | 설명 |
|------|------|
| `http://localhost:8080/fixlog/swagger-ui.html` | Swagger UI (API 탐색·테스트) |
| `http://localhost:8080/fixlog/v3/api-docs` | OpenAPI 3.0 JSON 스펙 |

> 두 경로 모두 **인증 불필요**입니다. 토큰 없이 접근 가능합니다.

### 인증이 필요한 API 테스트 방법

1. Swagger UI 우측 상단 **Authorize** 버튼 클릭
2. `bearerAuth` 항목에 `accessToken` 값 입력 (Bearer 접두사 없이)
3. **Authorize** → **Close**
4. 이후 잠금 아이콘이 표시된 엔드포인트가 토큰과 함께 호출됨

> `X-Workspace-Id` 헤더가 필요한 API는 각 엔드포인트의 **Parameters** 섹션에서 직접 입력합니다.
