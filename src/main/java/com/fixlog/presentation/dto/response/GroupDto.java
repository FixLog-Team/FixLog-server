package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.GroupEntity;

import java.time.Instant;
import java.util.UUID;

public record GroupDto(
        UUID groupId,
        UUID workspaceId,
        String groupName,
        Instant createAt
) {
    public static GroupDto from(GroupEntity entity) {
        return new GroupDto(
                entity.getGroupId(),
                entity.getWorkspaceId(),
                entity.getGroupName(),
                entity.getCreateAt()
        );
    }
}
