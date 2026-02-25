> 기능 기준으로 필요한 API만 나열하며, 상세 설명은 제외

- 디자인
  https://www.figma.com/make/XXeQOyo1kkDz1pBuq5rvrN/Product-UI-Design?p=f&t=4AYqgAbpDWJfE2TD-0
- 빠진 것 같은 기능(API)들 임시로 적어둠
  # FixLog MVP에서 "빠졌을 가능성 큰" API들 (추가 제안분)
  ## ✅ (B) 권한 / 소유권 검증 (지금 MVP에 숨어 있는 핵심)
  FixLog는 “문서 서비스”라서 **이거 안 하면 나중에 다 갈아엎음**.
  - 문서/폴더 **소유자 검증**
  - 워크스페이스 접근 권한 검증
  - (선택) 읽기 전용 / 수정 가능 권한
  ## 인증/세션
  - **POST /auth/token/refresh**
    Refresh Token으로 Access Token을 재발급한다.
  - **GET /auth/session**
    현재 로그인 세션(토큰 유효성)을 확인한다.
  ## 문서/폴더 기본
  - **POST /documents**
    새 문서를 생성한다(빈 문서/템플릿 기반).
  - **PATCH /folders/{folderId}/move**
    폴더를 다른 폴더로 이동한다.
  - **PATCH /folders/bulk/move**
    여러 폴더를 한 번에 다른 폴더로 이동한다.
  ## 에디터 안정성
  - **PATCH /documents/{documentId}/draft**
    자동저장/임시저장(draft)을 저장한다.
  - **GET /documents/{documentId}/save-state**
    마지막 저장 시각/상태를 조회한다.
  ## 동시 편집 방지 (최소선)
  - **POST /documents/{documentId}/lock**
    문서 편집 락을 획득한다(누가 편집 중인지 포함 가능).
  - **DELETE /documents/{documentId}/lock**
    문서 편집 락을 해제한다(또는 만료 처리).
  ## AI 작업 운영 (상태/취소)
  - **GET /ai/jobs/{jobId}**
    AI 작업 상태(pending/processing/done/failed)와 결과 참조를 조회한다.
  - **POST /ai/jobs/{jobId}/cancel**
    진행 중인 AI 작업을 취소한다(가능한 경우).

---

## 1. 로그인 페이지

![image.png](attachment:9a4dfeda-4117-4306-90f3-846a1c552b01:image.png)

- Google OAuth 로그인 API
- 사용자 프로필 조회 API
- 로그인 세션/토큰 갱신 API

---

## 2. 메인 페이지 (문서 탐색 / 관리)

### 공통

- 아래 API는 하나로 합치거나, 2개로 쪼개도 됨
  - 사용자 기본 정보 조회 API
  - 사용자 권한/워크스페이스 목록 조회 API

---

### 사이드바 (My Documents)

![image.png](attachment:557d8a8b-71f5-483e-8162-db13c2cc9316:image.png)

- My Documents 하위 폴더 조회 API

---

### 헤더 (Header)

![image.png](attachment:7dc5f12b-6073-442f-a2dd-30155ef8da04:image.png)

- 문서 의미 기반 검색 API
- 로그아웃 API

---

### 메인 콘텐츠 영역 (리스트)

![image.png](attachment:3735a468-c6f6-4b78-b14f-e0053a7cf968:image.png)

- 폴더 콘텐츠 조회 API
  (하위 폴더 + 파일 목록)
- 문서 목록 페이지네이션 API
  - 또는 커서 기반 페이지네이션 (더 보기 버튼)
  - 또는 무한 스크롤

**생성 / 수정 / 삭제**

- 폴더 생성 API
- 폴더 삭제 API
- 폴더 이름 변경 API
- 문서 복제 API
- 문서 삭제 API

**이동 / 다운로드**

- 문서의 폴더 이동 API
- 문서 다운로드 API

---

### 선택 모드 (일괄 작업)

![image.png](attachment:3a632bbc-3a83-42bd-8645-4d2d67aafaa0:image.png)

- 다중 문서 삭제 API
- 다중 폴더 삭제 API
- 다중 문서의 폴더 이동 API
- 다중 문서 다운로드 API (ZIP)

---

## 3. 문서 에디터 페이지

![image.png](attachment:1281c3ac-539a-4023-aa33-d37db3d74dc7:image.png)

### 문서 기본

- 문서 내용 저장 API
  - 문서 제목도 변경할 수 있다
  - → 문서 제목은 2개의 API를 통해 변경 가능
- 문서 제목 변경 API

![image.png](attachment:3ea00028-fa6a-4043-845b-6a7c0968bb37:image.png)

**히스토리**

- 문서 히스토리 목록 조회 API
- 선택한 히스토리 상세 조회 API
  - 해당 히스토리의 당시 마크다운 조회
- 히스토리 기준 문서 복원 API
- 문서 사본 생성 API

---

### AI 기능

![image.png](attachment:6be224e8-7d74-430f-8451-6d1579b5d787:image.png)

- 2개 API는 1개로 하는 것이 더 나은지? (요약 요청 후 Response로 요약 결과 값?)
  - 문서 요약 요청 API
  - AI 요약 결과 조회 API
- AI 요약 결과 적용 API (해당 API는 구현을 할지 말지 고민)
  - 어차피 문서 저장 API가 존재하므로?

---

### 상세 페이지

- 문서 상세 조회 API
  - (작성된 내용 조회)

---

## 4. 공통 / 보조 API

- 알림/토스트 메시지용 API (선택)
- 작업 상태 폴링 API (AI, 대용량 작업)
- 에러 로깅 / 트래킹 API
