package com.fixlog.dto;

import java.util.List;

public class DocumentSummaryResponse {
    private String summary;
    private List<String> tags;
    private List<Double> embedding;

    public DocumentSummaryResponse() {
    }

    public DocumentSummaryResponse(String summary, List<String> tags, List<Double> embedding) {
        this.summary = summary;
        this.tags = tags;
        this.embedding = embedding;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<Double> getEmbedding() {
        return embedding;
    }

    public void setEmbedding(List<Double> embedding) {
        this.embedding = embedding;
    }
}
