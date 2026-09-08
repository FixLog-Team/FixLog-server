-- 감사 로그. append-only로 수정·삭제 경로 자체를 두지 않는다.

CREATE TABLE audit_log
(
    id            uuid         NOT NULL DEFAULT gen_random_uuid(),
    workspace_id  uuid         NOT NULL,
    actor_user_id uuid         NOT NULL,
    action        varchar(20)  NOT NULL,
    resource_type varchar(20)  NOT NULL,
    resource_id   varchar(100) NOT NULL,
    -- 거부된 접근도 남긴다. 누가 무엇을 열려다 막혔는지가 조사에 필요하다
    result        varchar(20)  NOT NULL,
    -- 관리자 특권으로 접근했는지. 권한을 받아서 본 것과 구분해야 한다
    via_admin     boolean      NOT NULL DEFAULT false,
    create_at     timestamp(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (workspace_id),
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES "fixLog_user" (user_id)
);
COMMENT ON TABLE audit_log IS '접근 감사 기록 (append-only)';

-- 관리자 콘솔은 워크스페이스 단위 최신순으로 훑는다
CREATE INDEX idx_audit_workspace_time ON audit_log (workspace_id, create_at DESC);
CREATE INDEX idx_audit_resource ON audit_log (resource_type, resource_id, create_at DESC);
