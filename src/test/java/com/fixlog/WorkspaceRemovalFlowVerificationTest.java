package com.fixlog;

import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.application.repository.UserRepository;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class WorkspaceRemovalFlowVerificationTest {

    @Autowired FolderRepository folderRepository;
    @Autowired DocumentRepository documentRepository;
    @Autowired UserRepository userRepository;

    private FolderService folderService;
    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        folderService = new FolderService(folderRepository, documentRepository);
        documentService = new DocumentService(documentRepository, folderRepository,
                new DocumentTextExtractor(), new DocumentPdfGenerator());
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(UserEntity user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @Test
    void 폴더생성_문서생성_목록_이동_소유권격리_전체흐름() {
        UserEntity owner = userRepository.save(new UserEntity("owner", "owner@fixlog.dev"));
        loginAs(owner);
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "updateTime"));

        // 1) 폴더 생성 (workspaceId 없이)
        FolderEntity folder = folderService.createFolder(new FolderRequest(null, "트러블슈팅", 0));
        assertNotNull(folder.getFolderId());
        assertEquals("트러블슈팅", folder.getFolderName());
        assertEquals(owner.getUserId().toString(), folder.getCreateUser());
        System.out.println("[1] 폴더 생성 OK: " + folder.getFolderId());

        // 2) 폴더 안에 문서 생성
        DocumentEntity doc = documentService.create(
                new DocumentCreateRequest(folder.getFolderId(), "DB 커넥션 풀 고갈"));
        assertNotNull(doc.getDocumentId());
        assertEquals(folder.getFolderId(), doc.getFolderId());
        assertEquals("[]", doc.getBlocks());
        System.out.println("[2] 문서 생성 OK: " + doc.getDocumentId());

        // 3) 문서 목록 (폴더 필터) — 파생 쿼리 + 페이지네이션
        Page<DocumentEntity> byFolder = documentService.list(folder.getFolderId(), pageable);
        assertEquals(1, byFolder.getTotalElements());
        assertEquals(doc.getDocumentId(), byFolder.getContent().get(0).getDocumentId());
        System.out.println("[3] 폴더별 문서목록 OK: total=" + byFolder.getTotalElements());

        // 4) 폴더 콘텐츠에 문서가 포함되는지
        FolderContentsDto contents = folderService.getFolderContents(folder.getFolderId());
        assertEquals(1, contents.documents().size());
        assertEquals(doc.getDocumentId(), contents.documents().get(0).documentId());
        System.out.println("[4] 폴더 콘텐츠 OK: docs=" + contents.documents().size());

        // 5) 문서를 루트(folderId=null)로 이동
        DocumentEntity moved = documentService.move(doc.getDocumentId(), new DocumentMoveRequest(null));
        assertNull(moved.getFolderId());
        System.out.println("[5] 문서 루트 이동 OK");

        // 6) 루트 콘텐츠 / 전체 목록에 이동된 문서가 보이는지 (findByFolderIdIsNull...)
        FolderContentsDto root = folderService.getRootContents();
        assertTrue(root.documents().stream()
                .anyMatch(d -> d.documentId().equals(doc.getDocumentId())));
        assertEquals(1, documentService.list(null, pageable).getTotalElements());
        System.out.println("[6] 루트 목록 노출 OK: rootDocs=" + root.documents().size());

        // 7) 소유권 격리 — 다른 사용자는 이 문서에 접근 불가
        UserEntity intruder = userRepository.save(new UserEntity("intruder", "intruder@fixlog.dev"));
        loginAs(intruder);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> documentService.getDocument(doc.getDocumentId()));
        System.out.println("[7] 소유권 격리 OK: " + ex.getMessage());
    }
}
