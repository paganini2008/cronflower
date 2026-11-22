import { Component, inject, signal } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmInputImports } from '@spartan-ng/helm/input';
import { HlmLabelImports } from '@spartan-ng/helm/label';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'cf-login',
  imports: [
    ReactiveFormsModule, HlmCardImports, HlmInputImports, HlmLabelImports, HlmButtonImports,
  ],
  template: `
    <div class="min-h-screen flex items-center justify-center bg-muted/40 p-6">
      <section hlm-card class="w-full max-w-sm border-t-2 border-t-primary">
        <div hlm-card-header class="items-center text-center">
          <img src="default_logo.png" class="h-11 w-auto mx-auto mb-1" alt="cronflower" />
          <h3 hlmCardTitle>Sign in</h3>
          <p hlmCardDescription>Sign in to the Cronflow control plane</p>
        </div>
        <div hlm-card-content>
          <form [formGroup]="form" (ngSubmit)="submit()" class="flex flex-col gap-4">
            <div class="flex flex-col gap-1.5">
              <label hlmLabel for="username">Username</label>
              <input hlmInput id="username" class="w-full" formControlName="username"
                     autocomplete="username" placeholder="admin" />
            </div>
            <div class="flex flex-col gap-1.5">
              <label hlmLabel for="password">Password</label>
              <input hlmInput id="password" type="password" class="w-full" formControlName="password"
                     autocomplete="current-password" placeholder="••••••••" />
            </div>
            @if (error()) {
              <div class="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2
                          text-sm text-destructive">{{ error() }}</div>
            }
            <button hlmBtn type="submit" class="w-full" [disabled]="form.invalid || loading()">
              {{ loading() ? 'Signing in…' : 'Sign in' }}
            </button>
          </form>
        </div>
      </section>
    </div>
  `,
})
export class Login {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly error = signal('');
  protected readonly loading = signal(false);
  protected readonly form = this.fb.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  submit(): void {
    if (this.form.invalid || this.loading()) {
      return;
    }
    this.error.set('');
    this.loading.set(true);
    const { username, password } = this.form.getRawValue();
    this.auth.login(username, password).subscribe({
      next: () => {
        const back = this.route.snapshot.queryParamMap.get('returnUrl') || '/dashboard';
        this.router.navigateByUrl(back);
      },
      error: (e: unknown) => {
        const status = (e as { status?: number })?.status;
        this.error.set(status === 401 ? 'Invalid username or password.' : 'Sign in failed. Try again.');
        this.loading.set(false);
      },
    });
  }
}
