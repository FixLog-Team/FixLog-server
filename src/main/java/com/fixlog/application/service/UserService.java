package com.fixlog.application.service;

import com.fixlog.domain.model.UserEntity;
import com.fixlog.presentation.dto.response.UserProfileResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    public UserProfileResponse getMyProfile(UserEntity user) {
        return toResponse(user);
    }

    public UserProfileResponse updateNickname(UserEntity user, String nickname) {
        user.updateNickname(nickname);
        return toResponse(user);
    }

    private UserProfileResponse toResponse(UserEntity user) {
        return new UserProfileResponse(
                user.getUserId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getPictureUrl()
        );
    }
}
