package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.LabelEntity;

import java.util.UUID;

public record LabelDto(
        UUID labelId,
        String labelName
) {
    public static LabelDto from(LabelEntity entity) {
        return new LabelDto(entity.getId(), entity.getLabelName());
    }
}
