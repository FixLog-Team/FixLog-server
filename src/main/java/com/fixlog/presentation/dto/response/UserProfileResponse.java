package com.fixlog.presentation.dto.response;

import java.util.UUID;

public record UserProfileResponse(
        UUID userId,
        String displayName,
        String email,
        String pictureUrl
) {}
