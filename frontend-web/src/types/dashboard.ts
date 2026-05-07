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
  widgets?: DashboardWidget[];
  updatedAt?: string;
}

export interface DashboardTemplate {
  templateId: number;
  title: string;
  widgetType: string;
  thumbnailUrl: string | null;
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
