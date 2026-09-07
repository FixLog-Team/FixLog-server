package com.fixlog.application.service;

import com.fixlog.domain.model.AccessDecision;
import com.fixlog.domain.model.AccessEffect;
import com.fixlog.domain.model.AccessSource;
import com.fixlog.domain.model.BaseAccess;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.FolderEntity;
import com.fixlog.domain.model.NodeType;
import com.fixlog.domain.model.WorkspaceRole;

import java.util.Map;
import java.util.UUID;

/**
 * 한 사용자 · 한 워크스페이스에 대한 권한 판정 스냅샷.
 *
 * <p>워크스페이스의 폴더 전량과 해당 사용자의 개별 설정 전량을 미리 읽어두고,
 * 부모 체인 순회를 전부 메모리에서 수행한다. 노드 하나를 판정할 때마다 깊이만큼 쿼리가
 * 나가는 것을 막기 위한 구조다.
 *
 * <p><b>권한 판정 알고리즘의 구현은 이 클래스의 {@link #walk} 하나뿐이다.</b>
 * 단건 판정({@code PermissionResolver.resolve})도 이 클래스를 경유하므로,
 * 관리 화면에 표시되는 권한과 실제 동작이 어긋날 수 없다.
 */
public class PermissionSnapshot {

    /** 부모 체인이 순환하는 경우를 대비한 안전장치. */
    private static final int MAX_DEPTH = 10_000;

    private final String workspaceId;
    private final UUID userId;
    /** null이면 이 워크스페이스의 멤버가 아니다. */
    private final WorkspaceRole role;
    private final BaseAccess workspaceBaseAccess;
    private final Map<String, FolderEntity> foldersById;
    private final Map<String, AccessEffect> overridesByNodeId;

    PermissionSnapshot(String workspaceId,
                       UUID userId,
                       WorkspaceRole role,
                       BaseAccess workspaceBaseAccess,
                       Map<String, FolderEntity> foldersById,
                       Map<String, AccessEffect> overridesByNodeId) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.role = role;
        this.workspaceBaseAccess = workspaceBaseAccess;
        this.foldersById = foldersById;
        this.overridesByNodeId = overridesByNodeId;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public UUID userId() {
        return userId;
    }

    public WorkspaceRole role() {
        return role;
    }

    public boolean isMember() {
        return role != null;
    }

    public boolean canManagePermission() {
        return role != null && role.canManagePermission();
    }

    public AccessDecision decide(FolderEntity folder) {
        if (folder == null || !workspaceId.equals(folder.getWorkspaceId())) {
            return AccessDecision.deniedAsNonMember();
        }
        return walk(NodeRef.of(folder));
    }

    public AccessDecision decide(DocumentEntity document) {
        if (document == null || !workspaceId.equals(document.getWorkspaceId())) {
            return AccessDecision.deniedAsNonMember();
        }
        return walk(NodeRef.of(document));
    }

    public AccessDecision decideWorkspace() {
        return walk(workspaceNode());
    }

    public boolean isAllowed(FolderEntity folder) {
        return decide(folder).isAllowed();
    }

    public boolean isAllowed(DocumentEntity document) {
        return decide(document).isAllowed();
    }

    /**
     * 대상 노드에서 워크스페이스 루트 방향으로 올라가며 최종 권한과 그 출처를 함께 구한다.
     * 자식에서 먼저 매치되면 순회가 끝나므로, 부모가 Deny여도 자식만 직접 공유하는 것이 성립한다.
     */
    private AccessDecision walk(NodeRef start) {
        if (!isMember()) {
            return AccessDecision.deniedAsNonMember();
        }
        // 운영권 보유자는 스스로에게 Allow를 부여할 수 있으므로 Deny가 무의미하다.
        if (role.canManagePermission()) {
            return AccessDecision.allowedByAdmin();
        }

        NodeRef current = start;
        int depth = 0;
        while (current != null && depth < MAX_DEPTH) {
            boolean atTarget = depth == 0;

            AccessEffect override = overridesByNodeId.get(current.id());
            if (override != null) {
                return new AccessDecision(
                        override,
                        atTarget ? AccessSource.DIRECT : AccessSource.INHERITED,
                        current.id());
            }

            if (current.baseAccess().isTerminal()) {
                return new AccessDecision(
                        current.baseAccess().toEffect(),
                        atTarget ? AccessSource.DIRECT : AccessSource.DEFAULT,
                        current.id());
            }

            current = parentOf(current);
            depth++;
        }

        // 루트에 닿지 못했다 = 부모 체인이 끊어졌다(workspace_id 미채움, 조상 폴더 삭제 등).
        // 권한 판정에서 불확실은 누출이므로 닫는 방향으로 확정한다.
        return new AccessDecision(AccessEffect.DENY, AccessSource.DEFAULT, null);
    }

    private NodeRef parentOf(NodeRef node) {
        if (node.type() == NodeType.WORKSPACE) {
            return null;
        }
        if (node.parentFolderId() == null) {
            return workspaceNode();
        }
        FolderEntity parent = foldersById.get(node.parentFolderId());
        return parent == null ? null : NodeRef.of(parent);
    }

    private NodeRef workspaceNode() {
        return new NodeRef(NodeType.WORKSPACE, workspaceId, workspaceBaseAccess, null);
    }

    /**
     * 순회에 필요한 최소 정보만 담은 노드 표현.
     * 폴더와 문서를 같은 루프로 다루기 위한 어댑터이며, 별도 테이블이 아니다.
     *
     * @param parentFolderId 상위 폴더. null이면 부모가 워크스페이스 루트다.
     */
    private record NodeRef(NodeType type, String id, BaseAccess baseAccess, String parentFolderId) {

        static NodeRef of(FolderEntity folder) {
            return new NodeRef(NodeType.FOLDER, folder.getFolderId(),
                    folder.getBaseAccess(), folder.getParentId());
        }

        static NodeRef of(DocumentEntity document) {
            return new NodeRef(NodeType.DOCUMENT, document.getDocumentId(),
                    document.getBaseAccess(), document.getFolderId());
        }
    }
}
