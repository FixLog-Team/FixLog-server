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

-- 트러블슈팅 문서
CREATE TABLE IF NOT EXISTS apj_document (
    document_id    varchar(100) NOT NULL,
    folder_id      varchar(100) NOT NULL,
    workspace_id   varchar(100) NOT NULL,
    title          varchar(255) NOT NULL,
    old_block_json json,
    new_block_json json,
    content        text,
    ai_summary     varchar(500),
    ordinal        int          DEFAULT 0,
    create_user    varchar(100),
    create_time    datetime     DEFAULT CURRENT_TIMESTAMP,
    update_time    datetime     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (document_id, folder_id, workspace_id)
) COMMENT='트러블슈팅 문서/페이지 본체';

-- 트러블슈팅 문서 이력
CREATE TABLE IF NOT EXISTS apj_document_history (
    document_id    varchar(100) NOT NULL,
    folder_id      varchar(100) NOT NULL,
    workspace_id   varchar(100) NOT NULL,
    title          varchar(255) NOT NULL,
    old_block_json json,
    new_block_json json,
    content        text,
    ai_summary     varchar(500),
    ordinal        int          DEFAULT 0,
    create_user    varchar(100),
    create_time    datetime     DEFAULT CURRENT_TIMESTAMP,
    update_time    datetime     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (document_id, folder_id, workspace_id)
) COMMENT='트러블슈팅 문서/페이지 이력';

-- 파일
CREATE TABLE IF NOT EXISTS apj_file (
    file_id      varchar(100) NOT NULL,
    workspace_id varchar(100) NOT NULL,
    folder_id    varchar(100),
    log_id       varchar(100),
    file_name    varchar(255) NOT NULL,
    file_url     text         NOT NULL,
    file_size    bigint,
    file_ext     varchar(10),
    create_user  varchar(100),
    create_time  datetime     DEFAULT CURRENT_TIMESTAMP,
    update_time  datetime     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (file_id)
) COMMENT='문서 첨부 및 일반 파일 관리';

-- 폴더
CREATE TABLE IF NOT EXISTS apj_folder (
    folder_id    varchar(100) NOT NULL,
    workspace_id varchar(100) NOT NULL,
    parent_id    varchar(100),
    folder_name  varchar(100) NOT NULL,
    ordinal      int          DEFAULT 0,
    usable       int          DEFAULT 1,
    create_user  varchar(100),
    create_time  datetime     DEFAULT CURRENT_TIMESTAMP,
    update_user  varchar(100),
    update_time  datetime     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (folder_id, workspace_id)
) COMMENT='워크스페이스 내 폴더 구조';

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

-- 유저별 시스템 권한 매핑
CREATE TABLE IF NOT EXISTS apj_role_user (
    user_id     varchar(100) NOT NULL,
    role_id     varchar(10)  NOT NULL,
    ordinal     int          DEFAULT 0,
    usable      int          DEFAULT 1,
    create_user varchar(30),
    create_time datetime     DEFAULT CURRENT_TIMESTAMP,
    create_ip   varchar(30),
    update_user varchar(30),
    update_time datetime,
    update_ip   varchar(30),
    PRIMARY KEY (user_id, role_id)
) COMMENT='유저별 시스템 권한 매핑';

-- 사용자 정보
CREATE TABLE IF NOT EXISTS apj_user (
    user_id         varchar(100) NOT NULL,
    user_name       varchar(50),
    password        varchar(255),
    user_status     varchar(10),
    email           varchar(100) NOT NULL,
    last_login_time datetime,
    ordinal         int          DEFAULT 0,
    create_time     datetime     DEFAULT CURRENT_TIMESTAMP,
    create_ip       varchar(30),
    update_user     varchar(30),
    update_time     datetime     DEFAULT CURRENT_TIMESTAMP,
    update_ip       varchar(30),
    PRIMARY KEY (user_id)
) COMMENT='사용자 정보';

-- 비밀번호 변경 이력
CREATE TABLE IF NOT EXISTS apj_user_password_change (
    create_time datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id     varchar(100) NOT NULL,
    bf_password varchar(255),
    af_password varchar(255),
    PRIMARY KEY (create_time, user_id)
) COMMENT='비밀번호 변경 이력';

-- 비밀번호 입력 실패
CREATE TABLE IF NOT EXISTS apj_user_password_fail (
    user_id      varchar(100) NOT NULL,
    user_fail_seq int         DEFAULT 0,
    create_user  varchar(30),
    create_time  datetime     DEFAULT CURRENT_TIMESTAMP,
    create_ip    varchar(30),
    PRIMARY KEY (user_id)
) COMMENT='비밀번호 입력 실패 횟수 관리';

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
('ACTIVE',   'WS_STAT',   '활성 상태',        NULL, NULL, NULL, 1, 1, 'taehokwon48', '2026-01-27 23:36:59', '127.0.0.1', NULL, '2026-01-27 23:36:59', NULL),
('INACTIVE',  'WS_STAT',  '비활성 상태',      NULL, NULL, NULL, 2, 1, 'taehokwon48', '2026-01-27 23:36:59', '127.0.0.1', NULL, '2026-01-27 23:36:59', NULL),
('ARCHIVED',  'WS_STAT',  '보관(읽기 전용)',  NULL, NULL, NULL, 3, 1, 'taehokwon48', '2026-01-27 23:36:59', '127.0.0.1', NULL, '2026-01-27 23:36:59', NULL),
('TRB',  'LOG_TYPE', '트러블슈팅 로그',        NULL, NULL, NULL, 1, 1, 'taehokwon48', '2026-01-27 23:36:59', '127.0.0.1', NULL, '2026-01-27 23:36:59', NULL),
('DEV',  'LOG_TYPE', '개발 일지',              NULL, NULL, NULL, 2, 1, 'taehokwon48', '2026-01-27 23:36:59', '127.0.0.1', NULL, '2026-01-27 23:36:59', NULL),
('REQ',  'LOG_TYPE', '요구사항 명세',          NULL, NULL, NULL, 3, 1, 'taehokwon48', '2026-01-27 23:36:59', '127.0.0.1', NULL, '2026-01-27 23:36:59', NULL),
('MEET', 'LOG_TYPE', '회의록',                 NULL, NULL, NULL, 4, 1, 'taehokwon48', '2026-01-27 23:36:59', '127.0.0.1', NULL, '2026-01-27 23:36:59', NULL),
('IMG',  'FILE_TYPE', '이미지(PNG/JPG)',        NULL, NULL, NULL, 1, 1, 'taehokwon48', '2026-01-27 23:36:59', '127.0.0.1', NULL, '2026-01-27 23:36:59', NULL),
('DOC',  'FILE_TYPE', '문서(HWP/DOCX)',         NULL, NULL, NULL, 2, 1, 'taehokwon48', '2026-01-27 23:36:59', '127.0.0.1', NULL, '2026-01-27 23:36:59', NULL),
('PDF',  'FILE_TYPE', 'PDF 문서',               NULL, NULL, NULL, 3, 1, 'taehokwon48', '2026-01-27 23:36:59', '127.0.0.1', NULL, '2026-01-27 23:36:59', NULL),
('SRC',  'FILE_TYPE', '소스 코드',              NULL, NULL, NULL, 4, 1, 'taehokwon48', '2026-01-27 23:36:59', '127.0.0.1', NULL, '2026-01-27 23:36:59', NULL),
('KO',   'LANG_CODE', '한국어',                 NULL, NULL, NULL, 1, 1, 'taehokwon48', '2026-01-27 23:36:59', '127.0.0.1', NULL, '2026-01-27 23:36:59', NULL),
('EN',   'LANG_CODE', '영어',                   NULL, NULL, NULL, 2, 1, 'taehokwon48', '2026-01-27 23:36:59', '127.0.0.1', NULL, '2026-01-27 23:36:59', NULL),
('JP',   'LANG_CODE', '일본어',                 NULL, NULL, NULL, 3, 1, 'taehokwon48', '2026-01-27 23:36:59', '127.0.0.1', NULL, '2026-01-27 23:36:59', NULL);

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

INSERT IGNORE INTO apj_user (user_id, user_name, password, user_status, email, last_login_time, ordinal, create_time, create_ip, update_user, update_time, update_ip) VALUES
('taehokwon48', '권태호',  '1234', 'ACTIVE',   'admin@apj.com', NULL, 0, '2026-01-27 22:27:28', '192.168.56.1',   NULL, '2026-01-27 22:27:28', NULL),
('testUser1',   '테스트1', '1234', 'ACTIVE',   'test1@apj.com', NULL, 0, '2026-01-27 22:27:28', '192.168.56.101', NULL, '2026-01-27 22:27:28', NULL),
('testUser2',   '테스트2', '1234', 'ACTIVE',   'test2@apj.com', NULL, 0, '2026-01-27 22:27:28', '192.168.56.102', NULL, '2026-01-27 22:27:28', NULL),
('testUser3',   '테스트3', '1234', 'ACTIVE',   'test3@apj.com', NULL, 0, '2026-01-27 22:27:28', '127.0.0.1',      NULL, '2026-01-27 22:27:28', NULL),
('testUser4',   '테스트4', '1234', 'INACTIVE', 'test4@apj.com', NULL, 0, '2026-01-27 22:27:28', '211.11.22.33',   NULL, '2026-01-27 22:27:28', NULL);

INSERT IGNORE INTO apj_role_user (user_id, role_id, ordinal, usable, create_user, create_time, create_ip, update_user, update_time, update_ip) VALUES
('taehokwon48', 'SUPER', 1, 1, 'SYSTEM',      '2026-01-27 22:32:40', '127.0.0.1',     NULL, NULL, NULL),
('testUser1',   'ADMIN', 2, 1, 'taehokwon48', '2026-01-27 22:32:40', '192.168.56.1',  NULL, NULL, NULL),
('testUser2',   'USER',  3, 1, 'taehokwon48', '2026-01-27 22:32:40', '192.168.56.1',  NULL, NULL, NULL),
('testUser3',   'USER',  4, 1, 'taehokwon48', '2026-01-27 22:32:40', '192.168.56.1',  NULL, NULL, NULL),
('testUser4',   'GUEST', 5, 1, 'taehokwon48', '2026-01-27 22:32:40', '192.168.56.1',  NULL, NULL, NULL);

INSERT IGNORE INTO apj_workspace (workspace_id, workspace_name, workspace_status, owner_user_id, ordinal, usable, create_user, create_time, create_ip, update_user, update_time, update_ip) VALUES
('WS_TEST',    '테스트 워크스페이스',  'ACTIVE', 'taehokwon48', 0, 1, NULL, '2026-01-27 22:37:57', NULL, NULL, '2026-01-27 22:37:57', NULL),
('WS_PROJECT', '프로젝트 기획 관련',  'ACTIVE', 'testUser1',   0, 1, NULL, '2026-01-27 22:37:57', NULL, NULL, '2026-01-27 22:37:57', NULL),
('WS_AI',      'AI 연구소',           'ACTIVE', 'testUser2',   0, 1, NULL, '2026-01-27 22:37:57', NULL, NULL, '2026-01-27 22:37:57', NULL);

INSERT IGNORE INTO apj_workspace_user (user_id, role_id, workspace_id, ordinal, usable, create_time) VALUES
('taehokwon48', 'WS_OWN', 'WS_TEST',    1, 1, '2026-01-27 23:13:30'),
('taehokwon48', 'WS_OWN', 'WS_PROJECT', 2, 1, '2026-01-27 23:13:30'),
('taehokwon48', 'WS_MGR', 'WS_AI',      3, 1, '2026-01-27 23:13:30'),
('testUser1',   'WS_OWN', 'WS_AI',      1, 1, '2026-01-27 23:13:41'),
('testUser1',   'WS_EDT', 'WS_TEST',    2, 1, '2026-01-27 23:13:41'),
('testUser2',   'WS_EDT', 'WS_TEST',    1, 1, '2026-01-27 23:13:41'),
('testUser3',   'WS_RD',  'WS_PROJECT', 1, 1, '2026-01-27 23:13:41'),
('testUser4',   'WS_GST', 'WS_AI',      1, 1, '2026-01-27 23:13:41');

INSERT IGNORE INTO apj_folder (folder_id, workspace_id, parent_id, folder_name, ordinal, usable, create_user, create_time, update_user, update_time) VALUES
('FLD_T_01', 'WS_TEST',    NULL,      '단위 테스트 기록',       1, 1, 'taehokwon48', '2026-01-29 18:36:04', NULL, '2026-01-29 18:36:04'),
('FLD_T_02', 'WS_TEST',    'FLD_T_01','UI 컴포넌트 테스트',     1, 1, 'taehokwon48', '2026-01-29 18:36:04', NULL, '2026-01-29 18:36:04'),
('FLD_T_03', 'WS_TEST',    'FLD_T_01','API 연동 테스트',        2, 1, 'taehokwon48', '2026-01-29 18:36:04', NULL, '2026-01-29 18:36:04'),
('FLD_T_04', 'WS_TEST',    'FLD_T_03','API 연동 테스트 기록',   3, 1, 'taehokwon48', '2026-01-29 18:36:04', NULL, '2026-01-29 18:36:04'),
('FLD_P_01', 'WS_PROJECT', NULL,      '메뉴 추천 앱 기획',      1, 1, 'testUser1',   '2026-01-29 18:36:18', NULL, '2026-01-29 18:36:18'),
('FLD_P_02', 'WS_PROJECT', 'FLD_P_01','요구사항 정의서',        1, 1, 'testUser1',   '2026-01-29 18:36:18', NULL, '2026-01-29 18:36:18'),
('FLD_P_03', 'WS_PROJECT', 'FLD_P_01','데이터베이스 설계도',    2, 1, 'testUser2',   '2026-01-29 18:36:18', NULL, '2026-01-29 18:36:18'),
('FLD_P_04', 'WS_PROJECT', NULL,      '와이어프레임(V0)',        2, 1, 'testUser1',   '2026-01-29 18:36:18', NULL, '2026-01-29 18:36:18'),
('FLD_L_01', 'WS_AI',      NULL,      'AI 프롬프트 엔지니어링', 1, 1, 'testUser2',   '2026-01-29 18:37:25', NULL, '2026-01-29 18:37:25'),
('FLD_L_02', 'WS_AI',      'FLD_L_01','Cursor 활용 팁',         1, 1, 'taehokwon48', '2026-01-29 18:37:25', NULL, '2026-01-29 18:37:25'),
('FLD_L_03', 'WS_AI',      NULL,      'SQL 튜닝 및 아키텍처',   2, 1, 'testUser2',   '2026-01-29 18:37:25', NULL, '2026-01-29 18:37:25');
