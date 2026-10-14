package com.fixlog.domain.model;

/**
 * 이미지 분석 상태.
 *
 * <p>{@code SKIPPED}는 분석 대상이 아닌 형식(예: SVG는 래스터가 아니라 비전 모델이 읽지 못한다)을 뜻한다.
 * 실패와 구분해야 재시도 대상에서 빠진다.
 */
public enum ImageAnalysisStatus {
    PENDING,
    COMPLETED,
    FAILED,
    SKIPPED
}
