package com.fixlog.presentation.dto.response;

import com.fixlog.application.service.PermissionEvaluator;
import com.fixlog.domain.model.PermissionSource;

/** 현재 로그인 유저가 특정 리소스에 대해 갖는 유효 권한 + 출처. 프론트 UI 제어에 사용된다. */
public record MyPermissionDto(
        boolean access,
        boolean canDownload,
        boolean canEdit,
        PermissionSource source,
        String sourceDetail
) {
    public static MyPermissionDto of(PermissionEvaluator.DecisionWithSource dws) {
        return new MyPermissionDto(
                dws.decision().allowed(),
                dws.decision().canDownload(),
                dws.decision().canEdit(),
                dws.source(),
                dws.sourceDetail()
        );
    }
}
