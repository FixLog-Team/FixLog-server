SET NAMES utf8mb4;
SET time_zone = '+09:00';

-- 공통 코드 그룹
CREATE TABLE IF NOT EXISTS apj_code_group (
    code_group      varchar(30)  NOT NULL,
    code_group_type varchar(30),
    code_group_desc varchar(100),
    ordinal         int          DEFAULT 0,
    usable          int          DEFAULT 1,
    create_user     varchar(30),
    create_time     datetime     DEFAULT CURRENT_TIMESTAMP,
    create_ip       varchar(30),
    update_user     varchar(30),
    update_time     datetime     DEFAULT CURRENT_TIMESTAMP,
    update_ip       varchar(30),
    PRIMARY KEY (code_group)
) COMMENT='공통 코드 그룹';

-- 공통 코드 상세
CREATE TABLE IF NOT EXISTS apj_code (
    code        varchar(30) NOT NULL,
    code_group  varchar(30) NOT NULL,
    code_desc   varchar(50),
    code_option1 varchar(100),
    code_option2 varchar(100),
    code_option3 varchar(100),
    ordinal     int         DEFAULT 0,
    usable      int         DEFAULT 1,
    create_user varchar(30),
    create_time datetime    DEFAULT CURRENT_TIMESTAMP,
    create_ip   varchar(30),
    update_user varchar(30),
    update_time datetime    DEFAULT CURRENT_TIMESTAMP,
    update_ip   varchar(30),
    PRIMARY KEY (code, code_group)
) COMMENT='공통 코드 상세';

-- 사용자 정보 (OAuth 기반 — UserEntity)
CREATE TABLE IF NOT EXISTS `fixLog_user` (
    user_id       uuid         NOT NULL DEFAULT (UUID()),
    user_name     varchar(50)  NOT NULL,
    email         varchar(100) NOT NULL UNIQUE,
    user_status   varchar(20),
    last_login_at datetime(6),
    create_at     datetime(6),
    update_at     datetime(6),
    PRIMARY KEY (user_id)
) COMMENT='사용자 정보 (Google OAuth)';

-- OAuth 연동 정보 (UserOauthEntity)
CREATE TABLE IF NOT EXISTS `fixLog_user_oauth` (
    id          uuid         NOT NULL DEFAULT (UUID()),
    user_id     uuid         NOT NULL,
    provider    varchar(30)  NOT NULL,
    provider_id varchar(255) NOT NULL,
    create_at   datetime(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_oauth_provider (provider, provider_id),
    CONSTRAINT fk_oauth_user FOREIGN KEY (user_id) REFERENCES `fixLog_user` (user_id)
) COMMENT='소셜 로그인 OAuth 연동 정보';

-- 트러블슈팅 문서 (DocumentEntity)
CREATE TABLE IF NOT EXISTS apj_document (
    document_id  varchar(100) NOT NULL,
    workspace_id varchar(100) NOT NULL,
    folder_id    varchar(100),
    title        varchar(255) NOT NULL,
    blocks       text,
    plain_text   text,
    content_hash varchar(64),
    usable       int          DEFAULT 1,
    create_user  varchar(100),
    create_time  datetime(6),
    update_user  varchar(100),
    update_time  datetime(6),
    PRIMARY KEY (document_id)
) COMMENT='트러블슈팅 문서';

-- 폴더 (FolderEntity)
CREATE TABLE IF NOT EXISTS apj_folder (
    folder_id    varchar(100) NOT NULL,
    workspace_id varchar(100) NOT NULL,
    parent_id    varchar(100),
    folder_name  varchar(100) NOT NULL,
    ordinal      int          DEFAULT 0,
    usable       int          DEFAULT 1,
    create_user  varchar(100),
    create_time  datetime(6),
    update_user  varchar(100),
    update_time  datetime(6),
    PRIMARY KEY (folder_id, workspace_id)
) COMMENT='워크스페이스 내 폴더 구조';

-- 파일
CREATE TABLE IF NOT EXISTS apj_file (
    file_id      varchar(100) NOT NULL,
    workspace_id varchar(100) NOT NULL,
    folder_id    varchar(100),
    document_id  varchar(100),
    file_name    varchar(255) NOT NULL,
    file_url     text         NOT NULL,
    file_size    bigint,
    file_ext     varchar(10),
    create_user  varchar(100),
    create_time  datetime     DEFAULT CURRENT_TIMESTAMP,
    update_time  datetime     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (file_id)
) COMMENT='문서 첨부 및 일반 파일 관리';

-- 시스템 권한
CREATE TABLE IF NOT EXISTS apj_role (
    role_id     varchar(10) NOT NULL,
    role_status varchar(30),
    role_desc   varchar(30),
    ordinal     int         DEFAULT 0,
    usable      int         DEFAULT 1,
    create_user varchar(30),
    create_time datetime    DEFAULT CURRENT_TIMESTAMP,
    create_ip   varchar(30),
    update_user varchar(30),
    update_time datetime    DEFAULT CURRENT_TIMESTAMP,
    update_ip   varchar(30),
    PRIMARY KEY (role_id)
);

-- 워크스페이스
CREATE TABLE IF NOT EXISTS apj_workspace (
    workspace_id     varchar(100) NOT NULL,
    workspace_name   varchar(50),
    workspace_status varchar(10),
    owner_user_id    varchar(100),
    ordinal          int          DEFAULT 0,
    usable           int          DEFAULT 1,
    create_user      varchar(30),
    create_time      datetime     DEFAULT CURRENT_TIMESTAMP,
    create_ip        varchar(30),
    update_user      varchar(30),
    update_time      datetime     DEFAULT CURRENT_TIMESTAMP,
    update_ip        varchar(30),
    PRIMARY KEY (workspace_id)
) COMMENT='워크스페이스(드라이브) 정보';

-- 워크스페이스 권한
CREATE TABLE IF NOT EXISTS apj_workspace_role (
    role_id     varchar(10) NOT NULL,
    role_status varchar(30),
    role_desc   varchar(30),
    ordinal     int         DEFAULT 0,
    usable      int         DEFAULT 1,
    create_user varchar(30),
    create_time datetime    DEFAULT CURRENT_TIMESTAMP,
    update_user varchar(30),
    update_time datetime    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id)
) COMMENT='워크스페이스 내부 전용 권한 정의';

-- 워크스페이스 소속 사용자
CREATE TABLE IF NOT EXISTS apj_workspace_user (
    user_id      varchar(100) NOT NULL,
    role_id      varchar(10)  NOT NULL,
    workspace_id varchar(100) NOT NULL,
    ordinal      int          DEFAULT 0,
    usable       int          DEFAULT 1,
    create_time  datetime     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id, workspace_id)
) COMMENT='워크스페이스 소속 사용자 정보';

-- -----------------------------------------------
-- 기초 데이터 (seed data)
-- -----------------------------------------------

INSERT IGNORE INTO apj_code_group (code_group, code_group_type, code_group_desc, ordinal, usable, create_user, create_time, create_ip, update_user, update_time, update_ip) VALUES
('WS_STAT',   'SYSTEM',   '워크스페이스 상태',  0, 1, NULL, '2026-01-27 22:23:58', NULL, NULL, '2026-01-27 22:23:58', NULL),
('LOG_TYPE',  'BUSINESS', '로그 기록 유형',     0, 1, NULL, '2026-01-27 22:23:58', NULL, NULL, '2026-01-27 22:23:58', NULL),
('FILE_TYPE', 'SYSTEM',   '파일 확장자 그룹',   0, 1, NULL, '2026-01-27 22:23:58', NULL, NULL, '2026-01-27 22:23:58', NULL),
('LANG_CODE', 'SYSTEM',   '언어 설정',          0, 1, NULL, '2026-01-27 22:23:58', NULL, NULL, '2026-01-27 22:23:58', NULL);

INSERT IGNORE INTO apj_code (code, code_group, code_desc, code_option1, code_option2, code_option3, ordinal, usable, create_user, create_time, create_ip, update_user, update_time, update_ip) VALUES
('ACTIVE',   'WS_STAT',   '활성 상태',        NULL, NULL, NULL, 1, 1, 'SYSTEM', '2026-01-27 23:36:59', NULL, NULL, '2026-01-27 23:36:59', NULL),
('INACTIVE',  'WS_STAT',  '비활성 상태',      NULL, NULL, NULL, 2, 1, 'SYSTEM', '2026-01-27 23:36:59', NULL, NULL, '2026-01-27 23:36:59', NULL),
('ARCHIVED',  'WS_STAT',  '보관(읽기 전용)',  NULL, NULL, NULL, 3, 1, 'SYSTEM', '2026-01-27 23:36:59', NULL, NULL, '2026-01-27 23:36:59', NULL),
('TRB',  'LOG_TYPE', '트러블슈팅 로그',        NULL, NULL, NULL, 1, 1, 'SYSTEM', '2026-01-27 23:36:59', NULL, NULL, '2026-01-27 23:36:59', NULL),
('DEV',  'LOG_TYPE', '개발 일지',              NULL, NULL, NULL, 2, 1, 'SYSTEM', '2026-01-27 23:36:59', NULL, NULL, '2026-01-27 23:36:59', NULL),
('REQ',  'LOG_TYPE', '요구사항 명세',          NULL, NULL, NULL, 3, 1, 'SYSTEM', '2026-01-27 23:36:59', NULL, NULL, '2026-01-27 23:36:59', NULL),
('MEET', 'LOG_TYPE', '회의록',                 NULL, NULL, NULL, 4, 1, 'SYSTEM', '2026-01-27 23:36:59', NULL, NULL, '2026-01-27 23:36:59', NULL),
('IMG',  'FILE_TYPE', '이미지(PNG/JPG)',        NULL, NULL, NULL, 1, 1, 'SYSTEM', '2026-01-27 23:36:59', NULL, NULL, '2026-01-27 23:36:59', NULL),
('DOC',  'FILE_TYPE', '문서(HWP/DOCX)',         NULL, NULL, NULL, 2, 1, 'SYSTEM', '2026-01-27 23:36:59', NULL, NULL, '2026-01-27 23:36:59', NULL),
('PDF',  'FILE_TYPE', 'PDF 문서',               NULL, NULL, NULL, 3, 1, 'SYSTEM', '2026-01-27 23:36:59', NULL, NULL, '2026-01-27 23:36:59', NULL),
('SRC',  'FILE_TYPE', '소스 코드',              NULL, NULL, NULL, 4, 1, 'SYSTEM', '2026-01-27 23:36:59', NULL, NULL, '2026-01-27 23:36:59', NULL),
('KO',   'LANG_CODE', '한국어',                 NULL, NULL, NULL, 1, 1, 'SYSTEM', '2026-01-27 23:36:59', NULL, NULL, '2026-01-27 23:36:59', NULL),
('EN',   'LANG_CODE', '영어',                   NULL, NULL, NULL, 2, 1, 'SYSTEM', '2026-01-27 23:36:59', NULL, NULL, '2026-01-27 23:36:59', NULL),
('JP',   'LANG_CODE', '일본어',                 NULL, NULL, NULL, 3, 1, 'SYSTEM', '2026-01-27 23:36:59', NULL, NULL, '2026-01-27 23:36:59', NULL);

INSERT IGNORE INTO apj_role (role_id, role_status, role_desc, ordinal, usable, create_user, create_time, create_ip, update_user, update_time, update_ip) VALUES
('SUPER', 'USE', '시스템 최고관리자', 1, 1, NULL, '2026-01-27 22:23:39', NULL, NULL, '2026-01-27 22:23:39', NULL),
('ADMIN', 'USE', '운영 관리자',       2, 1, NULL, '2026-01-27 22:23:39', NULL, NULL, '2026-01-27 22:23:39', NULL),
('USER',  'USE', '일반 사용자',       3, 1, NULL, '2026-01-27 22:23:39', NULL, NULL, '2026-01-27 22:23:39', NULL),
('GUEST', 'USE', '임시 권한',         4, 1, NULL, '2026-01-27 22:23:39', NULL, NULL, '2026-01-27 22:23:39', NULL);

INSERT IGNORE INTO apj_workspace_role (role_id, role_status, role_desc, ordinal, usable, create_user, create_time, update_user, update_time) VALUES
('WS_OWN', 'USE', '워크스페이스 소유자', 1, 1, 'SYSTEM', '2026-01-27 22:47:19', 'SYSTEM', '2026-01-27 22:47:19'),
('WS_MGR', 'USE', '워크스페이스 관리자', 2, 1, 'SYSTEM', '2026-01-27 22:47:19', 'SYSTEM', '2026-01-27 22:47:19'),
('WS_EDT', 'USE', '콘텐츠 편집자',       3, 1, 'SYSTEM', '2026-01-27 22:47:19', 'SYSTEM', '2026-01-27 22:47:19'),
('WS_RD',  'USE', '읽기 전용 멤버',      4, 1, 'SYSTEM', '2026-01-27 22:47:19', 'SYSTEM', '2026-01-27 22:47:19'),
('WS_GST', 'USE', '임시 게스트',         5, 1, 'SYSTEM', '2026-01-27 22:47:19', 'SYSTEM', '2026-01-27 22:47:19');
