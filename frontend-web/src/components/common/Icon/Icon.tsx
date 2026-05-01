import { forwardRef, type SVGProps, type ReactNode } from 'react';

/**
 * 모든 라인 아이콘의 베이스.
 * - stroke 기반 (fill 없음)
 * - 색상은 `currentColor` — 부모 텍스트 컬러를 상속한다.
 * - 기본 크기 20px, `size` prop으로 변경.
 */

export interface IconProps extends Omit<SVGProps<SVGSVGElement>, 'children'> {
  size?: number;
  children?: ReactNode;
}

const Icon = forwardRef<SVGSVGElement, IconProps>(function Icon(
  { size = 20, strokeWidth = 1.5, children, ...rest },
  ref,
) {
  return (
    <svg
      ref={ref}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      {...rest}
    >
      {children}
    </svg>
  );
});

export default Icon;
