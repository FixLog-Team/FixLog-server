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
import com.fixlog.application.service.PermissionEvaluator;
import com.fixlog.application.service.WorkspaceContext;
import com.fixlog.application.service.WorkspaceService;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.domain.model.WorkspaceEntity;
import com.fixlog.domain.model.WorkspaceRole;
import com.fixlog.presentation.dto.request.DocumentCreateRequest;
import com.fixlog.presentation.dto.request.FolderRequest;
import com.fixlog.presentation.dto.response.WorkspaceDto;
import com.fixlog.presentation.dto.response.WorkspaceMemberDto;
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

/** 워크스페이스와 멤버십 (FR-WS-001 ~ FR-WS-012). */
@DataJpaTest
class WorkspaceServiceTest {

    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired PermissionRepository permissionRepository;
    @Autowired GroupRepository groupRepository;
    @Autowired GroupMemberRepository groupMemberRepository;
    @Autowired FolderRepository folderRepository;
    @Autowired DocumentRepository documentRepository;

    private WorkspaceService workspaceService;
    private FolderService folderService;
    private DocumentService documentService;

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
                new DocumentTextExtractor(), new DocumentPdfGenerator(), event -> {}, workspaceContext, permissionEvaluator);
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

    private UserEntity signUpAndLogin(String name) {
        UserEntity user = signUp(name);
        loginAs(user);
        return user;
    }

    // ---------- 생성과 개인 워크스페이스 ----------

    @Test
    void 워크스페이스를_만든_사람이_관리자가_된다() {
        UserEntity creator = signUpAndLogin("creator");

        WorkspaceEntity workspace = workspaceService.create("OS 스터디");

        assertEquals("OS 스터디", workspace.getWorkspaceName());
        assertFalse(workspace.isPersonal());
        assertEquals(WorkspaceRole.ADMIN,
                workspaceMemberRepository
                        .findByWorkspaceIdAndUserId(workspace.getWorkspaceId(), creator.getUserId())
                        .orElseThrow().getRole());
    }

    @Test
    void 개인_워크스페이스는_가입_시_하나만_생긴다() {
        UserEntity user = userRepository.save(new UserEntity("solo", "solo@fixlog.dev"));

        WorkspaceEntity first = workspaceService.ensurePersonalWorkspace(user);
        WorkspaceEntity again = workspaceService.ensurePersonalWorkspace(user);

        assertEquals(first.getWorkspaceId(), again.getWorkspaceId(), "재로그인해도 중복 생성되지 않는다");
        assertTrue(first.isPersonal());
        assertEquals(user.getUserId(), first.getPersonalOwnerId());
    }

    @Test
    void 폴더와_문서는_현재_워크스페이스에_속한다() {
        UserEntity user = signUpAndLogin("owner");
        UUID personal = workspaceRepository.findByPersonalOwnerId(user.getUserId())
                .orElseThrow().getWorkspaceId();

        var folder = folderService.createFolder(new FolderRequest(null, "폴더"));
        var document = documentService.create(new DocumentCreateRequest(null, "문서"));

        assertEquals(personal, folder.getWorkspaceId());
        assertEquals(personal, document.getWorkspaceId());
    }

    // ---------- 다중 소속 ----------

    @Test
    void 한_사용자가_여러_워크스페이스에_다른_역할로_속한다() {
        UserEntity admin = signUpAndLogin("admin");
        WorkspaceEntity study = workspaceService.create("OS 스터디");

        UserEntity member = signUp("member");
        workspaceService.invite(study.getWorkspaceId(), "member@fixlog.dev");

        loginAs(member);
        List<WorkspaceDto> mine = workspaceService.myWorkspaces();

        assertEquals(2, mine.size(), "개인 워크스페이스와 초대받은 워크스페이스");
        assertEquals(WorkspaceRole.ADMIN,
                mine.stream().filter(WorkspaceDto::personal).findFirst().orElseThrow().role());
        assertEquals(WorkspaceRole.MEMBER,
                mine.stream().filter(w -> !w.personal()).findFirst().orElseThrow().role());
    }

    // ---------- 초대 ----------

    @Test
    void 가입하지_않은_이메일은_초대할_수_없다() {
        signUpAndLogin("admin");
        WorkspaceEntity workspace = workspaceService.create("팀");

        BusinessException e = assertThrows(BusinessException.class,
                () -> workspaceService.invite(workspace.getWorkspaceId(), "nobody@fixlog.dev"));

        assertEquals(Code.NOT_FOUND, e.getCode());
    }

    @Test
    void 이미_속한_사용자는_다시_초대할_수_없다() {
        signUpAndLogin("admin");
        WorkspaceEntity workspace = workspaceService.create("팀");
        signUp("member");
        workspaceService.invite(workspace.getWorkspaceId(), "member@fixlog.dev");

        BusinessException e = assertThrows(BusinessException.class,
                () -> workspaceService.invite(workspace.getWorkspaceId(), "member@fixlog.dev"));

        assertEquals(Code.INVALID_REQUEST, e.getCode());
    }

    @Test
    void 일반_구성원은_초대할_수_없다() {
        signUpAndLogin("admin");
        WorkspaceEntity workspace = workspaceService.create("팀");
        UserEntity member = signUp("member");
        workspaceService.invite(workspace.getWorkspaceId(), "member@fixlog.dev");
        signUp("outsider");

        loginAs(member);
        BusinessException e = assertThrows(BusinessException.class,
                () -> workspaceService.invite(workspace.getWorkspaceId(), "outsider@fixlog.dev"));

        assertEquals(Code.FORBIDDEN, e.getCode());
    }

    // ---------- 역할과 마지막 관리자 ----------

    @Test
    void 관리자는_구성원을_관리자로_올릴_수_있다() {
        signUpAndLogin("admin");
        WorkspaceEntity workspace = workspaceService.create("팀");
        UserEntity member = signUp("member");
        workspaceService.invite(workspace.getWorkspaceId(), "member@fixlog.dev");

        WorkspaceMemberDto promoted = workspaceService.changeRole(
                workspace.getWorkspaceId(), member.getUserId(), WorkspaceRole.ADMIN);

        assertEquals(WorkspaceRole.ADMIN, promoted.role());
    }

    @Test
    void 마지막_관리자는_강등할_수_없다() {
        UserEntity admin = signUpAndLogin("admin");
        WorkspaceEntity workspace = workspaceService.create("팀");
        signUp("member");
        workspaceService.invite(workspace.getWorkspaceId(), "member@fixlog.dev");

        BusinessException e = assertThrows(BusinessException.class, () -> workspaceService.changeRole(
                workspace.getWorkspaceId(), admin.getUserId(), WorkspaceRole.MEMBER));

        assertEquals(Code.INVALID_REQUEST, e.getCode());
    }

    @Test
    void 마지막_관리자는_워크스페이스를_나갈_수_없다() {
        signUpAndLogin("admin");
        WorkspaceEntity workspace = workspaceService.create("팀");

        BusinessException e = assertThrows(BusinessException.class,
                () -> workspaceService.leave(workspace.getWorkspaceId()));

        assertEquals(Code.INVALID_REQUEST, e.getCode());
    }

    @Test
    void 관리자가_둘이면_한_명은_나갈_수_있다() {
        UserEntity admin = signUpAndLogin("admin");
        WorkspaceEntity workspace = workspaceService.create("팀");
        UserEntity second = signUp("second");
        workspaceService.invite(workspace.getWorkspaceId(), "second@fixlog.dev");
        workspaceService.changeRole(workspace.getWorkspaceId(), second.getUserId(), WorkspaceRole.ADMIN);

        workspaceService.leave(workspace.getWorkspaceId());

        assertFalse(workspaceMemberRepository
                .existsByWorkspaceIdAndUserId(workspace.getWorkspaceId(), admin.getUserId()));
        assertEquals(1, workspaceMemberRepository
                .countByWorkspaceIdAndRole(workspace.getWorkspaceId(), WorkspaceRole.ADMIN));
    }

    // ---------- 개인 워크스페이스 제약 ----------

    @Test
    void 개인_워크스페이스에서는_구성원을_관리할_수_없다() {
        UserEntity user = signUpAndLogin("solo");
        UUID personal = workspaceRepository.findByPersonalOwnerId(user.getUserId())
                .orElseThrow().getWorkspaceId();
        signUp("other");

        for (Runnable action : List.<Runnable>of(
                () -> workspaceService.invite(personal, "other@fixlog.dev"),
                () -> workspaceService.changeRole(personal, user.getUserId(), WorkspaceRole.MEMBER),
                () -> workspaceService.removeMember(personal, user.getUserId()),
                () -> workspaceService.leave(personal))) {
            assertEquals(Code.INVALID_REQUEST,
                    assertThrows(BusinessException.class, action::run).getCode());
        }
    }

    // ---------- 격리 ----------

    @Test
    void 비구성원에게는_워크스페이스가_존재하지_않는_것으로_보인다() {
        signUpAndLogin("owner");
        WorkspaceEntity workspace = workspaceService.create("남의 팀");

        UserEntity stranger = signUp("stranger");
        loginAs(stranger);

        for (Runnable access : List.<Runnable>of(
                () -> workspaceService.members(workspace.getWorkspaceId()),
                () -> workspaceService.invite(workspace.getWorkspaceId(), "stranger@fixlog.dev"),
                () -> workspaceService.leave(workspace.getWorkspaceId()))) {
            assertEquals(Code.NOT_FOUND,
                    assertThrows(BusinessException.class, access::run).getCode(),
                    "비구성원에게는 FORBIDDEN이 아니라 NOT_FOUND여야 존재가 드러나지 않는다");
        }
    }

    @Test
    void 내_목록에는_속하지_않은_워크스페이스가_없다() {
        signUpAndLogin("owner");
        workspaceService.create("남의 팀");

        UserEntity stranger = signUpAndLogin("stranger");

        List<WorkspaceDto> mine = workspaceService.myWorkspaces();
        assertEquals(1, mine.size());
        assertTrue(mine.get(0).personal());
        assertEquals(stranger.getUserId(),
                workspaceRepository.findById(mine.get(0).workspaceId()).orElseThrow().getPersonalOwnerId());
    }
}
