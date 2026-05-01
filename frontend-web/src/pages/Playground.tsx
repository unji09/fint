/**
 * 공통 컴포넌트 검증용 Playground.
 * Storybook 미도입 — `/playground` 라우트에서 모든 variant 한 페이지에 노출한다.
 */
import { useState } from 'react';
import Button from '@/components/common/Button';
import Input from '@/components/common/Input';
import Textarea from '@/components/common/Textarea';
import Card from '@/components/common/Card';
import Modal from '@/components/common/Modal';
import {
  StageBadge,
  TemperatureBadge,
  SignalSourceBadge,
  PersonaBadge,
} from '@/components/common/Badge';
import TopAppBar from '@/components/common/TopAppBar';
import BottomNav from '@/components/common/BottomNav';
import FAB from '@/components/common/FAB';
import Stepper from '@/components/common/Stepper';
import {
  ArrowRightIcon,
  BellIcon,
  CalendarIcon,
  CloseIcon,
  HomeIcon,
  PinIcon,
  SearchIcon,
  SettingsIcon,
  UserIcon,
  UsersIcon,
} from '@/components/common/Icon';
import { DEAL_STAGES } from '@/constants/dealStage';

const sectionStyle: React.CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 16,
  padding: 24,
  background: 'var(--color-bg-card)',
  borderRadius: 12,
  border: '1px solid var(--color-border)',
};

const rowStyle: React.CSSProperties = {
  display: 'flex',
  gap: 12,
  alignItems: 'center',
  flexWrap: 'wrap',
};

const headingStyle: React.CSSProperties = {
  fontSize: 'var(--fs-lg)',
  fontWeight: 600,
  color: 'var(--color-text-primary)',
  margin: 0,
};

const iconButtonStyle: React.CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  width: 40,
  height: 40,
  borderRadius: 9999,
  color: 'var(--color-text-primary)',
  background: 'transparent',
};

export default function Playground() {
  const [open, setOpen] = useState(false);
  const [openSheet, setOpenSheet] = useState(false);
  const [openFull, setOpenFull] = useState(false);
  const [tab, setTab] = useState('home');

  return (
    <div
      style={{
        minHeight: '100vh',
        background: 'var(--color-bg-page)',
        paddingBottom: 80,
      }}
    >
      <TopAppBar
        title="공통 컴포넌트 Playground"
        onLeftClick={() => alert('back')}
        rightActions={
          <button type="button" aria-label="알림" style={iconButtonStyle}>
            <BellIcon size={20} />
          </button>
        }
      />

      <div style={{ maxWidth: 960, margin: '0 auto', padding: 24, display: 'grid', gap: 24 }}>
        <section style={sectionStyle}>
          <h2 style={headingStyle}>Button</h2>
          <div style={rowStyle}>
            <Button>Primary</Button>
            <Button variant="sub">Sub</Button>
            <Button variant="danger">Danger</Button>
            <Button variant="ghost">Ghost</Button>
          </div>
          <div style={rowStyle}>
            <Button size="sm">small</Button>
            <Button size="md">medium</Button>
            <Button size="lg">large</Button>
          </div>
          <div style={rowStyle}>
            <Button loading>Loading</Button>
            <Button disabled>Disabled</Button>
            <Button iconLeft={<PinIcon size={16} />}>아이콘 좌</Button>
            <Button iconRight={<ArrowRightIcon size={16} />}>아이콘 우</Button>
          </div>
          <Button fullWidth>FullWidth</Button>
        </section>

        <section style={sectionStyle}>
          <h2 style={headingStyle}>Icon (라인 아이콘)</h2>
          <div style={{ ...rowStyle, color: 'var(--color-text-primary)' }}>
            <HomeIcon />
            <UsersIcon />
            <UserIcon />
            <CalendarIcon />
            <BellIcon />
            <SearchIcon />
            <PinIcon />
            <SettingsIcon />
            <ArrowRightIcon />
            <CloseIcon />
          </div>
          <p style={{ color: 'var(--color-text-muted)', fontSize: 12, margin: 0 }}>
            모두 stroke 기반. <code>currentColor</code>로 부모 텍스트 색상을 상속한다.
          </p>
        </section>

        <section style={sectionStyle}>
          <h2 style={headingStyle}>Input / Textarea</h2>
          <Input label="이메일" placeholder="email@example.com" required />
          <Input label="비밀번호" type="password" helperText="8자 이상" />
          <Input label="에러 케이스" defaultValue="abc" error="형식이 올바르지 않습니다" />
          <Input
            label="검색"
            leftAdornment={<SearchIcon size={18} />}
            rightAdornment={<CloseIcon size={18} />}
            placeholder="검색어"
          />
          <Input label="비활성" disabled defaultValue="readonly" />
          <Textarea label="메모" rows={4} placeholder="메모를 입력하세요" maxLength={200} />
        </section>

        <section style={sectionStyle}>
          <h2 style={headingStyle}>Card</h2>
          <div
            style={{
              display: 'grid',
              gap: 12,
              gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))',
            }}
          >
            {/* 1. 대시보드 KPI 카드 — header + body */}
            <Card header={<strong>월간 예상 매출</strong>} padding="lg">
              <div style={{ fontSize: 24, fontWeight: 700 }}>₩ 1.2억</div>
              <div style={{ color: 'var(--color-text-muted)' }}>전월 대비 +12%</div>
            </Card>

            {/* 2. 리스트 아이템 카드 — clickable */}
            <Card clickable onClick={() => alert('Card clicked')}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <strong>삼성SDS</strong>
                <StageBadge stage="PROPOSAL_PRESENT" size="sm" />
              </div>
              <div style={{ color: 'var(--color-text-muted)', marginTop: 4 }}>
                IT 서비스 · 5명 담당
              </div>
            </Card>
          </div>
        </section>

        <section style={sectionStyle}>
          <h2 style={headingStyle}>Badge — Stage (7단계)</h2>
          <div style={rowStyle}>
            {DEAL_STAGES.map((s) => (
              <StageBadge key={s.code} stage={s.code} />
            ))}
          </div>
          <h2 style={headingStyle}>Badge — Temperature</h2>
          <div style={rowStyle}>
            <TemperatureBadge temperature="HOT" />
            <TemperatureBadge temperature="WARM" />
            <TemperatureBadge temperature="COOL" />
          </div>
          <h2 style={headingStyle}>Badge — Signal Source</h2>
          <div style={rowStyle}>
            <SignalSourceBadge source="DART" />
            <SignalSourceBadge source="NEWS" />
            <SignalSourceBadge source="LOG" />
            <SignalSourceBadge source="AI" />
          </div>
          <h2 style={headingStyle}>Badge — Persona</h2>
          <div style={rowStyle}>
            <PersonaBadge persona="BLOCKER" />
            <PersonaBadge persona="CHAMPION" />
            <PersonaBadge persona="SKEPTIC" />
            <PersonaBadge persona="COMPANION" />
          </div>
        </section>

        <section style={sectionStyle}>
          <h2 style={headingStyle}>Modal</h2>
          <div style={rowStyle}>
            <Button onClick={() => setOpen(true)}>Centered Modal</Button>
            <Button variant="sub" onClick={() => setOpenSheet(true)}>
              Bottom Sheet
            </Button>
            <Button variant="ghost" onClick={() => setOpenFull(true)}>
              Fullscreen
            </Button>
          </div>
          <Modal
            open={open}
            onClose={() => setOpen(false)}
            title="고객사 추가"
            footer={
              <>
                <Button variant="ghost" onClick={() => setOpen(false)}>
                  취소
                </Button>
                <Button onClick={() => setOpen(false)}>저장</Button>
              </>
            }
          >
            <Input label="고객사명" placeholder="삼성SDS" required />
            <div style={{ height: 12 }} />
            <Input label="업종" placeholder="IT 서비스" />
          </Modal>

          <Modal
            open={openSheet}
            onClose={() => setOpenSheet(false)}
            variant="bottomSheet"
            title="일정 추가"
            footer={
              <Button fullWidth onClick={() => setOpenSheet(false)}>
                저장
              </Button>
            }
          >
            <Textarea label="메모" rows={3} />
          </Modal>

          <Modal
            open={openFull}
            onClose={() => setOpenFull(false)}
            variant="fullscreen"
            title="풀스크린 모달"
          >
            <p>모바일에서 풀스크린 시트 패턴.</p>
          </Modal>
        </section>

        <section style={sectionStyle}>
          <h2 style={headingStyle}>Stepper (담당자 등록 4단계)</h2>
          <Stepper
            current={1}
            steps={[
              { label: 'FINT 데이터베이스 조회' },
              { label: '페이지 생성' },
              { label: '담당자 정보 업데이트' },
              { label: '완료' },
            ]}
          />
        </section>
      </div>

      <FAB ariaLabel="추가" onClick={() => alert('FAB clicked')} />
      <BottomNav
        items={[
          { key: 'home', label: '대시보드', icon: <HomeIcon size={22} /> },
          { key: 'customers', label: '고객', icon: <UsersIcon size={22} /> },
          { key: 'schedule', label: '일정', icon: <CalendarIcon size={22} /> },
          { key: 'noti', label: '알림', icon: <BellIcon size={22} /> },
          { key: 'me', label: '나', icon: <UserIcon size={22} /> },
        ]}
        activeKey={tab}
        onChange={setTab}
      />
    </div>
  );
}
