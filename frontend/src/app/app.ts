import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

import { environment } from '../environments/environment';

interface TemplateUploadResponse {
  templateId: string;
  originalFilename: string;
  type: string;
  createdAt: string;
}

interface TemplateRequirementsResponse {
  templateId: string;
  requiredFields: string[];
  requiredSignatures: string[];
}

interface GenerateDocumentResponse {
  documentId: string;
  templateId: string;
  type: string;
  downloadUrl: string;
  createdAt: string;
}

@Component({
  selector: 'app-root',
  imports: [CommonModule, FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  readonly apiBaseUrl = environment.apiBaseUrl;

  selectedFile: File | null = null;
  templateId = '';
  requirements: TemplateRequirementsResponse | null = null;
  generated: GenerateDocumentResponse | null = null;

  fieldsJson = JSON.stringify(
    {
      NOMBRE: 'Juan Perez',
      FECHA: '18/02/2026',
      CURSO: '2A'
    },
    null,
    2
  );

  signaturesJson = JSON.stringify(
    {
      FIRMA_1:
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9sAq6LwAAAAASUVORK5CYII='
    },
    null,
    2
  );

  loading = false;
  message = '';
  error = '';

  constructor(private readonly http: HttpClient) {}

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile = input.files?.[0] ?? null;
  }

  async uploadTemplate(): Promise<void> {
    this.resetFeedback();
    if (!this.selectedFile) {
      this.error = 'Selecciona un archivo DOCX o PDF.';
      return;
    }

    const formData = new FormData();
    formData.append('file', this.selectedFile);

    await this.runWithFeedback(async () => {
      const response = await firstValueFrom(
        this.http.post<TemplateUploadResponse>(`${this.apiBaseUrl}/api/templates`, formData)
      );
      this.templateId = response.templateId;
      this.message = `Plantilla subida: ${response.templateId}`;
      this.requirements = null;
      this.generated = null;
    });
  }

  async getRequirements(): Promise<void> {
    this.resetFeedback();
    if (!this.templateId.trim()) {
      this.error = 'Indica un templateId.';
      return;
    }

    await this.runWithFeedback(async () => {
      this.requirements = await firstValueFrom(
        this.http.get<TemplateRequirementsResponse>(
          `${this.apiBaseUrl}/api/templates/${this.templateId.trim()}/requirements`
        )
      );
      this.message = 'Requisitos cargados correctamente.';
    });
  }

  async generateDocument(): Promise<void> {
    this.resetFeedback();
    if (!this.templateId.trim()) {
      this.error = 'Indica un templateId.';
      return;
    }

    let fields: Record<string, string>;
    let signatures: Record<string, string>;

    try {
      fields = JSON.parse(this.fieldsJson);
      signatures = JSON.parse(this.signaturesJson);
    } catch {
      this.error = 'Fields o signatures no contienen JSON valido.';
      return;
    }

    await this.runWithFeedback(async () => {
      this.generated = await firstValueFrom(
        this.http.post<GenerateDocumentResponse>(`${this.apiBaseUrl}/api/documents/generate`, {
          templateId: this.templateId.trim(),
          fields,
          signatures
        })
      );
      this.message = `Documento generado: ${this.generated.documentId}`;
    });
  }

  private async runWithFeedback(action: () => Promise<void>): Promise<void> {
    this.loading = true;
    try {
      await action();
    } catch (err: unknown) {
      this.error = this.extractErrorMessage(err);
    } finally {
      this.loading = false;
    }
  }

  private resetFeedback(): void {
    this.message = '';
    this.error = '';
  }

  private extractErrorMessage(err: unknown): string {
    if (typeof err === 'object' && err !== null && 'error' in err) {
      const withError = err as { error?: unknown };
      if (typeof withError.error === 'string') {
        return withError.error;
      }
      if (
        typeof withError.error === 'object' &&
        withError.error !== null &&
        'message' in withError.error
      ) {
        const payload = withError.error as { message?: unknown };
        if (typeof payload.message === 'string') {
          return payload.message;
        }
      }
    }
    return 'No se pudo completar la operacion.';
  }
}
