package com.fixlog;

import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.application.repository.GroupMemberRepository;
import com.fixlog.application.repository.GroupRepository;
import com.fixlog.application.repository.PermissionRepository;
import com.fixlog.application.repository.UserRepository;
import com.fixlog.application.repository.WorkspaceMemberRepository;
import com.fixlog.application.repository.WorkspaceRepository;
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
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.FolderEntity;
import com.fixlog.domain.model.PermissionEntity;
import com.fixlog.domain.model.PermissionLevel;
import com.fixlog.domain.model.PrincipalType;
import com.fixlog.domain.model.ResourceType;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.domain.model.WorkspaceEntity;
import com.fixlog.presentation.dto.request.DocumentCreateRequest;
import com.fixlog.presentation.dto.request.DocumentSaveRequest;
import com.fixlog.presentation.dto.request.FolderRequest;
import com.fixlog.presentation.dto.response.FolderTreeDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
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

/**
 * 문서·폴더 서비스가 실제로 권한 판정 위에서 도는지 확인한다 (M3 체크포인트).
 *
 * <p>{@code PermissionEvaluatorTest}가 판정 규칙 자체를 검증한다면, 여기서는 서비스가 그 판정을
 * 통과시키는지, 목록이 권한으로 걸러지는지를 본다. 판정이 배선되지 않아도 판정기 테스트는
 * 그대로 통과하므로 이 계층이 따로 필요하다.
 */
@DataJpaTest
class ServicePermissionFlowTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired GroupRepository groupRepository;
    @Autowired GroupMemberRepository groupMemberRepository;
    @Autowired PermissionRepository permissionRepository;
    @Autowired FolderRepository folderRepository;
    @Autowired DocumentRepository documentRepository;

    private WorkspaceService workspaceService;
    private DocumentService documentService;
    private FolderService folderService;

    private UserEntity admin;
    private UserEntity member;
    private WorkspaceEntity workspace;

    @BeforeEach
    void setUp() {
        WorkspaceContext workspaceContext =
                new WorkspaceContext(workspaceRepository, workspaceMemberRepository);
        workspaceService = new WorkspaceService(
                workspaceRepository, workspaceMemberRepository, userRepository, workspaceContext);
        PermissionEvaluator evaluator = new PermissionEvaluator(permissionRepository,
                workspaceMemberRepository, groupMemberRepository, groupRepository,
                folderRepository, documentRepository, workspaceContext);
                PermissionService permissionService = new PermissionService(
                permissionRepository, workspaceMemberRepository, groupRepository,
                userRepository, evaluator, workspaceContext);
folderService = new FolderService(folderRepository, documentRepository, workspaceContext, evaluator, permissionService);
        documentService = new DocumentService(documentRepository, folderRepository,
                new DocumentTextExtractor(), new DocumentPdfGenerator(), event -> {},
                workspaceContext, evaluator, permissionService);

        admin = signUp("admin");
        loginAs(admin);
        workspace = workspaceService.create("팀");
        member = signUp("member");
        workspaceService.invite(workspace.getWorkspaceId(), "member@fixlog.dev");

        useWorkspace(workspace.getWorkspaceId());
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

    /** 클라이언트가 X-Workspace-Id로 현재 워크스페이스를 지정하는 것과 같다. */
    private void useWorkspace(UUID workspaceId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(WorkspaceContext.HEADER_NAME, workspaceId.toString());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void grant(ResourceType type, String id, UUID userId,
                       PermissionLevel level, boolean canDownload) {
        permissionRepository.save(new PermissionEntity(workspace.getWorkspaceId(),
                PrincipalType.USER, userId, type, id, level, canDownload, admin.getUserId()));
    }

    // ---------- 헤더로 지정한 워크스페이스에서 만들어진다 ----------

    @Test
    void 헤더로_지정한_워크스페이스에_문서가_생긴다() {
        loginAs(admin);

        DocumentEntity doc = documentService.create(new DocumentCreateRequest(null, "팀 문서"));

        assertEquals(workspace.getWorkspaceId(), doc.getWorkspaceId());
    }

    @Test
    void 속하지_않은_워크스페이스를_헤더로_지정하면_거부된다() {
        UserEntity stranger = signUp("stranger");
        loginAs(stranger);
        useWorkspace(workspace.getWorkspaceId());

        BusinessException e = assertThrows(BusinessException.class,
                () -> documentService.create(new DocumentCreateRequest(null, "끼어들기")));

        assertEquals(Code.NOT_FOUND, e.getCode());
    }

    // ---------- 단건 접근 ----------

    @Test
    void 권한이_없는_구성원은_문서를_열_수_없다() {
        loginAs(admin);
        String docId = documentService.create(new DocumentCreateRequest(null, "비밀 문서")).getDocumentId();

        loginAs(member);
        BusinessException e = assertThrows(BusinessException.class,
                () -> documentService.getDocument(docId));

        assertEquals(Code.FORBIDDEN, e.getCode(), "구성원이지만 권한이 없으면 FORBIDDEN이다");
    }

    @Test
    void VIEWER는_문서를_저장할_수_없다() {
        loginAs(admin);
        String docId = documentService.create(new DocumentCreateRequest(null, "읽기 전용")).getDocumentId();
        grant(ResourceType.DOCUMENT, docId, member.getUserId(), PermissionLevel.VIEWER, true);

        loginAs(member);
        assertEquals("읽기 전용", documentService.getDocument(docId).getTitle());
        assertEquals(Code.FORBIDDEN, assertThrows(BusinessException.class,
                () -> documentService.saveContent(docId, new DocumentSaveRequest("고치기", JSON.readTree("[]"))))
                .getCode());
    }

    @Test
    void 다운로드_플래그가_없으면_문서를_내려받을_수_없다() {
        loginAs(admin);
        String docId = documentService.create(new DocumentCreateRequest(null, "반출 금지")).getDocumentId();
        grant(ResourceType.DOCUMENT, docId, member.getUserId(), PermissionLevel.EDITOR, false);

        loginAs(member);
        assertEquals(Code.FORBIDDEN, assertThrows(BusinessException.class,
                () -> documentService.downloadPdfResult(docId)).getCode());
    }

    // ---------- 목록 ----------

    @Test
    void 목록에는_볼_수_있는_문서만_나온다() {
        loginAs(admin);
        String visible = documentService.create(new DocumentCreateRequest(null, "공유된 문서")).getDocumentId();
        documentService.create(new DocumentCreateRequest(null, "안 준 문서"));
        grant(ResourceType.DOCUMENT, visible, member.getUserId(), PermissionLevel.VIEWER, true);

        loginAs(member);
        var page = documentService.list(null, PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements(), "권한 없는 문서는 개수에도 잡히지 않아야 한다");
        assertEquals("공유된 문서", page.getContent().get(0).getTitle());
    }

    @Test
    void 관리자는_목록에서_전부_본다() {
        loginAs(admin);
        documentService.create(new DocumentCreateRequest(null, "문서 1"));
        documentService.create(new DocumentCreateRequest(null, "문서 2"));

        assertEquals(2, documentService.list(null, PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void 폴더_트리에는_볼_수_있는_폴더만_나온다() {
        loginAs(admin);
        FolderEntity shared = folderService.createFolder(new FolderRequest(null, "공유 폴더"));
        folderService.createFolder(new FolderRequest(null, "비공개 폴더"));
        grant(ResourceType.FOLDER, shared.getFolderId(), member.getUserId(), PermissionLevel.VIEWER, true);

        loginAs(member);
        List<FolderTreeDto> tree = folderService.getFolderTree();

        assertEquals(1, tree.size());
        assertEquals("공유 폴더", tree.get(0).folderName());
    }

    @Test
    void 폴더_권한은_그_안의_문서까지_열어준다() {
        loginAs(admin);
        FolderEntity folder = folderService.createFolder(new FolderRequest(null, "공유 폴더"));
        String docId = documentService.create(
                new DocumentCreateRequest(folder.getFolderId(), "폴더 안 문서")).getDocumentId();
        grant(ResourceType.FOLDER, folder.getFolderId(), member.getUserId(), PermissionLevel.EDITOR, true);

        loginAs(member);

        assertEquals("폴더 안 문서", documentService.getDocument(docId).getTitle());
        assertTrue(folderService.getFolderContents(folder.getFolderId()).documents().stream()
                .anyMatch(d -> d.documentId().equals(docId)));
    }

    // 만든 사람이 자기 문서를 못 여는 상태였다. 생성 시 소유 권한이 부여되지 않으면
    // 일반 구성원은 문서를 만든 즉시 접근을 잃는다.
    @Test
    void 구성원이_만든_문서는_본인이_열_수_있다() {
        loginAs(member);
        String docId = documentService.create(new DocumentCreateRequest(null, "내가 만든 문서")).getDocumentId();

        assertEquals("내가 만든 문서", documentService.getDocument(docId).getTitle());
    }

    @Test
    void 권한_없는_폴더_안에는_문서를_만들_수_없다() {
        loginAs(admin);
        FolderEntity folder = folderService.createFolder(new FolderRequest(null, "남의 폴더"));

        loginAs(member);
        assertEquals(Code.FORBIDDEN, assertThrows(BusinessException.class,
                () -> documentService.create(new DocumentCreateRequest(folder.getFolderId(), "끼워넣기")))
                .getCode());
    }
}
