package com.fixlog.presentation.dto.response;

import com.fixlog.domain.model.AccessDecision;
import com.fixlog.domain.model.AccessEffect;
import com.fixlog.domain.model.AccessSource;
import com.fixlog.domain.model.BaseAccess;
import com.fixlog.domain.model.NodeType;

/**
 * 한 사용자의 노드별 최종 권한. {@code source}는 이 결과가 어디서 왔는지를 가리킨다.
 * "왜 이 사람이 접근하지 못하는가"를 추적하기 위한 값이다.
 */
public record UserAccessDto(
        NodeType nodeType,
        String nodeId,
        String nodeName,
        String parentId,
        BaseAccess baseAccess,
        AccessEffect effect,
        AccessSource source,
        String sourceNodeId
) {
    public static UserAccessDto of(NodeType nodeType, String nodeId, String nodeName, String parentId,
                                   BaseAccess baseAccess, AccessDecision decision) {
        return new UserAccessDto(
                nodeType,
                nodeId,
                nodeName,
                parentId,
                baseAccess,
                decision.effect(),
                decision.source(),
                decision.sourceNodeId()
        );
    }
}
