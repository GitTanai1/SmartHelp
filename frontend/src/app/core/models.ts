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

export interface UpdateUserRequest extends CreateUserRequest {}

export interface CategoryRequest {
  name: string;
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
  contractVersion: 'v1';
  ticketId: number;
  category: string | null;
  priority: string;
  confidence: number;
  generatedResponse: string;
  sensitive: boolean;
  finalStatus: string | null;
  evidence: Evidence[];
  path: string;
}

export interface Evidence {
  articleId: number;
  title: string;
  categoryId: number | null;
}

export interface AgentRun {
  id: string;
  ticketId: number;
  status: 'RUNNING' | 'WAITING_FOR_APPROVAL' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  requestedBy: string;
  requestId: string | null;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
  errorMessage: string | null;
}

export interface AgentRunEvent {
  id: number;
  runId: string;
  node: string;
  status: string;
  payload: string;
  occurredAt: string;
}

export interface AgentApproval {
  id: string;
  runId: string;
  ticketId: number;
  actionType: 'TICKET_RESOLUTION';
  proposedMessage: string;
  proposedPriority: TicketPriority | null;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  requestedAt: string;
  decidedAt: string | null;
  decidedBy: string | null;
  decisionNote: string | null;
}

// SSE workflow event emitted by Spring Boot /api/tickets/{id}/workflow
export interface WorkflowEvent {
  contractVersion: 'v1';
  ticketId: number;
  runId: string | null;
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
  | 'VERIFY_RESPONSE'
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
  evidence: Evidence[];
  generatedResponse: string;
  verificationFailed?: boolean;
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
  type: string;
  title: string;
  detail: string;
  instance: string;
  status: number;
  error: string;
  message: string;
  path: string;
  requestId: string | null;
  timestamp: string;
}
