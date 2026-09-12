-- document_revision 테이블 제거.
-- 문서 버전 관리는 V4에서 만든 document_revision이 아니라
-- dev 브랜치의 apj_document_history로 단일화한다 (hs/saas 통합 정리).
DROP TABLE IF EXISTS document_revision;
