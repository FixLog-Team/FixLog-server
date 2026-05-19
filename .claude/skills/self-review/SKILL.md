---
name: self-review
description: Review changed .java files against FixLog backend dev-guidelines (project structure + coding convention)
disable-model-invocation: true
allowed-tools: Bash(git *), Read, Glob, Grep
---

# 셀프 리뷰 (백엔드 가이드라인 준수 검사)

현재 브랜치에서 변경된 `.java` 파일들이 FixLog 백엔드 dev-guidelines를 준수하는지 자동으로 검사합니다.

---

## 작업 순서

### 1. 변경 범위 수집

- `git log dev..HEAD --format="%H %s"` 로 dev 대비 커밋 목록 확인
- `git diff dev..HEAD --name-only` 로 변경된 파일 전체 목록 수집 (`.java` 파일만 검사)
- `git status --short` 로 미커밋 파일 확인 → 있으면 리뷰 범위에 포함, 리포트 상단에 명시
- 변경된 파일들의 내용을 Read 도구로 **직접 전부 읽어서** 분석 (파일 목록만 보고 판단 금지)

---

### 2. 검사 항목

변경된 `.java` 파일에 대해 아래 두 카테고리를 모두 검사한다.

- **[A] 백엔드 구조** — 패키지 위치, 레이어 역할, 의존 방향 준수 여부
- **[B] 백엔드 코드 규칙** — 코딩 컨벤션, 네이밍, JPA, 트랜잭션, 로깅 등

---

## [A] 백엔드 구조 (FIXLOG-BACKEND-PROJECT-STRUCTURE-GUIDE.md)

각 `.java` 파일의 패키지 위치와 레이어 역할이 규칙에 맞는지 확인한다.

### 레이어 역할 규칙

| 레이어 | 허용 | 금지 |
|--------|------|------|
| `domain/model/` | JPA Entity, Enum, 복합 키 클래스, 도메인 행동 메서드 | 외부 레이어 참조(application/presentation), HTTP 관련 코드 |
| `application/service/` | 비즈니스 유스케이스 흐름, 유효성 검사, SecurityUtil 호출, BusinessException throw | HTTP 상태코드 결정, JSON 변환, Response 타입 반환 |
| `application/repository/` | Spring Data JPA 인터페이스, @Query 메서드 | 비즈니스 로직, 예외 처리 |
| `presentation/controller/` | @RestController, Service 호출, DTO 변환 후 반환 | 비즈니스 로직, DB 직접 접근, 예외 직접 처리 |
| `presentation/dto/request/` | record, @Valid 입력 검증 어노테이션 | 비즈니스 로직 |
| `presentation/dto/response/` | record, static from() 팩토리 메서드 | 비즈니스 로직 |
| `common/` | 응답 포맷, 에러 코드, 전역 예외 처리, 보안 설정 | 도메인 의미 있는 코드 |

### 의존 방향 규칙

```
presentation → application → domain
     ↓                ↓
   common           common
```

- `domain`은 어느 레이어도 참조하지 않는다.
- `application`은 `presentation`을 참조하지 않는다.
- `common`은 모든 레이어에서 참조 가능하다.

### 파일명 / 클래스명 규칙

- Entity 클래스: `[Domain]Entity` (예: `FolderEntity`)
- Service 클래스: `[Domain]Service` (예: `FolderService`)
- Repository 인터페이스: `[Domain]Repository` (예: `FolderRepository`)
- Controller 클래스: `[Domain]Controller` (예: `FolderController`)
- Request DTO: `[Domain]Request` (예: `FolderRequest`)
- Response DTO: `[Domain]Dto` (예: `FolderDto`)

### 체크포인트

- [ ] 파일 패키지 위치가 해당 레이어 역할에 맞는가?
- [ ] Controller가 Entity를 직접 반환하지 않는가? (반드시 DTO로 감싼다)
- [ ] Controller에 비즈니스 로직이 직접 들어가지 않았는가?
- [ ] Service가 `Response` / `DataResponse` 타입을 반환하지 않는가?
- [ ] `domain → presentation` 역방향 의존이 없는가? (Entity가 Request DTO를 파라미터로 받는 등)
- [ ] `application → presentation` 역방향 의존이 없는가?
- [ ] Response DTO에 `static from()` 팩토리 메서드가 있는가?
- [ ] DTO가 Java record로 작성되었는가?
- [ ] `SecurityUtil`은 Service에서만 호출하는가? (Controller에서 직접 호출 금지)
- [ ] `common/`에 도메인 의미가 있는 코드가 들어가지 않았는가?

## [B] 백엔드 코드 규칙 (Java / Spring Boot 전용)

`.java` 파일 변경 시 적용한다. 변경된 파일 코드를 실제로 읽으며 아래 규칙을 확인한다.

---

### B-01. Entity 직접 반환 금지

**규칙:**
- Controller는 항상 `Response` 또는 `DataResponse`로 감싸 반환한다.
- Entity → DTO 변환은 `from()` 팩토리 메서드를 사용한다.

**체크:**
- [ ] Controller 메서드의 반환 타입이 `Response` / `DataResponse`인가?
- [ ] Entity가 직접 JSON으로 노출되지 않는가?

**금지 패턴:**
```java
// 금지: Entity 직접 반환
@GetMapping("/{id}")
public FolderEntity getFolder(...) { ... }
```

---

### B-02. Controller에 비즈니스 로직 금지

**규칙:**
- Controller는 Service 호출 + DTO 변환만 한다.
- 유효성 검사, 인증 확인, DB 접근은 Controller에 두지 않는다.

**체크:**
- [ ] Controller 메서드 내부에 if 분기로 비즈니스 규칙을 직접 처리하지 않는가?
- [ ] Controller에서 Repository를 직접 주입·호출하지 않는가?
- [ ] try-catch로 예외를 직접 처리하지 않는가? (GlobalExceptionHandler가 담당)

**금지 패턴:**
```java
// 금지: Controller에서 직접 유효성 검사
@PostMapping
public Response createFolder(@RequestBody FolderRequest request) {
    if (request.folderName() == null) {
        return Response.failure("폴더명 필수");
    }
    folderRepository.save(...); // DB 직접 접근
}
```

---

### B-03. Service 반환 타입 규칙

**규칙:**
- Service는 도메인 객체(Entity, List 등)를 반환한다.
- `Response`, `DataResponse` 같은 HTTP 응답 타입을 반환하지 않는다.

**체크:**
- [ ] Service 메서드 반환 타입이 Entity 또는 도메인 타입인가?
- [ ] Service가 HTTP 응답 형태를 알지 못하는가?

**금지 패턴:**
```java
// 금지: Service가 HTTP 응답 타입 반환
public Response createFolder(FolderRequest request) { ... }
```

---

### B-04. 예외 처리 규칙

**규칙:**
- 비즈니스 예외는 `BusinessException(Code, message)`을 사용한다.
- HTTP 상태코드를 Service/Controller에서 직접 결정하지 않는다.
- `GlobalExceptionHandler`가 `BusinessException`을 응답으로 변환한다.

**체크:**
- [ ] 예외 발생 시 `BusinessException`을 사용하는가?
- [ ] 새로운 에러 케이스가 생겼다면 `Code` enum에 추가되었는가?
- [ ] Controller에서 try-catch로 예외를 직접 처리하지 않는가?

---

### B-05. 매직 넘버 / 매직 문자열 금지

**규칙:**
- 의미 있는 숫자와 문자열은 상수나 enum으로 추출한다.
- 컬럼 최대 길이, 기본값, 상태 코드 등은 이름이 있어야 한다.

**체크:**
- [ ] `usable = 1`, `usable = 0` 같은 숫자 리터럴이 의미 없이 반복되지 않는가?
- [ ] `length = 100` 같은 컬럼 크기가 여러 곳에 중복 박혀 있지 않은가?
- [ ] 의미를 모르는 숫자 리터럴이 코드에 직접 등장하지 않는가?

---

### B-06. 단일 책임

**규칙:**
- Service 메서드 하나는 하나의 유스케이스만 처리한다.
- Entity 메서드 하나는 하나의 도메인 행동만 담는다.
- "이 메서드를 바꿀 이유가 두 가지 이상인가?" 싶으면 분리를 검토한다.

**체크:**
- [ ] 하나의 Service 메서드가 여러 다른 비즈니스 유스케이스를 동시에 처리하지 않는가?
- [ ] Entity 메서드가 여러 다른 도메인 개념을 섞어서 처리하지 않는가?

---

### B-07. 조건에 이름 붙이기

**규칙:**
- 복합 조건(`&&`, `||`, 중첩 if)은 의미 있는 변수나 메서드로 뽑는다.
- 불리언 변수는 `is`, `has`, `can` 접두어 사용.

**체크:**
- [ ] 중첩 조건식이 의미 있는 변수명이나 메서드로 표현되어 있는가?
- [ ] `!entity.getUsable().equals(0) && entity.getWorkspaceId() != null` 같은 중첩 조건이 직접 박혀 있지 않은가?

---

### B-08. @Transactional 적절한 사용

**규칙:**
- 조회 전용 메서드는 `@Transactional(readOnly = true)`를 사용한다.
- 쓰기 작업은 `@Transactional`을 사용한다.
- Controller에 `@Transactional`을 붙이지 않는다.
- 트랜잭션 전파/격리 수준 변경은 원칙적으로 금지 (필요 시 근거 + 주석 문서화).

**체크:**
- [ ] 조회 메서드에 `readOnly = true`가 적용되어 있는가?
- [ ] Controller 클래스/메서드에 `@Transactional`이 붙어 있지 않는가?
- [ ] 전파/격리 수준을 변경한 경우 근거 주석이 있는가?

---

### B-09. 네이밍 컨벤션

**규칙:**
- 클래스/인터페이스: `PascalCase`
- 메서드/변수: `camelCase`
- 상수: `UPPER_SNAKE_CASE`
- 패키지: `lowercase`
- Boolean 변수/메서드: `is`, `has`, `can`, `should` 접두어 사용
- Request DTO: `XxxRequest`, Response DTO: `XxxResponse` 또는 `XxxDto`, 내부 전달 DTO: `XxxDto`
- 비즈니스 예외 클래스: `XxxException`

**체크:**
- [ ] 클래스명이 PascalCase를 따르는가?
- [ ] 메서드/변수명이 camelCase를 따르는가?
- [ ] 상수(`static final`)가 UPPER_SNAKE_CASE를 따르는가?
- [ ] Boolean 반환 메서드/변수에 `is`/`has`/`can`/`should` 접두어가 있는가?
- [ ] DTO 클래스명이 `XxxRequest` / `XxxResponse` / `XxxDto` 규칙을 따르는가?
- [ ] 예외 클래스명이 `XxxException`으로 끝나는가?

**금지 패턴:**
```java
// 금지: 맥락 없는 이름, 대소문자 규칙 위반
boolean flag = ...;           // → isActive, hasPermission 등
static final int max = 100;   // → MAX_FOLDER_NAME_LENGTH
class folderRequest { ... }   // → FolderRequest
```

---

### B-10. 메서드 크기 / 중첩 depth

**규칙:**
- 메서드 길이: 50줄 권장 (초과 시 분리 검토)
- 중첩 depth: 3 이상 지양
- 한 메서드의 책임: "한 가지 일"만 유지

**체크:**
- [ ] 메서드 길이가 50줄을 크게 초과하지 않는가?
- [ ] if/for/while 중첩이 3단계 이상 이어지지 않는가?
- [ ] 하나의 메서드가 여러 다른 역할을 동시에 하지 않는가?

---

### B-11. Guard Clause (Early Return)

**규칙:**
- 조건 분기는 Guard clause(early return) 방식을 우선 사용한다.
- 복잡한 조건식은 의미 있는 변수로 추출한다.

**체크:**
- [ ] 비정상 케이스를 함수 앞에서 먼저 처리하고 있는가?
- [ ] else 중첩 대신 early return을 사용하는가?
- [ ] 복합 조건(`&&`, `||`, 중첩 if)에 의미 있는 변수명이 붙어 있는가?

**금지 패턴:**
```java
// 금지: else 중첩
if (condition) {
    if (otherCondition) {
        // 핵심 로직
    }
}

// 권장: early return
if (!condition) throw new BusinessException(...);
if (!otherCondition) throw new BusinessException(...);
// 핵심 로직
```

---

### B-12. @Valid + Bean Validation

**규칙:**
- Controller에서 `@RequestBody` 파라미터에 `@Valid`를 붙인다.
- 입력 검증은 Bean Validation 어노테이션(`@NotNull`, `@NotBlank`, `@Size` 등)을 사용한다.
- 검증 실패 응답은 `GlobalExceptionHandler`가 공통 포맷으로 처리한다.

**체크:**
- [ ] `@RequestBody` 파라미터에 `@Valid`가 붙어 있는가?
- [ ] 필수 필드에 `@NotNull` / `@NotBlank` 등 검증 어노테이션이 있는가?
- [ ] Controller에서 직접 null 체크를 하는 대신 Bean Validation을 활용하는가?

**금지 패턴:**
```java
// 금지: @Valid 없이 null 직접 체크
@PostMapping
public Response create(@RequestBody FolderRequest request) {
    if (request.folderName() == null) return Response.failure("필수");
}

// 권장
@PostMapping
public Response create(@Valid @RequestBody FolderRequest request) { ... }
```

---

### B-13. JPA Entity 기본 규칙

**규칙:**
- 기본 생성자는 `protected`로 선언한다 (직접 생성 방지).
- Fetch 전략: 기본 `LAZY`, `EAGER` 금지 (예외는 근거 주석 필수).
- `equals`/`hashCode`는 팀 정책(id 기반)에 따라 일관되게 구현한다.

**체크:**
- [ ] 기본 생성자가 `protected` 또는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`인가?
- [ ] `@OneToMany`, `@ManyToOne`에 `fetch = FetchType.EAGER`가 쓰이지 않는가?
- [ ] EAGER를 사용한다면 근거 주석이 있는가?

**금지 패턴:**
```java
// 금지: public 기본 생성자, EAGER
public FolderEntity() {}
@OneToMany(fetch = FetchType.EAGER)
```

---

### B-14. N+1 방지

**규칙:**
- 조회 API는 N+1 여부를 항상 점검한다.
- 허용 패턴: `fetch join` / `@EntityGraph` / 배치 사이즈.
- 금지 패턴: 무분별한 `EAGER`로 해결.

**체크:**
- [ ] 컬렉션 연관관계를 루프 안에서 접근하는 N+1 패턴이 없는가?
- [ ] 연관 엔티티가 필요한 조회라면 `fetch join` 또는 `@EntityGraph`를 사용하는가?
- [ ] N+1 해결책으로 `FetchType.EAGER`를 사용하지 않는가?

---

### B-15. 로깅 규칙

**규칙:**
- 로거: `SLF4J` 사용 (`@Slf4j` 어노테이션 또는 `LoggerFactory`).
- 문자열 더하기(`+`) 금지, 파라미터 바인딩(`{}`) 사용.
- 민감 정보(비밀번호, 토큰, 개인정보)를 로그에 출력하지 않는다.
- 로그 레벨 기준: `DEBUG`(개발/진단), `INFO`(주요 비즈니스 이벤트), `WARN`(복구 가능 이상), `ERROR`(장애/복구 불가).

**체크:**
- [ ] 로그 출력 시 문자열 연결(`+`) 대신 `{}` 바인딩을 사용하는가?
- [ ] 비밀번호, 토큰, 개인정보가 로그에 출력되지 않는가?
- [ ] 로그 레벨이 상황에 맞게 적절히 사용되는가?

**금지 패턴:**
```java
// 금지: 문자열 더하기, 민감정보 로깅
log.info("User " + userId + " token: " + accessToken);

// 권장
log.info("User login: userId={}", userId);
```

---

### B-16. 주석 / TODO 규칙

**규칙:**
- 주석은 "무엇"보다 "왜/의도/제약"을 설명한다.
- `TODO`/`FIXME` 포맷: `TODO(#티켓번호): 내용`
- 코드만 보면 알 수 있는 내용은 주석으로 쓰지 않는다.

**체크:**
- [ ] 주석이 코드 자체를 반복하지 않고 "이유/제약/의도"를 설명하는가?
- [ ] TODO/FIXME에 티켓 번호가 포함되어 있는가?

**금지 패턴:**
```java
// 금지: 코드를 그대로 설명하는 주석
// folder를 저장한다
folderRepository.save(folder);

// TODO: 나중에 고치기 (티켓 없음) ← 금지
// 권장
// TODO(#KAN-123): 소프트 삭제 후 자식 폴더 처리 정책 확정 필요
```

---

### 3. 리뷰 리포트 출력

```markdown
# 셀프 리뷰 리포트

**브랜치**: [브랜치명]
**리뷰 대상 커밋 수**: N개 (dev 대비)
**미커밋 변경사항**: 있음 / 없음
**리뷰 기준일**: [오늘 날짜]

---

## [A] 백엔드 구조

| 파일 | 적합성 | 판단 이유 |
|------|--------|----------|
| com/fixlog/... | ✅ / ⚠️ / ❌ | [이유] |

**소견**: [전체 구조 평가]

---

## [B] 백엔드 코드 규칙

| # | 규칙 | 상태 | 파일:라인 | 구체적 내용 |
|---|------|------|----------|------------|
| B-01 | Entity 직접 반환 금지 | ✅ / ⚠️ / ❌ | - | - |
| B-02 | Controller 비즈니스 로직 금지 | | | |
| B-03 | Service 반환 타입 규칙 | | | |
| B-04 | 예외 처리 규칙 | | | |
| B-05 | 매직 넘버/문자열 금지 | | | |
| B-06 | 단일 책임 | | | |
| B-07 | 조건에 이름 붙이기 | | | |
| B-08 | @Transactional 적절한 사용 | | | |
| B-09 | 네이밍 컨벤션 | | | |
| B-10 | 메서드 크기 / 중첩 depth | | | |
| B-11 | Guard Clause (Early Return) | | | |
| B-12 | @Valid + Bean Validation | | | |
| B-13 | JPA Entity 기본 규칙 | | | |
| B-14 | N+1 방지 | | | |
| B-15 | 로깅 규칙 | | | |
| B-16 | 주석/TODO 규칙 | | | |

**소견**: [전체 백엔드 코드 평가]

---

## 종합 평가

| 카테고리 | 결과 |
|---------|------|
| A. 백엔드 구조 | ✅ 통과 / ⚠️ 검토 / ❌ 위반 / N/A |
| B. 백엔드 코드 규칙 | ✅ 통과 / ⚠️ 검토 / ❌ 위반 / N/A |

### 즉시 수정 필요 (❌)

**[파일:라인] 문제 설명**
수정 방법 설명
> 참고: `docs/dev-guidelines/FIXLOG-BACKEND-PROJECT-STRUCTURE-GUIDE.md` 또는 `docs/dev-guidelines/fundamentals/XX-규칙명.md`

### 검토 권장 (⚠️)

**[파일:라인] 개선 제안**
개선 방향 설명

### 잘 된 점 (✅)
- [가이드라인을 잘 따른 구체적 부분]
```

---

### 4. 수정 필요 항목 처리

- ❌ 항목이 있으면 "수정 후 재실행을 권장합니다" 안내
- 수정이 필요한 파일과 라인, 구체적인 개선 방향 제시
- 사용자가 수정 원하면 직접 코드 수정 도움 제공

---

## 주의사항

1. **실제 코드를 읽어야 함**: 파일 목록만 보고 판단하지 말고, 변경된 `.java` 파일을 반드시 Read로 읽어 분석
2. **[A]+[B] 규칙 전체 검사**: 두 카테고리 모두 하나씩 빠짐없이 확인 (일부만 보는 것 금지)
3. **변경 범위에만 집중**: 변경되지 않은 기존 코드는 검사하지 않음
4. **False Positive 주의**: 명확한 위반만 ❌, 맥락을 고려해 판단
5. **개선 제안은 구체적으로**: "가독성이 나쁨" ❌ → "`FolderService.java:29`의 `1`을 `FOLDER_USABLE` 상수로 추출하세요" ✅
6. **스캐폴딩/미구현 단계 고려**: 아직 구현되지 않은 placeholder 코드는 해당 규칙 N/A로 처리

## 실행 방법

```
/self-review
```
