"""뉴스 의미적 중복 제거 단위 테스트."""
from datetime import datetime, timezone, timedelta

import numpy as np
import pytest

from app.clients.naver import NaverNewsItem
from app.news.naver_collector import CollectedArticleData
from app.news.dedup import deduplicate_articles, SIMILARITY_THRESHOLD


DIM = 384
DAY1 = datetime(2026, 5, 14, 9, 0, tzinfo=timezone.utc)
DAY2 = datetime(2026, 5, 15, 9, 0, tzinfo=timezone.utc)


def _normalized(vec: np.ndarray) -> list[float]:
    norm = np.linalg.norm(vec)
    if norm < 1e-9:
        return vec.tolist()
    return (vec / norm).tolist()


def _make_embedding(seed: int) -> list[float]:
    rng = np.random.RandomState(seed)
    return _normalized(rng.randn(DIM).astype(np.float32))


# 동일 임베딩 → 유사도 1.0 (확실히 병합 대상)
EMBED_A = _make_embedding(42)
# 완전히 다른 임베딩 → 유사도 낮음 (병합 안 됨)
EMBED_B = _make_embedding(99)
EMBED_C = _make_embedding(7)


def _make_article(
    title: str = "삼성전자 실적 발표",
    description: str = "삼성전자가 실적을 발표했다",
    link: str = "https://n.news.naver.com/1",
    pub_date: datetime = DAY1,
    account_ids: list[int] | None = None,
    title_embedding: list[float] | None = None,
    summary_embedding: list[float] | None = None,
) -> CollectedArticleData:
    return CollectedArticleData(
        item=NaverNewsItem(
            title=title,
            original_link=f"https://original.com/{link}",
            link=link,
            description=description,
            pub_date=pub_date,
        ),
        title_embedding=title_embedding if title_embedding is not None else EMBED_A,
        summary_embedding=summary_embedding if summary_embedding is not None else EMBED_A,
        account_ids=account_ids if account_ids is not None else [1],
    )


class TestEmptyAndSingle:
    def test_empty_input(self):
        result = deduplicate_articles([])
        assert result == []

    def test_single_article(self):
        article = _make_article()
        result = deduplicate_articles([article])
        assert len(result) == 1
        assert result[0] is article


class TestIdenticalTitlesMerged:
    def test_identical_titles_are_merged(self):
        a1 = _make_article(
            title="삼성전자 실적 발표",
            link="https://link1",
            title_embedding=EMBED_A,
            account_ids=[1],
        )
        a2 = _make_article(
            title="삼성전자 실적 발표",
            link="https://link2",
            title_embedding=EMBED_A,
            account_ids=[2],
        )
        result = deduplicate_articles([a1, a2])
        assert len(result) == 1


class TestSimilarTitlesMerged:
    def test_similar_titles_are_merged(self):
        """동일 임베딩(유사도 1.0)이면 같은 날짜일 때 병합된다."""
        a1 = _make_article(
            title="삼성전자 2분기 실적 호조",
            link="https://link1",
            title_embedding=EMBED_A,
        )
        a2 = _make_article(
            title="삼성 2Q 어닝서프라이즈",
            link="https://link2",
            title_embedding=EMBED_A,
        )
        result = deduplicate_articles([a1, a2])
        assert len(result) == 1


class TestDifferentTopicsPreserved:
    def test_different_topics_preserved(self):
        """완전히 다른 임베딩은 병합되지 않는다."""
        a1 = _make_article(
            title="삼성전자 실적 발표",
            link="https://link1",
            title_embedding=EMBED_A,
        )
        a2 = _make_article(
            title="현대차 신차 출시",
            link="https://link2",
            title_embedding=EMBED_B,
        )
        result = deduplicate_articles([a1, a2])
        assert len(result) == 2


class TestDifferentDatesNotMerged:
    def test_different_dates_not_merged(self):
        """유사도가 높아도 날짜가 다르면 병합하지 않는다."""
        a1 = _make_article(
            title="삼성전자 실적 발표",
            link="https://link1",
            pub_date=DAY1,
            title_embedding=EMBED_A,
        )
        a2 = _make_article(
            title="삼성전자 실적 발표",
            link="https://link2",
            pub_date=DAY2,
            title_embedding=EMBED_A,
        )
        result = deduplicate_articles([a1, a2])
        assert len(result) == 2


class TestAccountIdsMerged:
    def test_account_ids_merged(self):
        """병합 시 클러스터 내 모든 account_ids가 합산된다."""
        a1 = _make_article(
            title="삼성전자 실적",
            link="https://link1",
            title_embedding=EMBED_A,
            account_ids=[1, 3],
        )
        a2 = _make_article(
            title="삼성전자 실적",
            link="https://link2",
            title_embedding=EMBED_A,
            account_ids=[2, 3],
        )
        result = deduplicate_articles([a1, a2])
        assert len(result) == 1
        assert sorted(result[0].account_ids) == [1, 2, 3]


class TestRepresentativeSelection:
    def test_longer_description_wins(self):
        """description이 긴 기사가 대표로 선정된다."""
        short = _make_article(
            title="삼성전자 실적",
            description="짧은 요약",
            link="https://short",
            title_embedding=EMBED_A,
            account_ids=[1],
        )
        long = _make_article(
            title="삼성전자 실적 상세",
            description="삼성전자가 2분기 실적을 발표했다. 매출은 전년 동기 대비 15% 증가했으며 영업이익도 크게 개선되었다.",
            link="https://long",
            title_embedding=EMBED_A,
            account_ids=[2],
        )
        result = deduplicate_articles([short, long])
        assert len(result) == 1
        assert result[0].item.link == "https://long"
        assert sorted(result[0].account_ids) == [1, 2]

    def test_more_accounts_wins_on_tie(self):
        """description 길이가 같으면 account_ids가 많은 기사가 대표."""
        a1 = _make_article(
            title="기사 A",
            description="동일한 길이 설명문",
            link="https://link1",
            title_embedding=EMBED_A,
            account_ids=[1],
        )
        a2 = _make_article(
            title="기사 B",
            description="동일한 길이 설명문",
            link="https://link2",
            title_embedding=EMBED_A,
            account_ids=[2, 3],
        )
        result = deduplicate_articles([a1, a2])
        assert len(result) == 1
        assert result[0].item.link == "https://link2"

    def test_earlier_pub_date_wins_on_full_tie(self):
        """description 길이와 account 수 모두 같으면 빠른 기사가 대표."""
        early = _make_article(
            title="기사 A",
            description="같은 길이",
            link="https://early",
            pub_date=DAY1,
            title_embedding=EMBED_A,
            account_ids=[1],
        )
        late = _make_article(
            title="기사 B",
            description="같은 길이",
            link="https://late",
            pub_date=DAY1 + timedelta(hours=3),
            title_embedding=EMBED_A,
            account_ids=[2],
        )
        result = deduplicate_articles([early, late])
        assert len(result) == 1
        assert result[0].item.link == "https://early"


class TestNoEmbeddingsSkipsDedup:
    def test_no_embeddings_skips_dedup(self):
        """임베딩이 빈 리스트면 중복 제거를 스킵한다."""
        a1 = _make_article(
            title="기사 A",
            link="https://link1",
            title_embedding=[],
        )
        a2 = _make_article(
            title="기사 B",
            link="https://link2",
            title_embedding=[],
        )
        result = deduplicate_articles([a1, a2])
        assert len(result) == 2


class TestMultipleClusters:
    def test_three_topics_two_duplicates(self):
        """3개 주제 중 2개가 각각 중복 → 3건으로 축소."""
        articles = [
            _make_article(title="삼성 실적 A", link="https://1", title_embedding=EMBED_A, account_ids=[1]),
            _make_article(title="삼성 실적 B", link="https://2", title_embedding=EMBED_A, account_ids=[2]),
            _make_article(title="현대차 신차", link="https://3", title_embedding=EMBED_B, account_ids=[1]),
            _make_article(title="현대차 출시", link="https://4", title_embedding=EMBED_B, account_ids=[3]),
            _make_article(title="카카오 AI", link="https://5", title_embedding=EMBED_C, account_ids=[1]),
        ]
        result = deduplicate_articles(articles)
        assert len(result) == 3
        all_links = {a.item.link for a in result}
        assert "https://5" in all_links
