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
    VIEW(false),
    DOWNLOAD(false),
    EDIT(false),
    DELETE(false),
    SHARE(false),
    RESTORE(false),

    // ---------- 권한 변경 ----------
    /** 폴더·문서 권한 부여 또는 수정. 수정이면 detail에 변경 전후가 남는다. */
    PERMISSION_GRANT(true),
    /** 폴더·문서 권한 회수. */
    PERMISSION_REVOKE(true),
    /** 워크스페이스 역할 변경 (MEMBER ↔ ADMIN ↔ OWNER). */
    ROLE_CHANGE(true),
    /** 워크스페이스 초대. */
    MEMBER_INVITE(true),
    /** 워크스페이스에서 내보내기 또는 스스로 나가기. */
    MEMBER_REMOVE(true),
    /** 그룹 구성원 변경. 그룹에 걸린 권한이 그대로 따라가므로 권한 변경으로 본다. */
    GROUP_MEMBER_CHANGE(true),
    /** 상속·기본 접근 정책 변경 (폴더 inherit·base_access, 워크스페이스 base_access). */
    ACCESS_POLICY_CHANGE(true);

    /**
     * 값마다 성격을 직접 들고 있다. 선언 순서로 판단하면 상수를 재배치하거나
     * 접근 계열 값을 뒤에 추가하는 순간 컴파일 에러도 테스트 실패도 없이 분류가 뒤집힌다.
     * 이 값은 관리 화면에서 열람 기록과 권한 변경 기록을 가르는 데 쓰인다.
     */
    private final boolean permissionChange;

    AuditAction(boolean permissionChange) {
        this.permissionChange = permissionChange;
    }

    /** 접근 기록이 아니라 권한을 바꾼 기록인지. */
    public boolean isPermissionChange() {
        return permissionChange;
    }
}
