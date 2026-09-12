package com.fixlog;

import com.fixlog.application.event.DocumentDeletedEvent;
import com.fixlog.application.event.DocumentSavedEvent;
import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.application.repository.UserRepository;
import com.fixlog.application.repository.GroupMemberRepository;
import com.fixlog.application.repository.GroupRepository;
import com.fixlog.application.repository.PermissionRepository;
import com.fixlog.application.repository.SecurityPolicyRepository;
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
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.presentation.dto.request.DocumentCreateRequest;
import com.fixlog.presentation.dto.request.DocumentMoveRequest;
import com.fixlog.presentation.dto.request.DocumentSaveRequest;
import com.fixlog.presentation.dto.request.DocumentTitleRequest;
import com.fixlog.presentation.dto.request.FolderRequest;
import com.fixlog.presentation.dto.response.DocumentSaveStateDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DocumentService의 동작을 고정하는 회귀 테스트 (FR-MIG-003).
 *
 * <p>소유자 결합 조회를 권한 판정으로 갈아엎을 때 깨지면 안 되는 것은 "누가 접근할 수 있는가"가
 * 아니라 <b>문서가 어떻게 만들어지고 저장되고 옮겨지는가</b>였고, 실제로 이 테스트들은
 * 전환 후에도 그대로 통과했다.
 *
 * <p>아래 격리 테스트는 이제 <b>워크스페이스 격리</b>를 검증한다. 사용자마다 개인 워크스페이스가
 * 경계이므로 남의 문서는 구성원이 아니라서 보이지 않는다. 같은 워크스페이스 안에서 권한이 없어
 * 막히는 경우(FORBIDDEN)는 {@code ServicePermissionFlowTest}가 덮는다.
 */
@DataJpaTest
class DocumentServiceCharacterizationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired DocumentRepository documentRepository;
    @Autowired FolderRepository folderRepository;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired PermissionRepository permissionRepository;
    @Autowired SecurityPolicyRepository policyRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired DocumentHistoryRepository documentHistoryRepository;
    @Autowired GroupRepository groupRepository;
    @Autowired GroupMemberRepository groupMemberRepository;
    @Autowired WorkspaceInvitationRepository invitationRepository;

    private DocumentService documentService;
    private FolderService folderService;
    private List<Object> publishedEvents;
    private WorkspaceService workspaceService;

    @BeforeEach
    void setUp() {
        publishedEvents = new ArrayList<>();
        WorkspaceContext workspaceContext =
                new WorkspaceContext(workspaceRepository, workspaceMemberRepository);
        workspaceService = new WorkspaceService(
                workspaceRepository, workspaceMemberRepository, userRepository, workspaceContext,
                folderRepository, documentRepository, permissionRepository, invitationRepository);
        PermissionEvaluator permissionEvaluator = new PermissionEvaluator(
                permissionRepository, workspaceMemberRepository, groupMemberRepository,
                groupRepository, folderRepository, documentRepository, workspaceContext, new AuditService(auditLogRepository), policyRepository);
                PermissionService permissionService = new PermissionService(
                permissionRepository, workspaceMemberRepository, groupRepository,
                userRepository, permissionEvaluator, workspaceContext);
        SecurityPolicyService securityPolicyService =
                new SecurityPolicyService(policyRepository, workspaceService);
folderService = new FolderService(folderRepository, documentRepository, workspaceContext, permissionEvaluator, permissionService);
        documentService = new DocumentService(documentRepository, folderRepository,
                new DocumentTextExtractor(), new DocumentPdfGenerator(),
                new DocumentHistoryService(documentRepository, documentHistoryRepository, 50),
                publishedEvents::add, workspaceContext, permissionEvaluator, permissionService, securityPolicyService);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void loginAsNewUser(String id) {
        UserEntity user = userRepository.save(new UserEntity(id, id + "@fixlog.dev"));
        workspaceService.ensurePersonalWorkspace(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private String createDocument(String folderId, String title) {
        return documentService.create(new DocumentCreateRequest(folderId, title)).getDocumentId();
    }

    private JsonNode blocks(String json) {
        return JSON.readTree(json);
    }

    private static final String PARAGRAPH =
            """
            [{"type":"paragraph","data":{"text":"JVM 힙 덤프를 떠서 확인했다"}}]
            """;

    // ---------- 생성 ----------

    @Test
    void 제목이_없으면_제목_없음으로_생성된다() {
        loginAsNewUser("create-user");

        assertEquals("제목 없음", documentService.create(new DocumentCreateRequest(null, null)).getTitle());
        assertEquals("제목 없음", documentService.create(new DocumentCreateRequest(null, "   ")).getTitle());
        assertEquals("회고", documentService.create(new DocumentCreateRequest(null, "회고")).getTitle());
    }

    @Test
    void 새_문서는_빈_블록으로_시작한다() {
        loginAsNewUser("empty-doc-user");

        DocumentEntity doc = documentService.create(new DocumentCreateRequest(null, "새 문서"));

        assertEquals("[]", doc.getBlocks());
        assertEquals("", doc.getPlainText());
        assertEquals(64, doc.getContentHash().length(), "SHA-256 hex는 64자다");
        assertEquals(Integer.valueOf(1), doc.getUsable());
    }

    @Test
    void 새_문서는_같은_폴더의_맨_뒤에_붙는다() {
        loginAsNewUser("ordinal-user");
        String folder = folderService.createFolder(new FolderRequest(null, "폴더")).getFolderId();

        assertEquals(0, documentService.create(new DocumentCreateRequest(folder, "첫째")).getOrdinal());
        assertEquals(1, documentService.create(new DocumentCreateRequest(folder, "둘째")).getOrdinal());
        // 루트는 별도의 순번 공간을 쓴다.
        assertEquals(0, documentService.create(new DocumentCreateRequest(null, "루트")).getOrdinal());
    }

    // ---------- 본문 저장 ----------

    @Test
    void 본문을_저장하면_평문이_추출되고_해시가_바뀐다() {
        loginAsNewUser("save-user");
        String id = createDocument(null, "원본 제목");
        String before = documentService.getDocument(id).getContentHash();

        DocumentEntity saved = documentService.saveContent(id, new DocumentSaveRequest("바뀐 제목", blocks(PARAGRAPH)));

        assertEquals("바뀐 제목", saved.getTitle());
        assertEquals("JVM 힙 덤프를 떠서 확인했다", saved.getPlainText());
        assertNotEquals(before, saved.getContentHash());
    }

    @Test
    void 본문_저장은_인덱싱용_이벤트를_발행한다() {
        loginAsNewUser("event-user");
        String id = createDocument(null, "문서");

        documentService.saveContent(id, new DocumentSaveRequest("문서", blocks(PARAGRAPH)));

        assertEquals(1, publishedEvents.size());
        assertEquals(id, assertInstanceOf(DocumentSavedEvent.class, publishedEvents.get(0))
                .document().getDocumentId());
    }

    @Test
    void 허용되지_않은_블록_타입은_저장을_거부한다() {
        loginAsNewUser("invalid-block-user");
        String id = createDocument(null, "문서");

        BusinessException e = assertThrows(BusinessException.class, () -> documentService.saveContent(
                id, new DocumentSaveRequest("문서", blocks("[{\"type\":\"script\",\"data\":{}}]"))));

        assertEquals(Code.INVALID_REQUEST, e.getCode());
        assertTrue(publishedEvents.isEmpty(), "저장이 거부되면 인덱싱 이벤트도 나가지 않아야 한다");
    }

    @Test
    void 저장_상태는_마지막_저장_시각과_해시를_돌려준다() {
        loginAsNewUser("state-user");
        String id = createDocument(null, "문서");
        DocumentEntity saved = documentService.saveContent(id, new DocumentSaveRequest("문서", blocks(PARAGRAPH)));

        DocumentSaveStateDto state = documentService.getSaveState(id);

        assertEquals(saved.getUpdateTime(), state.lastSavedAt());
        assertEquals(saved.getContentHash(), state.contentHash());
    }

    @Test
    void 제목만_바꾸면_본문은_그대로다() {
        loginAsNewUser("title-user");
        String id = createDocument(null, "이전 제목");
        documentService.saveContent(id, new DocumentSaveRequest("이전 제목", blocks(PARAGRAPH)));
        String blocksBefore = documentService.getDocument(id).getBlocks();

        DocumentEntity renamed = documentService.updateTitle(id, new DocumentTitleRequest("새 제목"));

        assertEquals("새 제목", renamed.getTitle());
        assertEquals(blocksBefore, renamed.getBlocks());
    }

    // ---------- 복제 ----------

    @Test
    void 복제본은_같은_폴더에_사본_표시를_달고_맨_뒤에_생긴다() {
        loginAsNewUser("duplicate-user");
        String folder = folderService.createFolder(new FolderRequest(null, "폴더")).getFolderId();
        String id = createDocument(folder, "원본");
        documentService.saveContent(id, new DocumentSaveRequest("원본", blocks(PARAGRAPH)));

        DocumentEntity copy = documentService.duplicate(id);

        assertNotEquals(id, copy.getDocumentId());
        assertEquals("원본 (1)", copy.getTitle());
        assertEquals(folder, copy.getFolderId());
        assertEquals(1, copy.getOrdinal());
        assertEquals(documentService.getDocument(id).getBlocks(), copy.getBlocks());
    }

    // ---------- 삭제 ----------

    @Test
    void 삭제된_문서는_조회되지_않고_삭제_이벤트가_나간다() {
        loginAsNewUser("delete-user");
        String id = createDocument(null, "지울 문서");
        publishedEvents.clear();

        documentService.delete(id);

        BusinessException e = assertThrows(BusinessException.class, () -> documentService.getDocument(id));
        assertEquals(Code.NOT_FOUND, e.getCode());
        assertEquals(id, assertInstanceOf(DocumentDeletedEvent.class, publishedEvents.get(0)).documentId());
    }

    @Test
    void 삭제는_행을_지우지_않고_usable만_내린다() {
        loginAsNewUser("soft-delete-user");
        String id = createDocument(null, "지울 문서");

        documentService.delete(id);

        assertEquals(Integer.valueOf(0), documentRepository.findById(id).orElseThrow().getUsable());
    }

    // ---------- 이동 ----------

    @Test
    void 문서를_이동하면_대상_폴더의_맨_뒤로_간다() {
        loginAsNewUser("move-user");
        String source = folderService.createFolder(new FolderRequest(null, "출발")).getFolderId();
        String target = folderService.createFolder(new FolderRequest(null, "도착")).getFolderId();
        createDocument(target, "이미 있던 문서");
        String moving = createDocument(source, "옮길 문서");

        DocumentEntity moved = documentService.move(moving, new DocumentMoveRequest(target));

        assertEquals(target, moved.getFolderId());
        assertEquals(1, moved.getOrdinal());
    }

    @Test
    void 문서를_루트로_이동할_수_있다() {
        loginAsNewUser("move-root-user");
        String folder = folderService.createFolder(new FolderRequest(null, "폴더")).getFolderId();
        String id = createDocument(folder, "문서");

        assertNull(documentService.move(id, new DocumentMoveRequest(null)).getFolderId());
    }

    @Test
    void 존재하지_않는_폴더로는_이동할_수_없다() {
        loginAsNewUser("move-invalid-user");
        String id = createDocument(null, "문서");

        BusinessException e = assertThrows(BusinessException.class,
                () -> documentService.move(id, new DocumentMoveRequest("없는-폴더")));

        assertEquals(Code.NOT_FOUND, e.getCode());
    }

    // ---------- 목록 ----------

    @Test
    void 목록은_폴더로_좁히거나_전체를_돌려준다() {
        loginAsNewUser("list-user");
        String folder = folderService.createFolder(new FolderRequest(null, "폴더")).getFolderId();
        createDocument(folder, "폴더 문서");
        createDocument(null, "루트 문서");

        assertEquals(1, documentService.list(folder, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(2, documentService.list(null, PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void 삭제된_문서는_목록에서_빠진다() {
        loginAsNewUser("list-deleted-user");
        createDocument(null, "남는 문서");
        documentService.delete(createDocument(null, "지울 문서"));

        Page<DocumentEntity> page = documentService.list(null, PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals("남는 문서", page.getContent().get(0).getTitle());
    }

    // ---------- 다운로드 ----------

    @Test
    void PDF_다운로드는_제목과_내용을_함께_돌려준다() {
        loginAsNewUser("pdf-user");
        String id = createDocument(null, "회고 문서");
        documentService.saveContent(id, new DocumentSaveRequest("회고 문서", blocks(PARAGRAPH)));

        DocumentService.PdfResult result = documentService.downloadPdfResult(id);

        assertEquals("회고 문서", result.title());
        assertFalse(result.bytes().length == 0, "PDF 바이트가 비어 있으면 안 된다");
    }

    // ---------- 워크스페이스 격리 ----------

    // 다른 사용자의 개인 워크스페이스에 있는 문서다. 권한 부족(FORBIDDEN)이 아니라
    // 구성원이 아니라서 존재 자체가 드러나지 않는다 (FR-PRM-008).
    @Test
    void 남의_문서는_존재하지_않는_것으로_취급된다() {
        loginAsNewUser("owner");
        String id = createDocument(null, "주인 문서");

        loginAsNewUser("stranger");

        for (Runnable access : List.<Runnable>of(
                () -> documentService.getDocument(id),
                () -> documentService.updateTitle(id, new DocumentTitleRequest("가로채기")),
                () -> documentService.delete(id),
                () -> documentService.duplicate(id),
                () -> documentService.move(id, new DocumentMoveRequest(null)))) {
            BusinessException e = assertThrows(BusinessException.class, access::run);
            assertEquals(Code.NOT_FOUND, e.getCode());
        }
    }

    @Test
    void 인증_정보가_없으면_거부한다() {
        SecurityContextHolder.clearContext();

        BusinessException e = assertThrows(BusinessException.class,
                () -> documentService.create(new DocumentCreateRequest(null, "문서")));

        assertEquals(Code.UNAUTHORIZED, e.getCode());
    }
}
