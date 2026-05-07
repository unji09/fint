import type { Signal } from '@/types/customer';

const SIGNAL_TAG: Record<string, { bg: string; border: string; color: string }> = {
  DART: { bg: '#f2fcff', border: '#bfdbfe', color: '#06b6d4' },
  NEWS: { bg: '#f8fafc', border: '#e2e8f0', color: '#64748b' },
  NEWS_WARN: { bg: '#fff7ed', border: '#fed7aa', color: '#ea580c' },
};

interface SignalItemProps {
  signal: Signal;
  accent: string;
}

export default function SignalItem({ signal, accent }: SignalItemProps) {
  const tagKey = signal.type === 'DART' ? 'DART' : accent === '#fb923c' ? 'NEWS_WARN' : 'NEWS';
  const tag = SIGNAL_TAG[tagKey];
  return (
    <div style={{ display: 'flex', gap: 0, alignItems: 'flex-start' }}>
      <div style={{ width: 16, paddingRight: 12, paddingTop: 3, flexShrink: 0 }}>
        <div style={{ width: 3, height: 38, background: accent, borderRadius: 9999 }} />
      </div>
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 2 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <span
            style={{
              background: tag.bg,
              border: `1px solid ${tag.border}`,
              borderRadius: 4,
              padding: '2px 6px',
              fontSize: 11,
              color: tag.color,
              fontFamily: 'Pretendard,sans-serif',
            }}
          >
            {signal.type}
          </span>
          <span
            style={{
              fontSize: 11,
              fontWeight: 300,
              color: '#94a3b8',
              fontFamily: 'Pretendard,sans-serif',
            }}
          >
            {signal.time}
          </span>
        </div>
        <div
          style={{
            fontSize: 14,
            color: '#1e293b',
            lineHeight: '18px',
            fontFamily: 'Pretendard,sans-serif',
          }}
        >
          {signal.content}
        </div>
      </div>
    </div>
  );
}
