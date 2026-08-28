export interface TaskView {
  taskGroup: string;
  taskName: string;
  taskType?: string;
  className?: string;
  methodName?: string;
  beanName?: string;
  application?: string;
  url?: string;
  httpMethod?: string;
  httpHeaders?: string;
  cron?: string;
  parser?: string;
  description?: string;
  initialParameter?: string;
  status: string;
  nextFiredDateTime?: string;
  previousFiredDateTime?: string;
  lastModified?: string;
  runCount: number;
  failureCount: number;
  misfireCount: number;
  timeout: number;
  maxRetryCount: number;
  retryInterval: number;
  misfirePolicy?: string;
}

export interface TaskListResponse {
  total: number;
  items: TaskView[];
}

export interface LogView {
  taskGroup: string;
  taskName: string;
  scheduledDateTime?: string;
  firedDateTime?: string;
  completedDateTime?: string;
  parameter?: string;
  returnValue?: string;
  errorDetail?: string;
  elapsed: number;
  attempt: number;
  success: boolean;
  schedulerRepr?: string;
  executorRepr?: string;
}

export interface Stats {
  taskTotal: number;
  tasksByStatus: Record<string, number>;
  executorsTotal: number;
  executorsLive: number;
}

export interface Executor {
  application: string;
  instanceId: string;
  runUrl: string;
  healthCheckUrl: string;
  weight: number;
  lastSeen: string;
  healthy: boolean;
}

export interface ClusterNode {
  id: string;
  name: string;
  host: string;
  port: number;
  self: boolean;
  leader: boolean;
  role: string;
}

export interface ClusterView {
  application?: string;
  selfId?: string;
  leaderId?: string;
  sharding: boolean;
  store: string;
  storeShared: boolean;
  storeReplicated: boolean;
  storeMetadata: Record<string, string>;
  nodeCount: number;
  nodes: ClusterNode[];
}

export interface HealthComponent {
  status: string;
  details?: Record<string, unknown>;
  components?: Record<string, HealthComponent>;
}

export interface HealthView {
  status: string;
  components?: Record<string, HealthComponent>;
  groups?: string[];
}

/** POST body for creating/updating a task — matches the server's TaskSaveRequest. */
export interface TaskMetadata {
  taskGroup: string;
  taskName: string;
  taskType: 'BEAN' | 'HTTP';
  // Spring-bean task fields
  className: string;
  beanName: string;
  methodName: string;
  // HTTP-API task fields (url is submitted plain; the server stores it as a request line)
  url: string;
  httpMethod: string;
  // Common
  initialParameter: string;
  cron: string;
  parser: string;
  description: string;
  timeout: number;
  maxRetryCount: number;
  retryInterval: number;
  misfirePolicy: string;
}

export const TASK_TYPES = ['BEAN', 'HTTP'] as const;
export const HTTP_METHODS = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'] as const;

export const TASK_STATUSES = [
  'STANDBY', 'SCHEDULED', 'RUNNING', 'PAUSED', 'FINISHED', 'CANCELED',
] as const;

export const MISFIRE_POLICIES = ['FIRE_ONCE_NOW', 'FIRE_ALL', 'SKIP'] as const;
