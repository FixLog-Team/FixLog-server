package com.fixlog;

import com.fixlog.application.repository.AIConversationRepository;
import com.fixlog.application.service.AIConversationService;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.AIConversationEntity;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.presentation.dto.request.AIConversationCreateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AIConversationServiceTest {

    @Mock
    private AIConversationRepository conversationRepository;

    private AIConversationService conversationService;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        conversationService = new AIConversationService(conversationRepository);
        userId = UUID.randomUUID();

        UserEntity user = new UserEntity("tester", "tester@example.com");
        Field userIdField = UserEntity.class.getDeclaredField("userId");
        userIdField.setAccessible(true);
        userIdField.set(user, userId);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null)
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsConversationWithDefaultTitle() {
        when(conversationRepository.save(any(AIConversationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AIConversationEntity conversation = conversationService.create(new AIConversationCreateRequest("  "));

        assertThat(conversation.getUserId()).isEqualTo(userId);
        assertThat(conversation.getTitle()).isEqualTo("새 대화");
        verify(conversationRepository).save(conversation);
    }

    @Test
    void cannotReadAnotherUsersConversation() {
        UUID conversationId = UUID.randomUUID();
        when(conversationRepository.findByConversationIdAndUserIdAndUsable(conversationId, userId, 1))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.get(conversationId))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(Code.NOT_FOUND));
    }

    @Test
    void softDeletesOwnedConversation() {
        UUID conversationId = UUID.randomUUID();
        AIConversationEntity conversation = new AIConversationEntity(userId, "대화");
        when(conversationRepository.findByConversationIdAndUserIdAndUsable(conversationId, userId, 1))
                .thenReturn(Optional.of(conversation));

        conversationService.delete(conversationId);

        assertThat(conversation.getUsable()).isZero();
    }
}
