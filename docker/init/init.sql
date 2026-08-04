SET client_encoding = 'UTF8';
SET timezone = 'Asia/Seoul';

-- 테이블 생성은 여기서 하지 않는다.
--
-- 스키마는 Flyway가 소유한다: src/main/resources/db/migration/
-- 애플리케이션이 뜰 때 마이그레이션이 실행되어 테이블이 만들어진다.
-- 스키마를 바꾸려면 이 파일이 아니라 새 마이그레이션 스크립트를 추가한다 (FR-MIG-001).
--
-- 이 파일은 컨테이너 최초 기동 시 한 번만 실행되며, DB 레벨 초기 설정만 담당한다.
