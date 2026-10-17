package com.fixlog.presentation.controller.user;

import com.fixlog.application.service.UserService;
import com.fixlog.common.response.DataResponse;
import com.fixlog.common.response.Response;
import com.fixlog.common.security.SecurityUtil;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.presentation.dto.request.UpdateProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@Tag(name = "User")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(operationId = "getMyProfile", summary = "내 프로필 조회")
    public Response getMyProfile() {
        UserEntity user = SecurityUtil.getCurrentUser();
        return DataResponse.success(userService.getMyProfile(user));
    }

    @PatchMapping("/me")
    @Operation(operationId = "updateMyProfile", summary = "프로필 이름 변경")
    public Response updateMyProfile(@Valid @RequestBody UpdateProfileRequest request) {
        UserEntity user = SecurityUtil.getCurrentUser();
        return DataResponse.success(userService.updateNickname(user, request.nickname()));
    }
}
