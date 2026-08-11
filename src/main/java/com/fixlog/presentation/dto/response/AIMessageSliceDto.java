package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.AIMessageEntity;
import org.springframework.data.domain.Slice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record AIMessageSliceDto(
        List<AIMessageDto> items,
        Integer nextBeforeSequence,
        boolean hasNext
) {
    public static AIMessageSliceDto from(Slice<AIMessageEntity> slice) {
        List<AIMessageEntity> entities = new ArrayList<>(slice.getContent());
        Collections.reverse(entities);
        List<AIMessageDto> items = entities.stream().map(AIMessageDto::from).toList();
        Integer nextBeforeSequence = slice.hasNext() && !entities.isEmpty()
                ? entities.getFirst().getMessageSequence()
                : null;
        return new AIMessageSliceDto(items, nextBeforeSequence, slice.hasNext());
    }
}
