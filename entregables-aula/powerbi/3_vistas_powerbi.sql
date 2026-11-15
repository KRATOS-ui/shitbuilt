-- Vistas para el tablero de Power BI de SyncFlow (PostgreSQL).
-- La recurrencia se une por recurrence_rules.event_id (asi la guarda la app).

CREATE OR REPLACE VIEW vw_eventos AS
SELECT e.id,
       e.title                                   AS titulo,
       e.start_time                              AS inicio,
       e.end_time                                AS fin,
       ROUND(EXTRACT(EPOCH FROM (e.end_time - e.start_time)) / 3600.0, 2) AS duracion_horas,
       e.created_at                              AS creado_en,
       u.name                                    AS creado_por,
       COALESCE(o.name, 'Personal')              AS organizacion,
       CASE WHEN e.organization_id IS NULL THEN 'Personal' ELSE 'Organizacional' END AS tipo_evento,
       COALESCE(r.frequency, 'NONE')             AS recurrencia,
       r.interval_count                          AS intervalo_recurrencia
FROM events e
JOIN users u                 ON u.id = e.created_by
LEFT JOIN organizations o    ON o.id = e.organization_id
LEFT JOIN recurrence_rules r ON r.event_id = e.id;

CREATE OR REPLACE VIEW vw_asignaciones AS
SELECT a.id,
       a.event_id,
       ev.title        AS evento,
       u.name          AS empleado,
       a.has_conflict  AS tiene_conflicto,
       a.created_at    AS creado_en
FROM event_assignments a
JOIN events ev ON ev.id = a.event_id
JOIN users u   ON u.id = a.user_id;

CREATE OR REPLACE VIEW vw_membresias AS
SELECT m.id,
       u.name  AS usuario,
       o.name  AS organizacion,
       o.plan  AS plan_organizacion,
       m.role  AS rol,
       m.joined_at AS ingreso,
       m.left_at   AS salida,
       (m.left_at IS NULL) AS activa
FROM memberships m
JOIN users u         ON u.id = m.user_id
JOIN organizations o ON o.id = m.organization_id;

CREATE OR REPLACE VIEW vw_checklist AS
SELECT c.id,
       c.event_id,
       ev.title AS evento,
       c.description  AS tarea,
       c.is_completed AS completada,
       c.completed_at AS completada_en
FROM checklist_items c
JOIN events ev ON ev.id = c.event_id;

CREATE OR REPLACE VIEW vw_usuarios AS   -- sin email ni password_hash (datos sensibles)
SELECT id,
       name AS nombre,
       subscription_status    AS estado_suscripcion,
       subscription_end_date  AS fin_suscripcion,
       paused_days_remaining  AS dias_pausados,
       created_at             AS registrado_en
FROM users;
