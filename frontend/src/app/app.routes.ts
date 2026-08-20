import { Routes } from '@angular/router';

import { DashboardComponent } from './dashboard/dashboard.component';
import { TicketListComponent } from './tickets/ticket-list.component';
import { TicketFormComponent } from './tickets/ticket-form.component';
import { TicketDetailComponent } from './tickets/ticket-detail.component';
import { KnowledgeListComponent } from './knowledge/knowledge-list.component';
import { WorkflowGraphComponent } from './workflow/workflow-graph.component';

/**
 * Application routes.
 *
 * Route structure:
 *   /dashboard          — landing page with ticket stats and recent list
 *   /tickets            — full ticket list with filters
 *   /tickets/new        — create ticket form
 *   /tickets/:id        — ticket detail, edit, responses, AI analysis
 *   /knowledge          — knowledge base list + inline create/edit
 *   /workflow/:ticketId — live SSE workflow graph for one ticket
 *   ''                  — redirects to /dashboard
 */
export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  { path: 'dashboard', component: DashboardComponent },
  { path: 'tickets', component: TicketListComponent },
  { path: 'tickets/new', component: TicketFormComponent },
  { path: 'tickets/:id', component: TicketDetailComponent },
  { path: 'knowledge', component: KnowledgeListComponent },
  { path: 'workflow/:ticketId', component: WorkflowGraphComponent },
  { path: '**', redirectTo: 'dashboard' },
];
