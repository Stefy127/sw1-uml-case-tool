import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
export const authGuard: CanActivateFn = (route, state) => inject(AuthService).token ? true : inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
