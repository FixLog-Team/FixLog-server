-- 권한 모델과 폴더 materialized path.
--
-- 권한 조건은 리포지토리 쿼리에 넣지 않고 PermissionEvaluator 한 곳에서만 판정한다.
-- 이 테이블은 그 판정의 입력이다.

CREATE TABLE permission
(
    id               uuid         NOT NULL DEFAULT gen_random_uuid(),
    -- 판정과 관리자 조회를 워크스페이스로 좁히기 위해 비정규화해 둔다
    workspace_id     uuid         NOT NULL,
    principal_type   varchar(20)  NOT NULL,
    principal_id     uuid         NOT NULL,
    resource_type    varchar(20)  NOT NULL,
    resource_id      varchar(100) NOT NULL,
    permission_level varchar(20)  NOT NULL,
    -- 레벨과 독립이다. 열람은 허용하되 반출은 막는 설정을 표현한다.
    can_download     boolean      NOT NULL DEFAULT true,
    granted_by       uuid,
    create_at        timestamp(6),
    update_at        timestamp(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_permission_target UNIQUE (resource_type, resource_id, principal_type, principal_id),
    CONSTRAINT fk_permission_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (workspace_id)
);
COMMENT ON TABLE permission IS '주체(사용자·그룹)가 대상(폴더·문서)에 대해 갖는 자격';

-- 판정은 (워크스페이스, 대상) 또는 (워크스페이스, 조상 폴더 집합)으로 조회한다
CREATE INDEX idx_permission_resource ON permission (workspace_id, resource_type, resource_id);
CREATE INDEX idx_permission_principal ON permission (workspace_id, principal_type, principal_id);

-- ---------------------------------------------------------------------------
-- 폴더 materialized path.
-- 상속 판정이 조상 집합을 폴더 깊이와 무관하게 단일 쿼리로 얻기 위한 것이다.
-- ---------------------------------------------------------------------------

ALTER TABLE apj_folder ADD COLUMN path varchar(1000);

WITH RECURSIVE tree AS (SELECT folder_id,
                               '/' || folder_id || '/' AS path
                        FROM apj_folder
                        WHERE parent_id IS NULL
                        UNION ALL
                        SELECT f.folder_id,
                               t.path || f.folder_id || '/'
                        FROM apj_folder f
                                 JOIN tree t ON f.parent_id = t.folder_id)
UPDATE apj_folder f
SET path = t.path
FROM tree t
WHERE f.folder_id = t.folder_id;

-- 부모를 따라 루트까지 닿지 못하는 폴더가 있으면 여기서 멈춘다.
-- 경로를 임의로 채우면 그 폴더의 권한 상속이 조용히 어긋난다.
ALTER TABLE apj_folder ALTER COLUMN path SET NOT NULL;

-- 서브트리 조회는 경로 접두사 검색이라 기본 인덱스로는 쓰이지 않는다
CREATE INDEX idx_folder_path ON apj_folder (path varchar_pattern_ops);
