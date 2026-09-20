import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, timeout } from 'rxjs';
import {
  ClusterView, DagDefinition, DagGraphPage, DagGraphView, DagRunDetail, DagRunPage, Executor,
  HealthView, LogView, NodeHealth, Stats, TaskListResponse, TaskMetadata, TaskView,
} from './models';
import { ConfigService } from './runtime-config';

export interface TaskQuery {
  group?: string;
  name?: string;
  status?: string;
  limit?: number;
  offset?: number;
}

/** Body for scheduling a DAG trigger (mirrors DagTriggerController.DagScheduleRequest). */
export interface DagScheduleRequest {
  taskGroup: string;
  taskName: string;
  cron: string;
  parser?: string;
  description?: string;
  seed?: Record<string, unknown>;
  timeout?: number;
  maxRetryCount?: number;
  retryInterval?: number;
  repeatCount?: number;
  misfirePolicy?: string;
  stopAt?: string;
}

export interface DagRunQuery {
  application?: string;
  graph?: string;
  status?: string;
  limit?: number;
  offset?: number;
}

@Injectable({ providedIn: 'root' })
export class CronsmithApi {
  private readonly http = inject(HttpClient);
  private readonly config = inject(ConfigService);

  /** '' (same-origin, via proxy) or the configured backend origin, e.g. http://localhost:19090. */
  private root(): string {
    return this.config.apiBaseUrl;
  }

  /** The REST base — `{apiBaseUrl}{apiPrefix}` (apiPrefix defaults to `/cronsmith`). */
  private get base(): string {
    return `${this.root()}${this.config.apiPrefix}`;
  }

  /** The cronflow (DAG) REST base — `{apiBaseUrl}{cronflowPrefix}` (defaults to `/cronflow`). */
  private get cronflowBase(): string {
    return `${this.root()}${this.config.cronflowPrefix}`;
  }

  stats(): Observable<Stats> {
    return this.http.get<Stats>(`${this.base}/stats`);
  }

  cluster(): Observable<ClusterView> {
    return this.http.get<ClusterView>(`${this.base}/cluster`);
  }

  executors(): Observable<Executor[]> {
    return this.http.get<Executor[]>(`${this.base}/executors`);
  }

  health(): Observable<HealthView> {
    return this.http.get<HealthView>(`${this.root()}/actuator/health`);
  }

  /** Per-node liveness + live JVM memory across the scheduler cluster. */
  nodesHealth(): Observable<NodeHealth[]> {
    return this.http.get<NodeHealth[]>(`${this.cronflowBase}/nodes/health`);
  }

  /** Deployment metadata for the header: environment label (dev / prod) and app name. */
  meta(): Observable<{ env: string; application?: string }> {
    return this.http.get<{ env: string; application?: string }>(`${this.cronflowBase}/meta`);
  }

  tasks(query: TaskQuery = {}): Observable<TaskListResponse> {
    let params = new HttpParams();
    for (const [k, v] of Object.entries(query)) {
      if (v !== undefined && v !== null && v !== '') {
        params = params.set(k, String(v));
      }
    }
    return this.http.get<TaskListResponse>(`${this.base}/tasks`, { params });
  }

  task(group: string, name: string): Observable<TaskView> {
    return this.http.get<TaskView>(`${this.base}/tasks/${enc(group)}/${enc(name)}`);
  }

  logs(group: string, name: string, limit = 20, offset = 0): Observable<LogView[]> {
    const params = new HttpParams().set('limit', String(limit)).set('offset', String(offset));
    return this.http.get<LogView[]>(`${this.base}/tasks/${enc(group)}/${enc(name)}/logs`, { params });
  }

  /** Run a task once now. An optional `parameter` overrides the stored initial parameter for this run. */
  runNow(group: string, name: string, parameter?: string): Observable<Record<string, unknown>> {
    const body = parameter != null ? { parameter } : {};
    return this.http.post<Record<string, unknown>>(
      `${this.base}/tasks/${enc(group)}/${enc(name)}/run`, body);
  }

  cronPreview(expr: string, count = 5): Observable<{ valid: boolean; next?: string[]; error?: string }> {
    const params = new HttpParams().set('expr', expr).set('count', String(count));
    return this.http.get<{ valid: boolean; next?: string[]; error?: string }>(
      `${this.base}/cron/preview`, { params }).pipe(timeout(8000));
  }

  save(metadata: TaskMetadata): Observable<TaskView> {
    return this.http.post<TaskView>(`${this.base}/tasks`, metadata);
  }

  remove(group: string, name: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/tasks/${enc(group)}/${enc(name)}`);
  }

  action(group: string, name: string, action: 'pause' | 'resume' | 'cancel'): Observable<TaskView> {
    return this.http.post<TaskView>(`${this.base}/tasks/${enc(group)}/${enc(name)}/${action}`, {});
  }

  // ---- cronflow (DAG) — under the separate cronflow prefix -------------------------------------

  /** A page of registered DAGs (server-side paged), optionally filtered by name. */
  dags(query: { q?: string; limit?: number; offset?: number } = {}): Observable<DagGraphPage> {
    let params = new HttpParams();
    if (query.q) {
      params = params.set('q', query.q);
    }
    if (query.limit != null) {
      params = params.set('limit', String(query.limit));
    }
    if (query.offset != null) {
      params = params.set('offset', String(query.offset));
    }
    return this.http.get<DagGraphPage>(`${this.cronflowBase}/dags`, { params });
  }

  /** One DAG's definition. */
  dag(application: string, graph: string): Observable<DagGraphView> {
    return this.http.get<DagGraphView>(`${this.cronflowBase}/dags/${enc(application)}/${enc(graph)}`);
  }

  /** A page of run history, newest first, optionally filtered. */
  dagRuns(query: DagRunQuery = {}): Observable<DagRunPage> {
    let params = new HttpParams();
    for (const [k, v] of Object.entries(query)) {
      if (v !== undefined && v !== null && v !== '') {
        params = params.set(k, String(v));
      }
    }
    return this.http.get<DagRunPage>(`${this.cronflowBase}/runs`, { params });
  }

  /** One run drilled down: the run, its node executions and any child (subgraph) runs. */
  dagRun(runId: string): Observable<DagRunDetail> {
    return this.http.get<DagRunDetail>(`${this.cronflowBase}/runs/${enc(runId)}`);
  }

  /** Applications the canvas can target (those with a live executor to host the node beans). */
  dagApplications(): Observable<string[]> {
    return this.http.get<string[]>(`${this.cronflowBase}/applications`);
  }

  /** Create a DAG drawn on the console canvas, hosted by a live executor of {@code application}. */
  createDag(application: string, definition: DagDefinition): Observable<{ graph: string }> {
    return this.http.post<{ graph: string }>(`${this.cronflowBase}/dags`, { application, definition });
  }

  /** Trigger a DAG by hand with an optional initial state; returns the new run id. */
  triggerDag(graph: string, initialState?: Record<string, unknown>): Observable<{ runId: string }> {
    return this.http.post<{ runId: string }>(
      `${this.cronflowBase}/dags/${enc(graph)}/trigger`, initialState ?? {});
  }

  /**
   * Register a scheduled trigger for a DAG: creates a cronsmith task that fires the graph on a cron.
   * The task then shows up in the task list and is paused/resumed/cancelled like any other.
   */
  scheduleDag(graph: string, req: DagScheduleRequest):
    Observable<{ graph: string; taskGroup: string; taskName: string }> {
    return this.http.post<{ graph: string; taskGroup: string; taskName: string }>(
      `${this.cronflowBase}/dags/${enc(graph)}/schedule`, req);
  }
}

function enc(s: string): string {
  return encodeURIComponent(s);
}
