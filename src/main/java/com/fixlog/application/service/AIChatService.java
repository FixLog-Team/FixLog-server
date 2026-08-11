package com.fixlog.application.service;

import com.fixlog.application.repository.AIMessageRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.AIMessageEntity;
import com.fixlog.domain.model.AIMessageRole;
import com.fixlog.domain.model.AIMessageStatus;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AIChatService {

    private static final String CHAT_PROMPT = """
            당신은 개발 과정의 문제 해결을 돕는 AI 어시스턴트입니다.
            이전 대화의 맥락을 참고해 사용자의 마지막 질문에 정확하고 간결하게 답변하세요.
            확실하지 않은 내용은 추측하지 말고 불확실하다고 밝혀 주세요.

            이전 대화와 현재 질문:
            {conversation}

            AI 답변:
            """;

    private final ChatClient chatClient;
    private final AIMessageRepository messageRepository;
    private final AIMessagePersistenceService persistenceService;

    public AIChatService(@Qualifier("googleGenAiChatModel") ChatModel chatModel,
                         AIMessageRepository messageRepository,
                         AIMessagePersistenceService persistenceService) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.messageRepository = messageRepository;
        this.persistenceService = persistenceService;
    }

    public ChatResult send(UUID conversationId, String content) {
        AIMessagePersistenceService.PreparedMessages messages = persistenceService.prepare(conversationId, content);
        AIMessageEntity assistantMessage = messages.assistantMessage();

        try {
            String answer = generateAnswer(conversationId, messages.userMessage().getMessageSequence());
            if (answer == null || answer.isBlank()) {
                throw new IllegalStateException("AI 응답 내용이 비어 있습니다.");
            }
            AIMessageEntity completedMessage = persistenceService.complete(
                    conversationId, assistantMessage.getMessageId(), answer);
            return new ChatResult(messages.userMessage(), completedMessage);
        } catch (Exception exception) {
            persistenceService.fail(conversationId, assistantMessage.getMessageId());
            throw new BusinessException(Code.UNKNOWN, "AI 응답 생성에 실패했습니다.");
        }
    }

    private String generateAnswer(UUID conversationId, int currentSequence) {
        List<AIMessageEntity> messages = new ArrayList<>(
                messageRepository
                        .findTop20ByConversationIdAndStatusAndMessageSequenceLessThanEqualOrderByMessageSequenceDesc(
                                conversationId, AIMessageStatus.COMPLETED, currentSequence)
        );
        Collections.reverse(messages);

        String conversation = messages.stream()
                .map(this::formatMessage)
                .collect(Collectors.joining("\n\n"));
        Prompt prompt = new PromptTemplate(CHAT_PROMPT).create(Map.of("conversation", conversation));
        return chatClient.prompt(prompt).call().content();
    }

    private String formatMessage(AIMessageEntity message) {
        String speaker = message.getRole() == AIMessageRole.USER ? "사용자" : "AI";
        return "%s: %s".formatted(speaker, message.getContent());
    }

    public record ChatResult(
            AIMessageEntity userMessage,
            AIMessageEntity assistantMessage
    ) {
    }
}
