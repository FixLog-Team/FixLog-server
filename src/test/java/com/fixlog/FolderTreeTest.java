package com.fixlog;

import com.fixlog.application.repository.DocumentHistoryRepository;
import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.application.repository.PermissionOverrideRepository;
import com.fixlog.application.repository.UserRepository;
import com.fixlog.application.repository.WorkspaceMemberRepository;
import com.fixlog.application.repository.WorkspaceRepository;
import com.fixlog.application.service.DocumentHistoryService;
import com.fixlog.application.service.DocumentPdfGenerator;
import com.fixlog.application.service.DocumentService;
import com.fixlog.application.service.DocumentTextExtractor;
import com.fixlog.application.service.FolderService;
import com.fixlog.domain.model.AccessEffect;
import com.fixlog.domain.model.BaseAccess;
import com.fixlog.domain.model.NodeType;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.domain.model.WorkspaceRole;
import com.fixlog.presentation.dto.request.DocumentCreateRequest;
import com.fixlog.presentation.dto.request.FolderRequest;
import com.fixlog.presentation.dto.response.FolderTreeDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class FolderTreeTest {

    @Autowired FolderRepository folderRepository;
    @Autowired DocumentRepository documentRepository;
    @Autowired DocumentHistoryRepository documentHistoryRepository;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository memberRepository;
    @Autowired PermissionOverrideRepository overrideRepository;

    private PermissionFixture fixture;

    private FolderService folderService;
    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        fixture = new PermissionFixture(userRepository, workspaceRepository, memberRepository,
                overrideRepository, folderRepository, documentRepository);
        folderService = new FolderService(folderRepository, documentRepository, fixture.permissionResolver);
        documentService = new DocumentService(documentRepository, folderRepository,
                new DocumentTextExtractor(), new DocumentPdfGenerator(),
                new DocumentHistoryService(documentRepository, documentHistoryRepository, fixture.permissionResolver, 50), fixture.permissionResolver, event -> {});
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void loginAsNewUser(String id) {
        fixture.loginAsNewUser(id);
    }

    private String createFolder(String parentId, String name) {
        return folderService.createFolder(null, new FolderRequest(parentId, name)).getFolderId();
    }

    private String createDocument(String folderId, String title) {
        return documentService.create(null, new DocumentCreateRequest(folderId, title)).getDocumentId();
    }

    private FolderTreeDto findByName(List<FolderTreeDto> nodes, String name) {
        return nodes.stream().filter(n -> n.folderName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void 트리는_부모_자식_관계를_중첩해서_돌려준다() {
        loginAsNewUser("tree-user");
        String engineering = createFolder(null, "Engineering");
        String backend = createFolder(engineering, "Backend");
        createFolder(backend, "Spring");

        List<FolderTreeDto> tree = folderService.getFolderTree(null);

        assertEquals(1, tree.size(), "루트에는 Engineering 하나만 있어야 한다");
        FolderTreeDto root = tree.get(0);
        assertEquals("Engineering", root.folderName());
        assertNull(root.parentId());

        FolderTreeDto child = root.children().get(0);
        assertEquals("Backend", child.folderName());
        assertEquals(engineering, child.parentId());

        FolderTreeDto grandChild = child.children().get(0);
        assertEquals("Spring", grandChild.folderName());
        assertEquals(backend, grandChild.parentId());
        assertTrue(grandChild.children().isEmpty());
    }

    @Test
    void documentCount는_직속_문서만_센다() {
        loginAsNewUser("count-user");
        String parent = createFolder(null, "Parent");
        String child = createFolder(parent, "Child");

        createDocument(parent, "부모 문서 1");
        createDocument(parent, "부모 문서 2");
        createDocument(child, "자식 문서");

        List<FolderTreeDto> tree = folderService.getFolderTree(null);
        FolderTreeDto parentNode = findByName(tree, "Parent");

        // 하위 폴더의 문서까지 합산하면 3이 된다. 폴더를 열었을 때 보이는 개수와 일치해야 하므로 2다.
        assertEquals(2, parentNode.documentCount());
        assertEquals(1, parentNode.children().get(0).documentCount());
    }

    @Test
    void 문서가_없는_폴더의_documentCount는_0이다() {
        loginAsNewUser("empty-user");
        createFolder(null, "빈 폴더");

        assertEquals(0, folderService.getFolderTree(null).get(0).documentCount());
    }

    @Test
    void 삭제된_문서는_documentCount에서_빠진다() {
        loginAsNewUser("delete-user");
        String folder = createFolder(null, "Folder");
        createDocument(folder, "남는 문서");
        String removed = createDocument(folder, "지울 문서");

        documentService.delete(removed);

        assertEquals(1, folderService.getFolderTree(null).get(0).documentCount());
    }

    @Test
    void 삭제된_폴더는_트리에_나오지_않는다() {
        loginAsNewUser("delete-folder-user");
        createFolder(null, "남는 폴더");
        String removed = createFolder(null, "지울 폴더");

        folderService.deleteFolder(removed);

        List<FolderTreeDto> tree = folderService.getFolderTree(null);
        assertEquals(1, tree.size());
        assertEquals("남는 폴더", tree.get(0).folderName());
    }

    @Test
    void 형제_폴더는_ordinal_순서로_정렬된다() {
        loginAsNewUser("order-user");
        String a = createFolder(null, "A");
        String b = createFolder(null, "B");
        String c = createFolder(null, "C");

        folderService.reorderFolders(null, null, List.of(c, a, b));

        List<String> names = folderService.getFolderTree(null).stream().map(FolderTreeDto::folderName).toList();
        assertEquals(List.of("C", "A", "B"), names);
    }

    @Test
    void 다른_사용자의_폴더는_트리에_섞이지_않는다() {
        loginAsNewUser("owner-a");
        createFolder(null, "A의 폴더");

        loginAsNewUser("owner-b");
        createFolder(null, "B의 폴더");

        List<FolderTreeDto> tree = folderService.getFolderTree(null);
        assertEquals(1, tree.size());
        assertEquals("B의 폴더", tree.get(0).folderName());
    }

    @Test
    void 루트_문서는_어떤_폴더의_documentCount에도_잡히지_않는다() {
        loginAsNewUser("root-doc-user");
        createFolder(null, "Folder");
        createDocument(null, "루트 문서");

        assertEquals(0, folderService.getFolderTree(null).get(0).documentCount());
    }

    @Test
    void 접근_불가_폴더는_이름도_경로도_노출되지_않고_자식이_루트로_올라온다() {
        UserEntity owner = fixture.loginAsNewUser("hoist-owner");
        String workspaceId = fixture.personalWorkspaceId(owner);
        String secret = createFolder(null, "2025 인수합병");
        String shared = createFolder(secret, "공유된 하위폴더");
        createDocument(shared, "공유 문서");

        // 상위 폴더만 차단하고, 하위 폴더는 개별 허용으로 열어 준다.
        UserEntity member = fixture.addMember(workspaceId, "hoist-member", WorkspaceRole.MEMBER);
        fixture.setFolderBaseAccess(secret, BaseAccess.DENY);
        fixture.putOverride(workspaceId, NodeType.FOLDER, shared, member, AccessEffect.ALLOW);

        PermissionFixture.login(member);
        List<FolderTreeDto> tree = folderService.getFolderTree(workspaceId);

        // 폴더명 자체가 정보이므로 차단된 폴더는 트리에서 완전히 사라진다.
        assertEquals(1, tree.size(), "차단된 상위 폴더는 트리에 나타나지 않아야 한다");
        FolderTreeDto hoisted = tree.get(0);
        assertEquals("공유된 하위폴더", hoisted.folderName());
        assertNull(hoisted.parentId(), "숨겨진 조상의 ID조차 내려보내지 않는다");
        assertEquals(1, hoisted.documentCount());

        // 소유자에게는 원래 구조가 그대로 보인다.
        PermissionFixture.login(owner);
        List<FolderTreeDto> ownerTree = folderService.getFolderTree(workspaceId);
        assertEquals(1, ownerTree.size());
        assertEquals("2025 인수합병", ownerTree.get(0).folderName());
        assertEquals(secret, ownerTree.get(0).children().get(0).parentId());
    }
}
