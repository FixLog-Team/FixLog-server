-- 이미지 검색: 업로드된 이미지의 AI 분석 결과와 문서 연결을 기록한다.
-- 분석 텍스트(OCR 글자 + 그래프·도표·그림의 의미)는 문서 본문과 함께 벡터 색인되어
-- 이미지 안의 내용까지 의미 기반 검색·답변에 쓰인다.

ALTER TABLE apj_image
    -- 이 이미지를 처음 참조한 문서. 업로드 시점엔 문서가 저장 전이라 비어 있고, 문서 저장 때 채운다.
    -- 문서 복제로 같은 이미지가 여러 문서에 걸릴 수 있어 '최초 연결'만 기록한다.
    ADD COLUMN document_id     varchar(100),
    -- 같은 이미지를 다시 올려도 분석을 재사용하기 위한 내용 해시(sha-256)
    ADD COLUMN content_hash    char(64),
    -- 분석 결과. 색인 대상이며 원본에서 언제든 다시 만들 수 있는 파생 데이터다
    ADD COLUMN analysis_text   text,
    ADD COLUMN analysis_status varchar(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN analysis_error  text,
    ADD COLUMN analyzed_at     timestamp(6);

COMMENT ON COLUMN apj_image.document_id IS '이 이미지를 처음 참조한 문서 (문서 저장 시 채움)';
COMMENT ON COLUMN apj_image.content_hash IS '내용 해시(sha-256). 같은 이미지의 분석 결과 재사용 키';
COMMENT ON COLUMN apj_image.analysis_text IS 'OCR 글자와 그래프·도표·그림 의미를 담은 검색용 텍스트';
COMMENT ON COLUMN apj_image.analysis_status IS 'PENDING | COMPLETED | FAILED | SKIPPED';

-- 분석 완료 후 해당 문서를 다시 색인할 때 역방향으로 찾는다
CREATE INDEX idx_image_document ON apj_image (document_id);

-- 같은 내용의 이미지가 이미 분석됐는지 찾아 LLM 재호출을 피한다
CREATE INDEX idx_image_content_hash ON apj_image (content_hash);
