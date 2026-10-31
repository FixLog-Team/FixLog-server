# 권한 모델 변경 노티 (2026-10-31)

백엔드 브랜치: `hs/permission` — 총 세 가지 변경이 있습니다. 아래 항목을 확인하고 프론트에서 대응해 주세요.

---

## 1. `baseAccess` 완전 제거

워크스페이스·폴더 수준의 `baseAccess` 개념이 사라졌습니다.

**기본 접근 정책 변경**
- 기존: 협업 워크스페이스 기본 `DENY`, 개인 워크스페이스 기본 `ALLOW`
- 변경: 모든 워크스페이스 기본 **`ALLOW`** — Admin이 폴더·문서에 `DENY`를 부여해 선택적으로 제한

**API 응답에서 제거된 필드**

| DTO | 제거된 필드 |
|---|---|
| `WorkspaceDto` | `baseAccess` |
| `ResourcePermissionsDto` | `baseAccess` |
| `FolderSettingsRequest` | `baseAccess` |

**삭제된 엔드포인트**

```
PATCH /api/workspaces/{workspaceId}/base-access  ← 제거됨
```

**프론트 할 일**
- `baseAccess` 필드 참조 제거
- 워크스페이스 기본 접근 변경 UI 제거

---

## 2. 상속 lock 검증 (신규)

상위 폴더의 `inheritFromParent=true` 상태에서 하위 항목의 권한 변경을 시도하면 `403 FORBIDDEN`이 반환됩니다.

**영향받는 API**

```
POST  /api/workspaces/{id}/admin/permissions         → 403 (상위 폴더 상속 중이면)
PATCH .../admin/permissions/resources/folders/{id}/settings  → 403 (상위 폴더 상속 중이면)
```

**리소스별 권한 목록 응답 변경**

`GET .../admin/permissions/resources/{type}/{id}` 응답에 `inheritFromParent: boolean` 필드가 추가됩니다.

```json
{
  "permissions": [ ... ],
  "inheritFromParent": true
}
```

**프론트 할 일**

| 상황 | 권장 UI |
|---|---|
| `inheritFromParent=true` (이 폴더가 상속 중) | 하위 항목 권한 편집 버튼·상속 토글 비활성화 |
| `403` 수신 시 | "상위 폴더가 상속 중입니다. 상속을 해제한 후 설정하세요" 안내 |

---

## 3. 고아 항목 float 처리 (신규)

부모 폴더에 접근 권한이 없지만 해당 항목(폴더·문서)에 직접 권한이 있는 경우, 가장 가까운 접근 가능 조상 레벨의 목록에 `orphaned: true`로 포함됩니다.

**FolderDto / DocumentDto 신규 필드**

| 필드 | 타입 | 설명 |
|---|---|---|
| `orphaned` | `boolean` | `true`이면 원래 위치의 상위가 접근 불가 |
| `originalParentId` | `string \| null` | 폴더의 원래 부모 ID (`orphaned=false`이면 `null`) |
| `originalFolderId` | `string \| null` | 문서의 원래 폴더 ID (`orphaned=false`이면 `null`) |

**영향받는 응답**

- `GET /api/folders` (루트 콘텐츠)
- `GET /api/folders/{folderId}/contents`
- `GET /search/title`
- `GET /api/documents/shared-with-me`

**프론트 할 일**

- `orphaned=true` 항목에 "접근 불가 폴더에 있음" 뱃지·툴팁 표시
- `originalParentId` / `originalFolderId`를 메타 정보로 노출하거나 이동 유도 UX 고려

---

## 참고

- 전체 API 변경 상세: [`docs/api/FRONTEND_API_GUIDE.md`](./FRONTEND_API_GUIDE.md)
- Swagger: https://fixlog.art/fixlog/swagger-ui.html
