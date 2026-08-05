package com.fixlog;

import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.application.repository.UserRepository;
import com.fixlog.application.repository.GroupMemberRepository;
import com.fixlog.application.repository.GroupRepository;
import com.fixlog.application.repository.PermissionRepository;
import com.fixlog.application.repository.WorkspaceMemberRepository;
import com.fixlog.application.repository.WorkspaceRepository;
import com.fixlog.application.service.DocumentPdfGenerator;
import com.fixlog.application.service.DocumentService;
import com.fixlog.application.service.DocumentTextExtractor;
import com.fixlog.application.service.FolderService;
import com.fixlog.application.repository.AuditLogRepository;
import com.fixlog.application.repository.DocumentRevisionRepository;
import com.fixlog.application.service.AuditService;
import com.fixlog.application.service.PermissionEvaluator;
import com.fixlog.application.service.PermissionService;
import com.fixlog.application.service.WorkspaceContext;
import com.fixlog.application.service.WorkspaceService;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.presentation.dto.request.DocumentCreateRequest;
import com.fixlog.presentation.dto.request.FolderRequest;
import com.fixlog.presentation.dto.response.FolderContentsDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * FolderService에서 기존 테스트가 덮지 않은 경로를 고정하는 회귀 테스트 (FR-MIG-003).
 *
 * <p>트리 구성·재배치·이동·삭제 연쇄는 {@code FolderTreeTest},
 * {@code FolderDocumentReorderTest}, {@code WorkspaceRemovalFlowVerificationTest}가 이미 덮는다.
 * 여기서는 입력 검증, 이름 변경, 콘텐츠 조회를 채운다.
 */
@DataJpaTest
class FolderServiceCharacterizationTest {

    @Autowired FolderRepository folderRepository;
    @Autowired DocumentRepository documentRepository;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired PermissionRepository permissionRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired DocumentRevisionRepository revisionRepository;
    @Autowired GroupRepository groupRepository;
    @Autowired GroupMemberRepository groupMemberRepository;

    private FolderService folderService;
    private DocumentService documentService;
    private WorkspaceService workspaceService;

    @BeforeEach
    void setUp() {
        WorkspaceContext workspaceContext =
                new WorkspaceContext(workspaceRepository, workspaceMemberRepository);
        workspaceService = new WorkspaceService(
                workspaceRepository, workspaceMemberRepository, userRepository, workspaceContext);
        PermissionEvaluator permissionEvaluator = new PermissionEvaluator(
                permissionRepository, workspaceMemberRepository, groupMemberRepository,
                groupRepository, folderRepository, documentRepository, workspaceContext, new AuditService(auditLogRepository));
                PermissionService permissionService = new PermissionService(
                permissionRepository, workspaceMemberRepository, groupRepository,
                userRepository, permissionEvaluator, workspaceContext);
folderService = new FolderService(folderRepository, documentRepository, workspaceContext, permissionEvaluator, permissionService);
        documentService = new DocumentService(documentRepository, folderRepository,
                new DocumentTextExtractor(), new DocumentPdfGenerator(), event -> {}, workspaceContext, permissionEvaluator, permissionService, revisionRepository);
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

    private String createFolder(String parentId, String name) {
        return folderService.createFolder(new FolderRequest(parentId, name)).getFolderId();
    }

    // ---------- 생성 검증 ----------

    @Test
    void 폴더명이_없으면_거부한다() {
        loginAsNewUser("no-name-user");

        BusinessException e = assertThrows(BusinessException.class,
                () -> folderService.createFolder(new FolderRequest(null, null)));

        assertEquals(Code.INVALID_REQUEST, e.getCode());
    }

    @Test
    void 존재하지_않는_상위_폴더_아래에는_만들_수_없다() {
        loginAsNewUser("no-parent-user");

        BusinessException e = assertThrows(BusinessException.class,
                () -> folderService.createFolder(new FolderRequest("없는-폴더", "새 폴더")));

        assertEquals(Code.NOT_FOUND, e.getCode());
    }

    @Test
    void 남의_폴더_아래에는_만들_수_없다() {
        loginAsNewUser("owner");
        String folder = createFolder(null, "주인 폴더");

        loginAsNewUser("stranger");

        BusinessException e = assertThrows(BusinessException.class,
                () -> folderService.createFolder(new FolderRequest(folder, "끼워넣기")));

        assertEquals(Code.NOT_FOUND, e.getCode());
    }

    // ---------- 이름 변경 ----------

    @Test
    void 이름을_바꾸면_부모와_순번은_그대로다() {
        loginAsNewUser("rename-user");
        String parent = createFolder(null, "부모");
        String child = createFolder(parent, "이전 이름");

        var renamed = folderService.updateFolder(child, new FolderRequest(null, "새 이름"));

        assertEquals("새 이름", renamed.getFolderName());
        assertEquals(parent, renamed.getParentId(), "이름 변경은 부모를 건드리지 않는다");
        assertEquals(0, renamed.getOrdinal());
    }

    // 이름에 null이 오면 기존 이름을 유지한다. 부분 갱신 요청에서 이름이 빠져도
    // 폴더명이 비어 버리지 않게 하려는 동작이다 (FolderEntity.rename).
    @Test
    void 이름이_null이면_기존_이름을_유지한다() {
        loginAsNewUser("rename-null-user");
        String folder = createFolder(null, "그대로");

        assertEquals("그대로",
                folderService.updateFolder(folder, new FolderRequest(null, null)).getFolderName());
    }

    @Test
    void 남의_폴더는_조회도_이름_변경도_할_수_없다() {
        loginAsNewUser("owner");
        String folder = createFolder(null, "주인 폴더");

        loginAsNewUser("stranger");

        for (Runnable access : List.<Runnable>of(
                () -> folderService.getFolder(folder),
                () -> folderService.updateFolder(folder, new FolderRequest(null, "가로채기")),
                () -> folderService.deleteFolder(folder),
                () -> folderService.getFolderContents(folder))) {
            assertEquals(Code.NOT_FOUND,
                    assertThrows(BusinessException.class, access::run).getCode());
        }
    }

    // ---------- 콘텐츠 조회 ----------

    @Test
    void 루트_콘텐츠는_루트_폴더와_루트_문서만_담는다() {
        loginAsNewUser("root-contents-user");
        String folder = createFolder(null, "루트 폴더");
        createFolder(folder, "하위 폴더");
        documentService.create(new DocumentCreateRequest(null, "루트 문서"));
        documentService.create(new DocumentCreateRequest(folder, "폴더 안 문서"));

        FolderContentsDto contents = folderService.getRootContents();

        assertEquals(1, contents.folders().size());
        assertEquals("루트 폴더", contents.folders().get(0).folderName());
        assertEquals(1, contents.documents().size());
        assertEquals("루트 문서", contents.documents().get(0).title());
    }

    @Test
    void 폴더_콘텐츠는_직속_하위만_담는다() {
        loginAsNewUser("folder-contents-user");
        String parent = createFolder(null, "부모");
        String child = createFolder(parent, "자식 폴더");
        createFolder(child, "손자 폴더");
        documentService.create(new DocumentCreateRequest(parent, "부모 문서"));
        documentService.create(new DocumentCreateRequest(child, "자식 문서"));

        FolderContentsDto contents = folderService.getFolderContents(parent);

        assertEquals(1, contents.folders().size());
        assertEquals("자식 폴더", contents.folders().get(0).folderName());
        assertEquals(1, contents.documents().size());
        assertEquals("부모 문서", contents.documents().get(0).title());
    }

    @Test
    void 삭제된_항목은_콘텐츠에서_빠진다() {
        loginAsNewUser("contents-deleted-user");
        String parent = createFolder(null, "부모");
        String removedFolder = createFolder(parent, "지울 폴더");
        createFolder(parent, "남는 폴더");
        String removedDoc = documentService.create(new DocumentCreateRequest(parent, "지울 문서")).getDocumentId();
        documentService.create(new DocumentCreateRequest(parent, "남는 문서"));

        folderService.deleteFolder(removedFolder);
        documentService.delete(removedDoc);

        FolderContentsDto contents = folderService.getFolderContents(parent);

        assertEquals(List.of("남는 폴더"), contents.folders().stream().map(f -> f.folderName()).toList());
        assertEquals(List.of("남는 문서"), contents.documents().stream().map(d -> d.title()).toList());
    }

    @Test
    void 삭제된_폴더의_콘텐츠는_조회할_수_없다() {
        loginAsNewUser("deleted-folder-user");
        String folder = createFolder(null, "지울 폴더");
        folderService.deleteFolder(folder);

        assertEquals(Code.NOT_FOUND,
                assertThrows(BusinessException.class, () -> folderService.getFolderContents(folder)).getCode());
    }

    // ---------- 인증 ----------

    @Test
    void 인증_정보가_없으면_거부한다() {
        SecurityContextHolder.clearContext();

        assertEquals(Code.UNAUTHORIZED,
                assertThrows(BusinessException.class, folderService::getFolderTree).getCode());
    }
}
