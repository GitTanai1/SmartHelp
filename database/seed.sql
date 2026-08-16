USE smarthelp;

INSERT INTO users (name, email, role) VALUES
  ('Asha Mehta', 'asha.mehta@example.com', 'CUSTOMER'),
  ('Rohan Iyer', 'rohan.iyer@example.com', 'CUSTOMER'),
  ('Neha Kapoor', 'neha.kapoor@example.com', 'CUSTOMER'),
  ('Maya Support', 'maya.support@example.com', 'AGENT');

INSERT INTO categories (name) VALUES
  ('Billing'),
  ('Account Access'),
  ('Subscription'),
  ('Refunds'),
  ('Security'),
  ('Technical Support');

INSERT INTO knowledge_articles (category_id, title, content) VALUES
  ((SELECT id FROM categories WHERE name = 'Billing'),
   'Payment deducted but subscription inactive',
   'If a customer was charged but the subscription is inactive, ask them to refresh account status, sign out and sign in again, and wait up to 10 minutes for payment confirmation. If the subscription is still inactive, verify the payment reference and mark the ticket for billing review only when the charge is missing from the billing system.'),
  ((SELECT id FROM categories WHERE name = 'Account Access'),
   'Password reset email not received',
   'For login problems involving missing password reset email, ask the customer to check spam, confirm the email address on the account, wait five minutes, and request a new reset link. If no email arrives after two attempts, escalate to account support.'),
  ((SELECT id FROM categories WHERE name = 'Refunds'),
   'Refund request policy',
   'Refund requests can be processed when the payment was made in the last 7 days and the customer has not used paid features after purchase. Ask for the payment reference and reason for refund. Escalate disputed charges, chargebacks, or legal threats.'),
  ((SELECT id FROM categories WHERE name = 'Subscription'),
   'Cancel or change subscription',
   'Customers can change or cancel subscriptions from Account Settings > Billing > Manage plan. Changes take effect at the next billing cycle unless support applies an immediate plan correction.'),
  ((SELECT id FROM categories WHERE name = 'Security'),
   'Suspected account compromise',
   'If a customer reports unauthorized access, suspicious login, account takeover, stolen credentials, or fraud, do not resolve automatically. Ask the customer to reset the password, enable two-step verification if available, and escalate to a human support agent.'),
  ((SELECT id FROM categories WHERE name = 'Technical Support'),
   'App page not loading',
   'For a page that does not load, ask the customer to refresh the browser, clear cache, try an incognito window, and confirm whether the problem happens on another network. Escalate if multiple users report the same outage.');

INSERT INTO tickets (user_id, category_id, subject, description, status, priority) VALUES
  ((SELECT id FROM users WHERE email = 'asha.mehta@example.com'),
   (SELECT id FROM categories WHERE name = 'Billing'),
   'Payment deducted but subscription inactive',
   'I was charged INR 999 but my subscription is still inactive.',
   'OPEN',
   'MEDIUM'),
  ((SELECT id FROM users WHERE email = 'rohan.iyer@example.com'),
   (SELECT id FROM categories WHERE name = 'Account Access'),
   'Cannot log in',
   'The password reset email never arrives in my inbox.',
   'IN_PROGRESS',
   'MEDIUM'),
  ((SELECT id FROM users WHERE email = 'neha.kapoor@example.com'),
   (SELECT id FROM categories WHERE name = 'Refunds'),
   'Refund request for accidental purchase',
   'I bought the subscription today by mistake and want a refund.',
   'OPEN',
   'LOW'),
  ((SELECT id FROM users WHERE email = 'asha.mehta@example.com'),
   (SELECT id FROM categories WHERE name = 'Security'),
   'Account security concern',
   'I see an unauthorized login and think my account is compromised.',
   'OPEN',
   'HIGH'),
  ((SELECT id FROM users WHERE email = 'rohan.iyer@example.com'),
   (SELECT id FROM categories WHERE name = 'Subscription'),
   'Need to change subscription',
   'I want to move from monthly billing to annual billing.',
   'OPEN',
   'LOW'),
  ((SELECT id FROM users WHERE email = 'neha.kapoor@example.com'),
   NULL,
   'Question about hardware warranty',
   'Can SmartHelp replace the battery in my wireless headphones?',
   'OPEN',
   'LOW');

INSERT INTO ticket_responses (ticket_id, message, sender_type) VALUES
  ((SELECT id FROM tickets WHERE subject = 'Cannot log in' LIMIT 1),
   'Please confirm whether the email address on your account is correct and check the spam folder.',
   'AGENT'),
  ((SELECT id FROM tickets WHERE subject = 'Need to change subscription' LIMIT 1),
   'You can change your plan from Account Settings > Billing > Manage plan.',
   'AI');
