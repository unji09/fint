const STAGES = ['발굴', '가치 제안', '솔루션 설계', '제안 제출', '협상', '계약 대기', '수주'];

interface Props {
  current: number;
  onStageClick?: (stageIndex: number, stageName: string) => void;
}

export default function PipelineProgress({ current, onStageClick }: Props) {
  const n = STAGES.length;
  // 각 stage div는 flex:1이므로 너비 = 1/n. stage i 중심 = (i + 0.5) / n * 100% from left.
  // 회색 바: stage 0 중심 ~ stage n-1 중심
  const grayLeft  = `${(0.5 / n) * 100}%`;
  const grayRight = `${(0.5 / n) * 100}%`;
  // 파란 바: left:10(회색과 동일 기준) ~ stage current 중심
  // right = (1 - (current+0.5)/n) * 100
  const blueRight = `${((n - current - 0.5) / n) * 100}%`;
  return (
    <div style={{ position: 'relative', display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', padding: '10px 0 30px' }}>
      <div style={{ position: 'absolute', top: 18, left: grayLeft, right: grayRight, height: 4, background: '#f3f4f6', borderRadius: 9999 }} />
      <div style={{ position: 'absolute', top: 18, left: grayLeft, right: blueRight, height: 4, background: '#06b6d4', borderRadius: 9999 }} />
      {STAGES.map((s, i) => (
        <div key={s}
          onClick={onStageClick ? () => onStageClick(i, s) : undefined}
          style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6, zIndex: 1, flex: 1, minHeight: 44, cursor: onStageClick ? 'pointer' : 'default' }}>
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
