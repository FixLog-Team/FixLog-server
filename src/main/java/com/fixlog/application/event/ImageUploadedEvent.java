package com.fixlog.application.event;

/**
 * 이미지 업로드 완료. 비전 분석을 비동기로 시작시킨다.
 * 같은 내용의 이미지가 이미 분석돼 결과를 재사용한 경우에는 발행하지 않는다.
 */
public record ImageUploadedEvent(String imageId) {
}
