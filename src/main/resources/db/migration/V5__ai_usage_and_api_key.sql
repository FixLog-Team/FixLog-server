-- AI 사용량 기록과 사용자 API Key.

CREATE TABLE ai_usage
(
    id                  uuid         NOT NULL DEFAULT gen_random_uuid(),
    workspace_id        uuid         NOT NULL,
    user_id             uuid         NOT NULL,
    tier                varchar(20)  NOT NULL,
    model               varchar(100) NOT NULL,
    input_tokens        bigint       NOT NULL DEFAULT 0,
    output_tokens       bigint       NOT NULL DEFAULT 0,
    -- 집계 시점 단가를 기록에 박아 둔다. 단가표가 바뀌어도 과거 비용이 소급 변조되지 않는다
    input_price_per_1k  numeric(12, 6),
    output_price_per_1k numeric(12, 6),
    cost                numeric(14, 6),
    -- 실패한 호출도 남긴다. 실패가 몰리는 구간을 못 보면 원인 추적이 안 된다
    success             boolean      NOT NULL DEFAULT true,
    create_at           timestamp(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_usage_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (workspace_id),
    CONSTRAINT fk_ai_usage_user FOREIGN KEY (user_id) REFERENCES "fixLog_user" (user_id)
);
COMMENT ON TABLE ai_usage IS 'AI 호출 단위 사용 기록';

-- 한도 판정은 (워크스페이스, 계층, 기간) 합계다
CREATE INDEX idx_ai_usage_quota ON ai_usage (workspace_id, tier, create_at);

CREATE TABLE user_api_key
(
    id            uuid         NOT NULL DEFAULT gen_random_uuid(),
    user_id       uuid         NOT NULL,
    provider      varchar(30)  NOT NULL,
    -- 원문이 아니라 암호문이다. 어떤 응답에도 나가지 않는다
    encrypted_key varchar(1000) NOT NULL,
    -- 어떤 키를 등록했는지 알아보기 위한 마지막 네 자리
    key_hint      varchar(8)   NOT NULL,
    create_at     timestamp(6),
    update_at     timestamp(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_user_api_key_provider UNIQUE (user_id, provider),
    CONSTRAINT fk_user_api_key_user FOREIGN KEY (user_id) REFERENCES "fixLog_user" (user_id)
);
COMMENT ON TABLE user_api_key IS '사용자가 등록한 고성능 모델 자격증명 (암호화 저장)';
