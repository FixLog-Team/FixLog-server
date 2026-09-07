package com.fixlog;

import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.application.repository.PermissionOverrideRepository;
import com.fixlog.application.repository.UserRepository;
import com.fixlog.application.repository.WorkspaceMemberRepository;
import com.fixlog.application.repository.WorkspaceRepository;
import com.fixlog.application.service.PermissionResolver;
import com.fixlog.application.service.WorkspaceService;
import com.fixlog.domain.model.AccessEffect;
import com.fixlog.domain.model.BaseAccess;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.FolderEntity;
import com.fixlog.domain.model.NodeType;
import com.fixlog.domain.model.PermissionOverrideEntity;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.domain.model.WorkspaceEntity;
import com.fixlog.domain.model.WorkspaceMemberEntity;
import com.fixlog.domain.model.WorkspaceRole;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/**
 * 권한 판정은 워크스페이스 루트가 있어야 성립한다.
 * 테스트마다 사용자 · 워크스페이스 · 멤버십을 손으로 만드는 준비를 반복하지 않기 위한 헬퍼다.
 *
 * <p>워크스페이스 생성은 운영 코드({@link WorkspaceService#ensurePersonalWorkspace})를 그대로 쓴다.
 * 테스트가 별도의 준비 로직을 갖게 되면 실제 가입 흐름과 어긋날 수 있기 때문이다.
 */
class PermissionFixture {

    final PermissionResolver permissionResolver;
    final WorkspaceService workspaceService;

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final PermissionOverrideRepository overrideRepository;
    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;

    PermissionFixture(UserRepository userRepository,
                      WorkspaceRepository workspaceRepository,
                      WorkspaceMemberRepository memberRepository,
                      PermissionOverrideRepository overrideRepository,
                      FolderRepository folderRepository,
                      DocumentRepository documentRepository) {
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.overrideRepository = overrideRepository;
        this.folderRepository = folderRepository;
        this.documentRepository = documentRepository;
        this.permissionResolver = new PermissionResolver(
                workspaceRepository, memberRepository, overrideRepository,
                folderRepository, documentRepository);
        this.workspaceService = new WorkspaceService(
                workspaceRepository, memberRepository, userRepository, permissionResolver);
    }

    /** 사용자를 만들고 개인 워크스페이스까지 준비한 뒤 로그인 상태로 만든다. */
    UserEntity loginAsNewUser(String id) {
        UserEntity user = userRepository.save(new UserEntity(id, id + "@fixlog.dev"));
        workspaceService.ensurePersonalWorkspace(user);
        login(user);
        return user;
    }

    /** 기존 워크스페이스에 다른 사용자를 합류시킨다. 개인 워크스페이스는 만들지 않는다. */
    UserEntity addMember(String workspaceId, String id, WorkspaceRole role) {
        UserEntity user = userRepository.save(new UserEntity(id, id + "@fixlog.dev"));
        memberRepository.save(new WorkspaceMemberEntity(workspaceId, user.getUserId(), role, "test"));
        return user;
    }

    String personalWorkspaceId(UserEntity user) {
        return permissionResolver.personalWorkspaceId(user.getUserId().toString());
    }

    WorkspaceEntity workspace(String workspaceId) {
        return workspaceRepository.findByWorkspaceIdAndUsable(workspaceId, 1).orElseThrow();
    }

    void setWorkspaceBaseAccess(String workspaceId, BaseAccess baseAccess) {
        WorkspaceEntity workspace = workspace(workspaceId);
        workspace.changeBaseAccess(baseAccess, "test");
        workspaceRepository.save(workspace);
    }

    void setFolderBaseAccess(String folderId, BaseAccess baseAccess) {
        FolderEntity folder = folderRepository.findByFolderIdAndUsable(folderId, 1).orElseThrow();
        folder.changeBaseAccess(baseAccess, "test");
        folderRepository.save(folder);
    }

    void setDocumentBaseAccess(String documentId, BaseAccess baseAccess) {
        DocumentEntity document = documentRepository.findByDocumentIdAndUsable(documentId, 1).orElseThrow();
        document.changeBaseAccess(baseAccess, "test");
        documentRepository.save(document);
    }

    void putOverride(String workspaceId, NodeType nodeType, String nodeId,
                     UserEntity user, AccessEffect effect) {
        overrideRepository.save(new PermissionOverrideEntity(
                nodeId, user.getUserId(), nodeType, workspaceId, effect, "test"));
    }

    static void login(UserEntity user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
}
