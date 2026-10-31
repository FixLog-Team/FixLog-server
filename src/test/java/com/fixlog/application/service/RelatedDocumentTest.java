package com.fixlog.application.service;

import com.fixlog.application.repository.DocumentRepository;
import com.fixlog.common.code.Code;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.domain.model.DocumentEntity;
import com.fixlog.domain.model.ResourceType;
import com.fixlog.presentation.dto.response.RelatedDocumentDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * 연관 문서.
 *
 * <p>package-private인 병합 로직을 직접 부르려고 테스트를 대상과 같은 패키지에 둔다
 * (저장소의 다른 테스트는 com.fixlog 평면 패키지에 있다).
 *
 * <p>벡터 이웃 조회 SQL은 pgvector 전용이라 H2에서 돌릴 수 없다. 여기서는 SQL 바깥의 판단
 * — 권한, 자기 제외, 문서 단위 병합, 임계값 — 을 고정한다. 실제 쿼리는 Postgres가 있는
 * 환경에서 확인해야 한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RelatedDocumentTest {

    private static final double THRESHOLD = 0.5;

    @Mock JdbcTemplate pgVectorJdbcTemplate;
    @Mock DocumentRepository documentRepository;
    @Mock WorkspaceContext workspaceContext;
    @Mock PermissionEvaluator permissionEvaluator;
    @Mock PermissionEvaluator.Scope scope;

    private RelatedDocumentService service;

    private final UUID workspaceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RelatedDocumentService(
                pgVectorJdbcTemplate, documentRepository, workspaceContext, permissionEvaluator,
                THRESHOLD, 5, 20, 200, "document_embeddings");

        given(workspaceContext.requireCurrentWorkspaceId()).willReturn(workspaceId);
        given(permissionEvaluator.evaluate(ResourceType.DOCUMENT, "doc-a"))
                .willReturn(new PermissionEvaluator.Decision(true, true, false, true));
        given(permissionEvaluator.scopeFor(workspaceId)).willReturn(scope);
        given(scope.canViewDocument(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any())).willReturn(true);
    }

    // --- 권한 ---

    @Test
    void 볼_수_없는_문서의_연관_목록은_거부된다() {
        given(permissionEvaluator.evaluate(ResourceType.DOCUMENT, "doc-a"))
                .willReturn(new PermissionEvaluator.Decision(false, false, false, false));

        assertThatThrownBy(() -> service.findRelated("doc-a", 5))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(Code.FORBIDDEN);
    }

    @Test
    void 볼_수_있는_다른_문서가_없으면_벡터_조회를_돌리지_않는다() {
        // 워크스페이스에 자기 자신뿐
        given(documentRepository.findByWorkspaceIdAndUsableOrderByOrdinalAscCreateTimeAsc(workspaceId, 1))
                .willReturn(List.of(document("doc-a")));

        assertThat(service.findRelated("doc-a", 5)).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(pgVectorJdbcTemplate);
    }

    @Test
    void 공유되지_않은_문서는_후보에서_빠진다() {
        given(documentRepository.findByWorkspaceIdAndUsableOrderByOrdinalAscCreateTimeAsc(workspaceId, 1))
                .willReturn(List.of(document("doc-a"), document("doc-secret")));
        given(scope.canViewDocument("doc-secret", null)).willReturn(false);

        // 볼 수 있는 다른 문서가 없으므로 조회 자체가 돌지 않는다
        assertThat(service.findRelated("doc-a", 5)).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(pgVectorJdbcTemplate);
    }

    // --- 문서 단위 병합 ---

    @Test
    void 같은_문서의_청크가_여러_번_걸리면_가장_높은_점수로_합친다() {
        List<RelatedDocumentDto> result = service.merge(List.of(
                chunk("doc-b", "타임아웃 정리", "앞부분 대목", 0.61),
                chunk("doc-b", "타임아웃 정리", "가장 비슷한 대목", 0.88),
                chunk("doc-b", "타임아웃 정리", "뒷부분 대목", 0.55)), 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).score()).isEqualTo(0.88);
        // 발췌는 가장 높은 점수의 대목이어야 왜 걸렸는지 설명이 된다
        assertThat(result.get(0).excerpt()).isEqualTo("가장 비슷한 대목");
    }

    @Test
    void 점수가_높은_문서부터_정렬된다() {
        List<RelatedDocumentDto> result = service.merge(List.of(
                chunk("doc-b", "B", "b", 0.62),
                chunk("doc-c", "C", "c", 0.91),
                chunk("doc-d", "D", "d", 0.77)), 5);

        assertThat(result).extracting(RelatedDocumentDto::documentId)
                .containsExactly("doc-c", "doc-d", "doc-b");
    }

    @Test
    void 임계값에_못_미치는_문서는_버린다() {
        List<RelatedDocumentDto> result = service.merge(List.of(
                chunk("doc-b", "B", "b", 0.49),
                chunk("doc-c", "C", "c", 0.51)), 5);

        assertThat(result).extracting(RelatedDocumentDto::documentId).containsExactly("doc-c");
    }

    @Test
    void 전부_임계값_미만이면_빈_목록이다() {
        List<RelatedDocumentDto> result = service.merge(List.of(
                chunk("doc-b", "B", "b", 0.2),
                chunk("doc-c", "C", "c", 0.1)), 5);

        // 억지로 채우지 않는다. 엉뚱한 결과 하나가 목록 전체의 신뢰를 깎는다
        assertThat(result).isEmpty();
    }

    @Test
    void 요청한_개수까지만_돌려준다() {
        List<RelatedDocumentDto> result = service.merge(List.of(
                chunk("doc-b", "B", "b", 0.9),
                chunk("doc-c", "C", "c", 0.8),
                chunk("doc-d", "D", "d", 0.7)), 2);

        assertThat(result).hasSize(2);
    }

    @Test
    void 긴_발췌는_잘라서_돌려준다() {
        String longText = "가".repeat(300);

        List<RelatedDocumentDto> result = service.merge(List.of(
                chunk("doc-b", "B", longText, 0.9)), 5);

        assertThat(result.get(0).excerpt()).hasSize(203).endsWith("...");
    }

    private RelatedDocumentService.NeighborChunk chunk(String documentId, String title,
                                                       String content, double score) {
        return new RelatedDocumentService.NeighborChunk(documentId, title, null, content, score);
    }

    private DocumentEntity document(String documentId) {
        return new DocumentEntity(documentId, workspaceId, null, "제목", "[]", "", "hash", 1, "user-1");
    }
}
