package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.PermissionType;

import java.util.List;

/** Admin 콘솔의 리소스별 권한 현황. 폴더는 상속 설정을 함께 반환한다. */
public record ResourcePermissionsDto(
        List<AdminPermissionDto> permissions,
        Boolean inheritFromParent,
        PermissionType baseAccess
) {
}
