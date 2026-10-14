package com.fixlog.application.service;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 검색된 청크를 프롬프트에 넣을 참고 자료 블록으로 만든다.
 *
 * <p>문서 QA와 대화형 RAG가 같은 형식을 써야 한 쪽에서 고친 표기가 다른 쪽에서 어긋나지 않는다.
 */
@Component
public class SearchContextFormatter {

    /** 청크당 컨텍스트 상한. 비정상적으로 긴 청크가 프롬프트를 부풀리는 것을 방지한다. */
    private static final int MAX_CONTEXT_CHARS_PER_CHUNK = 1000;

    public String build(List<Document> documents) {
        return IntStream.range(0, documents.size())
                .mapToObj(i -> {
                    Document doc = documents.get(i);
                    String title = (String) doc.getMetadata().get("title");
                    return "[문서 %d: %s%s]\n%s"
                            .formatted(i + 1, title, imageHintOf(doc), truncate(doc.getText()));
                })
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * 이미지에서 나온 근거임을 표시한다. 표시가 없으면 모델이 이미지 설명을 본문에 쓰인 문장처럼
     * 인용해, 사용자가 문서를 열어도 찾을 수 없는 문구를 근거로 제시하게 된다.
     */
    private String imageHintOf(Document doc) {
        if (!DocumentVectorStoreService.SOURCE_IMAGE.equals(doc.getMetadata().get("source"))) {
            return "";
        }
        Object imageName = doc.getMetadata().get("imageName");
        String name = imageName instanceof String s && !s.isBlank() ? " " + s : "";
        return " - 첨부 이미지%s에서 읽은 내용".formatted(name);
    }

    private String truncate(String text) {
        if (text == null) return "";
        return text.length() > MAX_CONTEXT_CHARS_PER_CHUNK
                ? text.substring(0, MAX_CONTEXT_CHARS_PER_CHUNK) + "..."
                : text;
    }
}
