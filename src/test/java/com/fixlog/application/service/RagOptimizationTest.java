package com.fixlog.application.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RagOptimizationTest {

    @Test
    void splitsLongTextWithContextOverlap() {
        DocumentChunkingService chunkingService = new DocumentChunkingService();
        String text = IntStream.range(0, 1_200)
                .mapToObj(index -> "token" + index)
                .collect(Collectors.joining(" "));

        List<String> chunks = chunkingService.chunk(text);

        assertThat(chunks).hasSizeGreaterThan(1);
        String previousTail = lastWord(chunks.get(0));
        assertThat(chunks.get(1)).contains(previousTail);
    }

    @Test
    void limitsChunksFromTheSameDocument() {
        DocumentSearchService searchService = new DocumentSearchService(
                mock(VectorStore.class),
                mock(com.fixlog.application.repository.DocumentRepository.class),
                mock(WorkspaceContext.class),
                mock(PermissionEvaluator.class),
                4,
                2);
        List<Document> candidates = List.of(
                document("doc-1", "first"),
                document("doc-1", "second"),
                document("doc-1", "third"),
                document("doc-2", "fourth"),
                document("doc-3", "fifth")
        );

        List<Document> result = searchService.limitChunksPerDocument(candidates, 4);

        assertThat(result).extracting(Document::getText)
                .containsExactly("first", "second", "fourth", "fifth");
    }

    @Test
    void rewritesOnlyContextDependentQuestions() {
        assertThat(AIChatService.shouldRewriteQuestion("그거 왜 실패한 거야?")).isTrue();
        assertThat(AIChatService.shouldRewriteQuestion("Why did it fail?")).isTrue();
        assertThat(AIChatService.shouldRewriteQuestion("Spring Security에서 401이 발생하는 원인은?")).isFalse();
    }

    private Document document(String documentId, String text) {
        return new Document(text, Map.of("documentId", documentId));
    }

    private String lastWord(String text) {
        String[] words = text.split("\\s+");
        return words[words.length - 1];
    }
}
