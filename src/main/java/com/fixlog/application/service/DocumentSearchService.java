package com.fixlog.application.service;

import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.common.security.SecurityUtil;
import com.fixlog.presentation.dto.response.SearchResultDto;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentSearchService {

    private final VectorStore vectorStore;

    public DocumentSearchService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<SearchResultDto> search(String query, int topK) {
        return toResults(similaritySearch(query, topK));
    }

    public List<Document> similaritySearch(String query, int topK) {
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) throw new BusinessException(Code.UNAUTHORIZED, "인증 정보가 없습니다.");

        FilterExpressionBuilder b = new FilterExpressionBuilder();
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .filterExpression(b.eq("createUser", userId).build())
                        .build()
        );
    }

    public List<SearchResultDto> toResults(List<Document> documents) {
        return documents.stream()
                .map(doc -> new SearchResultDto(
                        (String) doc.getMetadata().get("documentId"),
                        (String) doc.getMetadata().get("title"),
                        (String) doc.getMetadata().get("folderId"),
                        truncate(doc.getText(), 200),
                        doc.getScore()
                ))
                .toList();
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }
}
