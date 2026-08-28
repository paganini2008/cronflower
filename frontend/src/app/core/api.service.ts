import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, timeout } from 'rxjs';
import {
  ClusterView, Executor, HealthView, LogView, Stats, TaskListResponse, TaskMetadata, TaskView,
} from './models';
import { ConfigService } from './runtime-config';

export interface TaskQuery {
  group?: string;
  name?: string;
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

  runNow(group: string, name: string): Observable<Record<string, unknown>> {
    return this.http.post<Record<string, unknown>>(
      `${this.base}/tasks/${enc(group)}/${enc(name)}/run`, {});
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
}

function enc(s: string): string {
  return encodeURIComponent(s);
}
