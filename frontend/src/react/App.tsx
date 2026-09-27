import { FormEvent, ReactNode, useEffect, useMemo, useState } from 'react';
import { SignInButton, SignUpButton, UserButton, useAuth } from '@clerk/react';
import { BrowserRouter, Link, NavLink, Route, Routes, useNavigate, useParams } from 'react-router-dom';
import type { TicketSummary, TicketDetail, TicketStatus, TicketPriority, KnowledgeArticle } from '../app/core/models';
import { api } from './api';

const ST: TicketStatus[] = ['OPEN', 'IN_PROGRESS', 'ESCALATED', 'RESOLVED', 'CLOSED'];
const PR: TicketPriority[] = ['LOW', 'MEDIUM', 'HIGH'];

const formatDate = (x: string) =>
  new Intl.DateTimeFormat('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(
    new Date(x)
  );

function Load<T>({
  get,
  children,
}: {
  get: () => Promise<T>;
  children: (x: { data?: T; setData: (x: T) => void; loading: boolean; error: string; reload: () => Promise<void> }) => ReactNode;
}) {
  const [data, setData] = useState<T>();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const reload = async () => {
    setLoading(true);
    try {
      setData(await get());
      setError('');
    } catch {
      setError('Could not connect to support service. Ensure backend is running.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void reload();
  }, []);

  return <>{children({ data, setData, loading, error, reload })}</>;
}

function BadgePill({ value }: { value: string }) {
  const v = value.toLowerCase();
  return (
    <span className={`badge-pill badge-${v}`}>
      <span className="priority-dot" />
      {value.replace('_', ' ')}
    </span>
  );
}

function PriorityChip({ priority }: { priority: string }) {
  const p = priority.toLowerCase();
  return (
    <span className={`priority-chip ${p}`}>
      <span className="priority-dot" />
      {priority}
    </span>
  );
}

function SkeletonLoader() {
  return (
    <div style={{ padding: '28px', display: 'flex', flexDirection: 'column', gap: '14px' }}>
      <div style={{ height: '20px', background: 'rgba(20, 30, 50, 0.05)', borderRadius: '6px' }} />
      <div style={{ height: '20px', background: 'rgba(20, 30, 50, 0.05)', borderRadius: '6px' }} />
      <div style={{ height: '20px', background: 'rgba(20, 30, 50, 0.05)', borderRadius: '6px' }} />
    </div>
  );
}

function EmptyState({ title, copy, action }: { title: string; copy: string; action?: ReactNode }) {
  return (
    <div style={{ padding: '60px 24px', textAlign: 'center', background: 'var(--surface-solid)', borderRadius: 'var(--r-lg)', border: '1px solid var(--border)' }}>
      <div style={{ display: 'flex', justifyContent: 'center', marginBottom: '16px' }}>
        <div className="spectral-orb-visual" style={{ width: '64px', height: '64px' }} />
      </div>
      <h3 style={{ fontSize: '18px', fontWeight: 800, marginBottom: '6px', color: 'var(--text)' }}>{title}</h3>
      <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '20px' }}>{copy}</p>
      {action || (
        <Link to="/tickets/new" className="btn btn-primary">
          + Create Ticket
        </Link>
      )}
    </div>
  );
}

function ErrorAlert({ message }: { message: string }) {
  return (
    <div
      style={{
        padding: '16px 20px',
        borderRadius: 'var(--r-md)',
        background: 'var(--danger-bg)',
        border: '1px solid rgba(216, 102, 102, 0.25)',
        color: 'var(--danger)',
        marginBottom: '24px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
      }}
    >
      <div>
        <strong style={{ fontSize: '13px', display: 'block' }}>Service Connection Issue</strong>
        <span style={{ fontSize: '12px' }}>{message}</span>
      </div>
      <button className="btn btn-secondary" onClick={() => window.location.reload()}>
        Retry →
      </button>
    </div>
  );
}

function Shell({ children }: { children: ReactNode }) {
  const { isSignedIn } = useAuth();
  const [searchOpen, setSearchOpen] = useState(false);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        setSearchOpen((prev) => !prev);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  return (
    <div className="app-shell">
      <header className="top-nav">
        <Link className="brand-mark" to="/">
          <span className="brand-dot" />
          SmartHelp
        </Link>

        <nav className="nav-links">
          <NavLink end to="/" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
            Dashboard
          </NavLink>
          <NavLink to="/tickets" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
            Tickets
          </NavLink>
          <NavLink to="/knowledge" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
            Knowledge
          </NavLink>
          <NavLink to="/operations" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
            Operations
          </NavLink>
        </nav>

        <div className="nav-actions">
          <button className="search-trigger-btn" onClick={() => setSearchOpen(true)}>
            <span>Search...</span>
            <kbd className="kbd-chip">⌘K</kbd>
          </button>

          <Link className="btn btn-primary" to="/tickets/new">
            + New Ticket
          </Link>

          {isSignedIn ? (
            <UserButton />
          ) : (
            <div style={{ display: 'flex', gap: '8px' }}>
              <SignInButton>
                <button className="btn btn-secondary">Sign In</button>
              </SignInButton>
              <SignUpButton>
                <button className="btn btn-blue">Sign Up</button>
              </SignUpButton>
            </div>
          )}
        </div>
      </header>

      <main className="main-content">{children}</main>

      {searchOpen && <SearchModal onClose={() => setSearchOpen(false)} />}
    </div>
  );
}

function Head({
  label,
  title,
  subtext,
  action,
  showOrb = false,
}: {
  label: string;
  title: string;
  subtext?: string;
  action?: ReactNode;
  showOrb?: boolean;
}) {
  return (
    <header className={`editorial-intro ${showOrb ? 'has-orb-art' : ''}`}>
      <div>
        <div className="editorial-label">// {label}</div>
        <h1 className="editorial-headline">{title}</h1>
        {subtext && <p className="editorial-subtext">{subtext}</p>}
        {action && <div style={{ marginTop: '20px' }}>{action}</div>}
      </div>

      {showOrb && <div className="hero-art-spacer" aria-hidden="true" />}
    </header>
  );
}

function SearchModal({ onClose }: { onClose: () => void }) {
  const [query, setQuery] = useState('');
  const { data: tickets } = useData(api.tickets);
  const { data: articles } = useData(api.knowledge);
  const navigate = useNavigate();

  const filteredTickets = useMemo(
    () => (tickets || []).filter((t) => (t.subject + t.userName).toLowerCase().includes(query.toLowerCase())).slice(0, 5),
    [tickets, query]
  );

  const filteredArticles = useMemo(
    () => (articles || []).filter((a) => (a.title + a.content).toLowerCase().includes(query.toLowerCase())).slice(0, 5),
    [articles, query]
  );

  return (
    <div className="dark-overlay" onClick={onClose}>
      <div className="dark-glass-panel" onClick={(e) => e.stopPropagation()}>
        <div className="search-field" style={{ background: 'rgba(255, 255, 255, 0.08)', border: '1px solid rgba(255, 255, 255, 0.15)', marginBottom: '20px', padding: '12px 16px' }}>
          <span style={{ color: 'var(--cyan)', fontSize: '16px' }}>⌕</span>
          <input
            autoFocus
            style={{ color: '#ffffff' }}
            placeholder="Type to search tickets, users, knowledge..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>

        <div style={{ maxHeight: '340px', overflowY: 'auto' }}>
          {query ? (
            <>
              <div style={{ fontSize: '10px', fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.1em', marginBottom: '8px' }}>
                Tickets ({filteredTickets.length})
              </div>
              {filteredTickets.map((t) => (
                <div
                  key={t.id}
                  style={{
                    padding: '12px',
                    borderRadius: 'var(--r-md)',
                    background: 'rgba(255, 255, 255, 0.05)',
                    border: '1px solid rgba(255, 255, 255, 0.08)',
                    marginBottom: '8px',
                    cursor: 'pointer',
                  }}
                  onClick={() => {
                    navigate(`/tickets/${t.id}`);
                    onClose();
                  }}
                >
                  <div style={{ fontWeight: 600, fontSize: '13px', color: '#ffffff' }}>{t.subject}</div>
                  <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                    #{String(t.id).padStart(4, '0')} · {t.userName}
                  </div>
                </div>
              ))}

              <div style={{ fontSize: '10px', fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.1em', margin: '16px 0 8px' }}>
                Knowledge ({filteredArticles.length})
              </div>
              {filteredArticles.map((a) => (
                <div
                  key={a.id}
                  style={{
                    padding: '12px',
                    borderRadius: 'var(--r-md)',
                    background: 'rgba(255, 255, 255, 0.05)',
                    border: '1px solid rgba(255, 255, 255, 0.08)',
                    marginBottom: '8px',
                    cursor: 'pointer',
                  }}
                  onClick={() => {
                    navigate('/knowledge');
                    onClose();
                  }}
                >
                  <div style={{ fontWeight: 600, fontSize: '13px', color: '#ffffff' }}>{a.title}</div>
                  <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{a.content.slice(0, 65)}...</div>
                </div>
              ))}
            </>
          ) : (
            <div style={{ textAlign: 'center', padding: '30px 0', color: 'var(--text-muted)', fontSize: '13px' }}>
              Press ESC or click outside to dismiss command search...
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function TicketDataTable({ tickets }: { tickets: TicketSummary[] }) {
  const navigate = useNavigate();

  if (!tickets.length) {
    return <EmptyState title="No tickets in queue" copy="Try changing your search query or filter selection." />;
  }

  return (
    <div className="report-panel">
      <table className="editorial-table">
        <thead>
          <tr>
            <th>Ticket Reference</th>
            <th>Customer</th>
            <th>Priority</th>
            <th>Status</th>
            <th>Updated</th>
            <th>Action</th>
          </tr>
        </thead>
        <tbody>
          {tickets.map((t) => (
            <tr key={t.id} style={{ cursor: 'pointer' }} onClick={() => navigate(`/tickets/${t.id}`)}>
              <td>
                <div style={{ display: 'flex', flexDirection: 'column' }}>
                  <span style={{ fontWeight: 600, color: 'var(--text)' }}>{t.subject}</span>
                  <span style={{ fontSize: '11px', color: 'var(--text-muted)', fontFamily: 'monospace' }}>
                    #{String(t.id).padStart(4, '0')} · {t.categoryName || 'General Support'}
                  </span>
                </div>
              </td>
              <td>
                <div style={{ fontWeight: 500 }}>{t.userName}</div>
              </td>
              <td>
                <PriorityChip priority={t.priority} />
              </td>
              <td>
                <BadgePill value={t.status} />
              </td>
              <td style={{ color: 'var(--text-muted)', fontSize: '12px' }}>{formatDate(t.updatedAt)}</td>
              <td>
                <button className="btn btn-secondary" style={{ padding: '4px 10px', fontSize: '11px' }} onClick={(e) => { e.stopPropagation(); navigate(`/tickets/${t.id}`); }}>
                  View →
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Dashboard() {
  return (
    <Load get={api.tickets}>
      {({ data, loading, error }) => {
        const all = data || [];
        const count = (s?: TicketStatus) => (s ? all.filter((t) => t.status === s).length : all.length);
        const attention = all.filter((t) => t.status === 'ESCALATED' || t.priority === 'HIGH').slice(0, 5);

        return error ? (
          <ErrorAlert message={error} />
        ) : (
          <div className="animate-fade-in">
            <Head
              label="SUPPORT OVERVIEW"
              title="Good evening. Here's what needs attention."
              subtext="Everything important across your active customer queues and AI operations in one place."
              showOrb={true}
              action={
                <Link to="/tickets/new" className="btn btn-blue">
                  + Create Ticket
                </Link>
              }
            />

            {/* METRICS ROW */}
            <div className="metrics-grid">
              <div className="metric-card">
                <span className="metric-title">TOTAL TICKETS</span>
                <div className="metric-number">{all.length}</div>
                <div className="metric-sub">Across active queues</div>
              </div>

              <div className="metric-card">
                <span className="metric-title">OPEN QUEUE</span>
                <div className="metric-number">{count('OPEN')}</div>
                <div className="metric-sub">Needs assignment</div>
              </div>

              <div className="metric-card">
                <span className="metric-title">IN PROGRESS</span>
                <div className="metric-number">{count('IN_PROGRESS')}</div>
                <div className="metric-sub">Active agent replies</div>
              </div>

              <div className="metric-card">
                <span className="metric-title">NEEDS ATTENTION</span>
                <div className="metric-number" style={{ color: count('ESCALATED') > 0 ? 'var(--danger)' : 'inherit' }}>
                  {count('ESCALATED')}
                </div>
                <div className={`metric-sub ${count('ESCALATED') > 0 ? 'alert' : ''}`}>
                  {count('ESCALATED') > 0 ? '● Escalations pending' : 'Queue clear'}
                </div>
              </div>
            </div>

            {/* NEEDS ATTENTION SECTION */}
            <div style={{ marginBottom: '36px' }}>
              <div className="report-panel-header" style={{ background: 'transparent', padding: '0 0 16px 0', borderBottom: 'none' }}>
                <h2 className="report-panel-title">
                  <span>●</span> Needs Attention
                </h2>
                <Link to="/tickets" className="btn btn-secondary" style={{ padding: '5px 12px', fontSize: '11px' }}>
                  Open Inbox →
                </Link>
              </div>
              {loading ? <SkeletonLoader /> : <TicketDataTable tickets={attention} />}
            </div>

            {/* RECENT TICKETS */}
            <div>
              <div className="report-panel-header" style={{ background: 'transparent', padding: '0 0 16px 0', borderBottom: 'none' }}>
                <h2 className="report-panel-title">
                  <span>💬</span> Recent Customer Conversations
                </h2>
                <span style={{ fontSize: '12px', color: 'var(--text-muted)' }}>Latest items</span>
              </div>
              <TicketDataTable tickets={all.slice(0, 6)} />
            </div>
          </div>
        );
      }}
    </Load>
  );
}

function Tickets() {
  const [q, setQ] = useState('');
  const [s, setS] = useState('');
  const [p, setP] = useState('');

  return (
    <Load get={api.tickets}>
      {({ data, setData, loading, error }) => {
        const all = data || [];
        const shown = all.filter(
          (t) => (!s || t.status === s) && (!p || t.priority === p) && `${t.subject} ${t.userName}`.toLowerCase().includes(q.toLowerCase())
        );

        return (
          <div className="animate-fade-in">
            <Head
              label="CUSTOMER QUEUE"
              title="Resolve faster. Respond better."
              subtext="Unified conversation archive. Filter active tickets, assign agent priority, and handle escalations."
              action={
                <Link to="/tickets/new" className="btn btn-primary">
                  + Create Ticket
                </Link>
              }
            />

            {/* TOOLBAR */}
            <div className="filter-bar">
              <div className="filter-tabs">
                <button className={`filter-tab ${s === '' ? 'active' : ''}`} onClick={() => setS('')}>
                  All ({all.length})
                </button>
                {ST.map((status) => {
                  const cnt = all.filter((t) => t.status === status).length;
                  return (
                    <button key={status} className={`filter-tab ${s === status ? 'active' : ''}`} onClick={() => setS(status)}>
                      {status.replace('_', ' ')} ({cnt})
                    </button>
                  );
                })}
              </div>

              <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
                <div className="search-field">
                  <span>⌕</span>
                  <input placeholder="Filter subject or customer..." value={q} onChange={(e) => setQ(e.target.value)} />
                </div>

                <select
                  style={{
                    background: 'var(--surface-solid)',
                    border: '1px solid var(--border)',
                    padding: '8px 14px',
                    borderRadius: 'var(--r-md)',
                    color: 'var(--text)',
                    fontSize: '12px',
                  }}
                  value={p}
                  onChange={(e) => setP(e.target.value)}
                >
                  <option value="">All Priorities</option>
                  {PR.map((x) => (
                    <option key={x} value={x}>
                      {x} Priority
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {error ? <ErrorAlert message={error} /> : loading ? <SkeletonLoader /> : <TicketDataTable tickets={shown} />}
          </div>
        );
      }}
    </Load>
  );
}

function Form() {
  const nav = useNavigate();
  const { data: users } = useData(api.users);
  const { data: cats } = useData(api.categories);
  const [f, setF] = useState({ userId: 0, categoryId: '', subject: '', description: '', priority: 'MEDIUM' as TicketPriority });

  return (
    <div className="animate-fade-in">
      <Head
        label="NEW REQUEST"
        title="Create Ticket"
        subtext="Capture context once so the team and AI copilot can respond with precision."
      />

      <form
        className="form-panel"
        onSubmit={async (e) => {
          e.preventDefault();
          const t: any = await api.createTicket({ ...f, userId: +f.userId, categoryId: f.categoryId ? +f.categoryId : null });
          nav('/tickets/' + t.id);
        }}
      >
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
          <div className="form-group">
            <label>Customer Account</label>
            <select required value={f.userId} onChange={(e) => setF({ ...f, userId: +e.target.value })}>
              <option value={0}>Select Customer</option>
              {users?.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.name} ({u.email})
                </option>
              ))}
            </select>
          </div>

          <div className="form-group">
            <label>Priority</label>
            <select value={f.priority} onChange={(e) => setF({ ...f, priority: e.target.value as TicketPriority })}>
              {PR.map((x) => (
                <option key={x} value={x}>
                  {x} Priority
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="form-group">
          <label>Category</label>
          <select value={f.categoryId} onChange={(e) => setF({ ...f, categoryId: e.target.value })}>
            <option value="">Let AI Classify Category</option>
            {cats?.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </div>

        <div className="form-group">
          <label>Subject</label>
          <input required placeholder="Brief description of the issue..." value={f.subject} onChange={(e) => setF({ ...f, subject: e.target.value })} />
        </div>

        <div className="form-group">
          <label>Detailed Description</label>
          <textarea
            required
            rows={6}
            placeholder="Full context, customer report, error logs..."
            value={f.description}
            onChange={(e) => setF({ ...f, description: e.target.value })}
          />
        </div>

        <div style={{ display: 'flex', gap: '12px', justifyContent: 'flex-end' }}>
          <button className="btn btn-secondary" type="button" onClick={() => nav('/tickets')}>
            Cancel
          </button>
          <button className="btn btn-primary" type="submit">
            Create Ticket ↗
          </button>
        </div>
      </form>
    </div>
  );
}

function useData<T>(get: () => Promise<T>) {
  const [data, setData] = useState<T>();
  useEffect(() => {
    void get().then(setData);
  }, []);
  return { data, setData };
}

function Detail() {
  const { id = '' } = useParams();

  return (
    <Load get={() => api.ticket(id)}>
      {({ data, reload, loading, error }) => {
        if (error) return <ErrorAlert message={error} />;
        if (!data || loading) return <SkeletonLoader />;

        return (
          <div className="animate-fade-in">
            <div style={{ marginBottom: '16px' }}>
              <Link to="/tickets" className="btn btn-secondary" style={{ padding: '5px 12px', fontSize: '11px' }}>
                ← Back to Inbox
              </Link>
            </div>

            <Head
              label={`TICKET #${String(data.ticket.id).padStart(4, '0')}`}
              title={data.ticket.subject}
              action={
                <button className="btn btn-blue" onClick={async () => await api.analyze(id)}>
                  ✦ Run AI Analysis
                </button>
              }
            />

            <div className="detail-grid">
              {/* SIDEBAR */}
              <div className="detail-sidebar">
                <div className="meta-item">
                  <label>Status</label>
                  <div className="meta-value">
                    <BadgePill value={data.ticket.status} />
                  </div>
                </div>

                <div className="meta-item">
                  <label>Priority</label>
                  <div className="meta-value">
                    <PriorityChip priority={data.ticket.priority} />
                  </div>
                </div>

                <div className="meta-item">
                  <label>Customer</label>
                  <div className="meta-value">{data.ticket.userName}</div>
                </div>

                <div className="meta-item">
                  <label>Category</label>
                  <div className="meta-value">{data.ticket.categoryName || 'General Support'}</div>
                </div>

                <div className="meta-item">
                  <label>Created</label>
                  <div className="meta-value" style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{formatDate(data.ticket.createdAt)}</div>
                </div>
              </div>

              {/* CONVERSATION THREAD */}
              <div className="message-thread">
                <div className="message-card">
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '10px' }}>
                    <span style={{ fontWeight: 700, fontSize: '12px' }}>{data.ticket.userName} (Customer)</span>
                    <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{formatDate(data.ticket.createdAt)}</span>
                  </div>
                  <div style={{ fontSize: '14px', color: 'var(--text-secondary)', lineHeight: '1.6' }}>{data.ticket.description}</div>
                </div>

                {data.responses.map((r) => (
                  <div className={`message-card ${r.senderType === 'AI' ? 'ai' : ''}`} key={r.id}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '10px' }}>
                      <span style={{ fontWeight: 700, fontSize: '12px', color: r.senderType === 'AI' ? 'var(--violet)' : 'inherit' }}>
                        {r.senderType === 'AI' ? '✦ AI Copilot' : 'Support Agent'}
                      </span>
                      <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{formatDate(r.createdAt)}</span>
                    </div>
                    <div style={{ fontSize: '14px', color: 'var(--text-secondary)', lineHeight: '1.6' }}>{r.message}</div>
                  </div>
                ))}

                {/* REPLY BOX */}
                <form
                  className="reply-box"
                  onSubmit={async (e: FormEvent<HTMLFormElement>) => {
                    e.preventDefault();
                    const v = new FormData(e.currentTarget).get('message') as string;
                    if (v) {
                      await api.createResponse(id, { message: v, senderType: 'AGENT' });
                      e.currentTarget.reset();
                      await reload();
                    }
                  }}
                >
                  <div style={{ fontWeight: 700, fontSize: '12px', marginBottom: '8px' }}>Reply</div>
                  <textarea name="message" placeholder="Write a response..." />
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '12px' }}>
                    <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Ctrl / Cmd + Enter</span>
                    <button className="btn btn-primary" type="submit">
                      Send Reply →
                    </button>
                  </div>
                </form>
              </div>

              {/* AI ASSIST PANEL */}
              <div className="detail-sidebar" style={{ background: 'linear-gradient(180deg, rgba(115, 103, 240, 0.04) 0%, #ffffff 100%)' }}>
                <div style={{ fontSize: '11px', fontWeight: 700, letterSpacing: '0.12em', color: 'var(--violet)', textTransform: 'uppercase', marginBottom: '12px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                  <span>✦</span> AI Assist
                </div>
                <p style={{ fontSize: '13px', color: 'var(--text-secondary)', lineHeight: '1.5', marginBottom: '20px' }}>
                  Evaluates issue reports against knowledge articles to generate synthesis context and solution confidence.
                </p>

                <Link to={`/workflow/${id}`} className="btn btn-secondary" style={{ width: '100%', justifyContent: 'center' }}>
                  View Decision Graph →
                </Link>
              </div>
            </div>
          </div>
        );
      }}
    </Load>
  );
}

function Knowledge() {
  const { data: articles, setData } = useData(api.knowledge);
  const { data: categories } = useData(api.categories);
  const [q, setQ] = useState('');

  const shown = (articles || []).filter((a) => (a.title + a.content).toLowerCase().includes(q.toLowerCase()));

  return (
    <div className="animate-fade-in">
      <Head
        label="KNOWLEDGE ARCHIVE"
        title="Answers, policies and support intelligence."
        subtext="Searchable repository of canonical help documentation and AI synthesis context."
        action={
          <span style={{ fontSize: '12px', color: 'var(--text-muted)', fontWeight: 600 }}>
            {shown.length} Articles
          </span>
        }
      />

      <div style={{ marginBottom: '28px' }}>
        <div className="search-field" style={{ maxWidth: '420px' }}>
          <span>⌕</span>
          <input placeholder="Search knowledge base..." value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
      </div>

      <div className="kb-grid">
        {shown.map((article, idx) => (
          <div className="kb-card" key={article.id}>
            <div>
              <div style={{ fontSize: '10px', fontWeight: 700, color: 'var(--blue)', textTransform: 'uppercase', letterSpacing: '0.08em', marginBottom: '6px' }}>
                0{idx + 1} · {categories?.find((c) => c.id === article.categoryId)?.name || 'General'}
              </div>
              <h3 style={{ fontSize: '17px', fontWeight: 700, marginBottom: '10px', color: 'var(--text)' }}>{article.title}</h3>
              <p style={{ fontSize: '13px', color: 'var(--text-secondary)', lineHeight: '1.5', marginBottom: '20px' }}>
                {article.content.length > 140 ? article.content.slice(0, 140) + '...' : article.content}
              </p>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingTop: '14px', borderTop: '1px solid var(--border)' }}>
              <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Article #{article.id}</span>
              <button
                className="btn btn-secondary"
                style={{ padding: '4px 10px', fontSize: '11px' }}
                onClick={async () => {
                  if (confirm('Delete article?')) {
                    await api.deleteKnowledge(article.id);
                    setData((articles || []).filter((a) => a.id !== article.id));
                  }
                }}
              >
                Delete →
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function Operations() {
  const { data: users, setData: setUsers } = useData(api.users);
  const { data: categories, setData: setCategories } = useData(api.categories);
  const [name, setName] = useState('');

  return (
    <div className="animate-fade-in">
      <Head
        label="OPERATIONS"
        title="Live support performance and system health."
        subtext="Manage team workspace access, security roles, and ticket category classifications."
      />

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '28px' }}>
        {/* MEMBERS */}
        <div className="report-panel">
          <div className="report-panel-header">
            <h2 className="report-panel-title">👥 Team Members</h2>
            <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{users?.length || 0} Members</span>
          </div>
          <div style={{ padding: '16px' }}>
            {users?.map((u) => (
              <div
                key={u.id}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '12px 14px',
                  borderRadius: 'var(--r-md)',
                  background: 'var(--surface-muted)',
                  marginBottom: '8px',
                  border: '1px solid var(--border)',
                }}
              >
                <div>
                  <div style={{ fontWeight: 600, fontSize: '13px' }}>{u.name}</div>
                  <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{u.email}</div>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <BadgePill value={u.role} />
                  <button
                    className="btn btn-secondary"
                    style={{ padding: '4px 8px', fontSize: '11px' }}
                    onClick={async () => {
                      await api.deleteUser(u.id);
                      setUsers((users || []).filter((x) => x.id !== u.id));
                    }}
                  >
                    Remove
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* CATEGORIES */}
        <div className="report-panel">
          <div className="report-panel-header">
            <h2 className="report-panel-title">◇ Support Categories</h2>
          </div>
          <div style={{ padding: '16px' }}>
            <form
              style={{ display: 'flex', gap: '8px', marginBottom: '16px' }}
              onSubmit={async (e) => {
                e.preventDefault();
                if (name) {
                  const n: any = await api.createCategory({ name });
                  setCategories([...(categories || []), n]);
                  setName('');
                }
              }}
            >
              <input
                style={{
                  flex: 1,
                  background: 'var(--surface-muted)',
                  border: '1px solid var(--border)',
                  borderRadius: 'var(--r-md)',
                  padding: '8px 12px',
                  fontSize: '12px',
                  color: 'var(--text)',
                }}
                placeholder="New Category Name..."
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
              <button className="btn btn-primary" type="submit">
                Add
              </button>
            </form>

            {categories?.map((c) => (
              <div
                key={c.id}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '10px 14px',
                  borderRadius: 'var(--r-md)',
                  background: 'var(--surface-muted)',
                  marginBottom: '8px',
                  border: '1px solid var(--border)',
                }}
              >
                <div style={{ fontSize: '13px', fontWeight: 600 }}>◇ {c.name}</div>
                <button
                  className="btn btn-secondary"
                  style={{ padding: '4px 8px', fontSize: '11px' }}
                  onClick={async () => {
                    await api.deleteCategory(c.id);
                    setCategories((categories || []).filter((x) => x.id !== c.id));
                  }}
                >
                  Delete
                </button>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

function Workflow() {
  const { id = '' } = useParams();
  const { data: runs, setData } = useData(() => api.agentRuns(id));
  const [run, setRun] = useState(false);

  return (
    <div className="animate-fade-in">
      <Head
        label={`TICKET #${id} / WORKFLOW`}
        title="AI Copilot Decision Pipeline"
        subtext="Step-by-step resolution execution graph and human approval runs."
        action={
          <button
            className="btn btn-blue"
            disabled={run}
            onClick={async () => {
              setRun(true);
              await api.analyze(id);
              setData(await api.agentRuns(id));
              setRun(false);
            }}
          >
            {run ? 'Executing...' : 'Execute AI Pipeline →'}
          </button>
        }
      />

      <div className="report-panel" style={{ padding: '24px', marginBottom: '28px' }}>
        <h3 style={{ fontSize: '14px', fontWeight: 700, color: 'var(--text)', marginBottom: '16px' }}>Decision Nodes Graph</h3>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: '10px', textAlign: 'center' }}>
          {['CLASSIFY', 'SEARCH KNOWLEDGE', 'CHECK CONFIDENCE', 'GENERATE RESPONSE', 'VERIFY'].map((node, i) => (
            <div
              key={node}
              style={{
                padding: '14px 8px',
                borderRadius: 'var(--r-md)',
                background: 'var(--surface-muted)',
                border: '1px solid var(--border)',
              }}
            >
              <div style={{ fontSize: '10px', color: 'var(--blue)', fontWeight: 700, marginBottom: '2px' }}>STEP 0{i + 1}</div>
              <div style={{ fontSize: '11px', fontWeight: 700, color: 'var(--text)' }}>{node}</div>
            </div>
          ))}
        </div>
      </div>

      <div className="report-panel">
        <div className="report-panel-header">
          <h2 className="report-panel-title">📋 Run History</h2>
        </div>
        <div style={{ padding: '20px' }}>
          {runs?.length ? (
            runs.map((r: any) => (
              <div
                key={r.id}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '14px 16px',
                  borderRadius: 'var(--r-md)',
                  background: 'var(--surface-muted)',
                  border: '1px solid var(--border)',
                  marginBottom: '8px',
                }}
              >
                <div>
                  <div style={{ fontWeight: 700, fontSize: '13.5px' }}>Run #{r.id.slice(0, 8)}</div>
                  <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Created {formatDate(r.createdAt)}</div>
                </div>
                <BadgePill value={r.status} />
              </div>
            ))
          ) : (
            <EmptyState title="No Workflow Execution Runs" copy="Trigger an AI pipeline run to inspect decision steps." />
          )}
        </div>
      </div>
    </div>
  );
}

function NotFound() {
  return (
    <div style={{ padding: '80px 0', textAlign: 'center' }}>
      <div style={{ fontSize: '72px', fontWeight: 800, color: 'var(--blue)' }}>404</div>
      <h2 style={{ fontSize: '24px', fontWeight: 800, margin: '12px 0 8px' }}>Page Not Found</h2>
      <p style={{ color: 'var(--text-muted)', marginBottom: '24px' }}>The requested route does not exist.</p>
      <Link to="/" className="btn btn-primary">
        ← Return Home
      </Link>
    </div>
  );
}

export function App() {
  return (
    <BrowserRouter>
      <Shell>
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/tickets" element={<Tickets />} />
          <Route path="/tickets/new" element={<Form />} />
          <Route path="/tickets/:id" element={<Detail />} />
          <Route path="/knowledge" element={<Knowledge />} />
          <Route path="/operations" element={<Operations />} />
          <Route path="/workflow/:id" element={<Workflow />} />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </Shell>
    </BrowserRouter>
  );
}
