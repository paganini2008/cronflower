import { Component, inject, signal } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../core/auth.service';
import { ConfigService } from '../../core/runtime-config';

@Component({
  selector: 'cf-login',
  imports: [
    ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
  ],
  template: `
    <div class="login-bg">
      <div class="login-card card">
        <img src="default_logo.png" class="login-logo" alt="cronflower" />
        <p class="login-sub">Sign in to the cronsmith control plane</p>

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
                  [disabled]="form.invalid">
            <mat-icon>login</mat-icon> Sign in
          </button>
        </form>

        <!-- Demo convenience: reflects config.json. Delete this line for a non-demo deployment. -->
        <p class="login-hint">Demo credentials — <strong>{{ demoUser }}</strong> / <strong>{{ demoPass }}</strong></p>
      </div>
    </div>
  `,
  styles: [`
    .login-bg { min-height: 100vh; display: flex; align-items: center; justify-content: center;
      background: radial-gradient(1200px 600px at 50% -10%, #e8f1fd 0%, #f4f7fb 55%, #eef3f9 100%); padding: 1.5rem; }
    .login-card { width: 100%; max-width: 400px; padding: 2.25rem 2rem 1.75rem; text-align: center; }
    .login-logo { height: 44px; width: auto; margin: 0 auto 0.75rem; display: block; }
    .login-sub { color: #5b6b7f; margin: 0 0 1.5rem; font-size: 0.9rem; }
    .w-full { width: 100%; }
    .login-btn { height: 44px; margin-top: 0.25rem; }
    .login-error { display: flex; align-items: center; gap: 0.4rem; color: #d93025; font-size: 0.85rem;
      background: #fce8e6; border-radius: 8px; padding: 0.5rem 0.75rem; margin-bottom: 0.75rem; }
    .login-error mat-icon { font-size: 1.1rem; width: 1.1rem; height: 1.1rem; }
    .login-hint { color: #94a3b8; font-size: 0.8rem; margin: 1.25rem 0 0; }
    mat-icon[matPrefix] { margin-right: 0.5rem; color: #94a3b8; }
  `],
})
export class Login {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly config = inject(ConfigService);

  protected readonly demoUser = this.config.auth.username;
  protected readonly demoPass = this.config.auth.password;
  protected readonly error = signal('');
  protected readonly form = this.fb.nonNullable.group({
    username: ['admin', Validators.required],
    password: ['', Validators.required],
  });

  submit(): void {
    const { username, password } = this.form.getRawValue();
    if (this.auth.login(username, password)) {
      const back = this.route.snapshot.queryParamMap.get('returnUrl') || '/dashboard';
      this.router.navigateByUrl(back);
    } else {
      this.error.set('Invalid username or password.');
    }
  }
}
