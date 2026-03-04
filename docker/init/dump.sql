--
-- PostgreSQL database dump
--

-- Dumped from database version 17.4
-- Dumped by pg_dump version 17.0

-- Started on 2026-01-29 19:18:52

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- TOC entry 4 (class 2615 OID 2200)
-- Name: public; Type: SCHEMA; Schema: -; Owner: pg_database_owner
--

-- CREATE SCHEMA public;


-- ALTER SCHEMA public OWNER TO pg_database_owner;

--
-- TOC entry 4425 (class 0 OID 0)
-- Dependencies: 4
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: pg_database_owner
--

-- COMMENT ON SCHEMA public IS 'standard public schema';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- TOC entry 220 (class 1259 OID 33111)
-- Name: apj_code; Type: TABLE; Schema: public; Owner: kth
--

CREATE TABLE public.apj_code (
    code character varying(30) NOT NULL,
    code_group character varying(30) NOT NULL,
    code_desc character varying(50),
    code_option1 character varying(100),
    code_option2 character varying(100),
    code_option3 character varying(100),
    ordinal integer DEFAULT 0,
    usable integer DEFAULT 1,
    create_user character varying(30),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    create_ip character varying(30),
    update_user character varying(30),
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_ip character varying(30)
);


ALTER TABLE public.apj_code OWNER TO "user";

--
-- TOC entry 4426 (class 0 OID 0)
-- Dependencies: 220
-- Name: TABLE apj_code; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON TABLE public.apj_code IS '공통 코드 상세';


--
-- TOC entry 4427 (class 0 OID 0)
-- Dependencies: 220
-- Name: COLUMN apj_code.code; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_code.code IS '코드';


--
-- TOC entry 4428 (class 0 OID 0)
-- Dependencies: 220
-- Name: COLUMN apj_code.code_group; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_code.code_group IS '코드그룹';


--
-- TOC entry 219 (class 1259 OID 33102)
-- Name: apj_code_group; Type: TABLE; Schema: public; Owner: kth
--

CREATE TABLE public.apj_code_group (
    code_group character varying(30) NOT NULL,
    code_group_type character varying(30),
    code_group_desc character varying(100),
    ordinal integer DEFAULT 0,
    usable integer DEFAULT 1,
    create_user character varying(30),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    create_ip character varying(30),
    update_user character varying(30),
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_ip character varying(30)
);


ALTER TABLE public.apj_code_group OWNER TO "user";

--
-- TOC entry 4429 (class 0 OID 0)
-- Dependencies: 219
-- Name: TABLE apj_code_group; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON TABLE public.apj_code_group IS '공통 코드 그룹';


--
-- TOC entry 4430 (class 0 OID 0)
-- Dependencies: 219
-- Name: COLUMN apj_code_group.code_group; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_code_group.code_group IS '코드그룹';


--
-- TOC entry 225 (class 1259 OID 33159)
-- Name: apj_document; Type: TABLE; Schema: public; Owner: kth
--

CREATE TABLE public.apj_document (
    document_id character varying(100) NOT NULL,
    folder_id character varying(100) NOT NULL,
    workspace_id character varying(100) NOT NULL,
    title character varying(255) NOT NULL,
    old_block_json json,
    new_block_json json,
    content text,
    ai_summary character varying(500),
    ordinal integer DEFAULT 0,
    create_user character varying(100),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE public.apj_document OWNER TO "user";

--
-- TOC entry 4431 (class 0 OID 0)
-- Dependencies: 225
-- Name: TABLE apj_document; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON TABLE public.apj_document IS '트러블슈팅 문서/페이지 본체';


--
-- TOC entry 4432 (class 0 OID 0)
-- Dependencies: 225
-- Name: COLUMN apj_document.content; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_document.content IS '본문';


--
-- TOC entry 4433 (class 0 OID 0)
-- Dependencies: 225
-- Name: COLUMN apj_document.ai_summary; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_document.ai_summary IS 'AI 한 줄 요약 (검색 및 리스트용)';


--
-- TOC entry 226 (class 1259 OID 33169)
-- Name: apj_document_history; Type: TABLE; Schema: public; Owner: kth
--

CREATE TABLE public.apj_document_history (
    document_id character varying(100) NOT NULL,
    folder_id character varying(100) NOT NULL,
    workspace_id character varying(100) NOT NULL,
    title character varying(255) NOT NULL,
    old_block_json json,
    new_block_json json,
    content text,
    ai_summary character varying(500),
    ordinal integer DEFAULT 0,
    create_user character varying(100),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE public.apj_document_history OWNER TO "user";

--
-- TOC entry 4434 (class 0 OID 0)
-- Dependencies: 226
-- Name: TABLE apj_document_history; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON TABLE public.apj_document_history IS '트러블슈팅 문서/페이지 본체';


--
-- TOC entry 4435 (class 0 OID 0)
-- Dependencies: 226
-- Name: COLUMN apj_document_history.content; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_document_history.content IS '본문';


--
-- TOC entry 4436 (class 0 OID 0)
-- Dependencies: 226
-- Name: COLUMN apj_document_history.ai_summary; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_document_history.ai_summary IS 'AI 한 줄 요약 (검색 및 리스트용)';


--
-- TOC entry 229 (class 1259 OID 40968)
-- Name: apj_file; Type: TABLE; Schema: public; Owner: kth
--

CREATE TABLE public.apj_file (
    file_id character varying(100) NOT NULL,
    workspace_id character varying(100) NOT NULL,
    folder_id character varying(100),
    log_id character varying(100),
    file_name character varying(255) NOT NULL,
    file_url text NOT NULL,
    file_size bigint,
    file_ext character varying(10),
    create_user character varying(100),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE public.apj_file OWNER TO "user";

--
-- TOC entry 4437 (class 0 OID 0)
-- Dependencies: 229
-- Name: TABLE apj_file; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON TABLE public.apj_file IS '문서 첨부 및 일반 파일 관리';


--
-- TOC entry 4438 (class 0 OID 0)
-- Dependencies: 229
-- Name: COLUMN apj_file.file_id; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_file.file_id IS '파일 고유 ID';


--
-- TOC entry 4439 (class 0 OID 0)
-- Dependencies: 229
-- Name: COLUMN apj_file.workspace_id; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_file.workspace_id IS '소속 워크스페이스';


--
-- TOC entry 4440 (class 0 OID 0)
-- Dependencies: 229
-- Name: COLUMN apj_file.folder_id; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_file.folder_id IS '소속 폴더';


--
-- TOC entry 4441 (class 0 OID 0)
-- Dependencies: 229
-- Name: COLUMN apj_file.log_id; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_file.log_id IS '연관된 로그 ID';


--
-- TOC entry 4442 (class 0 OID 0)
-- Dependencies: 229
-- Name: COLUMN apj_file.file_name; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_file.file_name IS '원본파일명';


--
-- TOC entry 4443 (class 0 OID 0)
-- Dependencies: 229
-- Name: COLUMN apj_file.file_url; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_file.file_url IS '저장소 경로/URL';


--
-- TOC entry 4444 (class 0 OID 0)
-- Dependencies: 229
-- Name: COLUMN apj_file.file_size; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_file.file_size IS '용량(byte)';


--
-- TOC entry 4445 (class 0 OID 0)
-- Dependencies: 229
-- Name: COLUMN apj_file.file_ext; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_file.file_ext IS '확장자';


--
-- TOC entry 224 (class 1259 OID 33148)
-- Name: apj_folder; Type: TABLE; Schema: public; Owner: kth
--

CREATE TABLE public.apj_folder (
    folder_id character varying(100) NOT NULL,
    workspace_id character varying(100) NOT NULL,
    parent_id character varying(100),
    folder_name character varying(100) NOT NULL,
    ordinal integer DEFAULT 0,
    usable integer DEFAULT 1,
    create_user character varying(100),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_user character varying(100),
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE public.apj_folder OWNER TO "user";

--
-- TOC entry 4446 (class 0 OID 0)
-- Dependencies: 224
-- Name: TABLE apj_folder; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON TABLE public.apj_folder IS '워크스페이스 내 폴더 구조';


--
-- TOC entry 4447 (class 0 OID 0)
-- Dependencies: 224
-- Name: COLUMN apj_folder.parent_id; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_folder.parent_id IS '상위폴더id(null이면 최상위)';


--
-- TOC entry 230 (class 1259 OID 41027)
-- Name: apj_role; Type: TABLE; Schema: public; Owner: kth
--

CREATE TABLE public.apj_role (
    role_id character varying(10) NOT NULL,
    role_status character varying(30),
    role_desc character varying(30),
    ordinal integer DEFAULT 0,
    usable integer DEFAULT 1,
    create_user character varying(30),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    create_ip character varying(30),
    update_user character varying(30),
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_ip character varying(30)
);


ALTER TABLE public.apj_role OWNER TO "user";

--
-- TOC entry 218 (class 1259 OID 33094)
-- Name: apj_role_user; Type: TABLE; Schema: public; Owner: kth
--

CREATE TABLE public.apj_role_user (
    user_id character varying(100) NOT NULL,
    role_id character varying(10) NOT NULL,
    ordinal integer DEFAULT 0,
    usable integer DEFAULT 1,
    create_user character varying(30),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    create_ip character varying(30),
    update_user character varying(30),
    update_time timestamp without time zone,
    update_ip character varying(30)
);


ALTER TABLE public.apj_role_user OWNER TO "user";

--
-- TOC entry 4448 (class 0 OID 0)
-- Dependencies: 218
-- Name: TABLE apj_role_user; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON TABLE public.apj_role_user IS '유저별 시스템 권한 매핑';


--
-- TOC entry 4449 (class 0 OID 0)
-- Dependencies: 218
-- Name: COLUMN apj_role_user.user_id; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_role_user.user_id IS '유저id';


--
-- TOC entry 4450 (class 0 OID 0)
-- Dependencies: 218
-- Name: COLUMN apj_role_user.role_id; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_role_user.role_id IS '권한id';


--
-- TOC entry 217 (class 1259 OID 33084)
-- Name: apj_user; Type: TABLE; Schema: public; Owner: kth
--

CREATE TABLE public.apj_user (
    user_id character varying(100) NOT NULL,
    user_name character varying(50),
    password character varying(255),
    user_status character varying(10),
    email character varying(100) NOT NULL,
    last_login_time timestamp without time zone,
    ordinal integer DEFAULT 0,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    create_ip character varying(30),
    update_user character varying(30),
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_ip character varying(30)
);


ALTER TABLE public.apj_user OWNER TO "user";

--
-- TOC entry 4451 (class 0 OID 0)
-- Dependencies: 217
-- Name: TABLE apj_user; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON TABLE public.apj_user IS '사용자 정보';


--
-- TOC entry 4452 (class 0 OID 0)
-- Dependencies: 217
-- Name: COLUMN apj_user.user_id; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_user.user_id IS '유저id';


--
-- TOC entry 4453 (class 0 OID 0)
-- Dependencies: 217
-- Name: COLUMN apj_user.user_name; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_user.user_name IS '유저이름';


--
-- TOC entry 4454 (class 0 OID 0)
-- Dependencies: 217
-- Name: COLUMN apj_user.password; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_user.password IS '비밀번호(암호화)';


--
-- TOC entry 4455 (class 0 OID 0)
-- Dependencies: 217
-- Name: COLUMN apj_user.user_status; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_user.user_status IS '유저상태';


--
-- TOC entry 4456 (class 0 OID 0)
-- Dependencies: 217
-- Name: COLUMN apj_user.email; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_user.email IS '이메일';


--
-- TOC entry 4457 (class 0 OID 0)
-- Dependencies: 217
-- Name: COLUMN apj_user.last_login_time; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_user.last_login_time IS '마지막접속시간';


--
-- TOC entry 4458 (class 0 OID 0)
-- Dependencies: 217
-- Name: COLUMN apj_user.ordinal; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_user.ordinal IS '정렬';


--
-- TOC entry 4459 (class 0 OID 0)
-- Dependencies: 217
-- Name: COLUMN apj_user.create_time; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_user.create_time IS '생성시각';


--
-- TOC entry 4460 (class 0 OID 0)
-- Dependencies: 217
-- Name: COLUMN apj_user.create_ip; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_user.create_ip IS '생성ip';


--
-- TOC entry 4461 (class 0 OID 0)
-- Dependencies: 217
-- Name: COLUMN apj_user.update_user; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_user.update_user IS '수정유저';


--
-- TOC entry 4462 (class 0 OID 0)
-- Dependencies: 217
-- Name: COLUMN apj_user.update_time; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_user.update_time IS '수정시간';


--
-- TOC entry 4463 (class 0 OID 0)
-- Dependencies: 217
-- Name: COLUMN apj_user.update_ip; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_user.update_ip IS '수정ip';


--
-- TOC entry 228 (class 1259 OID 33186)
-- Name: apj_user_password_change; Type: TABLE; Schema: public; Owner: kth
--

CREATE TABLE public.apj_user_password_change (
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    user_id character varying(100) NOT NULL,
    bf_password character varying(255),
    af_password character varying(255)
);


ALTER TABLE public.apj_user_password_change OWNER TO "user";

--
-- TOC entry 4464 (class 0 OID 0)
-- Dependencies: 228
-- Name: TABLE apj_user_password_change; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON TABLE public.apj_user_password_change IS '비밀번호 변경 이력';


--
-- TOC entry 227 (class 1259 OID 33179)
-- Name: apj_user_password_fail; Type: TABLE; Schema: public; Owner: kth
--

CREATE TABLE public.apj_user_password_fail (
    user_id character varying(100) NOT NULL,
    user_fail_seq integer DEFAULT 0,
    create_user character varying(30),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    create_ip character varying(30)
);


ALTER TABLE public.apj_user_password_fail OWNER TO "user";

--
-- TOC entry 4465 (class 0 OID 0)
-- Dependencies: 227
-- Name: TABLE apj_user_password_fail; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON TABLE public.apj_user_password_fail IS '비밀번호 입력 실패 횟수 관리';


--
-- TOC entry 221 (class 1259 OID 33122)
-- Name: apj_workspace; Type: TABLE; Schema: public; Owner: kth
--

CREATE TABLE public.apj_workspace (
    workspace_id character varying(100) NOT NULL,
    workspace_name character varying(50),
    workspace_status character varying(10),
    owner_user_id character varying(100),
    ordinal integer DEFAULT 0,
    usable integer DEFAULT 1,
    create_user character varying(30),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    create_ip character varying(30),
    update_user character varying(30),
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_ip character varying(30)
);


ALTER TABLE public.apj_workspace OWNER TO "user";

--
-- TOC entry 4466 (class 0 OID 0)
-- Dependencies: 221
-- Name: TABLE apj_workspace; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON TABLE public.apj_workspace IS '워크스페이스(드라이브) 정보';


--
-- TOC entry 4467 (class 0 OID 0)
-- Dependencies: 221
-- Name: COLUMN apj_workspace.workspace_id; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_workspace.workspace_id IS '워크스페이스id';


--
-- TOC entry 4468 (class 0 OID 0)
-- Dependencies: 221
-- Name: COLUMN apj_workspace.owner_user_id; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON COLUMN public.apj_workspace.owner_user_id IS '워크스페이스대표';


--
-- TOC entry 222 (class 1259 OID 33131)
-- Name: apj_workspace_role; Type: TABLE; Schema: public; Owner: kth
--

CREATE TABLE public.apj_workspace_role (
    role_id character varying(10) NOT NULL,
    role_status character varying(30),
    role_desc character varying(30),
    ordinal integer DEFAULT 0,
    usable integer DEFAULT 1,
    create_user character varying(30),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_user character varying(30),
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE public.apj_workspace_role OWNER TO "user";

--
-- TOC entry 4469 (class 0 OID 0)
-- Dependencies: 222
-- Name: TABLE apj_workspace_role; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON TABLE public.apj_workspace_role IS '워크스페이스 내부 전용 권한 정의';


--
-- TOC entry 223 (class 1259 OID 33140)
-- Name: apj_workspace_user; Type: TABLE; Schema: public; Owner: kth
--

CREATE TABLE public.apj_workspace_user (
    user_id character varying(100) NOT NULL,
    role_id character varying(10) NOT NULL,
    workspace_id character varying(100) NOT NULL,
    ordinal integer DEFAULT 0,
    usable integer DEFAULT 1,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE public.apj_workspace_user OWNER TO "user";

--
-- TOC entry 4470 (class 0 OID 0)
-- Dependencies: 223
-- Name: TABLE apj_workspace_user; Type: COMMENT; Schema: public; Owner: kth
--

COMMENT ON TABLE public.apj_workspace_user IS '워크스페이스 소속 사용자 정보';


--
-- TOC entry 4409 (class 0 OID 33111)
-- Dependencies: 220
-- Data for Name: apj_code; Type: TABLE DATA; Schema: public; Owner: kth
--

COPY public.apj_code (code, code_group, code_desc, code_option1, code_option2, code_option3, ordinal, usable, create_user, create_time, create_ip, update_user, update_time, update_ip) FROM stdin;
ACTIVE	WS_STAT	활성 상태	\N	\N	\N	1	1	taehokwon48	2026-01-27 23:36:59.740043	127.0.0.1	\N	2026-01-27 23:36:59.740043	\N
INACTIVE	WS_STAT	비활성 상태	\N	\N	\N	2	1	taehokwon48	2026-01-27 23:36:59.740043	127.0.0.1	\N	2026-01-27 23:36:59.740043	\N
ARCHIVED	WS_STAT	보관(읽기 전용)	\N	\N	\N	3	1	taehokwon48	2026-01-27 23:36:59.740043	127.0.0.1	\N	2026-01-27 23:36:59.740043	\N
TRB	LOG_TYPE	트러블슈팅 로그	\N	\N	\N	1	1	taehokwon48	2026-01-27 23:36:59.740043	127.0.0.1	\N	2026-01-27 23:36:59.740043	\N
DEV	LOG_TYPE	개발 일지	\N	\N	\N	2	1	taehokwon48	2026-01-27 23:36:59.740043	127.0.0.1	\N	2026-01-27 23:36:59.740043	\N
REQ	LOG_TYPE	요구사항 명세	\N	\N	\N	3	1	taehokwon48	2026-01-27 23:36:59.740043	127.0.0.1	\N	2026-01-27 23:36:59.740043	\N
MEET	LOG_TYPE	회의록	\N	\N	\N	4	1	taehokwon48	2026-01-27 23:36:59.740043	127.0.0.1	\N	2026-01-27 23:36:59.740043	\N
IMG	FILE_TYPE	이미지(PNG/JPG)	\N	\N	\N	1	1	taehokwon48	2026-01-27 23:36:59.740043	127.0.0.1	\N	2026-01-27 23:36:59.740043	\N
DOC	FILE_TYPE	문서(HWP/DOCX)	\N	\N	\N	2	1	taehokwon48	2026-01-27 23:36:59.740043	127.0.0.1	\N	2026-01-27 23:36:59.740043	\N
PDF	FILE_TYPE	PDF 문서	\N	\N	\N	3	1	taehokwon48	2026-01-27 23:36:59.740043	127.0.0.1	\N	2026-01-27 23:36:59.740043	\N
SRC	FILE_TYPE	소스 코드	\N	\N	\N	4	1	taehokwon48	2026-01-27 23:36:59.740043	127.0.0.1	\N	2026-01-27 23:36:59.740043	\N
KO	LANG_CODE	한국어	\N	\N	\N	1	1	taehokwon48	2026-01-27 23:36:59.740043	127.0.0.1	\N	2026-01-27 23:36:59.740043	\N
EN	LANG_CODE	영어	\N	\N	\N	2	1	taehokwon48	2026-01-27 23:36:59.740043	127.0.0.1	\N	2026-01-27 23:36:59.740043	\N
JP	LANG_CODE	일본어	\N	\N	\N	3	1	taehokwon48	2026-01-27 23:36:59.740043	127.0.0.1	\N	2026-01-27 23:36:59.740043	\N
\.


--
-- TOC entry 4408 (class 0 OID 33102)
-- Dependencies: 219
-- Data for Name: apj_code_group; Type: TABLE DATA; Schema: public; Owner: kth
--

COPY public.apj_code_group (code_group, code_group_type, code_group_desc, ordinal, usable, create_user, create_time, create_ip, update_user, update_time, update_ip) FROM stdin;
WS_STAT	SYSTEM	워크스페이스 상태	0	1	\N	2026-01-27 22:23:58.838803	\N	\N	2026-01-27 22:23:58.838803	\N
LOG_TYPE	BUSINESS	로그 기록 유형	0	1	\N	2026-01-27 22:23:58.838803	\N	\N	2026-01-27 22:23:58.838803	\N
FILE_TYPE	SYSTEM	파일 확장자 그룹	0	1	\N	2026-01-27 22:23:58.838803	\N	\N	2026-01-27 22:23:58.838803	\N
LANG_CODE	SYSTEM	언어 설정	0	1	\N	2026-01-27 22:23:58.838803	\N	\N	2026-01-27 22:23:58.838803	\N
\.


--
-- TOC entry 4414 (class 0 OID 33159)
-- Dependencies: 225
-- Data for Name: apj_document; Type: TABLE DATA; Schema: public; Owner: kth
--

COPY public.apj_document (document_id, folder_id, workspace_id, title, old_block_json, new_block_json, content, ai_summary, ordinal, create_user, create_time, update_time) FROM stdin;
DOC_EDIT_01	FLD_T_01	WS_TEST	json저장 테스트	{\r\n   "time": 1550476186479,\r\n   "blocks": [\r\n      {\r\n         "id": "oUq2g_tl8y",\r\n         "type": "header",\r\n         "data": {\r\n            "text": "저는 집에 가고싶어요.",\r\n            "level": 2\r\n         }\r\n      },\r\n      {\r\n         "id": "zbGZFPM-iI",\r\n         "type": "paragraph",\r\n         "data": {\r\n            "text": "으아아악. 끼야아아ㅏ아악 — 어흐어으으흐ㅓㄱ후 ㅓㄱ."\r\n         }\r\n      },\r\n      {\r\n         "id": "qYIGsjS5rt",\r\n         "type": "header",\r\n         "data": {\r\n            "text": "Key features",\r\n            "level": 3\r\n         }\r\n      },\r\n      {\r\n         "id": "XV87kJS_H1",\r\n         "type": "list",\r\n         "data": {\r\n            "style": "unordered",\r\n            "items": [\r\n               "It is a block-styled editor",\r\n               "It returns clean data output in JSON",\r\n               "Designed to be extendable and pluggable with a simple API"\r\n            ]\r\n         }\r\n      },\r\n      {\r\n         "id": "AOulAjL8XM",\r\n         "type": "header",\r\n         "data": {\r\n            "text": "왓 도즈 잇 mean «block-styled editor»",\r\n            "level": 3\r\n         }\r\n      },\r\n      {\r\n         "id": "cyZjplMOZ0",\r\n         "type": "paragraph",\r\n         "data": {\r\n            "text": "워크 스페이스 in 클래식-에디따스 is made of a single contenteditable element, used to create different HTML markups. Editor.js <mark class=\\"cdx-marker\\">workspace consists of separate Blocks: paragraphs, headings, images, lists, quotes, etc</mark>. Each of them is an independent contenteditable element (or more complex structure) provided by Plugin and united by Editor's Core."\r\n         }\r\n      }\r\n   ],\r\n   "version": "2.8.1"\r\n}	{\r\n   "time": 1550476186479,\r\n   "blocks": [\r\n      {\r\n         "id": "oUq2g_tl8y",\r\n         "type": "header",\r\n         "data": {\r\n            "text": "Editor.js",\r\n            "level": 2\r\n         }\r\n      },\r\n      {\r\n         "id": "zbGZFPM-iI",\r\n         "type": "paragraph",\r\n         "data": {\r\n            "text": "Hey. Meet the new Editor. On this page you can see it in action — try to edit this text. Source code of the page contains the example of connection and configuration."\r\n         }\r\n      },\r\n      {\r\n         "id": "qYIGsjS5rt",\r\n         "type": "header",\r\n         "data": {\r\n            "text": "Key features",\r\n            "level": 3\r\n         }\r\n      },\r\n      {\r\n         "id": "XV87kJS_H1",\r\n         "type": "list",\r\n         "data": {\r\n            "style": "unordered",\r\n            "items": [\r\n               "It is a block-styled editor",\r\n               "It returns clean data output in JSON",\r\n               "Designed to be extendable and pluggable with a simple API"\r\n            ]\r\n         }\r\n      },\r\n      {\r\n         "id": "AOulAjL8XM",\r\n         "type": "header",\r\n         "data": {\r\n            "text": "What does it mean «block-styled editor»",\r\n            "level": 3\r\n         }\r\n      },\r\n      {\r\n         "id": "cyZjplMOZ0",\r\n         "type": "paragraph",\r\n         "data": {\r\n            "text": "Workspace in classic editors is made of a single contenteditable element, used to create different HTML markups. Editor.js <mark class=\\"cdx-marker\\">workspace consists of separate Blocks: paragraphs, headings, images, lists, quotes, etc</mark>. Each of them is an independent contenteditable element (or more complex structure) provided by Plugin and united by Editor's Core."\r\n         }\r\n      }\r\n   ],\r\n   "version": "2.8.1"\r\n}	Editor.js Hey. Meet the new Editor. On this page you can see it in action — try to edit this text. Source code of the page contains the example of connection and configuration. Key features\n\nIt is a block-styled editor\n\nIt returns clean data output in JSON\n\nDesigned to be extendable and pluggable with a simple API What does it mean «block-styled editor» Workspace in classic editors is made of a single contenteditable element, used to create different HTML markups. Editor.js workspace consists of separate Blocks: paragraphs, headings, images, lists, quotes, etc. Each of them is an independent contenteditable element (or more complex structure) provided by Plugin and united by Editor's Core.	\N	0	taehokwon48	2026-01-29 19:05:46.23938	2026-01-29 19:05:46.23938
\.


--
-- TOC entry 4415 (class 0 OID 33169)
-- Dependencies: 226
-- Data for Name: apj_document_history; Type: TABLE DATA; Schema: public; Owner: kth
--

COPY public.apj_document_history (document_id, folder_id, workspace_id, title, old_block_json, new_block_json, content, ai_summary, ordinal, create_user, create_time, update_time) FROM stdin;
DOC_EDIT_01	FLD_T_02	WS_TEST	json저장 테스트	{\r\n   "time": 1550476186479,\r\n   "blocks": [\r\n      {\r\n         "id": "oUq2g_tl8y",\r\n         "type": "header",\r\n         "data": {\r\n            "text": "저는 집에 가고싶어요.",\r\n            "level": 2\r\n         }\r\n      },\r\n      {\r\n         "id": "zbGZFPM-iI",\r\n         "type": "paragraph",\r\n         "data": {\r\n            "text": "으아아악. 끼야아아ㅏ아악 — 어흐어으으흐ㅓㄱ후 ㅓㄱ."\r\n         }\r\n      },\r\n      {\r\n         "id": "qYIGsjS5rt",\r\n         "type": "header",\r\n         "data": {\r\n            "text": "Key features",\r\n            "level": 3\r\n         }\r\n      },\r\n      {\r\n         "id": "XV87kJS_H1",\r\n         "type": "list",\r\n         "data": {\r\n            "style": "unordered",\r\n            "items": [\r\n               "It is a block-styled editor",\r\n               "It returns clean data output in JSON",\r\n               "Designed to be extendable and pluggable with a simple API"\r\n            ]\r\n         }\r\n      },\r\n      {\r\n         "id": "AOulAjL8XM",\r\n         "type": "header",\r\n         "data": {\r\n            "text": "왓 도즈 잇 mean «block-styled editor»",\r\n            "level": 3\r\n         }\r\n      },\r\n      {\r\n         "id": "cyZjplMOZ0",\r\n         "type": "paragraph",\r\n         "data": {\r\n            "text": "워크 스페이스 in 클래식-에디따스 is made of a single contenteditable element, used to create different HTML markups. Editor.js <mark class=\\"cdx-marker\\">workspace consists of separate Blocks: paragraphs, headings, images, lists, quotes, etc</mark>. Each of them is an independent contenteditable element (or more complex structure) provided by Plugin and united by Editor's Core."\r\n         }\r\n      }\r\n   ],\r\n   "version": "2.8.1"\r\n}	{\r\n   "time": 1550476186479,\r\n   "blocks": [\r\n      {\r\n         "id": "oUq2g_tl8y",\r\n         "type": "header",\r\n         "data": {\r\n            "text": "저는 집에 가고싶어요.",\r\n            "level": 2\r\n         }\r\n      },\r\n      {\r\n         "id": "zbGZFPM-iI",\r\n         "type": "paragraph",\r\n         "data": {\r\n            "text": "으아아악. 끼야아아ㅏ아악 — 어흐어으으흐ㅓㄱ후 ㅓㄱ."\r\n         }\r\n      },\r\n      {\r\n         "id": "qYIGsjS5rt",\r\n         "type": "header",\r\n         "data": {\r\n            "text": "Key features",\r\n            "level": 3\r\n         }\r\n      },\r\n      {\r\n         "id": "XV87kJS_H1",\r\n         "type": "list",\r\n         "data": {\r\n            "style": "unordered",\r\n            "items": [\r\n               "It is a block-styled editor",\r\n               "It returns clean data output in JSON",\r\n               "Designed to be extendable and pluggable with a simple API"\r\n            ]\r\n         }\r\n      },\r\n      {\r\n         "id": "AOulAjL8XM",\r\n         "type": "header",\r\n         "data": {\r\n            "text": "왓 도즈 잇 mean «block-styled editor»",\r\n            "level": 3\r\n         }\r\n      },\r\n      {\r\n         "id": "cyZjplMOZ0",\r\n         "type": "paragraph",\r\n         "data": {\r\n            "text": "워크 스페이스 in 클래식-에디따스 is made of a single contenteditable element, used to create different HTML markups. Editor.js <mark class=\\"cdx-marker\\">workspace consists of separate Blocks: paragraphs, headings, images, lists, quotes, etc</mark>. Each of them is an independent contenteditable element (or more complex structure) provided by Plugin and united by Editor's Core."\r\n         }\r\n      }\r\n   ],\r\n   "version": "2.8.1"\r\n}	Editor.js Hey. Meet the new Editor. On this page you can see it in action — try to edit this text. Source code of the page contains the example of connection and configuration. Key features\r\n\r\nIt is a block-styled editor\r\n\r\nIt returns clean data output in JSON\r\n\r\nDesigned to be extendable and pluggable with a simple API What does it mean «block-styled editor» Workspace in classic editors is made of a single contenteditable element, used to create different HTML markups. Editor.js workspace consists of separate Blocks: paragraphs, headings, images, lists, quotes, etc. Each of them is an independent contenteditable element (or more complex structure) provided by Plugin and united by Editor's Core.	\N	2	taehokwon48	2026-01-29 19:05:46.239	2026-01-29 19:05:46.239
DOC_EDIT_01	FLD_T_03	WS_TEST	json저장 테스트	{\r\n   "time": 1550476186479,\r\n   "blocks": [\r\n      {\r\n         "id": "oUq2g_tl8y",\r\n         "type": "header",\r\n         "data": {\r\n            "text": "저는 집에 가고싶어요.",\r\n            "level": 2\r\n         }\r\n      },\r\n      {\r\n         "id": "zbGZFPM-iI",\r\n         "type": "paragraph",\r\n         "data": {\r\n            "text": "으아아악. 끼야아아ㅏ아악 — 어흐어으으흐ㅓㄱ후 ㅓㄱ."\r\n         }\r\n      },\r\n      {\r\n         "id": "qYIGsjS5rt",\r\n         "type": "header",\r\n         "data": {\r\n            "text": "Key features",\r\n            "level": 3\r\n         }\r\n      },\r\n      {\r\n         "id": "XV87kJS_H1",\r\n         "type": "list",\r\n         "data": {\r\n            "style": "unordered",\r\n            "items": [\r\n               "It is a block-styled editor",\r\n               "It returns clean data output in JSON",\r\n               "Designed to be extendable and pluggable with a simple API"\r\n            ]\r\n         }\r\n      },\r\n      {\r\n         "id": "AOulAjL8XM",\r\n         "type": "header",\r\n         "data": {\r\n            "text": "왓 도즈 잇 mean «block-styled editor»",\r\n            "level": 3\r\n         }\r\n      },\r\n      {\r\n         "id": "cyZjplMOZ0",\r\n         "type": "paragraph",\r\n         "data": {\r\n            "text": "워크 스페이스 in 클래식-에디따스 is made of a single contenteditable element, used to create different HTML markups. Editor.js <mark class=\\"cdx-marker\\">workspace consists of separate Blocks: paragraphs, headings, images, lists, quotes, etc</mark>. Each of them is an independent contenteditable element (or more complex structure) provided by Plugin and united by Editor's Core."\r\n         }\r\n      }\r\n   ],\r\n   "version": "2.8.1"\r\n}	{\r\n   "time": 1550476186479,\r\n   "blocks": [\r\n      {\r\n         "id": "oUq2g_tl8y",\r\n         "type": "header",\r\n         "data": {\r\n            "text": "저는 집에 가고싶어요.",\r\n            "level": 2\r\n         }\r\n      },\r\n      {\r\n         "id": "zbGZFPM-iI",\r\n         "type": "paragraph",\r\n         "data": {\r\n            "text": "으아아악. 끼야아아ㅏ아악 — 어흐어으으흐ㅓㄱ후 ㅓㄱ."\r\n         }\r\n      },\r\n      {\r\n         "id": "qYIGsjS5rt",\r\n         "type": "header",\r\n         "data": {\r\n            "text": "Key features",\r\n            "level": 3\r\n         }\r\n      },\r\n      {\r\n         "id": "XV87kJS_H1",\r\n         "type": "list",\r\n         "data": {\r\n            "style": "unordered",\r\n            "items": [\r\n               "It is a block-styled editor",\r\n               "It returns clean data output in JSON",\r\n               "Designed to be extendable and pluggable with a simple API"\r\n            ]\r\n         }\r\n      },\r\n      {\r\n         "id": "AOulAjL8XM",\r\n         "type": "header",\r\n         "data": {\r\n            "text": "왓 도즈 잇 mean «block-styled editor»",\r\n            "level": 3\r\n         }\r\n      },\r\n      {\r\n         "id": "cyZjplMOZ0",\r\n         "type": "paragraph",\r\n         "data": {\r\n            "text": "워크 스페이스 in 클래식-에디따스 is made of a single contenteditable element, used to create different HTML markups. Editor.js <mark class=\\"cdx-marker\\">workspace consists of separate Blocks: paragraphs, headings, images, lists, quotes, etc</mark>. Each of them is an independent contenteditable element (or more complex structure) provided by Plugin and united by Editor's Core."\r\n         }\r\n      }\r\n   ],\r\n   "version": "2.8.1"\r\n}	Editor.js Hey. Meet the new Editor. On this page you can see it in action — try to edit this text. Source code of the page contains the example of connection and configuration. Key features\r\n\r\nIt is a block-styled editor\r\n\r\nIt returns clean data output in JSON\r\n\r\nDesigned to be extendable and pluggable with a simple API What does it mean «block-styled editor» Workspace in classic editors is made of a single contenteditable element, used to create different HTML markups. Editor.js workspace consists of separate Blocks: paragraphs, headings, images, lists, quotes, etc. Each of them is an independent contenteditable element (or more complex structure) provided by Plugin and united by Editor's Core.	\N	3	taehokwon48	2026-01-29 19:05:46.239	2026-01-29 19:05:46.239
DOC_EDIT_01	FLD_T_01	WS_TEST	json저장 테스트	{\r\n   "time": 1550476186479,\r\n   "blocks": [\r\n      {\r\n         "id": "oUq2g_tl8y",\r\n         "type": "header",\r\n         "data": {\r\n            "text": "저는 집에 가고싶어요.",\r\n            "level": 2\r\n         }\r\n      },\r\n      {\r\n         "id": "zbGZFPM-iI",\r\n         "type": "paragraph",\r\n         "data": {\r\n            "text": "으아아악. 끼야아아ㅏ아악 — 어흐어으으흐ㅓㄱ후 ㅓㄱ."\r\n         }\r\n      },\r\n      {\r\n         "id": "qYIGsjS5rt",\r\n         "type": "header",\r\n         "data": {\r\n            "text": "Key features",\r\n            "level": 3\r\n         }\r\n      },\r\n      {\r\n         "id": "XV87kJS_H1",\r\n         "type": "list",\r\n         "data": {\r\n            "style": "unordered",\r\n            "items": [\r\n               "It is a block-styled editor",\r\n               "It returns clean data output in JSON",\r\n               "Designed to be extendable and pluggable with a simple API"\r\n            ]\r\n         }\r\n      },\r\n      {\r\n         "id": "AOulAjL8XM",\r\n         "type": "header",\r\n         "data": {\r\n            "text": "왓 도즈 잇 mean «block-styled editor»",\r\n            "level": 3\r\n         }\r\n      },\r\n      {\r\n         "id": "cyZjplMOZ0",\r\n         "type": "paragraph",\r\n         "data": {\r\n            "text": "워크 스페이스 in 클래식-에디따스 is made of a single contenteditable element, used to create different HTML markups. Editor.js <mark class=\\"cdx-marker\\">workspace consists of separate Blocks: paragraphs, headings, images, lists, quotes, etc</mark>. Each of them is an independent contenteditable element (or more complex structure) provided by Plugin and united by Editor's Core."\r\n         }\r\n      }\r\n   ],\r\n   "version": "2.8.1"\r\n}	{\r\n   "time": 1550476186479,\r\n   "blocks": [\r\n      {\r\n         "id": "oUq2g_tl8y",\r\n         "type": "header",\r\n         "data": {\r\n            "text": "저는 집에 가고싶어요.",\r\n            "level": 2\r\n         }\r\n      },\r\n      {\r\n         "id": "zbGZFPM-iI",\r\n         "type": "paragraph",\r\n         "data": {\r\n            "text": "으아아악. 끼야아아ㅏ아악 — 어흐어으으흐ㅓㄱ후 ㅓㄱ."\r\n         }\r\n      },\r\n      {\r\n         "id": "qYIGsjS5rt",\r\n         "type": "header",\r\n         "data": {\r\n            "text": "Key features",\r\n            "level": 3\r\n         }\r\n      },\r\n      {\r\n         "id": "XV87kJS_H1",\r\n         "type": "list",\r\n         "data": {\r\n            "style": "unordered",\r\n            "items": [\r\n               "It is a block-styled editor",\r\n               "It returns clean data output in JSON",\r\n               "Designed to be extendable and pluggable with a simple API"\r\n            ]\r\n         }\r\n      },\r\n      {\r\n         "id": "AOulAjL8XM",\r\n         "type": "header",\r\n         "data": {\r\n            "text": "왓 도즈 잇 mean «block-styled editor»",\r\n            "level": 3\r\n         }\r\n      },\r\n      {\r\n         "id": "cyZjplMOZ0",\r\n         "type": "paragraph",\r\n         "data": {\r\n            "text": "워크 스페이스 in 클래식-에디따스 is made of a single contenteditable element, used to create different HTML markups. Editor.js <mark class=\\"cdx-marker\\">workspace consists of separate Blocks: paragraphs, headings, images, lists, quotes, etc</mark>. Each of them is an independent contenteditable element (or more complex structure) provided by Plugin and united by Editor's Core."\r\n         }\r\n      }\r\n   ],\r\n   "version": "2.8.1"\r\n}	Editor.js Hey. Meet the new Editor. On this page you can see it in action — try to edit this text. Source code of the page contains the example of connection and configuration. Key features\r\n\r\nIt is a block-styled editor\r\n\r\nIt returns clean data output in JSON\r\n\r\nDesigned to be extendable and pluggable with a simple API What does it mean «block-styled editor» Workspace in classic editors is made of a single contenteditable element, used to create different HTML markups. Editor.js workspace consists of separate Blocks: paragraphs, headings, images, lists, quotes, etc. Each of them is an independent contenteditable element (or more complex structure) provided by Plugin and united by Editor's Core.	\N	1	taehokwon48	2026-01-29 19:05:46.239	2026-01-29 19:05:46.239
\.


--
-- TOC entry 4418 (class 0 OID 40968)
-- Dependencies: 229
-- Data for Name: apj_file; Type: TABLE DATA; Schema: public; Owner: kth
--

COPY public.apj_file (file_id, workspace_id, folder_id, log_id, file_name, file_url, file_size, file_ext, create_user, create_time, update_time) FROM stdin;
FILE_001	WS_TEST	FLD_001	DOC_001	project_specification_v1.pdf	https://storage.apj-cloud.com/files/WS_001/docs/spec_v1.pdf	2540000	pdf	admin_user	2026-01-29 19:16:47.776296	2026-01-29 19:16:47.776296
FILE_002	WS_TEST	FLD_002	DOC_002	system_architecture.png	https://storage.apj-cloud.com/files/WS_001/images/arch.png	152400	png	dev_user_01	2026-01-29 19:16:47.776296	2026-01-29 19:16:47.776296
FILE_003	WS_PROJECT	\N	\N	db_backup_20260129.zip	https://storage.apj-cloud.com/files/WS_002/backups/db_20260129.zip	512000000	zip	system_manager	2026-01-29 19:16:47.776296	2026-01-29 19:16:47.776296
FILE_004	WS_PROJECT	FLD_001	DOC_003	requirements_draft.txt	https://storage.apj-cloud.com/files/WS_001/docs/req_draft.txt	4500	txt	testUser1	2026-01-29 19:16:47.776296	2026-01-29 19:16:47.776296
FILE_005	WS_AI	FLD_003	\N	api_sample_payload.json	https://storage.apj-cloud.com/files/WS_003/src/sample.json	12500	json	dev_user_02	2026-01-29 19:16:47.776296	2026-01-29 19:16:47.776296
\.


--
-- TOC entry 4413 (class 0 OID 33148)
-- Dependencies: 224
-- Data for Name: apj_folder; Type: TABLE DATA; Schema: public; Owner: kth
--

COPY public.apj_folder (folder_id, workspace_id, parent_id, folder_name, ordinal, usable, create_user, create_time, update_user, update_time) FROM stdin;
FLD_T_01	WS_TEST	\N	단위 테스트 기록	1	1	taehokwon48	2026-01-29 18:36:04.821494	\N	2026-01-29 18:36:04.821494
FLD_T_02	WS_TEST	FLD_T_01	UI 컴포넌트 테스트	1	1	taehokwon48	2026-01-29 18:36:04.821494	\N	2026-01-29 18:36:04.821494
FLD_T_03	WS_TEST	FLD_T_01	API 연동 테스트	2	1	taehokwon48	2026-01-29 18:36:04.821494	\N	2026-01-29 18:36:04.821494
FLD_P_01	WS_PROJECT	\N	메뉴 추천 앱 기획	1	1	testUser1	2026-01-29 18:36:18.55977	\N	2026-01-29 18:36:18.55977
FLD_P_02	WS_PROJECT	FLD_P_01	요구사항 정의서	1	1	testUser1	2026-01-29 18:36:18.55977	\N	2026-01-29 18:36:18.55977
FLD_P_03	WS_PROJECT	FLD_P_01	데이터베이스 설계도	2	1	testUser2	2026-01-29 18:36:18.55977	\N	2026-01-29 18:36:18.55977
FLD_P_04	WS_PROJECT	\N	와이어프레임(V0)	2	1	testUser1	2026-01-29 18:36:18.55977	\N	2026-01-29 18:36:18.55977
FLD_L_01	WS_AI	\N	AI 프롬프트 엔지니어링	1	1	testUser2	2026-01-29 18:37:25.555792	\N	2026-01-29 18:37:25.555792
FLD_L_02	WS_AI	FLD_L_01	Cursor 활용 팁	1	1	taehokwon48	2026-01-29 18:37:25.555792	\N	2026-01-29 18:37:25.555792
FLD_L_03	WS_AI	\N	SQL 튜닝 및 아키텍처	2	1	testUser2	2026-01-29 18:37:25.555792	\N	2026-01-29 18:37:25.555792
FLD_T_04	WS_TEST	FLD_T_03	API 연동 테스트 기록	3	1	taehokwon48	2026-01-29 18:36:04.821494	\N	2026-01-29 18:36:04.821494
\.


--
-- TOC entry 4419 (class 0 OID 41027)
-- Dependencies: 230
-- Data for Name: apj_role; Type: TABLE DATA; Schema: public; Owner: kth
--

COPY public.apj_role (role_id, role_status, role_desc, ordinal, usable, create_user, create_time, create_ip, update_user, update_time, update_ip) FROM stdin;
SUPER	USE	시스템 최고관리자	1	1	\N	2026-01-27 22:23:39.36896	\N	\N	2026-01-27 22:23:39.36896	\N
ADMIN	USE	운영 관리자	2	1	\N	2026-01-27 22:23:39.36896	\N	\N	2026-01-27 22:23:39.36896	\N
USER	USE	일반 사용자	3	1	\N	2026-01-27 22:23:39.36896	\N	\N	2026-01-27 22:23:39.36896	\N
GUEST	USE	임시 권한	4	1	\N	2026-01-27 22:23:39.36896	\N	\N	2026-01-27 22:23:39.36896	\N
\.


--
-- TOC entry 4407 (class 0 OID 33094)
-- Dependencies: 218
-- Data for Name: apj_role_user; Type: TABLE DATA; Schema: public; Owner: kth
--

COPY public.apj_role_user (user_id, role_id, ordinal, usable, create_user, create_time, create_ip, update_user, update_time, update_ip) FROM stdin;
testUser3	USER	4	1	taehokwon48	2026-01-27 22:32:40.310462	192.168.56.1	\N	\N	\N
testUser4	GUEST	5	1	taehokwon48	2026-01-27 22:32:40.310462	192.168.56.1	\N	\N	\N
testUser2	USER	3	1	taehokwon48	2026-01-27 22:32:40.310462	192.168.56.1	\N	\N	\N
taehokwon48	SUPER	1	1	SYSTEM	2026-01-27 22:32:40.310462	127.0.0.1	\N	\N	\N
testUser1	ADMIN	2	1	taehokwon48	2026-01-27 22:32:40.310462	192.168.56.1	\N	\N	\N
\.


--
-- TOC entry 4406 (class 0 OID 33084)
-- Dependencies: 217
-- Data for Name: apj_user; Type: TABLE DATA; Schema: public; Owner: kth
--

COPY public.apj_user (user_id, user_name, password, user_status, email, last_login_time, ordinal, create_time, create_ip, update_user, update_time, update_ip) FROM stdin;
taehokwon48	권태호	1234	ACTIVE	admin@apj.com	\N	0	2026-01-27 22:27:28.739564	192.168.56.1	\N	2026-01-27 22:27:28.739564	\N
testUser1	테스트1	1234	ACTIVE	test1@apj.com	\N	0	2026-01-27 22:27:28.739564	192.168.56.101	\N	2026-01-27 22:27:28.739564	\N
testUser2	테스트2	1234	ACTIVE	test2@apj.com	\N	0	2026-01-27 22:27:28.739564	192.168.56.102	\N	2026-01-27 22:27:28.739564	\N
testUser3	테스트3	1234	ACTIVE	test3@apj.com	\N	0	2026-01-27 22:27:28.739564	127.0.0.1	\N	2026-01-27 22:27:28.739564	\N
testUser4	테스트4	1234	INACTIVE	test4@apj.com	\N	0	2026-01-27 22:27:28.739564	211.11.22.33	\N	2026-01-27 22:27:28.739564	\N
\.


--
-- TOC entry 4417 (class 0 OID 33186)
-- Dependencies: 228
-- Data for Name: apj_user_password_change; Type: TABLE DATA; Schema: public; Owner: kth
--

COPY public.apj_user_password_change (create_time, user_id, bf_password, af_password) FROM stdin;
\.


--
-- TOC entry 4416 (class 0 OID 33179)
-- Dependencies: 227
-- Data for Name: apj_user_password_fail; Type: TABLE DATA; Schema: public; Owner: kth
--

COPY public.apj_user_password_fail (user_id, user_fail_seq, create_user, create_time, create_ip) FROM stdin;
\.


--
-- TOC entry 4410 (class 0 OID 33122)
-- Dependencies: 221
-- Data for Name: apj_workspace; Type: TABLE DATA; Schema: public; Owner: kth
--

COPY public.apj_workspace (workspace_id, workspace_name, workspace_status, owner_user_id, ordinal, usable, create_user, create_time, create_ip, update_user, update_time, update_ip) FROM stdin;
WS_TEST	테스트 워크스페이스	ACTIVE	taehokwon48	0	1	\N	2026-01-27 22:37:57.898644	\N	\N	2026-01-27 22:37:57.898644	\N
WS_PROJECT	프로젝트 기획 관련	ACTIVE	testUser1	0	1	\N	2026-01-27 22:37:57.898644	\N	\N	2026-01-27 22:37:57.898644	\N
WS_AI	AI 연구소	ACTIVE	testUser2	0	1	\N	2026-01-27 22:37:57.898644	\N	\N	2026-01-27 22:37:57.898644	\N
\.


--
-- TOC entry 4411 (class 0 OID 33131)
-- Dependencies: 222
-- Data for Name: apj_workspace_role; Type: TABLE DATA; Schema: public; Owner: kth
--

COPY public.apj_workspace_role (role_id, role_status, role_desc, ordinal, usable, create_user, create_time, update_user, update_time) FROM stdin;
WS_OWN	USE	워크스페이스 소유자	1	1	SYSTEM	2026-01-27 22:47:19.015012	SYSTEM	2026-01-27 22:47:19.015012
WS_MGR	USE	워크스페이스 관리자	2	1	SYSTEM	2026-01-27 22:47:19.015012	SYSTEM	2026-01-27 22:47:19.015012
WS_EDT	USE	콘텐츠 편집자	3	1	SYSTEM	2026-01-27 22:47:19.015012	SYSTEM	2026-01-27 22:47:19.015012
WS_RD	USE	읽기 전용 멤버	4	1	SYSTEM	2026-01-27 22:47:19.015012	SYSTEM	2026-01-27 22:47:19.015012
WS_GST	USE	임시 게스트	5	1	SYSTEM	2026-01-27 22:47:19.015012	SYSTEM	2026-01-27 22:47:19.015012
\.


--
-- TOC entry 4412 (class 0 OID 33140)
-- Dependencies: 223
-- Data for Name: apj_workspace_user; Type: TABLE DATA; Schema: public; Owner: kth
--

COPY public.apj_workspace_user (user_id, role_id, workspace_id, ordinal, usable, create_time) FROM stdin;
taehokwon48	WS_OWN	WS_TEST	1	1	2026-01-27 23:13:30.414279
taehokwon48	WS_OWN	WS_PROJECT	2	1	2026-01-27 23:13:30.414279
taehokwon48	WS_MGR	WS_AI	3	1	2026-01-27 23:13:30.414279
testUser1	WS_OWN	WS_AI	1	1	2026-01-27 23:13:41.645624
testUser1	WS_EDT	WS_TEST	2	1	2026-01-27 23:13:41.645624
testUser2	WS_EDT	WS_TEST	1	1	2026-01-27 23:13:41.645624
testUser3	WS_RD	WS_PROJECT	1	1	2026-01-27 23:13:41.645624
testUser4	WS_GST	WS_AI	1	1	2026-01-27 23:13:41.645624
\.


--
-- TOC entry 4228 (class 2606 OID 33110)
-- Name: apj_code_group apj_code_group_pkey; Type: CONSTRAINT; Schema: public; Owner: kth
--

ALTER TABLE ONLY public.apj_code_group
    ADD CONSTRAINT apj_code_group_pkey PRIMARY KEY (code_group);


--
-- TOC entry 4230 (class 2606 OID 33121)
-- Name: apj_code apj_code_pkey; Type: CONSTRAINT; Schema: public; Owner: kth
--

ALTER TABLE ONLY public.apj_code
    ADD CONSTRAINT apj_code_pkey PRIMARY KEY (code, code_group);


--
-- TOC entry 4248 (class 2606 OID 40976)
-- Name: apj_file apj_file_pkey; Type: CONSTRAINT; Schema: public; Owner: kth
--

ALTER TABLE ONLY public.apj_file
    ADD CONSTRAINT apj_file_pkey PRIMARY KEY (file_id);


--
-- TOC entry 4238 (class 2606 OID 33158)
-- Name: apj_folder apj_folder_pkey; Type: CONSTRAINT; Schema: public; Owner: kth
--

ALTER TABLE ONLY public.apj_folder
    ADD CONSTRAINT apj_folder_pkey PRIMARY KEY (folder_id, workspace_id);


--
-- TOC entry 4242 (class 2606 OID 33178)
-- Name: apj_document_history apj_log_history_pkey; Type: CONSTRAINT; Schema: public; Owner: kth
--

ALTER TABLE ONLY public.apj_document_history
    ADD CONSTRAINT apj_log_history_pkey PRIMARY KEY (document_id, folder_id, workspace_id);


--
-- TOC entry 4240 (class 2606 OID 33168)
-- Name: apj_document apj_log_pkey; Type: CONSTRAINT; Schema: public; Owner: kth
--

ALTER TABLE ONLY public.apj_document
    ADD CONSTRAINT apj_log_pkey PRIMARY KEY (document_id, folder_id, workspace_id);


--
-- TOC entry 4250 (class 2606 OID 41035)
-- Name: apj_role apj_role_pkey; Type: CONSTRAINT; Schema: public; Owner: kth
--

ALTER TABLE ONLY public.apj_role
    ADD CONSTRAINT apj_role_pkey PRIMARY KEY (role_id);


--
-- TOC entry 4226 (class 2606 OID 33101)
-- Name: apj_role_user apj_role_user_pkey; Type: CONSTRAINT; Schema: public; Owner: kth
--

ALTER TABLE ONLY public.apj_role_user
    ADD CONSTRAINT apj_role_user_pkey PRIMARY KEY (user_id, role_id);


--
-- TOC entry 4246 (class 2606 OID 33193)
-- Name: apj_user_password_change apj_user_password_change_pkey; Type: CONSTRAINT; Schema: public; Owner: kth
--

ALTER TABLE ONLY public.apj_user_password_change
    ADD CONSTRAINT apj_user_password_change_pkey PRIMARY KEY (create_time, user_id);


--
-- TOC entry 4244 (class 2606 OID 33185)
-- Name: apj_user_password_fail apj_user_password_fail_pkey; Type: CONSTRAINT; Schema: public; Owner: kth
--

ALTER TABLE ONLY public.apj_user_password_fail
    ADD CONSTRAINT apj_user_password_fail_pkey PRIMARY KEY (user_id);


--
-- TOC entry 4224 (class 2606 OID 33093)
-- Name: apj_user apj_user_pkey; Type: CONSTRAINT; Schema: public; Owner: kth
--

ALTER TABLE ONLY public.apj_user
    ADD CONSTRAINT apj_user_pkey PRIMARY KEY (user_id);


--
-- TOC entry 4232 (class 2606 OID 33130)
-- Name: apj_workspace apj_workspace_pkey; Type: CONSTRAINT; Schema: public; Owner: kth
--

ALTER TABLE ONLY public.apj_workspace
    ADD CONSTRAINT apj_workspace_pkey PRIMARY KEY (workspace_id);


--
-- TOC entry 4234 (class 2606 OID 33139)
-- Name: apj_workspace_role apj_workspace_role_pkey; Type: CONSTRAINT; Schema: public; Owner: kth
--

ALTER TABLE ONLY public.apj_workspace_role
    ADD CONSTRAINT apj_workspace_role_pkey PRIMARY KEY (role_id);


--
-- TOC entry 4236 (class 2606 OID 33147)
-- Name: apj_workspace_user apj_workspace_user_pkey; Type: CONSTRAINT; Schema: public; Owner: kth
--

ALTER TABLE ONLY public.apj_workspace_user
    ADD CONSTRAINT apj_workspace_user_pkey PRIMARY KEY (user_id, role_id, workspace_id);


--
-- TOC entry 4257 (class 2606 OID 41007)
-- Name: apj_document FK_folder_to_log; Type: FK CONSTRAINT; Schema: public; Owner: kth
--

ALTER TABLE ONLY public.apj_document
    ADD CONSTRAINT "FK_folder_to_log" FOREIGN KEY (folder_id, workspace_id) REFERENCES public.apj_folder(folder_id, workspace_id);


--
-- TOC entry 4252 (class 2606 OID 40982)
-- Name: apj_code FK_group_to_code; Type: FK CONSTRAINT; Schema: public; Owner: kth
--

ALTER TABLE ONLY public.apj_code
    ADD CONSTRAINT "FK_group_to_code" FOREIGN KEY (code_group) REFERENCES public.apj_code_group(code_group);


--
-- TOC entry 4259 (class 2606 OID 41017)
-- Name: apj_user_password_change FK_user_change; Type: FK CONSTRAINT; Schema: public; Owner: kth
--

ALTER TABLE ONLY public.apj_user_password_change
    ADD CONSTRAINT "FK_user_change" FOREIGN KEY (user_id) REFERENCES public.apj_user(user_id);


--
-- TOC entry 4258 (class 2606 OID 41012)
-- Name: apj_user_password_fail FK_user_fail; Type: FK CONSTRAINT; Schema: public; Owner: kth
--

ALTER TABLE ONLY public.apj_user_password_fail
    ADD CONSTRAINT "FK_user_fail" FOREIGN KEY (user_id) REFERENCES public.apj_user(user_id);


--
-- TOC entry 4251 (class 2606 OID 40977)
-- Name: apj_role_user FK_user_to_role; Type: FK CONSTRAINT; Schema: public; Owner: kth
--

ALTER TABLE ONLY public.apj_role_user
    ADD CONSTRAINT "FK_user_to_role" FOREIGN KEY (user_id) REFERENCES public.apj_user(user_id);


--
-- TOC entry 4253 (class 2606 OID 40997)
-- Name: apj_workspace_user FK_ws_main; Type: FK CONSTRAINT; Schema: public; Owner: kth
--

ALTER TABLE ONLY public.apj_workspace_user
    ADD CONSTRAINT "FK_ws_main" FOREIGN KEY (workspace_id) REFERENCES public.apj_workspace(workspace_id);


--
-- TOC entry 4254 (class 2606 OID 40992)
-- Name: apj_workspace_user FK_ws_role; Type: FK CONSTRAINT; Schema: public; Owner: kth
--

ALTER TABLE ONLY public.apj_workspace_user
    ADD CONSTRAINT "FK_ws_role" FOREIGN KEY (role_id) REFERENCES public.apj_workspace_role(role_id);


--
-- TOC entry 4256 (class 2606 OID 41002)
-- Name: apj_folder FK_ws_to_folder; Type: FK CONSTRAINT; Schema: public; Owner: kth
--

ALTER TABLE ONLY public.apj_folder
    ADD CONSTRAINT "FK_ws_to_folder" FOREIGN KEY (workspace_id) REFERENCES public.apj_workspace(workspace_id);


--
-- TOC entry 4255 (class 2606 OID 40987)
-- Name: apj_workspace_user FK_ws_user; Type: FK CONSTRAINT; Schema: public; Owner: kth
--

ALTER TABLE ONLY public.apj_workspace_user
    ADD CONSTRAINT "FK_ws_user" FOREIGN KEY (user_id) REFERENCES public.apj_user(user_id);


--
-- TOC entry 4260 (class 2606 OID 41022)
-- Name: apj_file apj_file_workspace_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: kth
--

ALTER TABLE ONLY public.apj_file
    ADD CONSTRAINT apj_file_workspace_id_fkey FOREIGN KEY (workspace_id) REFERENCES public.apj_workspace(workspace_id);


-- Completed on 2026-01-29 19:18:52

--
-- PostgreSQL database dump complete
--

