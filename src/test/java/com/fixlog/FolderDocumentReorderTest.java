package com.fixlog;

import com.fixlog.application.repository.DocumentHistoryRepository;
import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.application.repository.UserRepository;
import com.fixlog.application.repository.GroupMemberRepository;
import com.fixlog.application.repository.GroupRepository;
import com.fixlog.application.repository.PermissionRepository;
import com.fixlog.application.repository.WorkspaceMemberRepository;
import com.fixlog.application.repository.WorkspaceRepository;
import com.fixlog.application.service.DocumentHistoryService;
import com.fixlog.application.service.DocumentPdfGenerator;
import com.fixlog.application.service.DocumentService;
import com.fixlog.application.service.DocumentTextExtractor;
import com.fixlog.application.service.FolderService;
import com.fixlog.application.service.PermissionEvaluator;
import com.fixlog.application.service.WorkspaceContext;
import com.fixlog.application.service.WorkspaceService;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.FolderEntity;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.presentation.dto.request.DocumentCreateRequest;
import com.fixlog.presentation.dto.request.DocumentMoveRequest;
import com.fixlog.presentation.dto.request.FolderRequest;
import com.fixlog.presentation.dto.response.DocumentDto;
import com.fixlog.presentation.dto.response.FolderContentsDto;
import com.fixlog.presentation.dto.response.FolderDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class FolderDocumentReorderTest {

    @Autowired FolderRepository folderRepository;
    @Autowired DocumentRepository documentRepository;
    @Autowired DocumentHistoryRepository documentHistoryRepository;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired PermissionRepository permissionRepository;
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
                groupRepository, folderRepository, documentRepository, workspaceContext);
        folderService = new FolderService(folderRepository, documentRepository, workspaceContext, permissionEvaluator);
        documentService = new DocumentService(documentRepository, folderRepository,
                new DocumentTextExtractor(), new DocumentPdfGenerator(),
                new DocumentHistoryService(documentRepository, documentHistoryRepository, 50), event -> {}, workspaceContext, permissionEvaluator);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private UserEntity loginAsNewUser(String id) {
        UserEntity user = userRepository.save(new UserEntity(id, id + "@fixlog.dev"));
        workspaceService.ensurePersonalWorkspace(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
        return user;
    }

    private List<String> folderNames(FolderContentsDto contents) {
        return contents.folders().stream().map(FolderDto::folderName).toList();
    }

    private List<String> documentTitles(FolderContentsDto contents) {
        return contents.documents().stream().map(DocumentDto::title).toList();
    }

    @Test
    void 새폴더는_형제들_맨_뒤에_붙는다() {
        loginAsNewUser("owner");

        folderService.createFolder(new FolderRequest(null, "A"));
        folderService.createFolder(new FolderRequest(null, "B"));
        folderService.createFolder(new FolderRequest(null, "C"));

        // 생성 순서가 곧 표시 순서여야 한다. 모두 ordinal=0이면 순서가 보장되지 않는다.
        assertEquals(List.of("A", "B", "C"), folderNames(folderService.getRootContents()));
    }

    @Test
    void 폴더_재배치_후_조회순서가_바뀐다() {
        loginAsNewUser("owner");

        FolderEntity a = folderService.createFolder(new FolderRequest(null, "A"));
        FolderEntity b = folderService.createFolder(new FolderRequest(null, "B"));
        FolderEntity c = folderService.createFolder(new FolderRequest(null, "C"));

        folderService.reorderFolders(null, List.of(c.getFolderId(), a.getFolderId(), b.getFolderId()));

        assertEquals(List.of("C", "A", "B"), folderNames(folderService.getRootContents()));
    }

    @Test
    void 문서_재배치_후_조회순서가_바뀐다() {
        loginAsNewUser("owner");
        FolderEntity folder = folderService.createFolder(new FolderRequest(null, "폴더"));

        DocumentEntity d1 = documentService.create(new DocumentCreateRequest(folder.getFolderId(), "1번"));
        DocumentEntity d2 = documentService.create(new DocumentCreateRequest(folder.getFolderId(), "2번"));
        DocumentEntity d3 = documentService.create(new DocumentCreateRequest(folder.getFolderId(), "3번"));

        documentService.reorder(folder.getFolderId(),
                List.of(d3.getDocumentId(), d1.getDocumentId(), d2.getDocumentId()));

        assertEquals(List.of("3번", "1번", "2번"),
                documentTitles(folderService.getFolderContents(folder.getFolderId())));
    }

    @Test
    void 재배치는_문서의_수정시각을_건드리지_않는다() {
        loginAsNewUser("owner");
        FolderEntity folder = folderService.createFolder(new FolderRequest(null, "폴더"));

        DocumentEntity d1 = documentService.create(new DocumentCreateRequest(folder.getFolderId(), "1번"));
        DocumentEntity d2 = documentService.create(new DocumentCreateRequest(folder.getFolderId(), "2번"));
        Instant before = d1.getUpdateTime();

        documentService.reorder(folder.getFolderId(), List.of(d2.getDocumentId(), d1.getDocumentId()));

        // 문서 목록(GET /documents)은 updateTime 최신순이다. 순서만 바꿨는데 최근 수정 문서가 뒤바뀌면 안 된다.
        DocumentEntity reloaded = documentService.getDocument(d1.getDocumentId());
        assertEquals(before, reloaded.getUpdateTime());
    }

    @Test
    void 형제_일부만_보내면_거부한다() {
        loginAsNewUser("owner");

        FolderEntity a = folderService.createFolder(new FolderRequest(null, "A"));
        folderService.createFolder(new FolderRequest(null, "B"));

        // 일부만 재배치하면 순번에 중복이나 구멍이 생긴다.
        assertThrows(BusinessException.class,
                () -> folderService.reorderFolders(null, List.of(a.getFolderId())));
    }

    @Test
    void 중복된_아이디를_보내면_거부한다() {
        loginAsNewUser("owner");

        FolderEntity a = folderService.createFolder(new FolderRequest(null, "A"));
        folderService.createFolder(new FolderRequest(null, "B"));

        assertThrows(BusinessException.class,
                () -> folderService.reorderFolders(null, List.of(a.getFolderId(), a.getFolderId())));
    }

    @Test
    void 다른_부모에_속한_폴더가_섞이면_거부한다() {
        loginAsNewUser("owner");

        FolderEntity root = folderService.createFolder(new FolderRequest(null, "루트"));
        FolderEntity other = folderService.createFolder(new FolderRequest(null, "다른루트"));
        FolderEntity child = folderService.createFolder(new FolderRequest(root.getFolderId(), "자식"));

        assertThrows(BusinessException.class, () -> folderService.reorderFolders(
                root.getFolderId(), List.of(child.getFolderId(), other.getFolderId())));
    }

    @Test
    void 남의_폴더는_재배치할_수_없다() {
        loginAsNewUser("owner");
        FolderEntity mine = folderService.createFolder(new FolderRequest(null, "내폴더"));

        SecurityContextHolder.clearContext();
        loginAsNewUser("intruder");

        assertThrows(BusinessException.class,
                () -> folderService.reorderFolders(null, List.of(mine.getFolderId())));
    }

    @Test
    void 폴더를_이동하면_새_부모의_맨_뒤로_간다() {
        loginAsNewUser("owner");

        FolderEntity target = folderService.createFolder(new FolderRequest(null, "이동대상"));
        FolderEntity parent = folderService.createFolder(new FolderRequest(null, "새부모"));
        folderService.createFolder(new FolderRequest(parent.getFolderId(), "기존자식"));

        FolderEntity moved = folderService.moveFolder(target.getFolderId(), parent.getFolderId());

        // 새 부모에 이미 ordinal=0인 자식이 있으므로 겹치면 안 된다.
        assertEquals(1, moved.getOrdinal());
        assertEquals(List.of("기존자식", "이동대상"),
                folderNames(folderService.getFolderContents(parent.getFolderId())));
    }

    @Test
    void 문서를_이동하면_새_폴더의_맨_뒤로_간다() {
        loginAsNewUser("owner");

        FolderEntity folder = folderService.createFolder(new FolderRequest(null, "폴더"));
        documentService.create(new DocumentCreateRequest(folder.getFolderId(), "기존문서"));
        DocumentEntity moving = documentService.create(new DocumentCreateRequest(null, "루트문서"));

        DocumentEntity moved = documentService.move(moving.getDocumentId(),
                new DocumentMoveRequest(folder.getFolderId()));

        assertEquals(1, moved.getOrdinal());
        assertEquals(List.of("기존문서", "루트문서"),
                documentTitles(folderService.getFolderContents(folder.getFolderId())));
    }
}
