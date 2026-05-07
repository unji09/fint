interface Props {
  value: string;
  onChange: (v: string) => void;
  onSubmit: (v: string) => void;
  loading: boolean;
}

export default function QueryBar({ value, onChange, onSubmit, loading }: Props) {
  return (
    <div style={{ position: 'relative', width: 390 }}>
      <div
        style={{
          position: 'absolute',
          inset: -2,
          borderRadius: 32,
          background: 'linear-gradient(to right,rgba(6,182,212,0.2),rgba(0,104,122,0.2))',
          filter: 'blur(4px)',
          opacity: 0.4,
          pointerEvents: 'none',
        }}
      />
      <div
        style={{
          position: 'relative',
          background: 'rgba(248,250,255,0.78)',
          backdropFilter: 'blur(16px) saturate(180%)',
          WebkitBackdropFilter: 'blur(16px) saturate(180%)',
          borderRadius: 32,
          display: 'flex',
          alignItems: 'center',
          padding: 7,
          border: '1px solid rgba(255,255,255,0.9)',
          boxShadow: '0 6px 20px rgba(0,104,122,0.1)',
        }}
      >
        <div style={{ paddingLeft: 12, paddingRight: 8, flexShrink: 0 }}>
          <svg width="15" height="15" viewBox="0 0 18 18" fill="none">
            <circle cx="8" cy="8" r="5.5" stroke="#9CA3AF" strokeWidth="1.5" />
            <path d="M12.5 12.5L16 16" stroke="#9CA3AF" strokeWidth="1.5" strokeLinecap="round" />
          </svg>
        </div>
        <input
          style={{
            flex: 1,
            height: 38,
            fontSize: 14,
            fontFamily: 'Pretendard,sans-serif',
            fontWeight: loading ? 600 : 500,
            outline: 'none',
            border: 'none',
            background: 'transparent',
            color: '#64748b',
            minWidth: 0,
          }}
          placeholder={loading ? '분석하고 있어요...' : '무엇을 분석할까요?'}
          value={loading ? '' : value}
          disabled={loading}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !loading) onSubmit(value);
          }}
        />
        <button
          onClick={() => !loading && onSubmit(value)}
          style={{
            width: 42,
            height: 42,
            borderRadius: 12,
            flexShrink: 0,
            background: 'linear-gradient(135deg,#06b6d4 0%,#0891b2 100%)',
            border: 'none',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: '0 2px 8px rgba(6,182,212,0.3)',
          }}
        >
          {loading ? (
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="white"
              style={{ animation: 'spin 1s linear infinite' }}
            >
              <path d="M12 2A10 10 0 0 0 2 12h2a8 8 0 0 1 8-8V2z" />
            </svg>
          ) : (
            <svg width="13" height="13" viewBox="0 0 16 16" fill="none">
              <path
                d="M8 13V3M8 3L4 7M8 3L12 7"
                stroke="white"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          )}
        </button>
      </div>
    </div>
  );
}
