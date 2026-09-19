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
  /** Periodic-task limits: repeatCount <= 0 = unlimited; stopAt absent = no deadline. */
  repeatCount?: number;
  stopAt?: string;
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
  /** HTTP (REST API) port the node serves on; advertised via node metadata. */
  httpPort?: number;
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
  /** For a periodic task: total fires before it finishes; <= 0 = unlimited. */
  repeatCount: number;
  /** ISO-8601 local date-time after which the task stops firing; blank = no deadline. */
  stopAt: string;
}

export const TASK_TYPES = ['BEAN', 'HTTP'] as const;
export const HTTP_METHODS = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'] as const;

export const TASK_STATUSES = [
  'STANDBY', 'SCHEDULED', 'RUNNING', 'PAUSED', 'FINISHED', 'CANCELED',
] as const;

export const MISFIRE_POLICIES = ['FIRE_ONCE_NOW', 'FIRE_ALL', 'SKIP'] as const;

// ---- cronflow (DAG) — mirrors the backend's server.pojo records --------------------------------

export interface DagChannelDef {
  name: string;
  reducer: string;
}

export interface DagNodeDef {
  name: string;
  entry: boolean;
  trigger: string;
  retries: number;
  beanName: string;
  methodName: string;
  subgraph: string;
}

export interface DagEdgeDef {
  from: string;
  to: string;
  condition?: string;
  branch?: string;
}

export interface DagConditionalDef {
  sources: string[];
  form: string;
  expression?: string;
  predicates?: string[];
  branches?: Record<string, string[]>;
  elseTargets?: string[];
}

/** The woven DAG description (server.pojo.DagDefinition) the diagram renders. */
export interface DagDefinition {
  graph: string;
  inputs: string[];
  channels: DagChannelDef[];
  nodes: DagNodeDef[];
  edges: DagEdgeDef[];
  conditionals: DagConditionalDef[];
}

/** A registered DAG as the list shows it (server.pojo.DagGraphView). */
export interface DagGraphView {
  application: string;
  graph: string;
  nodeCount: number;
  definition: DagDefinition;
}

/** One DAG run (server.pojo.DagRunView). Times are UTC ISO strings, like every other timestamp. */
export interface DagRunView {
  runId: string;
  parentRunId?: string;
  application?: string;
  graph: string;
  triggeredBy?: string;
  status: string;
  startedAt?: string;
  finishedAt?: string;
  elapsedMs?: number;
  nodeCount?: number;
  failedNode?: string;
  inputParameter?: string;
  returnValue?: string;
  errorDetail?: string;
}

/** One node execution within a run (server.pojo.DagNodeView). */
export interface DagNodeView {
  runId: string;
  graph: string;
  node: string;
  seq: number;
  status: string;
  inputParam?: string;
  output?: string;
  executor?: string;
  elapsedMs?: number;
  errorDetail?: string;
  loggedAt?: string;
}

export interface DagRunPage {
  total: number;
  items: DagRunView[];
}

/** A run drilled down: the run, its nodes and any child (subgraph) runs (server.pojo.DagRunDetail). */
export interface DagRunDetail {
  run: DagRunView;
  nodes: DagNodeView[];
  children: DagRunView[];
  /** Nodes currently in flight (only while the run is RUNNING) — pulsed on the graph. */
  running?: string[];
}

/** Run statuses used for the filter dropdown and status chips. */
export const DAG_RUN_STATUSES = ['RUNNING', 'SUCCESS', 'FAILED'] as const;
