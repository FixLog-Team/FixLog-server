package com.fixlog.application.event;

import com.fixlog.domain.model.DocumentEntity;

public record DocumentSavedEvent(DocumentEntity document) {
}
