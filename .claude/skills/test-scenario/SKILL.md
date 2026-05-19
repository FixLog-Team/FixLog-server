---
name: test-scenario
description: JIRA 이슈 기반으로 TDD 테스트 시나리오 설계 (단위/통합, 실패 케이스 중심)
disable-model-invocation: true
allowed-tools: Bash(git *), Read, Glob, Grep, mcp__atlassian__*
---

# 테스트 시나리오 설계기

JIRA 이슈를 조회하고 변경된 코드를 분석하여, TDD 관점의 테스트 시나리오를 설계합니다.
정상 케이스보다 **실패·예외 케이스를 선제적으로 정의**하는 것이 핵심입니다.

---

## 작업 순서

### 1. JIRA 이슈 번호 추출

- `git branch --show-current` 로 현재 브랜치 확인
- 브랜치명에서 JIRA 이슈 번호 추출 (패턴: `KAN-숫자`, 예: `KAN-44-self-review` → `KAN-44`)
- 추출된 이슈 번호가 없으면 사용자에게 직접 입력 요청

---

### 2. JIRA 티켓 조회

- Atlassian MCP 도구가 사용 가능한지 확인한다.
- 사용 가능하면 `mcp__atlassian__jira_get_issue`로 티켓 정보 조회
  - 티켓 제목, 설명, 인수 조건(Acceptance Criteria), 상태 등 수집
- MCP 인증이 필요하면 `mcp__atlassian__authenticate`로 인증 후 재조회
- 조회 실패 시 "JIRA 티켓 정보 없음"으로 표시하고 코드 분석만으로 진행

---

### 3. 변경된 소스 파일 수집

- `git diff dev..HEAD --name-only` 로 변경 파일 목록 수집 (`.java` 파일만)
- 변경 파일이 없으면 `git status --short` 로 미커밋 파일 확인
- 모든 변경된 `.java` 파일을 Read 도구로 **직접 전부 읽어서** 구현 내용 파악
  - Entity, Service, Repository, Controller, DTO 각 역할 구분
  - 핵심 비즈니스 로직, 유효성 검사 조건, 예외 처리 흐름 파악

---

### 4. 기존 테스트 파일 확인

- `src/test/` 디렉토리에 이미 존재하는 테스트 파일 Glob으로 탐색
- 중복 시나리오를 방지하기 위해 기존 테스트의 커버리지 파악

---

### 5. 테스트 시나리오 설계

아래 기준으로 시나리오를 분류하고 설계한다.

#### 5.1 테스트 계층 분류

| 계층 | 어노테이션 | 목적 |
|------|-----------|------|
| 단위 테스트 — Service | `@ExtendWith(MockitoExtension.class)` | 비즈니스 로직 단독 검증, Repository Mocking |
| 단위 테스트 — Domain/Entity | 순수 JUnit 5 | Entity 도메인 메서드 검증 |
| 슬라이스 테스트 — Controller | `@WebMvcTest` + MockMvc | HTTP 요청/응답 형태 검증 |
| 슬라이스 테스트 — Repository | `@DataJpaTest` | 쿼리 메서드 검증 |
| 통합 테스트 | `@SpringBootTest` | 전체 흐름 검증 |

#### 5.2 시나리오 우선순위 원칙

1. **실패 케이스 우선**: 정상 케이스보다 실패·경계·예외 시나리오를 먼저 나열
2. **인증/권한 케이스 필수**: Spring Security가 적용된 프로젝트이므로 미인증·권한 없음 케이스 반드시 포함
3. **입력 유효성 검사**: `@Valid` 규칙 위반 케이스 (null, 빈 문자열, 길이 초과 등)
4. **비즈니스 규칙 위반**: 존재하지 않는 리소스 접근, 소유자 불일치, 중복 생성 등
5. **정상 케이스**: 마지막에 핵심 정상 흐름만 포함

#### 5.3 Given-When-Then 형식으로 작성

각 시나리오는 다음 형식으로 작성:

```
테스트 메서드명: should_[기대결과]_when_[조건]
  Given: [사전 조건 — Mock 설정, DB 상태, 인증 정보]
  When:  [실행 — 메서드 호출, HTTP 요청]
  Then:  [기대 결과 — 예외, 응답 코드, 반환값]
```

---

### 6. 리포트 출력

아래 형식으로 테스트 시나리오를 출력한다.

```markdown
# 테스트 시나리오 설계 리포트

**브랜치**: [브랜치명]
**JIRA 이슈**: [KAN-XX] [티켓 제목]
**분석 대상 파일**: [변경된 .java 파일 목록]
**설계 기준일**: [오늘 날짜]

---

## 개요

[JIRA 티켓 내용 및 변경 코드 분석 요약]

---

## [A] 단위 테스트 (Unit Test)

### A-1. [XxxService] 테스트

**테스트 파일**: `src/test/java/com/fixlog/.../XxxServiceTest.java`
**설정**: `@ExtendWith(MockitoExtension.class)`

#### 실패 케이스

| # | 메서드명 | Given | When | Then |
|---|---------|-------|------|------|
| F-01 | `should_throw_when_folderNotFound` | 존재하지 않는 folderId | `getFolder()` 호출 | `BusinessException(Code.NOT_FOUND)` |
| F-02 | `should_throw_when_unauthorized` | SecurityUtil이 null 반환 | `createFolder()` 호출 | `BusinessException(Code.UNAUTHORIZED)` |
| F-03 | `should_throw_when_parentFolderNotFound` | parentId가 존재하지 않음 | `createFolder()` 호출 | `BusinessException(Code.NOT_FOUND)` |

#### 정상 케이스

| # | 메서드명 | Given | When | Then |
|---|---------|-------|------|------|
| S-01 | `should_createFolder_successfully` | 유효한 request, 인증된 userId | `createFolder()` 호출 | FolderEntity 반환, `save()` 1회 호출 |

---

### A-2. [XxxEntity] 도메인 메서드 테스트

**테스트 파일**: `src/test/java/com/fixlog/.../XxxEntityTest.java`
**설정**: 순수 JUnit 5

| # | 메서드명 | Given | When | Then |
|---|---------|-------|------|------|
| D-01 | `should_setUsableToZero_when_softDelete` | FolderEntity 생성 (usable=1) | `softDelete("user")` 호출 | `usable == 0`, `updateUser == "user"` |
| D-02 | `should_updateFields_when_updateFolder` | FolderEntity 생성 | `updateFolder(name, ...)` 호출 | 필드 업데이트 확인 |

---

## [B] 슬라이스 테스트 (Slice Test)

### B-1. [XxxController] MockMvc 테스트

**테스트 파일**: `src/test/java/com/fixlog/.../XxxControllerTest.java`
**설정**: `@WebMvcTest(XxxController.class)` + `@MockBean XxxService`

#### 인증/권한 실패 케이스

| # | 메서드명 | 요청 | Then |
|---|---------|------|------|
| C-F01 | `should_return401_when_notAuthenticated` | 토큰 없이 `POST /api/xxx` | HTTP 401 |
| C-F02 | `should_return403_when_forbidden` | 권한 없는 사용자 | HTTP 403 |

#### 입력 유효성 실패 케이스

| # | 메서드명 | 요청 Body | Then |
|---|---------|----------|------|
| C-F03 | `should_return400_when_requiredFieldNull` | `folderName: null` | HTTP 400, 공통 에러 응답 |
| C-F04 | `should_return400_when_requiredFieldBlank` | `folderName: ""` | HTTP 400 |

#### 비즈니스 실패 케이스

| # | 메서드명 | Mock 설정 | Then |
|---|---------|----------|------|
| C-F05 | `should_return404_when_resourceNotFound` | Service가 `BusinessException(NOT_FOUND)` throw | HTTP 404 |
| C-F06 | `should_return409_when_conflict` | Service가 `BusinessException(CONFLICT)` throw | HTTP 409 |

#### 정상 케이스

| # | 메서드명 | 요청 | Then |
|---|---------|------|------|
| C-S01 | `should_return200_when_createSuccess` | 유효한 Body | HTTP 200, DataResponse 형식 확인 |
| C-S02 | `should_return200_when_deleteSuccess` | 유효한 Path Variable | HTTP 200, Response 형식 확인 |

---

### B-2. [XxxRepository] 쿼리 테스트

**테스트 파일**: `src/test/java/com/fixlog/.../XxxRepositoryTest.java`
**설정**: `@DataJpaTest`

| # | 메서드명 | Given | When | Then |
|---|---------|-------|------|------|
| R-01 | `should_returnEmpty_when_notFound` | 저장된 데이터 없음 | `findByXxxId()` 호출 | `Optional.empty()` 반환 |
| R-02 | `should_returnEntity_when_exists` | 엔티티 저장 후 | `findByXxxId()` 호출 | 저장된 엔티티 반환 |
| R-03 | `should_excludeSoftDeleted_when_queryUsable` | usable=0인 엔티티 | `findByUsable(1)` 호출 | 소프트 삭제된 항목 제외 |

---

## [C] 통합 테스트 (Integration Test)

**테스트 파일**: `src/test/java/com/fixlog/.../XxxIntegrationTest.java`
**설정**: `@SpringBootTest(webEnvironment = RANDOM_PORT)`

| # | 시나리오 | 흐름 | 검증 |
|---|---------|------|------|
| I-01 | 전체 생성 흐름 | 인증 → 요청 → DB 저장 확인 | HTTP 200, DB 레코드 존재 |
| I-02 | 중복 생성 방지 | 동일 조건으로 2회 생성 시도 | 두 번째 요청 HTTP 409 |
| I-03 | 소프트 삭제 후 조회 | 삭제 후 목록 조회 | 삭제된 항목 미포함 |

---

## 우선순위 요약

| 우선순위 | 테스트 | 이유 |
|---------|--------|------|
| P0 (즉시) | C-F01, C-F02 (인증/권한) | 보안 기본 |
| P0 (즉시) | C-F03, C-F04 (입력 검증) | API 안정성 |
| P1 (이번 PR) | A-1 서비스 실패 케이스 전체 | 비즈니스 규칙 보호 |
| P1 (이번 PR) | A-2 도메인 메서드 | Entity 행동 보호 |
| P2 (다음 PR) | B-2 Repository, C 통합 테스트 | 회귀 방지 |

---

## 테스트 코드 스켈레톤 생성 여부

위 시나리오를 바탕으로 테스트 파일 스켈레톤을 생성할 수 있습니다.
생성을 원하면: **"스켈레톤 생성해줘"**
특정 계층만 원하면: **"A-1 서비스 테스트 스켈레톤만 생성해줘"**
```

---

### 7. 테스트 스켈레톤 생성 (선택)

사용자가 스켈레톤 생성을 요청한 경우:

- 시나리오 리포트를 바탕으로 테스트 파일을 실제로 생성한다.
- 파일 위치: `src/test/java/com/fixlog/[레이어]/[도메인]/[XxxTest].java`
- 메서드 본문은 `// TODO: implement` + Given/When/Then 주석으로 채운다.

#### 스켈레톤 템플릿

**Service 단위 테스트**:
```java
@ExtendWith(MockitoExtension.class)
class XxxServiceTest {

    @InjectMocks
    private XxxService xxxService;

    @Mock
    private XxxRepository xxxRepository;

    @Test
    @DisplayName("F-01: [조건]일 때 [예외]를 던진다")
    void should_throw_when_condition() {
        // Given
        given(xxxRepository.findById(any())).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> xxxService.method(param))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("code", Code.NOT_FOUND);
    }
}
```

**Controller 슬라이스 테스트**:
```java
@WebMvcTest(XxxController.class)
class XxxControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private XxxService xxxService;

    @Test
    @DisplayName("C-F01: 인증 없이 요청 시 401 반환")
    void should_return401_when_notAuthenticated() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/xxx")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }
}
```

**Repository 슬라이스 테스트**:
```java
@DataJpaTest
class XxxRepositoryTest {

    @Autowired
    private XxxRepository xxxRepository;

    @Test
    @DisplayName("R-01: 존재하지 않는 ID 조회 시 Optional.empty() 반환")
    void should_returnEmpty_when_notFound() {
        // When
        Optional<XxxEntity> result = xxxRepository.findById("non-existent-id");

        // Then
        assertThat(result).isEmpty();
    }
}
```

---

## 주의사항

1. **JIRA 티켓 우선**: 티켓의 인수 조건(Acceptance Criteria)이 있으면 그것을 테스트의 1차 기준으로 삼는다
2. **실제 코드 기반**: 파일 목록만 보지 말고 구현 코드를 반드시 Read로 읽어 분석한다
3. **실패 케이스 비율**: 전체 시나리오의 60% 이상은 실패·예외 케이스로 구성한다
4. **Security 필수**: Spring Security가 적용된 프로젝트이므로 모든 Controller 테스트에 인증 케이스 포함
5. **기존 테스트 중복 방지**: `src/test/` 를 먼저 탐색하여 이미 작성된 시나리오는 제외한다
6. **스켈레톤 위치**: `src/test/java/com/fixlog/` 하위에 레이어별로 분리하여 생성

## 실행 방법

```
/test-scenario
```

스켈레톤까지 바로 생성하려면:

```
/test-scenario 스켈레톤도 생성해줘
```