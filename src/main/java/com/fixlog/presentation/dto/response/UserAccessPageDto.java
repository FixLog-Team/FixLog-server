package com.fixlog.presentation.dto.response;

import java.util.List;

public record UserAccessPageDto(
        List<UserAccessDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static UserAccessPageDto of(List<UserAccessDto> all, int page, int size) {
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) all.size() / size);
        return new UserAccessPageDto(
                all.subList(from, to),
                page,
                size,
                all.size(),
                totalPages,
                to < all.size()
        );
    }
}
