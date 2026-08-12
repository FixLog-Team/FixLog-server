package com.fixlog.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_message")
public class AIMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "message_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID messageId;

    @Column(name = "conversation_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID conversationId;

    @Column(name = "message_sequence", updatable = false, nullable = false)
    private Integer messageSequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, updatable = false, nullable = false)
    private AIMessageRole role;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private AIMessageStatus status;

    @Column(name = "create_time", updatable = false, nullable = false)
    private Instant createTime;

    @Column(name = "complete_time")
    private Instant completeTime;

    protected AIMessageEntity() {
    }

    private AIMessageEntity(UUID conversationId, int messageSequence, AIMessageRole role,
                            String content, AIMessageStatus status) {
        this.conversationId = conversationId;
        this.messageSequence = messageSequence;
        this.role = role;
        this.content = content;
        this.status = status;
        this.createTime = Instant.now();
        this.completeTime = status == AIMessageStatus.COMPLETED ? this.createTime : null;
    }

    public static AIMessageEntity createUserMessage(UUID conversationId, int messageSequence, String content) {
        return new AIMessageEntity(
                conversationId,
                messageSequence,
                AIMessageRole.USER,
                content,
                AIMessageStatus.COMPLETED
        );
    }

    public static AIMessageEntity createPendingAssistantMessage(UUID conversationId, int messageSequence) {
        return new AIMessageEntity(
                conversationId,
                messageSequence,
                AIMessageRole.ASSISTANT,
                null,
                AIMessageStatus.PENDING
        );
    }

    public void complete(String content) {
        this.content = content;
        this.status = AIMessageStatus.COMPLETED;
        this.completeTime = Instant.now();
    }

    public void fail() {
        this.status = AIMessageStatus.FAILED;
        this.completeTime = Instant.now();
    }

    public UUID getMessageId() {
        return messageId;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public Integer getMessageSequence() {
        return messageSequence;
    }

    public AIMessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public AIMessageStatus getStatus() {
        return status;
    }

    public Instant getCreateTime() {
        return createTime;
    }

    public Instant getCompleteTime() {
        return completeTime;
    }
}
