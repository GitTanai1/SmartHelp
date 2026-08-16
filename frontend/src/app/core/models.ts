// Core data models mirroring the Spring Boot API shapes.
// These are plain TypeScript interfaces — no framework-specific decorators.

export interface User {
  id: number;
  name: string;
  email: string;
  role: 'CUSTOMER' | 'AGENT';
  createdAt: string;
}

export interface Category {
  id: number;
  name: string;
}

export interface Ticket {
  id: number;
  userId: number;
  categoryId: number | null;
  subject: string;
  description: string;
  status: TicketStatus;
  priority: TicketPriority;
  createdAt: string;
  updatedAt: string;
}

export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'ESCALATED' | 'CLOSED';
export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH';

export interface TicketSummary {
  id: number;
  userId: number;
  userName: string;
  categoryId: number | null;
  categoryName: string | null;
  subject: string;
  description: string;
  status: TicketStatus;
  priority: TicketPriority;
  createdAt: string;
  updatedAt: string;
}

export interface TicketResponse {
  id: number;
  ticketId: number;
  message: string;
  senderType: 'AI' | 'AGENT';
  createdAt: string;
}

export interface TicketDetail {
  ticket: TicketSummary;
  responses: TicketResponse[];
}

export interface KnowledgeArticle {
  id: number;
  categoryId: number;
  title: string;
  content: string;
  createdAt: string;
  updatedAt: string;
}

// Request shapes sent to the backend
export interface CreateTicketRequest {
  userId: number;
  categoryId?: number | null;
  subject: string;
  description: string;
  priority: TicketPriority;
}

export interface UpdateTicketRequest {
  categoryId?: number | null;
  subject: string;
  description: string;
  status: TicketStatus;
  priority: TicketPriority;
}

export interface CreateUserRequest {
  name: string;
  email: string;
  role: 'CUSTOMER' | 'AGENT';
}

export interface CreateKnowledgeRequest {
  categoryId: number;
  title: string;
  content: string;
}

export interface UpdateKnowledgeRequest {
  categoryId: number;
  title: string;
  content: string;
}

export interface CreateTicketResponseRequest {
  message: string;
  senderType: 'AI' | 'AGENT';
}

// AI workflow types
export interface AiAnalysisResult {
  ticketId: number;
  category: string | null;
  priority: string;
  confidence: number;
  generatedResponse: string;
  sensitive: boolean;
  finalStatus: string | null;
  path: string;
}

// SSE workflow event emitted by Spring Boot /api/tickets/{id}/workflow
export interface WorkflowEvent {
  ticketId: number;
  node: WorkflowNode;
  status: NodeStatus;
  state: WorkflowState;
  message: string;
}

export type WorkflowNode =
  | 'CLASSIFY_TICKET'
  | 'SEARCH_KNOWLEDGE'
  | 'CHECK_CONFIDENCE'
  | 'GENERATE_RESPONSE'
  | 'CHECK_SENSITIVITY'
  | 'ESCALATE'
  | 'RESOLVE';

export type NodeStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'SKIPPED' | 'FAILED';

export interface WorkflowState {
  category: string | null;
  priority: string | null;
  confidence: number;
  sensitive: boolean;
  finalStatus: string | null;
  knowledgeCount: number;
  generatedResponse: string;
  path: string[];
}

// Dashboard summary
export interface DashboardStats {
  total: number;
  open: number;
  inProgress: number;
  escalated: number;
  resolved: number;
  closed: number;
}

// Generic API error from the backend
export interface ApiError {
  status: number;
  error: string;
  message: string;
  path: string;
  timestamp: string;
}
