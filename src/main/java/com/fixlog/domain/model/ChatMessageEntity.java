package com.fixlog.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * AI 질의응답 대화 메시지.
 * 이력에는 질문/답변 텍스트만 저장한다.
 * 검색된 참고 자료는 턴마다 새로 검색하므로 이력에 누적하지 않는다. (토큰 비용 통제)
 */
@Entity
@Table(name = "apj_chat_message",
        indexes = @Index(name = "idx_chat_message_conversation", columnList = "conversation_id, create_user, create_time"))
public class ChatMessageEntity {

    @Id
    @Column(name = "message_id", length = 100)
    private String messageId;

    @Column(name = "conversation_id", length = 100, nullable = false)
    private String conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false)
    private ChatMessageRole role;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "create_user", length = 100, nullable = false)
    private String createUser;

    @Column(name = "create_time", updatable = false)
    private Instant createTime;

    protected ChatMessageEntity() {
    }

    public ChatMessageEntity(String messageId, String conversationId,
                             ChatMessageRole role, String content, String createUser) {
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.createUser = createUser;
        this.createTime = Instant.now();
    }

    public String getMessageId() { return messageId; }
    public String getConversationId() { return conversationId; }
    public ChatMessageRole getRole() { return role; }
    public String getContent() { return content; }
    public String getCreateUser() { return createUser; }
    public Instant getCreateTime() { return createTime; }
}
