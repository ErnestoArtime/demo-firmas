import { CommonModule } from '@angular/common';
import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnDestroy,
  Output,
  ViewChild
} from '@angular/core';
import SignaturePad from 'signature_pad';

@Component({
  selector: 'app-signature-pad',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="sigpad-wrapper">
      <canvas
        #canvas
        class="sigpad-canvas"
        [attr.width]="width"
        [attr.height]="height"></canvas>
      <div class="sigpad-actions">
        <button type="button" (click)="clear()" [disabled]="disabled">Limpiar</button>
        <button type="button" (click)="emitSignature()" [disabled]="disabled || isEmpty()">
          Confirmar firma
        </button>
      </div>
    </div>
  `,
  styles: [
    `
      .sigpad-wrapper {
        display: inline-flex;
        flex-direction: column;
        gap: 0.5rem;
        align-items: stretch;
      }
      .sigpad-canvas {
        border: 1px solid #999;
        border-radius: 4px;
        background: #fff;
        touch-action: none;
      }
      .sigpad-actions {
        display: flex;
        gap: 0.5rem;
        justify-content: flex-end;
      }
      button {
        padding: 0.4rem 0.8rem;
        cursor: pointer;
      }
      button:disabled {
        cursor: not-allowed;
        opacity: 0.5;
      }
    `
  ]
})
export class SignaturePadComponent implements AfterViewInit, OnDestroy {
  @Input() width = 500;
  @Input() height = 200;
  @Input() penColor = '#111';
  @Input() disabled = false;

  @Output() signed = new EventEmitter<string>();
  @Output() cleared = new EventEmitter<void>();

  @ViewChild('canvas', { static: true }) canvasRef!: ElementRef<HTMLCanvasElement>;

  private pad: SignaturePad | null = null;
  private resizeObserver: ResizeObserver | null = null;

  ngAfterViewInit(): void {
    this.initPad();
  }

  ngOnDestroy(): void {
    this.pad?.off();
    this.resizeObserver?.disconnect();
  }

  private initPad(): void {
    const canvas = this.canvasRef.nativeElement;
    this.resizeCanvas(canvas);
    this.pad = new SignaturePad(canvas, {
      penColor: this.penColor,
      minWidth: 1,
      maxWidth: 2.4,
      backgroundColor: 'rgba(0,0,0,0)'
    });
  }

  private resizeCanvas(canvas: HTMLCanvasElement): void {
    const ratio = Math.max(window.devicePixelRatio || 1, 1);
    canvas.width = this.width * ratio;
    canvas.height = this.height * ratio;
    canvas.style.width = `${this.width}px`;
    canvas.style.height = `${this.height}px`;
    const ctx = canvas.getContext('2d');
    if (ctx) {
      ctx.scale(ratio, ratio);
    }
  }

  clear(): void {
    this.pad?.clear();
    this.cleared.emit();
  }

  isEmpty(): boolean {
    return this.pad?.isEmpty() ?? true;
  }

  emitSignature(): void {
    if (!this.pad || this.pad.isEmpty()) {
      return;
    }
    const dataUrl = this.pad.toDataURL('image/png');
    const marker = 'base64,';
    const idx = dataUrl.indexOf(marker);
    const base64 = idx >= 0 ? dataUrl.substring(idx + marker.length) : dataUrl;
    this.signed.emit(base64);
  }
}
