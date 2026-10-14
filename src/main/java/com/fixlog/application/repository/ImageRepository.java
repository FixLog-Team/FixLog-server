package com.fixlog.application.repository;

import com.fixlog.domain.model.ImageAnalysisStatus;
import com.fixlog.domain.model.ImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
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

    /** 정비 작업 대상. 막 올라온 이미지는 아직 문서에 붙기 전이라 유예 기간 밖의 것만 본다. */
    List<ImageEntity> findByCreateTimeBefore(Instant threshold);

    /**
     * 분석이 아직 돌지 않은 이미지. 일일 상한에 걸려 보류된 건이 여기 남는다.
     * 업로드 직후 분석 중인 건도 섞이지만, 재시도는 상태를 다시 보고 돌아가므로 중복 호출이 되지 않는다.
     */
    @Query("select i from ImageEntity i where i.analysisStatus = com.fixlog.domain.model.ImageAnalysisStatus.PENDING")
    List<ImageEntity> findPendingAnalysis();
}
