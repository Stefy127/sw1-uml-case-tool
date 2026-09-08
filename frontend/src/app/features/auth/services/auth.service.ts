import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, Observable, of, tap } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api.config';
import { AuthResponse, AuthUser, RegisterRequest, UpdateProfileRequest } from '../models/auth.model';
import { ThemeService } from '../../../core/theme/theme.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly storageKey = 'sw1.auth.token';
  readonly currentUser = signal<AuthUser | null>(null);
  readonly authLoading = signal(true);
  constructor(private readonly http: HttpClient, private readonly router: Router, private readonly theme: ThemeService) {
    const token = this.token;
    if (!token) { this.authLoading.set(false); return; }
    this.http.get<AuthUser>(`${API_BASE_URL}/auth/me`).pipe(catchError(() => { this.clear(); return of(null); })).subscribe(user => { this.currentUser.set(user); if (user) this.theme.applyServerTheme(user.theme); this.authLoading.set(false); });
  }
  get token(): string | null { return localStorage.getItem(this.storageKey); }
  get isAuthenticated(): boolean { return !!this.token && !!this.currentUser(); }
  login(email: string, password: string): Observable<AuthResponse> { return this.http.post<AuthResponse>(`${API_BASE_URL}/auth/login`, { email, password }).pipe(tap(response => this.store(response))); }
  register(request: RegisterRequest): Observable<AuthResponse> { return this.http.post<AuthResponse>(`${API_BASE_URL}/auth/register`, request).pipe(tap(response => this.store(response))); }
  updateProfile(request: UpdateProfileRequest): Observable<AuthUser> { return this.http.put<AuthUser>(`${API_BASE_URL}/auth/me`, request).pipe(tap(user => this.currentUser.set(user))); }
  changePassword(currentPassword: string, newPassword: string): Observable<void> { return this.http.put<void>(`${API_BASE_URL}/auth/me/password`, { currentPassword, newPassword }); }
  updateTheme(theme: string): Observable<AuthUser> { return this.http.put<AuthUser>(`${API_BASE_URL}/auth/me/preferences`, { theme }).pipe(tap(user => { this.currentUser.set(user); this.theme.applyServerTheme(user.theme); })); }
  logout(): void { this.clear(); void this.router.navigate(['/dashboard']); }
  private store(response: AuthResponse): void { localStorage.setItem(this.storageKey, response.token); this.currentUser.set(response.user); this.theme.applyServerTheme(response.user.theme); this.authLoading.set(false); }
  private clear(): void { localStorage.removeItem(this.storageKey); this.currentUser.set(null); this.authLoading.set(false); }
}
