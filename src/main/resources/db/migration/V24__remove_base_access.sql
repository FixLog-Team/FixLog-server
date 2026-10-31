-- base_access 개념 제거.
-- 기본값은 ALLOW. 관리자가 명시적 DENY로 선택적 차단.

ALTER TABLE workspace DROP COLUMN IF EXISTS base_access;
ALTER TABLE apj_folder DROP COLUMN IF EXISTS base_access;
