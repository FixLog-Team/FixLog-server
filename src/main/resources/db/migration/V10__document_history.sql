-- 문서 히스토리 (과거 버전 스냅샷).
-- dev 브랜치에서 ddl-auto=update로 자동 생성됐던 테이블을 Flyway 마이그레이션으로 명시한다.
-- 현재 본문은 apj_document에만 존재하고, 저장할 때 덮어쓰기 직전 버전이 여기로 밀려온다.

CREATE TABLE IF NOT EXISTS apj_document_history
(
    history_id   varchar(100) NOT NULL,
    document_id  varchar(100) NOT NULL,
    title        varchar(255) NOT NULL,
    blocks       text,
    content_hash varchar(64),
    -- MANUAL: 일반 저장, RESTORE: 되돌리기로 생성
    source       varchar(20)  NOT NULL,
    create_user  varchar(100),
    create_time  timestamp(6),
    PRIMARY KEY (history_id)
);

CREATE INDEX IF NOT EXISTS idx_document_history_document
    ON apj_document_history (document_id, create_time);
