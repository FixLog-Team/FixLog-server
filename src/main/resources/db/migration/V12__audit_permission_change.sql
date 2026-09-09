-- =====================================================================
-- 감사 로그에 권한 변경 기록 담기
-- ---------------------------------------------------------------------
-- 지금까지 audit_log에는 "누가 무엇을 열람했는가"만 쌓였다.
-- 정작 "누가 누구에게 그 접근을 열어줬는가"는 어디에도 남지 않았다.
-- 열람 기록은 사후 조사용이지만 권한 변경은 책임 추적이라, 빠지면
-- "이 사람이 왜 볼 수 있었나"를 되짚을 수 없다.
--
-- 기존 스키마로는 담을 수 없던 것이 셋이다.
--   1. 권한을 받은 대상  — actor_user_id는 부여한 사람이라 대상 자리가 없었다
--   2. 무엇이 어떻게 바뀌었는지 — ALLOW→DENY, MEMBER→ADMIN 같은 전후
--   3. 리소스 없는 사건 — 역할 변경·초대는 폴더도 문서도 아니다
-- =====================================================================

-- 1. 권한을 받거나 잃은 주체. 접근 기록에는 없는 값이므로 NULL을 허용한다.
ALTER TABLE audit_log
    ADD COLUMN target_principal_type varchar(20),
    ADD COLUMN target_principal_id   uuid,
    ADD COLUMN detail                varchar(255);

COMMENT ON COLUMN audit_log.target_principal_type IS '권한 변경의 대상. USER | GROUP';
COMMENT ON COLUMN audit_log.target_principal_id IS '권한을 받거나 잃은 주체';
COMMENT ON COLUMN audit_log.detail IS '변경 전후 요약. 예: "ALLOW → DENY", "MEMBER → ADMIN"';

-- 2. 역할 변경·초대처럼 폴더도 문서도 아닌 사건이 생긴다.
ALTER TABLE audit_log
    ALTER COLUMN resource_type DROP NOT NULL,
    ALTER COLUMN resource_id   DROP NOT NULL;

-- 3. 새 action 값이 기존 varchar(20)을 넘는다 (ACCESS_POLICY_CHANGE = 20, GROUP_MEMBER_CHANGE = 19).
--    경계에 걸치므로 여유를 둔다.
ALTER TABLE audit_log
    ALTER COLUMN action TYPE varchar(30);

-- 4. 관리 화면은 "이 사람에게 무슨 권한이 오갔나"로 조회한다.
CREATE INDEX IF NOT EXISTS idx_audit_target
    ON audit_log (workspace_id, target_principal_id, create_at DESC);
