"""
Deterministic end-to-end tests for the SmartHelp LangGraph workflow.

These tests run without a live LLM and without a running Spring Boot backend.
All external calls (tools) are mocked with unittest.mock.

Three required scenarios are verified:
  1. High-confidence billing ticket  -> RESOLVED
  2. Low-confidence hardware ticket  -> ESCALATED
  3. High-confidence sensitive security ticket -> ESCALATED
"""
import os
import sys
from unittest.mock import MagicMock, patch

# Force deterministic mode (no LLM calls)
os.environ['LLM_API_KEY'] = ''

from graph import WORKFLOW, route_confidence, route_sensitivity

# ── Routing unit tests ────────────────────────────────────────────────────────

def test_route_confidence():
    assert route_confidence({'confidence': 0.75}) == 'GENERATE_RESPONSE'
    assert route_confidence({'confidence': 0.70}) == 'GENERATE_RESPONSE'  # exactly at threshold
    assert route_confidence({'confidence': 0.69}) == 'ESCALATE'
    assert route_confidence({'confidence': 0.00}) == 'ESCALATE'
    print('test_route_confidence: PASS')

def test_route_sensitivity():
    assert route_sensitivity({'is_sensitive': True})  == 'ESCALATE'
    assert route_sensitivity({'is_sensitive': False}) == 'RESOLVE'
    print('test_route_sensitivity: PASS')

# ── Scenario helpers ──────────────────────────────────────────────────────────

def run_scenario(name, ticket_mock, articles_mock, expected_status, expected_nodes):
    with patch('graph.get_ticket') as mock_gt, \
         patch('graph.search_knowledge_base') as mock_kb, \
         patch('graph.get_customer_history') as mock_ch, \
         patch('graph.create_response') as mock_cr, \
         patch('graph.escalate_ticket') as mock_et, \
         patch('graph.update_ticket_status') as mock_us:

        mock_gt.invoke        = MagicMock(return_value=ticket_mock)
        mock_kb.invoke        = MagicMock(return_value=articles_mock)
        mock_ch.invoke        = MagicMock(return_value=[])
        mock_cr.invoke        = MagicMock(return_value={'id': 99})
        mock_et.invoke        = MagicMock(return_value={'id': 99})
        mock_us.invoke        = MagicMock(return_value={'id': 99})

        ticket_id = ticket_mock['ticket']['id']
        state = WORKFLOW.invoke({'ticket_id': ticket_id, 'path': [], 'confidence': 0.0})

        actual_status = state.get('final_status')
        actual_path   = state.get('path', [])
        path_str      = ' -> '.join(actual_path)

        ok_status = actual_status == expected_status
        ok_path   = all(node in actual_path for node in expected_nodes)

        label_status = 'PASS' if ok_status else f'FAIL (got {actual_status}, want {expected_status})'
        label_path   = 'PASS' if ok_path   else f'FAIL (path={path_str})'

        print(f'{name}:')
        print(f'  Path:         {path_str}')
        print(f'  Final status: {actual_status}')
        print(f'  Status:       {label_status}')
        print(f'  Path nodes:   {label_path}')
        print()
        return ok_status and ok_path

# ── Test data ─────────────────────────────────────────────────────────────────

TICKET_BILLING = {'ticket': {
    'id': 1, 'subject': 'Payment deducted but subscription inactive',
    'description': 'I was charged 999 but my subscription is still inactive.',
    'categoryId': 1, 'categoryName': 'Billing', 'priority': 'MEDIUM', 'userId': 1, 'status': 'OPEN'
}}

ARTICLES_BILLING = [
    {'id': 1, 'categoryId': 1, 'title': 'Billing Issues',
     'content': 'To resolve billing issues, check payment method and reactivate subscription from account settings.'},
    {'id': 2, 'categoryId': 1, 'title': 'Subscription Reactivation',
     'content': 'If payment was deducted but subscription inactive, go to Settings > Billing > Reactivate.'},
]

TICKET_HARDWARE = {'ticket': {
    'id': 2, 'subject': 'Question about hardware warranty',
    'description': 'My laptop screen cracked. Is this covered?',
    'categoryId': None, 'categoryName': None, 'priority': 'LOW', 'userId': 2, 'status': 'OPEN'
}}

ARTICLES_EMPTY = []

TICKET_SECURITY = {'ticket': {
    'id': 3, 'subject': 'Unauthorized access to my account',
    'description': 'Someone compromised my account and made unauthorized purchases.',
    'categoryId': 5, 'categoryName': 'Security', 'priority': 'HIGH', 'userId': 1, 'status': 'OPEN'
}}

ARTICLES_SECURITY = [
    {'id': 5, 'categoryId': 5, 'title': 'Account Security',
     'content': 'If your account is compromised, immediately reset password and contact support.'},
]

# ── Run all tests ─────────────────────────────────────────────────────────────

if __name__ == '__main__':
    print('=== Unit tests ===')
    test_route_confidence()
    test_route_sensitivity()
    print()

    print('=== Scenario tests ===')
    s1 = run_scenario(
        'Scenario 1 - High confidence billing ticket',
        TICKET_BILLING, ARTICLES_BILLING,
        'RESOLVED',
        ['CLASSIFY_TICKET', 'SEARCH_KNOWLEDGE', 'CHECK_CONFIDENCE',
         'GENERATE_RESPONSE', 'CHECK_SENSITIVITY', 'RESOLVE'],
    )
    s2 = run_scenario(
        'Scenario 2 - Low confidence hardware ticket',
        TICKET_HARDWARE, ARTICLES_EMPTY,
        'ESCALATED',
        ['CLASSIFY_TICKET', 'SEARCH_KNOWLEDGE', 'CHECK_CONFIDENCE', 'ESCALATE'],
    )
    s3 = run_scenario(
        'Scenario 3 - High confidence sensitive security ticket',
        TICKET_SECURITY, ARTICLES_SECURITY,
        'ESCALATED',
        ['CLASSIFY_TICKET', 'SEARCH_KNOWLEDGE', 'CHECK_CONFIDENCE',
         'GENERATE_RESPONSE', 'CHECK_SENSITIVITY', 'ESCALATE'],
    )

    all_pass = s1 and s2 and s3
    print('=== Overall result ===')
    print('ALL SCENARIOS PASS' if all_pass else 'SOME SCENARIOS FAILED')
    sys.exit(0 if all_pass else 1)
