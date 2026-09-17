-- ============================================================================
-- FixLog 공유 권한 테스트 데이터
--
-- 공유받는 사용자: 86f84b85-4fba-42b9-a627-648623b336c0
-- 목적:
--   1. 다른 사용자가 소유한 폴더를 직접 공유받고 하위 문서를 상속 조회
--   2. 다른 사용자가 소유한 문서를 직접 공유받아 조회
--   3. workspace.base_access = DENY 상태에서 명시적 permission만으로 접근되는지 확인
--
-- PostgreSQL/Flyway V14 적용 이후 스키마 기준이며, 여러 번 실행해도 같은 테스트
-- 데이터를 갱신하도록 작성했다.
-- ============================================================================

BEGIN;

-- 고정 테스트 식별자
-- 가상 소유자:     22222222-2222-4222-8222-222222222222
-- 협업 워크스페이스: 33333333-3333-4333-8333-333333333333

-- 공유받는 실제 사용자가 없으면 FK 오류보다 알아보기 쉬운 메시지로 중단한다.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM "fixLog_user"
        WHERE user_id = '86f84b85-4fba-42b9-a627-648623b336c0'::uuid
    ) THEN
        RAISE EXCEPTION '공유받는 사용자(86f84b85-4fba-42b9-a627-648623b336c0)가 fixLog_user에 없습니다.';
    END IF;
END $$;

-- 1. 문서/폴더를 소유할 다른 사용자
INSERT INTO "fixLog_user" (
    user_id, user_name, email, user_status, last_login_at, create_at, update_at
)
VALUES (
    '22222222-2222-4222-8222-222222222222',
    '공유 테스트 소유자',
    'permission-owner@fixlog.test',
    'ACTIVE',
    now(), now(), now()
)
ON CONFLICT (user_id) DO UPDATE
SET user_name = EXCLUDED.user_name,
    user_status = EXCLUDED.user_status,
    update_at = now();

-- 2. 기본 접근이 차단된 협업 워크스페이스
INSERT INTO workspace (
    workspace_id, workspace_name, personal_owner_id, base_access, create_at, update_at
)
VALUES (
    '33333333-3333-4333-8333-333333333333',
    '[TEST] 공유 권한 확인용 워크스페이스',
    NULL,
    'DENY',
    now(), now()
)
ON CONFLICT (workspace_id) DO UPDATE
SET workspace_name = EXCLUDED.workspace_name,
    base_access = 'DENY',
    update_at = now();

-- 소유자는 OWNER, 공유받는 사용자는 MEMBER여야 PermissionEvaluator가 판정한다.
INSERT INTO workspace_member (id, workspace_id, user_id, role, create_at)
VALUES
    ('44444444-4444-4444-8444-444444444441',
     '33333333-3333-4333-8333-333333333333',
     '22222222-2222-4222-8222-222222222222', 'OWNER', now()),
    ('44444444-4444-4444-8444-444444444442',
     '33333333-3333-4333-8333-333333333333',
     '86f84b85-4fba-42b9-a627-648623b336c0', 'MEMBER', now())
ON CONFLICT (workspace_id, user_id) DO UPDATE
SET role = EXCLUDED.role;

-- 3. 다른 사용자가 만든 공유 대상 폴더
INSERT INTO apj_folder (
    folder_id, workspace_id, parent_id, folder_name, path,
    ordinal, usable, inherit_from_parent, base_access,
    create_user, create_time, update_user, update_time
)
VALUES (
    'shared-permission-folder',
    '33333333-3333-4333-8333-333333333333',
    NULL,
    '[TEST] 다른 사람에게 공유받은 폴더',
    '/shared-permission-folder/',
    0, 1, true, 'DENY',
    '22222222-2222-4222-8222-222222222222', now(),
    '22222222-2222-4222-8222-222222222222', now()
)
ON CONFLICT (folder_id) DO UPDATE
SET workspace_id = EXCLUDED.workspace_id,
    parent_id = EXCLUDED.parent_id,
    folder_name = EXCLUDED.folder_name,
    path = EXCLUDED.path,
    usable = 1,
    deleted_at = NULL,
    deleted_by = NULL,
    inherit_from_parent = true,
    base_access = 'DENY',
    update_user = EXCLUDED.update_user,
    update_time = now();

-- 폴더 공유 권한이 하위 문서로 상속되는지 확인할 문서
INSERT INTO apj_document (
    document_id, workspace_id, folder_id, title, blocks, plain_text,
    content_hash, ordinal, usable, create_user, create_time, update_user, update_time
)
VALUES (
    'shared-permission-inherited-doc',
    '33333333-3333-4333-8333-333333333333',
    'shared-permission-folder',
    '[TEST] 폴더 권한을 상속받는 문서',
    '[{"type":"paragraph","data":{"text":"폴더에 부여된 권한으로 조회되는 문서입니다."}}]',
    '폴더에 부여된 권한으로 조회되는 문서입니다.',
    NULL, 0, 1,
    '22222222-2222-4222-8222-222222222222', now(),
    '22222222-2222-4222-8222-222222222222', now()
)
ON CONFLICT (document_id) DO UPDATE
SET workspace_id = EXCLUDED.workspace_id,
    folder_id = EXCLUDED.folder_id,
    title = EXCLUDED.title,
    blocks = EXCLUDED.blocks,
    plain_text = EXCLUDED.plain_text,
    usable = 1,
    deleted_at = NULL,
    deleted_by = NULL,
    update_user = EXCLUDED.update_user,
    update_time = now();

-- 폴더와 무관하게 문서에 직접 부여된 권한을 확인할 루트 문서
INSERT INTO apj_document (
    document_id, workspace_id, folder_id, title, blocks, plain_text,
    content_hash, ordinal, usable, create_user, create_time, update_user, update_time
)
VALUES (
    'shared-permission-direct-doc',
    '33333333-3333-4333-8333-333333333333',
    NULL,
    '[TEST] 다른 사람에게 직접 공유받은 문서',
    '[{"type":"paragraph","data":{"text":"문서에 직접 부여된 권한으로 조회됩니다."}}]',
    '문서에 직접 부여된 권한으로 조회됩니다.',
    NULL, 0, 1,
    '22222222-2222-4222-8222-222222222222', now(),
    '22222222-2222-4222-8222-222222222222', now()
)
ON CONFLICT (document_id) DO UPDATE
SET workspace_id = EXCLUDED.workspace_id,
    folder_id = NULL,
    title = EXCLUDED.title,
    blocks = EXCLUDED.blocks,
    plain_text = EXCLUDED.plain_text,
    usable = 1,
    deleted_at = NULL,
    deleted_by = NULL,
    update_user = EXCLUDED.update_user,
    update_time = now();

-- 4. 생성자 소유 권한: 애플리케이션의 grantCreatorOwnership() 결과와 동일
INSERT INTO permission (
    id, workspace_id, principal_type, principal_id,
    resource_type, resource_id, permission_type,
    can_download, can_edit, granted_by, create_at, update_at
)
VALUES
    ('55555555-5555-4555-8555-555555555551',
     '33333333-3333-4333-8333-333333333333', 'USER',
     '22222222-2222-4222-8222-222222222222', 'FOLDER',
     'shared-permission-folder', 'ALLOW', true, true,
     '22222222-2222-4222-8222-222222222222', now(), now()),
    ('55555555-5555-4555-8555-555555555552',
     '33333333-3333-4333-8333-333333333333', 'USER',
     '22222222-2222-4222-8222-222222222222', 'DOCUMENT',
     'shared-permission-inherited-doc', 'ALLOW', true, true,
     '22222222-2222-4222-8222-222222222222', now(), now()),
    ('55555555-5555-4555-8555-555555555553',
     '33333333-3333-4333-8333-333333333333', 'USER',
     '22222222-2222-4222-8222-222222222222', 'DOCUMENT',
     'shared-permission-direct-doc', 'ALLOW', true, true,
     '22222222-2222-4222-8222-222222222222', now(), now())
ON CONFLICT (resource_type, resource_id, principal_type, principal_id) DO UPDATE
SET permission_type = 'ALLOW',
    can_download = true,
    can_edit = true,
    granted_by = EXCLUDED.granted_by,
    update_at = now();

-- 5. 실제 사용자가 공유받은 권한
-- 폴더: 읽기/다운로드 가능, 편집 불가. 하위 문서에도 상속된다.
-- 문서: 읽기 가능, 다운로드/편집 불가. 해당 문서에만 적용된다.
INSERT INTO permission (
    id, workspace_id, principal_type, principal_id,
    resource_type, resource_id, permission_type,
    can_download, can_edit, granted_by, create_at, update_at
)
VALUES
    ('66666666-6666-4666-8666-666666666661',
     '33333333-3333-4333-8333-333333333333', 'USER',
     '86f84b85-4fba-42b9-a627-648623b336c0', 'FOLDER',
     'shared-permission-folder', 'ALLOW', true, false,
     '22222222-2222-4222-8222-222222222222', now(), now()),
    ('66666666-6666-4666-8666-666666666662',
     '33333333-3333-4333-8333-333333333333', 'USER',
     '86f84b85-4fba-42b9-a627-648623b336c0', 'DOCUMENT',
     'shared-permission-direct-doc', 'ALLOW', false, false,
     '22222222-2222-4222-8222-222222222222', now(), now())
ON CONFLICT (resource_type, resource_id, principal_type, principal_id) DO UPDATE
SET permission_type = 'ALLOW',
    can_download = EXCLUDED.can_download,
    can_edit = false,
    granted_by = EXCLUDED.granted_by,
    update_at = now();

COMMIT;

-- --------------------------------------------------------------------------
-- 확인 쿼리
-- --------------------------------------------------------------------------
SELECT wm.workspace_id, w.workspace_name, wm.user_id, wm.role, w.base_access
FROM workspace_member wm
JOIN workspace w ON w.workspace_id = wm.workspace_id
WHERE wm.workspace_id = '33333333-3333-4333-8333-333333333333';

SELECT p.id, p.resource_type, p.resource_id, p.permission_type,
       p.can_download, p.can_edit, p.principal_id, p.granted_by
FROM permission p
WHERE p.workspace_id = '33333333-3333-4333-8333-333333333333'
ORDER BY p.resource_type, p.resource_id, p.principal_id;

SELECT d.document_id, d.title, d.folder_id, d.create_user
FROM apj_document d
WHERE d.workspace_id = '33333333-3333-4333-8333-333333333333'
ORDER BY d.document_id;

-- --------------------------------------------------------------------------
-- 테스트 후 정리 쿼리 (필요할 때 주석을 해제해 한 번에 실행)
-- --------------------------------------------------------------------------
-- BEGIN;
-- DELETE FROM permission
-- WHERE workspace_id = '33333333-3333-4333-8333-333333333333';
-- DELETE FROM apj_document
-- WHERE workspace_id = '33333333-3333-4333-8333-333333333333';
-- DELETE FROM apj_folder
-- WHERE workspace_id = '33333333-3333-4333-8333-333333333333';
-- DELETE FROM workspace_member
-- WHERE workspace_id = '33333333-3333-4333-8333-333333333333';
-- DELETE FROM workspace
-- WHERE workspace_id = '33333333-3333-4333-8333-333333333333';
-- DELETE FROM "fixLog_user"
-- WHERE user_id = '22222222-2222-4222-8222-222222222222';
-- COMMIT;
