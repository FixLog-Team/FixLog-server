-- 문서 전용 AI 대화방: 특정 문서를 기준으로 질문하는 대화방에 연결된 문서 ID.
-- NULL이면 일반 검색 대화방.
ALTER TABLE ai_conversation ADD COLUMN IF NOT EXISTS document_id VARCHAR(36) DEFAULT NULL;
