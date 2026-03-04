---
name: pr-generate
description: Generate a pull request with proper commit message and PR description following project conventions
disable-model-invocation: true
allowed-tools: Bash(git *), Bash(gh *), Read, Glob, Grep, mcp__atlassian__jira_get_issue
---

# PR 자동 생성기

현재 브랜치에 커밋된 변경사항을 분석하여 프로젝트 규칙에 맞는 PR을 자동으로 생성합니다.

## 작업 순서

### 0. JIRA 티켓 정보 조회

- 현재 브랜치 이름에서 JIRA 이슈 번호 추출 (패턴: `kan-숫자` 또는 `KAN-숫자`)
    - 예: `kan-22`, `KAN-22-login-feature`, `hs/kan-22` 등에서 `KAN-22` 추출
- 이슈 번호가 있으면 `mcp__atlassian__jira_get_issue` 도구로 티켓 정보 조회
    - 티켓 제목, 설명, 상태 등 확인
    - JIRA 링크 생성: `https://[workspace].atlassian.net/browse/KAN-22`
- 이슈 번호가 없으면 JIRA 섹션은 "해당 없음"으로 작성

### 1. 브랜치 변경사항 분석

- `git log dev..HEAD`로 현재 브랜치의 커밋 목록 확인
- `git diff dev..HEAD`로 dev 브랜치 대비 변경 내용 분석
- 변경 유형 판단 (feat/fix/refactor/docs/chore)
- 커밋되지 않은 변경사항이 있으면 먼저 커밋 여부 확인

### 2. 커밋 메시지 생성 (docs/dev-guidelines/COMMIT_CONVENTION.md 준수)

- 형식: `[type] [JIRA-123] summary`
- type 규칙:
    - `feat`: 사용자 관점의 기능 추가 또는 변경
    - `fix`: 버그 수정, 잘못된 동작 수정
    - `refactor`: 동작 변경 없이 구조/가독성 개선
    - `docs`: 문서 수정 (README, Wiki 등)
    - `chore`: 빌드, 설정, 의존성, 스크립트 등 운영성 작업
    - `hotfix`: 배포 후 긴급 수정 (운영 이슈 대응)
- 요약은 50자 내외, 마침표 없음
- JIRA 이슈 번호가 없으면 생략 가능 (소규모 작업)
- 커밋 메시지 마지막에 다음 서명 추가:

```
🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>
```

### 3. PR 제목 생성

- 커밋 메시지의 요약 부분을 기반으로 간결하게 작성
- 형식: `[type] 작업 내용 요약`
- 예: `[feat] 로그인 시 토큰 재발급 로직 추가`

### 4. PR 본문 생성 (docs/dev-guidelines/PULL_REQUEST.md 양식 준수)

변경 유형에 따라 다음 형식으로 작성:

#### 공통 구조

```markdown
# 작업 유형

- [x] 기능 개발 / 버그 개선 / 리팩토링 (해당 항목 선택)

---

## 1. 작업 요약 (Summary)

- **한 줄 요약**
    - [변경 내용을 한 줄로 요약]

---

## 2. 작업 배경 / 목적 (Background)

- [왜 이 작업이 필요했는지 설명]
- [기존 문제점 또는 요구 사항]

---

## 3. 작업 내용 (Details)

[변경 유형에 따라 다음 섹션 중 하나 사용]

### 기능 개발 (Feature Development)

- **구현한 기능**
    - [추가된 기능 설명]
    - [주요 동작 흐름]
- **핵심 로직 / 포인트**
    - [중요한 조건, 분기, 상태 관리]
- **영향 범위**
    - [영향 받는 페이지/컴포넌트/API]

### 버그 개선 (Bug Fix)

- **증상 (Symptom)**
    - [발견된 문제]
- **원인 (Root Cause)**
    - [기술적 원인]
- **해결 방법 (Solution)**
    - [수정 내용]
- **재발 방지**
    - [추가된 가드 로직, 테스트 등]

### 리팩토링 (Refactoring)

- **작업 설명**
    - [어떤 코드를 왜 정리했는지]
- **변경 전 / 후**
    - [구조, 책임, 네이밍 변화]
- **개선 효과**
    - [가독성/유지보수성/성능 개선]
- **기능 변경 여부**
    - [ ] 있음 (사유 명시)
    - [x] 없음 (순수 리팩토링)

---

## 4. UI 변경 사항 (UI / UX)

- [UI 변경이 있다면 설명, 없으면 "해당 없음"]

---

## 5. 테스트 및 검증 (Verification)

- 확인한 시나리오
    - [x] 주요 정상 케이스
    - [ ] 엣지 케이스
- 테스트 환경: Dev

---

## 6. Context / 참고 자료 (Context)

- **JIRA**
    - 티켓: [KAN-XX](https://[workspace].atlassian.net/browse/KAN-XX)
    - 제목: [JIRA 티켓 제목]
    - 상태: [JIRA 티켓 상태]
- **참고 자료**
    - [관련 문서 링크]

---

## 7. 추가 메모 (Optional)

- [리뷰 시 참고사항]

---

🤖 Generated with [Claude Code](https://claude.com/claude-code)
```

### 5. 푸시

- 현재 브랜치를 원격에 푸시 (`git push`)
- 원격 추적 브랜치가 없는 경우 자동으로 설정 (`git push -u origin`)

### 6. PR 생성

- `gh pr create`를 사용하여 PR 생성
- PR 제목과 본문을 전달
- PR 생성 완료 후 URL 반환

## 주의사항

1. **브랜치에 커밋이 있어야 함**: dev 브랜치 대비 커밋된 변경사항이 있어야 실행됨 (작업 디렉토리의 uncommitted 변경사항 유무와 무관)
2. **GitHub CLI 필수**: `gh` 명령어가 설치되어 있고 인증되어 있어야 함
3. **브랜치 확인**: dev 브랜치가 아닌 feature 브랜치에서 실행하는 것을 권장
4. **JIRA 이슈**: 브랜치 이름에 JIRA 이슈 번호가 포함되어 있으면 자동으로 티켓 정보를 조회함
    - 브랜치 명명 규칙: `kan-1234` 또는 `kan-1234-description` 형태 권장
    - Atlassian MCP 연결이 필요함 (최초 실행 시 인증 요청됨)
5. **변경 내용 분석**: 실제 코드 변경을 꼼꼼히 읽고 정확하게 분석할 것

## 실행 방법

```
/pr-generate
```

단순히 위 명령어만 입력하면 자동으로 모든 작업을 수행합니다.

## 예외 처리

- PR 생성 실패 시 에러 메시지 출력
- dev 브랜치 대비 커밋이 없으면 작업 중단
- dev 브랜치인 경우 경고 후 사용자에게 확인 요청
- 원격 추적 브랜치가 없는 경우 자동으로 설정 (`git push -u origin`)
- 커밋되지 않은 변경사항이 있는 경우 먼저 커밋할지 사용자에게 확인
