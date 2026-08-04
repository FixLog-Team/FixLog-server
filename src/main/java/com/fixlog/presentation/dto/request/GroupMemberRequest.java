package com.fixlog.presentation.dto.request;

import java.util.UUID;

public record GroupMemberRequest(
        UUID userId
) {
}
