package com.ssafy.fint.domain.signal.repository;

import com.ssafy.fint.domain.signal.entity.AccountNewsArticle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountNewsArticleRepository extends JpaRepository<AccountNewsArticle, Long> {

    boolean existsByAccount_AccountIdAndNewsArticle_NewsArticleId(Long accountId, Long newsArticleId);
}
