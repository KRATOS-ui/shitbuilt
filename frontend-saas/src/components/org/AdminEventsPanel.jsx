import { useEffect, useMemo, useState } from 'react';
import api, { apiError } from '../../services/api.js';
import { useApp } from '../../context/appContext.js';
import EventEditorModal from '../EventEditorModal.jsx';
import { apiToInput, dateToInput, formatDateTime } from '../../utils/dates.js';
import { describeRecurrence, emptyRecurrence, recurrenceFromEvent, recurrenceFromObject } from '../../utils/recurrence.js';

/** Crear eventos organizacionales (manual o con IA), asignarlos a empleados y ver el calendario general. */
export default function AdminEventsPanel({ orgId }) {
    const { showToast } = useApp();
    const [events, setEvents] = useState(null);
    const [reloadKey, setReloadKey] = useState(0);
    const [promptText, setPromptText] = useState('');
    const [processing, setProcessing] = useState(false);
    const [editor, setEditor] = useState(null);

    useEffect(() => {
        let cancelled = false;
        api.get(`/organizations/${orgId}/events`)
            .then((res) => { if (!cancelled) setEvents(res.data.data || []); })
            .catch((err) => { if (!cancelled) { showToast(apiError(err, 'No se pudo cargar el calendario de la organización'), 'error'); setEvents([]); } });
        return () => { cancelled = true; };
    }, [orgId, reloadKey, showToast]);

    const reload = () => setReloadKey((k) => k + 1);

    const sorted = useMemo(
        () => [...(events || [])].sort((a, b) => String(a.startTime).localeCompare(String(b.startTime))),
        [events]
    );
    const conflictCount = useMemo(
        () => (events || []).reduce((n, ev) => n + (ev.assignees || []).filter((a) => a.hasConflict).length, 0),
        [events]
    );

    const newManual = () => {
        const start = new Date();
        start.setHours(start.getHours() + 1, 0, 0, 0);
        const end = new Date(start);
        end.setHours(end.getHours() + 1);
        setEditor({ mode: 'create', initial: { title: '', description: '', startTime: dateToInput(start), endTime: dateToInput(end), recurrence: emptyRecurrence() } });
    };

    const fromPrompt = async () => {
        if (!promptText.trim()) { showToast('Describe el evento primero', 'warning'); return; }
        setProcessing(true);
        try {
            const res = await api.post('/events/preview', { prompt: promptText }, { timeout: 15000 });
            const d = res.data.data;
            setEditor({
                mode: 'create', isDraft: true, missingFields: d.missingFields || [],
                initial: { title: d.title || '', description: d.description || '', startTime: apiToInput(d.startTime), endTime: apiToInput(d.endTime), recurrence: recurrenceFromObject(d.recurrence) },
            });
        } catch (err) {
            showToast(err?.response?.status === 502 || err?.code === 'ECONNABORTED'
                ? 'El asistente de IA no está disponible o tardó demasiado. Inténtalo de nuevo o crea el evento manualmente.'
                : apiError(err, 'No se pudo procesar el texto'), 'error');
        } finally {
            setProcessing(false);
        }
    };

    const edit = (ev) => setEditor({
        mode: 'edit', eventId: ev.id,
        initial: { title: ev.title, description: ev.description || '', startTime: apiToInput(ev.startTime), endTime: apiToInput(ev.endTime), recurrence: recurrenceFromEvent(ev) },
    });

    const remove = async (ev) => {
        if (!window.confirm(`¿Eliminar «${ev.title}» para todos los asignados? Se les notificará la cancelación.`)) return;
        try {
            await api.delete(`/events/${ev.id}`);
            showToast('Evento eliminado');
            reload();
        } catch (err) {
            showToast(apiError(err, 'No se pudo eliminar el evento'), 'error');
        }
    };

    return (
        <div className="stack">
            <div className="card">
                <h4>Nuevo evento para el equipo</h4>
                <p className="muted">Redáctalo en lenguaje natural (la IA propone un borrador que tú confirmas) o créalo manualmente, define su recurrencia y elige a quién asignarlo.</p>
                <textarea className="field-input" rows={3} placeholder="Ej: Reunión semanal de ventas todos los lunes y miércoles a las 9:00 durante 1 hora..." value={promptText} onChange={(e) => setPromptText(e.target.value)} />
                <div className="button-row">
                    <button className="primary-btn" onClick={fromPrompt} disabled={processing}>{processing ? 'Procesando...' : '🤖 Autocompletar con IA'}</button>
                    <button className="secondary-btn" onClick={newManual}>＋ Crear manualmente</button>
                </div>
            </div>

            <div className="card">
                <div className="card-title-row">
                    <h4>Calendario general de la organización</h4>
                    {conflictCount > 0 && <span className="badge badge-warn">⚠ {conflictCount} asignación(es) con conflicto</span>}
                </div>
                {events === null ? <p className="muted">Cargando…</p> : sorted.length === 0 ? <p className="muted">Todavía no hay eventos asignados en esta organización.</p> : (
                    <div className="table-wrap">
                        <table className="table">
                            <thead><tr><th>Cuándo</th><th>Evento</th><th>Repetición</th><th>Asignados</th><th></th></tr></thead>
                            <tbody>
                                {sorted.map((ev) => (
                                    <tr key={ev.id}>
                                        <td className="nowrap">{formatDateTime(ev.startTime)}<br /><span className="muted">hasta {new Date(ev.endTime).toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' })}</span></td>
                                        <td><strong>{ev.title}</strong>{ev.description && <><br /><span className="muted">{ev.description}</span></>}<br /><span className="muted">Creado por {ev.createdByName}</span></td>
                                        <td>{describeRecurrence(ev)}</td>
                                        <td>
                                            <div className="chips">
                                                {(ev.assignees || []).map((a) => (
                                                    <span key={a.userId} className={`chip ${a.hasConflict ? 'chip-warn' : ''}`} title={a.hasConflict ? `${a.email}: se solapa con otro compromiso` : a.email}>
                                                        {a.hasConflict ? '⚠ ' : ''}{a.name}
                                                    </span>
                                                ))}
                                            </div>
                                        </td>
                                        <td className="nowrap">
                                            {ev.owner && (
                                                <>
                                                    <button type="button" className="icon-action-btn" title="Editar" onClick={() => edit(ev)}>✏️</button>
                                                    <button type="button" className="icon-action-btn text-danger" title="Eliminar" onClick={() => remove(ev)}>🗑️</button>
                                                </>
                                            )}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>

            {editor && (
                <EventEditorModal
                    key={editor.eventId || (editor.isDraft ? 'draft' : 'new')}
                    mode={editor.mode}
                    eventId={editor.eventId}
                    initial={editor.initial}
                    missingFields={editor.missingFields}
                    isDraft={editor.isDraft}
                    fixedOrgId={editor.mode === 'edit' ? null : orgId}
                    onClose={() => setEditor(null)}
                    onRetry={editor.isDraft ? () => setEditor(null) : undefined}
                    onSaved={() => { reload(); if (editor.isDraft) setPromptText(''); }}
                />
            )}
        </div>
    );
}
