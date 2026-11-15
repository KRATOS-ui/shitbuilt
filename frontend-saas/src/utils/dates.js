const pad = (n) => String(n).padStart(2, '0');

/** Date -> 'YYYY-MM-DDTHH:mm' (valor de <input type="datetime-local">) */
export function dateToInput(date) {
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** 'YYYY-MM-DDTHH:mm:ss(.fff)' del backend -> 'YYYY-MM-DDTHH:mm' */
export function apiToInput(value) {
    return value ? String(value).slice(0, 16) : '';
}

/** Valor del input -> LocalDateTime ISO con segundos */
export function inputToApi(value) {
    if (!value) return value;
    return value.length === 16 ? `${value}:00` : value;
}

/** Date (hora local) -> 'YYYY-MM-DDTHH:mm:ss' para los parámetros from/to */
export function dateToApi(date) {
    return `${dateToInput(date)}:${pad(date.getSeconds())}`;
}

export function formatDisplayDate(start, end) {
    if (!start || !end) return '';
    const dateStr = start.toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'long' });
    const timeOpts = { hour: '2-digit', minute: '2-digit' };
    return `${dateStr.charAt(0).toUpperCase() + dateStr.slice(1)} ⋅ ${start.toLocaleTimeString('es-ES', timeOpts)} - ${end.toLocaleTimeString('es-ES', timeOpts)}`;
}

export function formatDateTime(value) {
    if (!value) return '—';
    return new Date(value).toLocaleString('es-ES', { dateStyle: 'medium', timeStyle: 'short' });
}

export function formatDate(value) {
    if (!value) return '—';
    // LocalDate 'YYYY-MM-DD' se interpreta como fecha local (sin desfase de zona)
    const d = /^\d{4}-\d{2}-\d{2}$/.test(String(value)) ? new Date(`${value}T00:00:00`) : new Date(value);
    return d.toLocaleDateString('es-ES', { day: 'numeric', month: 'long', year: 'numeric' });
}

/** Rango inicial del calendario: mes actual ± 1 semana */
export function initialRange(base = new Date()) {
    const start = new Date(base.getFullYear(), base.getMonth(), 1, 0, 0, 0);
    start.setDate(start.getDate() - 7);
    const end = new Date(base.getFullYear(), base.getMonth() + 1, 0, 23, 59, 59);
    end.setDate(end.getDate() + 7);
    return { start, end };
}

/** Normaliza lo que entrega onRangeChange de react-big-calendar (objeto o arreglo de fechas) */
export function normalizeRange(range) {
    if (Array.isArray(range)) {
        const start = new Date(range[0]);
        start.setHours(0, 0, 0, 0);
        const end = new Date(range[range.length - 1]);
        end.setHours(23, 59, 59, 0);
        return { start, end };
    }
    return { start: new Date(range.start), end: new Date(range.end) };
}
