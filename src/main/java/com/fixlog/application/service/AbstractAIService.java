package com.fixlog.application.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class AbstractAIService {

    protected final ChatClient chatClient;

    protected AbstractAIService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String summarizeDocument(String content) {
        String promptText = """
                다음 문서를 간결하게 요약해주세요. 핵심 내용만 3-5문장으로 요약하세요.

                작성 규칙:
                - 어떤 문제가 있었고, 원인이 무엇이었으며, 어떻게 해결했는지를 설명하세요.
                - 문서에 없는 내용은 추측해서 덧붙이지 마세요.

                문서 내용:
                {content}

                요약:
                """;

        PromptTemplate promptTemplate = new PromptTemplate(promptText);
        Prompt prompt = promptTemplate.create(Map.of("content", content));
        return chatClient.prompt(prompt).call().content();
    }

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
        String response = chatClient.prompt(prompt).call().content();

        return Arrays.stream(response.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .collect(Collectors.toList());
    }

    public Map<String, Object> summarizeAndTag(String content) {
        String summary = summarizeDocument(content);
        List<String> tags = generateTags(content);
        return Map.of("summary", summary, "tags", tags);
    }
}
