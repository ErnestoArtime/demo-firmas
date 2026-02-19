import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
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

interface CatalogFieldResponse {
  code: string;
  description: string;
  kind: 'field' | 'signature';
}

interface TemplateMappingResponse {
  templateId: string;
  fieldMappings: Record<string, string>;
  signatureMappings: Record<string, string>;
}

interface SavedSignature {
  id: string;
  name: string;
  base64: string;
  createdAt: string;
}

interface UiToast {
  id: string;
  message: string;
  type: 'success' | 'error' | 'info';
}

@Component({
  selector: 'app-root',
  imports: [CommonModule, FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit {
  readonly apiBaseUrl = environment.apiBaseUrl;
  private readonly actionTimeoutMs = 20000;

  selectedFile: File | null = null;
  templateId = '';
  selectedTemplateId = '';
  templates: TemplateUploadResponse[] = [];
  requirements: TemplateRequirementsResponse | null = null;
  generated: GenerateDocumentResponse | null = null;
  catalogFields: CatalogFieldResponse[] = [];
  fieldValues: Record<string, string> = {};
  signatureValues: Record<string, string> = {};
  selectedSignatureFiles: Record<string, string> = {};
  mappingFieldSelections: Record<string, string> = {};
  mappingSignatureSelections: Record<string, string> = {};
  savedSignatures: SavedSignature[] = [];
  newSignatureName = '';
  newSignatureFile: File | null = null;
  selectedLibrarySignatureByField: Record<string, string> = {};
  dataJson = JSON.stringify(
    {
      curso: {
        nombre: 'Prevencion de Riesgos Laborales',
        fecha: '2026-02-19',
        tutor: 'Beatriz Espana'
      },
      alumnos: [
        {
          nombre: 'Ana',
          apellidos: 'Lopez Ruiz',
          nif: '12345678A'
        },
        {
          nombre: 'Luis',
          apellidos: 'Perez Gomez',
          nif: '87654321B'
        }
      ]
    },
    null,
    2
  );

  busyActions = new Set<string>();
  toasts: UiToast[] = [];
  private readonly signatureLibraryStorageKey = 'demo.signature.library.v1';

  constructor(private readonly http: HttpClient) {}

  ngOnInit(): void {
    this.loadSignatureLibrary();
    void this.bootstrap();
  }

  private async bootstrap(): Promise<void> {
    await this.loadCatalogFields(false);
    await this.listTemplates(false);
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile = input.files?.[0] ?? null;
  }

  async uploadTemplate(): Promise<void> {
    if (!this.selectedFile) {
      this.pushToast('Selecciona un archivo DOCX o PDF.', 'error');
      return;
    }

    const formData = new FormData();
    formData.append('file', this.selectedFile);

    await this.runWithFeedback('uploadTemplate', async () => {
      const response = await firstValueFrom(
        this.http.post<TemplateUploadResponse>(`${this.apiBaseUrl}/api/templates`, formData)
      );
      this.templateId = response.templateId;
      await this.listTemplatesInternal();
      await this.getRequirementsInternal();
      this.generated = null;
      this.pushToast(`Plantilla subida: ${response.templateId}`, 'success');
    });
  }

  async listTemplates(showToast = true): Promise<void> {
    await this.runWithFeedback('listTemplates', async () => {
      await this.listTemplatesInternal();
      if (showToast) {
        this.pushToast('Listado de plantillas actualizado.', 'info');
      }
    });
  }

  async selectTemplate(template: TemplateUploadResponse): Promise<void> {
    this.templateId = template.templateId;
    this.selectedTemplateId = template.templateId;
    await this.getRequirements(true);
  }

  async getRequirements(showToast = true): Promise<void> {
    if (!this.currentTemplateId()) {
      this.pushToast('Indica un templateId.', 'error');
      return;
    }

    await this.runWithFeedback('getRequirements', async () => {
      await this.getRequirementsInternal();
      if (showToast) {
        this.pushToast('Requisitos cargados correctamente.', 'success');
      }
    });
  }

  async generateDocument(): Promise<void> {
    if (!this.currentTemplateId()) {
      this.pushToast('Indica un templateId.', 'error');
      return;
    }

    await this.runWithFeedback('generateDocument', async () => {
      this.generated = await firstValueFrom(
        this.http.post<GenerateDocumentResponse>(`${this.apiBaseUrl}/api/documents/generate`, {
          templateId: this.currentTemplateId(),
          fields: this.buildFieldsPayload(),
          signatures: this.buildSignaturesPayload(),
          dataJson: this.dataJson
        })
      );
      this.pushToast(`Documento generado: ${this.generated.documentId}`, 'success');
    });
  }

  async saveTemplateMapping(): Promise<void> {
    if (!this.currentTemplateId() || !this.requirements) {
      this.pushToast('Selecciona una plantilla y carga sus requisitos.', 'error');
      return;
    }
    await this.runWithFeedback('saveTemplateMapping', async () => {
      await firstValueFrom(
        this.http.post<TemplateMappingResponse>(
          `${this.apiBaseUrl}/api/templates/${this.currentTemplateId()}/mapping`,
          {
            fieldMappings: this.mappingFieldSelections,
            signatureMappings: this.mappingSignatureSelections
          }
        )
      );
      this.pushToast('Mapeo guardado para la plantilla.', 'success');
    });
  }

  async onSignatureLibraryFileSelected(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    this.newSignatureFile = input.files?.[0] ?? null;
  }

  async saveSignatureToLibrary(): Promise<void> {
    if (!this.newSignatureName.trim() || !this.newSignatureFile) {
      this.pushToast('Indica nombre y archivo de firma.', 'error');
      return;
    }
    const base64 = await this.readFileAsBase64(this.newSignatureFile);
    this.savedSignatures = [
      ...this.savedSignatures,
      {
        id: crypto.randomUUID(),
        name: this.newSignatureName.trim(),
        base64,
        createdAt: new Date().toISOString()
      }
    ];
    this.persistSignatureLibrary();
    this.newSignatureName = '';
    this.newSignatureFile = null;
    this.pushToast('Firma guardada en la libreria local.', 'success');
  }

  assignSavedSignature(targetField: string, signatureId: string): void {
    const signature = this.savedSignatures.find((item) => item.id === signatureId);
    if (!signature) {
      return;
    }
    this.signatureValues[targetField] = signature.base64;
    this.selectedSignatureFiles[targetField] = `[Libreria] ${signature.name}`;
  }

  removeSavedSignature(signatureId: string): void {
    this.savedSignatures = this.savedSignatures.filter((item) => item.id !== signatureId);
    this.persistSignatureLibrary();
  }

  async onSignatureSelected(fieldName: string, event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    const base64 = await this.readFileAsBase64(file);
    this.signatureValues[fieldName] = base64;
    this.selectedSignatureFiles[fieldName] = file.name;
  }

  hasTemplates(): boolean {
    return this.templates.length > 0;
  }

  isBusy(action?: string): boolean {
    if (action) {
      return this.busyActions.has(action);
    }
    return this.busyActions.size > 0;
  }

  canUploadTemplate(): boolean {
    return Boolean(this.selectedFile) && !this.isBusy();
  }

  canQueryRequirements(): boolean {
    return Boolean(this.currentTemplateId()) && !this.isBusy();
  }

  canGenerateDocument(): boolean {
    return Boolean(this.requirements && this.currentTemplateId()) && !this.isBusy();
  }

  canSaveMapping(): boolean {
    return Boolean(this.requirements && this.currentTemplateId()) && !this.isBusy();
  }

  canSaveSignatureToLibrary(): boolean {
    return Boolean(this.newSignatureName.trim() && this.newSignatureFile) && !this.isBusy();
  }

  canAssignSavedSignature(fieldName: string): boolean {
    return Boolean(this.selectedLibrarySignatureByField[fieldName]) && !this.isBusy();
  }

  fieldCatalogOptions(): CatalogFieldResponse[] {
    return this.catalogFields.filter((item) => item.kind === 'field');
  }

  signatureCatalogOptions(): CatalogFieldResponse[] {
    return this.catalogFields.filter((item) => item.kind === 'signature');
  }

  private initializeFormValues(): void {
    if (!this.requirements) {
      return;
    }

    this.fieldValues = {};
    for (const key of this.requirements.requiredFields) {
      this.fieldValues[key] = '';
    }

    this.signatureValues = {};
    this.selectedSignatureFiles = {};
    this.selectedLibrarySignatureByField = {};
    for (const key of this.requirements.requiredSignatures) {
      this.signatureValues[key] = '';
      this.selectedSignatureFiles[key] = '';
      this.selectedLibrarySignatureByField[key] = '';
    }
    this.mappingFieldSelections = {};
    this.mappingSignatureSelections = {};
  }

  private buildFieldsPayload(): Record<string, string> {
    return Object.fromEntries(
      Object.entries(this.fieldValues)
        .map(([key, value]) => [key, value?.trim() ?? ''])
        .filter(([, value]) => value !== '')
    );
  }

  private buildSignaturesPayload(): Record<string, string> {
    return Object.fromEntries(
      Object.entries(this.signatureValues).filter(([, value]) => Boolean(value?.trim()))
    );
  }

  private async readFileAsBase64(file: File): Promise<string> {
    const dataUrl = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result ?? ''));
      reader.onerror = () => reject(new Error('No se pudo leer la imagen de firma.'));
      reader.readAsDataURL(file);
    });
    const marker = 'base64,';
    const markerIndex = dataUrl.indexOf(marker);
    return markerIndex >= 0 ? dataUrl.substring(markerIndex + marker.length) : dataUrl;
  }

  private async loadCatalogFields(showToast = false): Promise<void> {
    await this.runWithFeedback('loadCatalogFields', async () => {
      this.catalogFields = await firstValueFrom(
        this.http.get<CatalogFieldResponse[]>(`${this.apiBaseUrl}/api/templates/catalog-fields`)
      );
      if (showToast) {
        this.pushToast('Catalogo de campos actualizado.', 'info');
      }
    });
  }

  private async loadTemplateMapping(): Promise<void> {
    if (!this.currentTemplateId() || !this.requirements) {
      return;
    }
    const mapping = await firstValueFrom(
      this.http.get<TemplateMappingResponse>(
        `${this.apiBaseUrl}/api/templates/${this.currentTemplateId()}/mapping`
      )
    );

    this.mappingFieldSelections = {};
    for (const key of this.requirements.requiredFields) {
      this.mappingFieldSelections[key] = mapping.fieldMappings[key] ?? '';
    }
    this.mappingSignatureSelections = {};
    for (const key of this.requirements.requiredSignatures) {
      this.mappingSignatureSelections[key] = mapping.signatureMappings[key] ?? '';
    }
  }

  private loadSignatureLibrary(): void {
    const raw = localStorage.getItem(this.signatureLibraryStorageKey);
    if (!raw) {
      this.savedSignatures = [];
      return;
    }
    try {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) {
        this.savedSignatures = parsed.filter(
          (item): item is SavedSignature =>
            typeof item?.id === 'string' &&
            typeof item?.name === 'string' &&
            typeof item?.base64 === 'string' &&
            typeof item?.createdAt === 'string'
        );
      } else {
        this.savedSignatures = [];
      }
    } catch {
      this.savedSignatures = [];
    }
  }

  private persistSignatureLibrary(): void {
    localStorage.setItem(this.signatureLibraryStorageKey, JSON.stringify(this.savedSignatures));
  }

  private async listTemplatesInternal(): Promise<void> {
    this.templates = await firstValueFrom(
      this.http.get<TemplateUploadResponse[]>(`${this.apiBaseUrl}/api/templates`)
    );
  }

  private async getRequirementsInternal(): Promise<void> {
    this.requirements = await firstValueFrom(
      this.http.get<TemplateRequirementsResponse>(
        `${this.apiBaseUrl}/api/templates/${this.currentTemplateId()}/requirements`
      )
    );
    this.initializeFormValues();
    await this.loadTemplateMapping();
  }

  onTemplateIdChanged(value: string): void {
    this.templateId = value;
    this.selectedTemplateId = value.trim();
  }

  private currentTemplateId(): string {
    return (this.selectedTemplateId || this.templateId).trim();
  }

  private async runWithFeedback(actionName: string, action: () => Promise<void>): Promise<void> {
    if (this.busyActions.has(actionName)) {
      return;
    }
    this.busyActions.add(actionName);
    try {
      await this.withActionTimeout(action(), actionName);
    } catch (err: unknown) {
      const message = await this.extractErrorMessage(err);
      this.pushToast(message, 'error', 7000);
    } finally {
      this.busyActions.delete(actionName);
    }
  }

  private withActionTimeout<T>(promise: Promise<T>, actionName: string): Promise<T> {
    return Promise.race([
      promise,
      new Promise<T>((_, reject) => {
        window.setTimeout(() => {
          reject(new Error(`La operacion "${actionName}" excedio el tiempo de espera.`));
        }, this.actionTimeoutMs);
      })
    ]);
  }

  formatDate(value: string): string {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return value;
    }
    return date.toLocaleString();
  }

  private async extractErrorMessage(err: unknown): Promise<string> {
    if (err instanceof HttpErrorResponse) {
      const fromPayload = await this.extractMessageFromPayload(err.error);
      if (fromPayload) {
        return fromPayload;
      }
      if (typeof err.message === 'string' && err.message.trim()) {
        return err.message;
      }
      return `Error HTTP ${err.status}`;
    }

    if (typeof err === 'object' && err !== null && 'message' in err) {
      const withMessage = err as { message?: unknown };
      if (typeof withMessage.message === 'string' && withMessage.message.trim()) {
        return withMessage.message;
      }
    }

    return 'No se pudo completar la operacion.';
  }

  private async extractMessageFromPayload(payload: unknown): Promise<string | null> {
    if (!payload) {
      return null;
    }

    if (typeof payload === 'string') {
      try {
        const parsed = JSON.parse(payload) as { message?: unknown };
        if (typeof parsed?.message === 'string' && parsed.message.trim()) {
          return parsed.message;
        }
      } catch {
        if (payload.trim()) {
          return payload;
        }
      }
      return null;
    }

    if (payload instanceof Blob) {
      const text = await payload.text();
      return this.extractMessageFromPayload(text);
    }

    if (typeof payload === 'object') {
      const withMessage = payload as { message?: unknown; error?: unknown };
      if (typeof withMessage.message === 'string' && withMessage.message.trim()) {
        return withMessage.message;
      }
      if (typeof withMessage.error === 'string' && withMessage.error.trim()) {
        return withMessage.error;
      }
    }

    return null;
  }

  removeToast(toastId: string): void {
    this.toasts = this.toasts.filter((toast) => toast.id !== toastId);
  }

  private pushToast(message: string, type: UiToast['type'], timeoutMs = 4500): void {
    const toast: UiToast = {
      id: crypto.randomUUID(),
      message,
      type
    };
    this.toasts = [...this.toasts, toast];
    window.setTimeout(() => this.removeToast(toast.id), timeoutMs);
  }
}
