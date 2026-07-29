# Integracion API, Ejemplos cURL y Seguridad con API Key

## Endpoints Disponibles (sin romper los existentes)

### Plantillas
- `POST /api/templates` subir plantilla (`multipart/form-data`, `file`)
- `POST /api/templates/convert/download?target=docx|pdf` convertir y descargar
- `GET /api/templates` listar plantillas
- `GET /api/templates/catalog-fields` catalogo de campos
- `GET /api/templates/{templateId}/requirements` requisitos detectados
- `GET /api/templates/{templateId}/mapping` leer mapeo guardado
- `POST /api/templates/{templateId}/mapping` guardar mapeo
- `GET /api/templates/{templateId}/assist` sugerencias de mapeo (nuevo)

### Documentos
- `POST /api/documents/generate` generar documento (existente)
- `POST /api/documents/generate/preview` validar/diagnosticar solicitud (nuevo)
- `POST /api/documents/generate/batch` generar en lote (nuevo)
- `GET /api/documents/{documentId}` descargar documento generado

---

## Seguridad API Key (Backend)

La app soporta 2 modos:

- `plain`: API Key directa en `X-API-Key` (simple)
- `hmac`: firma HMAC por request con `timestamp + nonce` (mas robusto)

Si ademas activas `require-https=true`, obligas cifrado en transito (HTTPS).

### Variables de entorno principales

- `APP_SECURITY_API_KEY_ENABLED=true`
- `APP_SECURITY_API_KEY_MODE=plain|hmac`
- `APP_SECURITY_API_KEY_PUBLIC_PATHS=/,/hola,/health,/swagger-ui/**,/v3/api-docs/**`

### Modo plain

- `APP_SECURITY_API_KEY_HEADER_NAME=X-API-Key`
- `APP_SECURITY_API_KEY_KEYS=mi-clave-1,mi-clave-2`

### Modo hmac (recomendado para integraciones server-to-server)

- `APP_SECURITY_API_KEY_HMAC_CLIENTS=clienteA:secretoA,clienteB:secretoB`
- `APP_SECURITY_API_KEY_ID_HEADER_NAME=X-API-Key-Id`
- `APP_SECURITY_API_KEY_SIGNATURE_HEADER_NAME=X-API-Signature`
- `APP_SECURITY_API_KEY_TIMESTAMP_HEADER_NAME=X-API-Timestamp`
- `APP_SECURITY_API_KEY_NONCE_HEADER_NAME=X-API-Nonce`
- `APP_SECURITY_API_KEY_MAX_SKEW_SECONDS=300`
- `APP_SECURITY_API_KEY_REQUIRE_NONCE=true`
- `APP_SECURITY_API_KEY_REQUIRE_HTTPS=true`

---


---

## Contrato JSON Unificado (campos + firmas)

Esta via permite que el cliente envie el JSON completo o que el backend lo construya desde BD.
El motor acepta `fields` (texto) y `signatures` (imagenes en Base64).

### Estructura recomendada

```json
{
  "templateId": "uuid-plantilla",
  "fields": {
    "CURSO_NOMBRE": "Prevencion",
    "ALUMNO_1_NOMBRE": "Ana",
    "ALUMNO_1_APELLIDOS": "Lopez",
    "ALUMNO_1_NIF": "12345678A"
  },
  "signatures": {
    "FIRMA_1": "iVBORw0KGgoAAAANSUhEUgAA..."
  },
  "outputType": "pdf",
  "forceJson": false
}
```

Notas:
- `signatures` debe ser **solo Base64 puro** (sin prefijo `data:image/png;base64,`).
- Si el JSON viene del cliente, puede ser el mismo que se arma desde BD.

### Validaciones minimas sugeridas

- Limite de tamanio por firma: 2 MB (constante MAX_SIGNATURE_BYTES en DocumentGenerationService).
- Aceptados: PNG y JPEG. Backend valida magic bytes; otros formatos se rechazan con 400.
- Rechazar Base64 invalido (caracteres no validos o padding incorrecto).
- Maximo 100 firmas por peticion (MAX_SIGNATURES).
- El prefijo `data:image/png;base64,` se limpia automaticamente en backend.
## Ejemplos cURL

Define variables:

```bash
BASE_URL="https://tu-backend.com"
API_KEY="mi-clave-1"
TEMPLATE_ID="reemplazar-template-id"
```

### 1) Subir plantilla (plain)

```bash
curl -X POST "$BASE_URL/api/templates" \
  -H "X-API-Key: $API_KEY" \
  -F "file=@./plantilla.docx"
```

### 2) Consultar requisitos (plain)

```bash
curl "$BASE_URL/api/templates/$TEMPLATE_ID/requirements" \
  -H "X-API-Key: $API_KEY"
```

### 3) Asistente de mapeo (plain)

```bash
curl "$BASE_URL/api/templates/$TEMPLATE_ID/assist" \
  -H "X-API-Key: $API_KEY"
```

### 4) Preview de generacion (plain)

```bash
curl -X POST "$BASE_URL/api/documents/generate/preview" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY" \
  -d '{
    "templateId": "'"$TEMPLATE_ID"'",
    "fields": {
      "CURSO_NOMBRE": "Prevencion",
      "CURSO_FECHA": "2026-02-22"
    },
    "dataJson": "{\"alumnos\":[{\"nombre\":\"Ana\",\"apellidos\":\"Lopez\",\"nif\":\"12345678A\"}]}",
    "outputType": "pdf",
    "forceJson": false
  }'
```

### 5) Generacion en lote (plain)

```bash
curl -X POST "$BASE_URL/api/documents/generate/batch" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $API_KEY" \
  -d '{
    "requests": [
      {
        "templateId": "'"$TEMPLATE_ID"'",
        "fields": { "CURSO_NOMBRE": "Curso A" },
        "outputType": "pdf"
      },
      {
        "templateId": "'"$TEMPLATE_ID"'",
        "fields": { "CURSO_NOMBRE": "Curso B" },
        "outputType": "docx"
      }
    ]
  }'
```

### 6) Descargar documento generado (plain)

```bash
DOCUMENT_ID="reemplazar-document-id"
curl "$BASE_URL/api/documents/$DOCUMENT_ID" \
  -H "X-API-Key: $API_KEY" \
  --output documento_generado.pdf
```

---

## Ejemplo robusto modo HMAC

Canonical string firmada:

```text
METHOD\nPATH\nQUERY\nTIMESTAMP\nNONCE
```

Ejemplo para `GET /api/templates`:

```bash
BASE_URL="https://tu-backend.com"
KEY_ID="clienteA"
SECRET="secretoA"
METHOD="GET"
PATH="/api/templates"
QUERY=""
TIMESTAMP="$(date +%s)"
NONCE="$(openssl rand -hex 12)"
CANONICAL="$METHOD\n$PATH\n$QUERY\n$TIMESTAMP\n$NONCE"
SIGNATURE="$(printf "%b" "$CANONICAL" | openssl dgst -sha256 -hmac "$SECRET" -hex | awk '{print $2}')"

curl "$BASE_URL$PATH" \
  -H "X-API-Key-Id: $KEY_ID" \
  -H "X-API-Timestamp: $TIMESTAMP" \
  -H "X-API-Nonce: $NONCE" \
  -H "X-API-Signature: $SIGNATURE"
```

Notas:
- `TIMESTAMP` es epoch seconds.
- Si repites `nonce` dentro de la ventana, la peticion se rechaza (anti-replay).
- Si el reloj se desvía mas de `max-skew-seconds`, se rechaza.

---

## Frontend y API Key

En Angular:
- `frontend/src/environments/environment.ts`
- `frontend/src/environments/environment.prod.ts`

Configura `apiKey` para que el interceptor agregue automaticamente `X-API-Key`.

---

## Recomendacion de arquitectura

Para tu caso, la mejor solucion practica ahora mismo es:

1. Activar `mode=hmac` para integraciones externas server-to-server.
2. Activar `require-https=true`.
3. Mantener `plain` solo para entornos internos/dev si lo necesitas.
4. Como siguiente paso: mover politicas a un API Gateway (rate limit, cuotas, rotacion y auditoria por cliente).
