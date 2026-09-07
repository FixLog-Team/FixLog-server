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
import com.fixlog.application.service.PermissionSnapshot;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.AccessDecision;
import com.fixlog.domain.model.AccessEffect;
import com.fixlog.domain.model.AccessSource;
import com.fixlog.domain.model.BaseAccess;
import com.fixlog.domain.model.NodeType;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.domain.model.WorkspaceRole;
import com.fixlog.presentation.dto.request.DocumentCreateRequest;
import com.fixlog.presentation.dto.request.FolderRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 권한 판정 알고리즘 검증.
 *
 * <p>트리 구조는 아래와 같다. base_access를 적지 않은 노드는 INHERIT(부모를 따름)이다.
 *
 * <pre>
 * 워크스페이스 (ALLOW)
 * ├─ 전공
 * │   ├─ 프로그래밍
 * │   │   └─ 정리노트
 * │   └─ 시험답안(문서)
 * └─ 강의자료 (DENY)
 *     └─ 강의노트(문서)
 * </pre>
 */
@DataJpaTest
class PermissionResolverTest {

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

    private String workspaceId;
    private UserEntity owner;
    private UserEntity member;

    private String majorId;
    private String programmingId;
    private String lectureId;
    private String noteDocId;
    private String examDocId;
    private String lectureDocId;

    @BeforeEach
    void setUp() {
        fixture = new PermissionFixture(userRepository, workspaceRepository, memberRepository,
                overrideRepository, folderRepository, documentRepository);
        folderService = new FolderService(folderRepository, documentRepository, fixture.permissionResolver);
        documentService = new DocumentService(documentRepository, folderRepository,
                new DocumentTextExtractor(), new DocumentPdfGenerator(),
                new DocumentHistoryService(documentRepository, documentHistoryRepository,
                        fixture.permissionResolver, 50),
                fixture.permissionResolver, event -> {});

        owner = fixture.loginAsNewUser("owner");
        workspaceId = fixture.personalWorkspaceId(owner);
        member = fixture.addMember(workspaceId, "member", WorkspaceRole.MEMBER);

        majorId = folderService.createFolder(null, new FolderRequest(null, "전공")).getFolderId();
        programmingId = folderService.createFolder(null, new FolderRequest(majorId, "프로그래밍")).getFolderId();
        lectureId = folderService.createFolder(null, new FolderRequest(null, "강의자료")).getFolderId();

        noteDocId = documentService.create(null, new DocumentCreateRequest(programmingId, "정리노트")).getDocumentId();
        examDocId = documentService.create(null, new DocumentCreateRequest(majorId, "시험답안")).getDocumentId();
        lectureDocId = documentService.create(null, new DocumentCreateRequest(lectureId, "강의노트")).getDocumentId();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private AccessDecision resolveForMember(NodeType nodeType, String nodeId) {
        return fixture.permissionResolver.resolve(member.getUserId().toString(), nodeType, nodeId);
    }

    @Test
    void 설정이_없으면_루트까지_올라가_기본_정책으로_확정된다() {
        AccessDecision decision = resolveForMember(NodeType.FOLDER, majorId);

        assertEquals(AccessEffect.ALLOW, decision.effect());
        assertEquals(AccessSource.DEFAULT, decision.source(), "루트의 기본 정책에서 온 결과다");
        assertEquals(workspaceId, decision.sourceNodeId());
    }

    @Test
    void 조상에_걸린_개별_설정을_물려받는다() {
        fixture.putOverride(workspaceId, NodeType.FOLDER, majorId, member, AccessEffect.DENY);

        AccessDecision decision = resolveForMember(NodeType.DOCUMENT, noteDocId);

        assertEquals(AccessEffect.DENY, decision.effect());
        assertEquals(AccessSource.INHERITED, decision.source());
        assertEquals(majorId, decision.sourceNodeId(), "판정을 끝낸 조상이 어디인지 함께 나와야 한다");
    }

    @Test
    void 대상_문서에_직접_걸린_설정이_결과를_확정한다() {
        fixture.putOverride(workspaceId, NodeType.DOCUMENT, examDocId, member, AccessEffect.DENY);

        AccessDecision decision = resolveForMember(NodeType.DOCUMENT, examDocId);

        assertEquals(AccessEffect.DENY, decision.effect());
        assertEquals(AccessSource.DIRECT, decision.source());
        assertEquals(examDocId, decision.sourceNodeId());
    }

    @Test
    void 폴더가_상속을_끊고_DENY면_개별_허용을_받은_사용자만_접근한다() {
        fixture.setFolderBaseAccess(lectureId, BaseAccess.DENY);
        UserEntity invited = fixture.addMember(workspaceId, "invited", WorkspaceRole.MEMBER);
        fixture.putOverride(workspaceId, NodeType.FOLDER, lectureId, invited, AccessEffect.ALLOW);

        AccessDecision denied = resolveForMember(NodeType.FOLDER, lectureId);
        assertEquals(AccessEffect.DENY, denied.effect());
        assertEquals(AccessSource.DIRECT, denied.source(), "폴더 자신의 기본 정책이므로 Direct다");

        AccessDecision allowed = fixture.permissionResolver
                .resolve(invited.getUserId().toString(), NodeType.FOLDER, lectureId);
        assertEquals(AccessEffect.ALLOW, allowed.effect());
        assertEquals(AccessSource.DIRECT, allowed.source());
    }

    @Test
    void 부모가_DENY여도_자식만_직접_공유할_수_있다() {
        fixture.setFolderBaseAccess(lectureId, BaseAccess.DENY);
        fixture.putOverride(workspaceId, NodeType.DOCUMENT, lectureDocId, member, AccessEffect.ALLOW);

        // 자식에서 먼저 매치되고 순회가 끝나므로, 부모의 DENY까지 올라가지 않는다.
        AccessDecision decision = resolveForMember(NodeType.DOCUMENT, lectureDocId);

        assertEquals(AccessEffect.ALLOW, decision.effect());
        assertEquals(AccessSource.DIRECT, decision.source());
        assertEquals(lectureDocId, decision.sourceNodeId());

        // 부모 폴더 자체는 여전히 접근 불가다.
        assertEquals(AccessEffect.DENY, resolveForMember(NodeType.FOLDER, lectureId).effect());
    }

    @Test
    void 운영권_보유자는_DENY_설정을_받아도_접근한다() {
        UserEntity admin = fixture.addMember(workspaceId, "admin", WorkspaceRole.ADMIN);
        fixture.putOverride(workspaceId, NodeType.DOCUMENT, examDocId, admin, AccessEffect.DENY);

        AccessDecision decision = fixture.permissionResolver
                .resolve(admin.getUserId().toString(), NodeType.DOCUMENT, examDocId);

        // Admin은 스스로에게 Allow를 부여할 수 있으므로 Deny가 무의미하다. 통제는 기록으로 한다.
        assertEquals(AccessEffect.ALLOW, decision.effect());
        assertEquals(AccessSource.ADMIN, decision.source());
        assertNull(decision.sourceNodeId());
    }

    @Test
    void 워크스페이스_멤버가_아니면_판정_트리에_진입하지_못한다() {
        UserEntity outsider = fixture.loginAsNewUser("outsider");

        AccessDecision decision = fixture.permissionResolver
                .resolve(outsider.getUserId().toString(), NodeType.DOCUMENT, noteDocId);

        assertEquals(AccessEffect.DENY, decision.effect());
        assertNull(decision.sourceNodeId(), "트리에 들어가지 못했으므로 결정한 노드가 없다");
    }

    @Test
    void 루트는_상속할_부모가_없으므로_INHERIT으로_설정할_수_없다() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> fixture.setWorkspaceBaseAccess(workspaceId, BaseAccess.INHERIT));

        assertEquals("INVALID_REQUEST", ex.getCode().name());
    }

    @Test
    void 루트가_DENY면_설정_없는_노드는_전부_차단된다() {
        fixture.setWorkspaceBaseAccess(workspaceId, BaseAccess.DENY);

        assertEquals(AccessEffect.DENY, resolveForMember(NodeType.FOLDER, majorId).effect());
        assertEquals(AccessEffect.DENY, resolveForMember(NodeType.DOCUMENT, noteDocId).effect());

        // 중간 폴더에서 상속을 끊으면 그 아래만 다시 열린다.
        fixture.setFolderBaseAccess(programmingId, BaseAccess.ALLOW);
        assertEquals(AccessEffect.ALLOW, resolveForMember(NodeType.DOCUMENT, noteDocId).effect());
    }

    @Test
    void 단건_판정과_벌크_스냅샷_판정의_결과가_같다() {
        fixture.putOverride(workspaceId, NodeType.FOLDER, majorId, member, AccessEffect.DENY);
        fixture.putOverride(workspaceId, NodeType.DOCUMENT, noteDocId, member, AccessEffect.ALLOW);
        fixture.setFolderBaseAccess(lectureId, BaseAccess.DENY);

        PermissionSnapshot snapshot = fixture.permissionResolver
                .snapshot(workspaceId, member.getUserId().toString());

        for (String folderId : new String[]{majorId, programmingId, lectureId}) {
            AccessDecision single = resolveForMember(NodeType.FOLDER, folderId);
            AccessDecision bulk = snapshot.decide(
                    folderRepository.findByFolderIdAndUsable(folderId, 1).orElseThrow());
            assertEquals(single, bulk, "폴더 판정이 어긋나면 화면과 실제 동작이 달라진다: " + folderId);
        }

        for (String documentId : new String[]{noteDocId, examDocId, lectureDocId}) {
            AccessDecision single = resolveForMember(NodeType.DOCUMENT, documentId);
            AccessDecision bulk = snapshot.decide(
                    documentRepository.findByDocumentIdAndUsable(documentId, 1).orElseThrow());
            assertEquals(single, bulk, "문서 판정이 어긋나면 화면과 실제 동작이 달라진다: " + documentId);
        }
    }
}
