package com.fixlog.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 메시지 전송 요청")
public record AIMessageCreateRequest(
        @Schema(description = "사용자가 전송할 메시지", example = "JWT 검증에서 401이 발생하는 원인을 알려줘.",
                minLength = 1, maxLength = 20_000)
        @NotBlank(message = "메시지를 입력해 주세요.")
        @Size(max = 20_000, message = "메시지는 20,000자 이하여야 합니다.")
        String content
) {
}
