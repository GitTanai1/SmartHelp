import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { ApiService } from './api.service';

describe('ApiService agent-run API contract', () => {
  let api: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('requests persisted approvals from the versioned run endpoint', () => {
    api.getAgentApprovals(12, 'run-1').subscribe();

    const request = http.expectOne('http://localhost:8080/api/v1/tickets/12/agent-runs/run-1/approvals');
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('posts an explicit approval decision', () => {
    api.decideAgentApproval(12, 'run-1', 'approval-1', 'approve', 'Reviewed').subscribe();

    const request = http.expectOne(
      'http://localhost:8080/api/v1/tickets/12/agent-runs/run-1/approvals/approval-1/approve',
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ note: 'Reviewed' });
    request.flush({});
  });

  it('sends bounded pagination with ticket filters', () => {
    api.getTickets({ status: 'OPEN', limit: 25, offset: 50 }).subscribe();

    const request = http.expectOne((candidate) => candidate.url.endsWith('/tickets'));
    expect(request.request.params.get('status')).toBe('OPEN');
    expect(request.request.params.get('limit')).toBe('25');
    expect(request.request.params.get('offset')).toBe('50');
    request.flush([]);
  });

  it('requests only events after the supplied reconnect cursor', () => {
    api.getAgentRunEvents(12, 'run-1', 42).subscribe();

    const request = http.expectOne((candidate) => candidate.url.endsWith('/tickets/12/agent-runs/run-1/events'));
    expect(request.request.params.get('afterEventId')).toBe('42');
    request.flush([]);
  });
});
