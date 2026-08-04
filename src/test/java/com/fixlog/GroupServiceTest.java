package com.fixlog;

import com.fixlog.application.repository.GroupMemberRepository;
import com.fixlog.application.repository.GroupRepository;
import com.fixlog.application.repository.UserRepository;
import com.fixlog.application.repository.WorkspaceMemberRepository;
import com.fixlog.application.repository.WorkspaceRepository;
import com.fixlog.application.service.GroupService;
import com.fixlog.application.service.WorkspaceContext;
import com.fixlog.application.service.WorkspaceService;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.GroupEntity;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 워크스페이스 내 그룹 (FR-WS-010, FR-WS-011). */
@DataJpaTest
class GroupServiceTest {

    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired GroupRepository groupRepository;
    @Autowired GroupMemberRepository groupMemberRepository;

    private WorkspaceService workspaceService;
    private GroupService groupService;

    @BeforeEach
    void setUp() {
        WorkspaceContext workspaceContext =
                new WorkspaceContext(workspaceRepository, workspaceMemberRepository);
        workspaceService = new WorkspaceService(
                workspaceRepository, workspaceMemberRepository, userRepository, workspaceContext);
        groupService = new GroupService(groupRepository, groupMemberRepository,
                workspaceMemberRepository, userRepository, workspaceService);
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

    private WorkspaceEntity workspaceWithAdmin(String adminName) {
        loginAs(signUp(adminName));
        return workspaceService.create("팀");
    }

    @Test
    void 관리자는_그룹을_만들고_이름을_바꾸고_지운다() {
        WorkspaceEntity workspace = workspaceWithAdmin("admin");

        GroupEntity group = groupService.create(workspace.getWorkspaceId(), "백엔드 파트");
        assertEquals("백엔드 파트", group.getGroupName());

        GroupEntity renamed = groupService.rename(
                workspace.getWorkspaceId(), group.getGroupId(), "서버 파트");
        assertEquals("서버 파트", renamed.getGroupName());

        groupService.delete(workspace.getWorkspaceId(), group.getGroupId());
        assertTrue(groupService.list(workspace.getWorkspaceId()).isEmpty());
    }

    @Test
    void 같은_워크스페이스_안에서_그룹_이름은_중복될_수_없다() {
        WorkspaceEntity workspace = workspaceWithAdmin("admin");
        groupService.create(workspace.getWorkspaceId(), "백엔드");

        BusinessException e = assertThrows(BusinessException.class,
                () -> groupService.create(workspace.getWorkspaceId(), "백엔드"));

        assertEquals(Code.INVALID_REQUEST, e.getCode());
    }

    @Test
    void 워크스페이스_구성원만_그룹에_넣을_수_있다() {
        WorkspaceEntity workspace = workspaceWithAdmin("admin");
        GroupEntity group = groupService.create(workspace.getWorkspaceId(), "백엔드");
        UserEntity outsider = signUp("outsider");

        BusinessException e = assertThrows(BusinessException.class,
                () -> groupService.addMember(workspace.getWorkspaceId(), group.getGroupId(), outsider.getUserId()));

        assertEquals(Code.NOT_FOUND, e.getCode());
    }

    @Test
    void 초대된_구성원은_그룹에_넣고_뺄_수_있다() {
        WorkspaceEntity workspace = workspaceWithAdmin("admin");
        GroupEntity group = groupService.create(workspace.getWorkspaceId(), "백엔드");
        UserEntity member = signUp("member");
        workspaceService.invite(workspace.getWorkspaceId(), "member@fixlog.dev");

        groupService.addMember(workspace.getWorkspaceId(), group.getGroupId(), member.getUserId());
        assertEquals(List.of(member.getUserId()),
                groupService.members(workspace.getWorkspaceId(), group.getGroupId())
                        .stream().map(m -> m.userId()).toList());

        groupService.removeMember(workspace.getWorkspaceId(), group.getGroupId(), member.getUserId());
        assertTrue(groupService.members(workspace.getWorkspaceId(), group.getGroupId()).isEmpty());
    }

    // 그룹 ID만으로 접근하면 다른 워크스페이스의 그룹을 건드릴 수 있다. 조회에 워크스페이스를 함께 건다.
    @Test
    void 다른_워크스페이스의_그룹은_같은_관리자라도_건드릴_수_없다() {
        WorkspaceEntity first = workspaceWithAdmin("admin");
        GroupEntity group = groupService.create(first.getWorkspaceId(), "백엔드");
        WorkspaceEntity second = workspaceService.create("다른 팀");

        BusinessException e = assertThrows(BusinessException.class,
                () -> groupService.rename(second.getWorkspaceId(), group.getGroupId(), "가로채기"));

        assertEquals(Code.NOT_FOUND, e.getCode());
    }

    @Test
    void 일반_구성원은_그룹을_관리할_수_없다() {
        WorkspaceEntity workspace = workspaceWithAdmin("admin");
        GroupEntity group = groupService.create(workspace.getWorkspaceId(), "백엔드");
        UserEntity member = signUp("member");
        workspaceService.invite(workspace.getWorkspaceId(), "member@fixlog.dev");

        loginAs(member);

        for (Runnable action : List.<Runnable>of(
                () -> groupService.create(workspace.getWorkspaceId(), "새 그룹"),
                () -> groupService.rename(workspace.getWorkspaceId(), group.getGroupId(), "바꾸기"),
                () -> groupService.delete(workspace.getWorkspaceId(), group.getGroupId()),
                () -> groupService.addMember(workspace.getWorkspaceId(), group.getGroupId(), member.getUserId()))) {
            assertEquals(Code.FORBIDDEN,
                    assertThrows(BusinessException.class, action::run).getCode());
        }

        // 조회는 구성원이면 된다
        assertEquals(1, groupService.list(workspace.getWorkspaceId()).size());
    }

    @Test
    void 비구성원에게는_그룹_목록이_보이지_않는다() {
        WorkspaceEntity workspace = workspaceWithAdmin("admin");
        groupService.create(workspace.getWorkspaceId(), "백엔드");

        loginAs(signUp("stranger"));

        assertEquals(Code.NOT_FOUND, assertThrows(BusinessException.class,
                () -> groupService.list(workspace.getWorkspaceId())).getCode());
    }
}
