package com.fixlog.presentation.controller.ai;

import com.fixlog.application.service.AiUsageService;
import com.fixlog.application.service.UserApiKeyService;
import com.fixlog.application.service.WorkspaceService;
import com.fixlog.common.response.DataResponse;
import com.fixlog.common.response.Response;
import com.fixlog.presentation.dto.request.ApiKeyRequest;
import com.fixlog.presentation.dto.response.AiUsageSummaryDto;
import com.fixlog.presentation.dto.response.UserApiKeyDto;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AiUsageController {

    private final UserApiKeyService userApiKeyService;
    private final AiUsageService aiUsageService;
    private final WorkspaceService workspaceService;

    public AiUsageController(UserApiKeyService userApiKeyService,
                             AiUsageService aiUsageService,
                             WorkspaceService workspaceService) {
        this.userApiKeyService = userApiKeyService;
        this.aiUsageService = aiUsageService;
        this.workspaceService = workspaceService;
    }

    /** 등록된 키 목록. 원문은 절대 내려가지 않고 마지막 네 자리만 보인다 (FR-AI-003). */
    @GetMapping("/users/me/ai-keys")
    public Response myKeys() {
        return DataResponse.success(userApiKeyService.myKeys().stream().map(UserApiKeyDto::from).toList());
    }

    @PostMapping("/users/me/ai-keys")
    public Response registerKey(@RequestBody ApiKeyRequest request) {
        return DataResponse.success(
                UserApiKeyDto.from(userApiKeyService.register(request.provider(), request.apiKey())));
    }

    @DeleteMapping("/users/me/ai-keys/{keyId}")
    public Response deleteKey(@PathVariable UUID keyId) {
        userApiKeyService.delete(keyId);
        return Response.success("API Key를 삭제했습니다.");
    }

    /** 워크스페이스의 이번 달 사용량과 남은 무료 한도 (FR-AI-008). */
    @GetMapping("/workspaces/{workspaceId}/ai-usage")
    public Response usage(@PathVariable UUID workspaceId) {
        workspaceService.requireMembership(workspaceId);
        return DataResponse.success(AiUsageSummaryDto.of(
                aiUsageService.freeTokensUsedThisMonth(workspaceId),
                aiUsageService.freeMonthlyLimit(),
                aiUsageService.usageThisMonth(workspaceId)));
    }
}
