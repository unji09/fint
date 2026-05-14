package com.ssafy.fint.domain.signal.repository;

import com.ssafy.fint.domain.signal.entity.NewsArticle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {

    Optional<NewsArticle> findByLink(String link);
}
