package com.fixlog;

import com.fixlog.application.repository.AuditLogRepository;
import com.fixlog.application.repository.DocumentHistoryRepository;
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
import com.fixlog.application.service.AuditService;
import com.fixlog.application.service.DocumentHistoryService;
import com.fixlog.application.service.DocumentPdfGenerator;
import com.fixlog.application.service.DocumentService;
import com.fixlog.application.service.DocumentTextExtractor;
import com.fixlog.application.service.FolderService;
import com.fixlog.application.service.PermissionEvaluator;
import com.fixlog.application.service.PermissionService;
import com.fixlog.application.service.SecurityPolicyService;
import com.fixlog.application.service.WorkspaceContext;
import com.fixlog.application.service.WorkspaceService;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.PermissionType;
import com.fixlog.domain.model.ResourceType;
import com.fixlog.domain.model.SecurityPolicyEntity;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.domain.model.WorkspaceEntity;
import com.fixlog.presentation.dto.request.DocumentCreateRequest;
import com.fixlog.presentation.dto.request.DocumentSaveRequest;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 워크스페이스 보안 정책 (FR-SEC-001~005). */
@DataJpaTest
class SecurityPolicyTest {

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
    private SecurityPolicyService policyService;
    private DocumentService documentService;

    private UserEntity admin;
    private UserEntity member;
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
                folderRepository, documentRepository, workspaceContext,
                new AuditService(auditLogRepository), policyRepository);
        permissionService = new PermissionService(permissionRepository, workspaceMemberRepository,
                groupRepository, userRepository, evaluator, workspaceContext);
        policyService = new SecurityPolicyService(policyRepository, workspaceService);
        FolderService folderService = new FolderService(folderRepository, documentRepository,
                workspaceContext, evaluator, permissionService);
        documentService = new DocumentService(documentRepository, folderRepository,
                new DocumentTextExtractor(), new DocumentPdfGenerator(),
                new DocumentHistoryService(documentRepository, documentHistoryRepository, 50),
                event -> {}, workspaceContext, evaluator, permissionService, policyService);

        auditLogRepository.deleteAll();

        admin = signUp("admin");
        loginAs(admin);
        workspace = workspaceService.create("팀");
        member = signUp("member");
        workspaceService.invite(workspace.getWorkspaceId(), "member@fixlog.dev");

        useWorkspace(workspace.getWorkspaceId());
        loginAs(admin);
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
        String id = documentService.create(new DocumentCreateRequest(null, title)).getDocumentId();
        documentService.saveContent(id, new DocumentSaveRequest(title, JsonMapper.builder().build()
                .readTree("[{\"type\":\"paragraph\",\"data\":{\"text\":\"내용\"}}]")));
        return id;
    }

    // ---------- 기본값 ----------

    @Test
    void 정책_행이_없으면_기본값으로_본다() {
        SecurityPolicyEntity policy = policyService.effectivePolicy(workspace.getWorkspaceId());

        assertTrue(policy.isAllowSharing());
        assertTrue(policy.isAllowDownload());
        assertFalse(policy.isEnforceWatermark());
        assertEquals(365, policy.getAuditRetentionDays());
        assertEquals(30, policy.getTrashRetentionDays());
    }

    // ---------- 정책이 권한보다 위 ----------

    // 개별 권한이 다운로드를 허용해도 정책이 금지하면 금지된다.
    @Test
    void 정책이_다운로드를_막으면_권한이_있어도_막힌다() {
        String docId = newDocument("문서");
        permissionService.shareWithEmail(ResourceType.DOCUMENT, docId,
                "member@fixlog.dev", PermissionType.ALLOW, true);
        policyService.update(workspace.getWorkspaceId(), null, false, null, null, null);

        loginAs(member);
        assertEquals("문서", documentService.getDocument(docId).getTitle(), "열람은 된다");
        assertEquals(Code.FORBIDDEN, assertThrows(BusinessException.class,
                () -> documentService.downloadPdfResult(docId)).getCode());
    }

    // 정책은 관리자가 스스로에게 건 제약이다. 관리자라고 빠져나갈 수 있으면 정책이 아니다.
    @Test
    void 정책은_관리자에게도_적용된다() {
        String docId = newDocument("문서");
        policyService.update(workspace.getWorkspaceId(), null, false, null, null, null);

        assertEquals(Code.FORBIDDEN, assertThrows(BusinessException.class,
                () -> documentService.downloadPdfResult(docId)).getCode());
    }

    @Test
    void 정책이_공유를_막으면_소유자도_공유할_수_없다() {
        String docId = newDocument("문서");
        policyService.update(workspace.getWorkspaceId(), false, null, null, null, null);

        assertEquals(Code.FORBIDDEN, assertThrows(BusinessException.class,
                () -> permissionService.shareWithEmail(ResourceType.DOCUMENT, docId,
                        "member@fixlog.dev", PermissionType.ALLOW, true)).getCode());
    }

    @Test
    void 정책을_되돌리면_다시_허용된다() {
        String docId = newDocument("문서");
        policyService.update(workspace.getWorkspaceId(), null, false, null, null, null);
        policyService.update(workspace.getWorkspaceId(), null, true, null, null, null);

        assertTrue(documentService.downloadPdfResult(docId).bytes().length > 0);
    }

    // ---------- 워터마크 ----------

    @Test
    void 워터마크를_강제하면_산출물이_달라진다() {
        String docId = newDocument("문서");
        byte[] plain = documentService.downloadPdfResult(docId).bytes();

        policyService.update(workspace.getWorkspaceId(), null, null, true, null, null);
        byte[] marked = documentService.downloadPdfResult(docId).bytes();

        assertTrue(marked.length != plain.length,
                "각인이 들어가면 산출물이 그대로일 수 없다");
    }

    // ---------- 변경 권한 ----------

    @Test
    void 정책_변경은_관리자만_할_수_있다() {
        loginAs(member);

        assertEquals(Code.FORBIDDEN, assertThrows(BusinessException.class,
                () -> policyService.update(workspace.getWorkspaceId(), null, false, null, null, null))
                .getCode());
    }

    @Test
    void 구성원은_자기에게_걸린_정책을_볼_수_있다() {
        policyService.update(workspace.getWorkspaceId(), null, false, null, null, null);

        loginAs(member);
        assertFalse(policyService.view(workspace.getWorkspaceId()).isAllowDownload());
    }

    @Test
    void 비구성원에게는_정책이_보이지_않는다() {
        loginAs(signUp("stranger"));

        assertEquals(Code.NOT_FOUND, assertThrows(BusinessException.class,
                () -> policyService.view(workspace.getWorkspaceId())).getCode());
    }

    @Test
    void 넘기지_않은_항목은_그대로_둔다() {
        policyService.update(workspace.getWorkspaceId(), null, false, null, null, null);
        policyService.update(workspace.getWorkspaceId(), null, null, true, null, null);

        SecurityPolicyEntity policy = policyService.effectivePolicy(workspace.getWorkspaceId());
        assertFalse(policy.isAllowDownload(), "앞서 끈 설정이 되살아나면 안 된다");
        assertTrue(policy.isEnforceWatermark());
    }
}
