-- Carga masiva de datos de DEMO para SyncFlow (~18.800 registros).
-- Se puede ejecutar con la app encendida. Todo en una transaccion: si algo falla, no queda nada a medias.
-- Los datos van en organizaciones "Demo Org N" y usuarios seed.N@example.com (no tocan tus datos reales).
-- Los usuarios demo NO pueden iniciar sesion (hash de relleno).
BEGIN;

CREATE TEMP TABLE s_orgs ON COMMIT DROP AS
  SELECT g AS n, gen_random_uuid() AS id FROM generate_series(1,20) g;

INSERT INTO organizations(id, name, plan, created_at)
SELECT id, 'Demo Org ' || n, 'FREE_TIER', localtimestamp - make_interval(days => (random()*300)::int)
FROM s_orgs;

CREATE TEMP TABLE s_users ON COMMIT DROP AS
  SELECT g AS n, gen_random_uuid() AS id,
         (ARRAY['ACTIVE','INACTIVE','PAUSED'])[1 + floor(random()*3)::int] AS st
  FROM generate_series(1,1000) g;

INSERT INTO users(id, email, password_hash, name, subscription_status,
                  subscription_end_date, paused_days_remaining, created_at, privacy_accepted_at)
SELECT id, 'seed.' || n || '@example.com',
       '$2a$10$EJEMPLO.hash.de.prueba.no.sirve.para.iniciar.sesion0000000000',
       'Usuario Demo ' || n, st,
       CASE WHEN st = 'ACTIVE' THEN current_date + 30 END,
       CASE WHEN st = 'PAUSED' THEN 15 END,
       localtimestamp - make_interval(days => (random()*300)::int),
       localtimestamp - make_interval(days => (random()*300)::int)
FROM s_users;

-- Usuario n -> organizacion ((n-1)%20)+1. Los usuarios 1..20 son ADMIN (uno por organizacion).
INSERT INTO memberships(id, user_id, organization_id, role, joined_at)
SELECT gen_random_uuid(), u.id, o.id,
       CASE WHEN u.n <= 20 THEN 'ADMIN' ELSE 'EMPLOYEE' END,
       localtimestamp - make_interval(days => (random()*200)::int)
FROM s_users u JOIN s_orgs o ON o.n = ((u.n - 1) % 20) + 1;

CREATE TEMP TABLE s_events ON COMMIT DROP AS
  SELECT g AS n, gen_random_uuid() AS id, ((g - 1) % 20) + 1 AS org_n,
         date_trunc('day', localtimestamp)
           + make_interval(days => (floor(random()*120)::int - 60),
                           hours => (8 + floor(random()*10)::int)) AS st,
         30 * (1 + floor(random()*4)::int) AS mins
  FROM generate_series(1,3000) g;

INSERT INTO events(id, created_by, organization_id, title, description, start_time, end_time, created_at)
SELECT e.id, u.id, o.id,
       (ARRAY['Reunion de planeacion','Comite de calidad','Capacitacion','Revision de avances',
              'Entrevista candidato','Cumpleanos','Retrospectiva','Demo con cliente'])[1 + floor(random()*8)::int]
         || ' #' || e.n,
       'Evento de prueba generado para carga masiva',
       e.st, e.st + make_interval(mins => e.mins), e.st - interval '7 days'
FROM s_events e
JOIN s_users u ON u.n = e.org_n
JOIN s_orgs  o ON o.n = e.org_n;

-- 2 asignados distintos por evento (empleados de la misma organizacion)
INSERT INTO event_assignments(id, event_id, user_id, has_conflict, created_at)
SELECT gen_random_uuid(), e.id, u.id, false, e.st - interval '3 days'
FROM s_events e
CROSS JOIN LATERAL (VALUES (1 + (e.n % 49)), (1 + ((e.n + 7) % 49))) k(kk)
JOIN s_users u ON u.n = e.org_n + 20 * k.kk;

-- 2 tareas de checklist por evento
INSERT INTO checklist_items(id, event_id, description, is_completed, completed_at, created_at)
SELECT gen_random_uuid(), e.id, t.d, c.done,
       CASE WHEN c.done THEN e.st - interval '1 day' END,
       e.st - interval '6 days'
FROM s_events e
CROSS JOIN LATERAL (VALUES ('Preparar materiales'), ('Confirmar asistentes')) t(d)
CROSS JOIN LATERAL (SELECT random() < 0.5 AS done) c;

-- Comentarios en la linea de tiempo (1 de cada 2 eventos), escritos por el admin de la organizacion
INSERT INTO timeline_entries(id, event_id, author_id, entry_type, content, created_at)
SELECT gen_random_uuid(), e.id, u.id, 'COMMENT',
       'Comentario de prueba del evento #' || e.n, e.st - interval '2 days'
FROM s_events e JOIN s_users u ON u.n = e.org_n
WHERE e.n % 2 = 0;

-- Reglas de recurrencia (1 de cada 10 eventos)
INSERT INTO recurrence_rules(id, event_id, frequency, interval_count, until_date)
SELECT gen_random_uuid(), e.id,
       CASE WHEN e.n % 20 = 0 THEN 'DAILY' ELSE 'WEEKLY' END,
       1, e.st::date + 90
FROM s_events e
WHERE e.n % 10 = 0;

COMMIT;

SELECT 'organizations' AS tabla, count(*) FROM organizations
UNION ALL SELECT 'users', count(*) FROM users
UNION ALL SELECT 'memberships', count(*) FROM memberships
UNION ALL SELECT 'events', count(*) FROM events
UNION ALL SELECT 'event_assignments', count(*) FROM event_assignments
UNION ALL SELECT 'checklist_items', count(*) FROM checklist_items
UNION ALL SELECT 'timeline_entries', count(*) FROM timeline_entries
UNION ALL SELECT 'recurrence_rules', count(*) FROM recurrence_rules;
