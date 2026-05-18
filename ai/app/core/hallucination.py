"""Whisper 환각(hallucination) 필터 유틸리티.

배치 STT(stt.py)와 실시간 스트림 STT(stt_stream.py) 양쪽에서 공유한다.
"""

# Whisper가 YouTube/방송 학습 데이터 또는 initial_prompt에서 복사하는 알려진 환각 패턴.
# 키워드 단위로 등록해 변형도 잡는다.
HALLUCINATION_PATTERNS: tuple[str, ...] = (
    "자막은 설정에서",
    "시청해주셔서 감사합니다",
    "구독과 좋아요",
    "자막 제공",
    "자막을 사용",
    "다음 영상에서 만나요",
    "제작지원으로 제작",
    "미팅 내용을 전사합니다",   # "영업 미팅 내용을 전사합니다" 포함 — initial_prompt 반복
    "MBC 뉴스",
    "KBS 뉴스",
    "SBS 뉴스",
    "翻訳",
    "字幕",
    "Subtitles by",
    "amara.org",
)

# Whisper no_speech_prob 임계값 — 이 이상이면 hallucination으로 간주하고 버린다
NO_SPEECH_THRESHOLD: float = 0.6

# 환각 패턴 제거 후 남은 실제 발화로 간주하기 위한 최소 글자 수.
# 한국어는 한 글자가 영어 단어 수준의 정보량을 담으므로 4자로 설정.
MIN_REAL_LEN: int = 4


def is_hallucination(text: str) -> bool:
    """배치 전사 결과에서 환각 세그먼트를 판별한다 (완전 포함 여부)."""
    return any(p in text for p in HALLUCINATION_PATTERNS)


def clean_stream_text(text: str, no_speech_prob: float) -> str:
    """실시간 스트림 전사 텍스트에서 환각 패턴을 제거하고 실제 발화를 반환한다.

    - no_speech_prob ≥ NO_SPEECH_THRESHOLD → 전체 무음으로 판단, "" 반환
    - 환각 패턴이 텍스트 안에 있으면 가장 이른 위치(earliest match)에서 잘라낸다
    - 잘라낸 뒤 남은 텍스트가 MIN_REAL_LEN 미만이면 "" 반환
    """
    if no_speech_prob >= NO_SPEECH_THRESHOLD:
        return ""

    indices = [text.find(p) for p in HALLUCINATION_PATTERNS]
    earliest = min((i for i in indices if i >= 0), default=-1)
    if earliest >= 0:
        text = text[:earliest].rstrip(" .,。!?　")

    if len(text) < MIN_REAL_LEN:
        return ""
    return text
