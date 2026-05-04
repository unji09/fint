import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Modal from './Modal';

describe('Modal', () => {
  it('open=false면 렌더링되지 않는다', () => {
    render(
      <Modal open={false} onClose={() => {}}>
        본문
      </Modal>,
    );
    expect(screen.queryByText('본문')).not.toBeInTheDocument();
  });

  it('open=true면 본문/title/footer를 렌더링한다', () => {
    render(
      <Modal open onClose={() => {}} title="제목" footer={<button>저장</button>}>
        본문
      </Modal>,
    );
    expect(screen.getByText('제목')).toBeInTheDocument();
    expect(screen.getByText('본문')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '저장' })).toBeInTheDocument();
  });

  it('role=dialog와 aria-modal=true를 둔다', () => {
    render(
      <Modal open onClose={() => {}} title="제목">
        본문
      </Modal>,
    );
    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveAttribute('aria-modal', 'true');
  });

  it('ESC 키 입력 시 onClose가 호출된다', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(
      <Modal open onClose={onClose}>
        본문
      </Modal>,
    );
    await user.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalled();
  });

  it('backdrop 클릭 시 closeOnBackdrop=true(기본)면 onClose가 호출된다', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(
      <Modal open onClose={onClose}>
        본문
      </Modal>,
    );
    const backdrop = screen.getByTestId('modal-backdrop');
    await user.click(backdrop);
    expect(onClose).toHaveBeenCalled();
  });

  it('closeOnBackdrop=false면 backdrop 클릭이 무시된다', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(
      <Modal open onClose={onClose} closeOnBackdrop={false}>
        본문
      </Modal>,
    );
    await user.click(screen.getByTestId('modal-backdrop'));
    expect(onClose).not.toHaveBeenCalled();
  });

  it('variant=fullscreen / bottomSheet 클래스를 적용한다', () => {
    const { rerender } = render(
      <Modal open onClose={() => {}} variant="fullscreen">
        본문
      </Modal>,
    );
    expect(screen.getByRole('dialog')).toHaveClass(/fullscreen/);

    rerender(
      <Modal open onClose={() => {}} variant="bottomSheet">
        본문
      </Modal>,
    );
    expect(screen.getByRole('dialog')).toHaveClass(/bottomSheet/);
  });

  it('close 버튼 클릭 시 onClose가 호출된다', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(
      <Modal open onClose={onClose} title="제목">
        본문
      </Modal>,
    );
    await user.click(screen.getByRole('button', { name: /닫기/ }));
    expect(onClose).toHaveBeenCalled();
  });
});
