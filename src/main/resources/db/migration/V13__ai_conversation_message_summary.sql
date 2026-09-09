-- AI 대화방
CREATE TABLE ai_conversation
(
    conversation_id       UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id               UUID         NOT NULL,
    title                 VARCHAR(255) NOT NULL,
    next_message_sequence INTEGER      NOT NULL,
    usable                INTEGER      NOT NULL,
    create_time           TIMESTAMP(6) NOT NULL,
    update_time           TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (conversation_id),
    CONSTRAINT fk_ai_conversation_user
        FOREIGN KEY (user_id) REFERENCES "fixLog_user" (user_id)
);

-- AI 메시지
CREATE TABLE ai_message
(
    message_id       UUID         NOT NULL DEFAULT gen_random_uuid(),
    conversation_id  UUID         NOT NULL,
    message_sequence INTEGER      NOT NULL,
    role             VARCHAR(20)  NOT NULL,
    content          TEXT,
    status           VARCHAR(20)  NOT NULL,
    create_time      TIMESTAMP(6) NOT NULL,
    complete_time    TIMESTAMP(6),
    reference_docs   TEXT,
    PRIMARY KEY (message_id),
    CONSTRAINT fk_ai_message_conversation
        FOREIGN KEY (conversation_id) REFERENCES ai_conversation (conversation_id)
);

CREATE INDEX idx_ai_message_conversation
    ON ai_message (conversation_id, message_sequence);

-- 문서 AI 요약 캐시
CREATE TABLE apj_document_ai_summary
(
    document_id  VARCHAR(100) NOT NULL,
    content_hash VARCHAR(64)  NOT NULL,
    summary      TEXT         NOT NULL,
    create_time  TIMESTAMP(6),
    update_time  TIMESTAMP(6),
    PRIMARY KEY (document_id),
    CONSTRAINT fk_ai_summary_document
        FOREIGN KEY (document_id) REFERENCES apj_document (document_id)
);
