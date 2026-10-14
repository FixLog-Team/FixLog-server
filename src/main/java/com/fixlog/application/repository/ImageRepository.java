package com.fixlog.application.repository;

import com.fixlog.domain.model.ImageAnalysisStatus;
import com.fixlog.domain.model.ImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImageRepository extends JpaRepository<ImageEntity, String> {

    List<ImageEntity> findByImageIdIn(List<String> imageIds);

    /** 분석이 끝난 뒤 어떤 문서를 다시 색인할지 역방향으로 찾는다. */
    List<ImageEntity> findByDocumentId(String documentId);

    /**
     * 같은 내용의 이미지가 이미 분석됐는지 찾는다. 분석 결과를 재사용해 LLM 재호출을 피한다.
     * 같은 이미지가 여러 번 올라올 수 있어 가장 먼저 분석된 하나만 쓴다.
     */
    Optional<ImageEntity> findFirstByContentHashAndAnalysisStatusOrderByAnalyzedAtAsc(
            String contentHash, ImageAnalysisStatus analysisStatus);
}
