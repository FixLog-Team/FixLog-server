-- =====================================================================
-- 권한 모델 도입 마이그레이션 (기존 DB용)
-- ---------------------------------------------------------------------
-- 대상: 이미 폴더 / 문서 데이터가 들어 있는 기존 DB
-- 성격: 멱등(re-runnable). 여러 번 실행해도 결과가 같다.
-- 실행:
--   docker exec -i fixlog-postgres psql -U user -d fixlog -v ON_ERROR_STOP=1 < docker/migration/001_permission.sql
--
-- 왜 필요한가:
--   ddl-auto=update는 컬럼을 추가해 주지만 값을 채워주지는 않는다.
--   권한 판정은 노드에서 워크스페이스 루트까지 부모 체인을 올라가는 방식이므로,
--   workspace_id가 비어 있으면 루트에 닿지 못하고 전부 DENY로 확정된다(불확실은 닫는 방향).
--   즉 이 스크립트를 실행하지 않으면 기존 문서에 아무도 접근할 수 없다.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- 1) 컬럼 확보 (ddl-auto가 이미 만들었을 수 있다)
-- ---------------------------------------------------------------------
ALTER TABLE apj_folder   ADD COLUMN IF NOT EXISTS workspace_id varchar(100);
ALTER TABLE apj_folder   ADD COLUMN IF NOT EXISTS base_access  varchar(10);
ALTER TABLE apj_document ADD COLUMN IF NOT EXISTS workspace_id varchar(100);
ALTER TABLE apj_document ADD COLUMN IF NOT EXISTS base_access  varchar(10);

-- ---------------------------------------------------------------------
-- 2) 개인 워크스페이스가 없는 사용자에게 하나씩 만든다
--    (OWNER 멤버십이 없는 사용자가 대상)
-- ---------------------------------------------------------------------
CREATE TEMP TABLE tmp_new_workspace AS
SELECT u.user_id,
       u.user_name,
       gen_random_uuid()::text AS workspace_id
FROM "fixLog_user" u
WHERE NOT EXISTS (
    SELECT 1
    FROM apj_workspace_member m
    WHERE m.user_id = u.user_id
      AND m.role = 'OWNER'
);

INSERT INTO apj_workspace
    (workspace_id, workspace_name, base_access, usable, create_user, create_time, update_user, update_time)
SELECT t.workspace_id,
       t.user_name || ' 워크스페이스',
       -- 초대된 사용자가 별도 설정 없이 접근 가능한 것이 기본 정책이다.
       'ALLOW',
       1,
       t.user_id::text,
       now(),
       t.user_id::text,
       now()
FROM tmp_new_workspace t;

INSERT INTO apj_workspace_member (workspace_id, user_id, role, create_user, create_time)
SELECT t.workspace_id,
       t.user_id,
       'OWNER',
       t.user_id::text,
       now()
FROM tmp_new_workspace t;

-- ---------------------------------------------------------------------
-- 3) 기존 폴더 / 문서를 작성자의 개인 워크스페이스에 귀속시킨다
-- ---------------------------------------------------------------------
UPDATE apj_folder f
SET workspace_id = m.workspace_id
FROM apj_workspace_member m
WHERE m.role = 'OWNER'
  AND m.user_id::text = f.create_user
  AND f.workspace_id IS NULL;

UPDATE apj_document d
SET workspace_id = m.workspace_id
FROM apj_workspace_member m
WHERE m.role = 'OWNER'
  AND m.user_id::text = d.create_user
  AND d.workspace_id IS NULL;

-- ---------------------------------------------------------------------
-- 4) base_access를 명시적으로 채운다
--    NULL도 코드에서 INHERIT으로 해석되지만, 값을 보고 판단할 수 있게 남긴다.
-- ---------------------------------------------------------------------
UPDATE apj_folder   SET base_access = 'INHERIT' WHERE base_access IS NULL;
UPDATE apj_document SET base_access = 'INHERIT' WHERE base_access IS NULL;

-- ---------------------------------------------------------------------
-- 5) 인덱스. 모든 조회가 workspace_id로 스코핑된다.
-- ---------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_folder_workspace   ON apj_folder (workspace_id, usable);
CREATE INDEX IF NOT EXISTS idx_document_workspace ON apj_document (workspace_id, usable);
CREATE INDEX IF NOT EXISTS idx_override_workspace_user ON apj_permission_override (workspace_id, user_id);

DROP TABLE tmp_new_workspace;

COMMIT;

-- ---------------------------------------------------------------------
-- 검증: 아래 두 값이 모두 0이어야 한다.
-- 0이 아니면 create_user가 "fixLog_user"에 없는 고아 데이터가 남아 있다는 뜻이고,
-- 그 폴더 / 문서는 누구도 접근할 수 없는 상태가 된다.
-- ---------------------------------------------------------------------
SELECT (SELECT count(*) FROM apj_folder   WHERE workspace_id IS NULL) AS folder_without_workspace,
       (SELECT count(*) FROM apj_document WHERE workspace_id IS NULL) AS document_without_workspace;
