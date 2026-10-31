package com.fixlog.presentation.dto.response;

/**
 * 연관 문서 한 건.
 *
 * @param excerpt 연관도가 가장 높았던 대목. 왜 이 문서가 걸렸는지 짐작할 수 있게 보여준다.
 * @param score   0~1. 문서가 가진 청크 중 가장 높은 유사도다.
 */
public record RelatedDocumentDto(
        String documentId,
        String title,
        String folderId,
        String excerpt,
        double score
) {}
