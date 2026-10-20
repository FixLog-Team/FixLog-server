package com.fixlog.presentation.dto.request;

import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 대화방 생성 요청")
public record AIConversationCreateRequest(
        @Schema(description = "대화방 제목. 생략하거나 공백이면 '새 대화'를 사용합니다.",
                example = "Spring Security 오류 분석", maxLength = 255)
        @Size(max = 255, message = "대화방 제목은 255자 이하여야 합니다.")
        String title,

        @Schema(description = "문서 전용 대화방일 때 대상 문서 ID. 생략하면 일반 검색 대화방.",
                example = "0c64315f-241e-4ac2-a8cf-9ff02c63d18f")
        String documentId
) {
}
