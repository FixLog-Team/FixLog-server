# FixLog 백엔드 프로젝트 구조 가이드

> 이 문서는 FixLog 백엔드 프로젝트 구조를 정리한 문서이다.
> 목적은 **패키지를 예쁘게 나누는 것**이 아니라,
> **변경 범위가 예측 가능하고**, **함께 바뀌는 코드가 함께 있도록** 구조를 설계하는 것이다.

---

# 1. 핵심 철학

이 구조는 아래 기준을 우선한다.

1. **함께 수정되는 파일은 가깝게 둔다.**
2. `utils`, `helpers`, `managers` 같은 **역할 모호한 창고형 패키지를 피한다.**
3. **레이어 간 의존 방향을 지킨다.** `presentation` → `application` → `domain` 순으로만 흐른다.
4. **`common`은 정말 공통일 때만 사용한다.**
5. 공통화는 무조건 좋은 것이 아니다.
   **같이 바뀌지 않을 가능성이 높다면 중복을 허용한다.**

---

# 2. 최상위 구조

```txt
com.fixlog/
  presentation/
  application/
  domain/
  common/
```

---

# 3. 각 레이어의 역할

## 3.1 `presentation/`
HTTP 요청과 응답을 처리한다.

역할:
- REST Controller
- Request DTO (입력 검증 포함)
- Response DTO (응답 형태 정의)

중요:
- **비즈니스 로직을 직접 담지 않는다.**
- `application/service`를 호출하고 결과를 DTO로 변환해서 반환한다.

---

## 3.2 `application/`
비즈니스 유스케이스를 처리한다.

역할:
- Service (비즈니스 로직)
- Repository 인터페이스

중요:
- **HTTP 요청/응답을 알지 못한다.** (`HttpServletRequest` 등 반입 금지)
- `domain` 모델을 직접 다룬다.
- `presentation`의 DTO를 받아도 되지만, 의존 방향을 주의한다.

---

## 3.3 `domain/`
비즈니스 핵심 개념을 담는다.

역할:
- JPA Entity
- Enum (도메인 상태값)
- 복합 키 클래스 (`@IdClass`)

중요:
- **외부 레이어(presentation, application)에 의존하지 않는다.**
- 엔티티 내부에 도메인 행동을 메서드로 담을 수 있다. (예: `softDelete`, `updateFolder`)
- Entity는 단순한 데이터 컨테이너가 아니라 **행동도 가진 객체**다.

---

## 3.4 `common/`
레이어와 관계없이 **프로젝트 전반에서 공통으로 사용하는 것**을 둔다.

역할:
- 공통 응답 포맷 (`Response`, `DataResponse`)
- 공통 에러 코드 (`Code`)
- 공통 예외 클래스 (`BusinessException`)
- 전역 예외 핸들러 (`GlobalExceptionHandler`)
- 보안 설정 및 유틸 (`SecurityConfig`, `SecurityUtil`)

중요:
- `common`은 특정 도메인 의미를 담으면 안 된다.
- "일단 애매하니 common에 넣자"는 금지한다.

---

# 4. 추천 디렉토리 구조

```txt
com.fixlog/
  presentation/
    controller/
      folder/
        FolderController.java
      document/
        DocumentController.java
      login/
        LoginController.java
    dto/
      request/
        FolderRequest.java
        DocumentRequest.java
      response/
        FolderDto.java
        DocumentDto.java

  application/
    service/
      FolderService.java
      DocumentService.java
      OAuth2UserService.java
    repository/
      FolderRepository.java
      DocumentRepository.java
      UserRepository.java

  domain/
    model/
      FolderEntity.java
      FolderId.java
      DocumentEntity.java
      UserEntity.java
      UserStatus.java

  common/
    response/
      Response.java
      DataResponse.java
    exception/
      BusinessException.java
      GlobalExceptionHandler.java
    code/
      Code.java
    security/
      SecurityConfig.java
      SecurityUtil.java
```

---

# 5. `domain/` 기준으로 이해하기

## 5.1 Entity는 무엇을 담는가?

Entity는 **비즈니스 개념의 영속화 단위**다.

여기에 들어가는 것:
- 컬럼 매핑
- 생성자 (의미 있는 초기화)
- 도메인 행동 메서드

예시:

```java
// domain/model/FolderEntity.java
@Entity
@Table(name = "apj_folder", schema = "public")
@IdClass(FolderId.java)
public class FolderEntity {

    @Id
    private String folderId;

    @Column(nullable = false)
    private String folderName;

    private Integer usable;

    // 의미 있는 생성자 - 생성 시점에 필요한 값을 강제한다
    public FolderEntity(String folderId, String workspaceId, String parentId,
                        String folderName, String createUser) { ... }

    // 도메인 행동 메서드 - 업데이트 로직을 Entity가 직접 담는다
    public void updateFolder(FolderRequest request, String updateUser) { ... }

    // 소프트 삭제 - 삭제 의도를 명확히 표현한다
    public void softDelete(String updateUser) {
        this.usable = 0;
        this.updateUser = updateUser;
        this.updateTime = Instant.now();
    }
}
```

---

## 5.2 Entity 안에 도메인 메서드를 두는 이유

나쁜 예 (Service에서 직접 필드 조작):

```java
// BAD: Service가 Entity 내부를 알아야 한다
folder.setUsable(0);
folder.setUpdateUser(userId);
folder.setUpdateTime(Instant.now());
```

좋은 예 (Entity에 의도를 위임):

```java
// GOOD: 삭제 의도가 메서드 이름에 드러난다
folder.softDelete(userId);
```

즉:
- Entity 메서드 = 도메인 의도를 이름으로 표현
- Service = 언제 호출할지 결정

---

## 5.3 Enum은 어디에 두는가?

도메인 상태값은 `domain/model/` 아래에 둔다.

예:

```java
// domain/model/UserStatus.java
public enum UserStatus {
    ACTIVE,
    INACTIVE,
    DELETED
}
```

---

# 6. `application/` 기준으로 이해하기

## 6.1 Service는 무엇을 담는가?

Service는 **비즈니스 유스케이스를 처리하는 흐름**을 담는다.

여기에 들어가는 것:
- 유효성 검사 (비즈니스 규칙 기반)
- 인증 확인 (`SecurityUtil.getCurrentUserId()`)
- Repository 호출
- Entity 생성 / 도메인 메서드 호출
- 예외 throw (`BusinessException`)

여기에 들어가지 않는 것:
- HTTP 상태 코드 결정
- JSON 변환
- DTO → Entity 직접 필드 매핑 (Entity 생성자 또는 팩토리 메서드 활용)

예시:

```java
// application/service/FolderService.java
@Service
public class FolderService {

    @Transactional
    public FolderEntity createFolder(FolderRequest request) {
        // 1. 비즈니스 유효성 검사
        if (request.workspaceId() == null || request.folderName() == null) {
            throw new BusinessException(Code.INVALID_REQUEST, "워크스페이스 ID와 폴더명은 필수입니다.");
        }

        // 2. 인증 확인
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(Code.UNAUTHORIZED, "인증 정보가 없습니다.");
        }

        // 3. 상위 폴더 존재 확인
        if (request.parentId() != null) {
            folderRepository.findByFolderIdAndWorkspaceId(request.parentId(), request.workspaceId())
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "상위 폴더를 찾을 수 없습니다."));
        }

        // 4. Entity 생성 및 저장
        String folderId = UUID.randomUUID().toString();
        FolderEntity folder = new FolderEntity(folderId, request.workspaceId(),
            request.parentId(), request.folderName(), userId);
        return folderRepository.save(folder);
    }
}
```

---

## 6.2 Repository는 무엇을 담는가?

Repository는 **데이터 접근 인터페이스**다.

여기에 들어가는 것:
- Spring Data JPA 쿼리 메서드
- `@Query` 어노테이션 기반 JPQL/Native 쿼리

여기에 들어가지 않는 것:
- 비즈니스 로직
- 예외 처리 (Service에서 한다)

예시:

```java
// application/repository/FolderRepository.java
public interface FolderRepository extends JpaRepository<FolderEntity, FolderId> {
    Optional<FolderEntity> findByFolderIdAndWorkspaceId(String folderId, String workspaceId);
    List<FolderEntity> findByWorkspaceIdAndUsable(String workspaceId, Integer usable);
}
```

---

# 7. `presentation/` 기준으로 이해하기

## 7.1 Controller는 무엇을 담는가?

Controller는 **HTTP 요청을 받아 Service에 위임하고 결과를 반환**한다.

여기에 들어가는 것:
- `@RestController`, `@RequestMapping`
- `@PathVariable`, `@RequestBody`, `@RequestParam` 파라미터 수신
- Service 호출
- DTO 변환 (`FolderDto::from`)
- `Response` / `DataResponse` 반환

여기에 들어가지 않는 것:
- 비즈니스 로직
- DB 직접 접근
- 예외 처리 (GlobalExceptionHandler가 담당)

예시:

```java
// presentation/controller/folder/FolderController.java
@RestController
@RequestMapping("/api/folders")
public class FolderController {

    @PostMapping
    public Response createFolder(@RequestBody FolderRequest request) {
        FolderEntity folder = folderService.createFolder(request);
        return DataResponse.success(FolderDto.from(folder));
    }

    @DeleteMapping("/{folderId}/{workspaceId}")
    public Response deleteFolder(@PathVariable String folderId,
                                 @PathVariable String workspaceId) {
        folderService.deleteFolder(folderId, workspaceId);
        return Response.success("폴더가 삭제되었습니다.");
    }
}
```

---

## 7.2 Request DTO는 무엇인가?

Request DTO는 **클라이언트 입력을 받는 객체**다.

- Java `record`를 사용한다.
- 입력 검증 어노테이션(`@NotNull`, `@NotBlank` 등)을 붙일 수 있다.
- 비즈니스 로직을 담지 않는다.

예시:

```java
// presentation/dto/request/FolderRequest.java
public record FolderRequest(
    String workspaceId,
    String parentId,
    String folderName,
    Integer ordinal
) {}
```

---

## 7.3 Response DTO는 무엇인가?

Response DTO는 **클라이언트에 반환할 데이터 형태를 정의하는 객체**다.

- Java `record`를 사용한다.
- Entity를 직접 노출하지 않는다. DTO가 Entity를 감싼다.
- Entity → DTO 변환은 DTO 안에 `static from()` 팩토리 메서드로 담는다.

예시:

```java
// presentation/dto/response/FolderDto.java
public record FolderDto(
    String folderId,
    String workspaceId,
    String parentId,
    String folderName,
    Integer ordinal,
    String createUser,
    Instant createTime
) {
    public static FolderDto from(FolderEntity entity) {
        return new FolderDto(
            entity.getFolderId(),
            entity.getWorkspaceId(),
            entity.getParentId(),
            entity.getFolderName(),
            entity.getOrdinal(),
            entity.getCreateUser(),
            entity.getCreateTime()
        );
    }
}
```

### 왜 Entity를 직접 반환하면 안 되는가?

- Entity에는 클라이언트가 알 필요 없는 내부 필드가 있다. (예: `usable`, `updateUser`)
- Entity 구조가 바뀌면 API 응답도 함께 깨진다.
- DTO는 API 계약(Contract)이고, Entity는 내부 구현이다.

---

## 7.4 Controller 패키지 구조

Controller는 도메인 단위로 패키지를 분리한다.

```txt
presentation/controller/
  folder/
    FolderController.java
  document/
    DocumentController.java
  login/
    LoginController.java
```

하나의 Controller 파일에 모든 기능을 담지 않는다.
도메인이 커지면 기능별로 추가 분리를 고려한다.

---

# 8. `common/` 기준으로 이해하기

## 8.1 응답 포맷

모든 API 응답은 `Response` 또는 `DataResponse`로 감싼다.

```java
// 데이터 없는 성공 응답
return Response.success("폴더가 삭제되었습니다.");

// 데이터 있는 성공 응답
return DataResponse.success(FolderDto.from(folder));
```

---

## 8.2 예외 처리

비즈니스 예외는 `BusinessException`을 사용한다.

```java
throw new BusinessException(Code.NOT_FOUND, "폴더를 찾을 수 없습니다.");
```

`GlobalExceptionHandler`가 `BusinessException`을 잡아 적절한 응답으로 변환한다.

즉:
- Service는 `throw`만 한다.
- 응답 변환은 `GlobalExceptionHandler`가 담당한다.

---

## 8.3 에러 코드

에러 코드는 `Code` enum으로 관리한다.

```java
// common/code/Code.java
public enum Code {
    SUCCESS,
    UNKNOWN,
    UNAUTHORIZED,
    NOT_FOUND,
    INVALID_REQUEST
}
```

새로운 에러 케이스가 생기면 여기에 추가한다.

---

## 8.4 `SecurityUtil`

현재 로그인한 사용자 ID를 꺼낼 때 사용한다.

```java
String userId = SecurityUtil.getCurrentUserId();
```

`SecurityUtil`은 `common/security/`에 두며,
Service 레이어에서만 호출한다.
Controller에서 직접 호출하지 않는다.

---

# 9. 레이어 간 의존 방향

```txt
presentation  →  application  →  domain
     ↓                ↓
   common           common
```

중요 원칙:
- `domain`은 어느 레이어도 참조하지 않는다.
- `application`은 `presentation`을 참조하지 않는다.
- `common`은 모든 레이어에서 참조 가능하다.

### 금지된 의존 방향

```txt
domain → application   (금지)
domain → presentation  (금지)
application → presentation  (금지)
```

현재 코드에서 `FolderEntity`가 `FolderRequest`를 참조하는 부분이 있다.
이는 `domain → presentation` 의존이 생긴 것이므로 **점진적으로 개선**이 필요하다.
개선 방향: `updateFolder()`의 파라미터를 Request DTO 대신 개별 값으로 받도록 변경한다.

---

# 10. 한 요청이 처리되는 흐름

폴더 생성(`POST /api/folders`)을 기준으로 보면:

## 1단계. presentation (Controller)
`FolderController`가 `@RequestBody FolderRequest`로 요청을 받는다.

## 2단계. application (Service)
`FolderService.createFolder(request)`를 호출한다.
- 비즈니스 유효성 검사
- 인증 확인
- 상위 폴더 존재 확인
- Entity 생성 및 저장

## 3단계. domain (Entity)
`new FolderEntity(folderId, workspaceId, parentId, folderName, userId)` 생성.
생성자에서 초기값(`ordinal=0`, `usable=1`, `createTime`) 설정.

## 4단계. application (Repository)
`folderRepository.save(folder)`로 DB에 저장.

## 5단계. presentation (Controller → DTO)
Service가 반환한 `FolderEntity`를 `FolderDto.from(folder)`로 변환.
`DataResponse.success(dto)`로 감싸 반환.

---

# 11. domain / application / presentation을 구분하는 질문

파일을 만들 때 아래 질문으로 판단한다.

## 11.1 이건 비즈니스 개념 자체인가?
예:
- 폴더가 무엇인지 (Entity)
- 폴더의 상태값 (Enum)
- 폴더의 복합 키 (IdClass)

→ `domain/model/`

## 11.2 이건 비즈니스 유스케이스인가?
예:
- 폴더 생성 로직
- 폴더 삭제 유효성 검사
- 소유자 확인

→ `application/service/`

## 11.3 이건 데이터 접근인가?
예:
- 폴더 ID와 워크스페이스 ID로 조회
- 워크스페이스 내 사용 가능한 폴더 목록 조회

→ `application/repository/`

## 11.4 이건 HTTP 요청/응답 형태인가?
예:
- 클라이언트가 보내는 폴더 생성 데이터
- 클라이언트에 반환할 폴더 정보

→ `presentation/dto/request/` 또는 `presentation/dto/response/`

## 11.5 이건 HTTP 엔드포인트인가?
예:
- `POST /api/folders`
- `DELETE /api/folders/{folderId}/{workspaceId}`

→ `presentation/controller/`

## 11.6 이건 도메인과 무관하게 공통으로 쓰이는가?
예:
- 응답 포맷
- 에러 코드
- 전역 예외 처리
- 보안 설정

→ `common/`

---

# 12. 가장 자주 헷갈리는 포인트 정리

## 12.1 비즈니스 로직은 Service인가, Entity인가?

### Entity
- "이 데이터가 어떻게 바뀌는가"를 담는다.
- 예: `softDelete()`, `updateFolder()`, `activate()`

### Service
- "언제, 왜 Entity 메서드를 호출하는가"를 담는다.
- 예: 권한 확인 후 `softDelete()` 호출

### 한 줄 요약
- Entity = 도메인 행동 구현
- Service = 유스케이스 흐름 제어

---

## 12.2 DTO 변환은 어디서 하는가?

`from()` 팩토리 메서드는 **Response DTO 안에** 둔다.

```java
// presentation/dto/response/FolderDto.java
public static FolderDto from(FolderEntity entity) { ... }
```

Controller에서 직접 필드를 꺼내 조립하지 않는다.

---

## 12.3 Request DTO를 그대로 Service에 넘겨도 되는가?

현재 프로젝트에서는 허용한다.
다만 레이어 의존 방향에 주의한다.

- `application/service`가 `presentation/dto/request`를 참조하는 것은 **application → presentation 의존**이다.
- 이를 피하려면 Service 전용 Command 객체를 별도로 만들 수 있다.
- 1차 MVP 범위에서는 Request DTO를 그대로 사용하되, 구조가 복잡해지면 분리를 고려한다.

---

## 12.4 Repository는 application에 있는데 왜 application인가?

Repository 인터페이스는 Service가 사용하는 **데이터 접근 추상화**다.
구현체(JPA)는 프레임워크가 제공하므로, 인터페이스 정의만 `application/repository/`에 둔다.

---

# 13. 이 구조에서 금지하거나 조심할 것

## 13.1 금지: Controller에서 비즈니스 로직 처리

```java
// BAD
@PostMapping
public Response createFolder(@RequestBody FolderRequest request) {
    if (request.folderName() == null) {
        return Response.failure("폴더명 필수");
    }
    // DB 직접 접근...
}
```

Controller는 Service를 호출하고 결과를 반환하는 것만 한다.

---

## 13.2 금지: Entity 직접 반환

```java
// BAD
@GetMapping("/{folderId}")
public FolderEntity getFolder(...) { ... }

// GOOD
@GetMapping("/{folderId}")
public Response getFolder(...) {
    FolderEntity folder = folderService.getFolder(...);
    return DataResponse.success(FolderDto.from(folder));
}
```

Entity를 직접 노출하면 내부 구현과 API 계약이 결합된다.

---

## 13.3 금지: Service에서 Response 객체 사용

```java
// BAD: Service가 HTTP 응답 형태를 알면 안 된다
public Response createFolder(FolderRequest request) { ... }

// GOOD: Service는 도메인 객체를 반환한다
public FolderEntity createFolder(FolderRequest request) { ... }
```

---

## 13.4 조심: common 비대화

`common`에 도메인 의미가 있는 코드가 들어가기 시작하면 구조가 흔들린다.

도메인 특화 코드는 해당 도메인 패키지로 분리한다.

---

## 13.5 조심: domain → presentation 역방향 의존

현재 `FolderEntity`가 `FolderRequest`를 파라미터로 받는다.
이는 `domain → presentation` 의존이 생긴 상태다.

개선 방향:

```java
// 현재 (개선 전)
public void updateFolder(FolderRequest request, String updateUser) { ... }

// 개선 후: 개별 값으로 받는다
public void updateFolder(String folderName, String parentId, Integer ordinal, String updateUser) { ... }
```

---

# 14. 실무용 최종 규칙

## 규칙 1. Entity는 `domain/model/`
- JPA Entity
- Enum (도메인 상태값)
- 복합 키 클래스

## 규칙 2. 비즈니스 흐름은 `application/service/`
- 유효성 검사
- 인증 확인
- Entity 조작 흐름
- `BusinessException` throw

## 규칙 3. DB 접근은 `application/repository/`
- Spring Data JPA 인터페이스
- 쿼리 메서드 또는 `@Query`

## 규칙 4. HTTP 처리는 `presentation/controller/`
- 요청 수신
- Service 위임
- DTO 변환 후 반환

## 규칙 5. DTO는 `presentation/dto/`
- Request: `dto/request/` (record)
- Response: `dto/response/` (record + `from()` 팩토리)

## 규칙 6. 공통 인프라는 `common/`
- 응답 포맷, 에러 코드, 예외, 보안 설정

## 규칙 7. Entity는 직접 반환하지 않는다
- Controller 반환값은 항상 `Response` 또는 `DataResponse`
- Entity → DTO 변환은 `from()` 팩토리 메서드 활용

---

# 15. 한 줄 최종 정리

- **domain은 "비즈니스 개념이 무엇인지"**
- **application은 "비즈니스 유스케이스를 어떻게 처리하는지"**
- **presentation은 "HTTP 요청과 응답을 어떻게 다루는지"**
- **common은 "전 레이어에서 공통으로 쓰이는 것"**

---

# 16. 최종 예시 요약

```txt
domain/model/
  FolderEntity.java       -> 폴더 개념, softDelete/updateFolder 도메인 메서드
  FolderId.java           -> 복합 키
  UserStatus.java         -> 사용자 상태 Enum

application/service/
  FolderService.java      -> 폴더 유스케이스 흐름 (검증 → Entity 조작 → 저장)

application/repository/
  FolderRepository.java   -> DB 접근 인터페이스

presentation/controller/folder/
  FolderController.java   -> POST/GET/PUT/DELETE 엔드포인트

presentation/dto/request/
  FolderRequest.java      -> 클라이언트 입력 record

presentation/dto/response/
  FolderDto.java          -> 클라이언트 응답 record + from() 팩토리

common/
  response/Response.java         -> 공통 응답 포맷
  response/DataResponse.java     -> 데이터 포함 응답 포맷
  exception/BusinessException.java  -> 비즈니스 예외
  exception/GlobalExceptionHandler.java  -> 전역 예외 → 응답 변환
  code/Code.java                 -> 에러 코드 Enum
  security/SecurityConfig.java   -> Spring Security 설정
  security/SecurityUtil.java     -> 현재 사용자 ID 조회 유틸
```

---

# 17. 코딩 컨벤션 핵심 규칙

> 전체 컨벤션은 `docs/dev-guidelines/Coding_Convention.pdf` 참조.
> 여기에는 구조 가이드와 직접 관련된 핵심 규칙만 정리한다.

---

## 17.1 파일/포맷 규칙

- 인코딩: **UTF-8**
- 줄바꿈: **LF**
- 파일 끝: **마지막 줄 newline 필수**
- 라인 길이: **120자** 이하
- 들여쓰기: **스페이스 4칸** (탭 금지)

`.editorconfig` 설정:

```ini
[*.java]
indent_style = space
indent_size = 4
max_line_length = 120
```

---

## 17.2 네이밍 컨벤션

| 대상 | 규칙 | 예시 |
|------|------|------|
| 클래스/인터페이스 | PascalCase | `FolderService`, `FolderRepository` |
| 메서드/변수 | camelCase | `createFolder`, `folderId` |
| 상수 (`static final`) | UPPER_SNAKE_CASE | `MAX_FOLDER_NAME_LENGTH` |
| 패키지 | lowercase | `com.fixlog.presentation` |
| Boolean 변수/메서드 | `is`/`has`/`can`/`should` 접두어 | `isActive`, `hasPermission` |
| Request DTO | `XxxRequest` | `FolderRequest` |
| Response DTO | `XxxResponse` 또는 `XxxDto` | `FolderDto` |
| 내부 전달 DTO | `XxxDto` | `FolderDto` |
| 비즈니스 예외 | `XxxException` | `FolderNotFoundException` |

---

## 17.3 코드 스타일

### 메서드 크기 / 중첩 depth

- 메서드 길이: **50줄 권장** (초과 시 분리 검토)
- 중첩 depth: **3 이상 지양**
- 한 메서드의 책임: **한 가지 일**만 유지

### Guard Clause (Early Return)

조건 분기는 early return을 우선한다.

```java
// BAD: else 중첩
if (condition) {
    if (otherCondition) {
        // 핵심 로직
    }
}

// GOOD: early return
if (!condition) throw new BusinessException(Code.INVALID_REQUEST, "...");
if (!otherCondition) throw new BusinessException(Code.NOT_FOUND, "...");
// 핵심 로직
```

### 복잡한 조건에 이름 붙이기

```java
// BAD
if (!entity.getUsable().equals(0) && entity.getWorkspaceId() != null) { ... }

// GOOD
boolean isAvailable = entity.getUsable() != 0 && entity.getWorkspaceId() != null;
if (isAvailable) { ... }
```

---

## 17.4 JPA/Hibernate 규칙

### Entity 기본 규칙

- **기본 생성자**: `protected`로 선언 (직접 생성 방지)
- **Fetch 전략**: 기본 `LAZY`, `EAGER` 금지 (예외 시 근거 주석 필수)
- `equals`/`hashCode`: id 기반으로 일관되게 구현

```java
// 기본 생성자
@Entity
public class FolderEntity {
    protected FolderEntity() {} // JPA 전용, 직접 사용 금지
}
```

### N+1 방지

- 조회 API 작성 시 N+1 여부를 항상 점검한다.
- 허용 패턴: `fetch join`, `@EntityGraph`, 배치 사이즈
- 금지 패턴: 무분별한 `FetchType.EAGER`로 해결

```java
// BAD: EAGER로 N+1 해결 시도
@OneToMany(fetch = FetchType.EAGER)
private List<BlockEntity> blocks;

// GOOD: fetch join 사용
@Query("SELECT f FROM FolderEntity f JOIN FETCH f.blocks WHERE f.folderId = :id")
Optional<FolderEntity> findWithBlocks(@Param("id") String id);
```

---

## 17.5 Service / 트랜잭션 규칙

- `@Transactional`은 **Service 레이어에만** 붙인다. (Controller 금지)
- 조회 전용 메서드는 `@Transactional(readOnly = true)` 필수.
- 트랜잭션 전파/격리 수준 변경은 원칙적으로 금지 (필요 시 근거 + 주석).

```java
// 조회
@Transactional(readOnly = true)
public FolderEntity getFolder(String folderId, String workspaceId) { ... }

// 변경
@Transactional
public FolderEntity createFolder(FolderRequest request) { ... }
```

---

## 17.6 로깅 규칙

- 로거: `SLF4J` 사용 (`@Slf4j` 롬복 어노테이션 권장)
- 문자열 더하기(`+`) 금지 → **파라미터 바인딩(`{}`)** 사용
- **민감 정보**(비밀번호, 토큰, 개인정보)를 로그에 출력하지 않는다.
- 로그 레벨 기준:

| 레벨 | 사용 기준 |
|------|----------|
| `DEBUG` | 개발/진단용 상세 정보 |
| `INFO` | 주요 비즈니스 이벤트 |
| `WARN` | 복구 가능한 이상 상황 |
| `ERROR` | 장애/복구 불가 상황 |

```java
// BAD: 문자열 더하기, 민감정보 노출
log.info("User " + userId + " token: " + accessToken);

// GOOD
log.info("User login: userId={}", userId);
```

---

## 17.7 주석 / TODO 규칙

- 주석은 **"무엇"이 아니라 "왜/의도/제약"**을 설명한다.
- `TODO`/`FIXME` 포맷: `TODO(#티켓번호): 내용`

```java
// BAD: 코드 반복 설명
// folder를 저장한다
folderRepository.save(folder);

// BAD: 티켓 없는 TODO
// TODO: 나중에 고치기

// GOOD: 의도/제약 설명 + 티켓 연결
// TODO(#KAN-123): 소프트 삭제 후 자식 폴더 처리 정책 확정 필요
```

---

# 18. 이 문서를 기준으로 코드를 만들 때 체크리스트

새 파일을 만들기 전 아래를 체크한다.

### 패키지/레이어 배치

- 이건 **비즈니스 개념 자체**인가? → `domain/model/`
- 이건 **비즈니스 유스케이스 흐름**인가? → `application/service/`
- 이건 **DB 접근**인가? → `application/repository/`
- 이건 **HTTP 엔드포인트**인가? → `presentation/controller/`
- 이건 **입력 형태**인가? → `presentation/dto/request/`
- 이건 **출력 형태**인가? → `presentation/dto/response/`
- 이건 **전 레이어 공통**인가? → `common/`
- Entity를 직접 반환하려 하는가? → **DTO로 감싼다**
- 레이어 의존 방향이 역전되지 않았는가? → **domain → application 금지, application → presentation 금지**

### 코딩 컨벤션

- 라인 길이 120자 이하인가?
- 들여쓰기가 스페이스 4칸인가? (탭 금지)
- 클래스/메서드/변수 네이밍 규칙을 따르는가?
- Boolean 변수에 `is`/`has`/`can`/`should` 접두어가 있는가?
- 메서드 길이가 50줄을 크게 초과하지 않는가?
- 중첩 depth가 3 이상 이어지지 않는가?
- Guard clause (early return)를 우선 사용하는가?
- `@RequestBody`에 `@Valid`가 붙어 있는가?
- JPA Entity 기본 생성자가 `protected`인가?
- 연관관계에 `FetchType.EAGER`를 쓰지 않는가?
- 조회 Service 메서드에 `@Transactional(readOnly = true)`가 있는가?
- 로그에 민감 정보가 포함되지 않는가?
- 로그 출력 시 파라미터 바인딩(`{}`)을 사용하는가?
- TODO에 티켓 번호가 포함되어 있는가?

이 기준만 지켜도 구조와 품질이 크게 흔들리지 않는다.