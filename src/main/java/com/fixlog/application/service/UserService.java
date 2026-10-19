package com.fixlog.application.service;

import com.fixlog.application.repository.UserRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.presentation.dto.response.UserProfileResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserProfileResponse getMyProfile(UserEntity user) {
        return toResponse(user);
    }

    public UserProfileResponse updateNickname(UserEntity user, String nickname) {
        // JwtAuthenticationFilter가 트랜잭션 밖에서 로드하므로 detached 상태.
        // 현재 트랜잭션 안에서 다시 로드해 dirty checking이 작동하게 한다.
        UserEntity managed = userRepository.findById(user.getUserId())
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        managed.updateNickname(nickname);
        return toResponse(managed);
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
