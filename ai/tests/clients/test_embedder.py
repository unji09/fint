from __future__ import annotations

import numpy as np

from app.clients.embedder import EmbedderClient, OnnxEmbedderClient


def test_onnx_embedder_satisfies_protocol():
    client = TestOnnxEmbedderClient._make_client()
    assert isinstance(client, EmbedderClient)


class TestOnnxEmbedderClient:
    """ONNX 런타임 없이 stub session으로 테스트."""

    @staticmethod
    def _make_client(dim: int = 384) -> OnnxEmbedderClient:
        client = OnnxEmbedderClient.__new__(OnnxEmbedderClient)
        client._dim = dim
        client._session = _StubSession(dim)
        client._tokenizer = _StubTokenizer()
        return client

    def test_embed_single_text(self):
        client = self._make_client()
        vectors = client.embed(["query: 테스트 문장"])

        assert isinstance(vectors, np.ndarray)
        assert vectors.shape == (1, 384)

    def test_embed_multiple_texts(self):
        client = self._make_client()
        vectors = client.embed(["query: 첫번째", "passage: 두번째", "query: 세번째"])

        assert vectors.shape == (3, 384)

    def test_embed_returns_normalized_vectors(self):
        client = self._make_client()
        vectors = client.embed(["query: 정규화 확인"])

        norms = np.linalg.norm(vectors, axis=1)
        np.testing.assert_allclose(norms, 1.0, atol=1e-6)

    def test_embed_empty_list_returns_empty_array(self):
        client = self._make_client()
        vectors = client.embed([])

        assert vectors.shape == (0, 384)

    def test_embed_query_returns_single_vector(self):
        client = self._make_client()
        vector = client.embed_query("삼성전자 반도체")

        assert vector.shape == (384,)

    def test_embed_query_adds_prefix(self):
        client = self._make_client()
        client._tokenizer = _PrefixCapturingTokenizer()
        client.embed_query("테스트")

        assert client._tokenizer.last_texts[0].startswith("query: ")

    def test_embed_passages_adds_prefix(self):
        client = self._make_client()
        client._tokenizer = _PrefixCapturingTokenizer()
        client.embed_passages(["텍스트1", "텍스트2"])

        assert all(t.startswith("passage: ") for t in client._tokenizer.last_texts)

    def test_embed_passages_returns_correct_shape(self):
        client = self._make_client()
        vectors = client.embed_passages(["문장1", "문장2"])

        assert vectors.shape == (2, 384)

    def test_dimension_property(self):
        client = self._make_client(dim=384)
        assert client.dimension == 384


class _StubTokenizer:
    def encode_batch(self, texts: list[str]) -> list[_StubEncoding]:
        max_len = max((len(t) for t in texts), default=0)
        max_len = min(max_len, 128)
        return [_StubEncoding(len(t), max_len) for t in texts]


class _StubEncoding:
    def __init__(self, length: int, padded_length: int) -> None:
        real_len = min(length, padded_length)
        self.ids = list(range(real_len)) + [0] * (padded_length - real_len)
        self.attention_mask = [1] * real_len + [0] * (padded_length - real_len)


class _PrefixCapturingTokenizer(_StubTokenizer):
    def __init__(self) -> None:
        self.last_texts: list[str] = []

    def encode_batch(self, texts: list[str]) -> list[_StubEncoding]:
        self.last_texts = texts
        return super().encode_batch(texts)


class _StubSession:
    def __init__(self, dim: int) -> None:
        self._dim = dim

    def run(self, _output_names, input_feed: dict) -> list[np.ndarray]:
        batch_size, seq_len = input_feed["input_ids"].shape
        rng = np.random.default_rng(42)
        raw = rng.standard_normal((batch_size, seq_len, self._dim)).astype(np.float32)
        return [raw]
