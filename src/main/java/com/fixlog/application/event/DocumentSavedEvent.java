package com.fixlog.application.event;

import com.fixlog.domain.model.DocumentEntity;

/**
 * 문서 저장 이벤트.
 * contentChanged/metadataChanged로 벡터 인덱싱 필요 여부를 구분하여
 * 내용이 바뀌지 않은 저장에서 불필요한 임베딩 재생성을 막는다.
 */
public record DocumentSavedEvent(DocumentEntity document, boolean contentChanged, boolean metadataChanged) {
}
