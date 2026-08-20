import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../core/api.service';
import { Category, TicketPriority, TicketStatus, TicketSummary } from '../core/models';

/**
 * TicketListComponent shows all tickets with optional filtering by status,
 * priority, and category. Filters are applied client-side from the full list
 * for simplicity; the API also supports server-side filtering via query params.
 */
@Component({
  selector: 'app-ticket-list',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './ticket-list.component.html',
  styleUrl: './ticket-list.component.scss',
})
export class TicketListComponent implements OnInit {
  allTickets: TicketSummary[] = [];
  filteredTickets: TicketSummary[] = [];
  categories: Category[] = [];

  filterStatus: string = '';
  filterPriority: string = '';
  filterCategoryId: string = '';

  loading = true;
  error: string | null = null;

  readonly statusOptions: TicketStatus[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'ESCALATED', 'CLOSED'];
  readonly priorityOptions: TicketPriority[] = ['LOW', 'MEDIUM', 'HIGH'];

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.getCategories().subscribe({ next: (cats) => (this.categories = cats) });
    this.loadTickets();
  }

  loadTickets(): void {
    this.loading = true;
    this.error = null;
    this.api.getTickets().subscribe({
      next: (tickets) => {
        this.allTickets = tickets;
        this.applyFilters();
        this.loading = false;
      },
      error: () => {
        this.error = 'Could not load tickets. Ensure the backend is running on port 8080.';
        this.loading = false;
      },
    });
  }

  applyFilters(): void {
    this.filteredTickets = this.allTickets.filter((t) => {
      if (this.filterStatus && t.status !== this.filterStatus) return false;
      if (this.filterPriority && t.priority !== this.filterPriority) return false;
      if (this.filterCategoryId && String(t.categoryId) !== this.filterCategoryId) return false;
      return true;
    });
  }

  clearFilters(): void {
    this.filterStatus = '';
    this.filterPriority = '';
    this.filterCategoryId = '';
    this.applyFilters();
  }

  get hasActiveFilters(): boolean {
    return !!(this.filterStatus || this.filterPriority || this.filterCategoryId);
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
      HIGH: 'text-danger fw-semibold',
      MEDIUM: 'text-warning fw-semibold',
      LOW: 'text-success',
    };
    return map[priority] ?? '';
  }

  deleteTicket(id: number, event: Event): void {
    event.stopPropagation();
    if (!confirm('Delete this ticket? This cannot be undone.')) return;
    this.api.deleteTicket(id).subscribe({
      next: () => {
        this.allTickets = this.allTickets.filter((t) => t.id !== id);
        this.applyFilters();
      },
      error: () => alert('Failed to delete ticket.'),
    });
  }
}
