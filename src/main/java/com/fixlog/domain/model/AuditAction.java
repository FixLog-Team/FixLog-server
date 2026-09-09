package com.fixlog.domain.model;

/**
 * 감사 로그에 남기는 행위 (FR-AUD-001).
 *
 * <p>두 종류가 섞여 있다.
 * <ul>
 *   <li><b>접근</b>({@code VIEW}~{@code RESTORE}) — 누가 무엇을 봤는지. 양이 많고 사후 조사용이다.</li>
 *   <li><b>권한 변경</b>({@code PERMISSION_GRANT} 이하) — 누가 누구에게 접근을 열어줬는지.
 *       책임 추적이라 빠지면 "이 사람이 왜 볼 수 있었나"를 되짚을 수 없다.</li>
 * </ul>
 */
public enum AuditAction {

    // ---------- 접근 ----------
    VIEW,
    DOWNLOAD,
    EDIT,
    DELETE,
    SHARE,
    RESTORE,

    // ---------- 권한 변경 ----------
    /** 폴더·문서 권한 부여 또는 수정. 수정이면 detail에 변경 전후가 남는다. */
    PERMISSION_GRANT,
    /** 폴더·문서 권한 회수. */
    PERMISSION_REVOKE,
    /** 워크스페이스 역할 변경 (MEMBER ↔ ADMIN ↔ OWNER). */
    ROLE_CHANGE,
    /** 워크스페이스 초대. */
    MEMBER_INVITE,
    /** 워크스페이스에서 내보내기 또는 스스로 나가기. */
    MEMBER_REMOVE,
    /** 그룹 구성원 변경. 그룹에 걸린 권한이 그대로 따라가므로 권한 변경으로 본다. */
    GROUP_MEMBER_CHANGE,
    /** 상속·기본 접근 정책 변경 (폴더 inherit·base_access, 워크스페이스 base_access). */
    ACCESS_POLICY_CHANGE;

    /** 접근 기록이 아니라 권한을 바꾼 기록인지. 관리 화면에서 두 종류를 갈라 보여주기 위한 구분이다. */
    public boolean isPermissionChange() {
        return ordinal() >= PERMISSION_GRANT.ordinal();
    }
}
