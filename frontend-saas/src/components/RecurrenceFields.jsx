import { WEEKDAYS, FREQUENCY_UNITS } from '../utils/recurrence.js';

export default function RecurrenceFields({ value, onChange }) {
    const patch = (p) => onChange({ ...value, ...p });
    const toggleDay = (key) => {
        const days = value.days.includes(key) ? value.days.filter((d) => d !== key) : [...value.days, key];
        patch({ days });
    };
    const unit = FREQUENCY_UNITS[value.mode];
    const n = parseInt(value.interval, 10) || 1;

    return (
        <div className="recurrence-box">
            <div className="form-row">
                <div className="form-group">
                    <label className="modal-label">Repetir</label>
                    <select value={value.mode} onChange={(e) => patch({ mode: e.target.value })} className="modal-input">
                        <option value="NONE">Sin repetición</option>
                        <option value="DAILY">Diaria</option>
                        <option value="WEEKLY">Semanal</option>
                        <option value="MONTHLY">Mensual</option>
                    </select>
                </div>
                {unit && (
                    <div className="form-group">
                        <label className="modal-label">Cada</label>
                        <div className="inline-field">
                            <input
                                type="number"
                                min="1"
                                max="365"
                                value={value.interval}
                                onChange={(e) => patch({ interval: e.target.value })}
                                className="modal-input"
                                style={{ width: '80px' }}
                            />
                            <span className="modal-sub-label">{n === 1 ? unit.one : unit.many}</span>
                        </div>
                    </div>
                )}
            </div>

            {value.mode === 'WEEKLY' && (
                <div className="form-group">
                    <label className="modal-label">Días de la semana</label>
                    <div className="weekday-row">
                        {WEEKDAYS.map((d) => (
                            <button
                                type="button"
                                key={d.key}
                                title={d.label}
                                className={`weekday-btn ${value.days.includes(d.key) ? 'active' : ''}`}
                                onClick={() => toggleDay(d.key)}
                                aria-pressed={value.days.includes(d.key)}
                            >
                                {d.short}
                            </button>
                        ))}
                    </div>
                    <span className="modal-sub-label">Si no eliges ninguno, se usa el día de la semana del inicio.</span>
                </div>
            )}

            {unit && (
                <div className="form-group">
                    <label className="modal-label">Termina el (opcional)</label>
                    <input
                        type="date"
                        value={value.until}
                        onChange={(e) => patch({ until: e.target.value })}
                        className="modal-input"
                    />
                </div>
            )}
        </div>
    );
}
