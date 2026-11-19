-- Borra SOLO los datos demo creados por seed_masivo.sql (Demo Org N / seed.N@example.com).
BEGIN;
CREATE TEMP TABLE d_orgs ON COMMIT DROP AS SELECT id FROM organizations WHERE name LIKE 'Demo Org %';
CREATE TEMP TABLE d_events ON COMMIT DROP AS SELECT id FROM events WHERE organization_id IN (SELECT id FROM d_orgs);
DELETE FROM timeline_entries  WHERE event_id IN (SELECT id FROM d_events);
DELETE FROM checklist_items   WHERE event_id IN (SELECT id FROM d_events);
DELETE FROM event_assignments WHERE event_id IN (SELECT id FROM d_events);
DELETE FROM recurrence_rules  WHERE event_id IN (SELECT id FROM d_events);
DELETE FROM events            WHERE id IN (SELECT id FROM d_events);
DELETE FROM invite_codes      WHERE organization_id IN (SELECT id FROM d_orgs);
DELETE FROM memberships       WHERE organization_id IN (SELECT id FROM d_orgs);
DELETE FROM users             WHERE email LIKE 'seed.%@example.com';
DELETE FROM organizations     WHERE id IN (SELECT id FROM d_orgs);
COMMIT;
