CREATE TABLE apj_image
(
    image_id      varchar(100) NOT NULL,
    workspace_id  uuid         NOT NULL,
    object_name   varchar(500) NOT NULL,
    content_type  varchar(100),
    original_name varchar(255),
    file_size     bigint,
    create_user   varchar(100),
    create_time   timestamp(6),
    PRIMARY KEY (image_id)
);
COMMENT ON TABLE apj_image IS '문서 첨부 이미지 메타데이터 (OCI Object Storage 연동)';
