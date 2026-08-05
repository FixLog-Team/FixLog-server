-- 문서 리비전과 감사 로그.
--
-- 둘 다 append-only다. 리비전은 롤백조차 기존 행을 건드리지 않고 새 행을 쌓고,
-- 감사 로그는 수정·삭제 경로 자체를 두지 않는다.

CREATE TABLE document_revision
(
    id               uuid         NOT NULL DEFAULT gen_random_uuid(),
    document_id      varchar(100) NOT NULL,
    workspace_id     uuid         NOT NULL,
    -- 문서 안에서 1부터 증가한다
    revision_no      int          NOT NULL,
    title            varchar(255) NOT NULL,
    blocks           text,
    plain_text       text,
    content_hash     varchar(64),
    -- 어느 리비전에서 되돌린 결과인지. 일반 저장이면 NULL
    restored_from_no int,
    create_user      varchar(100),
    create_at        timestamp(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_document_revision_no UNIQUE (document_id, revision_no),
    CONSTRAINT fk_revision_document FOREIGN KEY (document_id) REFERENCES apj_document (document_id),
    CONSTRAINT fk_revision_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (workspace_id)
);
COMMENT ON TABLE document_revision IS '문서 본문의 시점 스냅샷 (불변)';

-- 목록은 항상 최신순이다
CREATE INDEX idx_revision_document ON document_revision (document_id, revision_no DESC);

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
