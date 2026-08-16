import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import {
  AiAnalysisResult,
  Category,
  CreateKnowledgeRequest,
  CreateTicketRequest,
  CreateTicketResponseRequest,
  CreateUserRequest,
  KnowledgeArticle,
  Ticket,
  TicketDetail,
  TicketPriority,
  TicketResponse,
  TicketStatus,
  TicketSummary,
  UpdateKnowledgeRequest,
  UpdateTicketRequest,
  User,
} from './models';

// Base URL for the Spring Boot backend. In production build this would be
// replaced by an environment file. For development the default points to
// the local Spring Boot server.
const API_BASE = 'http://localhost:8080/api';

/**
 * ApiService centralises every HTTP call Angular makes to the Spring Boot backend.
 * Components never use HttpClient directly; they call methods here instead.
 *
 * This keeps the HTTP layer in one place and makes it easy to swap the base
 * URL, add interceptors, or mock the service during tests.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private http: HttpClient) {}

  // ── Users ──────────────────────────────────────────────────────────────────

  getUsers(): Observable<User[]> {
    return this.http.get<User[]>(`${API_BASE}/users`);
  }

  getUser(id: number): Observable<User> {
    return this.http.get<User>(`${API_BASE}/users/${id}`);
  }

  createUser(request: CreateUserRequest): Observable<User> {
    return this.http.post<User>(`${API_BASE}/users`, request);
  }

  // ── Categories ─────────────────────────────────────────────────────────────

  getCategories(): Observable<Category[]> {
    return this.http.get<Category[]>(`${API_BASE}/categories`);
  }

  // ── Tickets ────────────────────────────────────────────────────────────────

  getTickets(filters?: {
    status?: TicketStatus;
    categoryId?: number;
    userId?: number;
    priority?: TicketPriority;
  }): Observable<TicketSummary[]> {
    let params = new HttpParams();
    if (filters?.status) params = params.set('status', filters.status);
    if (filters?.categoryId) params = params.set('categoryId', String(filters.categoryId));
    if (filters?.userId) params = params.set('userId', String(filters.userId));
    if (filters?.priority) params = params.set('priority', filters.priority);
    return this.http.get<TicketSummary[]>(`${API_BASE}/tickets`, { params });
  }

  getTicket(id: number): Observable<TicketDetail> {
    return this.http.get<TicketDetail>(`${API_BASE}/tickets/${id}`);
  }

  createTicket(request: CreateTicketRequest): Observable<Ticket> {
    return this.http.post<Ticket>(`${API_BASE}/tickets`, request);
  }

  updateTicket(id: number, request: UpdateTicketRequest): Observable<Ticket> {
    return this.http.put<Ticket>(`${API_BASE}/tickets/${id}`, request);
  }

  deleteTicket(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/tickets/${id}`);
  }

  // ── Ticket Responses ───────────────────────────────────────────────────────

  getTicketResponses(ticketId: number): Observable<TicketResponse[]> {
    return this.http.get<TicketResponse[]>(`${API_BASE}/tickets/${ticketId}/responses`);
  }

  createTicketResponse(
    ticketId: number,
    request: CreateTicketResponseRequest,
  ): Observable<TicketResponse> {
    return this.http.post<TicketResponse>(
      `${API_BASE}/tickets/${ticketId}/responses`,
      request,
    );
  }

  // ── Knowledge ──────────────────────────────────────────────────────────────

  getKnowledgeArticles(filters?: {
    categoryId?: number;
    query?: string;
  }): Observable<KnowledgeArticle[]> {
    let params = new HttpParams();
    if (filters?.categoryId) params = params.set('categoryId', String(filters.categoryId));
    if (filters?.query) params = params.set('query', filters.query);
    return this.http.get<KnowledgeArticle[]>(`${API_BASE}/knowledge`, { params });
  }

  getKnowledgeArticle(id: number): Observable<KnowledgeArticle> {
    return this.http.get<KnowledgeArticle>(`${API_BASE}/knowledge/${id}`);
  }

  createKnowledgeArticle(request: CreateKnowledgeRequest): Observable<KnowledgeArticle> {
    return this.http.post<KnowledgeArticle>(`${API_BASE}/knowledge`, request);
  }

  updateKnowledgeArticle(
    id: number,
    request: UpdateKnowledgeRequest,
  ): Observable<KnowledgeArticle> {
    return this.http.put<KnowledgeArticle>(`${API_BASE}/knowledge/${id}`, request);
  }

  deleteKnowledgeArticle(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/knowledge/${id}`);
  }

  // ── AI Analysis ────────────────────────────────────────────────────────────

  analyzeTicket(ticketId: number): Observable<AiAnalysisResult> {
    return this.http.post<AiAnalysisResult>(
      `${API_BASE}/tickets/${ticketId}/analyze`,
      {},
    );
  }

  /**
   * Opens a Server-Sent Events connection for the live workflow stream.
   * Returns an EventSource, NOT an Observable, because the native EventSource
   * API is the simplest way to consume SSE in Angular.
   *
   * The caller is responsible for closing the EventSource on destroy.
   */
  openWorkflowStream(ticketId: number): EventSource {
    return new EventSource(`${API_BASE}/tickets/${ticketId}/workflow`);
  }
}
