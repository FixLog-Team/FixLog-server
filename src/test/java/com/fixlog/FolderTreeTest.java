package com.fixlog;

import com.fixlog.application.repository.DocumentHistoryRepository;
import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.application.repository.UserRepository;
import com.fixlog.application.service.DocumentHistoryService;
import com.fixlog.application.service.DocumentPdfGenerator;
import com.fixlog.application.service.DocumentService;
import com.fixlog.application.service.DocumentTextExtractor;
import com.fixlog.application.service.FolderService;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.presentation.dto.request.DocumentCreateRequest;
import com.fixlog.presentation.dto.request.FolderRequest;
import com.fixlog.presentation.dto.response.FolderTreeDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class FolderTreeTest {

    @Autowired FolderRepository folderRepository;
    @Autowired DocumentRepository documentRepository;
    @Autowired DocumentHistoryRepository documentHistoryRepository;
    @Autowired UserRepository userRepository;

    private FolderService folderService;
    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        folderService = new FolderService(folderRepository, documentRepository);
        documentService = new DocumentService(documentRepository, folderRepository,
                new DocumentTextExtractor(), new DocumentPdfGenerator(),
                new DocumentHistoryService(documentRepository, documentHistoryRepository, 50), event -> {});
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void loginAsNewUser(String id) {
        UserEntity user = userRepository.save(new UserEntity(id, id + "@fixlog.dev"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private String createFolder(String parentId, String name) {
        return folderService.createFolder(new FolderRequest(parentId, name)).getFolderId();
    }

    private String createDocument(String folderId, String title) {
        return documentService.create(new DocumentCreateRequest(folderId, title)).getDocumentId();
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

        List<FolderTreeDto> tree = folderService.getFolderTree();

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

        List<FolderTreeDto> tree = folderService.getFolderTree();
        FolderTreeDto parentNode = findByName(tree, "Parent");

        // 하위 폴더의 문서까지 합산하면 3이 된다. 폴더를 열었을 때 보이는 개수와 일치해야 하므로 2다.
        assertEquals(2, parentNode.documentCount());
        assertEquals(1, parentNode.children().get(0).documentCount());
    }

    @Test
    void 문서가_없는_폴더의_documentCount는_0이다() {
        loginAsNewUser("empty-user");
        createFolder(null, "빈 폴더");

        assertEquals(0, folderService.getFolderTree().get(0).documentCount());
    }

    @Test
    void 삭제된_문서는_documentCount에서_빠진다() {
        loginAsNewUser("delete-user");
        String folder = createFolder(null, "Folder");
        createDocument(folder, "남는 문서");
        String removed = createDocument(folder, "지울 문서");

        documentService.delete(removed);

        assertEquals(1, folderService.getFolderTree().get(0).documentCount());
    }

    @Test
    void 삭제된_폴더는_트리에_나오지_않는다() {
        loginAsNewUser("delete-folder-user");
        createFolder(null, "남는 폴더");
        String removed = createFolder(null, "지울 폴더");

        folderService.deleteFolder(removed);

        List<FolderTreeDto> tree = folderService.getFolderTree();
        assertEquals(1, tree.size());
        assertEquals("남는 폴더", tree.get(0).folderName());
    }

    @Test
    void 형제_폴더는_ordinal_순서로_정렬된다() {
        loginAsNewUser("order-user");
        String a = createFolder(null, "A");
        String b = createFolder(null, "B");
        String c = createFolder(null, "C");

        folderService.reorderFolders(null, List.of(c, a, b));

        List<String> names = folderService.getFolderTree().stream().map(FolderTreeDto::folderName).toList();
        assertEquals(List.of("C", "A", "B"), names);
    }

    @Test
    void 다른_사용자의_폴더는_트리에_섞이지_않는다() {
        loginAsNewUser("owner-a");
        createFolder(null, "A의 폴더");

        loginAsNewUser("owner-b");
        createFolder(null, "B의 폴더");

        List<FolderTreeDto> tree = folderService.getFolderTree();
        assertEquals(1, tree.size());
        assertEquals("B의 폴더", tree.get(0).folderName());
    }

    @Test
    void 루트_문서는_어떤_폴더의_documentCount에도_잡히지_않는다() {
        loginAsNewUser("root-doc-user");
        createFolder(null, "Folder");
        createDocument(null, "루트 문서");

        assertEquals(0, folderService.getFolderTree().get(0).documentCount());
    }
}
