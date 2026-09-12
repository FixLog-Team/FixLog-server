package com.fixlog.presentation.controller.workspace;

import com.fixlog.application.service.GroupService;
import com.fixlog.common.response.DataResponse;
import com.fixlog.common.response.Response;
import com.fixlog.presentation.dto.request.GroupMemberRequest;
import com.fixlog.presentation.dto.request.GroupRequest;
import com.fixlog.presentation.dto.response.GroupDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/groups")
@Tag(name = "Workspace")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    @Operation(operationId = "listGroups")
    public Response list(@PathVariable UUID workspaceId) {
        return DataResponse.success(groupService.list(workspaceId).stream().map(GroupDto::from).toList());
    }

    @PostMapping
    @Operation(operationId = "createGroup")
    public Response create(@PathVariable UUID workspaceId, @RequestBody GroupRequest request) {
        return DataResponse.success(GroupDto.from(groupService.create(workspaceId, request.groupName())));
    }

    @PatchMapping("/{groupId}")
    @Operation(operationId = "renameGroup")
    public Response rename(@PathVariable UUID workspaceId,
                           @PathVariable UUID groupId,
                           @RequestBody GroupRequest request) {
        return DataResponse.success(GroupDto.from(groupService.rename(workspaceId, groupId, request.groupName())));
    }

    @DeleteMapping("/{groupId}")
    @Operation(operationId = "deleteGroup")
    public Response delete(@PathVariable UUID workspaceId, @PathVariable UUID groupId) {
        groupService.delete(workspaceId, groupId);
        return Response.success("그룹이 삭제되었습니다.");
    }

    @GetMapping("/{groupId}/members")
    @Operation(operationId = "listGroupMembers")
    public Response members(@PathVariable UUID workspaceId, @PathVariable UUID groupId) {
        return DataResponse.success(groupService.members(workspaceId, groupId));
    }

    @PostMapping("/{groupId}/members")
    @Operation(operationId = "addGroupMember")
    public Response addMember(@PathVariable UUID workspaceId,
                              @PathVariable UUID groupId,
                              @RequestBody GroupMemberRequest request) {
        groupService.addMember(workspaceId, groupId, request.userId());
        return Response.success("그룹에 구성원을 추가했습니다.");
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    @Operation(operationId = "removeGroupMember")
    public Response removeMember(@PathVariable UUID workspaceId,
                                 @PathVariable UUID groupId,
                                 @PathVariable UUID userId) {
        groupService.removeMember(workspaceId, groupId, userId);
        return Response.success("그룹에서 구성원을 제거했습니다.");
    }
}
