package com.fixlog.application.service;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(@Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public List<Double> generateEmbedding(String text) {
        EmbeddingRequest embeddingRequest = new EmbeddingRequest(List.of(text), null);
        EmbeddingResponse embeddingResponse = embeddingModel.call(embeddingRequest);
        float[] floatArray = embeddingResponse.getResults().get(0).getOutput();
        return toDoubleList(floatArray);
    }

    public List<List<Double>> generateEmbeddings(List<String> texts) {
        EmbeddingRequest embeddingRequest = new EmbeddingRequest(texts, null);
        EmbeddingResponse embeddingResponse = embeddingModel.call(embeddingRequest);
        return embeddingResponse.getResults().stream()
                .map(result -> toDoubleList(result.getOutput()))
                .toList();
    }

    private List<Double> toDoubleList(float[] array) {
        List<Double> result = new ArrayList<>(array.length);
        for (float value : array) {
            result.add((double) value);
        }
        return result;
    }
}
