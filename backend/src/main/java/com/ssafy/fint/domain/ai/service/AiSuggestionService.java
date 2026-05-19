package com.ssafy.fint.domain.ai.service;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.ai.client.NextActionAiResponse;
import com.ssafy.fint.domain.ai.client.NextActionClient;
import com.ssafy.fint.domain.ai.dto.NextActionCreateRequest;
import com.ssafy.fint.domain.ai.dto.NextActionCreateResponse;
import com.ssafy.fint.domain.ai.dto.NextActionDetailResponse;
import com.ssafy.fint.domain.ai.dto.NextActionListResponse;
import com.ssafy.fint.domain.ai.entity.AiSuggestion;
import com.ssafy.fint.domain.ai.entity.AiSuggestionRelatedType;
import com.ssafy.fint.domain.ai.entity.TriggerType;
import com.ssafy.fint.domain.ai.repository.AiSuggestionRepository;
import com.ssafy.fint.domain.deal.entity.PipelineStage;
import com.ssafy.fint.domain.deal.repository.PipelineStageRepository;
import com.ssafy.fint.domain.notification.service.NotificationService;
import com.ssafy.fint.global.exception.AccountErrorCode;
import com.ssafy.fint.global.exception.AiErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiSuggestionService {

    private static final double URGENT_NOTIFICATION_THRESHOLD = 4.0;

    private final AccountRepository accountRepository;
    private final AiSuggestionRepository aiSuggestionRepository;
    private final PipelineStageRepository pipelineStageRepository;
    private final NextActionClient nextActionClient;
    private final NotificationService notificationService;

    @Transactional
    public List<NextActionCreateResponse> createNextAction(CustomUserDetails me, NextActionCreateRequest request) {
        Long tenantId = me.getTenantId();
        Long accountId = request.accountId();

        Account account = accountRepository.findByIdAndTenantId(accountId, tenantId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        List<NextActionAiResponse> aiResponses = nextActionClient.generate(tenantId, request);

        List<NextActionCreateResponse> results = new ArrayList<>();

        for (NextActionAiResponse aiResponse : aiResponses) {
            PipelineStage stage = pipelineStageRepository
                    .findByPipelineStageIdAndTenant_TenantId(aiResponse.pipelineStageId(), tenantId)
                    .orElseThrow(() -> new BusinessException(AiErrorCode.INVALID_AI_RESPONSE));

            AiSuggestionRelatedType relatedType = resolveRelatedType(aiResponse, request);
            String category = aiResponse.category() != null ? aiResponse.category() : "GENERAL";
            int successProbability = aiResponse.successProbability() != null ? aiResponse.successProbability() : 0;
            double importanceScore = aiResponse.importanceScore() != null ? aiResponse.importanceScore() : 0.0;

            Map<String, Object> reason = new HashMap<>();
            reason.put("sources", aiResponse.sources());
            reason.put("recommendedScript", aiResponse.recommendedScript());

            AiSuggestion suggestion = AiSuggestion.builder()
                    .account(account)
                    .pipelineStage(stage)
                    .title(aiResponse.action())
                    .content(aiResponse.reason())
                    .relatedType(relatedType)
                    .category(category)
                    .successProbability(successProbability)
                    .importanceScore(importanceScore)
                    .reason(reason)
                    .build();

            AiSuggestion saved = aiSuggestionRepository.save(suggestion);
            if (saved.getImportanceScore() >= URGENT_NOTIFICATION_THRESHOLD) {
                notificationService.pushNotification(saved);
            }
            results.add(NextActionCreateResponse.from(saved));
        }

        return results;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createNextActionBySystem(Long tenantId, NextActionCreateRequest request) {
        Long accountId = request.accountId();

        Account account = accountRepository.findByIdAndTenantId(accountId, tenantId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        List<NextActionAiResponse> aiResponses = nextActionClient.generate(tenantId, request);

        for (NextActionAiResponse aiResponse : aiResponses) {
            PipelineStage stage = pipelineStageRepository
                    .findByPipelineStageIdAndTenant_TenantId(aiResponse.pipelineStageId(), tenantId)
                    .orElseThrow(() -> new BusinessException(AiErrorCode.INVALID_AI_RESPONSE));

            AiSuggestionRelatedType relatedType = resolveRelatedType(aiResponse, request);
            String category = aiResponse.category() != null ? aiResponse.category() : "GENERAL";
            int successProbability = aiResponse.successProbability() != null ? aiResponse.successProbability() : 0;
            double importanceScore = aiResponse.importanceScore() != null ? aiResponse.importanceScore() : 0.0;

            Map<String, Object> reason = new HashMap<>();
            reason.put("sources", aiResponse.sources());
            reason.put("recommendedScript", aiResponse.recommendedScript());

            AiSuggestion suggestion = AiSuggestion.builder()
                    .account(account)
                    .pipelineStage(stage)
                    .title(aiResponse.action())
                    .content(aiResponse.reason())
                    .relatedType(relatedType)
                    .category(category)
                    .successProbability(successProbability)
                    .importanceScore(importanceScore)
                    .reason(reason)
                    .build();

            AiSuggestion saved = aiSuggestionRepository.save(suggestion);
            if (saved.getImportanceScore() >= URGENT_NOTIFICATION_THRESHOLD) {
                notificationService.pushNotification(saved);
            }
        }
    }

    public List<NextActionListResponse> findNextActions(CustomUserDetails me, Long accountId) {
        Long tenantId = me.getTenantId();
        accountRepository.findByIdAndTenantId(accountId, tenantId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        return aiSuggestionRepository.findAllByAccountIdAndTenantId(accountId, tenantId)
                .stream()
                .map(NextActionListResponse::from)
                .toList();
    }

    public NextActionDetailResponse findNextActionDetail(CustomUserDetails me, Long accountId, Long suggestionId) {
        Long tenantId = me.getTenantId();
        accountRepository.findByIdAndTenantId(accountId, tenantId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        return aiSuggestionRepository.findByIdAndAccountIdAndTenantId(suggestionId, accountId, tenantId)
                .map(NextActionDetailResponse::from)
                .orElseThrow(() -> new BusinessException(AiErrorCode.AI_SUGGESTION_NOT_FOUND));
    }

    @Transactional
    public void createDummyUrgentSignal(Long tenantId, Long accountId, double importanceScore) {
        Account account = accountRepository.findByIdAndTenantId(accountId, tenantId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        PipelineStage stage = pipelineStageRepository
                .findFirstByTenant_TenantIdOrderBySortOrderAsc(tenantId)
                .orElseThrow(() -> new BusinessException(AiErrorCode.INVALID_AI_RESPONSE));

        Map<String, Object> sources = Map.of(
                "news", List.of(
                        Map.of(
                                "title", "삼성전자, 2026년 AI 반도체에 110조 투자…역대 최대 규모",
                                "summary", "삼성전자가 2026년 시설 투자와 R&D에 총 110조 원 이상을 투입한다고 밝혔다. AI 데이터센터 시대에 메모리 수요가 폭발할 것으로 전망되며, HBM4와 차세대 저전력 메모리 모듈 SOCAMM2를 동시 양산 개시했다.",
                                "url", "https://www.hankyung.com/article/202605132789i"
                        ),
                        Map.of(
                                "title", "삼성전자 2026년 정기 임원 인사 – 미래 리더로 세대교체 가속",
                                "summary", "삼성전자가 2026년 정기 임원 인사에서 부사장 51명, 상무 93명 등 총 161명을 승진시켰다. AI·로봇·반도체 분야 미래 기술 리더를 중용하며 세대교체를 가속화했다.",
                                "url", "https://news.samsung.com/kr/%EC%82%BC%EC%84%B1%EC%A0%84%EC%9E%90-2026%EB%85%84-%EC%A0%95%EA%B8%B0-%EC%9E%84%EC%9B%90-%EC%9D%B8%EC%82%AC"
                        )
                ),
                "dart", List.of(
                        Map.of(
                                "title", "삼성전자 2026년 1분기 실적 발표",
                                "summary", "반도체 부문 영업이익 대폭 개선. AI 반도체 매출 비중 확대로 전사 수익성 회복세.",
                                "url", "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20260415000123"
                        )
                ),
                "crm", List.of(
                        Map.of("summary", "삼성전자 IT 본부 조직 개편으로 기존 챔피언(김 부장) 부서 이동 확인. 후임 담당자 미정 상태.")
                )
        );

        Map<String, Object> reason = Map.of(
                "sources", sources,
                "recommendedScript", "챔피언, 백업 챔피언에게 연락하여 Champion 위기 대응 (조직 개편/이직 시그널)을(를) 진행하세요.\n기대 결과: deal 보호, 백업 챔피언으로 전환"
        );

        AiSuggestion suggestion = AiSuggestion.builder()
                .account(account)
                .pipelineStage(stage)
                .title("Champion 위기 대응 (조직 개편/이직 시그널)")
                .content("**이유**: 조직 개편 발표; 챔피언 응답 지연; 이 단계 35일째 · **대상**: 챔피언, 백업 챔피언 · **기대**: deal 보호, 백업 챔피언으로 전환")
                .relatedType(AiSuggestionRelatedType.ACCOUNT)
                .category("Champion Building & Multi-thread")
                .successProbability(68)
                .importanceScore(importanceScore)
                .reason(reason)
                .build();

        AiSuggestion saved = aiSuggestionRepository.save(suggestion);

        if (saved.getImportanceScore() >= URGENT_NOTIFICATION_THRESHOLD) {
            notificationService.pushNotification(saved);
        }
    }

    private AiSuggestionRelatedType resolveRelatedType(NextActionAiResponse aiResponse, NextActionCreateRequest request) {
        if (aiResponse.relatedType() != null) {
            try {
                return AiSuggestionRelatedType.valueOf(aiResponse.relatedType().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (request.triggerType() == TriggerType.MEETING_CREATED || request.meetingId() != null) {
            return AiSuggestionRelatedType.MEETING;
        }
        return AiSuggestionRelatedType.ACCOUNT;
    }
}
