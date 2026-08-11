package com.fixlog;

import com.fixlog.domain.model.AIConversationEntity;
import com.fixlog.domain.model.AIMessageEntity;
import com.fixlog.domain.model.AIMessageRole;
import com.fixlog.domain.model.AIMessageStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AIChatDomainTest {

    @Test
    void reservesMessageSequencesWithoutDuplicates() {
        AIConversationEntity conversation = new AIConversationEntity(UUID.randomUUID(), "새 대화");

        int firstSequence = conversation.reserveMessageSequences(2);
        int thirdSequence = conversation.reserveMessageSequences(1);

        assertThat(firstSequence).isEqualTo(1);
        assertThat(thirdSequence).isEqualTo(3);
        assertThat(conversation.getNextMessageSequence()).isEqualTo(4);
    }

    @Test
    void rejectsInvalidSequenceReservationCount() {
        AIConversationEntity conversation = new AIConversationEntity(UUID.randomUUID(), "새 대화");

        assertThatThrownBy(() -> conversation.reserveMessageSequences(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void completesPendingAssistantMessage() {
        AIMessageEntity message = AIMessageEntity.createPendingAssistantMessage(UUID.randomUUID(), 2);

        message.complete("AI 응답");

        assertThat(message.getRole()).isEqualTo(AIMessageRole.ASSISTANT);
        assertThat(message.getStatus()).isEqualTo(AIMessageStatus.COMPLETED);
        assertThat(message.getContent()).isEqualTo("AI 응답");
        assertThat(message.getCompleteTime()).isNotNull();
    }
}
