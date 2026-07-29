import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable, firstValueFrom, timeout } from 'rxjs';

import { environment } from '../../environments/environment';

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

interface GenerateDocumentPreviewResponse {
  templateId: string;
  templateType: 'docx' | 'pdf';
  outputType: 'docx' | 'pdf';
  requiredFieldKeys: string[];
  requiredSignatureKeys: string[];
  providedFieldKeys: string[];
  providedSignatureKeys: string[];
  missingFieldKeys: string[];
  missingSignatureKeys: string[];
  warnings: string[];
  readyToGenerate: boolean;
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
  requiredFieldKeys: string[];
  requiredSignatureKeys: string[];
}

interface TemplateAssistResponse {
  templateId: string;
  templateType: 'docx' | 'pdf';
  requiredFields: string[];
  requiredSignatures: string[];
  catalogFields: CatalogFieldResponse[];
  suggestedFieldMappings: Record<string, string[]>;
  suggestedSignatureMappings: Record<string, string[]>;
}

interface BatchGenerateDocumentsResponse {
  total: number;
  successCount: number;
  errorCount: number;
  items: BatchGenerateDocumentsItem[];
}

interface BatchGenerateDocumentsItem {
  index: number;
  status: 'success' | 'error';
  templateId: string;
  documentId: string | null;
  downloadUrl: string | null;
  type: 'docx' | 'pdf' | null;
  createdAt: string | null;
  message: string | null;
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
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css'
})
export class HomeComponent implements OnInit {
  readonly apiBaseUrl = environment.apiBaseUrl;
  private readonly actionTimeoutMs = 20000;

  selectedFile: File | null = null;
  selectedConvertibleFile: File | null = null;
  templateId = '';
  selectedTemplateId = '';
  conversionTarget: 'docx' | 'pdf' = 'docx';
  outputType: 'docx' | 'pdf' = 'docx';
  selectedTemplateType: '' | 'docx' | 'pdf' = '';
  templates: TemplateUploadResponse[] = [];
  requirements: TemplateRequirementsResponse | null = null;
  templateAssist: TemplateAssistResponse | null = null;
  generated: GenerateDocumentResponse | null = null;
  generationPreview: GenerateDocumentPreviewResponse | null = null;
  batchGenerationResult: BatchGenerateDocumentsResponse | null = null;
  catalogFields: CatalogFieldResponse[] = [];
  fieldValues: Record<string, string> = {};
  signatureValues: Record<string, string> = {};
  selectedSignatureFiles: Record<string, string> = {};
  mappingFieldSelections: Record<string, string> = {};
  mappingSignatureSelections: Record<string, string> = {};
  requiredFieldFlags: Record<string, boolean> = {};
  requiredSignatureFlags: Record<string, boolean> = {};
  savedSignatures: SavedSignature[] = [];
  newSignatureName = '';
  newSignatureFile: File | null = null;
  forceJson = false;
  batchRequestsJson = JSON.stringify(
    [
      {
        templateId: 'TEMPLATE_ID_1',
        fields: { CURSO_NOMBRE: 'Curso A' },
        dataJson: '{\"alumnos\":[{\"nombre\":\"Ana\",\"apellidos\":\"Lopez\",\"nif\":\"12345678A\"}]}',
        outputType: 'pdf'
      },
      {
        templateId: 'TEMPLATE_ID_2',
        fields: { CURSO_NOMBRE: 'Curso B' },
        outputType: 'docx'
      }
    ],
    null,
    2
  );
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

  constructor(
    private readonly http: HttpClient,
    private readonly cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.loadSignatureLibrary();
    void this.bootstrap();
  }

  private async bootstrap(): Promise<void> {
    await this.loadCatalogFields(false);
    await this.listTemplates(false);
  }

  onUploadFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile = input.files?.[0] ?? null;
  }

  onConvertibleFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedConvertibleFile = input.files?.[0] ?? null;
  }

  async uploadTemplate(): Promise<void> {
    if (!this.selectedFile) {
      this.pushToast('Selecciona un archivo.', 'error');
      return;
    }
    if (!this.isDirectTemplateFile(this.selectedFile)) {
      this.pushToast('Solo puedes subir DOCX o PDF en el flujo principal.', 'error');
      return;
    }

    const formData = new FormData();
    formData.append('file', this.selectedFile);

    await this.runWithFeedback('uploadTemplate', async () => {
      const response = await this.httpOnce(
        this.http.post<TemplateUploadResponse>(`${this.apiBaseUrl}/api/templates`, formData)
      );
      this.templateId = response.templateId;
      this.selectedTemplateId = response.templateId;
      this.upsertTemplateInList(response);
      this.syncOutputTypeWithTemplate(response.templateId);
      this.generated = null;
      this.pushToast(`Plantilla subida: ${response.templateId}`, 'success');

      const [listResult, requirementsResult] = await Promise.allSettled([
        this.listTemplatesInternal(),
        this.getRequirementsInternal()
      ]);
      if (listResult.status === 'rejected') {
        this.pushToast('Se subio la plantilla, pero no se pudo refrescar el listado.', 'info');
      }
      if (requirementsResult.status === 'rejected') {
        this.pushToast('Se subio la plantilla, pero no se pudieron cargar requisitos.', 'info');
      }
    });
  }

  async convertTemplateAndDownload(): Promise<void> {
    if (!this.selectedConvertibleFile) {
      this.pushToast('Selecciona un archivo RTF o DOC para convertir.', 'error');
      return;
    }
    if (!this.isConvertibleTemplateFile(this.selectedConvertibleFile)) {
      this.pushToast('Solo puedes convertir archivos RTF o DOC.', 'error');
      return;
    }

    const formData = new FormData();
    formData.append('file', this.selectedConvertibleFile);

    await this.runWithFeedback('convertTemplate', async () => {
      const response = await this.httpOnce(
        this.http.post(`${this.apiBaseUrl}/api/templates/convert/download?target=${this.conversionTarget}`, formData, {
          observe: 'response',
          responseType: 'blob'
        })
      );
      this.downloadConvertedTemplate(response);
      this.pushToast('Archivo convertido y descargado. Puedes revisarlo y luego subirlo como plantilla.', 'success');
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
    this.syncOutputTypeWithTemplate(template.templateId);
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

  async previewGenerationRequest(): Promise<void> {
    if (!this.currentTemplateId()) {
      this.pushToast('Indica un templateId.', 'error');
      return;
    }

    await this.runWithFeedback('previewGenerationRequest', async () => {
      this.generationPreview = await this.httpOnce(
        this.http.post<GenerateDocumentPreviewResponse>(`${this.apiBaseUrl}/api/documents/generate/preview`, {
          templateId: this.currentTemplateId(),
          fields: this.buildFieldsPayload(),
          signatures: this.buildSignaturesPayload(),
          dataJson: this.dataJson,
          requiredFieldKeys: this.selectedRequiredFieldKeys(),
          requiredSignatureKeys: this.selectedRequiredSignatureKeys(),
          outputType: this.outputType,
          forceJson: this.forceJson
        })
      );
      this.pushToast(
        this.generationPreview.readyToGenerate
          ? 'Preview OK: la solicitud esta lista para generar.'
          : 'Preview con observaciones: revisa faltantes y warnings.',
        'info'
      );
    });
  }

  async generateDocument(): Promise<void> {
    if (!this.currentTemplateId()) {
      this.pushToast('Indica un templateId.', 'error');
      return;
    }
    if (!this.validateSignaturesVsStudentsCount(true)) {
      return;
    }

    await this.runWithFeedback('generateDocument', async () => {
      this.generated = await this.httpOnce(
        this.http.post<GenerateDocumentResponse>(`${this.apiBaseUrl}/api/documents/generate`, {
          templateId: this.currentTemplateId(),
          fields: this.buildFieldsPayload(),
          signatures: this.buildSignaturesPayload(),
          dataJson: this.dataJson,
          requiredFieldKeys: this.selectedRequiredFieldKeys(),
          requiredSignatureKeys: this.selectedRequiredSignatureKeys(),
          outputType: this.outputType,
          forceJson: this.forceJson
        })
      );
      this.pushToast(`Documento generado: ${this.generated.documentId}`, 'success');
    });
  }

  async generateBatchDocuments(): Promise<void> {
    let parsed: unknown;
    try {
      parsed = JSON.parse(this.batchRequestsJson);
    } catch {
      this.pushToast('El JSON de lote no es valido.', 'error');
      return;
    }
    if (!Array.isArray(parsed) || parsed.length === 0) {
      this.pushToast('El lote debe ser un arreglo con al menos una solicitud.', 'error');
      return;
    }

    await this.runWithFeedback('generateBatchDocuments', async () => {
      this.batchGenerationResult = await this.httpOnce(
        this.http.post<BatchGenerateDocumentsResponse>(`${this.apiBaseUrl}/api/documents/generate/batch`, {
          requests: parsed
        })
      );
      this.pushToast(
        `Lote ejecutado. Exitos: ${this.batchGenerationResult.successCount}, errores: ${this.batchGenerationResult.errorCount}.`,
        this.batchGenerationResult.errorCount > 0 ? 'info' : 'success'
      );
    });
  }

  async saveTemplateMapping(): Promise<void> {
    if (!this.currentTemplateId() || !this.requirements) {
      this.pushToast('Selecciona una plantilla y carga sus requisitos.', 'error');
      return;
    }
    await this.runWithFeedback('saveTemplateMapping', async () => {
      await this.httpOnce(
        this.http.post<TemplateMappingResponse>(
          `${this.apiBaseUrl}/api/templates/${this.currentTemplateId()}/mapping`,
          {
            fieldMappings: this.mappingFieldSelections,
            signatureMappings: this.mappingSignatureSelections,
            requiredFieldKeys: this.selectedRequiredFieldKeys(),
            requiredSignatureKeys: this.selectedRequiredSignatureKeys()
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

  onSavedSignatureSelectionChange(targetField: string, signatureId: string): void {
    if (!signatureId) {
      this.signatureValues[targetField] = '';
      this.selectedSignatureFiles[targetField] = '';
      return;
    }
    this.assignSavedSignature(targetField, signatureId);
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

  canConvertTemplate(): boolean {
    return Boolean(this.selectedConvertibleFile) && !this.isBusy();
  }

  canQueryRequirements(): boolean {
    return Boolean(this.currentTemplateId()) && !this.isBusy();
  }

  canGenerateDocument(): boolean {
    return (
      Boolean(this.requirements && this.currentTemplateId()) &&
      !this.isBusy() &&
      this.validateSignaturesVsStudentsCount()
    );
  }

  canPreviewGeneration(): boolean {
    return Boolean(this.requirements && this.currentTemplateId()) && !this.isBusy();
  }

  canGenerateBatchDocuments(): boolean {
    return Boolean(this.batchRequestsJson.trim()) && !this.isBusy();
  }

  canSaveMapping(): boolean {
    return Boolean(this.requirements && this.currentTemplateId()) && !this.isBusy();
  }

  canSaveSignatureToLibrary(): boolean {
    return Boolean(this.newSignatureName.trim() && this.newSignatureFile) && !this.isBusy();
  }

  canApplySuggestedMappings(): boolean {
    return Boolean(this.templateAssist && this.requirements) && !this.isBusy();
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
      this.requiredFieldFlags[key] = true;
    }

    this.signatureValues = {};
    this.selectedSignatureFiles = {};
    this.selectedLibrarySignatureByField = {};
    for (const key of this.requirements.requiredSignatures) {
      this.signatureValues[key] = '';
      this.selectedSignatureFiles[key] = '';
      this.selectedLibrarySignatureByField[key] = '';
      this.requiredSignatureFlags[key] = false;
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
      this.catalogFields = await this.httpOnce(
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
    const mapping = await this.httpOnce(
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

    const requiredFieldSet = new Set(mapping.requiredFieldKeys ?? []);
    const requiredSignatureSet = new Set(mapping.requiredSignatureKeys ?? []);
    for (const key of this.requirements.requiredFields) {
      this.requiredFieldFlags[key] = requiredFieldSet.has(key);
    }
    for (const key of this.requirements.requiredSignatures) {
      this.requiredSignatureFlags[key] = requiredSignatureSet.has(key);
    }
  }

  private async loadTemplateAssist(): Promise<void> {
    if (!this.currentTemplateId()) {
      this.templateAssist = null;
      return;
    }
    this.templateAssist = await this.httpOnce(
      this.http.get<TemplateAssistResponse>(
        `${this.apiBaseUrl}/api/templates/${this.currentTemplateId()}/assist`
      )
    );
  }

  applySuggestedMappings(): void {
    if (!this.templateAssist || !this.requirements) {
      return;
    }

    for (const field of this.requirements.requiredFields) {
      const suggestions = this.templateAssist.suggestedFieldMappings[field];
      if (suggestions?.length && !this.mappingFieldSelections[field]) {
        this.mappingFieldSelections[field] = suggestions[0];
      }
    }

    for (const signature of this.requirements.requiredSignatures) {
      const suggestions = this.templateAssist.suggestedSignatureMappings[signature];
      if (suggestions?.length && !this.mappingSignatureSelections[signature]) {
        this.mappingSignatureSelections[signature] = suggestions[0];
      }
    }

    this.pushToast('Se aplicaron sugerencias de mapeo en campos vacios.', 'info');
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
    this.templates = await this.httpOnce(
      this.http.get<TemplateUploadResponse[]>(`${this.apiBaseUrl}/api/templates`)
    );
  }

  private upsertTemplateInList(template: TemplateUploadResponse): void {
    const withoutCurrent = this.templates.filter((item) => item.templateId !== template.templateId);
    this.templates = [template, ...withoutCurrent];
    this.refreshUi();
  }

  private async getRequirementsInternal(): Promise<void> {
    this.requirements = await this.httpOnce(
      this.http.get<TemplateRequirementsResponse>(
        `${this.apiBaseUrl}/api/templates/${this.currentTemplateId()}/requirements`
      )
    );
    this.initializeFormValues();
    await this.loadTemplateMapping();
    await this.loadTemplateAssist();
    this.generationPreview = null;
  }

  onTemplateIdChanged(value: string): void {
    this.templateId = value;
    this.selectedTemplateId = value.trim();
    this.syncOutputTypeWithTemplate(this.selectedTemplateId);
    this.generationPreview = null;
    this.templateAssist = null;
  }

  private currentTemplateId(): string {
    return (this.selectedTemplateId || this.templateId).trim();
  }

  private selectedRequiredFieldKeys(): string[] {
    return Object.entries(this.requiredFieldFlags)
      .filter(([, required]) => required)
      .map(([key]) => key);
  }

  private selectedRequiredSignatureKeys(): string[] {
    return Object.entries(this.requiredSignatureFlags)
      .filter(([, required]) => required)
      .map(([key]) => key);
  }

  private validateSignaturesVsStudentsCount(showToast = false): boolean {
    const signaturesToApply = Object.keys(this.buildSignaturesPayload()).length;
    if (signaturesToApply === 0) {
      return true;
    }
    let parsed: unknown;
    try {
      parsed = JSON.parse(this.dataJson);
    } catch {
      return true;
    }
    if (!parsed || typeof parsed !== 'object') {
      return true;
    }
    const container = parsed as { alumnos?: unknown; students?: unknown };
    const studentsRaw = Array.isArray(container.alumnos)
      ? container.alumnos
      : Array.isArray(container.students)
      ? container.students
      : null;
    if (!studentsRaw) {
      return true;
    }
    if (studentsRaw.length < signaturesToApply) {
      if (showToast) {
        this.pushToast(
          `El JSON tiene ${studentsRaw.length} alumnos y estas intentando aplicar ${signaturesToApply} firmas.`,
          'error',
          7000
        );
      }
      return false;
    }
    return true;
  }

  private isDirectTemplateFile(file: File): boolean {
    const ext = this.fileExtension(file.name);
    return ext === 'docx' || ext === 'pdf';
  }

  private isConvertibleTemplateFile(file: File): boolean {
    const ext = this.fileExtension(file.name);
    return ext === 'rtf' || ext === 'doc';
  }

  private fileExtension(filename: string): string {
    const idx = filename.lastIndexOf('.');
    if (idx < 0 || idx === filename.length - 1) {
      return '';
    }
    return filename.substring(idx + 1).toLowerCase();
  }

  private syncOutputTypeWithTemplate(templateId: string): void {
    if (!templateId) {
      return;
    }
    const template = this.templates.find((item) => item.templateId === templateId);
    if (!template) {
      this.selectedTemplateType = '';
      return;
    }
    this.selectedTemplateType = template.type?.toLowerCase() === 'pdf' ? 'pdf' : 'docx';
    if (this.selectedTemplateType === 'pdf') {
      this.outputType = 'pdf';
    } else if (!this.outputType) {
      this.outputType = 'docx';
    }
  }

  private downloadConvertedTemplate(response: HttpResponse<Blob>): void {
    const blob = response.body;
    if (!blob) {
      throw new Error('No se recibio archivo convertido.');
    }
    const contentDisposition = response.headers.get('content-disposition');
    const filename = this.extractFilenameFromContentDisposition(contentDisposition) ?? `plantilla_convertida.${this.conversionTarget}`;
    this.downloadBlob(blob, filename);
  }

  private extractFilenameFromContentDisposition(contentDisposition: string | null): string | null {
    if (!contentDisposition) {
      return null;
    }
    const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
    if (utf8Match?.[1]) {
      return decodeURIComponent(utf8Match[1]);
    }
    const plainMatch = contentDisposition.match(/filename=\"?([^\";]+)\"?/i);
    return plainMatch?.[1] ?? null;
  }

  private downloadBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  private async runWithFeedback(actionName: string, action: () => Promise<void>): Promise<void> {
    if (this.busyActions.has(actionName)) {
      return;
    }
    this.setBusyAction(actionName, true);
    try {
      await action();
    } catch (err: unknown) {
      const message = await this.extractErrorMessage(err);
      this.pushToast(message, 'error', 7000);
    } finally {
      this.setBusyAction(actionName, false);
      this.refreshUi();
    }
  }

  private async httpOnce<T>(obs: Observable<T>): Promise<T> {
    return firstValueFrom(obs.pipe(timeout({ first: this.actionTimeoutMs })));
  }

  private setBusyAction(actionName: string, active: boolean): void {
    const next = new Set(this.busyActions);
    if (active) {
      next.add(actionName);
    } else {
      next.delete(actionName);
    }
    this.busyActions = next;
    this.refreshUi();
  }

  private refreshUi(): void {
    this.cdr.detectChanges();
  }

  formatDate(value: string): string {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return value;
    }
    return date.toLocaleString();
  }

  batchDownloadHref(item: BatchGenerateDocumentsItem): string | null {
    if (!item.downloadUrl) {
      return null;
    }
    return `${this.apiBaseUrl}${item.downloadUrl}`;
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
    this.refreshUi();
  }

  private pushToast(message: string, type: UiToast['type'], timeoutMs = 4500): void {
    const toast: UiToast = {
      id: crypto.randomUUID(),
      message,
      type
    };
    this.toasts = [...this.toasts, toast];
    window.setTimeout(() => this.removeToast(toast.id), timeoutMs);
    this.refreshUi();
  }
}
