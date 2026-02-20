# Frontend Angular (Demo Firmas)

Frontend sencillo para consumir el backend Spring Boot del mismo repositorio.

## Requisitos

- Node.js 20+
- Backend ejecutandose en `http://localhost:8080`

## Ejecutar en local

Desde esta carpeta (`frontend`):

```bash
npm install
npm run start:proxy
```

La app queda en `http://localhost:4200` y las llamadas a `/api/*` se redirigen al backend via `proxy.conf.json`.

## Funcionalidad incluida

- Subir plantilla (`POST /api/templates`)
- Convertir plantilla RTF/DOC y descargar resultado (`POST /api/templates/convert/download?target=docx|pdf`)
- Consultar requisitos (`GET /api/templates/{templateId}/requirements`)
- Generar documento (`POST /api/documents/generate`)
- Descargar documento generado

## Variables de entorno

- Desarrollo: `src/environments/environment.ts`
- Produccion: `src/environments/environment.prod.ts`

En produccion define la URL publica del backend en `apiBaseUrl`.

## Build

```bash
npm run build
```

Salida esperada: `dist/frontend/browser`

## Despliegue en Vercel

1. Importa el repositorio en Vercel.
2. Configura **Root Directory** = `frontend`.
3. Build command: `npm run build`.
4. Output directory: `dist/frontend/browser`.
5. Publica.

El archivo `vercel.json` ya incluye fallback a `index.html` para rutas SPA.
