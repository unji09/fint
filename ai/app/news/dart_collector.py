"""DART 전자공시 수집기 — 추후 구현."""
from __future__ import annotations

from dataclasses import dataclass


@dataclass
class DartCollectResult:
    inserted: int
    linked: int
    skipped: int


class DartCollector:
    async def collect_for_accounts(self, accounts: list, db: object) -> DartCollectResult:
        raise NotImplementedError("DART 수집은 추후 구현 예정")