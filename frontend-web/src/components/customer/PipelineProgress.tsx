const STAGES = ['발굴', '가치 제안', '솔루션 설계', '제안 제출', '협상', '계약 대기', '수주'];

interface Props {
  current: number;
  onStageClick?: (stageIndex: number, stageName: string) => void;
}

export default function PipelineProgress({ current, onStageClick }: Props) {
  return (
    <div style={{ position: 'relative', display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', padding: '10px 0 30px' }}>
      <div style={{ position: 'absolute', top: 18, left: 10, right: 10, height: 4, background: '#f3f4f6', borderRadius: 9999 }} />
      <div style={{ position: 'absolute', top: 18, left: 10, right: `${(1 - current / (STAGES.length - 1)) * 100}%`, height: 4, background: '#06b6d4', borderRadius: 9999 }} />
      {STAGES.map((s, i) => (
        <div key={s}
          onClick={onStageClick ? () => onStageClick(i, s) : undefined}
          style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6, zIndex: 1, flex: 1, cursor: onStageClick ? 'pointer' : 'default' }}>
          <div style={{
            width: 20, height: 20, borderRadius: '50%',
            background: i <= current ? '#06b6d4' : 'white',
            border: `2px solid ${i <= current ? '#06b6d4' : '#d1d5db'}`,
            boxShadow: i === current ? '0 0 0 4px rgba(6,182,212,0.2)' : 'none',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            {i < current && <div style={{ width: 8, height: 8, borderRadius: '50%', background: 'white' }} />}
          </div>
          <span style={{ fontFamily: 'Pretendard,sans-serif', fontSize: 11, color: i === current ? '#06b6d4' : i < current ? '#1e293b' : '#94a3b8', whiteSpace: 'nowrap', fontWeight: i === current ? 600 : 400 }}>
            {s}
          </span>
        </div>
      ))}
    </div>
  );
}
