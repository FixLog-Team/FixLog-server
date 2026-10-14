package com.fixlog.presentation.dto.response;

/**
 * 검색 결과 한 건.
 *
 * @param source    근거가 나온 곳. {@code "text"}는 본문, {@code "image"}는 첨부 이미지 분석 결과다.
 * @param imageId   {@code source}가 {@code "image"}일 때만 채워진다. 어느 이미지가 걸렸는지 짚어준다.
 * @param imageName 이미지 원본 파일명. 없으면 비어 있다.
 */
public record SearchResultDto(
        String documentId,
        String title,
        String folderId,
        String excerpt,
        Double score,
        String source,
        String imageId,
        String imageName
) {}
