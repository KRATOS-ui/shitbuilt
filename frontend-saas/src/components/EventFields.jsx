import RecurrenceFields from './RecurrenceFields.jsx';

/** Campos comunes de un evento. `missing` resalta los campos que la IA no pudo extraer. */
export default function EventFields({ values, onChange, missing = [], minStart }) {
    const isMissing = (field) => missing.includes(field) && !values[field];
    const cls = (field) => `modal-input ${isMissing(field) ? 'field-missing' : ''}`;

    return (
        <>
            <div className="form-group">
                <label className="modal-label">Asunto / Título {isMissing('title') && <em className="missing-tag">falta</em>}</label>
                <input type="text" value={values.title} onChange={(e) => onChange({ title: e.target.value })} className={cls('title')} required />
            </div>
            <div className="form-group">
                <label className="modal-label">Orden del Día (Opcional)</label>
                <textarea
                    value={values.description}
                    onChange={(e) => onChange({ description: e.target.value })}
                    className="modal-input"
                    style={{ minHeight: '60px', resize: 'vertical' }}
                    placeholder="Temas a tratar..."
                />
            </div>
            <div className="form-row">
                <div className="form-group">
                    <label className="modal-label">Inicio {isMissing('startTime') && <em className="missing-tag">falta</em>}</label>
                    <input type="datetime-local" value={values.startTime} onChange={(e) => onChange({ startTime: e.target.value })} className={cls('startTime')} min={minStart} required />
                </div>
                <div className="form-group">
                    <label className="modal-label">Fin {isMissing('endTime') && <em className="missing-tag">falta</em>}</label>
                    <input type="datetime-local" value={values.endTime} onChange={(e) => onChange({ endTime: e.target.value })} className={cls('endTime')} min={values.startTime || minStart} required />
                </div>
            </div>
            <RecurrenceFields value={values.recurrence} onChange={(recurrence) => onChange({ recurrence })} />
        </>
    );
}
