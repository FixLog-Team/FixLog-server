-- FixLog 스키마 baseline.
--
-- 이 시점의 스키마는 기존 docker/init/init.sql이 만들던 것과 동일하다.
-- 이후 모든 스키마 변경은 이 디렉터리의 마이그레이션 스크립트로만 수행한다 (FR-MIG-001).
--
-- 이미 테이블이 존재하는 기존 개발 DB에서는 spring.flyway.baseline-on-migrate=true 설정에 의해
-- 이 스크립트가 실행되지 않고 baseline으로만 기록된다.
--
-- 벡터 테이블(document_embeddings)은 Spring AI PgVectorStore가 직접 생성하므로
-- (VectorStoreConfig의 initializeSchema(true)) 여기서 관리하지 않는다.

-- 사용자 정보 (UserEntity)
CREATE TABLE "fixLog_user"
(
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
CREATE TABLE "fixLog_user_oauth"
(
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
CREATE TABLE apj_folder
(
    folder_id   varchar(100) NOT NULL,
    parent_id   varchar(100),
    folder_name varchar(100) NOT NULL,
    ordinal     int DEFAULT 0,
    usable      int DEFAULT 1,
    create_user varchar(100),
    create_time timestamp(6),
    update_user varchar(100),
    update_time timestamp(6),
    PRIMARY KEY (folder_id)
);
COMMENT ON TABLE apj_folder IS '사용자 폴더 구조';

-- 트러블슈팅 문서 (DocumentEntity)
CREATE TABLE apj_document
(
    document_id  varchar(100) NOT NULL,
    folder_id    varchar(100),
    title        varchar(255) NOT NULL,
    blocks       text,
    plain_text   text,
    content_hash varchar(64),
    ordinal      int DEFAULT 0,
    usable       int DEFAULT 1,
    create_user  varchar(100),
    create_time  timestamp(6),
    update_user  varchar(100),
    update_time  timestamp(6),
    PRIMARY KEY (document_id)
);
COMMENT ON TABLE apj_document IS '트러블슈팅 문서';
