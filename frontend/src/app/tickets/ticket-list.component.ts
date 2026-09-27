import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../core/api.service';
import { Category, TicketPriority, TicketStatus, TicketSummary } from '../core/models';

/**
 * TicketListComponent shows all tickets with optional filtering by status,
 * priority, and category. Filters and pagination are applied server-side, so a
 * large ticket table is never loaded into the browser merely to filter it.
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
  readonly pageSize = 25;
  offset = 0;

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
    this.api.getTickets({
      status: this.filterStatus as TicketStatus || undefined,
      priority: this.filterPriority as TicketPriority || undefined,
      categoryId: this.filterCategoryId ? Number(this.filterCategoryId) : undefined,
      limit: this.pageSize,
      offset: this.offset,
    }).subscribe({
      next: (tickets) => {
        this.allTickets = tickets;
        this.filteredTickets = tickets;
        this.loading = false;
      },
      error: () => {
        this.error = 'Could not load tickets. Ensure the backend is running on port 8080.';
        this.loading = false;
      },
    });
  }

  onFiltersChanged(): void {
    this.offset = 0;
    this.loadTickets();
  }

  clearFilters(): void {
    this.filterStatus = '';
    this.filterPriority = '';
    this.filterCategoryId = '';
    this.offset = 0;
    this.loadTickets();
  }

  previousPage(): void {
    if (this.offset === 0) return;
    this.offset = Math.max(0, this.offset - this.pageSize);
    this.loadTickets();
  }

  nextPage(): void {
    if (!this.hasNextPage) return;
    this.offset += this.pageSize;
    this.loadTickets();
  }

  get hasNextPage(): boolean {
    return this.allTickets.length === this.pageSize;
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
        this.filteredTickets = this.allTickets;
      },
      error: () => alert('Failed to delete ticket.'),
    });
  }
}
