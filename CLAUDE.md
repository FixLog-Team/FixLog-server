# FixLog 프로젝트 가이드

---

## 프로젝트 개요

FixLog는 **AI 기반 트러블슈팅 자동 회고록 서비스**입니다.

개발 과정에서 발생하는 오류, 원인 분석, 해결 방법, 참고 자료를 단순 메모가 아닌 **구조화된 문서 자산**으로 축적하는 것을 목표로 합니다.

### 핵심 가치

- 메모장처럼 가볍게 작성해도 **AI가 문서 형태로 자동 정리**
- 기록의 시작점 자체를 서비스화
- 디렉토리 기반 구조로 장기 관리 가능
- 의미 기반 검색으로 과거 트러블슈팅 기록 재활용

### 1차 MVP 범위 (2025.12.18 ~ 2026.03.29)

- 개인 문서 드라이브
- 블록 기반 문서 관리 (Notion 방식)
- AI 요약 및 정리 기능
- 의미 기반 문서 검색 기능
- 문서 버전 히스토리 관리

---

## 핵심 개념 정의

### 1. Document (문서)

사용자가 작성한 하나의 지식 단위입니다.
트러블슈팅, 오류 원인 분석, 해결 과정, 회고 메모 등을 포함합니다.

### 2. Block (블록)

문서를 구성하는 최소 편집 단위입니다.
Notion처럼 여러 개의 Block으로 구성되며, 블록 단위로 저장됩니다.

### 3. Metadata (메타데이터)

문서 검색, 정렬, AI 처리 효율을 위한 보조 데이터입니다.

- 사용자 입력 태그
- 자동 추출 키워드
- 생성/수정 시점

### 4. AI Artifact

AI 처리 전반에서 생기는 모든 파생 데이터입니다.

- 문서 요약 결과
- 문서 임베딩 벡터
- 검색용 문장 매핑 정보

---

## 아키텍처 설계 원칙

### 데이터 분리 원칙

다음 데이터는 분리하여 저장합니다:

1. **원문** (Document / Block)
2. **메타데이터**
3. **AI Artifact** (요약, 임베딩)

### 재생성 가능성 전제

- AI 요약 및 임베딩은 언제든 재생성 가능합니다
- 원본 데이터는 항상 보존됩니다
- AI 결과는 항상 "제안"입니다

### 보안 고려사항

- 문서 내용은 Base64 인코딩 및 압축 후 저장 가능
- SQL Injection 방어 목적
- AI 처리 및 검색 시 디코딩 후 사용

---

## 주요 기능 흐름

### 1. 문서 작성 및 저장

```
1. 사용자가 블록 기반으로 문서를 작성함 (클라이언트 상태)
2. 사용자가 저장 버튼을 누름
3. 문서 데이터가 DB에 저장됨 (블록 JSON과 각 블록의 Text 저장)
4. AI 분석이 비동기로 시작됨 (옵션)
5. 사용자가 문서를 탐색/검색함
```

### 2. AI 요약 기능

#### 트리거

사용자가 "문서 요약" 버튼을 실행합니다.

#### 처리 흐름

1. 원본 문서(Block JSON)를 기준으로 AI 요약 요청
2. AI는 정리된 문서 초안을 생성 (Block JSON 형식)
3. 좌우 분할 화면 표시:
   - 좌측: 기존 문서
   - 우측: AI가 생성한 정리 문서
4. 사용자 선택:
   - **수락**: AI 문서를 새로운 본문으로 저장, 기존 문서는 History로 보관
   - **거절**: 기존 문서 유지

#### 설계 원칙

- AI 결과는 항상 "제안"입니다
- 원본 문서는 항상 보호됩니다

### 3. 문서 검색 (의미 기반)

#### 1단계: 문서 인덱싱

- 문서 저장/수정 시 임베딩 생성
- Sentence Transformer 사용 (snunlp 계열 권장)
- 임베딩 벡터를 벡터 DB에 저장

#### 2단계: 태그 기반 1차 필터링

- 문서에서 키워드/태그 추출
- Postgres JSONB + GIN Index 활용
- 탐색 문서 수를 제한 (토큰 절약)

#### 3단계: 의미 기반 검색

- 사용자 검색 문장을 임베딩
- 벡터 유사도 기반 검색 수행
- 상위 N개 문서 반환

#### 4단계: 결과 표시

- 가장 연관성 높은 문서 목록 제공
- 관련 문장 또는 요약 정보 표시

### 4. 문서 히스토리 관리

- 문서 수정 시마다 이전 버전이 History로 보관됩니다
- 과거 내역 페이지에서 최신 순으로 나열됩니다
- 각 내역마다 **롤백** 버튼이 존재합니다
- 클릭 시 해당 내용으로 복원할 수 있습니다

---

## API 설계 가이드

### 인증 / 세션

- `POST /auth/login` - Google OAuth 로그인
- `POST /auth/token/refresh` - Access Token 재발급
- `GET /auth/session` - 현재 로그인 세션 확인
- `POST /auth/logout` - 로그아웃

### 문서 관리

- `GET /documents` - 문서 목록 조회 (페이지네이션/무한스크롤)
- `GET /documents/{documentId}` - 문서 상세 조회
- `POST /documents` - 새 문서 생성
- `PUT /documents/{documentId}` - 문서 내용 저장
- `PATCH /documents/{documentId}` - 문서 제목 변경
- `DELETE /documents/{documentId}` - 문서 삭제
- `POST /documents/{documentId}/duplicate` - 문서 복제
- `PATCH /documents/{documentId}/move` - 문서 폴더 이동
- `GET /documents/{documentId}/download` - 문서 다운로드

### 문서 편집 (안정성)

- `PATCH /documents/{documentId}/draft` - 자동저장/임시저장
- `GET /documents/{documentId}/save-state` - 마지막 저장 상태 조회
- `POST /documents/{documentId}/lock` - 문서 편집 락 획득
- `DELETE /documents/{documentId}/lock` - 문서 편집 락 해제

### 폴더 관리

- `GET /folders` - My Documents 하위 폴더 조회
- `GET /folders/{folderId}/contents` - 폴더 콘텐츠 조회 (하위 폴더 + 파일)
- `POST /folders` - 폴더 생성
- `PATCH /folders/{folderId}` - 폴더 이름 변경
- `DELETE /folders/{folderId}` - 폴더 삭제
- `PATCH /folders/{folderId}/move` - 폴더 이동

### 일괄 작업

- `DELETE /documents/bulk` - 다중 문서 삭제
- `DELETE /folders/bulk` - 다중 폴더 삭제
- `PATCH /documents/bulk/move` - 다중 문서 폴더 이동
- `GET /documents/bulk/download` - 다중 문서 다운로드 (ZIP)

### AI 기능

- `POST /ai/summarize` - 문서 요약 요청
- `GET /ai/jobs/{jobId}` - AI 작업 상태 조회
- `POST /ai/jobs/{jobId}/cancel` - AI 작업 취소
- `POST /documents/{documentId}/apply-summary` - AI 요약 결과 적용 (선택)

### 검색

- `POST /search` - 의미 기반 문서 검색

### 문서 히스토리

- `GET /documents/{documentId}/history` - 문서 히스토리 목록 조회
- `GET /documents/{documentId}/history/{historyId}` - 특정 히스토리 상세 조회
- `POST /documents/{documentId}/history/{historyId}/restore` - 히스토리 기준 복원

### 사용자

- `GET /users/me` - 현재 사용자 정보 조회
- `GET /users/me/workspaces` - 사용자 워크스페이스 목록 조회

---

## 프론트엔드 기술 스택 (권장)

### 핵심 프레임워크

- **React** (함수형 컴포넌트 + Hooks 기반)
- **TypeScript** (타입 안정성 필수)

### 상태 관리

- **Zustand** 또는 **Recoil** (가벼운 상태 관리)
- 서버 상태는 **React Query** 또는 **SWR** 활용

### 에디터

- **BlockNote** 또는 **Slate.js** (블록 기반 에디터)
- Notion 스타일 UX 구현 가능

### 스타일링

- **Tailwind CSS** (빠른 UI 구축)
- **shadcn/ui** (일관된 디자인 시스템)

### 라우팅

- **React Router v6**

### API 통신

- **Axios** 또는 **Fetch API**
- React Query와 함께 사용

---

## 개발 시 주의사항

### 1. 블록 기반 문서 구조

- 문서는 Block JSON 형태로 저장됩니다
- 각 블록은 `type`, `content`, `id` 등을 포함합니다
- AI 요약 시에도 Block JSON 형식을 유지해야 합니다

### 2. 권한 및 소유권 검증

모든 문서/폴더 접근 시 소유자 검증을 반드시 수행합니다:

- 문서 조회/수정/삭제 전 소유자 확인
- 폴더 이동 시 권한 확인
- 워크스페이스 접근 권한 검증

### 3. AI 처리는 비동기

- AI 요약 및 임베딩 생성은 시간이 소요됩니다
- 사용자에게 로딩 상태를 명확히 표시합니다
- 작업 상태 폴링 또는 WebSocket 활용을 고려합니다

### 4. 에러 처리

- 네트워크 에러, AI 처리 실패 등에 대한 Fallback UI 제공
- 사용자에게 명확한 에러 메시지 전달
- 자동 재시도 로직 구현 (옵션)

### 5. 성능 최적화

- 문서 목록은 페이지네이션 또는 무한 스크롤 적용
- 이미지 Lazy Loading
- 에디터 렌더링 최적화 (Virtual Scrolling 고려)

### 6. 보안

- XSS 방어: 사용자 입력 데이터 sanitize
- CSRF 방어: Token 기반 인증
- SQL Injection: 백엔드에서 처리하지만 프론트에서도 입력 검증

### 7. 1차 MVP에서 제외된 기능

다음 기능은 구현하지 않습니다:

- 문서 공유 / 팀 문서
- 실시간 협업
- 퍼블릭 문서 공개
- 자동 태그 고도화
- 문서 버전 비교 (히스토리 조회는 가능, 비교 UI는 제외)

---

## 디자인 참고

Figma 디자인: https://www.figma.com/make/XXeQOyo1kkDz1pBuq5rvrN/Product-UI-Design?p=f&t=4AYqgAbpDWJfE2TD-0

### 주요 페이지

1. **로그인 페이지**: Google OAuth 로그인
2. **메인 페이지**: 문서 탐색 / 관리 (Gmail 스타일)
3. **에디터 페이지**: 블록 기반 문서 작성 / 수정
4. **검색 페이지**: 의미 기반 검색 결과
5. **히스토리 페이지**: 문서 버전 히스토리

---

## 디렉토리 구조 (권장)

```
src/
├── components/          # 재사용 가능한 컴포넌트
│   ├── editor/         # 에디터 관련 컴포넌트
│   ├── document/       # 문서 관련 컴포넌트
│   ├── folder/         # 폴더 관련 컴포넌트
│   └── ui/             # 공통 UI 컴포넌트
├── pages/              # 페이지 컴포넌트
│   ├── Login/
│   ├── Main/
│   ├── Editor/
│   └── History/
├── hooks/              # Custom Hooks
├── services/           # API 호출 로직
│   ├── auth.ts
│   ├── documents.ts
│   ├── folders.ts
│   └── ai.ts
├── store/              # 상태 관리 (Zustand/Recoil)
├── types/              # TypeScript 타입 정의
├── utils/              # 유틸리티 함수
└── constants/          # 상수 정의
```

---

## 코딩 컨벤션

### 네이밍 규칙

- **컴포넌트**: PascalCase (예: `DocumentEditor.tsx`)
- **함수/변수**: camelCase (예: `fetchDocuments`, `userId`)
- **상수**: UPPER_SNAKE_CASE (예: `API_BASE_URL`)
- **타입/인터페이스**: PascalCase (예: `Document`, `UserProfile`)

### 파일 구조

- 한 파일에 하나의 컴포넌트만 export
- 관련된 타입은 같은 파일에 정의 가능
- 스타일은 CSS Module 또는 Tailwind 활용

### 함수형 컴포넌트 기본 구조

```tsx
import React from 'react';

interface Props {
  title: string;
  onSave: () => void;
}

export const DocumentEditor: React.FC<Props> = ({ title, onSave }) => {
  // Hooks
  const [content, setContent] = React.useState('');

  // Handlers
  const handleSave = () => {
    onSave();
  };

  // Render
  return (
    <div>
      <h1>{title}</h1>
      {/* ... */}
    </div>
  );
};
```

---

## 추가 고려사항

### 확장 가능성 (2차 MVP 이후)

- 개인 드라이브 → 팀 드라이브 확장
- 여러 문서를 대상으로 한 AI 통합 검색
- Git 연동 (자동 커밋, 잔디 심기)
- PDF 다운로드 기능
- Read Only 공유 링크
- 기술 위키 / 팀 회고 아카이브 / 개인 학습 이력 관리

---

## 참고 문서

- `docs/planning/프로젝트_제안서.md` - 전체 프로젝트 개요
- `docs/planning/핵심_시스템_기획서.md` - 시스템 상세 설계
- `docs/planning/페이지별_필요_기능(API).md` - API 명세
- `docs/dev-guidelines/COMMIT_CONVENTION.md` - 커밋 메시지 규칙 상세
- `docs/dev-guidelines/PULL_REQUEST.md` - PR 템플릿 상세
- `docs/dev-guidelines/BRANCH_CONVENTION.md` - Git 브랜치 규칙 상세

---

## Git 브랜치 전략

### 브랜치 종류

| 브랜치 | 역할 | 규칙 |
|--------|------|------|
| `main` | 배포 기준 | 스프린트 종료 후만 머지, 직접 push 금지 |
| `dev` | 개발 통합 | 완료된 이슈를 순차적으로 머지, 직접 push 금지 |
| `kan-<이슈번호>-설명` | 이슈 단위 개발 | JIRA 티켓에 대응, 완료 시 `dev`로 머지 |
| `<이니셜>/<브랜치명>` | 개인 작업 | 개인 브랜치에서 이슈 브랜치로 PR |

### 작업 흐름

```
개인 브랜치 (hs/kan-123)
    ↓ PR
이슈 브랜치 (kan-123-login)
    ↓ PR (테스트 완료 후)
dev
    ↓ 스프린트 종료 시
main (배포)
```

### 머지 방식

- **Rebase and merge** 사용 (GitHub)
- 선형 히스토리 유지

---

## 커밋 메시지 규칙

### 형식

```
[type] [JIRA-123] summary
```

### Type 종류

| type | 사용 목적 |
|------|----------|
| `feat` | 사용자 관점의 기능 추가 또는 변경 |
| `fix` | 버그 수정, 잘못된 동작 수정 |
| `refactor` | **동작 변경 없이** 구조/가독성 개선 |
| `docs` | 문서 수정 (README, Wiki 등) |
| `chore` | 빌드, 설정, 의존성, 스크립트 등 |
| `hotfix` | 배포 후 긴급 수정 |

### 원칙

- **One Commit = One Intent**: 한 커밋에 하나의 목적만
- **Describe WHAT, not HOW**: 구현 방식보다 변경 결과와 의도
- **Avoid Mixed Commits**: 성격이 다른 변경은 커밋 분리
- 요약은 **50자 내외**, 마침표 없음

### 예시

```
[feat] [KAN-101] 로그인 시 토큰 재발급 로직 추가
[fix] [KAN-115] 중복 결제 요청 시 예외 처리 누락 수정
[refactor] [KAN-120] 결제 검증 로직 메서드 분리
[docs] 로컬 실행 방법 및 환경변수 설명 추가
```

---

## PR 작성 가이드

### PR 제목 형식

```
[type] 작업 내용 요약
```

### PR 본문 필수 섹션

1. **작업 유형**: 기능 개발 / 버그 개선 / 리팩토링 중 **하나만** 선택
2. **작업 요약**: 한 줄 요약
3. **작업 배경/목적**: 왜 이 작업이 필요했는지
4. **작업 내용**: 유형별 상세 내용
5. **UI 변경 사항**: 스크린샷/영상 (해당 시)
6. **테스트 및 검증**: 확인한 시나리오
7. **Context/참고 자료**: JIRA 링크, 관련 PR 등

---

## Claude Code 커스텀 스킬

### `/commit-generate`

변경사항을 분석하여 프로젝트 커밋 규칙에 맞는 커밋을 자동 생성합니다.

- 변경사항 자동 분석
- 성격별 커밋 분리 (feat/fix/refactor/docs/chore)
- JIRA 이슈 번호 자동 감지 (브랜치명 기반)
- 커밋 메시지 자동 생성

### `/pr-generate`

현재 브랜치의 커밋을 분석하여 PR을 자동 생성합니다.

- JIRA 티켓 정보 자동 조회 (Atlassian MCP 연동)
- 변경 유형별 PR 본문 자동 작성
- 원격 푸시 및 PR 생성 자동화

---

## 현재 프로젝트 상태

> **초기 설정 단계** - 아직 소스 코드 개발 전

### 완료된 작업

- 프로젝트 기획 문서 작성 완료
- Git 브랜치/커밋/PR 규칙 정립
- Claude Code 자동화 스킬 설정

### 다음 단계

1. React + TypeScript 프로젝트 초기화 (Vite)
2. 기본 디렉토리 구조 생성
3. 핵심 의존성 설치 (Tailwind, shadcn/ui, React Router, Zustand, React Query)
4. 기본 레이아웃 및 라우팅 설정

---

> 이 가이드는 FixLog 프로젝트의 1차 MVP 개발을 위한 문서입니다.
> 실제 개발 과정에서 내용이 업데이트될 수 있습니다.
