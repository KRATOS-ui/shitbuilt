import { useCallback, useEffect, useMemo, useState } from 'react';
import { Calendar, dateFnsLocalizer } from 'react-big-calendar';
import format from 'date-fns/format';
import parse from 'date-fns/parse';
import startOfWeek from 'date-fns/startOfWeek';
import getDay from 'date-fns/getDay';
import es from 'date-fns/locale/es';
import api, { apiError } from '../services/api';
import { useApp } from '../context/appContext.js';
import EventEditorModal from '../components/EventEditorModal.jsx';
import EventDetailModal from '../components/EventDetailModal.jsx';
import TimelineModal from '../components/TimelineModal.jsx';
import { dateToApi, dateToInput, apiToInput, initialRange, normalizeRange } from '../utils/dates.js';
import { recurrenceFromEvent, recurrenceFromObject, emptyRecurrence } from '../utils/recurrence.js';

import 'react-big-calendar/lib/css/react-big-calendar.css';

const locales = { es };
const localizer = dateFnsLocalizer({ format, parse, startOfWeek, getDay, locales });

const mapOccurrence = (o) => ({
    ...o,
    id: `${o.eventId}-${o.occurrenceStart}`,
    baseId: o.eventId,
    start: new Date(o.occurrenceStart),
    end: new Date(o.occurrenceEnd),
});

const CalendarEvent = ({ event }) => (
    <span>{event.hasConflict ? '⚠ ' : ''}{event.title}</span>
);

export default function Dashboard() {
    const { memberships, showToast } = useApp();
    const adminOrgs = useMemo(() => memberships.filter((m) => m.role === 'ADMIN'), [memberships]);

    const [currentDate, setCurrentDate] = useState(new Date());
    const [currentView, setCurrentView] = useState('month');
    const [range, setRange] = useState(() => initialRange());
    const [events, setEvents] = useState([]);
    const [refreshKey, setRefreshKey] = useState(0);
    const reload = useCallback(() => setRefreshKey((k) => k + 1), []);

    const [promptText, setPromptText] = useState('');
    const [isProcessingPrompt, setIsProcessingPrompt] = useState(false);

    const [selected, setSelected] = useState(null);
    const [showDetail, setShowDetail] = useState(false);
    const [showTimeline, setShowTimeline] = useState(false);
    const [editor, setEditor] = useState(null); // { mode, eventId, initial, missingFields, isDraft }

    useEffect(() => {
        let cancelled = false;
        api.get('/events/occurrences', { params: { from: dateToApi(range.start), to: dateToApi(range.end) } })
            .then((res) => { if (!cancelled) setEvents((res.data.data || []).map(mapOccurrence)); })
            .catch((err) => { if (!cancelled) showToast(apiError(err, 'Error al cargar los eventos'), 'error'); });
        return () => { cancelled = true; };
    }, [range, refreshKey, showToast]);

    const handleSelectSlot = ({ start }) => {
        if (start < new Date(new Date().setHours(0, 0, 0, 0))) {
            showToast('No puedes agendar eventos en fechas pasadas', 'warning');
            return;
        }
        const from = start < new Date() ? new Date() : start;
        const end = new Date(from);
        end.setHours(end.getHours() + 1);
        setEditor({
            mode: 'create',
            initial: { title: '', description: '', startTime: dateToInput(from), endTime: dateToInput(end), recurrence: emptyRecurrence() },
        });
    };

    const handleSelectEvent = (event) => {
        setSelected(event);
        setShowDetail(true);
    };

    const handleEdit = () => {
        setShowDetail(false);
        setEditor({
            mode: 'edit',
            eventId: selected.baseId,
            initial: {
                title: selected.title,
                description: selected.description || '',
                // Se edita el evento base (no la ocurrencia)
                startTime: apiToInput(selected.startTime),
                endTime: apiToInput(selected.endTime),
                recurrence: recurrenceFromEvent(selected),
            },
        });
    };

    const handleDelete = async () => {
        const msg = selected.owner
            ? '¿Eliminar este evento para todos los asignados? Se les notificará la cancelación.'
            : '¿Quitar este evento de tu calendario?';
        if (!window.confirm(msg)) return;
        try {
            await api.delete(`/events/${selected.baseId}`);
            showToast(selected.owner ? 'Evento eliminado' : 'Evento quitado de tu calendario');
            setShowDetail(false);
            reload();
        } catch (err) {
            showToast(apiError(err, 'No se pudo eliminar el evento'), 'error');
        }
    };

    const handlePromptSubmit = async () => {
        if (!promptText.trim()) {
            showToast('Escribe los detalles de la reunión primero', 'warning');
            return;
        }
        setIsProcessingPrompt(true);
        try {
            const res = await api.post('/events/preview', { prompt: promptText }, { timeout: 15000 });
            const draft = res.data.data;
            setEditor({
                mode: 'create',
                isDraft: true,
                missingFields: draft.missingFields || [],
                initial: {
                    title: draft.title || '',
                    description: draft.description || '',
                    startTime: apiToInput(draft.startTime),
                    endTime: apiToInput(draft.endTime),
                    recurrence: recurrenceFromObject(draft.recurrence),
                },
            });
        } catch (err) {
            const status = err?.response?.status;
            if (status === 502 || err?.code === 'ECONNABORTED') {
                showToast('El asistente de IA no está disponible o tardó demasiado. Inténtalo de nuevo o crea el evento manualmente.', 'error');
            } else {
                showToast(apiError(err, 'No se pudo procesar el texto'), 'error');
            }
        } finally {
            setIsProcessingPrompt(false);
        }
    };

    const eventPropGetter = (event) => ({
        className: event.hasConflict ? 'calendar-event-conflict' : 'calendar-event-normal',
    });

    return (
        <>
            <section className="prompt-section">
                <div className="section-header">
                    <h3 className="section-title">Asistente de Agendamiento IA</h3>
                </div>
                <div className="prompt-box">
                    <textarea
                        placeholder="Ej: Programa una revisión trimestral con el equipo el viernes a las 10:00 AM, cada mes..."
                        value={promptText}
                        onChange={(e) => setPromptText(e.target.value)}
                        className="prompt-textarea"
                    />
                    <div className="prompt-actions">
                        <span>La IA propone un borrador; tú lo confirmas antes de guardarlo.</span>
                        <button className="primary-btn" onClick={handlePromptSubmit} disabled={isProcessingPrompt}>
                            {isProcessingPrompt ? 'Procesando...' : 'Autocompletar Evento'}
                        </button>
                    </div>
                </div>
            </section>

            <section className="calendar-section">
                <div className="section-header">
                    <h3 className="section-title">Mi calendario</h3>
                    <span className="legend"><i className="dot dot-normal"></i> Normal <i className="dot dot-conflict"></i> Con solapamiento</span>
                </div>
                <div className="calendar-wrapper">
                    <Calendar
                        localizer={localizer}
                        events={events}
                        startAccessor="start"
                        endAccessor="end"
                        culture="es"
                        selectable
                        onSelectSlot={handleSelectSlot}
                        onSelectEvent={handleSelectEvent}
                        eventPropGetter={eventPropGetter}
                        components={{ event: CalendarEvent }}
                        date={currentDate}
                        onNavigate={setCurrentDate}
                        view={currentView}
                        onView={setCurrentView}
                        onRangeChange={(r) => setRange(normalizeRange(r))}
                        style={{ height: 600 }}
                        messages={{
                            next: 'Sig', previous: 'Ant', today: 'Hoy', month: 'Mes',
                            week: 'Semana', day: 'Día', agenda: 'Agenda',
                            noEventsInRange: 'No hay eventos en este rango.',
                        }}
                    />
                </div>
            </section>

            {showDetail && selected && (
                <EventDetailModal
                    event={selected}
                    onClose={() => setShowDetail(false)}
                    onEdit={handleEdit}
                    onDelete={handleDelete}
                    onOpenTimeline={() => { setShowDetail(false); setShowTimeline(true); }}
                />
            )}

            {showTimeline && selected && <TimelineModal event={selected} onClose={() => setShowTimeline(false)} />}

            {editor && (
                <EventEditorModal
                    key={editor.eventId || (editor.isDraft ? 'draft' : 'new')}
                    mode={editor.mode}
                    eventId={editor.eventId}
                    initial={editor.initial}
                    missingFields={editor.missingFields}
                    isDraft={editor.isDraft}
                    adminOrgs={adminOrgs}
                    onClose={() => setEditor(null)}
                    onRetry={editor.isDraft ? () => setEditor(null) : undefined}
                    onSaved={() => { reload(); if (editor.isDraft) setPromptText(''); }}
                />
            )}
        </>
    );
}
