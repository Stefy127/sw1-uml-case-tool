export interface AuthUser { id: string; firstName: string; lastName: string; email: string; theme: string; }
export interface AuthResponse { token: string; user: AuthUser; }
export interface RegisterRequest { firstName: string; lastName: string; email: string; password: string; }
export interface UpdateProfileRequest { firstName: string; lastName: string; email: string; }
