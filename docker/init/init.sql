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
    usable       int          DEFAULT 1,
    create_user  varchar(100),
    create_time  timestamp(6),
    update_user  varchar(100),
    update_time  timestamp(6),
    PRIMARY KEY (document_id)
);
COMMENT ON TABLE apj_document IS '트러블슈팅 문서';
