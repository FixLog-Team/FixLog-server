package com.fixlog.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_conversation")
public class AIConversationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "conversation_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID conversationId;

    @Column(name = "user_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID userId;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "next_message_sequence", nullable = false)
    private Integer nextMessageSequence;

    @Column(name = "usable", nullable = false)
    private Integer usable;

    @Column(name = "create_time", updatable = false, nullable = false)
    private Instant createTime;

    @Column(name = "update_time", nullable = false)
    private Instant updateTime;

    protected AIConversationEntity() {
    }

    public AIConversationEntity(UUID userId, String title) {
        this.userId = userId;
        this.title = title;
        this.nextMessageSequence = 1;
        this.usable = 1;
        this.createTime = Instant.now();
        this.updateTime = this.createTime;
    }

    public int reserveMessageSequences(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("예약할 메시지 순번 개수는 1 이상이어야 합니다.");
        }

        int firstSequence = nextMessageSequence;
        nextMessageSequence += count;
        updateTime = Instant.now();
        return firstSequence;
    }

    public void rename(String title) {
        this.title = title;
        this.updateTime = Instant.now();
    }

    public void touch() {
        this.updateTime = Instant.now();
    }

    public void softDelete() {
        this.usable = 0;
        this.updateTime = Instant.now();
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public Integer getNextMessageSequence() {
        return nextMessageSequence;
    }

    public Integer getUsable() {
        return usable;
    }

    public Instant getCreateTime() {
        return createTime;
    }

    public Instant getUpdateTime() {
        return updateTime;
    }
}
