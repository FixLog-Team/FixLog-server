-- 휴지통과 라벨.
--
-- 삭제는 이미 soft delete(usable=0)라 데이터는 남아 있었고, 조회·복원 경로만 없었다.
-- 휴지통 목록에 "언제 누가 지웠는지"를 보여주기 위해 삭제 시점과 삭제자를 따로 남긴다.

ALTER TABLE apj_folder ADD COLUMN deleted_at timestamp(6);
ALTER TABLE apj_folder ADD COLUMN deleted_by varchar(100);
ALTER TABLE apj_document ADD COLUMN deleted_at timestamp(6);
ALTER TABLE apj_document ADD COLUMN deleted_by varchar(100);

-- 이미 삭제돼 있던 항목은 삭제 시각을 알 수 없다. 마지막 수정 시각으로 근사한다.
-- 지운 사람은 update_user가 곧 삭제자다.
UPDATE apj_folder SET deleted_at = update_time, deleted_by = update_user WHERE usable = 0;
UPDATE apj_document SET deleted_at = update_time, deleted_by = update_user WHERE usable = 0;

-- 휴지통 목록은 워크스페이스 단위 최근 삭제순이다
CREATE INDEX idx_folder_trash ON apj_folder (workspace_id, usable, deleted_at DESC);
CREATE INDEX idx_document_trash ON apj_document (workspace_id, usable, deleted_at DESC);

CREATE TABLE label
(
    id           uuid        NOT NULL DEFAULT gen_random_uuid(),
    workspace_id uuid        NOT NULL,
    label_name   varchar(50) NOT NULL,
    create_at    timestamp(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_label_name UNIQUE (workspace_id, label_name),
    CONSTRAINT fk_label_workspace FOREIGN KEY (workspace_id) REFERENCES workspace (workspace_id)
);
COMMENT ON TABLE label IS '워크스페이스 범위의 분류 태그';

CREATE TABLE document_label
(
    id          uuid         NOT NULL DEFAULT gen_random_uuid(),
    document_id varchar(100) NOT NULL,
    label_id    uuid         NOT NULL,
    create_user varchar(100),
    create_at   timestamp(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_document_label UNIQUE (document_id, label_id),
    CONSTRAINT fk_document_label_document FOREIGN KEY (document_id) REFERENCES apj_document (document_id),
    CONSTRAINT fk_document_label_label FOREIGN KEY (label_id) REFERENCES label (id)
);
COMMENT ON TABLE document_label IS '문서-라벨 매핑';

-- 라벨로 문서 찾기
CREATE INDEX idx_document_label_label ON document_label (label_id);
