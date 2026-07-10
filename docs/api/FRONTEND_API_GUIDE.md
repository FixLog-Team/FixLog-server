# FixLog API 가이드 (프론트엔드용)

> 최종 업데이트: 2026-07-09
> Base URL (개발): `http://localhost:8080/fixlog`

---

## 목차

1. [공통 규칙](#1-공통-규칙)
2. [인증 플로우](#2-인증-플로우)
3. [인증 API](#3-인증-api)
4. [문서 API](#4-문서-api)
5. [폴더 API](#5-폴더-api)
6. [AI API](#6-ai-api)
7. [에러 처리](#7-에러-처리)

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
| `NOT_FOUND` | 404 | 리소스 없음 |
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
Google OAuth 로그인 시작 (페이지 이동)

**인증 불필요**

```
응답: 302 Redirect → Google 로그인 페이지
```

---

### POST /auth/token/refresh
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
> 문서는 로그인한 사용자 본인 소유 문서만 접근 가능

### POST /api/documents
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
문서 PDF 다운로드

**Response**: `application/pdf` 바이너리
```
Content-Disposition: attachment; filename*=UTF-8''문서제목.pdf
```

---

## 5. 폴더 API

> 모든 엔드포인트 **인증 필요**

### GET /api/folders
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
폴더 콘텐츠 조회 (하위 폴더 + 문서 목록)

**Response**: 위와 동일한 `FolderContentsDto` 형식

> `folders`와 `documents`는 각각 `ordinal` 오름차순(동률이면 생성순)으로 정렬되어 내려온다.
> 두 목록은 **서로 독립된 순번 공간**을 쓴다. 사이드바에서는 폴더를 먼저, 문서를 그다음에 렌더링하면 된다.

> 사이드바 폴더 트리는 이 엔드포인트를 재귀 호출하지 말고 `GET /api/folders/tree`를 한 번 호출한다.
> 이 엔드포인트는 특정 폴더를 열었을 때 그 안의 문서 목록까지 함께 받는 용도다.

---

### GET /api/folders/tree
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
> 즉 `Parent(2)` + `Parent/Child(1)`이면 Parent의 `documentCount`는 3이 아니라 2다. 폴더를 열었을 때 보이는 개수와 일치시키기 위함이다.
> 루트에 바로 놓인 문서(`folderId`가 `null`)는 어떤 폴더의 `documentCount`에도 포함되지 않는다. 루트 문서 목록은 `GET /api/folders`로 받는다.
> 트리에는 폴더만 담긴다. 문서 목록은 포함되지 않는다.

---

### GET /api/folders/{folderId}
특정 폴더 정보 조회

**Response**: `FolderDto`

---

### POST /api/folders
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
폴더 이름 변경 (이동은 `PATCH .../move`, 순서 변경은 `PATCH /reorder` 사용)

**Request Body**
```json
{
  "folderName": "변경된 폴더명"
}
```

> 폴더 이동(부모 변경)과 순서 변경은 이 엔드포인트에서 처리하지 않는다. `parentId`나 `ordinal`을 보내도 무시된다.

**Response**: `FolderDto`

---

### PATCH /api/folders/reorder
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
> 일부만 보내거나, 중복이 있거나, 다른 부모의 폴더가 섞이면 `INVALID_REQUEST`로 거부된다.

**Response**: `FolderDto[]` (새 순서대로, `ordinal`은 0부터 재부여됨)

---

### PATCH /api/folders/{folderId}/move
폴더 이동 (부모 폴더 변경)

**Request Body**
```json
{
  "parentId": "target-parent-folder-uuid"
}
```

> `parentId`를 `null`로 보내면 루트로 이동한다.
> 자기 자신 또는 자신의 하위 폴더로 이동하면 `INVALID_REQUEST`로 거부된다(순환 방지).
> 이동한 폴더는 새 부모의 **맨 뒤** 순번을 새로 받는다.

**Response**: `FolderDto`

---

### DELETE /api/folders/{folderId}
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
> `/ai/**` → OpenAI (GPT-4o), `/ollama/**` → Ollama (llama3)

### POST /ai/analyze 또는 POST /ollama/analyze
문서 분석 (요약 + 태그 + 임베딩 한 번에)

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
  "message": "",
  "result": {
    "summary": "요약된 내용",
    "tags": ["태그1", "태그2"],
    "embedding": [0.123, -0.456, ...]
  }
}
```

---

### POST /ai/summarize 또는 POST /ollama/summarize
문서 요약

**Request Body**
```json
{ "content": "요약할 내용" }
```

**Response**
```json
{
  "code": "SUCCESS",
  "message": "",
  "result": "요약된 텍스트"
}
```

---

### POST /ai/tags 또는 POST /ollama/tags
태그 자동 생성

**Response**
```json
{
  "code": "SUCCESS",
  "message": "",
  "result": ["Spring Boot", "JWT", "OAuth2"]
}
```

---

### POST /ai/embedding 또는 POST /ollama/embedding
임베딩 벡터 생성

**Response**
```json
{
  "code": "SUCCESS",
  "message": "",
  "result": [0.123, -0.456, 0.789, ...]
}
```

---

## 7. 에러 처리

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
| 문서/폴더 없거나 타인 소유 | `NOT_FOUND` | 404 |
| blocks 유효성 실패 (type 오류, 크기 초과 등) | `INVALID_REQUEST` | 400 |
| title 빈 값 | `INVALID_REQUEST` | 400 |
| AI 서비스 오류 | `UNKNOWN` | 500 |

### blocks 유효성 규칙

| 항목 | 제한 |
|------|------|
| blocks 배열 최대 크기 | 5,000개 |
| text / code 최대 길이 | 100,000자 |
| list items 최대 개수 | 1,000개 |
| 허용 block type | `paragraph`, `header`, `list`, `code`, `image`, `table` |

---

## Swagger UI

개발 환경에서 직접 API 테스트 가능:

```
http://localhost:8080/fixlog/swagger-ui.html
```
