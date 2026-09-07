package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.BaseAccess;
import com.fixlog.domain.model.NodeType;

import java.util.List;

/**
 * 관리 화면(Permissions &gt; Folders &amp; Documents)의 노드 상세.
 * 기본 정책과 사용자 개별 설정을 함께 보여준다.
 */
public record NodePermissionDto(
        NodeType nodeType,
        String nodeId,
        String nodeName,
        String workspaceId,
        BaseAccess baseAccess,
        List<NodeOverrideDto> overrides
) {}
