package com.fixlog.presentation.dto.request;

import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 대화방 생성 요청")
public record AIConversationCreateRequest(
        @Schema(description = "대화방 제목. 생략하거나 공백이면 '새 대화'를 사용합니다.",
                example = "Spring Security 오류 분석", maxLength = 255)
        @Size(max = 255, message = "대화방 제목은 255자 이하여야 합니다.")
        String title
) {
}
