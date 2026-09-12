package com.fixlog.presentation.controller.admin;

import com.fixlog.application.service.SecurityPolicyService;
import com.fixlog.common.response.DataResponse;
import com.fixlog.common.response.Response;
import com.fixlog.presentation.dto.request.SecurityPolicyRequest;
import com.fixlog.presentation.dto.response.SecurityPolicyDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/security-policy")
@Tag(name = "SecurityPolicy")
public class SecurityPolicyController {

    private final SecurityPolicyService securityPolicyService;

    public SecurityPolicyController(SecurityPolicyService securityPolicyService) {
        this.securityPolicyService = securityPolicyService;
    }

    /** 구성원이면 볼 수 있다. 자기에게 어떤 제약이 걸려 있는지는 알아야 한다. */
    @GetMapping
    @Operation(operationId = "getSecurityPolicy")
    public Response view(@PathVariable UUID workspaceId) {
        return DataResponse.success(SecurityPolicyDto.from(securityPolicyService.view(workspaceId)));
    }

    /** 변경은 관리자만 (FR-SEC-005). 넘기지 않은 항목은 그대로 둔다. */
    @PatchMapping
    @Operation(operationId = "updateSecurityPolicy")
    public Response update(@PathVariable UUID workspaceId,
                           @RequestBody SecurityPolicyRequest request) {
        return DataResponse.success(SecurityPolicyDto.from(securityPolicyService.update(
                workspaceId,
                request.allowSharing(),
                request.allowDownload(),
                request.enforceWatermark(),
                request.auditRetentionDays(),
                request.trashRetentionDays())));
    }
}
