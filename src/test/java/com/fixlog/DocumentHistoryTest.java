package com.fixlog;

import com.fixlog.application.repository.AuditLogRepository;
import com.fixlog.application.repository.DocumentHistoryRepository;
import com.fixlog.application.repository.DocumentHistoryRepository.DocumentHistorySummary;
import com.fixlog.application.repository.DocumentLabelRepository;
import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.LabelRepository;
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
import com.fixlog.application.service.PermissionEvaluator;
import com.fixlog.application.service.PermissionService;
import com.fixlog.application.service.SecurityPolicyService;
import com.fixlog.application.service.WorkspaceContext;
import com.fixlog.application.service.WorkspaceService;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.DocumentHistorySource;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.domain.model.WorkspaceEntity;
import com.fixlog.presentation.dto.request.DocumentCreateRequest;
import com.fixlog.presentation.dto.request.DocumentSaveRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class DocumentHistoryTest {

    private static final int RETENTION = 3;

    @Autowired FolderRepository folderRepository;
    @Autowired DocumentRepository documentRepository;
    @Autowired DocumentHistoryRepository documentHistoryRepository;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired PermissionRepository permissionRepository;
    @Autowired SecurityPolicyRepository policyRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired LabelRepository labelRepository;
    @Autowired DocumentLabelRepository documentLabelRepository;
    @Autowired GroupRepository groupRepository;
    @Autowired GroupMemberRepository groupMemberRepository;
    @Autowired WorkspaceInvitationRepository invitationRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private DocumentService documentService;
    private DocumentHistoryService documentHistoryService;
    private WorkspaceService workspaceService;

    @BeforeEach
    void setUp() {
        WorkspaceContext workspaceContext =
                new WorkspaceContext(workspaceRepository, workspaceMemberRepository);
        workspaceService = new WorkspaceService(
                workspaceRepository, workspaceMemberRepository, userRepository, workspaceContext,
                folderRepository, documentRepository, permissionRepository, invitationRepository, new AuditService(auditLogRepository),
                auditLogRepository, labelRepository, documentLabelRepository, policyRepository, groupRepository, groupMemberRepository);
        PermissionEvaluator permissionEvaluator = new PermissionEvaluator(
                permissionRepository, workspaceMemberRepository, groupMemberRepository,
                groupRepository, folderRepository, documentRepository, workspaceContext,
                new AuditService(auditLogRepository), policyRepository);
        PermissionService permissionService = new PermissionService(
                permissionRepository, workspaceMemberRepository, groupRepository,
                userRepository, permissionEvaluator, workspaceContext, new AuditService(auditLogRepository));
        SecurityPolicyService securityPolicyService =
                new SecurityPolicyService(policyRepository, workspaceService);
        documentHistoryService = new DocumentHistoryService(
                documentRepository, documentHistoryRepository, RETENTION);
        documentService = new DocumentService(documentRepository, folderRepository,
                new DocumentTextExtractor(), new DocumentPdfGenerator(),
                documentHistoryService, event -> {}, workspaceContext,
                permissionEvaluator, permissionService, securityPolicyService);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private void loginAsNewUser(String id) {
        UserEntity user = userRepository.save(new UserEntity(id, id + "@fixlog.dev"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
        WorkspaceEntity personal = workspaceService.ensurePersonalWorkspace(user);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(WorkspaceContext.HEADER_NAME, personal.getWorkspaceId().toString());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private JsonNode blocksOf(String text) {
        return objectMapper.readTree(
                "[{\"type\":\"paragraph\",\"data\":{\"text\":\"%s\"}}]".formatted(text));
    }

    private DocumentEntity save(String documentId, String title, String text) {
        return documentService.saveContent(documentId, new DocumentSaveRequest(title, blocksOf(text)));
    }

    private String createDocument() {
        return documentService.create(new DocumentCreateRequest(null, "문서")).getDocumentId();
    }

    private List<DocumentHistorySummary> history(String documentId) {
        return documentHistoryService.list(documentId, PageRequest.of(0, 20)).getContent();
    }

    @Test
    void 저장하면_직전_내용이_히스토리로_밀려난다() {
        loginAsNewUser("history-user");
        String documentId = createDocument();

        save(documentId, "문서", "첫 번째 내용");
        save(documentId, "문서", "두 번째 내용");

        // 최초 생성 시의 빈 문서 + "첫 번째 내용" 두 버전이 쌓인다.
        List<DocumentHistorySummary> versions = history(documentId);
        assertEquals(2, versions.size());

        // 현재 본문은 문서 테이블에만 있고, 히스토리에는 지나간 버전만 있다.
        DocumentEntity current = documentService.getDocument(documentId);
        assertTrue(current.getPlainText().contains("두 번째 내용"));

        String latestHistoryBlocks = documentHistoryService
                .getVersion(documentId, versions.get(0).getHistoryId()).getBlocks();
        assertTrue(latestHistoryBlocks.contains("첫 번째 내용"));
    }

    @Test
    void 내용이_같으면_버전을_만들지_않는다() {
        loginAsNewUser("same-user");
        String documentId = createDocument();

        save(documentId, "문서", "같은 내용");
        int afterFirstSave = history(documentId).size();
        save(documentId, "문서", "같은 내용");

        assertEquals(afterFirstSave, history(documentId).size());
    }

    @Test
    void 복원하면_복원_직전_내용도_히스토리에_남는다() {
        loginAsNewUser("restore-user");
        String documentId = createDocument();
        save(documentId, "문서", "예전 내용");
        save(documentId, "문서", "최신 내용");

        String oldVersionId = history(documentId).stream()
                .filter(v -> documentHistoryService.getVersion(documentId, v.getHistoryId())
                        .getBlocks().contains("예전 내용"))
                .findFirst().orElseThrow().getHistoryId();

        DocumentEntity restored = documentService.restoreFromHistory(documentId, oldVersionId);

        assertTrue(restored.getPlainText().contains("예전 내용"));
        // 복원 직전의 "최신 내용"이 RESTORE 버전으로 남아 복원을 되돌릴 수 있다.
        DocumentHistorySummary newest = history(documentId).get(0);
        assertEquals(DocumentHistorySource.RESTORE, newest.getSource());
        assertTrue(documentHistoryService.getVersion(documentId, newest.getHistoryId())
                .getBlocks().contains("최신 내용"));
    }

    @Test
    void 보존_개수를_넘으면_오래된_버전부터_지워진다() {
        loginAsNewUser("retention-user");
        String documentId = createDocument();

        for (int i = 1; i <= RETENTION + 3; i++) {
            save(documentId, "문서", "내용 " + i);
        }

        assertEquals(RETENTION, documentHistoryRepository.countByDocumentId(documentId));
        // 가장 최근에 밀려난 버전은 살아 있어야 한다.
        assertTrue(documentHistoryService
                .getVersion(documentId, history(documentId).get(0).getHistoryId())
                .getBlocks().contains("내용 " + (RETENTION + 2)));
    }

    @Test
    void 보존_개수를_0이하로_설정하면_기동에_실패한다() {
        // 설정 실수로 히스토리가 통째로 지워지는 것을 기동 시점에 막는다.
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentHistoryService(documentRepository, documentHistoryRepository, 0));
    }

    @Test
    void 다른_문서의_히스토리는_조회되지_않는다() {
        loginAsNewUser("idor-user");
        String mine = createDocument();
        String other = createDocument();
        save(mine, "문서", "내 내용");
        save(other, "문서", "남의 내용");

        String otherHistoryId = history(other).get(0).getHistoryId();

        assertThrows(BusinessException.class,
                () -> documentHistoryService.getVersion(mine, otherHistoryId));
    }

    @Test
    @Disabled("액션별 권한 미구현 — permission 테이블에 action 컬럼이 없어 ALLOW 하나로 VIEW/EDIT/DELETE가 모두 열린다. 구현 시 이 테스트를 인수 조건으로 삼을 것")
    void 다른_사용자의_문서_히스토리는_접근할_수_없다() {
        loginAsNewUser("owner");
        String documentId = createDocument();
        save(documentId, "문서", "주인 내용");

        loginAsNewUser("stranger");

        assertThrows(BusinessException.class,
                () -> documentHistoryService.list(documentId, PageRequest.of(0, 20)));
    }
}
