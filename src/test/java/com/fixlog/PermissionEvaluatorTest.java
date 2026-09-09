package com.fixlog;

import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.application.repository.FolderRepository;
import com.fixlog.application.repository.GroupMemberRepository;
import com.fixlog.application.repository.GroupRepository;
import com.fixlog.application.repository.PermissionRepository;
import com.fixlog.application.repository.SecurityPolicyRepository;
import com.fixlog.application.repository.UserRepository;
import com.fixlog.application.repository.WorkspaceInvitationRepository;
import com.fixlog.application.repository.WorkspaceMemberRepository;
import com.fixlog.application.repository.WorkspaceRepository;
import com.fixlog.application.service.FolderService;
import com.fixlog.application.repository.AuditLogRepository;
import com.fixlog.application.service.AuditService;
import com.fixlog.application.service.PermissionEvaluator;
import com.fixlog.application.service.PermissionService;
import com.fixlog.application.service.WorkspaceContext;
import com.fixlog.application.service.WorkspaceService;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.FolderEntity;
import com.fixlog.domain.model.GroupEntity;
import com.fixlog.domain.model.GroupMemberEntity;
import com.fixlog.domain.model.PermissionAction;
import com.fixlog.domain.model.PermissionEntity;
import com.fixlog.domain.model.PermissionType;
import com.fixlog.domain.model.PrincipalType;
import com.fixlog.domain.model.ResourceType;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.domain.model.WorkspaceEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 접근 판정의 단일 지점 (FR-PRM-002 ~ FR-PRM-010). */
@DataJpaTest
class PermissionEvaluatorTest {

    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired GroupRepository groupRepository;
    @Autowired GroupMemberRepository groupMemberRepository;
    @Autowired PermissionRepository permissionRepository;
    @Autowired SecurityPolicyRepository policyRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired FolderRepository folderRepository;
    @Autowired DocumentRepository documentRepository;
    @Autowired WorkspaceInvitationRepository invitationRepository;

    private WorkspaceService workspaceService;
    private PermissionEvaluator evaluator;
    private FolderService folderService;

    private UserEntity admin;
    private UserEntity member;
    private WorkspaceEntity workspace;

    @BeforeEach
    void setUp() {
        WorkspaceContext workspaceContext =
                new WorkspaceContext(workspaceRepository, workspaceMemberRepository);
        workspaceService = new WorkspaceService(
                workspaceRepository, workspaceMemberRepository, userRepository, workspaceContext,
                folderRepository, documentRepository, permissionRepository, invitationRepository);
        evaluator = new PermissionEvaluator(permissionRepository, workspaceMemberRepository,
                groupMemberRepository, groupRepository, folderRepository, documentRepository,
                workspaceContext, new AuditService(auditLogRepository), policyRepository);
                PermissionService permissionService = new PermissionService(
                permissionRepository, workspaceMemberRepository, groupRepository,
                userRepository, evaluator, workspaceContext);
folderService = new FolderService(folderRepository, documentRepository, workspaceContext, evaluator, permissionService);

        admin = signUp("admin");
        loginAs(admin);
        workspace = workspaceService.create("팀");

        member = signUp("member");
        workspaceService.invite(workspace.getWorkspaceId(), "member@fixlog.dev");
        loginAs(member);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
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

    /** 협업 워크스페이스의 폴더·문서는 직접 만든다. 컨텍스트 헤더는 요청 밖에서 쓸 수 없다. */
    private FolderEntity folder(String name, FolderEntity parent) {
        String id = UUID.randomUUID().toString();
        return folderRepository.save(new FolderEntity(
                id, workspace.getWorkspaceId(),
                parent == null ? null : parent.getFolderId(),
                name, 0, admin.getUserId().toString(),
                parent == null ? "/" : parent.getPath()));
    }

    private DocumentEntity document(String title, FolderEntity parent) {
        return documentRepository.save(new DocumentEntity(
                UUID.randomUUID().toString(), workspace.getWorkspaceId(),
                parent == null ? null : parent.getFolderId(),
                title, "[]", "", "hash", 0, admin.getUserId().toString()));
    }

    private void grantUser(ResourceType type, String id, UUID userId,
                           PermissionType permissionType, boolean canDownload) {
        permissionRepository.save(new PermissionEntity(workspace.getWorkspaceId(),
                PrincipalType.USER, userId, type, id, permissionType, canDownload, admin.getUserId()));
    }

    private void grantGroup(ResourceType type, String id, UUID groupId, PermissionType permissionType) {
        permissionRepository.save(new PermissionEntity(workspace.getWorkspaceId(),
                PrincipalType.GROUP, groupId, type, id, permissionType, true, admin.getUserId()));
    }

    // ---------- 관리자와 기본 거부 ----------

    @Test
    void 관리자는_권한_레코드_없이도_전부_접근한다() {
        DocumentEntity doc = document("문서", null);
        loginAs(admin);

        PermissionEvaluator.Decision decision = evaluator.evaluate(ResourceType.DOCUMENT, doc.getDocumentId());

        assertTrue(decision.workspaceAdmin());
        assertTrue(decision.allowed());
        assertTrue(decision.canDownload());
    }

    @Test
    void 권한이_없는_구성원은_거부된다() {
        DocumentEntity doc = document("문서", null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> evaluator.evaluate(ResourceType.DOCUMENT, doc.getDocumentId()));

        assertEquals(Code.FORBIDDEN, e.getCode(), "구성원이지만 권한이 없으면 FORBIDDEN이다");
    }

    // 기본 정책이 코드에 박혀 있으면 워크스페이스마다 노출 범위를 고를 수 없다.
    // 판정의 마지막 단계가 데이터(workspace.base_access)를 따르는지 고정한다.
    @Test
    void 워크스페이스_기본이_ALLOW면_권한_없는_구성원도_접근한다() {
        DocumentEntity doc = document("문서", null);
        changeWorkspaceBaseAccess(PermissionType.ALLOW);

        PermissionEvaluator.Decision decision = evaluator.evaluate(ResourceType.DOCUMENT, doc.getDocumentId());

        assertTrue(decision.allowed());
        assertFalse(decision.workspaceAdmin(), "관리자 특권이 아니라 기본 정책으로 열린 것이다");
    }

    @Test
    void 기본이_ALLOW여도_명시적_DENY가_우선한다() {
        DocumentEntity doc = document("문서", null);
        changeWorkspaceBaseAccess(PermissionType.ALLOW);
        grantUser(ResourceType.DOCUMENT, doc.getDocumentId(), member.getUserId(),
                PermissionType.DENY, false);

        BusinessException e = assertThrows(BusinessException.class,
                () -> evaluator.evaluate(ResourceType.DOCUMENT, doc.getDocumentId()));

        assertEquals(Code.FORBIDDEN, e.getCode());
    }

    @Test
    void 새_협업_워크스페이스는_기본이_DENY다() {
        assertEquals(PermissionType.DENY,
                workspaceRepository.findById(workspace.getWorkspaceId()).orElseThrow().getBaseAccess(),
                "명시적으로 부여한 것만 열리는 쪽이 기본이어야 한다");
    }

    @Test
    void 개인_워크스페이스는_기본이_ALLOW다() {
        UserEntity solo = signUp("solo");

        assertEquals(PermissionType.ALLOW,
                workspaceRepository.findByPersonalOwnerId(solo.getUserId()).orElseThrow().getBaseAccess(),
                "혼자 쓰는 공간을 기본 차단할 이유가 없다");
    }

    private void changeWorkspaceBaseAccess(PermissionType baseAccess) {
        WorkspaceEntity entity = workspaceRepository.findById(workspace.getWorkspaceId()).orElseThrow();
        entity.changeBaseAccess(baseAccess);
        workspaceRepository.saveAndFlush(entity);
    }

    @Test
    void 비구성원에게는_문서가_존재하지_않는다() {
        DocumentEntity doc = document("문서", null);
        loginAs(signUp("stranger"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> evaluator.evaluate(ResourceType.DOCUMENT, doc.getDocumentId()));

        assertEquals(Code.NOT_FOUND, e.getCode(), "비구성원에게는 존재 자체를 알리지 않는다");
    }

    // ---------- 레벨 ----------

    @Test
    void ALLOW_권한이_있으면_접근된다() {
        DocumentEntity doc = document("문서", null);
        grantUser(ResourceType.DOCUMENT, doc.getDocumentId(), member.getUserId(),
                PermissionType.ALLOW, true);

        PermissionEvaluator.Decision decision = evaluator.evaluate(ResourceType.DOCUMENT, doc.getDocumentId());

        assertTrue(decision.allowed());
        assertTrue(decision.canDownload());
        assertFalse(decision.workspaceAdmin());
    }

    @Test
    void 다운로드는_별도_플래그로_막힌다() {
        DocumentEntity doc = document("문서", null);
        grantUser(ResourceType.DOCUMENT, doc.getDocumentId(), member.getUserId(),
                PermissionType.ALLOW, false);

        // 접근은 허용되지만 반출만 막힌다
        assertTrue(evaluator.require(ResourceType.DOCUMENT, doc.getDocumentId(), PermissionAction.EDIT)
                .allowed());
        assertEquals(Code.FORBIDDEN, assertThrows(BusinessException.class,
                () -> evaluator.requireDownload(ResourceType.DOCUMENT, doc.getDocumentId())).getCode());
    }

    // ---------- 상속 ----------

    @Test
    void 폴더_권한은_하위_문서로_상속된다() {
        FolderEntity parent = folder("스터디", null);
        DocumentEntity doc = document("1주차", parent);
        grantUser(ResourceType.FOLDER, parent.getFolderId(), member.getUserId(),
                PermissionType.ALLOW, true);

        assertTrue(evaluator.evaluate(ResourceType.DOCUMENT, doc.getDocumentId()).allowed());
    }

    @Test
    void 문서에_직접_준_권한이_상속보다_우선한다() {
        FolderEntity parent = folder("스터디", null);
        DocumentEntity doc = document("1주차", parent);
        grantUser(ResourceType.FOLDER, parent.getFolderId(), member.getUserId(),
                PermissionType.ALLOW, true);
        grantUser(ResourceType.DOCUMENT, doc.getDocumentId(), member.getUserId(),
                PermissionType.DENY, true);

        // 문서에 DENY가 직접 부여되면 폴더의 ALLOW 상속보다 우선해 차단된다
        assertEquals(Code.FORBIDDEN, assertThrows(BusinessException.class,
                () -> evaluator.evaluate(ResourceType.DOCUMENT, doc.getDocumentId())).getCode());
    }

    @Test
    void 가까운_조상이_먼_조상을_이긴다() {
        FolderEntity root = folder("루트", null);
        FolderEntity child = folder("하위", root);
        DocumentEntity doc = document("문서", child);
        grantUser(ResourceType.FOLDER, root.getFolderId(), member.getUserId(),
                PermissionType.ALLOW, true);
        grantUser(ResourceType.FOLDER, child.getFolderId(), member.getUserId(),
                PermissionType.DENY, true);

        // 가까운 조상(child)의 DENY가 먼 조상(root)의 ALLOW를 이긴다
        assertEquals(Code.FORBIDDEN, assertThrows(BusinessException.class,
                () -> evaluator.evaluate(ResourceType.DOCUMENT, doc.getDocumentId())).getCode());
    }

    @Test
    void 폴더_자신의_권한은_조상보다_우선한다() {
        FolderEntity root = folder("루트", null);
        FolderEntity child = folder("하위", root);
        grantUser(ResourceType.FOLDER, root.getFolderId(), member.getUserId(),
                PermissionType.ALLOW, true);
        grantUser(ResourceType.FOLDER, child.getFolderId(), member.getUserId(),
                PermissionType.DENY, true);

        // 폴더 자신의 DENY가 조상의 ALLOW보다 우선해 차단된다
        assertEquals(Code.FORBIDDEN, assertThrows(BusinessException.class,
                () -> evaluator.evaluate(ResourceType.FOLDER, child.getFolderId())).getCode());
    }

    // ---------- 주체 우선순위 ----------

    @Test
    void 같은_대상에서는_사용자_권한이_그룹_권한을_이긴다() {
        DocumentEntity doc = document("문서", null);
        GroupEntity group = groupRepository.save(new GroupEntity(workspace.getWorkspaceId(), "백엔드"));
        groupMemberRepository.save(new GroupMemberEntity(group.getGroupId(), member.getUserId()));

        grantGroup(ResourceType.DOCUMENT, doc.getDocumentId(), group.getGroupId(), PermissionType.ALLOW);
        grantUser(ResourceType.DOCUMENT, doc.getDocumentId(), member.getUserId(),
                PermissionType.DENY, true);

        // 그룹은 ALLOW이지만 사용자 직접 DENY가 우선한다
        assertEquals(Code.FORBIDDEN, assertThrows(BusinessException.class,
                () -> evaluator.evaluate(ResourceType.DOCUMENT, doc.getDocumentId())).getCode());
    }

    @Test
    void 그룹_권한만_있으면_그룹을_따른다() {
        DocumentEntity doc = document("문서", null);
        GroupEntity group = groupRepository.save(new GroupEntity(workspace.getWorkspaceId(), "백엔드"));
        groupMemberRepository.save(new GroupMemberEntity(group.getGroupId(), member.getUserId()));
        grantGroup(ResourceType.DOCUMENT, doc.getDocumentId(), group.getGroupId(), PermissionType.ALLOW);

        assertTrue(evaluator.evaluate(ResourceType.DOCUMENT, doc.getDocumentId()).allowed());
    }

    @Test
    void 속하지_않은_그룹의_권한은_적용되지_않는다() {
        DocumentEntity doc = document("문서", null);
        GroupEntity group = groupRepository.save(new GroupEntity(workspace.getWorkspaceId(), "다른 파트"));
        grantGroup(ResourceType.DOCUMENT, doc.getDocumentId(), group.getGroupId(), PermissionType.ALLOW);

        assertEquals(Code.FORBIDDEN, assertThrows(BusinessException.class,
                () -> evaluator.evaluate(ResourceType.DOCUMENT, doc.getDocumentId())).getCode());
    }

    // ---------- 경로 갱신 ----------

    // 폴더를 옮겼는데 하위 경로가 그대로면 상속이 옛 조상을 따라간다. 조용히 새는 경로다.
    @Test
    void 폴더를_옮기면_하위_문서의_상속_조상도_바뀐다() {
        FolderEntity granted = folder("권한 있는 폴더", null);
        FolderEntity other = folder("권한 없는 폴더", null);
        FolderEntity moving = folder("이동할 폴더", granted);
        DocumentEntity doc = document("문서", moving);
        grantUser(ResourceType.FOLDER, granted.getFolderId(), member.getUserId(),
                PermissionType.ALLOW, true);

        assertTrue(evaluator.evaluate(ResourceType.DOCUMENT, doc.getDocumentId()).allowed());

        loginAs(admin);
        folderService.moveFolder(moving.getFolderId(), other.getFolderId());
        loginAs(member);

        assertEquals(Code.FORBIDDEN, assertThrows(BusinessException.class,
                () -> evaluator.evaluate(ResourceType.DOCUMENT, doc.getDocumentId())).getCode(),
                "새 조상에는 권한이 없으므로 접근이 끊겨야 한다");
    }

    @Test
    void 서브트리_전체의_경로가_함께_갱신된다() {
        FolderEntity source = folder("출발", null);
        FolderEntity target = folder("도착", null);
        FolderEntity moving = folder("이동", source);
        FolderEntity grandChild = folder("손자", moving);

        loginAs(admin);
        folderService.moveFolder(moving.getFolderId(), target.getFolderId());

        String movedPath = folderRepository.findById(moving.getFolderId()).orElseThrow().getPath();
        String grandChildPath = folderRepository.findById(grandChild.getFolderId()).orElseThrow().getPath();

        assertEquals(target.getPath() + moving.getFolderId() + "/", movedPath);
        assertEquals(movedPath + grandChild.getFolderId() + "/", grandChildPath);
    }
}
