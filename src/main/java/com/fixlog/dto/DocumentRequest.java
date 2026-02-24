package com.fixlog.dto;

public class DocumentRequest {
    private String content;

    public DocumentRequest() {
    }

    public DocumentRequest(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
