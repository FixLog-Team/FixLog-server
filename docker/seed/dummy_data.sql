-- =====================================================================
-- FixLog 개발용 더미 데이터
-- ---------------------------------------------------------------------
-- 대상: PostgreSQL (fixlog DB)
-- 성격: 로컬 개발/테스트용. 자동 실행되지 않음(docker/init/ 아님).
-- 실행:
--   docker exec -i fixlog-postgres psql -U user -d fixlog -v ON_ERROR_STOP=1 < docker/seed/dummy_data.sql
-- 특징: 멱등(re-runnable) — 아래 소유자(create_user)의 기존 더미를 지우고 다시 넣는다.
-- 구성: 사용자 1명 / 폴더 3개(중첩) / 문서 5개(루트 문서 포함)
-- =====================================================================

-- 재실행 대비 기존 더미 정리 (문서 → 폴더 → 사용자 순)
DELETE FROM apj_document  WHERE create_user = '11111111-1111-1111-1111-111111111111';
DELETE FROM apj_folder    WHERE create_user = '11111111-1111-1111-1111-111111111111';
DELETE FROM "fixLog_user" WHERE user_id     = '11111111-1111-1111-1111-111111111111';

-- 사용자
INSERT INTO "fixLog_user" (user_id, user_name, email, user_status, last_login_at, create_at, update_at) VALUES
('11111111-1111-1111-1111-111111111111', '홍길동', 'hong@fixlog.dev', 'ACTIVE',
 '2026-07-08 09:00:00', '2026-07-01 10:00:00', '2026-07-08 09:00:00');

-- 폴더 (create_user = 사용자 uuid, ordinal = 표시 순서)
INSERT INTO apj_folder (folder_id, parent_id, folder_name, ordinal, usable, create_user, create_time, update_user, update_time) VALUES
('folder-eng',    NULL,         'Engineering', 0, 1, '11111111-1111-1111-1111-111111111111', '2026-07-01 10:00:00', '11111111-1111-1111-1111-111111111111', '2026-07-01 10:00:00'),
('folder-ops',    NULL,         'Operations',  1, 1, '11111111-1111-1111-1111-111111111111', '2026-07-01 10:10:00', '11111111-1111-1111-1111-111111111111', '2026-07-01 10:10:00'),
('folder-guides', 'folder-eng', 'Guides',      0, 1, '11111111-1111-1111-1111-111111111111', '2026-07-01 10:05:00', '11111111-1111-1111-1111-111111111111', '2026-07-01 10:05:00');

-- 문서 (blocks = 블록 JSON, ordinal = 폴더 내 순서, 루트 문서는 folder_id=NULL)
INSERT INTO apj_document (document_id, folder_id, title, blocks, plain_text, content_hash, ordinal, usable, create_user, create_time, update_user, update_time) VALUES
('doc-1', 'folder-guides', 'DB 커넥션 풀 고갈 트러블슈팅',
 '[{"type":"header","data":{"text":"증상","level":2}},{"type":"paragraph","data":{"text":"피크 시간대에 커넥션 풀이 고갈되어 요청이 대기함"}},{"type":"code","data":{"code":"SHOW PROCESSLIST;"}}]',
 '증상 피크 시간대에 커넥션 풀이 고갈되어 요청이 대기함 SHOW PROCESSLIST;', NULL, 0, 1,
 '11111111-1111-1111-1111-111111111111', '2026-07-02 11:00:00', '11111111-1111-1111-1111-111111111111', '2026-07-05 14:30:00'),
('doc-2', 'folder-guides', 'JWT 토큰 만료 처리',
 '[{"type":"paragraph","data":{"text":"access token 만료 시 refresh token으로 재발급"}},{"type":"list","data":{"items":["401 감지","refresh 호출","원요청 재시도"]}}]',
 'access token 만료 시 refresh token으로 재발급 401 감지 refresh 호출 원요청 재시도', NULL, 1, 1,
 '11111111-1111-1111-1111-111111111111', '2026-07-03 09:20:00', '11111111-1111-1111-1111-111111111111', '2026-07-06 10:00:00'),
('doc-3', 'folder-eng', '배포 체크리스트',
 '[{"type":"header","data":{"text":"릴리스 전 확인","level":2}},{"type":"list","data":{"items":["마이그레이션 확인","롤백 플랜","모니터링 알림"]}}]',
 '릴리스 전 확인 마이그레이션 확인 롤백 플랜 모니터링 알림', NULL, 0, 1,
 '11111111-1111-1111-1111-111111111111', '2026-07-04 16:00:00', '11111111-1111-1111-1111-111111111111', '2026-07-07 09:00:00'),
('doc-4', 'folder-ops', '장애 대응 런북',
 '[{"type":"paragraph","data":{"text":"1차 대응: 알림 확인 후 헬스체크"}},{"type":"table","data":{"rows":[["단계","담당"],["감지","온콜"],["복구","백엔드"]]}}]',
 '1차 대응: 알림 확인 후 헬스체크 단계 담당 감지 온콜 복구 백엔드', NULL, 0, 1,
 '11111111-1111-1111-1111-111111111111', '2026-07-05 08:30:00', '11111111-1111-1111-1111-111111111111', '2026-07-08 08:45:00'),
('doc-5', NULL, '스크래치 메모',
 '[{"type":"paragraph","data":{"text":"폴더 미지정 루트 문서 예시"}}]',
 '폴더 미지정 루트 문서 예시', NULL, 0, 1,
 '11111111-1111-1111-1111-111111111111', '2026-07-08 07:00:00', '11111111-1111-1111-1111-111111111111', '2026-07-08 07:00:00');
