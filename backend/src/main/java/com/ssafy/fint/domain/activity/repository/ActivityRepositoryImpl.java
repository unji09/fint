package com.ssafy.fint.domain.activity.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ssafy.fint.domain.account.entity.QAccount;
import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.entity.QActivity;
import com.ssafy.fint.domain.activity.service.ActivityListFilter;
import com.ssafy.fint.domain.deal.entity.QDeal;
import com.ssafy.fint.domain.deal.entity.QPipelineStage;
import com.ssafy.fint.domain.user.entity.QUser;
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

@RequiredArgsConstructor
public class ActivityRepositoryImpl implements ActivityRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Activity> search(ActivityListFilter filter, Pageable pageable) {
        Long tenantId = currentTenantId();

        QActivity activity = QActivity.activity;
        QDeal deal = new QDeal("deal");
        QAccount account = new QAccount("account");
        QUser owner = new QUser("owner");
        QPipelineStage stage = new QPipelineStage("stage");

        BooleanBuilder where = new BooleanBuilder()
                .and(owner.tenant.tenantId.eq(tenantId)
                        .or(stage.tenant.tenantId.eq(tenantId)));

        if (filter.accountId() != null) {
            where.and(account.accountId.eq(filter.accountId()));
        }
        if (filter.dealId() != null) {
            where.and(deal.dealId.eq(filter.dealId()));
        }
        if (filter.type() != null) {
            where.and(activity.type.eq(filter.type()));
        }

        List<Activity> content = queryFactory
                .selectFrom(activity)
                .leftJoin(activity.deal, deal)
                .leftJoin(deal.account, account)
                .leftJoin(account.user, owner)
                .leftJoin(activity.pipelineStage, stage).fetchJoin()
                .where(where)
                .orderBy(activity.startAt.desc(), activity.activityId.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(activity.count())
                .from(activity)
                .leftJoin(activity.deal, deal)
                .leftJoin(deal.account, account)
                .leftJoin(account.user, owner)
                .leftJoin(activity.pipelineStage, stage)
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }

    private Long currentTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails me)) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }
        return me.getTenantId();
    }
}
