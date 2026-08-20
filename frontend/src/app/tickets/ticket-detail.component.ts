import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { ApiService } from '../core/api.service';
import {
  Category,
  CreateTicketResponseRequest,
  TicketDetail,
  TicketPriority,
  TicketStatus,
  TicketSummary,
  UpdateTicketRequest,
} from '../core/models';

/**
 * TicketDetailComponent shows all information about one ticket.
 *
 * Features:
 * - displays ticket fields and all responses,
 * - inline editing of status, priority, and category,
 * - form to add a new agent response,
 * - button to trigger AI analysis (non-streaming, shows final result),
 * - link to the live SSE workflow visualization page.
 */
@Component({
  selector: 'app-ticket-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './ticket-detail.component.html',
  styleUrl: './ticket-detail.component.scss',
})
export class TicketDetailComponent implements OnInit {
  ticketId!: number;
  detail: TicketDetail | null = null;
  categories: Category[] = [];

  // Edit mode state
  editing = false;
  editModel: UpdateTicketRequest = {
    subject: '',
    description: '',
    status: 'OPEN',
    priority: 'MEDIUM',
    categoryId: null,
  };
  saving = false;
  saveError: string | null = null;

  // New response form
  responseMessage = '';
  addingResponse = false;
  responseError: string | null = null;

  // AI analysis
  analyzing = false;
  analysisResult: string | null = null;
  analysisError: string | null = null;

  loading = true;
  loadError: string | null = null;

  readonly statusOptions: TicketStatus[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'ESCALATED', 'CLOSED'];
  readonly priorityOptions: TicketPriority[] = ['LOW', 'MEDIUM', 'HIGH'];

  constructor(
    private api: ApiService,
    private route: ActivatedRoute,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.ticketId = Number(this.route.snapshot.paramMap.get('id'));
    this.api.getCategories().subscribe({ next: (cats) => (this.categories = cats) });
    this.loadTicket();
  }

  loadTicket(): void {
    this.loading = true;
    this.loadError = null;
    this.api.getTicket(this.ticketId).subscribe({
      next: (d) => {
        this.detail = d;
        this.loading = false;
      },
      error: () => {
        this.loadError = 'Ticket not found or backend unavailable.';
        this.loading = false;
      },
    });
  }

  startEdit(): void {
    if (!this.detail) return;
    const t = this.detail.ticket;
    this.editModel = {
      subject: t.subject,
      description: t.description,
      status: t.status,
      priority: t.priority,
      categoryId: t.categoryId,
    };
    this.editing = true;
    this.saveError = null;
  }

  cancelEdit(): void {
    this.editing = false;
    this.saveError = null;
  }

  saveEdit(form: NgForm): void {
    if (form.invalid) return;
    this.saving = true;
    this.saveError = null;
    this.api.updateTicket(this.ticketId, this.editModel).subscribe({
      next: () => {
        this.saving = false;
        this.editing = false;
        this.loadTicket();
      },
      error: (err) => {
        this.saving = false;
        this.saveError = err?.error?.message ?? 'Failed to save changes.';
      },
    });
  }

  submitResponse(form: NgForm): void {
    if (!this.responseMessage.trim()) return;
    this.addingResponse = true;
    this.responseError = null;
    const req: CreateTicketResponseRequest = {
      message: this.responseMessage,
      senderType: 'AGENT',
    };
    this.api.createTicketResponse(this.ticketId, req).subscribe({
      next: () => {
        this.responseMessage = '';
        form.resetForm();
        this.addingResponse = false;
        this.loadTicket(); // Refresh to show the new response
      },
      error: (err) => {
        this.addingResponse = false;
        this.responseError = err?.error?.message ?? 'Failed to add response.';
      },
    });
  }

  analyzeTicket(): void {
    this.analyzing = true;
    this.analysisResult = null;
    this.analysisError = null;
    this.api.analyzeTicket(this.ticketId).subscribe({
      next: (result) => {
        this.analyzing = false;
        this.analysisResult = `AI analysis complete. Final status: ${result.finalStatus}. Confidence: ${(result.confidence * 100).toFixed(0)}%. Path: ${result.path}`;
        this.loadTicket(); // Reload so any AI response/status changes appear
      },
      error: (err) => {
        this.analyzing = false;
        this.analysisError = err?.error?.message ?? 'AI analysis failed. Is the AI service running on port 8000?';
      },
    });
  }

  getStatusClass(status: string): string {
    const map: Record<string, string> = {
      OPEN: 'badge-open',
      IN_PROGRESS: 'badge-in-progress',
      ESCALATED: 'badge-escalated',
      RESOLVED: 'badge-resolved',
      CLOSED: 'badge-closed',
    };
    return map[status] ?? 'bg-secondary';
  }

  getPriorityClass(priority: string): string {
    const map: Record<string, string> = {
      HIGH: 'bg-danger text-white',
      MEDIUM: 'bg-warning text-dark',
      LOW: 'bg-success text-white',
    };
    return map[priority] ?? 'bg-secondary text-white';
  }

  get ticket(): TicketSummary | undefined {
    return this.detail?.ticket;
  }
}
