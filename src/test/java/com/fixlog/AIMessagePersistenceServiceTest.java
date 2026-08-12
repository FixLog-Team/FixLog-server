package com.fixlog;

import com.fixlog.application.repository.AIConversationRepository;
import com.fixlog.application.repository.AIMessageRepository;
import com.fixlog.application.service.AIMessagePersistenceService;
import com.fixlog.domain.model.AIConversationEntity;
import com.fixlog.domain.model.AIMessageEntity;
import com.fixlog.domain.model.AIMessageRole;
import com.fixlog.domain.model.AIMessageStatus;
import com.fixlog.domain.model.UserEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AIMessagePersistenceServiceTest {

    @Mock
    private AIConversationRepository conversationRepository;

    @Mock
    private AIMessageRepository messageRepository;

    private AIMessagePersistenceService persistenceService;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        persistenceService = new AIMessagePersistenceService(conversationRepository, messageRepository);
        userId = UUID.randomUUID();

        UserEntity user = new UserEntity("tester", "tester@example.com");
        setField(user, "userId", userId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null)
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void storesUserAndPendingAssistantMessagesTogether() throws Exception {
        UUID conversationId = UUID.randomUUID();
        AIConversationEntity conversation = new AIConversationEntity(userId, "대화");
        setField(conversation, "conversationId", conversationId);
        when(conversationRepository.findActiveByIdAndUserIdForUpdate(conversationId, userId))
                .thenReturn(Optional.of(conversation));
        when(messageRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AIMessagePersistenceService.PreparedMessages result =
                persistenceService.prepare(conversationId, " 질문 내용 ");

        assertThat(result.userMessage().getMessageSequence()).isEqualTo(1);
        assertThat(result.userMessage().getRole()).isEqualTo(AIMessageRole.USER);
        assertThat(result.userMessage().getContent()).isEqualTo("질문 내용");
        assertThat(result.assistantMessage().getMessageSequence()).isEqualTo(2);
        assertThat(result.assistantMessage().getStatus()).isEqualTo(AIMessageStatus.PENDING);
        assertThat(conversation.getNextMessageSequence()).isEqualTo(3);
        verify(messageRepository).saveAll(List.of(result.userMessage(), result.assistantMessage()));
    }

    @Test
    void marksPendingAssistantMessageAsFailed() {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        AIMessageEntity message = AIMessageEntity.createPendingAssistantMessage(conversationId, 2);
        when(messageRepository.findByMessageIdAndConversationId(messageId, conversationId))
                .thenReturn(Optional.of(message));

        persistenceService.fail(conversationId, messageId);

        assertThat(message.getStatus()).isEqualTo(AIMessageStatus.FAILED);
        assertThat(message.getCompleteTime()).isNotNull();
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
