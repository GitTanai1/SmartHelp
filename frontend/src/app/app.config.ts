import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';

import { routes } from './app.routes';

/**
 * appConfig wires together Angular's core providers.
 *
 * provideHttpClient()   — enables Angular's HttpClient for ApiService calls.
 * provideRouter(routes) — enables client-side routing through app.routes.ts.
 * provideZoneChangeDetection — improves change-detection performance.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(),
  ],
};
