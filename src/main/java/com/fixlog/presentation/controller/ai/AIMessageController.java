package com.fixlog.presentation.controller.ai;

import com.fixlog.application.service.AIChatService;
import com.fixlog.application.service.AIMessageQueryService;
import com.fixlog.common.response.DataResponse;
import com.fixlog.common.response.Response;
import com.fixlog.presentation.dto.request.AIMessageCreateRequest;
import com.fixlog.presentation.dto.response.AIChatResponse;
import com.fixlog.presentation.dto.response.AIMessageSliceDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/ai/conversations/{conversationId}/messages")
@Tag(name = "AI 메시지", description = "AI 대화방 메시지 전송 및 기록 조회 API")
@SecurityRequirement(name = "Bearer Authentication")
public class AIMessageController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AIChatService chatService;
    private final AIMessageQueryService messageQueryService;

    public AIMessageController(AIChatService chatService, AIMessageQueryService messageQueryService) {
        this.chatService = chatService;
        this.messageQueryService = messageQueryService;
    }

    @PostMapping
    @Operation(
            summary = "AI 메시지 전송",
            description = "사용자 메시지를 저장하고 최근 완료 메시지 최대 20개를 맥락으로 Gemini 답변을 생성해 함께 저장합니다. "
                    + "AI 호출 중에는 응답 메시지가 PENDING이며, 성공 시 COMPLETED, 실패 시 FAILED로 변경됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "사용자 메시지 및 AI 답변 저장 성공"),
            @ApiResponse(responseCode = "400", description = "빈 메시지 또는 20,000자 초과 등 요청값 검증 실패"),
            @ApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음"),
            @ApiResponse(responseCode = "404", description = "대화방이 없거나 로그인 사용자의 소유가 아님"),
            @ApiResponse(responseCode = "500", description = "Gemini 답변 생성 실패. AI 메시지는 FAILED 상태로 저장됨")
    })
    public Response send(
                         @Parameter(description = "메시지를 저장할 대화방 UUID",
                                 example = "550e8400-e29b-41d4-a716-446655440000")
                         @PathVariable UUID conversationId,
                         @Valid @RequestBody AIMessageCreateRequest request) {
        AIChatService.ChatResult messages = chatService.send(conversationId, request.content());
        return DataResponse.success(AIChatResponse.from(messages.userMessage(), messages.assistantMessage()));
    }

    @GetMapping
    @Operation(
            summary = "AI 메시지 기록 조회",
            description = "대화방 메시지를 순번 기반 커서 방식으로 조회합니다. 응답 items는 화면 출력에 맞게 오래된 순서부터 반환됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "메시지 기록 조회 성공"),
            @ApiResponse(responseCode = "400", description = "대화방 ID 또는 커서 요청값 형식이 잘못됨"),
            @ApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음"),
            @ApiResponse(responseCode = "404", description = "대화방이 없거나 로그인 사용자의 소유가 아님")
    })
    public Response list(
                         @Parameter(description = "메시지를 조회할 대화방 UUID",
                                 example = "550e8400-e29b-41d4-a716-446655440000")
                         @PathVariable UUID conversationId,
                         @Parameter(description = "이 순번보다 오래된 메시지를 조회하는 커서", example = "21")
                         @RequestParam(required = false) Integer beforeSequence,
                         @Parameter(description = "조회 개수. 서버에서 1~100 범위로 보정됩니다.", example = "20")
                         @RequestParam(defaultValue = "20") int size) {
        int normalizedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return DataResponse.success(AIMessageSliceDto.from(
                messageQueryService.list(conversationId, beforeSequence, normalizedSize)
        ));
    }
}
