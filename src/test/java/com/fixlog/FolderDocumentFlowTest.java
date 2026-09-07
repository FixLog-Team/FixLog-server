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
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.FolderEntity;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.presentation.dto.request.DocumentCreateRequest;
import com.fixlog.presentation.dto.request.DocumentMoveRequest;
import com.fixlog.presentation.dto.request.FolderRequest;
import com.fixlog.presentation.dto.response.FolderContentsDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class FolderDocumentFlowTest {

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

    /** 권한 판정은 워크스페이스 루트에서 끝나므로, 로그인 사용자에게는 개인 워크스페이스가 있어야 한다. */
    private void loginAs(UserEntity user) {
        fixture.workspaceService.ensurePersonalWorkspace(user);
        PermissionFixture.login(user);
    }

    @Test
    void 폴더생성_문서생성_목록_이동_소유권격리_전체흐름() {
        UserEntity owner = userRepository.save(new UserEntity("owner", "owner@fixlog.dev"));
        loginAs(owner);
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updateTime"));

        // 1) 폴더 생성 (workspaceId 생략 → 개인 워크스페이스)
        FolderEntity folder = folderService.createFolder(null, new FolderRequest(null, "트러블슈팅"));
        assertNotNull(folder.getFolderId());
        assertEquals("트러블슈팅", folder.getFolderName());
        assertEquals(owner.getUserId().toString(), folder.getCreateUser());
        System.out.println("[1] 폴더 생성 OK: " + folder.getFolderId());

        // 2) 폴더 안에 문서 생성
        DocumentEntity doc = documentService.create(
                null, new DocumentCreateRequest(folder.getFolderId(), "DB 커넥션 풀 고갈"));
        assertNotNull(doc.getDocumentId());
        assertEquals(folder.getFolderId(), doc.getFolderId());
        assertEquals("[]", doc.getBlocks());
        System.out.println("[2] 문서 생성 OK: " + doc.getDocumentId());

        // 3) 문서 목록 (폴더 필터) — 파생 쿼리 + 페이지네이션
        Page<DocumentEntity> byFolder = documentService.list(null, folder.getFolderId(), pageable);
        assertEquals(1, byFolder.getTotalElements());
        assertEquals(doc.getDocumentId(), byFolder.getContent().get(0).getDocumentId());
        System.out.println("[3] 폴더별 문서목록 OK: total=" + byFolder.getTotalElements());

        // 4) 폴더 콘텐츠에 문서가 포함되는지
        FolderContentsDto contents = folderService.getFolderContents(null, folder.getFolderId());
        assertEquals(1, contents.documents().size());
        assertEquals(doc.getDocumentId(), contents.documents().get(0).documentId());
        System.out.println("[4] 폴더 콘텐츠 OK: docs=" + contents.documents().size());

        // 5) 문서를 루트(folderId=null)로 이동
        DocumentEntity moved = documentService.move(doc.getDocumentId(), new DocumentMoveRequest(null));
        assertNull(moved.getFolderId());
        System.out.println("[5] 문서 루트 이동 OK");

        // 6) 루트 콘텐츠 / 전체 목록에 이동된 문서가 보이는지 (findByFolderIdIsNull...)
        FolderContentsDto root = folderService.getRootContents(null);
        assertTrue(root.documents().stream()
                .anyMatch(d -> d.documentId().equals(doc.getDocumentId())));
        assertEquals(1, documentService.list(null, null, pageable).getTotalElements());
        System.out.println("[6] 루트 목록 노출 OK: rootDocs=" + root.documents().size());

        // 7) 격리 — 다른 워크스페이스 사용자는 이 문서에 접근 불가(비멤버는 판정 트리에 진입하지 못한다)
        UserEntity intruder = userRepository.save(new UserEntity("intruder", "intruder@fixlog.dev"));
        loginAs(intruder);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentService.getDocument(doc.getDocumentId()));
        System.out.println("[7] 워크스페이스 격리 OK: " + ex.getMessage());
    }

    @Test
    void 폴더삭제_하위폴더와문서까지_캐스케이드() {
        UserEntity owner = userRepository.save(new UserEntity("cascade", "cascade@fixlog.dev"));
        loginAs(owner);
        String workspaceId = fixture.personalWorkspaceId(owner);

        FolderEntity parent = folderService.createFolder(null, new FolderRequest(null, "부모"));
        FolderEntity child = folderService.createFolder(null, new FolderRequest(parent.getFolderId(), "자식"));
        DocumentEntity docParent = documentService.create(null, new DocumentCreateRequest(parent.getFolderId(), "부모문서"));
        DocumentEntity docChild = documentService.create(null, new DocumentCreateRequest(child.getFolderId(), "자식문서"));

        folderService.deleteFolder(parent.getFolderId());

        // 루트 콘텐츠가 완전히 비어야 하고, 하위 폴더/문서 모두 usable=0
        FolderContentsDto root = folderService.getRootContents(null);
        assertTrue(root.folders().isEmpty(), "루트 폴더가 비어야 함");
        assertTrue(root.documents().isEmpty(), "루트 문서가 비어야 함");
        assertEquals(0, folderRepository.findByFolderId(child.getFolderId()).orElseThrow().getUsable());
        assertEquals(0, documentRepository
                .findByFolderIdAndWorkspaceIdAndUsableOrderByOrdinalAscCreateTimeAsc(
                        parent.getFolderId(), workspaceId, 1).size());
        assertEquals(0, documentRepository
                .findByFolderIdAndWorkspaceIdAndUsableOrderByOrdinalAscCreateTimeAsc(
                        child.getFolderId(), workspaceId, 1).size());
        assertNotNull(docParent);
        assertNotNull(docChild);
        System.out.println("[C] 폴더 삭제 캐스케이드 OK (하위 폴더+문서 전부 삭제)");
    }

    @Test
    void 폴더_루트로이동_가능() {
        UserEntity owner = userRepository.save(new UserEntity("rootmove", "rootmove@fixlog.dev"));
        loginAs(owner);
        FolderEntity a = folderService.createFolder(null, new FolderRequest(null, "A"));
        FolderEntity b = folderService.createFolder(null, new FolderRequest(a.getFolderId(), "B"));

        FolderEntity moved = folderService.moveFolder(b.getFolderId(), null);

        assertNull(moved.getParentId(), "루트로 이동하면 parentId=null");
        assertTrue(folderService.getRootContents(null).folders().stream()
                .anyMatch(f -> f.folderId().equals(b.getFolderId())), "루트 콘텐츠에 B가 보여야 함");
        System.out.println("[R] 폴더 루트 이동 OK");
    }

    @Test
    void 폴더_순환참조이동_거부() {
        UserEntity owner = userRepository.save(new UserEntity("cycle", "cycle@fixlog.dev"));
        loginAs(owner);
        FolderEntity a = folderService.createFolder(null, new FolderRequest(null, "A"));
        FolderEntity b = folderService.createFolder(null, new FolderRequest(a.getFolderId(), "B"));

        // A를 자신의 하위 B로 이동 → 거부
        assertThrows(BusinessException.class, () -> folderService.moveFolder(a.getFolderId(), b.getFolderId()));
        // A를 자기 자신으로 이동 → 거부
        assertThrows(BusinessException.class, () -> folderService.moveFolder(a.getFolderId(), a.getFolderId()));
        System.out.println("[X] 폴더 순환참조 이동 거부 OK");
    }
}
