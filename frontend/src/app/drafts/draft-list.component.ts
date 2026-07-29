import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../environments/environment';

interface DraftSummary {
  draftId: string;
  templateId: string;
  title: string | null;
  status: string;
  outputType: string;
  totalSigners: number;
  signedSigners: number;
  createdAt: string;
  finalizedAt: string | null;
}

@Component({
  selector: 'app-draft-list',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <section class="draft-list">
      <header class="draft-list-header">
        <h1>Borradores de firma</h1>
        <a class="primary" routerLink="/drafts/new">+ Nuevo borrador</a>
      </header>

      <p *ngIf="errorMessage as err" class="error">{{ err }}</p>

      <table>
        <thead>
          <tr>
            <th>ID</th><th>Titulo</th><th>Plantilla</th><th>Estado</th>
            <th>Firmas</th><th>Salida</th><th>Creado</th><th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let d of drafts">
            <td><code>{{ d.draftId.substring(0, 8) }}</code></td>
            <td>{{ d.title || '-' }}</td>
            <td><code>{{ d.templateId.substring(0, 8) }}</code></td>
            <td><span class="badge" [attr.data-status]="d.status">{{ d.status }}</span></td>
            <td>{{ d.signedSigners }}/{{ d.totalSigners }}</td>
            <td>{{ d.outputType }}</td>
            <td>{{ formatDate(d.createdAt) }}</td>
            <td><a [routerLink]="['/drafts', d.draftId]">Abrir</a></td>
          </tr>
          <tr *ngIf="drafts.length === 0">
            <td colspan="8"><em>Sin borradores todavia.</em></td>
          </tr>
        </tbody>
      </table>
    </section>
  `,
  styles: [
    `
      .draft-list { max-width: 1100px; margin: 1rem auto; padding: 1rem; font-family: system-ui, sans-serif; }
      .draft-list-header { display: flex; justify-content: space-between; align-items: center; }
      a.primary { background: #1d6fe0; color: #fff; padding: 0.5rem 0.8rem; border-radius: 4px; text-decoration: none; }
      table { width: 100%; border-collapse: collapse; }
      th, td { border: 1px solid #ddd; padding: 0.4rem; }
      th { background: #f0f0f0; }
      .badge { display: inline-block; padding: 0.1rem 0.5rem; border-radius: 4px; font-size: 0.8rem; font-weight: 600; background: #ddd; }
      .badge[data-status='FIRMADO'], .badge[data-status='COMPLETO'], .badge[data-status='FINALIZADO'] { background: #c8f7c8; }
      .badge[data-status='PARCIAL'] { background: #fff3cd; }
      .badge[data-status='CANCELADO'] { background: #f5c2c2; }
      .error { color: #b00020; font-weight: 600; }
    `
  ]
})
export class DraftListComponent implements OnInit {
  drafts: DraftSummary[] = [];
  errorMessage: string | null = null;

  constructor(private readonly http: HttpClient) {}

  async ngOnInit(): Promise<void> {
    try {
      this.drafts = await firstValueFrom(
        this.http.get<DraftSummary[]>(`${environment.apiBaseUrl}/api/drafts`)
      );
    } catch (err) {
      this.errorMessage = this.extractMessage(err);
    }
  }

  formatDate(value: string): string {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
  }

  private extractMessage(err: unknown): string {
    if (typeof err === 'object' && err !== null) {
      const e = err as { error?: { message?: string }; message?: string };
      if (e.error?.message) return e.error.message;
      if (e.message) return e.message;
    }
    return 'Error inesperado';
  }
}
