package com.fixlog.presentation.controller.ai;

import com.fixlog.application.service.AIConversationService;
import com.fixlog.common.response.DataResponse;
import com.fixlog.common.response.Response;
import com.fixlog.presentation.dto.request.AIConversationCreateRequest;
import com.fixlog.presentation.dto.response.AIConversationDto;
import com.fixlog.presentation.dto.response.AIConversationPageDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/ai/conversations")
@Tag(name = "AI-Chat", description = "사용자별 AI 대화방 생성, 조회 및 삭제 API")
@SecurityRequirement(name = "Bearer Authentication")
public class AIConversationController {

    private final AIConversationService conversationService;

    public AIConversationController(AIConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    @Operation(
            operationId = "createConversation",
            summary = "AI 대화방 생성",
            description = "로그인 사용자의 새 AI 대화방을 생성합니다. 제목을 생략하거나 공백으로 보내면 '새 대화'로 생성됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "대화방 생성 성공"),
            @ApiResponse(responseCode = "400", description = "제목 길이 등 요청값 검증 실패"),
            @ApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음")
    })
    public Response create(@Valid @RequestBody AIConversationCreateRequest request) {
        return DataResponse.success(AIConversationDto.from(conversationService.create(request)));
    }

    @GetMapping
    @Operation(
            operationId = "listConversations",
            summary = "AI 대화방 목록 조회",
            description = "로그인 사용자의 삭제되지 않은 대화방을 최근 활동 순으로 페이지 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "대화방 목록 조회 성공"),
            @ApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음")
    })
    public Response list(
            @Parameter(description = "0부터 시작하는 페이지 번호", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "한 페이지에 조회할 대화방 수", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        return DataResponse.success(
                AIConversationPageDto.from(conversationService.list(PageRequest.of(page, size)))
        );
    }

    @GetMapping("/{conversationId}")
    @Operation(
            operationId = "getConversation",
            summary = "AI 대화방 상세 조회",
            description = "대화방 기본 정보를 조회합니다. 로그인 사용자가 소유하지 않은 대화방도 404로 처리합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "대화방 조회 성공"),
            @ApiResponse(responseCode = "400", description = "대화방 ID의 UUID 형식이 잘못됨"),
            @ApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음"),
            @ApiResponse(responseCode = "404", description = "대화방이 없거나 로그인 사용자의 소유가 아님")
    })
    public Response get(
            @Parameter(description = "조회할 대화방 UUID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID conversationId) {
        return DataResponse.success(AIConversationDto.from(conversationService.get(conversationId)));
    }

    @DeleteMapping("/{conversationId}")
    @Operation(
            operationId = "deleteConversation",
            summary = "AI 대화방 삭제",
            description = "대화방을 소프트 삭제합니다. 메시지 기록은 DB에 보존되지만 이후 API 조회에서는 제외됩니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "대화방 삭제 성공"),
            @ApiResponse(responseCode = "400", description = "대화방 ID의 UUID 형식이 잘못됨"),
            @ApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음"),
            @ApiResponse(responseCode = "404", description = "대화방이 없거나 로그인 사용자의 소유가 아님")
    })
    public Response delete(
            @Parameter(description = "삭제할 대화방 UUID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID conversationId) {
        conversationService.delete(conversationId);
        return Response.success("대화방이 삭제되었습니다.");
    }
}
