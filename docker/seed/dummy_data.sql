-- =====================================================================
-- FixLog 개발용 더미 데이터
-- ---------------------------------------------------------------------
-- 대상: PostgreSQL (fixlog DB)
-- 성격: 로컬 개발/테스트용. 자동 실행되지 않음(docker/init/ 아님).
-- 실행:
--   docker exec -i fixlog-postgres psql -U user -d fixlog -v ON_ERROR_STOP=1 < docker/seed/dummy_data.sql
-- 특징: 멱등(re-runnable) — 아래 워크스페이스의 기존 더미를 지우고 다시 넣는다.
-- 구성: 워크스페이스 1개 / 사용자 2명(OWNER, MEMBER) / 폴더 3개(중첩) / 문서 5개(루트 문서 포함)
--       + 권한 설정 예시 3건
-- =====================================================================

-- 재실행 대비 기존 더미 정리 (FK 역순: 권한 → 멤버 → 문서 → 폴더 → 워크스페이스 → 사용자)
DELETE FROM apj_permission_override WHERE workspace_id = 'ws-hong';
DELETE FROM apj_workspace_member    WHERE workspace_id = 'ws-hong';
DELETE FROM apj_document WHERE create_user IN (
    '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222');
DELETE FROM apj_folder   WHERE create_user IN (
    '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222');
DELETE FROM apj_workspace WHERE workspace_id = 'ws-hong';
DELETE FROM "fixLog_user" WHERE user_id IN (
    '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222');

-- 사용자 (홍길동 = 워크스페이스 OWNER, 김민후 = 일반 MEMBER)
INSERT INTO "fixLog_user" (user_id, user_name, email, user_status, last_login_at, create_at, update_at) VALUES
('11111111-1111-1111-1111-111111111111', '홍길동', 'hong@fixlog.dev', 'ACTIVE',
 '2026-07-08 09:00:00', '2026-07-01 10:00:00', '2026-07-08 09:00:00'),
('22222222-2222-2222-2222-222222222222', '김민후', 'minhu@fixlog.dev', 'ACTIVE',
 '2026-07-08 09:30:00', '2026-07-01 11:00:00', '2026-07-08 09:30:00');

-- 워크스페이스 = 권한 트리의 루트. 루트는 INHERIT이 될 수 없고 기본은 ALLOW다.
INSERT INTO apj_workspace (workspace_id, workspace_name, base_access, usable, create_user, create_time, update_user, update_time) VALUES
('ws-hong', '홍길동 워크스페이스', 'ALLOW', 1,
 '11111111-1111-1111-1111-111111111111', '2026-07-01 10:00:00', '11111111-1111-1111-1111-111111111111', '2026-07-01 10:00:00');

-- 멤버십과 운영권. OWNER/ADMIN은 판정이 ALLOW로 단축되므로 Deny 설정이 적용되지 않는다.
INSERT INTO apj_workspace_member (workspace_id, user_id, role, create_user, create_time) VALUES
('ws-hong', '11111111-1111-1111-1111-111111111111', 'OWNER',  '11111111-1111-1111-1111-111111111111', '2026-07-01 10:00:00'),
('ws-hong', '22222222-2222-2222-2222-222222222222', 'MEMBER', '11111111-1111-1111-1111-111111111111', '2026-07-01 11:00:00');

-- 폴더 (create_user = 사용자 uuid, ordinal = 표시 순서)
-- base_access = INHERIT 이면 부모를 따른다. Operations 폴더만 여기서 상속을 끊고 DENY로 확정한다.
INSERT INTO apj_folder (folder_id, workspace_id, parent_id, folder_name, base_access, ordinal, usable, create_user, create_time, update_user, update_time) VALUES
('folder-eng',    'ws-hong', NULL,         'Engineering', 'INHERIT', 0, 1, '11111111-1111-1111-1111-111111111111', '2026-07-01 10:00:00', '11111111-1111-1111-1111-111111111111', '2026-07-01 10:00:00'),
('folder-ops',    'ws-hong', NULL,         'Operations',  'DENY',    1, 1, '11111111-1111-1111-1111-111111111111', '2026-07-01 10:10:00', '11111111-1111-1111-1111-111111111111', '2026-07-01 10:10:00'),
('folder-guides', 'ws-hong', 'folder-eng', 'Guides',      'INHERIT', 0, 1, '11111111-1111-1111-1111-111111111111', '2026-07-01 10:05:00', '11111111-1111-1111-1111-111111111111', '2026-07-01 10:05:00');

-- 문서 (blocks = 블록 JSON, ordinal = 폴더 내 순서, 루트 문서는 folder_id=NULL)
INSERT INTO apj_document (document_id, workspace_id, folder_id, title, blocks, plain_text, content_hash, base_access, ordinal, usable, create_user, create_time, update_user, update_time) VALUES
('doc-1', 'ws-hong', 'folder-guides', 'DB 커넥션 풀 고갈 트러블슈팅',
 '[{"type":"header","data":{"text":"증상","level":2}},{"type":"paragraph","data":{"text":"피크 시간대에 커넥션 풀이 고갈되어 요청이 대기함"}},{"type":"code","data":{"code":"SHOW PROCESSLIST;"}}]',
 '증상 피크 시간대에 커넥션 풀이 고갈되어 요청이 대기함 SHOW PROCESSLIST;', NULL, 'INHERIT', 0, 1,
 '11111111-1111-1111-1111-111111111111', '2026-07-02 11:00:00', '11111111-1111-1111-1111-111111111111', '2026-07-05 14:30:00'),
('doc-2', 'ws-hong', 'folder-guides', 'JWT 토큰 만료 처리',
 '[{"type":"paragraph","data":{"text":"access token 만료 시 refresh token으로 재발급"}},{"type":"list","data":{"items":["401 감지","refresh 호출","원요청 재시도"]}}]',
 'access token 만료 시 refresh token으로 재발급 401 감지 refresh 호출 원요청 재시도', NULL, 'INHERIT', 1, 1,
 '11111111-1111-1111-1111-111111111111', '2026-07-03 09:20:00', '11111111-1111-1111-1111-111111111111', '2026-07-06 10:00:00'),
('doc-3', 'ws-hong', 'folder-eng', '배포 체크리스트',
 '[{"type":"header","data":{"text":"릴리스 전 확인","level":2}},{"type":"list","data":{"items":["마이그레이션 확인","롤백 플랜","모니터링 알림"]}}]',
 '릴리스 전 확인 마이그레이션 확인 롤백 플랜 모니터링 알림', NULL, 'INHERIT', 0, 1,
 '11111111-1111-1111-1111-111111111111', '2026-07-04 16:00:00', '11111111-1111-1111-1111-111111111111', '2026-07-07 09:00:00'),
('doc-4', 'ws-hong', 'folder-ops', '장애 대응 런북',
 '[{"type":"paragraph","data":{"text":"1차 대응: 알림 확인 후 헬스체크"}},{"type":"table","data":{"rows":[["단계","담당"],["감지","온콜"],["복구","백엔드"]]}}]',
 '1차 대응: 알림 확인 후 헬스체크 단계 담당 감지 온콜 복구 백엔드', NULL, 'INHERIT', 0, 1,
 '11111111-1111-1111-1111-111111111111', '2026-07-05 08:30:00', '11111111-1111-1111-1111-111111111111', '2026-07-08 08:45:00'),
('doc-5', 'ws-hong', NULL, '스크래치 메모',
 '[{"type":"paragraph","data":{"text":"폴더 미지정 루트 문서 예시"}}]',
 '폴더 미지정 루트 문서 예시', NULL, 'INHERIT', 0, 1,
 '11111111-1111-1111-1111-111111111111', '2026-07-08 07:00:00', '11111111-1111-1111-1111-111111111111', '2026-07-08 07:00:00');

-- ---------------------------------------------------------------------
-- 권한 설정 예시 (모두 김민후 기준)
-- ---------------------------------------------------------------------
--  1) doc-3 DENY  : 접근 가능한 폴더 안의 한 문서만 차단. 목록 · 검색 · AI 답변에서 모두 빠진다.
--  2) doc-4 ALLOW : 부모(folder-ops)가 DENY여도 이 문서만 직접 공유. 자식에서 먼저 매치되어 ALLOW로 끝난다.
--                   folder-ops는 이름조차 노출되지 않으므로 doc-4는 트리 루트로 끌어올려져 보인다.
--  3) folder-guides ALLOW : 이미 상속으로 ALLOW지만, 조회 화면에서 Source가
--                   Default가 아니라 Direct로 표시되는 것을 확인하기 위한 설정.
INSERT INTO apj_permission_override (node_id, user_id, node_type, workspace_id, effect, create_time, update_user, update_time) VALUES
('doc-3',         '22222222-2222-2222-2222-222222222222', 'DOCUMENT', 'ws-hong', 'DENY',  now(), '11111111-1111-1111-1111-111111111111', now()),
('doc-4',         '22222222-2222-2222-2222-222222222222', 'DOCUMENT', 'ws-hong', 'ALLOW', now(), '11111111-1111-1111-1111-111111111111', now()),
('folder-guides', '22222222-2222-2222-2222-222222222222', 'FOLDER',   'ws-hong', 'ALLOW', now(), '11111111-1111-1111-1111-111111111111', now());
