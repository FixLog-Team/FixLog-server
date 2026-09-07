SET client_encoding = 'UTF8';
SET timezone = 'Asia/Seoul';

-- 사용자 정보 (OAuth 기반 — UserEntity)
CREATE TABLE IF NOT EXISTS "fixLog_user" (
    user_id       uuid         NOT NULL DEFAULT gen_random_uuid(),
    user_name     varchar(50)  NOT NULL,
    email         varchar(100) NOT NULL UNIQUE,
    user_status   varchar(20),
    last_login_at timestamp(6),
    create_at     timestamp(6),
    update_at     timestamp(6),
    PRIMARY KEY (user_id)
);
COMMENT ON TABLE "fixLog_user" IS '사용자 정보 (Google OAuth)';

-- OAuth 연동 정보 (UserOauthEntity)
CREATE TABLE IF NOT EXISTS "fixLog_user_oauth" (
    id          uuid         NOT NULL DEFAULT gen_random_uuid(),
    user_id     uuid         NOT NULL,
    provider    varchar(30)  NOT NULL,
    provider_id varchar(255) NOT NULL,
    create_at   timestamp(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_oauth_provider UNIQUE (provider, provider_id),
    CONSTRAINT fk_oauth_user FOREIGN KEY (user_id) REFERENCES "fixLog_user" (user_id)
);
COMMENT ON TABLE "fixLog_user_oauth" IS '소셜 로그인 OAuth 연동 정보';

-- 폴더 (FolderEntity)
CREATE TABLE IF NOT EXISTS apj_folder (
    folder_id    varchar(100) NOT NULL,
    parent_id    varchar(100),
    folder_name  varchar(100) NOT NULL,
    ordinal      int          DEFAULT 0,
    usable       int          DEFAULT 1,
    create_user  varchar(100),
    create_time  timestamp(6),
    update_user  varchar(100),
    update_time  timestamp(6),
    PRIMARY KEY (folder_id)
);
COMMENT ON TABLE apj_folder IS '사용자 폴더 구조';

-- 트러블슈팅 문서 (DocumentEntity)
CREATE TABLE IF NOT EXISTS apj_document (
    document_id  varchar(100) NOT NULL,
    folder_id    varchar(100),
    title        varchar(255) NOT NULL,
    blocks       text,
    plain_text   text,
    content_hash varchar(64),
    ordinal      int          DEFAULT 0,
    usable       int          DEFAULT 1,
    create_user  varchar(100),
    create_time  timestamp(6),
    update_user  varchar(100),
    update_time  timestamp(6),
    PRIMARY KEY (document_id)
);
COMMENT ON TABLE apj_document IS '트러블슈팅 문서';

-- =====================================================================
-- 권한(Permission) 모델
-- ---------------------------------------------------------------------
-- 권한은 두 축으로 나뉜다.
--   1) 운영권(Role)      : 워크스페이스를 무엇을 관리할 수 있는가
--   2) 접근권(Permission): 어떤 폴더 / 문서에 접근할 수 있는가
--
-- 접근권은 노드별 기본 정책(base_access)과 사용자 개별 설정(override)의 조합이며,
-- 판정은 대상 노드에서 워크스페이스 루트 방향으로 올라가며 가장 먼저 만나는 값으로 확정된다.
-- 통합 node 테이블을 두지 않고, 기존 폴더 / 문서 테이블에 base_access 컬럼을 얹는 방식이다.
-- =====================================================================

-- 워크스페이스 = 권한 트리의 루트 노드 (WorkspaceEntity)
CREATE TABLE IF NOT EXISTS apj_workspace (
    workspace_id   varchar(100) NOT NULL,
    workspace_name varchar(100) NOT NULL,
    -- 루트는 상속할 부모가 없으므로 INHERIT이 될 수 없다. ALLOW | DENY
    base_access    varchar(10)  NOT NULL DEFAULT 'ALLOW',
    usable         int          DEFAULT 1,
    create_user    varchar(100),
    create_time    timestamp(6),
    update_user    varchar(100),
    update_time    timestamp(6),
    PRIMARY KEY (workspace_id)
);
COMMENT ON TABLE apj_workspace IS '워크스페이스 (권한 트리의 루트)';

-- 워크스페이스 소속과 운영권 (WorkspaceMemberEntity)
CREATE TABLE IF NOT EXISTS apj_workspace_member (
    workspace_id varchar(100) NOT NULL,
    user_id      uuid         NOT NULL,
    role         varchar(10)  NOT NULL,   -- OWNER | ADMIN | MEMBER
    create_user  varchar(100),
    create_time  timestamp(6),
    PRIMARY KEY (workspace_id, user_id),
    CONSTRAINT fk_member_workspace FOREIGN KEY (workspace_id) REFERENCES apj_workspace (workspace_id),
    CONSTRAINT fk_member_user      FOREIGN KEY (user_id)      REFERENCES "fixLog_user" (user_id)
);
COMMENT ON TABLE apj_workspace_member IS '워크스페이스 멤버십 및 운영권';

-- 노드별 사용자 개별 설정 (PermissionOverrideEntity)
-- PK가 (노드, 사용자)이므로 같은 레벨에서 상충하는 설정이 존재할 수 없다.
CREATE TABLE IF NOT EXISTS apj_permission_override (
    node_id      varchar(100) NOT NULL,
    user_id      uuid         NOT NULL,
    node_type    varchar(10)  NOT NULL,   -- WORKSPACE | FOLDER | DOCUMENT
    workspace_id varchar(100) NOT NULL,   -- 관리 화면의 워크스페이스 단위 조회용 보조 컬럼
    effect       varchar(10)  NOT NULL,   -- ALLOW | DENY
    create_time  timestamp(6),
    update_user  varchar(100),
    update_time  timestamp(6),
    PRIMARY KEY (node_id, user_id),
    CONSTRAINT fk_override_workspace FOREIGN KEY (workspace_id) REFERENCES apj_workspace (workspace_id),
    CONSTRAINT fk_override_user      FOREIGN KEY (user_id)      REFERENCES "fixLog_user" (user_id)
);
COMMENT ON TABLE apj_permission_override IS '폴더 / 문서에 대한 사용자 개별 접근 설정';

-- 기존 테이블에 권한 컬럼 추가
-- base_access가 NULL이면 코드에서 INHERIT(부모를 따름)으로 해석한다.
ALTER TABLE apj_folder   ADD COLUMN IF NOT EXISTS workspace_id varchar(100);
ALTER TABLE apj_folder   ADD COLUMN IF NOT EXISTS base_access  varchar(10) DEFAULT 'INHERIT';
ALTER TABLE apj_document ADD COLUMN IF NOT EXISTS workspace_id varchar(100);
ALTER TABLE apj_document ADD COLUMN IF NOT EXISTS base_access  varchar(10) DEFAULT 'INHERIT';

-- 모든 조회가 workspace_id로 스코핑되므로 인덱스를 둔다.
CREATE INDEX IF NOT EXISTS idx_folder_workspace   ON apj_folder (workspace_id, usable);
CREATE INDEX IF NOT EXISTS idx_document_workspace ON apj_document (workspace_id, usable);
CREATE INDEX IF NOT EXISTS idx_override_workspace_user ON apj_permission_override (workspace_id, user_id);
