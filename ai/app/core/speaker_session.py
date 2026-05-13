from __future__ import annotations

import math


class SpeakerSession:
    """실시간 청크 단위 화자 할당. 인메모리 cosine similarity 클러스터링."""

    def __init__(self, threshold: float = 0.75) -> None:
        self._profiles: dict[str, list[float]] = {}
        self._counts: dict[str, int] = {}
        self._threshold = threshold

    @staticmethod
    def _cosine_similarity(a: list[float], b: list[float]) -> float:
        dot = sum(x * y for x, y in zip(a, b))
        norm_a = math.sqrt(sum(x * x for x in a))
        norm_b = math.sqrt(sum(x * x for x in b))
        if norm_a == 0 or norm_b == 0:
            return 0.0
        return dot / (norm_a * norm_b)

    def assign(self, embedding: list[float]) -> str:
        if not embedding or all(v == 0.0 for v in embedding):
            return f"SPEAKER_{len(self._profiles):02d}"

        best_id: str | None = None
        best_sim = -1.0

        for speaker_id, centroid in self._profiles.items():
            sim = self._cosine_similarity(embedding, centroid)
            if sim > best_sim:
                best_sim = sim
                best_id = speaker_id

        if best_id is None or best_sim < self._threshold:
            return self._new_speaker(embedding)

        n = self._counts[best_id]
        self._profiles[best_id] = [
            (c * n + e) / (n + 1)
            for c, e in zip(self._profiles[best_id], embedding)
        ]
        self._counts[best_id] = n + 1
        return best_id

    def _new_speaker(self, embedding: list[float]) -> str:
        speaker_id = f"SPEAKER_{len(self._profiles):02d}"
        self._profiles[speaker_id] = list(embedding)
        self._counts[speaker_id] = 1
        return speaker_id


class SpeakerSessionManager:
    """tenant_id:activity_id 키로 SpeakerSession 관리. 싱글톤."""

    def __init__(self) -> None:
        self._sessions: dict[str, SpeakerSession] = {}

    def get_or_create(self, key: str) -> SpeakerSession:
        if key not in self._sessions:
            self._sessions[key] = SpeakerSession()
        return self._sessions[key]

    def remove(self, key: str) -> None:
        self._sessions.pop(key, None)
