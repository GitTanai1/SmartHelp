import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

import { ApiService } from '../core/api.service';
import { DashboardStats, TicketSummary } from '../core/models';

/**
 * DashboardComponent is the landing page.
 *
 * It loads all tickets once, then computes summary counts client-side.
 * This keeps the backend simple — no dedicated /dashboard endpoint is needed.
 * For a production system you would add a server-side aggregate endpoint.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  stats: DashboardStats = { total: 0, open: 0, inProgress: 0, escalated: 0, resolved: 0, closed: 0 };
  recentTickets: TicketSummary[] = [];
  loading = true;
  error: string | null = null;

  constructor(private api: ApiService) {}

  ngOnInit(): void {
    this.api.getTickets().subscribe({
      next: (tickets) => {
        this.stats = this.computeStats(tickets);
        // Show the 5 most recent tickets on the dashboard
        this.recentTickets = tickets.slice(0, 5);
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Could not load dashboard data. Make sure the backend is running.';
        this.loading = false;
      },
    });
  }

  private computeStats(tickets: TicketSummary[]): DashboardStats {
    return {
      total: tickets.length,
      open: tickets.filter((t) => t.status === 'OPEN').length,
      inProgress: tickets.filter((t) => t.status === 'IN_PROGRESS').length,
      escalated: tickets.filter((t) => t.status === 'ESCALATED').length,
      resolved: tickets.filter((t) => t.status === 'RESOLVED').length,
      closed: tickets.filter((t) => t.status === 'CLOSED').length,
    };
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
}
