export default function GridBg() {
  return (
    <div style={{ position: 'absolute', inset: 0, pointerEvents: 'none' }}>
      <div style={{ position: 'absolute', inset: 0, backgroundColor: '#eef1ff' }} />
      <div
        style={{
          position: 'absolute',
          inset: 0,
          backgroundImage: `linear-gradient(rgba(99,118,183,0.13) 1px, transparent 1px),
          linear-gradient(90deg, rgba(99,118,183,0.13) 1px, transparent 1px)`,
          backgroundSize: '20px 20px',
        }}
      />
      <div
        style={{
          position: 'absolute',
          inset: 0,
          background:
            'radial-gradient(ellipse 70% 70% at 50% 40%, transparent 40%, rgba(180,190,230,0.18) 100%)',
        }}
      />
      <div
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          height: 120,
          background: 'linear-gradient(to bottom, rgba(255,255,255,0.4), transparent)',
        }}
      />
    </div>
  );
}
