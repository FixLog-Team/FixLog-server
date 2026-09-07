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
        // page * size를 int로 계산하면 큰 page 값에서 넘쳐 음수가 되고 subList가 터진다.
        // 요청 검증과 별개로, 잘라내는 쪽에서도 범위를 벗어나지 않도록 막는다.
        int from = (int) Math.clamp((long) page * size, 0L, all.size());
        int to = (int) Math.clamp((long) from + size, 0L, all.size());
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) all.size() / size);
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
