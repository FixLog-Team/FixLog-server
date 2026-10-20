package com.fixlog.presentation.dto.response;

import java.util.List;

/**
 * 검색 결과 한 건 (문서 단위).
 *
 * <p>같은 문서에서 본문·이미지 청크가 여러 건 검색되면 하나로 합쳐진다.
 *
 * @param source    근거가 나온 곳. {@code "text"}는 본문, {@code "image"}는 첨부 이미지,
 *                  {@code "mixed"}는 본문+이미지가 모두 검색된 경우다.
 * @param imageId   하위 호환용. 첫 번째 이미지의 ID. 이미지 검색 결과가 없으면 null.
 * @param imageName 하위 호환용. 첫 번째 이미지의 원본 파일명.
 * @param images    이 문서에서 검색된 이미지 목록. 본문만 검색됐으면 빈 목록.
 */
public record SearchResultDto(
        String documentId,
        String title,
        String folderId,
        String excerpt,
        Double score,
        String source,
        String imageId,
        String imageName,
        List<ImageRef> images
) {
    /** 하위 호환: images 없이 생성하면 빈 목록으로 채운다. */
    public SearchResultDto(String documentId, String title, String folderId,
                           String excerpt, Double score, String source,
                           String imageId, String imageName) {
        this(documentId, title, folderId, excerpt, score, source, imageId, imageName, List.of());
    }

    /** 검색에 걸린 이미지 한 건의 참조 정보. */
    public record ImageRef(
            String imageId,
            String imageName
    ) {}
}
