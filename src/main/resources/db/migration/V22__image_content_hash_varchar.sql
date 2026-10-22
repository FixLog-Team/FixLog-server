-- char(64)로 만들어진 content_hash를 varchar(64)로 변경한다.
-- Hibernate가 String 필드를 varchar로 매핑하는데, PostgreSQL의 char(bpchar)와 맞지 않아
-- 스키마 검증이 실패한다.
ALTER TABLE apj_image ALTER COLUMN content_hash TYPE varchar(64);
