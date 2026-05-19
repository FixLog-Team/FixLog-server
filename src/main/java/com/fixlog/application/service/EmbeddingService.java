package com.fixlog.application.service;

import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
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
        try {
            EmbeddingRequest embeddingRequest = new EmbeddingRequest(List.of(text), null);
            EmbeddingResponse embeddingResponse = embeddingModel.call(embeddingRequest);
            if (embeddingResponse.getResults() == null || embeddingResponse.getResults().isEmpty()) {
                throw new BusinessException(Code.UNKNOWN, "임베딩 결과가 비어 있습니다.");
            }
            float[] floatArray = embeddingResponse.getResults().get(0).getOutput();
            return toDoubleList(floatArray);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(Code.UNKNOWN, "임베딩 생성 중 오류가 발생했습니다.");
        }
    }

    public List<List<Double>> generateEmbeddings(List<String> texts) {
        try {
            EmbeddingRequest embeddingRequest = new EmbeddingRequest(texts, null);
            EmbeddingResponse embeddingResponse = embeddingModel.call(embeddingRequest);
            if (embeddingResponse.getResults() == null || embeddingResponse.getResults().isEmpty()) {
                throw new BusinessException(Code.UNKNOWN, "임베딩 결과가 비어 있습니다.");
            }
            return embeddingResponse.getResults().stream()
                    .map(result -> toDoubleList(result.getOutput()))
                    .toList();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(Code.UNKNOWN, "임베딩 생성 중 오류가 발생했습니다.");
        }
    }

    private List<Double> toDoubleList(float[] array) {
        List<Double> result = new ArrayList<>(array.length);
        for (float value : array) {
            result.add((double) value);
        }
        return result;
    }
}
