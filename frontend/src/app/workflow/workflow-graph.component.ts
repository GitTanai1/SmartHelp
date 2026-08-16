import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ApiService } from '../core/api.service';
import { NodeStatus, WorkflowEvent, WorkflowNode, WorkflowState } from '../core/models';

/** Describes one node as rendered in the SVG graph. */
interface GraphNode {
  id: WorkflowNode;
  label: string;
  description: string;
  x: number;
  y: number;
  status: NodeStatus;
  /** State snapshot from the last event for this node. */
  lastState: WorkflowState | null;
  message: string;
}

/** Describes one directed edge between two nodes. */
interface GraphEdge {
  from: WorkflowNode;
  to: WorkflowNode;
  label?: string;
  /** Whether this edge was actually taken during execution. */
  taken: boolean;
  /** Conditional edges that were NOT taken appear muted. */
  conditional: boolean;
}

/**
 * WorkflowGraphComponent opens a Server-Sent Events stream for the given
 * ticket ID and updates the SVG workflow graph in real time as each LangGraph
 * node starts and completes.
 *
 * The graph layout mirrors the LangGraph design:
 *
 *   CLASSIFY_TICKET
 *         ↓
 *   SEARCH_KNOWLEDGE
 *         ↓
 *   CHECK_CONFIDENCE
 *      /         \
 *   HIGH         LOW
 *    ↓             ↓
 * GENERATE      ESCALATE
 *    ↓
 * CHECK_SENSITIVITY
 *    /           \
 * SENSITIVE    NOT SENSITIVE
 *    ↓               ↓
 * ESCALATE       RESOLVE
 *
 * Conditional branches that were not taken are rendered muted.
 */
@Component({
  selector: 'app-workflow-graph',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './workflow-graph.component.html',
  styleUrl: './workflow-graph.component.scss',
})
export class WorkflowGraphComponent implements OnInit, OnDestroy {
  ticketId!: number;

  // Graph nodes laid out for the SVG viewport (width=560, flexible height)
  nodes: GraphNode[] = [
    {
      id: 'CLASSIFY_TICKET',
      label: 'Classify Ticket',
      description: 'Identifies the support category and priority from the ticket description.',
      x: 280, y: 60,
      status: 'PENDING',
      lastState: null,
      message: '',
    },
    {
      id: 'SEARCH_KNOWLEDGE',
      label: 'Search Knowledge',
      description: 'Searches the knowledge base for articles relevant to the ticket.',
      x: 280, y: 150,
      status: 'PENDING',
      lastState: null,
      message: '',
    },
    {
      id: 'CHECK_CONFIDENCE',
      label: 'Check Confidence',
      description: 'Evaluates whether the retrieved knowledge is sufficient for an automatic response.',
      x: 280, y: 240,
      status: 'PENDING',
      lastState: null,
      message: '',
    },
    {
      id: 'GENERATE_RESPONSE',
      label: 'Generate Response',
      description: 'Uses the LLM to draft a support response from the retrieved articles.',
      x: 150, y: 340,
      status: 'PENDING',
      lastState: null,
      message: '',
    },
    {
      id: 'CHECK_SENSITIVITY',
      label: 'Check Sensitivity',
      description: 'Detects whether the ticket involves security, billing disputes, or other sensitive topics.',
      x: 150, y: 430,
      status: 'PENDING',
      lastState: null,
      message: '',
    },
    {
      id: 'ESCALATE',
      label: 'Escalate',
      description: 'Sets the ticket status to ESCALATED and adds an AI note explaining why.',
      x: 410, y: 490,
      status: 'PENDING',
      lastState: null,
      message: '',
    },
    {
      id: 'RESOLVE',
      label: 'Resolve',
      description: 'Posts the AI response and sets the ticket status to RESOLVED.',
      x: 150, y: 520,
      status: 'PENDING',
      lastState: null,
      message: '',
    },
  ];

  // Graph edges
  edges: GraphEdge[] = [
    { from: 'CLASSIFY_TICKET',   to: 'SEARCH_KNOWLEDGE',  taken: false, conditional: false },
    { from: 'SEARCH_KNOWLEDGE',  to: 'CHECK_CONFIDENCE',  taken: false, conditional: false },
    // Conditional: confidence high → GENERATE_RESPONSE
    { from: 'CHECK_CONFIDENCE',  to: 'GENERATE_RESPONSE', label: 'High confidence', taken: false, conditional: true },
    // Conditional: confidence low → ESCALATE
    { from: 'CHECK_CONFIDENCE',  to: 'ESCALATE',          label: 'Low confidence',  taken: false, conditional: true },
    { from: 'GENERATE_RESPONSE', to: 'CHECK_SENSITIVITY', taken: false, conditional: false },
    // Conditional: sensitive → ESCALATE
    { from: 'CHECK_SENSITIVITY', to: 'ESCALATE',          label: 'Sensitive',       taken: false, conditional: true },
    // Conditional: not sensitive → RESOLVE
    { from: 'CHECK_SENSITIVITY', to: 'RESOLVE',           label: 'Not sensitive',   taken: false, conditional: true },
  ];

  selectedNode: GraphNode | null = null;
  streamStatus: 'idle' | 'connecting' | 'running' | 'complete' | 'error' = 'idle';
  streamError: string | null = null;
  finalStatus: string | null = null;
  executionPath: WorkflowNode[] = [];

  private eventSource: EventSource | null = null;

  // Radius and width constants for SVG layout
  readonly NODE_RADIUS = 36;
  readonly SVG_WIDTH = 560;
  readonly SVG_HEIGHT = 600;

  constructor(
    private route: ActivatedRoute,
    private api: ApiService,
  ) {}

  ngOnInit(): void {
    this.ticketId = Number(this.route.snapshot.paramMap.get('ticketId'));
    this.startStream();
  }

  ngOnDestroy(): void {
    this.closeStream();
  }

  startStream(): void {
    this.resetGraph();
    this.streamStatus = 'connecting';
    this.streamError = null;
    this.finalStatus = null;
    this.executionPath = [];

    this.closeStream();

    this.eventSource = this.api.openWorkflowStream(this.ticketId);

    this.eventSource.onopen = () => {
      this.streamStatus = 'running';
    };

    this.eventSource.onmessage = (event) => {
      try {
        const data: WorkflowEvent = JSON.parse(event.data);
        this.handleEvent(data);
      } catch {
        // Ignore unparseable events
      }
    };

    this.eventSource.onerror = () => {
      // EventSource closes automatically after the stream ends.
      // If the status is still running, it's a real error.
      if (this.streamStatus === 'running' || this.streamStatus === 'connecting') {
        this.streamStatus = 'error';
        this.streamError =
          'Could not connect to the workflow stream. ' +
          'Ensure the backend is running on port 8080 and the AI service on port 8000.';
      }
      this.closeStream();
    };
  }

  private handleEvent(event: WorkflowEvent): void {
    const node = this.findNode(event.node as WorkflowNode);
    if (!node) return;

    node.status = event.status;
    node.message = event.message;
    if (event.state) {
      node.lastState = event.state;
    }

    if (event.status === 'COMPLETED') {
      this.executionPath.push(event.node as WorkflowNode);
      this.updateTakenEdges(event.node as WorkflowNode, event.state);

      // Check for terminal nodes
      if (event.node === 'RESOLVE' || event.node === 'ESCALATE') {
        this.finalStatus = event.state?.finalStatus ?? event.node;
        this.streamStatus = 'complete';
        // Mark any unvisited nodes as SKIPPED
        this.markSkippedNodes();
        this.closeStream();
      }
    }

    // FAILED event — mark stream as error
    if (event.status === 'FAILED') {
      this.streamStatus = 'error';
      this.streamError = event.message;
      this.markSkippedNodes();
      this.closeStream();
    }
  }

  private updateTakenEdges(completedNode: WorkflowNode, state: WorkflowState | undefined): void {
    if (completedNode === 'CHECK_CONFIDENCE' && state) {
      const threshold = 0.70;
      const highConf = state.confidence >= threshold;
      this.setEdgeTaken('CHECK_CONFIDENCE', 'GENERATE_RESPONSE', highConf);
      this.setEdgeTaken('CHECK_CONFIDENCE', 'ESCALATE', !highConf);
    } else if (completedNode === 'CHECK_SENSITIVITY' && state) {
      this.setEdgeTaken('CHECK_SENSITIVITY', 'ESCALATE', state.sensitive);
      this.setEdgeTaken('CHECK_SENSITIVITY', 'RESOLVE', !state.sensitive);
    } else {
      // Non-conditional edges: mark the edge FROM this node as taken
      this.edges
        .filter((e) => e.from === completedNode && !e.conditional)
        .forEach((e) => (e.taken = true));
    }
  }

  private setEdgeTaken(from: WorkflowNode, to: WorkflowNode, taken: boolean): void {
    const edge = this.edges.find((e) => e.from === from && e.to === to);
    if (edge) edge.taken = taken;
  }

  private markSkippedNodes(): void {
    for (const node of this.nodes) {
      if (node.status === 'PENDING') {
        node.status = 'SKIPPED';
      }
    }
  }

  private resetGraph(): void {
    for (const node of this.nodes) {
      node.status = 'PENDING';
      node.lastState = null;
      node.message = '';
    }
    for (const edge of this.edges) {
      edge.taken = false;
    }
    this.selectedNode = null;
  }

  private closeStream(): void {
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }
  }

  selectNode(node: GraphNode): void {
    this.selectedNode = this.selectedNode?.id === node.id ? null : node;
  }

  findNode(id: WorkflowNode): GraphNode | undefined {
    return this.nodes.find((n) => n.id === id);
  }

  // SVG helpers — compute edge endpoints so lines connect circles properly

  edgePath(edge: GraphEdge): string {
    const from = this.findNode(edge.from);
    const to = this.findNode(edge.to);
    if (!from || !to) return '';
    const dx = to.x - from.x;
    const dy = to.y - from.y;
    const dist = Math.sqrt(dx * dx + dy * dy);
    if (dist === 0) return '';
    const r = this.NODE_RADIUS;
    const sx = from.x + (dx / dist) * r;
    const sy = from.y + (dy / dist) * r;
    const ex = to.x - (dx / dist) * (r + 8); // +8 for arrowhead
    const ey = to.y - (dy / dist) * (r + 8);
    return `M ${sx} ${sy} L ${ex} ${ey}`;
  }

  edgeLabelX(edge: GraphEdge): number {
    const from = this.findNode(edge.from);
    const to = this.findNode(edge.to);
    if (!from || !to) return 0;
    return (from.x + to.x) / 2;
  }

  edgeLabelY(edge: GraphEdge): number {
    const from = this.findNode(edge.from);
    const to = this.findNode(edge.to);
    if (!from || !to) return 0;
    return (from.y + to.y) / 2;
  }

  arrowTransform(edge: GraphEdge): string {
    const from = this.findNode(edge.from);
    const to = this.findNode(edge.to);
    if (!from || !to) return '';
    const dx = to.x - from.x;
    const dy = to.y - from.y;
    const angle = Math.atan2(dy, dx) * (180 / Math.PI);
    const dist = Math.sqrt(dx * dx + dy * dy);
    const r = this.NODE_RADIUS;
    const ex = to.x - (dx / dist) * (r + 2);
    const ey = to.y - (dy / dist) * (r + 2);
    return `translate(${ex},${ey}) rotate(${angle})`;
  }

  getNodeFill(node: GraphNode): string {
    const fills: Record<NodeStatus, string> = {
      PENDING: '#f3f4f6',
      RUNNING: '#dbeafe',
      COMPLETED: '#dcfce7',
      SKIPPED: '#f3f4f6',
      FAILED: '#fee2e2',
    };
    return fills[node.status];
  }

  getNodeStroke(node: GraphNode): string {
    const strokes: Record<NodeStatus, string> = {
      PENDING: '#d1d5db',
      RUNNING: '#3b82f6',
      COMPLETED: '#16a34a',
      SKIPPED: '#d1d5db',
      FAILED: '#dc2626',
    };
    return strokes[node.status];
  }

  getNodeTextColor(node: GraphNode): string {
    const colors: Record<NodeStatus, string> = {
      PENDING: '#9ca3af',
      RUNNING: '#1d4ed8',
      COMPLETED: '#15803d',
      SKIPPED: '#d1d5db',
      FAILED: '#dc2626',
    };
    return colors[node.status];
  }

  getEdgeStroke(edge: GraphEdge): string {
    if (!edge.conditional) {
      return edge.taken ? '#16a34a' : '#d1d5db';
    }
    if (edge.taken) return '#16a34a';
    // Conditional edge not taken = muted
    return '#e5e7eb';
  }

  getEdgeOpacity(edge: GraphEdge): number {
    if (edge.conditional && !edge.taken) return 0.4;
    return 1;
  }

  getStatusIcon(status: NodeStatus): string {
    const icons: Record<NodeStatus, string> = {
      PENDING: '○',
      RUNNING: '◉',
      COMPLETED: '✓',
      SKIPPED: '—',
      FAILED: '✗',
    };
    return icons[status];
  }

  getStatusBadgeClass(status: NodeStatus): string {
    const classes: Record<NodeStatus, string> = {
      PENDING: 'bg-secondary',
      RUNNING: 'bg-primary',
      COMPLETED: 'bg-success',
      SKIPPED: 'bg-light text-dark border',
      FAILED: 'bg-danger',
    };
    return classes[status];
  }

  confidencePercent(state: WorkflowState | null): string {
    if (!state) return '—';
    return `${(state.confidence * 100).toFixed(0)}%`;
  }
}
