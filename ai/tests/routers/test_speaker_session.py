import math

import pytest

from app.core.speaker_session import SpeakerSession, SpeakerSessionManager


def _make_embedding(dim: int = 256, value: float = 1.0) -> list[float]:
    """단위 벡터 생성."""
    raw = [value] * dim
    norm = math.sqrt(sum(x * x for x in raw))
    return [x / norm for x in raw]


def _make_orthogonal_embedding(dim: int = 256) -> list[float]:
    """첫 번째 차원만 1인 벡터."""
    v = [0.0] * dim
    v[0] = 1.0
    return v


def _make_orthogonal_embedding2(dim: int = 256) -> list[float]:
    """두 번째 차원만 1인 벡터."""
    v = [0.0] * dim
    v[1] = 1.0
    return v


class TestSpeakerSession:
    def test_first_embedding_creates_speaker_00(self):
        session = SpeakerSession()
        emb = _make_embedding()
        speaker_id = session.assign(emb)
        assert speaker_id == "SPEAKER_00"

    def test_similar_embedding_same_speaker(self):
        session = SpeakerSession(threshold=0.75)
        emb1 = _make_embedding(value=1.0)
        emb2 = _make_embedding(value=1.0)

        id1 = session.assign(emb1)
        id2 = session.assign(emb2)

        assert id1 == id2 == "SPEAKER_00"

    def test_orthogonal_embedding_new_speaker(self):
        session = SpeakerSession(threshold=0.75)
        emb1 = _make_orthogonal_embedding()
        emb2 = _make_orthogonal_embedding2()

        id1 = session.assign(emb1)
        id2 = session.assign(emb2)

        assert id1 == "SPEAKER_00"
        assert id2 == "SPEAKER_01"

    def test_zero_vector_returns_fallback_without_storing_profile(self):
        session = SpeakerSession()
        zero = [0.0] * 256
        id1 = session.assign(zero)
        assert id1 == "SPEAKER_00"
        assert len(session._profiles) == 0

    def test_zero_vector_does_not_pollute_subsequent_assignments(self):
        session = SpeakerSession(threshold=0.75)
        zero = [0.0] * 256
        real = _make_embedding()

        session.assign(zero)
        id_real = session.assign(real)

        assert id_real == "SPEAKER_00"

    def test_multiple_speakers(self):
        session = SpeakerSession(threshold=0.75)
        embeddings = [
            _make_orthogonal_embedding(),
            _make_orthogonal_embedding2(),
            _make_orthogonal_embedding(),
        ]

        ids = [session.assign(e) for e in embeddings]

        assert ids[0] == "SPEAKER_00"
        assert ids[1] == "SPEAKER_01"
        assert ids[2] == "SPEAKER_00"


class TestSpeakerSessionManager:
    def test_get_or_create_returns_same_session(self):
        manager = SpeakerSessionManager()
        key = "1:activity_001"

        s1 = manager.get_or_create(key)
        s2 = manager.get_or_create(key)

        assert s1 is s2

    def test_different_keys_different_sessions(self):
        manager = SpeakerSessionManager()

        s1 = manager.get_or_create("1:activity_001")
        s2 = manager.get_or_create("1:activity_002")

        assert s1 is not s2

    def test_remove_session(self):
        manager = SpeakerSessionManager()
        key = "1:activity_001"

        s1 = manager.get_or_create(key)
        manager.remove(key)
        s2 = manager.get_or_create(key)

        assert s1 is not s2

    def test_remove_nonexistent_key_no_error(self):
        manager = SpeakerSessionManager()
        manager.remove("nonexistent")
