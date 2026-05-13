export interface WidgetPosition {
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface WidgetResult {
  data: unknown;
  insightText: string;
}

export interface DashboardWidget {
  widgetId: number;
  widgetType: string;
  title: string;
  config: Record<string, unknown>;
  position: WidgetPosition;
  queryId: number | null;
  inputText: string | null;
  result: WidgetResult | null;
}

export interface Dashboard {
  dashboardId: number;
  title: string;
  thumbnailKey: string | null;
  lastAccessedAt: string | null;
  widgets?: DashboardWidget[];
}

export interface DashboardTemplate {
  templateId: number;
  title: string;
  widgetType: string;
  thumbnailKey: string | null;
  config: Record<string, unknown>;
  position: WidgetPosition;
}

export interface CreateDashboardRequest {
  title?: string;
  templateId?: number;
  inputText?: string;
}

export interface CreateDashboardResponse {
  dashboardId: number;
  traceId: string | null;
}

// ── 대시보드 [id] 페이지 전용 타입 ──

export type Step = {
  label: string;
  done: boolean;
  active: boolean;
};

export type CanvasWidget = DashboardWidget & {
  px: number;
  py: number;
  pw: number;
  ph: number;
};
