package com.fixlog.application.service;

import com.fixlog.common.ai.TokenUsageLogger;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.presentation.dto.response.AskResponse;
import com.fixlog.presentation.dto.response.SearchResultDto;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class DocumentQAService extends AbstractAIService {

    private static final String NO_REFERENCE_ANSWER = "질문과 관련된 문서를 찾지 못해 답변할 수 없습니다.";

    /** 청크당 컨텍스트 상한. 비정상적으로 긴 청크가 프롬프트를 부풀리는 것을 방지한다. */
    private static final int MAX_CONTEXT_CHARS_PER_CHUNK = 1000;

    private final DocumentSearchService searchService;
    private final double similarityThreshold;

    public DocumentQAService(@Qualifier("googleGenAiChatModel") ChatModel chatModel,
                             TokenUsageLogger tokenUsageLogger,
                             DocumentSearchService searchService,
                             @Value("${fixlog.ai.qa.similarity-threshold:0.35}") double similarityThreshold) {
        super(ChatClient.builder(chatModel).build(), tokenUsageLogger);
        this.searchService = searchService;
        this.similarityThreshold = similarityThreshold;
    }

    public AskResponse ask(String question, int topK) {
        List<Document> documents = searchService.similaritySearch(question, topK, similarityThreshold);
        if (documents.isEmpty()) {
            return new AskResponse(NO_REFERENCE_ANSWER, List.of());
        }

        String answer = generateAnswer(question, buildContext(documents));
        List<SearchResultDto> references = searchService.toResults(documents);
        return new AskResponse(answer, references);
    }

    private String buildContext(List<Document> documents) {
        return IntStream.range(0, documents.size())
                .mapToObj(i -> {
                    Document doc = documents.get(i);
                    String title = (String) doc.getMetadata().get("title");
                    return "[문서 %d: %s]\n%s".formatted(i + 1, title, truncate(doc.getText()));
                })
                .collect(Collectors.joining("\n\n"));
    }

    private String truncate(String text) {
        if (text == null) return "";
        return text.length() > MAX_CONTEXT_CHARS_PER_CHUNK
                ? text.substring(0, MAX_CONTEXT_CHARS_PER_CHUNK) + "..."
                : text;
    }

    private String generateAnswer(String question, String context) {
        String promptText = """
                다음은 사용자의 과거 트러블슈팅 문서에서 검색된 참고 자료입니다.
                참고 자료만을 근거로 질문에 답변해주세요.

                작성 규칙:
                - 참고 자료에 없는 내용은 추측해서 답변하지 마세요.
                - 참고 자료로 답변할 수 없다면 그렇게 답변하세요.
                - 어떤 문서를 근거로 했는지 알 수 있도록 자연스럽게 언급해주세요.

                참고 자료:
                {context}

                질문:
                {question}

                답변:
                """;

        PromptTemplate promptTemplate = new PromptTemplate(promptText);
        Prompt prompt = promptTemplate.create(Map.of("context", context, "question", question));
        ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
        tokenUsageLogger.logChat("qa", response);
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new BusinessException(Code.UNKNOWN, "AI 응답이 비어 있습니다.");
        }
        return response.getResult().getOutput().getText();
    }
}
