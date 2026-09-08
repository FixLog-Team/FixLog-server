package com.fixlog;

import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.application.repository.GroupMemberRepository;
import com.fixlog.application.repository.GroupRepository;
import com.fixlog.application.repository.PermissionRepository;
import com.fixlog.application.repository.SecurityPolicyRepository;
import com.fixlog.application.repository.UserRepository;
import com.fixlog.application.repository.WorkspaceInvitationRepository;
import com.fixlog.application.repository.WorkspaceMemberRepository;
import com.fixlog.application.repository.WorkspaceRepository;
import com.fixlog.application.service.DocumentPdfGenerator;
import com.fixlog.application.service.DocumentService;
import com.fixlog.application.service.DocumentTextExtractor;
import com.fixlog.application.service.FolderService;
import com.fixlog.application.repository.AuditLogRepository;
import com.fixlog.application.repository.DocumentHistoryRepository;
import com.fixlog.application.service.AuditService;
import com.fixlog.application.service.DocumentHistoryService;
import com.fixlog.application.service.PermissionEvaluator;
import com.fixlog.application.service.PermissionService;
import com.fixlog.application.service.SecurityPolicyService;
import com.fixlog.application.service.WorkspaceContext;
import com.fixlog.application.service.WorkspaceService;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.GroupEntity;
import com.fixlog.domain.model.GroupMemberEntity;
import com.fixlog.domain.model.PermissionEntity;
import com.fixlog.domain.model.PermissionType;
import com.fixlog.domain.model.PrincipalType;
import com.fixlog.domain.model.ResourceType;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.domain.model.WorkspaceEntity;
import com.fixlog.presentation.dto.request.DocumentCreateRequest;
import com.fixlog.presentation.dto.request.DocumentSaveRequest;
import com.fixlog.presentation.dto.request.FolderRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 공유 = 권한 부여 (FR-SHR-001 ~ FR-SHR-007). */
@DataJpaTest
class SharingTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired GroupRepository groupRepository;
    @Autowired GroupMemberRepository groupMemberRepository;
    @Autowired PermissionRepository permissionRepository;
    @Autowired SecurityPolicyRepository policyRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired DocumentHistoryRepository documentHistoryRepository;
    @Autowired FolderRepository folderRepository;
    @Autowired DocumentRepository documentRepository;
    @Autowired WorkspaceInvitationRepository invitationRepository;

    private WorkspaceService workspaceService;
    private PermissionService permissionService;
    private DocumentService documentService;
    private FolderService folderService;

    private UserEntity owner;
    private UserEntity mate;
    private WorkspaceEntity workspace;

    @BeforeEach
    void setUp() {
        WorkspaceContext workspaceContext =
                new WorkspaceContext(workspaceRepository, workspaceMemberRepository);
        workspaceService = new WorkspaceService(
                workspaceRepository, workspaceMemberRepository, userRepository, workspaceContext,
                folderRepository, documentRepository, permissionRepository, invitationRepository);
        PermissionEvaluator evaluator = new PermissionEvaluator(permissionRepository,
                workspaceMemberRepository, groupMemberRepository, groupRepository,
                folderRepository, documentRepository, workspaceContext, new AuditService(auditLogRepository), policyRepository);
        permissionService = new PermissionService(permissionRepository, workspaceMemberRepository,
                groupRepository, userRepository, evaluator, workspaceContext);
        SecurityPolicyService securityPolicyService =
                new SecurityPolicyService(policyRepository, workspaceService);
        folderService = new FolderService(folderRepository, documentRepository,
                workspaceContext, evaluator, permissionService);
        documentService = new DocumentService(documentRepository, folderRepository,
                new DocumentTextExtractor(), new DocumentPdfGenerator(),
                new DocumentHistoryService(documentRepository, documentHistoryRepository, 50),
                event -> {}, workspaceContext, evaluator, permissionService, securityPolicyService);

        UserEntity admin = signUp("admin");
        loginAs(admin);
        workspace = workspaceService.create("팀");
        owner = signUp("owner");
        mate = signUp("mate");
        workspaceService.invite(workspace.getWorkspaceId(), "owner@fixlog.dev");
        workspaceService.invite(workspace.getWorkspaceId(), "mate@fixlog.dev");

        useWorkspace(workspace.getWorkspaceId());
        loginAs(owner);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private UserEntity signUp(String name) {
        UserEntity user = userRepository.save(new UserEntity(name, name + "@fixlog.dev"));
        workspaceService.ensurePersonalWorkspace(user);
        return user;
    }

    private void loginAs(UserEntity user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private void useWorkspace(UUID workspaceId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(WorkspaceContext.HEADER_NAME, workspaceId.toString());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private String newDocument(String title) {
        return documentService.create(new DocumentCreateRequest(null, title)).getDocumentId();
    }

    // ---------- 부여 ----------

    @Test
    void 만든_사람은_소유자로_시작해서_공유할_수_있다() {
        String docId = newDocument("내 문서");

        permissionService.shareWithEmail(ResourceType.DOCUMENT, docId,
                "mate@fixlog.dev", PermissionType.ALLOW, true);

        loginAs(mate);
        assertEquals("내 문서", documentService.getDocument(docId).getTitle());
    }

    @Test
    void 공유_레벨이_행위를_제한한다() {
        String docId = newDocument("읽기만");
        permissionService.shareWithEmail(ResourceType.DOCUMENT, docId,
                "mate@fixlog.dev", PermissionType.ALLOW, true);

        loginAs(mate);
        assertEquals(Code.FORBIDDEN, assertThrows(BusinessException.class,
                () -> documentService.saveContent(docId,
                        new DocumentSaveRequest("고치기", JSON.readTree("[]")))).getCode());
    }

    @Test
    void 다운로드만_따로_막을_수_있다() {
        String docId = newDocument("반출 금지");
        permissionService.shareWithEmail(ResourceType.DOCUMENT, docId,
                "mate@fixlog.dev", PermissionType.ALLOW, false);

        loginAs(mate);
        assertEquals("반출 금지", documentService.getDocument(docId).getTitle());
        assertEquals(Code.FORBIDDEN, assertThrows(BusinessException.class,
                () -> documentService.downloadPdfResult(docId)).getCode());
    }

    // 같은 대상·주체에 레코드가 둘 생기면 어느 쪽이 이기는지가 불분명해진다.
    @Test
    void 같은_대상에_다시_공유하면_레벨이_갱신된다() {
        String docId = newDocument("문서");
        permissionService.shareWithEmail(ResourceType.DOCUMENT, docId,
                "mate@fixlog.dev", PermissionType.ALLOW, true);
        permissionService.shareWithEmail(ResourceType.DOCUMENT, docId,
                "mate@fixlog.dev", PermissionType.ALLOW, true);

        List<PermissionEntity> forMate = permissionRepository
                .findByResourceTypeAndResourceId(ResourceType.DOCUMENT, docId).stream()
                .filter(p -> p.getPrincipalId().equals(mate.getUserId())).toList();

        assertEquals(1, forMate.size(), "중복 레코드가 생기면 안 된다");
        assertEquals(PermissionType.ALLOW, forMate.get(0).getPermissionType());
    }

    @Test
    void 폴더를_공유하면_그_안의_문서까지_열린다() {
        var folder = folderService.createFolder(new FolderRequest(null, "공유 폴더"));
        String docId = documentService.create(
                new DocumentCreateRequest(folder.getFolderId(), "폴더 안 문서")).getDocumentId();

        permissionService.shareWithEmail(ResourceType.FOLDER, folder.getFolderId(),
                "mate@fixlog.dev", PermissionType.ALLOW, true);

        loginAs(mate);
        assertEquals("폴더 안 문서", documentService.getDocument(docId).getTitle());
    }

    @Test
    void 그룹에_공유하면_그룹원이_볼_수_있다() {
        String docId = newDocument("그룹 문서");
        GroupEntity group = groupRepository.save(new GroupEntity(workspace.getWorkspaceId(), "백엔드"));
        groupMemberRepository.save(new GroupMemberEntity(group.getGroupId(), mate.getUserId()));

        permissionService.share(ResourceType.DOCUMENT, docId,
                PrincipalType.GROUP, group.getGroupId(), PermissionType.ALLOW, true);

        loginAs(mate);
        assertEquals("그룹 문서", documentService.getDocument(docId).getTitle());
    }

    // ---------- 제약 ----------

    @Test
    void 워크스페이스_밖의_사용자에게는_공유할_수_없다() {
        String docId = newDocument("문서");
        signUp("outsider");

        BusinessException e = assertThrows(BusinessException.class,
                () -> permissionService.shareWithEmail(ResourceType.DOCUMENT, docId,
                        "outsider@fixlog.dev", PermissionType.ALLOW, true));

        assertEquals(Code.NOT_FOUND, e.getCode());
    }

    @Test
    void 소유자가_아니면_공유_설정을_할_수_없다() {
        String docId = newDocument("문서");
        permissionService.shareWithEmail(ResourceType.DOCUMENT, docId,
                "mate@fixlog.dev", PermissionType.ALLOW, true);

        loginAs(mate);
        assertEquals(Code.FORBIDDEN, assertThrows(BusinessException.class,
                () -> permissionService.shareWithEmail(ResourceType.DOCUMENT, docId,
                        "admin@fixlog.dev", PermissionType.ALLOW, true)).getCode());
    }

    // ---------- 회수 ----------

    @Test
    void 공유를_회수하면_접근이_끊긴다() {
        String docId = newDocument("문서");
        PermissionEntity granted = permissionService.shareWithEmail(ResourceType.DOCUMENT, docId,
                "mate@fixlog.dev", PermissionType.ALLOW, true);

        permissionService.revoke(ResourceType.DOCUMENT, docId, granted.getId());

        loginAs(mate);
        assertEquals(Code.FORBIDDEN, assertThrows(BusinessException.class,
                () -> documentService.getDocument(docId)).getCode());
    }

    // ---------- 나와 공유됨 ----------

    @Test
    void 나와_공유됨에는_내가_만들지_않은_것만_나온다() {
        String mine = newDocument("내가 만든 문서");
        permissionService.shareWithEmail(ResourceType.DOCUMENT, mine,
                "mate@fixlog.dev", PermissionType.ALLOW, true);

        loginAs(mate);
        String mateOwn = newDocument("mate가 만든 문서");

        List<String> titles = documentService.sharedWithMe().stream()
                .map(d -> d.getTitle()).toList();

        assertEquals(List.of("내가 만든 문서"), titles);
        assertTrue(documentService.getDocument(mateOwn) != null, "자기 문서는 여전히 볼 수 있다");
    }

    @Test
    void 공유받지_않으면_나와_공유됨이_비어_있다() {
        newDocument("남의 문서");

        loginAs(mate);
        assertTrue(documentService.sharedWithMe().isEmpty());
    }
}
