package com.fixlog;

import com.fixlog.domain.model.AccessEffect;
import com.fixlog.domain.model.AccessSource;
import com.fixlog.domain.model.BaseAccess;
import com.fixlog.domain.model.NodeType;
import com.fixlog.presentation.dto.response.UserAccessDto;
import com.fixlog.presentation.dto.response.UserAccessPageDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserAccessPageDtoTest {

    private List<UserAccessDto> rows(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new UserAccessDto(
                        NodeType.FOLDER, "folder-" + i, "폴더 " + i, null,
                        BaseAccess.INHERIT, AccessEffect.ALLOW, AccessSource.DEFAULT, "ws"))
                .toList();
    }

    @Test
    void 첫_페이지를_자르고_다음_페이지_여부를_알려준다() {
        UserAccessPageDto page = UserAccessPageDto.of(rows(5), 0, 2);

        assertEquals(2, page.items().size());
        assertEquals("folder-0", page.items().get(0).nodeId());
        assertEquals(5, page.totalElements());
        assertEquals(3, page.totalPages());
        assertTrue(page.hasNext());
    }

    @Test
    void 마지막_페이지는_남은_만큼만_담고_hasNext가_false다() {
        UserAccessPageDto page = UserAccessPageDto.of(rows(5), 2, 2);

        assertEquals(1, page.items().size());
        assertEquals("folder-4", page.items().get(0).nodeId());
        assertFalse(page.hasNext());
    }

    @Test
    void 페이지_번호가_커도_잘라내기_범위를_벗어나지_않는다() {
        // page * size를 int로 계산하면 넘쳐 음수가 되고 subList가 터진다.
        UserAccessPageDto page = UserAccessPageDto.of(rows(3), 200_000_000, 20);

        assertTrue(page.items().isEmpty());
        assertEquals(3, page.totalElements(), "총 개수는 그대로여야 한다");
        assertFalse(page.hasNext());
    }

    @Test
    void 음수_페이지는_예외_대신_첫_페이지로_처리한다() {
        // 컨트롤러의 @Min(0)이 1차 방어이고, 이 계산은 그것을 통과하지 못한 호출에 대한 2차 방어다.
        UserAccessPageDto page = UserAccessPageDto.of(rows(3), -1, 20);

        assertEquals(3, page.items().size());
        assertEquals("folder-0", page.items().get(0).nodeId());
        assertEquals(3, page.totalElements());
    }

    @Test
    void size가_0이면_페이지를_나눌_수_없으므로_hasNext도_false다() {
        UserAccessPageDto page = UserAccessPageDto.of(rows(3), 0, 0);

        assertTrue(page.items().isEmpty());
        assertEquals(0, page.totalPages());
        // totalPages가 0인데 hasNext가 true면 응답이 앞뒤가 맞지 않는다.
        assertFalse(page.hasNext());
    }
}
