package com.fixlog.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OllamaAIService {

    private final ChatClient chatClient;

    public OllamaAIService(@Qualifier("ollamaChatModel") ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    /**
     * Ollama LLM으로 문서 내용을 요약합니다.
     */
    public String summarizeDocument(String content) {
        String promptText = """
                다음 문서를 간결하게 요약해주세요. 핵심 내용만 3-5문장으로 요약하세요.

                문서 내용:
                {content}

                요약:
                """;

        PromptTemplate promptTemplate = new PromptTemplate(promptText);
        Prompt prompt = promptTemplate.create(Map.of("content", content));

        return chatClient.prompt(prompt)
                .call()
                .content();
    }

    /**
     * Ollama LLM으로 문서에서 관련 태그를 생성합니다.
     */
    public List<String> generateTags(String content) {
        String promptText = """
                다음 문서에서 가장 관련성이 높은 태그를 5개 생성해주세요.
                태그는 쉼표로 구분하여 반환해주세요.

                문서 내용:
                {content}

                태그 (쉼표로 구분):
                """;

        PromptTemplate promptTemplate = new PromptTemplate(promptText);
        Prompt prompt = promptTemplate.create(Map.of("content", content));

        String response = chatClient.prompt(prompt)
                .call()
                .content();

        // 태그를 파싱하여 리스트로 변환
        return Arrays.stream(response.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 문서를 요약하고 태그를 생성합니다.
     */
    public Map<String, Object> summarizeAndTag(String content) {
        String summary = summarizeDocument(content);
        List<String> tags = generateTags(content);

        return Map.of(
                "summary", summary,
                "tags", tags
        );
    }
}
