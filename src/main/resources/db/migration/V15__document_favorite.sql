-- 즐겨찾기: 사용자가 자주 찾는 문서를 워크스페이스 단위로 고정한다.
-- 즐겨찾기는 개인 설정이므로 user_id + document_id 쌍은 유일하다.

CREATE TABLE document_favorite
(
    id          uuid         NOT NULL DEFAULT gen_random_uuid(),
    document_id varchar(100) NOT NULL,
    user_id     uuid         NOT NULL,
    create_at   timestamp(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_document_favorite UNIQUE (document_id, user_id),
    CONSTRAINT fk_favorite_document FOREIGN KEY (document_id) REFERENCES apj_document (document_id)
);
COMMENT ON TABLE document_favorite IS '사용자별 문서 즐겨찾기';

-- 사용자의 즐겨찾기 목록 조회
CREATE INDEX idx_favorite_user ON document_favorite (user_id, create_at DESC);
