import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../environments/environment';

type DraftStatus = 'PENDIENTE' | 'PARCIAL' | 'COMPLETO' | 'FINALIZADO' | 'CANCELADO';
type SlotStatus = 'PENDIENTE' | 'FIRMADO' | 'EXPIRADO' | 'REVOCADO';

interface DraftSignerView {
  slotId: string;
  rowIndex: number;
  signerName: string | null;
  signerEmail: string | null;
  signerNif: string | null;
  signatureKey: string;
  status: SlotStatus;
  signedAt: string | null;
  tokenExpiresAt: string;
  shareUrl: string;
}

interface DraftStatusResponse {
  draftId: string;
  templateId: string;
  title: string | null;
  status: DraftStatus;
  outputType: string;
  totalSigners: number;
  signedSigners: number;
  createdAt: string;
  finalizedAt: string | null;
  generatedDocumentId: string | null;
  signers: DraftSignerView[];
}

interface FinalizeDraftResponse {
  draftId: string;
  status: DraftStatus;
  generatedDocumentId: string;
  downloadUrl: string;
  outputType: string;
  finalizedAt: string;
}

interface AuditLogEntry {
  id: number;
  draftId: string;
  slotId: string | null;
  action: string;
  timestamp: string;
  ip: string | null;
  userAgent: string | null;
  details: string | null;
}

@Component({
  selector: 'app-draft-detail',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <section class="draft-detail">
      <p><a routerLink="/drafts">&larr; Volver al listado</a></p>
      <h1>Borrador {{ draft?.draftId }}</h1>

      <ng-container *ngIf="draft as d">
        <div class="status-block">
          <p><strong>Estado:</strong> <span class="badge" [attr.data-status]="d.status">{{ d.status }}</span></p>
          <p *ngIf="d.title"><strong>Titulo:</strong> {{ d.title }}</p>
          <p><strong>Plantilla:</strong> {{ d.templateId }}</p>
          <p><strong>Tipo salida:</strong> {{ d.outputType }}</p>
          <p><strong>Firmas:</strong> {{ d.signedSigners }} / {{ d.totalSigners }}</p>
          <p><strong>Creado:</strong> {{ formatDate(d.createdAt) }}</p>
          <p *ngIf="d.finalizedAt"><strong>Finalizado:</strong> {{ formatDate(d.finalizedAt) }}</p>
          <p *ngIf="d.generatedDocumentId">
            <strong>Documento generado:</strong>
            <a [href]="documentDownloadUrl(d.generatedDocumentId)" target="_blank">Descargar</a>
          </p>
        </div>

        <h2>Firmantes</h2>
        <table>
          <thead>
            <tr>
              <th>#</th><th>Nombre</th><th>NIF</th><th>Clave</th><th>Estado</th><th>Firmado</th><th>Enlace</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let s of d.signers" [class.signed]="s.status === 'FIRMADO'">
              <td>{{ s.rowIndex }}</td>
              <td>{{ s.signerName || '(sin nombre)' }}</td>
              <td>{{ s.signerNif || '-' }}</td>
              <td>{{ s.signatureKey }}</td>
              <td><span class="badge" [attr.data-status]="s.status">{{ s.status }}</span></td>
              <td>{{ s.signedAt ? formatDate(s.signedAt) : '-' }}</td>
              <td>
                <div class="share-cell">
                  <input type="text" readonly [value]="s.shareUrl" #shareInput />
                  <button type="button" (click)="copy(s.shareUrl)">Copiar</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>

        <div class="actions-row">
          <button type="button"
            (click)="finalizeDraft(false)"
            [disabled]="busy || !canFinalize(d) || isClosed(d)">
            Finalizar y generar documento
          </button>
          <button type="button"
            (click)="finalizeDraft(true)"
            [disabled]="busy || isClosed(d)">
            Forzar finalizacion (con firmas pendientes)
          </button>
          <button type="button"
            class="danger"
            (click)="cancelDraft()"
            [disabled]="busy || isClosed(d)">
            Cancelar borrador
          </button>
          <button type="button" (click)="refresh()" [disabled]="busy">Refrescar</button>
        </div>

        <p *ngIf="errorMessage as err" class="error">{{ err }}</p>
        <p *ngIf="okMessage as ok" class="ok">{{ ok }}</p>

        <h2>Auditoria</h2>
        <table class="audit-table">
          <thead>
            <tr><th>Fecha</th><th>Accion</th><th>Slot</th><th>IP</th><th>Detalles</th></tr>
          </thead>
          <tbody>
            <tr *ngFor="let a of audit">
              <td>{{ formatDate(a.timestamp) }}</td>
              <td>{{ a.action }}</td>
              <td>{{ a.slotId || '-' }}</td>
              <td>{{ a.ip || '-' }}</td>
              <td>{{ a.details || '-' }}</td>
            </tr>
            <tr *ngIf="audit.length === 0">
              <td colspan="5"><em>Sin eventos.</em></td>
            </tr>
          </tbody>
        </table>
      </ng-container>

      <p *ngIf="loadError as err" class="error">{{ err }}</p>
    </section>
  `,
  styles: [
    `
      .draft-detail { max-width: 1100px; margin: 1rem auto; padding: 1rem; font-family: system-ui, sans-serif; }
      .status-block { background: #f5f5f5; padding: 1rem; border-radius: 6px; margin-bottom: 1rem; }
      .status-block p { margin: 0.25rem 0; }
      table { width: 100%; border-collapse: collapse; margin-bottom: 1rem; }
      th, td { border: 1px solid #ddd; padding: 0.4rem; vertical-align: middle; }
      th { background: #f0f0f0; }
      tr.signed { background: #eaffea; }
      .share-cell { display: flex; gap: 0.25rem; }
      .share-cell input { flex: 1; padding: 0.2rem; font-family: monospace; font-size: 0.8rem; }
      .actions-row { display: flex; flex-wrap: wrap; gap: 0.5rem; margin: 1rem 0; }
      button { padding: 0.4rem 0.8rem; cursor: pointer; }
      button.danger { background: #b00020; color: #fff; }
      button:disabled { opacity: 0.5; cursor: not-allowed; }
      .error { color: #b00020; font-weight: 600; }
      .ok { color: #0a6; font-weight: 600; }
      .badge { display: inline-block; padding: 0.1rem 0.5rem; border-radius: 4px; font-size: 0.8rem; font-weight: 600; background: #ddd; }
      .badge[data-status='FIRMADO'], .badge[data-status='COMPLETO'], .badge[data-status='FINALIZADO'] { background: #c8f7c8; }
      .badge[data-status='PARCIAL'] { background: #fff3cd; }
      .badge[data-status='EXPIRADO'], .badge[data-status='REVOCADO'], .badge[data-status='CANCELADO'] { background: #f5c2c2; }
      .audit-table { font-size: 0.85rem; }
    `
  ]
})
export class DraftDetailComponent implements OnInit, OnDestroy {
  draft: DraftStatusResponse | null = null;
  audit: AuditLogEntry[] = [];
  busy = false;
  errorMessage: string | null = null;
  okMessage: string | null = null;
  loadError: string | null = null;
  private draftId = '';
  private pollHandle: number | null = null;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly http: HttpClient
  ) {}

  async ngOnInit(): Promise<void> {
    this.draftId = this.route.snapshot.paramMap.get('id') ?? '';
    if (!this.draftId) {
      this.loadError = 'Borrador no especificado';
      return;
    }
    await this.refresh();
    this.pollHandle = window.setInterval(() => {
      if (!this.busy && this.draft && !this.isClosed(this.draft)) {
        void this.refresh(true);
      }
    }, 15000);
  }

  ngOnDestroy(): void {
    if (this.pollHandle !== null) {
      window.clearInterval(this.pollHandle);
    }
  }

  async refresh(silent = false): Promise<void> {
    if (!silent) {
      this.busy = true;
    }
    try {
      this.draft = await firstValueFrom(
        this.http.get<DraftStatusResponse>(`${environment.apiBaseUrl}/api/drafts/${this.draftId}`)
      );
      this.audit = await firstValueFrom(
        this.http.get<AuditLogEntry[]>(`${environment.apiBaseUrl}/api/drafts/${this.draftId}/audit`)
      );
    } catch (err) {
      this.loadError = this.extractMessage(err);
    } finally {
      this.busy = false;
    }
  }

  async finalizeDraft(allowIncomplete: boolean): Promise<void> {
    if (!this.draftId || this.busy) {
      return;
    }
    this.busy = true;
    this.errorMessage = null;
    this.okMessage = null;
    try {
      const result = await firstValueFrom(
        this.http.post<FinalizeDraftResponse>(
          `${environment.apiBaseUrl}/api/drafts/${this.draftId}/finalize?allowIncomplete=${allowIncomplete}`,
          {}
        )
      );
      this.okMessage = `Documento generado: ${result.generatedDocumentId}`;
      await this.refresh();
    } catch (err) {
      this.errorMessage = this.extractMessage(err);
    } finally {
      this.busy = false;
    }
  }

  async cancelDraft(): Promise<void> {
    if (!this.draftId || this.busy) {
      return;
    }
    if (!window.confirm('Cancelar el borrador? Esta accion revoca todos los enlaces pendientes.')) {
      return;
    }
    this.busy = true;
    this.errorMessage = null;
    try {
      await firstValueFrom(
        this.http.delete(`${environment.apiBaseUrl}/api/drafts/${this.draftId}`)
      );
      await this.refresh();
    } catch (err) {
      this.errorMessage = this.extractMessage(err);
    } finally {
      this.busy = false;
    }
  }

  async copy(value: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(value);
      this.okMessage = 'Enlace copiado';
      window.setTimeout(() => (this.okMessage = null), 2500);
    } catch {
      this.okMessage = null;
      this.errorMessage = 'No se pudo copiar';
    }
  }

  documentDownloadUrl(documentId: string): string {
    return `${environment.apiBaseUrl}/api/documents/${documentId}`;
  }

  canFinalize(d: DraftStatusResponse): boolean {
    return d.status === 'COMPLETO';
  }

  isClosed(d: DraftStatusResponse): boolean {
    return d.status === 'FINALIZADO' || d.status === 'CANCELADO';
  }

  formatDate(value: string | null): string {
    if (!value) {
      return '';
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
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
