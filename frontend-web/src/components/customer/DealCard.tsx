import type { Deal } from '@/types/customer';

interface DealCardProps {
  deal: Deal;
  selected?: boolean;
  onClick?: () => void;
}

export default function DealCard({ deal, selected, onClick }: DealCardProps) {
  return (
    <div
      onClick={onClick}
      style={{
        background: 'white',
        borderRadius: 10,
        width: 200,
        minWidth: 200,
        height: 140,
        border: selected ? '2px solid #06b6d4' : '1px solid #e2e8f0',
        borderLeft: selected ? '3px solid #06b6d4' : '3px solid #7c3aed',
        boxShadow: selected ? '0 4px 16px rgba(6,182,212,0.2)' : '0 2px 4px rgba(0,0,0,0.05)',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
        padding: '12px 14px 12px 16px',
        flexShrink: 0,
        cursor: onClick ? 'pointer' : 'default',
        transition: 'border-color 0.15s',
        boxSizing: 'border-box',
      }}
    >
      <div
        style={{
          textAlign: 'right',
          fontFamily: 'Inter,sans-serif',
          fontSize: 10,
          color: '#475569',
        }}
      >
        {deal.expectedCloseDate ?? '예상 마감일'}
      </div>
      <div
        style={{
          textAlign: 'center',
          fontFamily: 'Pretendard,sans-serif',
          fontWeight: 500,
          fontSize: 18,
          color: '#7c3aed',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
          padding: '0 4px',
        }}
        title={deal.title}
      >
        {deal.title}
      </div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <span
          style={{
            background: '#faf5ff',
            borderRadius: 100,
            padding: '3px 7px',
            fontFamily: 'Pretendard,sans-serif',
            fontWeight: 600,
            fontSize: 11,
            color: '#7c3aed',
          }}
        >
          {deal.assignee}
        </span>
        <span
          style={{
            fontFamily: 'Inter,sans-serif',
            fontWeight: 700,
            fontSize: 10,
            color: '#1e293b',
          }}
        >
          {deal.expectedAmount > 0 ? deal.expectedAmount.toLocaleString() : '예상 금액'}
        </span>
      </div>
    </div>
  );
}
