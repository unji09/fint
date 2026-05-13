export default function GridBg() {
  return (
    <div style={{ position: 'absolute', inset: 0, pointerEvents: 'none' }}>
      {/* 그리드 라인 */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          backgroundImage:
            'linear-gradient(rgba(99,118,183,0.10) 1px, transparent 1px),' +
            'linear-gradient(90deg, rgba(99,118,183,0.10) 1px, transparent 1px)',
          backgroundSize: '20px 20px',
        }}
      />
      {/* 위쪽 가벼운 highlight + 가장자리 음영 — 평면을 입체적으로 */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          background:
            'radial-gradient(ellipse 70% 50% at 50% 0%, rgba(255,255,255,0.5) 0%, rgba(255,255,255,0) 60%),' +
            'radial-gradient(ellipse 100% 100% at 50% 50%, transparent 55%, rgba(15,23,42,0.06) 100%)',
        }}
      />
    </div>
  );
}
