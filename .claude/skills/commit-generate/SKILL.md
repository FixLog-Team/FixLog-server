---
name: commit-generate
description: Analyze changes and generate commits following project conventions, automatically splitting by change type if needed
disable-model-invocation: true
allowed-tools: Bash(git *), Read, Glob, Grep
---

# 커밋 자동 생성기

현재 변경사항을 분석하여 프로젝트 커밋 규칙에 맞는 커밋을 자동으로 생성합니다.
필요시 성격에 맞게 여러 개의 커밋으로 분리합니다.

## 작업 순서

### 1. 변경사항 분석
- `git status`로 현재 변경/추가/삭제된 파일 목록 확인
- `git diff`로 각 파일의 변경 내용 상세 분석
- 변경사항이 없으면 작업 중단하고 안내

### 2. 변경 유형 분류 (docs/dev-guidelines/COMMIT_CONVENTION.md 준수)

변경된 파일들을 다음 기준으로 그룹화:

#### Type 분류 기준

| Type | 판단 기준 | 예시 파일 |
|------|----------|----------|
| `feat` | 새로운 기능 추가, 기능 변경 | 새 컴포넌트, API 추가, 새로운 비즈니스 로직 |
| `fix` | 버그 수정, 잘못된 동작 수정 | 오류 수정, 예외 처리 추가, 로직 오류 개선 |
| `refactor` | **동작 변경 없이** 구조 개선 | 함수 분리, 중복 제거, 네이밍 변경, 구조 정리 |
| `docs` | 문서만 수정 | README, CLAUDE.md, .md 파일, 주석 |
| `chore` | 빌드, 설정, 의존성 | package.json, .config 파일, .env, 스크립트 |

#### 분류 원칙
- **One Commit = One Intent**: 한 커밋에는 하나의 목적만
- **Avoid Mixed Commits**: 성격이 다른 변경은 반드시 분리
- **Logic Impact Matters**: 동작이 바뀌면 `fix`/`feat`, 바뀌지 않으면 `refactor`

#### 분리 예시
```
변경사항:
- src/components/Login.tsx (새로운 OAuth 로직 추가)
- src/components/Header.tsx (네이밍만 변경)
- package.json (의존성 추가)
- README.md (설치 방법 업데이트)

→ 4개 커밋으로 분리:
  1. [feat] OAuth 로직 추가
  2. [refactor] Header 컴포넌트 네이밍 개선
  3. [chore] OAuth 라이브러리 의존성 추가
  4. [docs] OAuth 설치 방법 문서화
```

### 3. 커밋 메시지 생성 규칙

#### 형식
```
[type] [JIRA-123] summary

상세 설명 (필요시)

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>
```

#### 규칙
- **요약(summary)**: 50자 내외, 마침표 없음
- **type**: feat, fix, refactor, docs, chore, hotfix 중 하나
- **JIRA 이슈**:
  - 브랜치명에서 자동 감지 (예: `feature/ABC-123-login` → `[ABC-123]`)
  - 감지되지 않으면 생략 (소규모 작업)
- **상세 설명**: 복잡한 변경이면 한 줄 띄고 추가
- **Claude 서명**: 항상 마지막에 추가

#### 예시
```
[feat] [ABC-101] 로그인 시 토큰 재발급 로직 추가

기존 토큰 만료 시 자동으로 refresh token을 사용하여
새로운 access token을 발급받도록 구현

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>
```

### 4. 커밋 계획 제시

사용자에게 다음 정보를 제시하고 확인:

```markdown
## 📋 커밋 계획

분석 결과: 총 N개의 커밋으로 분리합니다.

### 커밋 1: [feat] [ABC-123] 로그인 기능 추가
- src/components/Login.tsx
- src/services/auth.ts

### 커밋 2: [chore] [ABC-123] 인증 라이브러리 의존성 추가
- package.json

### 커밋 3: [docs] 로그인 사용 방법 문서화
- README.md

위 계획대로 커밋을 생성합니다.
```

### 5. 순차적 커밋 실행

각 커밋 그룹별로:

1. **파일 스테이징**: `git add [해당 파일들]`
2. **커밋 실행**: `git commit -m "..."` (메시지는 heredoc 사용)
3. **결과 확인**: `git log -1 --oneline`으로 커밋 확인
4. **다음 커밋으로 진행**

#### 커밋 명령어 예시
```bash
git add src/components/Login.tsx src/services/auth.ts

git commit -m "$(cat <<'EOF'
[feat] [ABC-123] 로그인 시 토큰 재발급 로직 추가

기존 토큰 만료 시 자동으로 refresh token을 사용하여
새로운 access token을 발급받도록 구현

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>
EOF
)"
```

### 6. 완료 보고

모든 커밋 완료 후:
- 생성된 커밋 목록 출력
- `git log --oneline -N` 으로 최근 커밋 히스토리 표시
- 다음 단계 안내 (push, PR 생성 등)

## 지능형 분석 요구사항

### 파일 변경 내용 상세 분석
- 단순히 파일명만 보지 말고 **실제 diff 내용**을 읽어야 함
- 같은 파일 내에서도 여러 성격의 변경이 있을 수 있음
- 예: `Login.tsx`에서 기능 추가 + 네이밍 변경 → 가능하면 분리, 어렵면 주된 성격으로 판단

### 논리적 그룹화
- 서로 연관된 파일들은 하나의 커밋으로
- 예: 컴포넌트 + 해당 컴포넌트의 스타일 + 해당 컴포넌트의 타입 정의 → 하나의 `feat` 커밋

### Edge Case 처리
- **모든 변경이 같은 성격**: 커밋 1개로 충분
- **문서 + 코드 혼재**: 분리 (docs / feat)
- **의존성 + 해당 기능 코드**: 분리 가능하면 분리, 밀접하면 하나로
- **너무 많은 변경**: 5개 이상의 커밋으로 분리되면 경고 (리뷰 어려움)

### JIRA 이슈 번호 자동 감지
1. 현재 브랜치명 확인: `git branch --show-current`
2. 패턴 매칭: `feature/ABC-123-description`, `fix/DEF-456-bug-fix` 등
3. 정규식 추출: `[A-Z]+-\d+`
4. 감지되면 모든 커밋에 자동 포함
5. 감지 안 되면 생략 (소규모 작업으로 간주)

## 사용자 인터랙션

### 자동 실행 모드 (기본)
- 분석 후 커밋 계획만 보여주고 바로 실행
- 사용자가 중단을 원하면 언제든 Ctrl+C 가능

### 확인 모드 (선택)
- 만약 커밋 분리가 3개 이상이거나 복잡하면
- "위 계획으로 진행할까요?" 물어봄
- 사용자가 수정 요청하면 조정

## 주의사항

1. **변경사항 없음**: staged/unstaged 변경이 없으면 안내하고 종료
2. **이미 staged된 파일**: 기존 staging은 유지하되, 필요시 unstage 후 재분류
3. **.gitignore 파일**: 무시된 파일은 커밋 대상에서 제외
4. **Binary 파일**: 이미지, 폰트 등은 별도 커밋으로 분리 (`chore: 리소스 추가`)
5. **안전성**: 커밋 실패 시 즉시 중단하고 에러 보고

## 실행 방법

```
/commit-generate
```

단순히 위 명령어만 입력하면:
1. 변경사항 자동 분석
2. 커밋 계획 제시
3. 자동으로 커밋 생성
4. 결과 보고

## 예외 처리

- **변경사항 없음**: "커밋할 변경사항이 없습니다" 안내
- **커밋 실패**: Git 에러 메시지와 함께 중단
- **브랜치가 main/master**: 경고 표시 (실수 방지)
- **Merge conflict 상태**: "충돌을 먼저 해결하세요" 안내

## 설계 철학

### One Commit = One Intent
- 하나의 커밋은 하나의 목적만 가져야 함
- 리뷰어가 각 커밋을 독립적으로 이해할 수 있어야 함

### Clean History
- 커밋 히스토리가 프로젝트의 진화 과정을 명확히 보여줘야 함
- `git log --oneline`만 봐도 무슨 일이 있었는지 파악 가능

### Review-Friendly
- PR에서 커밋 단위로 리뷰하기 쉽게
- 불필요한 커밋 혼합 방지

## 후속 작업 안내

커밋 완료 후 사용자에게 안내:

```
✅ 총 3개의 커밋이 생성되었습니다.

다음 작업:
- 원격에 푸시: git push
- PR 생성: /pr-generate
- 커밋 수정이 필요하면: git reset --soft HEAD~N
```
