-- 문서/폴더 생성자(creator)에게만 편집 권한을 부여하기 위한 플래그
-- 기존 레코드는 false로 초기화 후, 생성자 권한(grantedBy == principalId)은 true로 복원한다.
ALTER TABLE permission ADD COLUMN can_edit BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE permission SET can_edit = TRUE WHERE granted_by = principal_id;
