package com.fixlog.application.service;

import com.fixlog.application.repository.ImageAnalysisQuotaRepository;
import com.fixlog.domain.model.ImageAnalysisQuotaEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 워크스페이스별 하루 비전 분석 호출 상한.
 *
 * <p>실수든 악의든 이미지를 쏟아부으면 호출이 그대로 비용이 된다. 정상 사용으로는 닿지 않을 선을
 * 두어 폭주만 막는다. 상한에 걸려도 업로드와 이미지 사용은 막지 않는다 — 검색에만 늦게 들어온다.
 *
 * <p>분석 결과를 재사용한 건(내용 해시 일치)은 모델을 부르지 않으므로 세지 않는다.
 */
@Service
public class ImageAnalysisQuotaService {

    private final ImageAnalysisQuotaRepository quotaRepository;
    private final int dailyLimit;

    public ImageAnalysisQuotaService(ImageAnalysisQuotaRepository quotaRepository,
                                     @Value("${fixlog.ai.image.daily-analysis-limit}") int dailyLimit) {
        this.quotaRepository = quotaRepository;
        this.dailyLimit = dailyLimit;
    }

    /**
     * 오늘 분 한 건을 당겨쓴다. 상한을 넘었으면 {@code false}를 돌려주고 세지 않는다.
     *
     * <p>동시에 여러 건이 들어오면 몇 건 더 통과할 수 있다. 폭주를 막는 것이 목적이라
     * 정확히 N건에서 끊는 것보다 단순한 구현을 택했다.
     */
    public boolean tryConsume(UUID workspaceId) {
        LocalDate today = LocalDate.now();
        ImageAnalysisQuotaEntity quota = quotaRepository
                .findByWorkspaceIdAndUsageDate(workspaceId, today)
                .orElseGet(() -> new ImageAnalysisQuotaEntity(workspaceId, today));

        if (quota.hasReached(dailyLimit)) {
            return false;
        }

        quota.increase();
        quotaRepository.save(quota);
        return true;
    }

    public int getDailyLimit() {
        return dailyLimit;
    }
}
