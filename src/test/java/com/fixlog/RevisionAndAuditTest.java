package com.fixlog;

import com.fixlog.application.repository.AuditLogRepository;
import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.DocumentRevisionRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.application.repository.GroupMemberRepository;
import com.fixlog.application.repository.GroupRepository;
import com.fixlog.application.repository.PermissionRepository;
import com.fixlog.application.repository.UserRepository;
import com.fixlog.application.repository.WorkspaceMemberRepository;
import com.fixlog.application.repository.WorkspaceRepository;
import com.fixlog.application.service.AuditService;
import com.fixlog.application.service.DocumentPdfGenerator;
import com.fixlog.application.service.DocumentService;
import com.fixlog.application.service.DocumentTextExtractor;
import com.fixlog.application.service.FolderService;
import com.fixlog.application.service.PermissionEvaluator;
import com.fixlog.application.service.PermissionService;
import com.fixlog.application.service.WorkspaceContext;
import com.fixlog.application.service.WorkspaceService;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.AuditAction;
import com.fixlog.domain.model.AuditLogEntity;
import com.fixlog.domain.model.AuditResult;
import com.fixlog.domain.model.DocumentRevisionEntity;
import com.fixlog.domain.model.PermissionLevel;
import com.fixlog.domain.model.ResourceType;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 문서 리비전·롤백(FR-REV-*)과 감사 로그(FR-AUD-*). */
@DataJpaTest
class RevisionAndAuditTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired GroupRepository groupRepository;
    @Autowired GroupMemberRepository groupMemberRepository;
    @Autowired PermissionRepository permissionRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired DocumentRevisionRepository revisionRepository;
    @Autowired FolderRepository folderRepository;
    @Autowired DocumentRepository documentRepository;

    private WorkspaceService workspaceService;
    private PermissionService permissionService;
    private DocumentService documentService;

    private UserEntity admin;
    private UserEntity author;
    private UserEntity mate;
    private WorkspaceEntity workspace;

    @BeforeEach
    void setUp() {
        WorkspaceContext workspaceContext =
                new WorkspaceContext(workspaceRepository, workspaceMemberRepository);
        workspaceService = new WorkspaceService(
                workspaceRepository, workspaceMemberRepository, userRepository, workspaceContext);
        PermissionEvaluator evaluator = new PermissionEvaluator(permissionRepository,
                workspaceMemberRepository, groupMemberRepository, groupRepository,
                folderRepository, documentRepository, workspaceContext,
                new AuditService(auditLogRepository));
        permissionService = new PermissionService(permissionRepository, workspaceMemberRepository,
                groupRepository, userRepository, evaluator, workspaceContext);
        FolderService folderService = new FolderService(folderRepository, documentRepository,
                workspaceContext, evaluator, permissionService);
        documentService = new DocumentService(documentRepository, folderRepository,
                new DocumentTextExtractor(), new DocumentPdfGenerator(), event -> {},
                workspaceContext, evaluator, permissionService, revisionRepository);

        // 감사 로그는 별도 트랜잭션에 커밋되므로 테스트 롤백으로 지워지지 않는다
        auditLogRepository.deleteAll();

        admin = signUp("admin");
        loginAs(admin);
        workspace = workspaceService.create("팀");
        author = signUp("author");
        mate = signUp("mate");
        workspaceService.invite(workspace.getWorkspaceId(), "author@fixlog.dev");
        workspaceService.invite(workspace.getWorkspaceId(), "mate@fixlog.dev");

        useWorkspace(workspace.getWorkspaceId());
        loginAs(author);
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

    private JsonNode blocks(String text) {
        return JSON.readTree("[{\"type\":\"paragraph\",\"data\":{\"text\":\"" + text + "\"}}]");
    }

    private String newDocument(String title) {
        return documentService.create(new DocumentCreateRequest(null, title)).getDocumentId();
    }

    // ---------- 리비전 ----------

    @Test
    void 저장할_때마다_리비전이_쌓인다() {
        String docId = newDocument("문서");

        documentService.saveContent(docId, new DocumentSaveRequest("문서", blocks("첫 번째")));
        documentService.saveContent(docId, new DocumentSaveRequest("문서", blocks("두 번째")));

        List<DocumentRevisionEntity> revisions = documentService.revisions(docId);
        assertEquals(2, revisions.size());
        assertEquals(2, revisions.get(0).getRevisionNo(), "목록은 최신순이다");
        assertEquals("두 번째", revisions.get(0).getPlainText());
    }

    // 자동저장이 내용 변경 없이 반복되면 의미 없는 리비전만 쌓인다.
    @Test
    void 내용이_같으면_리비전을_만들지_않는다() {
        String docId = newDocument("문서");
        for (int i = 0; i < 5; i++) {
            documentService.saveContent(docId, new DocumentSaveRequest("문서", blocks("같은 내용")));
        }

        assertEquals(1, documentService.revisions(docId).size());
    }

    @Test
    void 롤백하면_본문이_되돌아가고_과거는_남는다() {
        String docId = newDocument("문서");
        documentService.saveContent(docId, new DocumentSaveRequest("문서", blocks("처음")));
        documentService.saveContent(docId, new DocumentSaveRequest("문서", blocks("나중")));

        documentService.restore(docId, 1);

        assertEquals("처음", documentService.getDocument(docId).getPlainText());
        List<DocumentRevisionEntity> revisions = documentService.revisions(docId);
        assertEquals(3, revisions.size(), "롤백도 새 리비전으로 쌓인다");
        assertEquals(Integer.valueOf(1), revisions.get(0).getRestoredFromNo());
        assertEquals("나중", revisions.get(1).getPlainText(), "되돌리기 전 리비전이 지워지지 않는다");
    }

    @Test
    void 롤백을_다시_되돌릴_수_있다() {
        String docId = newDocument("문서");
        documentService.saveContent(docId, new DocumentSaveRequest("문서", blocks("처음")));
        documentService.saveContent(docId, new DocumentSaveRequest("문서", blocks("나중")));
        documentService.restore(docId, 1);

        documentService.restore(docId, 2);

        assertEquals("나중", documentService.getDocument(docId).getPlainText());
    }

    @Test
    void 읽기_권한만_있으면_롤백할_수_없다() {
        String docId = newDocument("문서");
        documentService.saveContent(docId, new DocumentSaveRequest("문서", blocks("내용")));
        permissionService.shareWithEmail(ResourceType.DOCUMENT, docId,
                "mate@fixlog.dev", PermissionLevel.VIEWER, true);

        loginAs(mate);
        assertEquals(1, documentService.revisions(docId).size(), "조회는 된다");
        assertEquals(Code.FORBIDDEN, assertThrows(BusinessException.class,
                () -> documentService.restore(docId, 1)).getCode());
    }

    // ---------- 감사 로그 ----------

    @Test
    void 열람은_감사_로그에_남는다() {
        String docId = newDocument("문서");
        auditLogRepository.deleteAll();

        documentService.getDocument(docId);

        List<AuditLogEntity> logs = auditLogRepository
                .findByResourceTypeAndResourceIdOrderByCreateAtDesc(ResourceType.DOCUMENT, docId);
        assertEquals(1, logs.size());
        assertEquals(AuditAction.VIEW, logs.get(0).getAction());
        assertEquals(AuditResult.ALLOWED, logs.get(0).getResult());
        assertEquals(author.getUserId(), logs.get(0).getActorUserId());
    }

    // 거부는 예외로 끝나 본 트랜잭션이 롤백된다. 같은 트랜잭션에 쓰면 정작 남겨야 할 기록이 사라진다.
    @Test
    void 거부된_접근도_남는다() {
        String docId = newDocument("비밀 문서");
        auditLogRepository.deleteAll();

        loginAs(mate);
        assertThrows(BusinessException.class, () -> documentService.getDocument(docId));

        List<AuditLogEntity> logs = auditLogRepository
                .findByResourceTypeAndResourceIdOrderByCreateAtDesc(ResourceType.DOCUMENT, docId);
        assertEquals(1, logs.size());
        assertEquals(AuditResult.DENIED, logs.get(0).getResult());
        assertEquals(mate.getUserId(), logs.get(0).getActorUserId());
    }

    @Test
    void 다운로드는_열람과_구분되어_남는다() {
        String docId = newDocument("문서");
        documentService.saveContent(docId, new DocumentSaveRequest("문서", blocks("내용")));
        auditLogRepository.deleteAll();

        documentService.downloadPdfResult(docId);

        List<AuditLogEntity> logs = auditLogRepository
                .findByResourceTypeAndResourceIdOrderByCreateAtDesc(ResourceType.DOCUMENT, docId);
        assertEquals(1, logs.size());
        assertEquals(AuditAction.DOWNLOAD, logs.get(0).getAction());
    }

    // 권한을 받아서 본 것과 관리자 특권으로 본 것은 조사에서 구분되어야 한다.
    @Test
    void 관리자_특권_접근은_따로_표시된다() {
        String docId = newDocument("문서");
        auditLogRepository.deleteAll();

        loginAs(admin);
        documentService.getDocument(docId);

        AuditLogEntity log = auditLogRepository
                .findByResourceTypeAndResourceIdOrderByCreateAtDesc(ResourceType.DOCUMENT, docId).get(0);
        assertTrue(log.isViaAdmin());
    }

    @Test
    void 소유자의_접근은_관리자_특권이_아니다() {
        String docId = newDocument("문서");
        auditLogRepository.deleteAll();

        documentService.getDocument(docId);

        AuditLogEntity log = auditLogRepository
                .findByResourceTypeAndResourceIdOrderByCreateAtDesc(ResourceType.DOCUMENT, docId).get(0);
        assertFalse(log.isViaAdmin());
    }
}
