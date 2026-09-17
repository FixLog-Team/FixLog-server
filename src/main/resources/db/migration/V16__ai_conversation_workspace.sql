-- 기존 대화는 소유자의 개인 워크스페이스로 귀속한다.
ALTER TABLE ai_conversation ADD COLUMN workspace_id UUID;

UPDATE ai_conversation c
SET workspace_id = w.workspace_id
FROM workspace w
WHERE w.personal_owner_id = c.user_id;

-- 귀속할 개인 워크스페이스가 없는 데이터는 임의로 배정하지 않고 실패시킨다.
ALTER TABLE ai_conversation ALTER COLUMN workspace_id SET NOT NULL;

-- 워크스페이스 삭제 후에도 대화 기록을 보존하므로 workspace FK는 두지 않는다.
-- 접근 시 WorkspaceContext에서 현재 멤버십을 반드시 확인한다.
CREATE INDEX idx_ai_conversation_user_workspace_active_updated
    ON ai_conversation (user_id, workspace_id, usable, update_time DESC);
