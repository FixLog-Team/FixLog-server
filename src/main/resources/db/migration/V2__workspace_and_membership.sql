-- 워크스페이스 · 멤버십 · 그룹 도입, 그리고 폴더·문서의 워크스페이스 소속 부여.
--
-- 역할은 사용자의 속성이 아니라 (워크스페이스, 사용자) 쌍의 속성이므로
-- "fixLog_user"에 컬럼을 더하지 않고 workspace_member로 분리한다.

CREATE TABLE workspace
(
    workspace_id      uuid         NOT NULL DEFAULT gen_random_uuid(),
    workspace_name    varchar(100) NOT NULL,
    -- 개인 워크스페이스의 소유자. 협업 워크스페이스는 NULL.
    -- UNIQUE라 사용자당 개인 워크스페이스는 최대 하나다.
    personal_owner_id uuid,
    create_at         timestamp(6),
    update_at         timestamp(6),
    PRIMARY KEY (workspace_id),
    CONSTRAINT uq_workspace_personal_owner UNIQUE (personal_owner_id),
    CONSTRAINT fk_workspace_personal_owner FOREIGN KEY (personal_owner_id) REFERENCES "fixLog_user" (user_id)
);
COMMENT ON TABLE workspace IS '협업·정책·관리자 권한의 경계';

CREATE TABLE workspace_member
(
    id           uuid        NOT NULL DEFAULT gen_random_uuid(),
    workspace_id uuid        NOT NULL,
    user_id      uuid        NOT NULL,
    role         varchar(20) NOT NULL,
    create_at    timestamp(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_workspace_member UNIQUE (workspace_id, user_id),
    CONSTRAINT fk_workspace_member_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (workspace_id),
    CONSTRAINT fk_workspace_member_user FOREIGN KEY (user_id) REFERENCES "fixLog_user" (user_id)
);
COMMENT ON TABLE workspace_member IS '(워크스페이스, 사용자, 역할)의 결합';

-- 한 사용자가 속한 워크스페이스 목록 조회용
CREATE INDEX idx_workspace_member_user ON workspace_member (user_id);

CREATE TABLE workspace_group
(
    group_id     uuid         NOT NULL DEFAULT gen_random_uuid(),
    workspace_id uuid         NOT NULL,
    group_name   varchar(100) NOT NULL,
    create_at    timestamp(6),
    update_at    timestamp(6),
    PRIMARY KEY (group_id),
    CONSTRAINT uq_workspace_group_name UNIQUE (workspace_id, group_name),
    CONSTRAINT fk_workspace_group_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (workspace_id)
);
COMMENT ON TABLE workspace_group IS '워크스페이스 내 사용자 묶음 (권한 부여의 주체)';

CREATE TABLE workspace_group_member
(
    id        uuid NOT NULL DEFAULT gen_random_uuid(),
    group_id  uuid NOT NULL,
    user_id   uuid NOT NULL,
    create_at timestamp(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_workspace_group_member UNIQUE (group_id, user_id),
    CONSTRAINT fk_group_member_group FOREIGN KEY (group_id) REFERENCES workspace_group (group_id),
    CONSTRAINT fk_group_member_user FOREIGN KEY (user_id) REFERENCES "fixLog_user" (user_id)
);
COMMENT ON TABLE workspace_group_member IS '그룹 소속';

-- ---------------------------------------------------------------------------
-- 기존 사용자에게 개인 워크스페이스를 만들어 준다 (FR-WS-002).
-- ---------------------------------------------------------------------------

INSERT INTO workspace (workspace_id, workspace_name, personal_owner_id, create_at, update_at)
SELECT gen_random_uuid(), u.user_name || '의 워크스페이스', u.user_id, now(), now()
FROM "fixLog_user" u;

INSERT INTO workspace_member (id, workspace_id, user_id, role, create_at)
SELECT gen_random_uuid(), w.workspace_id, w.personal_owner_id, 'ADMIN', now()
FROM workspace w
WHERE w.personal_owner_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 폴더·문서에 워크스페이스 소속을 부여한다 (FR-MIG-004).
-- ---------------------------------------------------------------------------

ALTER TABLE apj_folder ADD COLUMN workspace_id uuid;
ALTER TABLE apj_document ADD COLUMN workspace_id uuid;

UPDATE apj_folder f
SET workspace_id = w.workspace_id
FROM workspace w
WHERE w.personal_owner_id::text = f.create_user;

UPDATE apj_document d
SET workspace_id = w.workspace_id
FROM workspace w
WHERE w.personal_owner_id::text = d.create_user;

-- 작성자를 사용자 테이블에서 찾을 수 없는 행이 남아 있으면 여기서 마이그레이션이 실패한다.
-- 귀속을 알 수 없는 데이터를 임의의 워크스페이스에 넣거나 조용히 지우는 대신 멈추게 한다.
ALTER TABLE apj_folder ALTER COLUMN workspace_id SET NOT NULL;
ALTER TABLE apj_document ALTER COLUMN workspace_id SET NOT NULL;

ALTER TABLE apj_folder
    ADD CONSTRAINT fk_folder_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (workspace_id);
ALTER TABLE apj_document
    ADD CONSTRAINT fk_document_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (workspace_id);

-- 목록 조회는 항상 워크스페이스로 범위가 한정된다
CREATE INDEX idx_folder_workspace ON apj_folder (workspace_id);
CREATE INDEX idx_document_workspace ON apj_document (workspace_id);
