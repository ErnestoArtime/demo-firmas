import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

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

interface SignerInput {
  signerName: string;
  signerEmail: string;
  signerNif: string;
  signatureKey: string;
}

interface DraftStatusResponse {
  draftId: string;
}

@Component({
  selector: 'app-draft-create',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <section class="draft-create">
      <h1>Crear borrador de firma</h1>

      <div class="form-group">
        <label>Plantilla</label>
        <select [(ngModel)]="templateId" (ngModelChange)="loadRequirements()">
          <option value="">-- selecciona --</option>
          <option *ngFor="let t of templates" [value]="t.templateId">
            {{ t.originalFilename }} ({{ t.type }})
          </option>
        </select>
      </div>

      <div class="form-group">
        <label>Titulo (opcional)</label>
        <input type="text" [(ngModel)]="title" placeholder="Acta sesion XYZ" />
      </div>

      <div class="form-group">
        <label>Tipo de salida</label>
        <select [(ngModel)]="outputType">
          <option value="docx">DOCX</option>
          <option value="pdf">PDF</option>
        </select>
      </div>

      <div class="form-group">
        <label>dataJson (opcional, lo que vaya a campos derivados)</label>
        <textarea rows="6" [(ngModel)]="dataJson"></textarea>
      </div>

      <div *ngIf="requirements" class="hint">
        <p>Firmas detectadas en la plantilla:
          <code *ngFor="let s of requirements.requiredSignatures">{{ s }} </code>
        </p>
      </div>

      <h2>Firmantes</h2>
      <table>
        <thead>
          <tr>
            <th>#</th><th>Nombre</th><th>NIF</th><th>Email</th><th>Clave firma</th><th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let s of signers; let i = index">
            <td>{{ i + 1 }}</td>
            <td><input type="text" [(ngModel)]="s.signerName" /></td>
            <td><input type="text" [(ngModel)]="s.signerNif" /></td>
            <td><input type="email" [(ngModel)]="s.signerEmail" /></td>
            <td>
              <input
                type="text"
                [(ngModel)]="s.signatureKey"
                [placeholder]="defaultKey(i)"
                list="signature-keys" />
            </td>
            <td><button type="button" (click)="removeSigner(i)">x</button></td>
          </tr>
        </tbody>
      </table>
      <datalist id="signature-keys">
        <option *ngFor="let s of requirements?.requiredSignatures ?? []" [value]="s"></option>
      </datalist>

      <div class="actions-row">
        <button type="button" (click)="addSigner()">+ Anadir firmante</button>
        <button type="button" (click)="autofillFromRequirements()" [disabled]="!requirements">
          Autocompletar desde plantilla
        </button>
      </div>

      <hr />

      <button type="button" (click)="create()" [disabled]="creating || !canCreate()">
        Crear borrador
      </button>
      <p *ngIf="errorMessage as err" class="error">{{ err }}</p>
    </section>
  `,
  styles: [
    `
      .draft-create { max-width: 900px; margin: 1rem auto; padding: 1rem; font-family: system-ui, sans-serif; }
      .form-group { display: flex; flex-direction: column; gap: 0.25rem; margin-bottom: 0.75rem; }
      .form-group input, .form-group select, .form-group textarea { padding: 0.4rem; }
      table { width: 100%; border-collapse: collapse; margin-bottom: 0.5rem; }
      th, td { border: 1px solid #ddd; padding: 0.3rem; }
      th { background: #f0f0f0; }
      .actions-row { display: flex; gap: 0.5rem; margin-bottom: 1rem; }
      .error { color: #b00020; font-weight: 600; }
      .hint { background: #f7f7f7; padding: 0.5rem; border-radius: 4px; margin: 0.5rem 0; }
      .hint code { background: #fff; padding: 0.1rem 0.3rem; margin-right: 0.3rem; }
      button { padding: 0.4rem 0.8rem; cursor: pointer; }
      button:disabled { opacity: 0.5; cursor: not-allowed; }
    `
  ]
})
export class DraftCreateComponent implements OnInit {
  templates: TemplateUploadResponse[] = [];
  templateId = '';
  title = '';
  outputType: 'docx' | 'pdf' = 'docx';
  dataJson = '';
  requirements: TemplateRequirementsResponse | null = null;
  signers: SignerInput[] = [
    { signerName: '', signerEmail: '', signerNif: '', signatureKey: '' }
  ];
  creating = false;
  errorMessage: string | null = null;

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router
  ) {}

  async ngOnInit(): Promise<void> {
    try {
      this.templates = await firstValueFrom(
        this.http.get<TemplateUploadResponse[]>(`${environment.apiBaseUrl}/api/templates`)
      );
    } catch (err) {
      this.errorMessage = this.extractMessage(err);
    }
  }

  async loadRequirements(): Promise<void> {
    this.requirements = null;
    if (!this.templateId) {
      return;
    }
    try {
      this.requirements = await firstValueFrom(
        this.http.get<TemplateRequirementsResponse>(
          `${environment.apiBaseUrl}/api/templates/${this.templateId}/requirements`
        )
      );
    } catch (err) {
      this.errorMessage = this.extractMessage(err);
    }
  }

  addSigner(): void {
    this.signers.push({ signerName: '', signerEmail: '', signerNif: '', signatureKey: '' });
  }

  removeSigner(index: number): void {
    if (this.signers.length === 1) {
      return;
    }
    this.signers.splice(index, 1);
  }

  autofillFromRequirements(): void {
    if (!this.requirements) {
      return;
    }
    const keys = this.requirements.requiredSignatures;
    if (keys.length === 0) {
      return;
    }
    this.signers = keys.map((key, idx) => ({
      signerName: this.signers[idx]?.signerName ?? '',
      signerEmail: this.signers[idx]?.signerEmail ?? '',
      signerNif: this.signers[idx]?.signerNif ?? '',
      signatureKey: key
    }));
  }

  defaultKey(index: number): string {
    return `FIRMA_${index + 1}`;
  }

  canCreate(): boolean {
    return !!this.templateId && this.signers.length > 0;
  }

  async create(): Promise<void> {
    if (!this.canCreate()) {
      return;
    }
    this.creating = true;
    this.errorMessage = null;
    try {
      const body = {
        templateId: this.templateId,
        title: this.title || null,
        outputType: this.outputType,
        dataJson: this.dataJson || null,
        signers: this.signers.map((s, i) => ({
          ...s,
          signatureKey: s.signatureKey || this.defaultKey(i)
        }))
      };
      const response = await firstValueFrom(
        this.http.post<DraftStatusResponse>(`${environment.apiBaseUrl}/api/drafts`, body)
      );
      await this.router.navigate(['/drafts', response.draftId]);
    } catch (err) {
      this.errorMessage = this.extractMessage(err);
    } finally {
      this.creating = false;
    }
  }

  private extractMessage(err: unknown): string {
    if (typeof err === 'object' && err !== null) {
      const e = err as { error?: { message?: string }; message?: string };
      if (e.error?.message) {
        return e.error.message;
      }
      if (e.message) {
        return e.message;
      }
    }
    return 'Error inesperado';
  }
}
