export const WEEKDAYS = [
    { key: 'MONDAY', short: 'L', label: 'Lun' },
    { key: 'TUESDAY', short: 'M', label: 'Mar' },
    { key: 'WEDNESDAY', short: 'X', label: 'Mié' },
    { key: 'THURSDAY', short: 'J', label: 'Jue' },
    { key: 'FRIDAY', short: 'V', label: 'Vie' },
    { key: 'SATURDAY', short: 'S', label: 'Sáb' },
    { key: 'SUNDAY', short: 'D', label: 'Dom' },
];

export const FREQUENCY_UNITS = {
    DAILY: { one: 'día', many: 'días' },
    WEEKLY: { one: 'semana', many: 'semanas' },
    MONTHLY: { one: 'mes', many: 'meses' },
};

export const emptyRecurrence = () => ({ mode: 'NONE', interval: 1, days: [], until: '' });

/** Objeto del contrato {frequency,intervalCount,daysOfWeek,untilDate} (o null) -> estado del formulario */
export function recurrenceFromObject(rec) {
    if (!rec || !rec.frequency || !FREQUENCY_UNITS[rec.frequency]) return emptyRecurrence();
    return {
        mode: rec.frequency,
        interval: rec.intervalCount || 1,
        days: rec.daysOfWeek || [],
        until: rec.untilDate || '',
    };
}

/** EventResponse (campos planos recurrenceFrequency...) -> estado del formulario */
export function recurrenceFromEvent(ev) {
    return recurrenceFromObject(
        ev && ev.recurrenceFrequency
            ? {
                frequency: ev.recurrenceFrequency,
                intervalCount: ev.intervalCount,
                daysOfWeek: ev.daysOfWeek,
                untilDate: ev.untilDate,
            }
            : null
    );
}

/** Estado del formulario -> RecurrenceRequest del contrato (o null si no repite) */
export function recurrenceToPayload(form) {
    if (!form || form.mode === 'NONE') return null;
    const interval = Math.max(1, parseInt(form.interval, 10) || 1);
    return {
        frequency: form.mode,
        intervalCount: interval,
        daysOfWeek: form.mode === 'WEEKLY' && form.days.length > 0 ? form.days : null,
        untilDate: form.until || null,
    };
}

/** Texto legible: "Cada 2 semanas (Lun, Mié) hasta 31 dic 2026" */
export function describeRecurrence(src) {
    const rec = src && src.mode !== undefined
        ? (src.mode === 'NONE' ? null : { frequency: src.mode, intervalCount: src.interval, daysOfWeek: src.days, untilDate: src.until })
        : (src && src.recurrenceFrequency
            ? { frequency: src.recurrenceFrequency, intervalCount: src.intervalCount, daysOfWeek: src.daysOfWeek, untilDate: src.untilDate }
            : (src && src.frequency ? src : null));
    if (!rec || !FREQUENCY_UNITS[rec.frequency]) return 'No se repite';
    const n = rec.intervalCount || 1;
    const unit = FREQUENCY_UNITS[rec.frequency];
    let text = n === 1
        ? ({ DAILY: 'Todos los días', WEEKLY: 'Cada semana', MONTHLY: 'Cada mes' })[rec.frequency]
        : `Cada ${n} ${unit.many}`;
    if (rec.frequency === 'WEEKLY' && rec.daysOfWeek && rec.daysOfWeek.length) {
        const labels = WEEKDAYS.filter((d) => rec.daysOfWeek.includes(d.key)).map((d) => d.label);
        text += ` (${labels.join(', ')})`;
    }
    if (rec.untilDate) {
        text += ` hasta ${new Date(`${rec.untilDate}T00:00:00`).toLocaleDateString('es-ES', { day: 'numeric', month: 'short', year: 'numeric' })}`;
    }
    return text;
}
