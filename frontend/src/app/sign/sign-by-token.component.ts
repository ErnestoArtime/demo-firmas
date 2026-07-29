import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../environments/environment';
import { SignaturePadComponent } from '../signature-pad/signature-pad.component';

interface SignByTokenInfo {
  draftId: string;
  title: string | null;
  draftStatus: 'PENDIENTE' | 'PARCIAL' | 'COMPLETO' | 'FINALIZADO' | 'CANCELADO';
  slotStatus: 'PENDIENTE' | 'FIRMADO' | 'EXPIRADO' | 'REVOCADO';
  rowIndex: number;
  signerName: string | null;
  signerNif: string | null;
  signatureKey: string;
  tokenExpiresAt: string;
  signedAt: string | null;
}

@Component({
  selector: 'app-sign-by-token',
  standalone: true,
  imports: [CommonModule, SignaturePadComponent],
  template: `
    <section class="sign-shell">
      <h1>Firma electronica</h1>

      <ng-container *ngIf="loadError as err">
        <p class="error">{{ err }}</p>
      </ng-container>

      <ng-container *ngIf="info as i">
        <div class="info-block">
          <p *ngIf="i.title"><strong>Documento:</strong> {{ i.title }}</p>
          <p><strong>Firmante:</strong> {{ i.signerName ?? '(sin nombre)' }}</p>
          <p *ngIf="i.signerNif"><strong>NIF:</strong> {{ i.signerNif }}</p>
          <p><strong>Fila:</strong> {{ i.rowIndex }} ({{ i.signatureKey }})</p>
          <p><strong>Estado:</strong> {{ i.slotStatus }}</p>
          <p><strong>Expira:</strong> {{ formatDate(i.tokenExpiresAt) }}</p>
        </div>

        <ng-container [ngSwitch]="i.slotStatus">
          <ng-container *ngSwitchCase="'FIRMADO'">
            <p class="ok">Esta fila ya fue firmada el {{ formatDate(i.signedAt) }}.</p>
          </ng-container>
          <ng-container *ngSwitchCase="'EXPIRADO'">
            <p class="error">El enlace expiro. Solicita uno nuevo al organizador.</p>
          </ng-container>
          <ng-container *ngSwitchCase="'REVOCADO'">
            <p class="error">El enlace fue revocado.</p>
          </ng-container>
          <ng-container *ngSwitchDefault>
            <p>Dibuja tu firma en el recuadro y confirma.</p>
            <app-signature-pad
              [width]="500"
              [height]="200"
              [disabled]="submitting"
              (signed)="submit($event)"></app-signature-pad>
            <p *ngIf="submitting">Enviando firma...</p>
            <p *ngIf="submitError as err" class="error">{{ err }}</p>
          </ng-container>
        </ng-container>
      </ng-container>
    </section>
  `,
  styles: [
    `
      .sign-shell { max-width: 640px; margin: 2rem auto; padding: 1.5rem; font-family: system-ui, sans-serif; }
      .info-block { background: #f5f5f5; padding: 1rem; border-radius: 6px; margin-bottom: 1rem; }
      .info-block p { margin: 0.25rem 0; }
      .error { color: #b00020; font-weight: 600; }
      .ok { color: #0a6; font-weight: 600; }
    `
  ]
})
export class SignByTokenComponent implements OnInit {
  info: SignByTokenInfo | null = null;
  loadError: string | null = null;
  submitError: string | null = null;
  submitting = false;
  private token = '';

  constructor(
    private readonly route: ActivatedRoute,
    private readonly http: HttpClient
  ) {}

  async ngOnInit(): Promise<void> {
    this.token = this.route.snapshot.paramMap.get('token') ?? '';
    if (!this.token) {
      this.loadError = 'Enlace invalido.';
      return;
    }
    await this.loadInfo();
  }

  async submit(base64: string): Promise<void> {
    if (!this.token || this.submitting) {
      return;
    }
    this.submitting = true;
    this.submitError = null;
    try {
      this.info = await firstValueFrom(
        this.http.post<SignByTokenInfo>(`${environment.apiBaseUrl}/api/drafts/sign/${this.token}`, {
          signatureBase64: base64
        })
      );
    } catch (err) {
      this.submitError = this.extractMessage(err);
    } finally {
      this.submitting = false;
    }
  }

  formatDate(value: string | null): string {
    if (!value) {
      return '';
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
  }

  private async loadInfo(): Promise<void> {
    try {
      this.info = await firstValueFrom(
        this.http.get<SignByTokenInfo>(`${environment.apiBaseUrl}/api/drafts/sign/${this.token}`)
      );
    } catch (err) {
      this.loadError = this.extractMessage(err);
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
    return 'No se pudo completar la operacion.';
  }
}
