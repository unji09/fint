package com.ssafy.fint.domain.activity.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ssafy.fint.domain.account.entity.QAccount;
import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.entity.QActivity;
import com.ssafy.fint.domain.activity.service.ActivityListFilter;
import com.ssafy.fint.domain.deal.entity.QDeal;
import com.ssafy.fint.domain.deal.entity.QPipelineStage;
import com.ssafy.fint.global.exception.AuthErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class ActivityRepositoryImpl implements ActivityRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Activity> search(ActivityListFilter filter, Pageable pageable) {
        Long tenantId = currentTenantId();

        QActivity activity = QActivity.activity;
        QDeal deal = new QDeal("deal");
        QAccount account = new QAccount("account");
        QPipelineStage stage = new QPipelineStage("stage");

        BooleanBuilder where = new BooleanBuilder()
                .and(activity.user.tenant.tenantId.eq(tenantId));

        if (filter.dealId() != null) {
            where.and(deal.dealId.eq(filter.dealId()));
        }
        if (filter.accountId() != null) {
            where.and(account.accountId.eq(filter.accountId()));
        }
        if (filter.type() != null) {
            where.and(activity.type.eq(filter.type()));
        }

        JPAQuery<Activity> contentQuery = queryFactory
                .selectFrom(activity)
                .leftJoin(activity.pipelineStage, stage).fetchJoin();
        applyDealAccountJoins(contentQuery, filter, activity, deal, account);

        List<Activity> content = contentQuery
                .where(where)
                .orderBy(activity.startAt.desc(), activity.activityId.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(activity.count())
                .from(activity);
        applyDealAccountJoins(countQuery, filter, activity, deal, account);

        Long total = countQuery.where(where).fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }

    private static void applyDealAccountJoins(
            JPAQuery<?> query,
            ActivityListFilter filter,
            QActivity activity,
            QDeal deal,
            QAccount account
    ) {
        if (filter.dealId() != null || filter.accountId() != null) {
            query.leftJoin(activity.deal, deal);
        }
        if (filter.accountId() != null) {
            query.leftJoin(deal.account, account);
        }
    }

    @Override
    public Optional<Activity> findDetail(Long activityId) {
        Long tenantId = currentTenantId();

        QActivity activity = QActivity.activity;
        QDeal deal = new QDeal("deal");
        QPipelineStage stage = new QPipelineStage("stage");

        Activity result = queryFactory
                .selectFrom(activity)
                .leftJoin(activity.deal, deal).fetchJoin()
                .leftJoin(activity.pipelineStage, stage).fetchJoin()
                .where(activity.activityId.eq(activityId)
                        .and(activity.user.tenant.tenantId.eq(tenantId)))
                .fetchOne();

        return Optional.ofNullable(result);
    }

    private Long currentTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails me)) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }
        return me.getTenantId();
    }
}
