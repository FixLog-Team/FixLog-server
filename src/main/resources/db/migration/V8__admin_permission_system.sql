-- Admin 권한 시스템 구현.
-- 1. permission_level → permission_type(ALLOW|DENY) 교체
-- 2. workspace_member 역할에 OWNER 추가 (기존 ADMIN → OWNER 마이그레이션)
-- 3. apj_folder에 상속 제어 필드 추가
-- 4. workspace_invitation 테이블 신규 생성

-- ---------------------------------------------------------------------------
-- 1. permission_level 제거 → permission_type(ALLOW|DENY) 추가
-- 기존 레코드는 전부 ALLOW로 마이그레이션한다.
-- ---------------------------------------------------------------------------

ALTER TABLE permission
    ADD COLUMN permission_type varchar(10) NOT NULL DEFAULT 'ALLOW';

UPDATE permission SET permission_type = 'ALLOW';

ALTER TABLE permission DROP COLUMN permission_level;

-- ---------------------------------------------------------------------------
-- 2. workspace_member 역할 마이그레이션
-- 협업 워크스페이스별 가장 먼저 가입한 ADMIN을 OWNER로 승격한다.
-- 개인 워크스페이스(personal_owner_id IS NOT NULL)는 소유자 = OWNER.
-- ---------------------------------------------------------------------------

-- 개인 워크스페이스 소유자 승격
UPDATE workspace_member wm
SET role = 'OWNER'
FROM workspace w
WHERE wm.workspace_id = w.workspace_id
  AND w.personal_owner_id = wm.user_id
  AND wm.role = 'ADMIN';

-- 협업 워크스페이스의 첫 번째 ADMIN을 OWNER로 승격
UPDATE workspace_member
SET role = 'OWNER'
WHERE id IN (
    SELECT DISTINCT ON (workspace_id) id
    FROM workspace_member
    WHERE role = 'ADMIN'
    ORDER BY workspace_id, create_at ASC
);

-- ---------------------------------------------------------------------------
-- 3. apj_folder 상속 제어 필드 추가
-- inherit_from_parent=false 폴더부터 부모 권한 상속이 끊긴다.
-- base_access는 상속 체인이 끝났을 때 적용하는 기본값이다.
-- 기존 폴더는 모두 상속 ON + 기본 ALLOW 로 초기화해 현재 동작을 보존한다.
-- ---------------------------------------------------------------------------

ALTER TABLE apj_folder
    ADD COLUMN inherit_from_parent boolean NOT NULL DEFAULT true,
    ADD COLUMN base_access         varchar(10) NOT NULL DEFAULT 'ALLOW';

-- ---------------------------------------------------------------------------
-- 4. workspace_invitation — 이메일 기반 초대 흐름
-- 토큰으로 수락/거절하는 비동기 초대 방식을 지원한다.
-- ---------------------------------------------------------------------------

CREATE TABLE workspace_invitation
(
    id           uuid         NOT NULL DEFAULT gen_random_uuid(),
    workspace_id uuid         NOT NULL,
    email        varchar(100) NOT NULL,
    role         varchar(20)  NOT NULL DEFAULT 'MEMBER',
    token        varchar(64)  NOT NULL,
    status       varchar(20)  NOT NULL DEFAULT 'PENDING',
    invited_by   uuid         NOT NULL,
    expires_at   timestamp(6) NOT NULL,
    create_at    timestamp(6),
    update_at    timestamp(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_invitation_token UNIQUE (token),
    CONSTRAINT fk_invitation_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (workspace_id)
);

COMMENT ON TABLE workspace_invitation IS '이메일 기반 워크스페이스 초대. 토큰으로 수락/거절한다.';
COMMENT ON COLUMN workspace_invitation.status IS 'PENDING | ACCEPTED | DECLINED | EXPIRED';

CREATE INDEX idx_invitation_token ON workspace_invitation (token);
CREATE INDEX idx_invitation_ws_status ON workspace_invitation (workspace_id, status);
CREATE INDEX idx_invitation_email ON workspace_invitation (email, status);
