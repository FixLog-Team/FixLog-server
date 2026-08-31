# FixLog AI 채팅 API 및 프론트엔드 연동 가이드

## 1. 개요

AI 채팅 기록 기능은 사용자별 대화방과 대화방에 포함된 메시지를 저장한다.

- 인증 방식: JWT Bearer Token
- 개발 Base URL: `http://localhost:8080/fixlog`
- 공통 인증 헤더: `Authorization: Bearer {accessToken}`
- AI 응답 방식: 동기 HTTP 응답
- 메시지 조회 방식: `messageSequence` 기반 커서 페이지네이션

## 2. 새로 추가된 API

| Method | Endpoint | 설명 |
|---|---|---|
| `POST` | `/api/ai/conversations` | AI 대화방 생성 |
| `GET` | `/api/ai/conversations` | 로그인 사용자의 대화방 목록 조회 |
| `GET` | `/api/ai/conversations/{conversationId}` | 대화방 상세 조회 |
| `DELETE` | `/api/ai/conversations/{conversationId}` | 대화방 소프트 삭제 |
| `POST` | `/api/ai/conversations/{conversationId}/messages` | 사용자 메시지 저장 및 Gemini 답변 생성 |
| `GET` | `/api/ai/conversations/{conversationId}/messages` | 대화방 메시지 기록 조회 |

## 3. 공통 응답 타입

```ts
export type ApiCode =
  | 'SUCCESS'
  | 'UNAUTHORIZED'
  | 'NOT_FOUND'
  | 'INVALID_REQUEST'
  | 'UNKNOWN';

export interface ApiResponse<T> {
  code: ApiCode;
  message: string;
  result: T;
}
```

삭제 API처럼 `result`가 없는 응답은 별도 타입을 사용한다.

```ts
export interface ApiMessageResponse {
  code: ApiCode;
  message: string;
}
```

## 4. 프론트엔드 타입

```ts
export interface AIConversation {
  conversationId: string;
  title: string;
  createTime: string;
  updateTime: string;
}

export interface AIConversationPage {
  items: AIConversation[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export type AIMessageRole = 'USER' | 'ASSISTANT';
export type AIMessageStatus = 'PENDING' | 'COMPLETED' | 'FAILED';

export interface AIMessage {
  messageId: string;
  conversationId: string;
  messageSequence: number;
  role: AIMessageRole;
  content: string | null;
  status: AIMessageStatus;
  createTime: string;
  completeTime: string | null;
}

/** 답변 근거로 검색된 참고 문서. POST /search 응답과 동일 구조. */
export interface AIChatReference {
  documentId: string;
  title: string;
  folderId: string | null;
  excerpt: string;
  score: number;
}

export interface AIChatResponse {
  userMessage: AIMessage;
  assistantMessage: AIMessage;
  /**
   * 답변 근거로 검색된 참고 문서. 관련 문서가 없으면 빈 배열 (일반 대화 답변).
   * 메시지 이력에는 저장되지 않으므로, 이력 조회로 복원된 과거 메시지에는 없다.
   */
  references: AIChatReference[];
}

export interface AIMessageSlice {
  items: AIMessage[];
  nextBeforeSequence: number | null;
  hasNext: boolean;
}
```

## 5. API 클라이언트 구현

Axios 인스턴스에서 Access Token을 공통 헤더로 전달한다.

```ts
import axios from 'axios';

export const apiClient = axios.create({
  baseURL: 'http://localhost:8080/fixlog',
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use((config) => {
  const accessToken = localStorage.getItem('accessToken');

  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }

  return config;
});
```

AI 채팅 전용 API 모듈을 만든다.

```ts
import { apiClient } from './apiClient';

export async function createAIConversation(title?: string) {
  const response = await apiClient.post<ApiResponse<AIConversation>>(
    '/api/ai/conversations',
    { title },
  );
  return response.data.result;
}

export async function getAIConversations(page = 0, size = 20) {
  const response = await apiClient.get<ApiResponse<AIConversationPage>>(
    '/api/ai/conversations',
    { params: { page, size } },
  );
  return response.data.result;
}

export async function getAIConversation(conversationId: string) {
  const response = await apiClient.get<ApiResponse<AIConversation>>(
    `/api/ai/conversations/${conversationId}`,
  );
  return response.data.result;
}

export async function deleteAIConversation(conversationId: string) {
  await apiClient.delete(`/api/ai/conversations/${conversationId}`);
}

export async function sendAIMessage(conversationId: string, content: string) {
  const response = await apiClient.post<ApiResponse<AIChatResponse>>(
    `/api/ai/conversations/${conversationId}/messages`,
    { content },
  );
  return response.data.result;
}

export async function getAIMessages(
  conversationId: string,
  beforeSequence?: number,
  size = 20,
) {
  const response = await apiClient.get<ApiResponse<AIMessageSlice>>(
    `/api/ai/conversations/${conversationId}/messages`,
    { params: { beforeSequence, size } },
  );
  return response.data.result;
}
```

## 6. 화면별 구현 작업

### 6.1 대화방 목록 화면

1. 화면 진입 시 `GET /api/ai/conversations?page=0&size=20`을 호출한다.
2. `items`를 `updateTime` 기준 최신 대화부터 표시한다.
3. `hasNext=true`이면 다음 페이지를 추가 조회한다.
4. 항목을 선택하면 `/ai/chat/{conversationId}`로 이동한다.
5. 삭제 성공 후 목록 Query를 invalidate한다.

### 6.2 새 채팅 시작

1. `POST /api/ai/conversations`를 호출한다.
2. 제목이 정해지지 않았다면 `{ "title": null }` 또는 빈 문자열을 보낸다.
3. 응답의 `conversationId`로 채팅 상세 화면에 이동한다.

```ts
const conversation = await createAIConversation();
navigate(`/ai/chat/${conversation.conversationId}`);
```

### 6.3 채팅 상세 화면

1. URL의 `conversationId`를 읽는다.
2. 대화방 상세와 첫 메시지 페이지를 조회한다.
3. 메시지는 API가 오래된 순서부터 반환하므로 받은 순서 그대로 출력한다.
4. `hasNext=true`이면 상단 스크롤 시 `nextBeforeSequence`로 이전 메시지를 조회한다.
5. 이전 메시지 페이지는 기존 배열 앞에 추가한다.

```ts
const olderPage = await getAIMessages(
  conversationId,
  currentPage.nextBeforeSequence ?? undefined,
  20,
);

setMessages((current) => [...olderPage.items, ...current]);
```

## 7. 메시지 전송 UI 흐름

현재 API는 Gemini 답변 생성이 끝날 때까지 HTTP 요청이 유지되는 동기 방식이다.

1. 입력값의 앞뒤 공백을 제거한다.
2. 빈 문자열이면 전송하지 않는다.
3. 요청 중에는 중복 전송을 방지한다.
4. 전송 버튼을 비활성화하고 AI 답변 생성 중 UI를 표시한다.
5. 성공하면 응답의 `userMessage`, `assistantMessage`를 메시지 목록 끝에 추가한다.
6. `references`가 비어 있지 않으면 답변 아래에 참고 문서 카드를 표시한다.
   (references는 이력에 저장되지 않으므로 화면 상태로만 관리한다)
7. 실패하면 오류 메시지를 표시하고 메시지 기록을 다시 조회한다.

```ts
async function handleSendMessage(rawContent: string) {
  const content = rawContent.trim();
  if (!content || isSending) return;

  setIsSending(true);

  try {
    const result = await sendAIMessage(conversationId, content);
    setMessages((current) => [
      ...current,
      result.userMessage,
      result.assistantMessage,
    ]);
    setInput('');
  } catch (error) {
    const refreshed = await getAIMessages(conversationId, undefined, 20);
    setMessages(refreshed.items);
    showErrorToast('AI 답변을 생성하지 못했습니다.');
  } finally {
    setIsSending(false);
  }
}
```

AI 호출이 실패하더라도 서버에는 다음 상태로 기록될 수 있다.

```text
USER      COMPLETED
ASSISTANT FAILED
```

따라서 실패 후 메시지를 다시 조회하고 `FAILED` 메시지에 재시도 안내 UI를 표시한다.

## 8. React Query 연결 권장안

권장 Query Key:

```ts
export const aiChatKeys = {
  all: ['ai-chat'] as const,
  conversations: () => [...aiChatKeys.all, 'conversations'] as const,
  conversationList: (page: number, size: number) =>
    [...aiChatKeys.conversations(), page, size] as const,
  conversation: (conversationId: string) =>
    [...aiChatKeys.conversations(), conversationId] as const,
  messages: (conversationId: string) =>
    [...aiChatKeys.conversation(conversationId), 'messages'] as const,
};
```

Mutation 성공 시 다음 캐시를 갱신한다.

| 작업 | 갱신 대상 |
|---|---|
| 대화방 생성 | 대화방 목록 invalidate |
| 메시지 전송 | 해당 대화방 메시지 캐시 갱신, 대화방 목록 invalidate |
| 대화방 삭제 | 대화방 목록 invalidate, 상세/메시지 캐시 제거 |

## 9. 인증 및 오류 처리

### 9.1 Access Token

모든 AI 채팅 API 요청에 다음 헤더를 포함한다.

```http
Authorization: Bearer eyJhbGciOi...
```

### 9.2 HTTP 상태별 처리

| HTTP | 의미 | 프론트 처리 |
|---:|---|---|
| `400` | 입력값 또는 UUID 형식 오류 | 입력 오류 메시지 표시 |
| `401` | Access Token 누락 또는 만료 | 토큰 재발급 후 기존 요청 재시도 |
| `404` | 대화방 없음 또는 소유권 없음 | 목록 화면 이동 및 안내 표시 |
| `500` | Gemini 또는 서버 오류 | 오류 토스트 표시 후 메시지 재조회 |

Access Token 재발급 요청:

```http
POST /auth/token/refresh
Content-Type: application/json

{
  "refreshToken": "eyJhbGci..."
}
```

재발급 성공 시 새 `accessToken`을 저장하고 원래 요청을 한 번만 재시도한다. 재발급 요청 자체가 401이면 로그인 화면으로 이동한다.

## 10. 프론트엔드 완료 체크리스트

- [ ] OAuth 콜백에서 Access Token과 Refresh Token 저장
- [ ] Axios Authorization 인터셉터 설정
- [ ] 401 발생 시 Access Token 재발급 처리
- [ ] 대화방 목록 및 무한 스크롤 구현
- [ ] 새 대화방 생성 후 상세 화면 이동
- [ ] 대화방 상세 및 메시지 첫 페이지 조회
- [ ] 메시지 커서 기반 이전 기록 로딩
- [ ] 메시지 입력 검증 및 중복 전송 차단
- [ ] AI 답변 생성 중 로딩 표시
- [ ] `USER`, `ASSISTANT`에 따른 메시지 UI 분리
- [ ] `FAILED` AI 메시지 오류 UI 표시
- [ ] 대화방 삭제 확인창 및 목록 캐시 갱신
- [ ] 다른 사용자/삭제된 대화방 404 처리

## 11. Swagger 확인

서버 실행 후 다음 주소에서 새 API 설명과 직접 호출 기능을 확인한다.

```text
http://localhost:8080/fixlog/swagger-ui.html
```

Swagger 테스트 전 로그인:

```text
http://localhost:8080/fixlog/login/swag
```
