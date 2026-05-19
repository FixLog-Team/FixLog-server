# Commit Message Convention

이 문서는 **커밋 로그만으로 변경 의도와 맥락을 빠르게 파악**하기 위한 규칙이다.  
Jira 이슈 추적, 릴리즈/핫픽스 상황에서도 **일관된 커밋 이력 관리**를 목표로 한다.

---

## 1. Goals (목적)

- 커밋 로그만 보고도 **무엇이 변경되었는지** 즉시 이해한다
- Jira 이슈 ↔ 커밋을 연결해 **변경 추적 가능성**을 확보한다
- 릴리즈 / 핫픽스 상황에서도 **형식이 무너지지 않도록** 한다

---

## 2. Core Principles (기본 원칙)

1. **One Commit = One Intent**
   - 한 커밋에는 하나의 목적만 담는다 (기능 / 버그 / 리팩터링 등)

2. **Issue-driven Development**
   - 가능하면 작업 전 Jira 이슈를 생성하고 해당 이슈 기준으로 작업한다

3. **Describe WHAT, not HOW**
   - 구현 방식보다 **변경 결과와 의도**를 중심으로 작성한다

4. **Avoid Mixed Commits**
   - 성격이 다른 변경은 커밋을 분리한다  
     (예: `feat` + `style` → 분리 권장)

5. **Logic Impact Matters**
   - 동작이 바뀌지 않으면 `refactor`
   - 동작/결과가 바뀌면 `fix` 또는 `feat`

---

## 3. Commit Message Format (권장 형식)

### Bracket-based Format

```
[type] [JIRA-123] summary
```

예시

```
[feat] [ABC-1] 로그인 시 토큰 재발급 로직 추가
```

### Rules

- Subject(요약)는 **50자 내외**
- 마침표(`.`)는 사용하지 않음 (로그 가독성)
- 상세 설명이 필요하면 한 줄 띄고 본문 작성
- 필요 시 Footer에 관련 이슈 또는 Breaking Change 명시

---

## 4. Commit Type Rules

| type       | 사용 목적                                   |
| ---------- | ------------------------------------------- |
| `feat`     | 사용자 관점의 기능 추가 또는 변경           |
| `fix`      | 버그 수정, 잘못된 동작 수정                 |
| `refactor` | **동작 변경 없이** 구조/가독성 개선         |
| `docs`     | 문서 수정 (README, Wiki 등)                 |
| `chore`    | 빌드, 설정, 의존성, 스크립트 등 운영성 작업 |
| `hotfix`   | 배포 후 긴급 수정 (운영 이슈 대응)          |

### refactor 기준

- 함수 분리
- 중복 제거
- 네이밍 개선
- 구조 정리  
  ➡️ **동작이 100% 동일해야 함**

---

## 5. Hotfix Rules (운영 기준)

- `hotfix`는 **배포 이후 긴급 수정**에만 사용
- 가능하면 **Jira 이슈를 생성한 후 작업**
- 일반 `fix`와 구분하여 릴리즈 히스토리를 명확히 한다

---

## 6. Jira Issue Number Rules

### 기본 규칙

- 모든 커밋에 Jira 이슈 번호 포함을 원칙으로 한다
- 형식: `ABC-123`

### 예외 (팀 합의 시)

- 오타 수준의 문서 수정
- 영향 범위가 극히 작은 작업

> 기준이 애매하면 **포함하는 방향**을 선택한다

---

## 7. Commit Message Examples

### Feature

```
[feat] [ABC-101] 로그인 시 토큰 재발급 로직 추가
```

### Bug Fix

```
[fix] [ABC-115] 중복 결제 요청 시 예외 처리 누락 수정
```

### Refactor (No Behavior Change)

```
[refactor] [ABC-120] 결제 검증 로직 메서드 분리 및 네이밍 정리
```

### Docs

```
[docs] [ABC-130] 로컬 실행 방법 및 환경변수 설명 추가
```

### Chore

```
[chore] [ABC-140] 의존성 버전 업데이트 및 불필요 패키지 제거
```

### Hotfix

```
[hotfix] [ABC-201] 운영 환경 NullPointerException 긴급 수정
```

---

## 8. Anti-patterns (금지 / 주의)

### ❌ 의미 없는 메시지

```
[fix] [ABC-10] 수정
[refactor] 정리
```

### ❌ 성격 혼합 커밋

- feat + style + chore 혼합
- 가능하면 목적별로 커밋 분리

### ❌ refactor로 버그 수정

- 버그가 고쳐졌다면 `fix` 사용
- 또는 refactor / fix 커밋 분리
