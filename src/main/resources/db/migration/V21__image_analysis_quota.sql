-- 워크스페이스별 하루 비전 분석 호출 수. 폭주로 비용이 터지는 것을 막는 상한의 근거가 된다.
-- 분석 결과를 재사용한 건(내용 해시 일치)은 모델을 부르지 않으므로 세지 않는다.

CREATE TABLE image_analysis_quota
(
    id             uuid    NOT NULL DEFAULT gen_random_uuid(),
    workspace_id   uuid    NOT NULL,
    usage_date     date    NOT NULL,
    analyzed_count integer NOT NULL DEFAULT 0,
    update_time    timestamp(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_image_analysis_quota UNIQUE (workspace_id, usage_date)
);
COMMENT ON TABLE image_analysis_quota IS '워크스페이스별 일일 이미지 분석 호출 수 (상한 판정용)';
COMMENT ON COLUMN image_analysis_quota.analyzed_count IS '실제 비전 모델 호출 수. 해시 재사용 건은 제외';
