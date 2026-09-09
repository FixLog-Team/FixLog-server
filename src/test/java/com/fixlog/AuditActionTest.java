package com.fixlog;

import com.fixlog.domain.model.AuditAction;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 권한 변경 여부는 관리 화면에서 열람 기록과 권한 변경 기록을 가르는 데 쓰인다.
 * 선언 순서에 기대면 상수를 재배치할 때 아무 신호 없이 분류가 뒤집히므로 값별로 고정한다.
 */
class AuditActionTest {

    private static final Set<AuditAction> ACCESS = EnumSet.of(
            AuditAction.VIEW, AuditAction.DOWNLOAD, AuditAction.EDIT,
            AuditAction.DELETE, AuditAction.SHARE, AuditAction.RESTORE);

    @Test
    void 접근_행위는_권한_변경이_아니다() {
        ACCESS.forEach(action ->
                assertFalse(action.isPermissionChange(), action + "는 접근 기록이다"));
    }

    @Test
    void 나머지는_전부_권한_변경이다() {
        EnumSet.complementOf(EnumSet.copyOf(ACCESS)).forEach(action ->
                assertTrue(action.isPermissionChange(), action + "는 권한 변경 기록이다"));
    }

    // 새 값을 추가하고 성격을 정하지 않으면 여기서 걸린다.
    @Test
    void 모든_값이_두_분류_중_하나에_속한다() {
        long changes = EnumSet.allOf(AuditAction.class).stream()
                .filter(AuditAction::isPermissionChange).count();

        assertEquals(AuditAction.values().length - ACCESS.size(), changes);
    }
}
