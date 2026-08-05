package com.fixlog;

import com.fixlog.application.repository.AiUsageRepository;
import com.fixlog.application.repository.UserApiKeyRepository;
import com.fixlog.application.repository.WorkspaceMemberRepository;
import com.fixlog.application.repository.WorkspaceRepository;
import com.fixlog.application.repository.UserRepository;
import com.fixlog.application.service.AiUsageService;
import com.fixlog.application.service.UserApiKeyService;
import com.fixlog.application.service.WorkspaceContext;
import com.fixlog.application.service.WorkspaceService;
import com.fixlog.common.code.Code;
import com.fixlog.common.config.AiUsageProperties;
import com.fixlog.common.exception.BusinessException;
import com.fixlog.common.security.SecretCipher;
import com.fixlog.domain.model.AiModelTier;
import com.fixlog.domain.model.AiUsageEntity;
import com.fixlog.domain.model.UserApiKeyEntity;
import com.fixlog.domain.model.UserEntity;
import com.fixlog.domain.model.WorkspaceEntity;
import com.fixlog.presentation.dto.response.UserApiKeyDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AI 사용량·한도·비용과 사용자 API Key (FR-AI-002~007). */
@DataJpaTest
class AiUsageAndApiKeyTest {

    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMemberRepository workspaceMemberRepository;
    @Autowired AiUsageRepository aiUsageRepository;
    @Autowired UserApiKeyRepository apiKeyRepository;

    private WorkspaceService workspaceService;
    private AiUsageService aiUsageService;
    private UserApiKeyService apiKeyService;
    private SecretCipher secretCipher;
    private AiUsageProperties properties;

    private UserEntity user;
    private WorkspaceEntity workspace;

    @BeforeEach
    void setUp() {
        WorkspaceContext workspaceContext =
                new WorkspaceContext(workspaceRepository, workspaceMemberRepository);
        workspaceService = new WorkspaceService(
                workspaceRepository, workspaceMemberRepository, userRepository, workspaceContext);

        properties = new AiUsageProperties();
        properties.setFreeMonthlyTokenLimit(1_000L);
        properties.setFreeModel("test-model");
        AiUsageProperties.ModelPrice price = new AiUsageProperties.ModelPrice();
        price.setInput(new BigDecimal("0.001"));
        price.setOutput(new BigDecimal("0.002"));
        properties.getPricing().put("test-model", price);

        aiUsageService = new AiUsageService(aiUsageRepository, properties);
        secretCipher = new SecretCipher(
                Base64.getEncoder().encodeToString("fixlog-test-key-32-bytes-long!!!".getBytes()));
        apiKeyService = new UserApiKeyService(apiKeyRepository, secretCipher, workspaceContext);

        aiUsageRepository.deleteAll();

        user = userRepository.save(new UserEntity("user", "user@fixlog.dev"));
        workspace = workspaceService.ensurePersonalWorkspace(user);
        loginAs(user);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(UserEntity target) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(target, null, List.of()));
    }

    private AiUsageEntity record(long input, long output, boolean success) {
        return aiUsageService.record(workspace.getWorkspaceId(), user.getUserId(),
                AiModelTier.FREE, "test-model", input, output, success);
    }

    // ---------- 사용량과 비용 ----------

    @Test
    void 집계_시점_단가가_기록에_함께_저장된다() {
        AiUsageEntity usage = record(1_000, 500, true);

        assertEquals(new BigDecimal("0.001"), usage.getInputPricePer1k());
        assertEquals(new BigDecimal("0.002"), usage.getOutputPricePer1k());
        // 1000/1000 * 0.001 + 500/1000 * 0.002 = 0.002
        assertEquals(0, usage.getCost().compareTo(new BigDecimal("0.002")));
    }

    // 단가표가 바뀌어도 이미 남은 기록의 비용은 그대로여야 한다. 청구한 값이 흔들리면 안 된다.
    @Test
    void 단가표가_바뀌어도_과거_비용은_변하지_않는다() {
        AiUsageEntity usage = record(1_000, 0, true);
        BigDecimal before = usage.getCost();

        AiUsageProperties.ModelPrice raised = new AiUsageProperties.ModelPrice();
        raised.setInput(new BigDecimal("999"));
        raised.setOutput(new BigDecimal("999"));
        properties.getPricing().put("test-model", raised);

        assertEquals(0, aiUsageRepository.findById(usage.getId()).orElseThrow()
                .getCost().compareTo(before));
    }

    @Test
    void 표에_없는_모델은_비용을_지어내지_않는다() {
        AiUsageEntity usage = aiUsageService.record(workspace.getWorkspaceId(), user.getUserId(),
                AiModelTier.FREE, "알 수 없는 모델", 1_000, 1_000, true);

        assertEquals(0, usage.getCost().compareTo(BigDecimal.ZERO));
    }

    // ---------- 한도 ----------

    @Test
    void 한도_안에서는_통과한다() {
        record(400, 400, true);

        assertEquals(800, aiUsageService.freeTokensUsedThisMonth(workspace.getWorkspaceId()));
        aiUsageService.requireFreeQuota(workspace.getWorkspaceId());
    }

    @Test
    void 한도를_넘으면_호출이_차단된다() {
        record(600, 500, true);

        BusinessException e = assertThrows(BusinessException.class,
                () -> aiUsageService.requireFreeQuota(workspace.getWorkspaceId()));

        assertEquals(Code.FORBIDDEN, e.getCode());
        assertTrue(e.getMessage().contains("무료"));
    }

    // 응답을 받지 못한 요청까지 한도를 깎으면 장애가 곧 사용자 손해가 된다.
    @Test
    void 실패한_호출은_한도를_깎지_않는다() {
        record(5_000, 5_000, false);

        assertEquals(0, aiUsageService.freeTokensUsedThisMonth(workspace.getWorkspaceId()));
        aiUsageService.requireFreeQuota(workspace.getWorkspaceId());
    }

    @Test
    void 실패한_호출도_기록에는_남는다() {
        record(100, 0, false);

        List<AiUsageEntity> usages = aiUsageService.usageThisMonth(workspace.getWorkspaceId());
        assertEquals(1, usages.size());
        assertFalse(usages.get(0).isSuccess());
    }

    // ---------- API Key ----------

    @Test
    void 키는_암호문으로_저장되고_원문은_응답에_없다() {
        String plain = "sk-super-secret-key-1234";

        UserApiKeyEntity saved = apiKeyService.register("openai", plain);

        assertNotEquals(plain, saved.getEncryptedKey(), "원문이 그대로 저장되면 안 된다");
        assertFalse(saved.getEncryptedKey().contains(plain));

        UserApiKeyDto dto = UserApiKeyDto.from(saved);
        assertEquals("****1234", dto.maskedKey());
        assertFalse(dto.toString().contains(plain), "DTO 문자열에도 원문이 없어야 한다");
        assertFalse(saved.toString().contains(plain), "엔티티 문자열에도 원문이 없어야 한다");
    }

    @Test
    void 암호문은_같은_키로_다시_읽을_수_있다() {
        String plain = "sk-super-secret-key-1234";
        apiKeyService.register("openai", plain);

        assertEquals(plain, apiKeyService.resolveKey(user.getUserId(), "openai").orElseThrow());
    }

    @Test
    void 같은_제공자에_다시_등록하면_교체된다() {
        apiKeyService.register("openai", "sk-old-key-1111");
        apiKeyService.register("openai", "sk-new-key-2222");

        List<UserApiKeyEntity> keys = apiKeyService.myKeys();
        assertEquals(1, keys.size(), "제공자마다 키는 하나여야 어느 것을 쓸지 모호하지 않다");
        assertEquals("2222", keys.get(0).getKeyHint());
    }

    @Test
    void 남의_키는_지울_수_없다() {
        UserApiKeyEntity mine = apiKeyService.register("openai", "sk-mine-1234");

        UserEntity other = userRepository.save(new UserEntity("other", "other@fixlog.dev"));
        workspaceService.ensurePersonalWorkspace(other);
        loginAs(other);

        assertEquals(Code.NOT_FOUND, assertThrows(BusinessException.class,
                () -> apiKeyService.delete(mine.getId())).getCode());
    }

    @Test
    void 매번_다른_암호문이_나온다() {
        String plain = "sk-same-key-9999";
        String first = secretCipher.encrypt(plain);
        String second = secretCipher.encrypt(plain);

        assertNotEquals(first, second, "같은 IV를 재사용하면 GCM은 안전성을 잃는다");
        assertEquals(plain, secretCipher.decrypt(first));
        assertEquals(plain, secretCipher.decrypt(second));
    }
}
