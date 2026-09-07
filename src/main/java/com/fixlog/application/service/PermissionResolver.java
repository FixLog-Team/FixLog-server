package com.fixlog.application.service;

import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.application.repository.PermissionOverrideRepository;
import com.fixlog.application.repository.WorkspaceMemberRepository;
import com.fixlog.application.repository.WorkspaceRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.AccessDecision;
import com.fixlog.domain.model.AccessEffect;
import com.fixlog.domain.model.BaseAccess;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.FolderEntity;
import com.fixlog.domain.model.NodeType;
import com.fixlog.domain.model.PermissionOverrideEntity;
import com.fixlog.domain.model.WorkspaceEntity;
import com.fixlog.domain.model.WorkspaceMemberEntity;
import com.fixlog.domain.model.WorkspaceRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 권한 판정의 유일한 진입점.
 *
 * <p>관리 화면, 트리 조회, 문서 접근, 검색이 모두 이 클래스를 통과해야 한다.
 * 특정 화면에만 권한 조건을 따로 작성하면 "화면에는 안 보이는데 검색에는 뜨는" 종류의
 * 버그가 생긴다.
 *
 * <p>판정 알고리즘 자체는 {@link PermissionSnapshot}에 한 번만 구현되어 있고,
 * 이 클래스는 판정에 필요한 데이터를 모아 스냅샷을 만드는 역할을 한다.
 */
@Service
public class PermissionResolver {

    private static final Logger log = LoggerFactory.getLogger(PermissionResolver.class);
    private static final Integer ACTIVE = 1;

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final PermissionOverrideRepository overrideRepository;
    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;

    public PermissionResolver(WorkspaceRepository workspaceRepository,
                              WorkspaceMemberRepository memberRepository,
                              PermissionOverrideRepository overrideRepository,
                              FolderRepository folderRepository,
                              DocumentRepository documentRepository) {
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.overrideRepository = overrideRepository;
        this.folderRepository = folderRepository;
        this.documentRepository = documentRepository;
    }

    /**
     * 노드 하나에 대한 판정. 노드가 속한 워크스페이스는 노드에서 역으로 유도하므로
     * 호출자가 워크스페이스를 넘기지 않아도 된다.
     */
    @Transactional(readOnly = true)
    public AccessDecision resolve(String userId, NodeType nodeType, String nodeId) {
        return switch (nodeType) {
            case DOCUMENT -> documentRepository.findByDocumentIdAndUsable(nodeId, ACTIVE)
                    .map(doc -> snapshot(doc.getWorkspaceId(), userId).decide(doc))
                    .orElseGet(AccessDecision::deniedAsNonMember);
            case FOLDER -> folderRepository.findByFolderIdAndUsable(nodeId, ACTIVE)
                    .map(folder -> snapshot(folder.getWorkspaceId(), userId).decide(folder))
                    .orElseGet(AccessDecision::deniedAsNonMember);
            case WORKSPACE -> snapshot(nodeId, userId).decideWorkspace();
        };
    }

    /**
     * 목록 · 트리 · 검색처럼 여러 노드를 연속 판정할 때 쓰는 스냅샷.
     * 폴더 전량과 사용자 개별 설정을 각각 한 번씩만 조회한다.
     */
    @Transactional(readOnly = true)
    public PermissionSnapshot snapshot(String workspaceId, String userId) {
        UUID user = toUuid(userId);

        if (workspaceId == null) {
            // 마이그레이션 전 행(workspace_id 미채움)은 소속을 판단할 수 없다.
            return nonMemberSnapshot(null, user);
        }

        WorkspaceEntity workspace = workspaceRepository
                .findByWorkspaceIdAndUsable(workspaceId, ACTIVE)
                .orElse(null);
        if (workspace == null) {
            return nonMemberSnapshot(workspaceId, user);
        }

        WorkspaceRole role = memberRepository.findByWorkspaceIdAndUserId(workspaceId, user)
                .map(WorkspaceMemberEntity::getRole)
                .orElse(null);
        if (role == null) {
            return nonMemberSnapshot(workspaceId, user);
        }

        // 운영권 보유자는 판정이 ALLOW로 단축되므로 폴더 · 개별 설정을 읽지 않는다.
        if (role.canManagePermission()) {
            // 결정서 §1.2 — Admin에게는 Deny를 두지 않는 대신 사후 추적으로 통제한다.
            // 정식 Audit Log(개발 순서 5단계) 전까지의 최소 기록이다.
            log.info("운영권 권한 단축 판정: userId={}, workspaceId={}, role={}", user, workspaceId, role);
            return new PermissionSnapshot(workspaceId, user, role,
                    workspace.getBaseAccess(), Map.of(), Map.of());
        }

        return new PermissionSnapshot(workspaceId, user, role, workspace.getBaseAccess(),
                loadFolders(workspaceId), loadOverrides(workspaceId, user));
    }

    /**
     * 호출자가 지금 어느 워크스페이스에서 작업 중인지 결정한다.
     * 생략되면 가입 시 만들어진 개인 워크스페이스를 쓴다.
     */
    @Transactional(readOnly = true)
    public String resolveWorkspaceId(String userId, String requestedWorkspaceId) {
        if (requestedWorkspaceId == null || requestedWorkspaceId.isBlank()) {
            return personalWorkspaceId(userId);
        }
        boolean member = memberRepository
                .findByWorkspaceIdAndUserId(requestedWorkspaceId, toUuid(userId))
                .isPresent();
        if (!member) {
            // 소속되지 않은 워크스페이스는 존재 자체를 알리지 않는다.
            throw new BusinessException(Code.NOT_FOUND, "워크스페이스를 찾을 수 없습니다.");
        }
        return requestedWorkspaceId;
    }

    @Transactional(readOnly = true)
    public String personalWorkspaceId(String userId) {
        return memberRepository
                .findFirstByUserIdAndRoleOrderByCreateTimeAsc(toUuid(userId), WorkspaceRole.OWNER)
                .map(WorkspaceMemberEntity::getWorkspaceId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND,
                        "개인 워크스페이스를 찾을 수 없습니다."));
    }

    /** 콘텐츠 권한을 변경 · 조회할 수 있는지. 아니면 403. */
    @Transactional(readOnly = true)
    public WorkspaceRole requireManageRole(String userId, String workspaceId) {
        WorkspaceRole role = memberRepository.findByWorkspaceIdAndUserId(workspaceId, toUuid(userId))
                .map(WorkspaceMemberEntity::getRole)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "워크스페이스를 찾을 수 없습니다."));
        if (!role.canManagePermission()) {
            throw new BusinessException(Code.FORBIDDEN, "권한을 관리할 수 있는 역할이 아닙니다.");
        }
        return role;
    }

    private PermissionSnapshot nonMemberSnapshot(String workspaceId, UUID user) {
        return new PermissionSnapshot(workspaceId, user, null, BaseAccess.DENY, Map.of(), Map.of());
    }

    private Map<String, FolderEntity> loadFolders(String workspaceId) {
        return folderRepository.findByWorkspaceIdAndUsable(workspaceId, ACTIVE).stream()
                .collect(Collectors.toMap(FolderEntity::getFolderId, Function.identity()));
    }

    private Map<String, AccessEffect> loadOverrides(String workspaceId, UUID user) {
        List<PermissionOverrideEntity> overrides =
                overrideRepository.findByWorkspaceIdAndUserId(workspaceId, user);
        Map<String, AccessEffect> byNodeId = new HashMap<>();
        for (PermissionOverrideEntity override : overrides) {
            byNodeId.put(override.getNodeId(), override.getEffect());
        }
        return byNodeId;
    }

    private UUID toUuid(String userId) {
        if (userId == null) {
            throw new BusinessException(Code.UNAUTHORIZED, "인증 정보가 없습니다.");
        }
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(Code.INVALID_REQUEST, "사용자 식별자 형식이 올바르지 않습니다.");
        }
    }
}
