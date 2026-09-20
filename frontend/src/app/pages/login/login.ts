import { Component, inject, signal } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'cf-login',
  imports: [
    ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
  ],
  template: `
    <div class="login-bg">
      <div class="login-card card">
        <img src="default_logo.png" class="login-logo" alt="cronflower" />
        <p class="login-sub">Sign in to the Cronflow control plane</p>

        <form [formGroup]="form" (ngSubmit)="submit()">
          <mat-form-field appearance="outline" class="w-full">
            <mat-label>Username</mat-label>
            <mat-icon matPrefix>person</mat-icon>
            <input matInput formControlName="username" autocomplete="username" />
          </mat-form-field>

          <mat-form-field appearance="outline" class="w-full">
            <mat-label>Password</mat-label>
            <mat-icon matPrefix>lock</mat-icon>
            <input matInput type="password" formControlName="password" autocomplete="current-password" />
          </mat-form-field>

          @if (error()) { <div class="login-error"><mat-icon>error</mat-icon> {{ error() }}</div> }

          <button mat-flat-button color="primary" type="submit" class="w-full login-btn"
                  [disabled]="form.invalid || loading()">
            <mat-icon>login</mat-icon> {{ loading() ? 'Signing in…' : 'Sign in' }}
          </button>
        </form>
      </div>
    </div>
  `,
  styles: [`
    .login-bg { min-height: 100vh; display: flex; align-items: center; justify-content: center;
      background: #eef2f7; padding: 1.5rem; }
    .login-card { width: 100%; max-width: 400px; padding: 2.25rem 2rem 1.75rem; text-align: center;
      border-top: 3px solid #1565c0; }
    .login-logo { height: 44px; width: auto; margin: 0 auto 0.75rem; display: block; }
    .login-sub { color: #3d5372; margin: 0 0 1.5rem; font-size: 0.9rem; }
    .w-full { width: 100%; }
    .login-btn { height: 44px; margin-top: 0.25rem; }
    .login-error { display: flex; align-items: center; gap: 0.4rem; color: #d93025; font-size: 0.85rem;
      background: #fce8e6; border-radius: 8px; padding: 0.5rem 0.75rem; margin-bottom: 0.75rem; }
    .login-error mat-icon { font-size: 1.1rem; width: 1.1rem; height: 1.1rem; }
    mat-icon[matPrefix] { margin-right: 0.5rem; color: #3d5372; }
  `],
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
