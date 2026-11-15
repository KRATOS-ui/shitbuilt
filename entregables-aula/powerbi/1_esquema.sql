-- Esquema de SyncFlow (igual al que genera Hibernate con ddl-auto=update).
-- Solo es necesario si la base esta vacia y NO se ha iniciado el backend.
-- Si ya levantaste la app con docker compose, las tablas existen y este script no cambia nada.
CREATE TABLE IF NOT EXISTS users (
  id uuid PRIMARY KEY, email varchar(255) NOT NULL UNIQUE, password_hash varchar(255) NOT NULL,
  name varchar(255) NOT NULL, subscription_status varchar(255), subscription_end_date date,
  paused_days_remaining integer, created_at timestamp(6), privacy_accepted_at timestamp(6));
CREATE TABLE IF NOT EXISTS organizations (
  id uuid PRIMARY KEY, name varchar(255) NOT NULL, plan varchar(255), plan_end_date date, created_at timestamp(6));
CREATE TABLE IF NOT EXISTS memberships (
  id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id),
  organization_id uuid NOT NULL REFERENCES organizations(id),
  role varchar(255), joined_at timestamp(6), left_at timestamp(6));
CREATE TABLE IF NOT EXISTS invite_codes (
  id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organizations(id),
  code varchar(255) NOT NULL, max_uses integer NOT NULL, uses_count integer,
  expires_at timestamp(6), created_at timestamp(6));
CREATE TABLE IF NOT EXISTS events (
  id uuid PRIMARY KEY, created_by uuid NOT NULL REFERENCES users(id),
  organization_id uuid REFERENCES organizations(id), recurrence_rule_id uuid,
  title varchar(255) NOT NULL, description text,
  start_time timestamp(6) NOT NULL, end_time timestamp(6) NOT NULL, created_at timestamp(6));
CREATE TABLE IF NOT EXISTS recurrence_rules (
  id uuid PRIMARY KEY, event_id uuid NOT NULL UNIQUE REFERENCES events(id),
  frequency varchar(255) NOT NULL, interval_count integer NOT NULL, days_of_week varchar(255), until_date date);
CREATE TABLE IF NOT EXISTS event_assignments (
  id uuid PRIMARY KEY, event_id uuid NOT NULL REFERENCES events(id),
  user_id uuid NOT NULL REFERENCES users(id), has_conflict boolean, created_at timestamp(6));
CREATE TABLE IF NOT EXISTS checklist_items (
  id uuid PRIMARY KEY, event_id uuid NOT NULL REFERENCES events(id),
  description varchar(255) NOT NULL, is_completed boolean NOT NULL, completed_at timestamp(6), created_at timestamp(6));
CREATE TABLE IF NOT EXISTS timeline_entries (
  id uuid PRIMARY KEY, event_id uuid NOT NULL REFERENCES events(id),
  author_id uuid NOT NULL REFERENCES users(id), entry_type varchar(255) NOT NULL,
  content text NOT NULL, created_at timestamp(6));
