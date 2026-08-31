package com.fixlog.application.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Component
public class DocumentChunkingService {

    private static final int CHUNK_SIZE_TOKENS = 400;
    private static final int CHUNK_OVERLAP_TOKENS = 60;
    private static final int MIN_CHUNK_SIZE_CHARS = 80;
    private static final int MIN_CHUNK_LENGTH_TO_EMBED = 5;
    private static final int MAX_NUM_CHUNKS = 10_000;

    private final TokenTextSplitter chunkSplitter;
    private final TokenTextSplitter overlapSplitter;

    public DocumentChunkingService() {
        this.chunkSplitter = createSplitter(CHUNK_SIZE_TOKENS, MIN_CHUNK_SIZE_CHARS);
        this.overlapSplitter = createSplitter(CHUNK_OVERLAP_TOKENS, 0);
    }

    /**
     * 모델 입력 크기를 안정적으로 유지하기 위해 토큰 단위로 분할하고,
     * 검색 시 경계 문맥이 끊기지 않도록 이전 청크의 끝부분을 다음 청크에 겹친다.
     */
    public List<String> chunk(String plainText) {
        if (plainText == null || plainText.isBlank()) return List.of();

        List<String> chunks = split(chunkSplitter, plainText.trim());
        if (chunks.size() < 2) return chunks;

        return IntStream.range(0, chunks.size())
                .mapToObj(index -> index == 0
                        ? chunks.get(index)
                        : tail(chunks.get(index - 1)) + "\n" + chunks.get(index))
                .toList();
    }

    private List<String> split(TokenTextSplitter splitter, String text) {
        return splitter.apply(List.of(new Document(text, Map.of()))).stream()
                .map(Document::getText)
                .filter(chunk -> chunk != null && !chunk.isBlank())
                .map(String::trim)
                .toList();
    }

    private String tail(String text) {
        List<String> overlapChunks = split(overlapSplitter, text);
        return overlapChunks.isEmpty() ? "" : overlapChunks.get(overlapChunks.size() - 1);
    }

    private static TokenTextSplitter createSplitter(int chunkSize, int minChunkSizeChars) {
        return TokenTextSplitter.builder()
                .withChunkSize(chunkSize)
                .withMinChunkSizeChars(minChunkSizeChars)
                .withMinChunkLengthToEmbed(MIN_CHUNK_LENGTH_TO_EMBED)
                .withMaxNumChunks(MAX_NUM_CHUNKS)
                .withKeepSeparator(true)
                .build();
    }
}
