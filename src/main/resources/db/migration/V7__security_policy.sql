-- 워크스페이스 보안 정책.
--
-- 행이 없으면 기본값으로 본다. 워크스페이스마다 미리 만들어 두면 워크스페이스 생성 경로가
-- 정책을 알아야 하고, 항목이 늘 때마다 백필이 필요해진다.

CREATE TABLE security_policy
(
    -- 워크스페이스당 하나뿐이라 워크스페이스 ID를 그대로 키로 쓴다
    workspace_id         uuid    NOT NULL,
    -- 구성원 간 공유 허용 여부
    allow_sharing        boolean NOT NULL DEFAULT true,
    allow_download       boolean NOT NULL DEFAULT true,
    enforce_watermark    boolean NOT NULL DEFAULT false,
    audit_retention_days int     NOT NULL DEFAULT 365,
    trash_retention_days int     NOT NULL DEFAULT 30,
    create_at            timestamp(6),
    update_at            timestamp(6),
    PRIMARY KEY (workspace_id),
    CONSTRAINT fk_security_policy_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (workspace_id)
);
COMMENT ON TABLE security_policy IS '워크스페이스 보안 정책 (개별 권한보다 우선)';
