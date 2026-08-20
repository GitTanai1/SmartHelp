import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { Router } from '@angular/router';

import { ApiService } from '../core/api.service';
import { Category, CreateTicketRequest, TicketPriority, User } from '../core/models';

/**
 * TicketFormComponent handles creation of new tickets.
 * It loads users and categories on init, then posts a CreateTicketRequest
 * to the backend. On success it navigates to the new ticket's detail page.
 */
@Component({
  selector: 'app-ticket-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './ticket-form.component.html',
  styleUrl: './ticket-form.component.scss',
})
export class TicketFormComponent implements OnInit {
  users: User[] = [];
  categories: Category[] = [];

  model: CreateTicketRequest = {
    userId: 0,
    categoryId: null,
    subject: '',
    description: '',
    priority: 'MEDIUM',
  };

  saving = false;
  loadingData = true;
  submitError: string | null = null;

  readonly priorityOptions: TicketPriority[] = ['LOW', 'MEDIUM', 'HIGH'];

  constructor(
    private api: ApiService,
    private router: Router,
  ) {}

  ngOnInit(): void {
    let usersLoaded = false;
    let catsLoaded = false;

    this.api.getUsers().subscribe({
      next: (users) => {
        this.users = users;
        usersLoaded = true;
        if (catsLoaded) this.loadingData = false;
        // Default to first customer
        const customer = users.find((u) => u.role === 'CUSTOMER');
        if (customer) this.model.userId = customer.id;
      },
      error: () => {
        usersLoaded = true;
        if (catsLoaded) this.loadingData = false;
      },
    });

    this.api.getCategories().subscribe({
      next: (cats) => {
        this.categories = cats;
        catsLoaded = true;
        if (usersLoaded) this.loadingData = false;
      },
      error: () => {
        catsLoaded = true;
        if (usersLoaded) this.loadingData = false;
      },
    });
  }

  submit(form: NgForm): void {
    if (form.invalid) return;
    this.saving = true;
    this.submitError = null;

    // Coerce empty string category selection to null
    const request: CreateTicketRequest = {
      ...this.model,
      categoryId: this.model.categoryId || null,
    };

    this.api.createTicket(request).subscribe({
      next: (ticket) => {
        this.router.navigate(['/tickets', ticket.id]);
      },
      error: (err) => {
        this.saving = false;
        this.submitError =
          err?.error?.message ?? 'Failed to create ticket. Check that the backend is running.';
      },
    });
  }

  cancel(): void {
    this.router.navigate(['/tickets']);
  }
}
