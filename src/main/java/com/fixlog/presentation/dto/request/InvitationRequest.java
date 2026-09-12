package com.fixlog.presentation.dto.request;

import com.fixlog.domain.model.WorkspaceRole;

public record InvitationRequest(
        String email,
        WorkspaceRole role
) {
    public WorkspaceRole resolvedRole() {
        return role == null ? WorkspaceRole.MEMBER : role;
    }
}
