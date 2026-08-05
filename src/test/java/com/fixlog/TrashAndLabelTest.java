package com.fixlog;

import com.fixlog.application.repository.AuditLogRepository;
import com.fixlog.application.repository.DocumentLabelRepository;
import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.DocumentRevisionRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.application.repository.GroupMemberRepository;
import com.fixlog.application.repository.GroupRepository;
import com.fixlog.application.repository.LabelRepository;
import com.fixlog.application.repository.PermissionRepository;
import com.fixlog.application.repository.SecurityPolicyRepository;
import com.fixlog.application.repository.UserRepository;
import com.fixlog.application.repository.WorkspaceMemberRepository;
import com.fixlog.application.repository.WorkspaceRepository;
import com.fixlog.application.service.AuditService;
import com.fixlog.application.service.DocumentPdfGenerator;
import com.fixlog.application.service.DocumentService;
import com.fixlog.application.service.DocumentTextExtractor;
import com.fixlog.application.service.FolderService;
import com.fixlog.application.service.LabelService;
import com.fixlog.application.service.PermissionEvaluator;
import com.fixlog.application.service.PermissionService;
import com.fixlog.application.service.SecurityPolicyService;
import com.fixlog.application.service.TrashService;
import com.fixlog.application.service.WorkspaceContext;
import com.fixlog.application.service.WorkspaceService;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.FolderEntity;
import com.fixlog.domain.model.LabelEntity;
import com.fixlog.domain.model.PermissionLevel;
import com.fixlog.domain.model.ResourceType;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.domain.model.WorkspaceEntity;
import com.fixlog.presentation.dto.request.DocumentCreateRequest;
import com.fixlog.presentation.dto.request.DocumentSaveRequest;
import com.fixlog.presentation.dto.request.FolderRequest;
import com.fixlog.presentation.dto.response.TrashItemDto;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 휴지통(FR-TRS-*)과 라벨(FR-LBL-*). */
@DataJpaTest
class TrashAndLabelTest {

    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired GroupRepository groupRepository;
    @Autowired GroupMemberRepository groupMemberRepository;
    @Autowired PermissionRepository permissionRepository;
    @Autowired SecurityPolicyRepository policyRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired DocumentRevisionRepository revisionRepository;
    @Autowired LabelRepository labelRepository;
    @Autowired DocumentLabelRepository documentLabelRepository;
    @Autowired FolderRepository folderRepository;
    @Autowired DocumentRepository documentRepository;

    private WorkspaceService workspaceService;
    private PermissionService permissionService;
    private DocumentService documentService;
    private FolderService folderService;
    private TrashService trashService;
    private LabelService labelService;

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
        trashService = new TrashService(documentRepository, folderRepository, revisionRepository,
                documentLabelRepository, permissionRepository, workspaceMemberRepository,
                workspaceContext, auditService);
        labelService = new LabelService(labelRepository, documentLabelRepository,
                documentRepository, workspaceContext, evaluator);

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

    private String newDocument(String title) {
        return documentService.create(new DocumentCreateRequest(null, title)).getDocumentId();
    }

    private DocumentSaveRequest saveRequest(String text) {
        return new DocumentSaveRequest("문서", JsonMapper.builder().build()
                .readTree("[{\"type\":\"paragraph\",\"data\":{\"text\":\"" + text + "\"}}]"));
    }

    // ---------- 휴지통 ----------

    @Test
    void 삭제한_문서가_휴지통에_들어간다() {
        String docId = newDocument("지울 문서");
        documentService.delete(docId);

        List<TrashItemDto> trash = trashService.list();

        assertEquals(1, trash.size());
        assertEquals(ResourceType.DOCUMENT, trash.get(0).resourceType());
        assertEquals("지울 문서", trash.get(0).name());
        assertEquals(author.getUserId().toString(), trash.get(0).deletedBy());
    }

    @Test
    void 휴지통_항목은_일반_목록에_나오지_않는다() {
        String docId = newDocument("지울 문서");
        newDocument("남는 문서");
        documentService.delete(docId);

        assertEquals(1, documentService.list(null,
                org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void 복원하면_다시_보인다() {
        String docId = newDocument("지울 문서");
        documentService.delete(docId);

        trashService.restore(ResourceType.DOCUMENT, docId);

        assertEquals("지울 문서", documentService.getDocument(docId).getTitle());
        assertTrue(trashService.list().isEmpty());
    }

    // 삭제된 폴더 안으로 되살리면 트리에서 보이지 않는 고아가 된다.
    @Test
    void 부모_폴더가_아직_휴지통이면_루트로_복원된다() {
        FolderEntity folder = folderService.createFolder(new FolderRequest(null, "폴더"));
        String docId = documentService.create(
                new DocumentCreateRequest(folder.getFolderId(), "문서")).getDocumentId();

        folderService.deleteFolder(folder.getFolderId());
        trashService.restore(ResourceType.DOCUMENT, docId);

        DocumentEntity restored = documentService.getDocument(docId);
        assertNull(restored.getFolderId(), "삭제된 폴더 안으로 되살아나면 안 된다");
    }

    @Test
    void 폴더를_지우면_안의_문서도_함께_휴지통으로_간다() {
        FolderEntity folder = folderService.createFolder(new FolderRequest(null, "폴더"));
        documentService.create(new DocumentCreateRequest(folder.getFolderId(), "안의 문서"));

        folderService.deleteFolder(folder.getFolderId());

        assertEquals(2, trashService.list().size(), "폴더와 문서가 모두 들어간다");
    }

    @Test
    void 남이_지운_항목은_휴지통에_보이지_않는다() {
        String docId = newDocument("author의 문서");
        documentService.delete(docId);

        loginAs(mate);
        assertTrue(trashService.list().isEmpty());
        assertEquals(Code.FORBIDDEN, assertThrows(BusinessException.class,
                () -> trashService.restore(ResourceType.DOCUMENT, docId)).getCode());
    }

    @Test
    void 관리자는_남이_지운_항목도_다룰_수_있다() {
        String docId = newDocument("author의 문서");
        documentService.delete(docId);

        loginAs(admin);
        assertEquals(1, trashService.list().size());
        trashService.restore(ResourceType.DOCUMENT, docId);
        assertTrue(trashService.list().isEmpty());
    }

    @Test
    void 영구_삭제하면_리비전과_라벨까지_사라진다() {
        String docId = newDocument("문서");
        labelService.attach(docId, "spring");
        documentService.saveContent(docId, saveRequest("내용"));
        documentService.delete(docId);

        trashService.purge(ResourceType.DOCUMENT, docId);

        assertTrue(documentRepository.findById(docId).isEmpty());
        assertTrue(revisionRepository.findByDocumentIdOrderByRevisionNoDesc(docId).isEmpty());
        assertTrue(documentLabelRepository.findByDocumentId(docId).isEmpty());
    }

    // ---------- 라벨 ----------

    @Test
    void 라벨을_붙이고_뗄_수_있다() {
        String docId = newDocument("문서");

        LabelEntity label = labelService.attach(docId, "spring");
        assertEquals(List.of("spring"), labelService.labelsOf(docId).stream()
                .map(LabelEntity::getLabelName).toList());

        labelService.detach(docId, label.getId());
        assertTrue(labelService.labelsOf(docId).isEmpty());
    }

    @Test
    void 같은_이름의_라벨은_워크스페이스에서_하나로_공유된다() {
        String first = newDocument("문서 1");
        String second = newDocument("문서 2");

        LabelEntity a = labelService.attach(first, "spring");
        LabelEntity b = labelService.attach(second, "spring");

        assertEquals(a.getId(), b.getId());
        assertEquals(1, labelService.labelsOfWorkspace().size());
    }

    @Test
    void 라벨로_문서를_찾을_수_있다() {
        String docId = newDocument("스프링 문서");
        newDocument("관계 없는 문서");
        LabelEntity label = labelService.attach(docId, "spring");

        List<DocumentEntity> found = labelService.documentsWith(label.getId());

        assertEquals(1, found.size());
        assertEquals("스프링 문서", found.get(0).getTitle());
    }

    // 라벨이 권한 우회 통로가 되면 안 된다.
    @Test
    void 라벨로_찾아도_권한_없는_문서는_빠진다() {
        String mine = newDocument("내 문서");
        LabelEntity label = labelService.attach(mine, "spring");

        loginAs(mate);
        String mateDoc = newDocument("mate 문서");
        labelService.attach(mateDoc, "spring");

        List<DocumentEntity> found = labelService.documentsWith(label.getId());

        assertEquals(List.of("mate 문서"), found.stream().map(DocumentEntity::getTitle).toList());
    }

    @Test
    void 읽기_권한만_있으면_라벨을_붙일_수_없다() {
        String docId = newDocument("문서");
        permissionService.shareWithEmail(ResourceType.DOCUMENT, docId,
                "mate@fixlog.dev", PermissionLevel.VIEWER, true);

        loginAs(mate);
        assertEquals(Code.FORBIDDEN, assertThrows(BusinessException.class,
                () -> labelService.attach(docId, "몰래")).getCode());
    }
}
