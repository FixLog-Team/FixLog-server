package com.fixlog.application.repository;

import com.fixlog.domain.model.AiModelTier;
import com.fixlog.domain.model.AiUsageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AiUsageRepository extends JpaRepository<AiUsageEntity, UUID> {

    /**
     * 한도 판정용 합계. 실패한 호출은 세지 않는다 — 응답을 받지 못한 요청까지 한도를 깎으면
     * 장애가 곧 사용자 손해가 된다.
     */
    @Query("""
            select coalesce(sum(u.inputTokens + u.outputTokens), 0) from AiUsageEntity u
            where u.workspaceId = :workspaceId and u.tier = :tier
              and u.success = true and u.createAt >= :since
            """)
    long sumTokens(@Param("workspaceId") UUID workspaceId,
                   @Param("tier") AiModelTier tier,
                   @Param("since") Instant since);

    List<AiUsageEntity> findByWorkspaceIdAndCreateAtGreaterThanEqualOrderByCreateAtDesc(
            UUID workspaceId, Instant since);
}
