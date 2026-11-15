import ChecklistSection from './ChecklistSection.jsx';
import { formatDisplayDate } from '../utils/dates.js';
import { describeRecurrence } from '../utils/recurrence.js';

export default function EventDetailModal({ event, onClose, onEdit, onDelete, onOpenTimeline }) {
    return (
        <div className="modal-overlay">
            <div className="modal-container" style={{ maxWidth: '480px', maxHeight: '92vh', overflowY: 'auto' }}>
                <div className="modal-header">
                    <h3 className="modal-title">Detalles del Evento</h3>
                    <button onClick={onClose} className="close-btn">✕</button>
                </div>

                <div className="view-event-body">
                    <div className="view-event-title-row">
                        <span className="color-indicator" style={{ backgroundColor: event.hasConflict ? '#f59e0b' : '#4f46e5' }}></span>
                        <h2>{event.title}</h2>
                    </div>

                    <div className="view-event-detail">
                        <div className="view-event-text">
                            <strong>Horario</strong>
                            <span>{formatDisplayDate(event.start, event.end)}</span>
                            {event.recurring && <span style={{ marginTop: '0.25rem' }}>🔁 {describeRecurrence(event)}</span>}
                        </div>
                    </div>

                    {(event.organizationName || event.createdByName) && (
                        <div className="view-event-detail">
                            <div className="view-event-text">
                                <strong>{event.organizationName ? 'Organización' : 'Creado por'}</strong>
                                <span>
                                    {event.organizationName ? `${event.organizationName} · ` : ''}
                                    {event.owner ? 'Creado por ti' : `Asignado por ${event.createdByName}`}
                                </span>
                            </div>
                        </div>
                    )}

                    {event.description && (
                        <div className="view-event-detail">
                            <div className="view-event-text">
                                <strong>Descripción</strong>
                                <span>{event.description}</span>
                            </div>
                        </div>
                    )}

                    {event.hasConflict && (
                        <div className="conflict-alert" role="alert">
                            ⚠️ Este evento se solapa con otro compromiso de tu calendario. Puedes resolverlo como prefieras: no se canceló nada.
                        </div>
                    )}

                    <ChecklistSection eventId={event.baseId} />

                    <button type="button" className="primary-btn" style={{ width: '100%', backgroundColor: '#334155' }} onClick={onOpenTimeline}>
                        📂 Centro de Contexto y Evidencias
                    </button>

                    <div className="modal-actions-spaced">
                        <button type="button" onClick={onDelete} className="delete-btn">
                            {event.owner ? 'Eliminar Evento' : 'Quitar de mi calendario'}
                        </button>
                        {event.owner && (
                            <button type="button" onClick={onEdit} className="confirm-btn">Editar Detalles</button>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}
