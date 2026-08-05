package com.fixlog;

import com.fixlog.application.repository.AuditLogRepository;
import com.fixlog.application.repository.DocumentLabelRepository;
import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.DocumentRevisionRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.application.repository.GroupMemberRepository;
import com.fixlog.application.repository.GroupRepository;
import com.fixlog.application.repository.PermissionRepository;
import com.fixlog.application.repository.SecurityPolicyRepository;
import com.fixlog.application.repository.UserRepository;
import com.fixlog.application.repository.WorkspaceMemberRepository;
import com.fixlog.application.repository.WorkspaceRepository;
import com.fixlog.application.service.AdminConsoleService;
import com.fixlog.application.service.AuditService;
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
import com.fixlog.domain.model.AuditAction;
import com.fixlog.domain.model.AuditResult;
import com.fixlog.domain.model.PermissionLevel;
import com.fixlog.domain.model.ResourceType;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.domain.model.WorkspaceEntity;
import com.fixlog.presentation.dto.request.DocumentCreateRequest;
import com.fixlog.presentation.dto.request.FolderRequest;
import com.fixlog.presentation.dto.response.AdminPermissionDto;
import com.fixlog.presentation.dto.response.AuditLogDto;
import com.fixlog.presentation.dto.response.WorkspaceStatsDto;
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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 워크스페이스 관리자 콘솔 (FR-ADM-002~007, FR-AUD-005). */
@DataJpaTest
class AdminConsoleTest {

    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired GroupRepository groupRepository;
    @Autowired GroupMemberRepository groupMemberRepository;
    @Autowired PermissionRepository permissionRepository;
    @Autowired SecurityPolicyRepository policyRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired DocumentRevisionRepository revisionRepository;
    @Autowired DocumentLabelRepository documentLabelRepository;
    @Autowired FolderRepository folderRepository;
    @Autowired DocumentRepository documentRepository;

    private WorkspaceService workspaceService;
    private PermissionService permissionService;
    private DocumentService documentService;
    private FolderService folderService;
    private AdminConsoleService adminConsole;

    private UserEntity admin;
    private UserEntity member;
    private WorkspaceEntity workspace;

    @BeforeEach
    void setUp() {
        WorkspaceContext workspaceContext =
                new WorkspaceContext(workspaceRepository, workspaceMemberRepository);
        workspaceService = new WorkspaceService(
                workspaceRepository, workspaceMemberRepository, userRepository, workspaceContext);
        AuditService auditService = new AuditService(auditLogRepository);
        PermissionEvaluator evaluator = new PermissionEvaluator(permissionRepository,
                workspaceMemberRepository, groupMemberRepository, groupRepository,
                folderRepository, documentRepository, workspaceContext, auditService, policyRepository);
        permissionService = new PermissionService(permissionRepository, workspaceMemberRepository,
                groupRepository, userRepository, evaluator, workspaceContext);
        SecurityPolicyService securityPolicyService =
                new SecurityPolicyService(policyRepository, workspaceService);
        folderService = new FolderService(folderRepository, documentRepository,
                workspaceContext, evaluator, permissionService);
        documentService = new DocumentService(documentRepository, folderRepository,
                new DocumentTextExtractor(), new DocumentPdfGenerator(), event -> {},
                workspaceContext, evaluator, permissionService, revisionRepository, securityPolicyService);
        adminConsole = new AdminConsoleService(permissionRepository, auditLogRepository,
                documentRepository, folderRepository, userRepository, groupRepository, workspaceService);

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
        return documentService.create(new DocumentCreateRequest(null, title)).getDocumentId();
    }

    // ---------- 접근 통제 ----------

    @Test
    void 일반_구성원은_관리자_콘솔을_볼_수_없다() {
        loginAs(member);
        UUID ws = workspace.getWorkspaceId();

        for (Runnable call : List.<Runnable>of(
                () -> adminConsole.permissions(ws),
                () -> adminConsole.shares(ws),
                () -> adminConsole.stats(ws),
                () -> adminConsole.auditLogs(ws, null, null, null, null, null))) {
            assertEquals(Code.FORBIDDEN, assertThrows(BusinessException.class, call::run).getCode());
        }
    }

    @Test
    void 비구성원에게는_워크스페이스가_존재하지_않는다() {
        loginAs(signUp("stranger"));

        assertEquals(Code.NOT_FOUND, assertThrows(BusinessException.class,
                () -> adminConsole.stats(workspace.getWorkspaceId())).getCode());
    }

    // ---------- 권한·공유 현황 ----------

    @Test
    void 권한_현황에는_이름이_함께_나온다() {
        String docId = newDocument("문서");

        List<AdminPermissionDto> permissions = adminConsole.permissions(workspace.getWorkspaceId());

        assertEquals(1, permissions.size());
        assertEquals("문서", permissions.get(0).resourceName());
        assertEquals("admin", permissions.get(0).principalName());
    }

    // 생성자 소유 권한까지 세면 모든 문서가 "공유됨"으로 잡혀 목록이 쓸모없어진다.
    @Test
    void 공유_현황에는_생성자_소유_권한이_빠진다() {
        String shared = newDocument("공유한 문서");
        newDocument("공유 안 한 문서");
        permissionService.shareWithEmail(ResourceType.DOCUMENT, shared,
                "member@fixlog.dev", PermissionLevel.VIEWER, true);

        List<AdminPermissionDto> shares = adminConsole.shares(workspace.getWorkspaceId());

        assertEquals(1, shares.size());
        assertEquals("공유한 문서", shares.get(0).resourceName());
        assertEquals("member", shares.get(0).principalName());
    }

    // ---------- 감사 로그 ----------

    @Test
    void 감사_로그를_행위자로_거를_수_있다() {
        String docId = newDocument("문서");
        permissionService.shareWithEmail(ResourceType.DOCUMENT, docId,
                "member@fixlog.dev", PermissionLevel.VIEWER, true);
        auditLogRepository.deleteAll();

        loginAs(member);
        documentService.getDocument(docId);
        loginAs(admin);
        documentService.getDocument(docId);

        List<AuditLogDto> byMember = adminConsole.auditLogs(
                workspace.getWorkspaceId(), member.getUserId(), null, null, null, null);

        assertEquals(1, byMember.size());
        assertEquals("member", byMember.get(0).actorName());
        assertEquals(2, adminConsole.auditLogs(
                workspace.getWorkspaceId(), null, null, null, null, null).size());
    }

    @Test
    void 거부된_접근만_따로_볼_수_있다() {
        String docId = newDocument("비밀 문서");
        auditLogRepository.deleteAll();

        loginAs(member);
        assertThrows(BusinessException.class, () -> documentService.getDocument(docId));
        loginAs(admin);
        documentService.getDocument(docId);

        List<AuditLogDto> denied = adminConsole.auditLogs(
                workspace.getWorkspaceId(), null, null, AuditResult.DENIED, null, null);

        assertEquals(1, denied.size());
        assertEquals(member.getUserId(), denied.get(0).actorUserId());
        assertEquals(AuditAction.VIEW, denied.get(0).action());
    }

    @Test
    void 관리자_특권_접근이_구분되어_보인다() {
        String docId = newDocument("문서");
        auditLogRepository.deleteAll();
        documentService.getDocument(docId);

        List<AuditLogDto> logs = adminConsole.auditLogs(
                workspace.getWorkspaceId(), null, null, null, null, null);

        assertTrue(logs.get(0).viaAdmin());
    }

    // ---------- 현황 ----------

    @Test
    void 문서_폴더_현황과_사용자별_분포를_센다() {
        newDocument("문서 1");
        String toDelete = newDocument("지울 문서");
        folderService.createFolder(new FolderRequest(null, "폴더"));
        documentService.delete(toDelete);

        WorkspaceStatsDto stats = adminConsole.stats(workspace.getWorkspaceId());

        assertEquals(1, stats.documentCount());
        assertEquals(1, stats.folderCount());
        assertEquals(1, stats.trashedDocumentCount(), "휴지통 현황도 함께 본다");
        assertEquals(0, stats.trashedFolderCount());
        assertEquals(Long.valueOf(1), stats.documentCountByUser().get("admin"));
    }

    @Test
    void 다른_워크스페이스의_수치가_섞이지_않는다() {
        newDocument("팀 문서");

        WorkspaceEntity another = workspaceService.create("다른 팀");
        useWorkspace(another.getWorkspaceId());
        newDocument("다른 팀 문서");

        assertEquals(1, adminConsole.stats(workspace.getWorkspaceId()).documentCount());
        assertEquals(1, adminConsole.stats(another.getWorkspaceId()).documentCount());
    }
}
