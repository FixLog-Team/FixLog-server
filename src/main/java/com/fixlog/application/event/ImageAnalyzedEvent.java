package com.fixlog.application.event;

/**
 * 이미지 분석 완료.
 *
 * <p>업로드 직후 바로 문서가 저장되면 색인 시점에 분석이 끝나 있지 않다. 그대로 두면 그 이미지는
 * 문서를 다시 저장할 때까지 검색되지 않으므로, 분석이 끝난 시점에 해당 문서를 다시 색인한다.
 */
public record ImageAnalyzedEvent(String imageId, String documentId) {
}
