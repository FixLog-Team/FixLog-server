package com.fixlog.application.service;

import com.fixlog.application.repository.UserRepository;
import com.fixlog.application.repository.WorkspaceInvitationRepository;
import com.fixlog.application.repository.WorkspaceMemberRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.InvitationStatus;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.domain.model.WorkspaceInvitationEntity;
import com.fixlog.domain.model.WorkspaceMemberEntity;
import com.fixlog.domain.model.WorkspaceRole;
import com.fixlog.presentation.dto.response.InvitationDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 이메일 기반 워크스페이스 초대.
 *
 * <p>1차 구현에서는 이메일 발송을 로그로 대체한다.
 * 토큰을 통해 수락/거절하는 비동기 초대 흐름을 지원한다.
 */
@Service
public class InvitationService {

    private static final Logger log = LoggerFactory.getLogger(InvitationService.class);
    private static final int TOKEN_BYTES = 48;
    private static final int EXPIRY_DAYS = 7;

    private final WorkspaceInvitationRepository invitationRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final WorkspaceService workspaceService;
    private final WorkspaceContext workspaceContext;

    public InvitationService(WorkspaceInvitationRepository invitationRepository,
                             WorkspaceMemberRepository memberRepository,
                             UserRepository userRepository,
                             WorkspaceService workspaceService,
                             WorkspaceContext workspaceContext) {
        this.invitationRepository = invitationRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.workspaceService = workspaceService;
        this.workspaceContext = workspaceContext;
    }

    /** Admin/Owner가 이메일로 초대 발송. 이미 PENDING 초대가 있으면 중복 불가. */
    @Transactional
    public InvitationDto invite(UUID workspaceId, String email, WorkspaceRole role) {
        workspaceService.requireAdmin(workspaceId);

        if (email == null || email.isBlank()) {
            throw new BusinessException(Code.INVALID_REQUEST, "초대할 이메일은 필수입니다.");
        }
        String normalizedEmail = email.trim().toLowerCase();

        // 이미 멤버인 경우 거부
        userRepository.findByEmail(normalizedEmail)
                .map(UserEntity::getUserId)
                .ifPresent(userId -> {
                    if (memberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
                        throw new BusinessException(Code.INVALID_REQUEST, "이미 워크스페이스에 속한 사용자입니다.");
                    }
                });

        if (invitationRepository.existsByWorkspaceIdAndEmailAndStatus(
                workspaceId, normalizedEmail, InvitationStatus.PENDING)) {
            throw new BusinessException(Code.INVALID_REQUEST, "이미 PENDING 상태의 초대가 있습니다.");
        }

        UUID invitedBy = workspaceContext.requireCurrentUserId();
        String token = generateToken();
        Instant expiresAt = Instant.now().plus(EXPIRY_DAYS, ChronoUnit.DAYS);

        WorkspaceInvitationEntity invitation = invitationRepository.save(
                new WorkspaceInvitationEntity(workspaceId, normalizedEmail, role, token, invitedBy, expiresAt));

        // 1차: 이메일 발송 대신 로그 출력
        log.info("[INVITATION] workspaceId={} email={} token={} expiresAt={}",
                workspaceId, normalizedEmail, token, expiresAt);

        return InvitationDto.of(invitation, inviterName(invitedBy));
    }

    @Transactional(readOnly = true)
    public List<InvitationDto> listByWorkspace(UUID workspaceId) {
        workspaceService.requireAdmin(workspaceId);
        List<WorkspaceInvitationEntity> invitations =
                invitationRepository.findByWorkspaceIdOrderByCreateAtDesc(workspaceId);

        Map<UUID, String> names = userNames(invitations.stream()
                .map(WorkspaceInvitationEntity::getInvitedBy).distinct().toList());

        return invitations.stream()
                .map(inv -> InvitationDto.of(inv, names.get(inv.getInvitedBy())))
                .toList();
    }

    /** 초대 수락. 토큰으로 조회 후 워크스페이스 멤버로 추가한다. */
    @Transactional
    public InvitationDto accept(String token) {
        WorkspaceInvitationEntity invitation = requirePendingInvitation(token);
        UUID userId = workspaceContext.requireCurrentUserId();

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        if (!user.getEmail().equalsIgnoreCase(invitation.getEmail())) {
            throw new BusinessException(Code.FORBIDDEN, "초대받은 이메일과 로그인 이메일이 다릅니다.");
        }

        if (memberRepository.existsByWorkspaceIdAndUserId(invitation.getWorkspaceId(), userId)) {
            throw new BusinessException(Code.INVALID_REQUEST, "이미 워크스페이스에 속한 사용자입니다.");
        }

        memberRepository.save(new WorkspaceMemberEntity(
                invitation.getWorkspaceId(), userId, invitation.getRole()));
        invitation.accept();
        invitationRepository.save(invitation);

        return InvitationDto.of(invitation, inviterName(invitation.getInvitedBy()));
    }

    @Transactional
    public InvitationDto decline(String token) {
        WorkspaceInvitationEntity invitation = requirePendingInvitation(token);
        invitation.decline();
        invitationRepository.save(invitation);
        return InvitationDto.of(invitation, inviterName(invitation.getInvitedBy()));
    }

    @Transactional
    public void cancel(UUID workspaceId, UUID invitationId) {
        workspaceService.requireAdmin(workspaceId);
        WorkspaceInvitationEntity invitation = invitationRepository.findById(invitationId)
                .filter(inv -> inv.getWorkspaceId().equals(workspaceId))
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "초대를 찾을 수 없습니다."));
        if (!invitation.isPending()) {
            throw new BusinessException(Code.INVALID_REQUEST, "PENDING 상태의 초대만 취소할 수 있습니다.");
        }
        invitationRepository.delete(invitation);
    }

    private WorkspaceInvitationEntity requirePendingInvitation(String token) {
        WorkspaceInvitationEntity invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException(Code.NOT_FOUND, "유효하지 않은 초대 토큰입니다."));
        if (invitation.isExpired()) {
            invitation.expire();
            invitationRepository.save(invitation);
            throw new BusinessException(Code.INVALID_REQUEST, "만료된 초대입니다.");
        }
        if (!invitation.isPending()) {
            throw new BusinessException(Code.INVALID_REQUEST, "이미 처리된 초대입니다.");
        }
        return invitation;
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String inviterName(UUID userId) {
        return userRepository.findById(userId)
                .map(UserEntity::getUserName)
                .orElse(null);
    }

    private Map<UUID, String> userNames(List<UUID> userIds) {
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getUserId, UserEntity::getUserName, (a, b) -> a));
    }
}
