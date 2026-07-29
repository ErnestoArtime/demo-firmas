import { HttpInterceptorFn } from '@angular/common/http';

import { environment } from '../environments/environment';

export const apiKeyInterceptor: HttpInterceptorFn = (req, next) => {
  const apiKey = environment.apiKey?.trim();
  if (!apiKey) {
    return next(req);
  }

  const baseUrl = environment.apiBaseUrl?.trim();
  if (!baseUrl || !req.url.startsWith(baseUrl)) {
    return next(req);
  }

  const updated = req.clone({
    setHeaders: {
      'X-API-Key': apiKey
    }
  });
  return next(updated);
};
