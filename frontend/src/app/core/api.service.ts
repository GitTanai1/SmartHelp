import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import {
  AiAnalysisResult,
  AgentApproval,
  AgentRun,
  AgentRunEvent,
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
import { environment } from '../../environments/environment';

// Base URL is read from the Angular environment file so that the production
// build (ng build --configuration production) automatically points at the
// deployed Render backend URL without any code changes.
const API_BASE = environment.apiBaseUrl;

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

  updateUser(id: number, request: CreateUserRequest): Observable<User> {
    return this.http.put<User>(`${API_BASE}/users/${id}`, request);
  }

  deleteUser(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/users/${id}`);
  }

  // ── Categories ─────────────────────────────────────────────────────────────

  getCategories(): Observable<Category[]> {
    return this.http.get<Category[]>(`${API_BASE}/categories`);
  }

  createCategory(request: { name: string }): Observable<Category> {
    return this.http.post<Category>(`${API_BASE}/categories`, request);
  }

  updateCategory(id: number, request: { name: string }): Observable<Category> {
    return this.http.put<Category>(`${API_BASE}/categories/${id}`, request);
  }

  deleteCategory(id: number): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/categories/${id}`);
  }

  // ── Tickets ────────────────────────────────────────────────────────────────

  getTickets(filters?: {
    status?: TicketStatus;
    categoryId?: number;
    userId?: number;
    priority?: TicketPriority;
    limit?: number;
    offset?: number;
  }): Observable<TicketSummary[]> {
    let params = new HttpParams();
    if (filters?.status) params = params.set('status', filters.status);
    if (filters?.categoryId) params = params.set('categoryId', String(filters.categoryId));
    if (filters?.userId) params = params.set('userId', String(filters.userId));
    if (filters?.priority) params = params.set('priority', filters.priority);
    if (filters?.limit !== undefined) params = params.set('limit', String(filters.limit));
    if (filters?.offset !== undefined) params = params.set('offset', String(filters.offset));
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
  openWorkflowStream(ticketId: number, runId?: string): EventSource {
    const query = runId ? `?runId=${encodeURIComponent(runId)}` : '';
    return new EventSource(`${API_BASE}/tickets/${ticketId}/workflow${query}`);
  }

  getAgentRuns(ticketId: number): Observable<AgentRun[]> {
    return this.http.get<AgentRun[]>(`${API_BASE}/tickets/${ticketId}/agent-runs`);
  }

  getAgentRun(ticketId: number, runId: string): Observable<AgentRun> {
    return this.http.get<AgentRun>(`${API_BASE}/tickets/${ticketId}/agent-runs/${runId}`);
  }

  getAgentRunEvents(ticketId: number, runId: string, afterEventId = 0): Observable<AgentRunEvent[]> {
    return this.http.get<AgentRunEvent[]>(
      `${API_BASE}/tickets/${ticketId}/agent-runs/${runId}/events`,
      { params: new HttpParams().set('afterEventId', String(afterEventId)) },
    );
  }

  cancelAgentRun(ticketId: number, runId: string): Observable<AgentRun> {
    return this.http.post<AgentRun>(
      `${API_BASE}/tickets/${ticketId}/agent-runs/${runId}/cancel`,
      {},
    );
  }

  getAgentApprovals(ticketId: number, runId: string): Observable<AgentApproval[]> {
    return this.http.get<AgentApproval[]>(
      `${API_BASE}/tickets/${ticketId}/agent-runs/${runId}/approvals`,
    );
  }

  decideAgentApproval(
    ticketId: number,
    runId: string,
    approvalId: string,
    decision: 'approve' | 'reject',
    note = '',
  ): Observable<AgentApproval> {
    return this.http.post<AgentApproval>(
      `${API_BASE}/tickets/${ticketId}/agent-runs/${runId}/approvals/${approvalId}/${decision}`,
      { note },
    );
  }
}
