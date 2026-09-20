import { Component, inject } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ConfigService } from '../../core/runtime-config';

/**
 * API: the scheduler's REST API explorer, embedding the backend's Swagger UI in a frame so it lives
 * inside the console (under System) instead of being a separate URL. It loads through the same proxy
 * as every other call, so "Try it out" runs against the live cluster.
 */
@Component({
  selector: 'cf-api-docs',
  template: `
    <h1 class="page-title">API</h1>
    <p class="page-sub">
      The scheduler's REST API, explorable via Swagger UI. Requests run against the live cluster.
      <a [href]="href" target="_blank" rel="noopener" class="open-ext">Open in a new tab</a>
    </p>
    <div class="card frame-wrap">
      <iframe [src]="url" title="Swagger UI" class="swagger" loading="lazy"></iframe>
    </div>
  `,
  styles: [`
    .open-ext { margin-left: 0.5rem; color: var(--cf-blue, #1565c0); text-decoration: none; font-weight: 600; }
    .open-ext:hover { text-decoration: underline; }
    .frame-wrap { padding: 0; overflow: hidden; }
    .swagger { width: 100%; height: calc(100vh - 260px); min-height: 520px; border: 0; display: block; background: #fff; }
  `],
})
export class ApiDocs {
  private readonly sanitizer = inject(DomSanitizer);
  private readonly config = inject(ConfigService);

  protected readonly href = `${this.config.apiBaseUrl}/swagger-ui/index.html`;
  protected readonly url: SafeResourceUrl =
    this.sanitizer.bypassSecurityTrustResourceUrl(this.href);
}
