package com.fixlog.service;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OllamaEmbeddingService {

    private final EmbeddingModel embeddingModel;

    public OllamaEmbeddingService(@Qualifier("ollamaEmbeddingModel") EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * Ollama embedding 모델로 텍스트를 벡터로 변환합니다.
     * nomic-embed-text 모델을 사용합니다.
     *
     * @param text 임베딩할 텍스트
     * @return 벡터 표현 (List<Double>)
     */
    public List<Double> generateEmbedding(String text) {
        EmbeddingRequest embeddingRequest = new EmbeddingRequest(List.of(text), null);
        EmbeddingResponse embeddingResponse = embeddingModel.call(embeddingRequest);

        // float[]를 List<Double>로 변환
        float[] floatArray = embeddingResponse.getResults().get(0).getOutput();
        return convertFloatArrayToList(floatArray);
    }

    /**
     * 여러 텍스트를 한 번에 벡터로 변환합니다.
     *
     * @param texts 임베딩할 텍스트 리스트
     * @return 각 텍스트의 벡터 표현 리스트
     */
    public List<List<Double>> generateEmbeddings(List<String> texts) {
        EmbeddingRequest embeddingRequest = new EmbeddingRequest(texts, null);
        EmbeddingResponse embeddingResponse = embeddingModel.call(embeddingRequest);

        return embeddingResponse.getResults().stream()
                .map(result -> convertFloatArrayToList(result.getOutput()))
                .toList();
    }

    /**
     * float[] 배열을 List<Double>로 변환합니다.
     */
    private List<Double> convertFloatArrayToList(float[] array) {
        List<Double> result = new java.util.ArrayList<>(array.length);
        for (float value : array) {
            result.add((double) value);
        }
        return result;
    }
}
