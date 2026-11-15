import { useEffect, useState } from 'react';
import api, { apiError } from '../services/api.js';

/** Lista de empleados (miembros EMPLOYEE activos) con selección múltiple. Usar con key={orgId}. */
export default function EmployeePicker({ orgId, selected, onChange }) {
    const [members, setMembers] = useState(null);
    const [error, setError] = useState('');

    useEffect(() => {
        let cancelled = false;
        api.get(`/organizations/${orgId}/members`)
            .then((res) => { if (!cancelled) setMembers((res.data.data || []).filter((m) => m.role === 'EMPLOYEE')); })
            .catch((err) => { if (!cancelled) { setError(apiError(err, 'No se pudo cargar el equipo')); setMembers([]); } });
        return () => { cancelled = true; };
    }, [orgId]);

    const toggle = (id) => onChange(selected.includes(id) ? selected.filter((x) => x !== id) : [...selected, id]);

    if (members === null) return <p className="muted">Cargando empleados…</p>;
    if (error) return <div className="conflict-alert">{error}</div>;
    if (members.length === 0) {
        return <p className="muted">Esta organización aún no tiene empleados. Comparte un código de invitación desde la pestaña «Equipo».</p>;
    }

    const allSelected = selected.length === members.length;
    return (
        <div className="form-group">
            <div className="picker-header">
                <label className="modal-label">Asignar a empleados ({selected.length} seleccionados)</label>
                <button type="button" className="link-btn" onClick={() => onChange(allSelected ? [] : members.map((m) => m.userId))}>
                    {allSelected ? 'Quitar todos' : 'Seleccionar todos'}
                </button>
            </div>
            <div className="picker-list">
                {members.map((m) => (
                    <label key={m.userId} className="picker-item">
                        <input type="checkbox" checked={selected.includes(m.userId)} onChange={() => toggle(m.userId)} />
                        <span><strong>{m.name}</strong> <span className="muted">{m.email}</span></span>
                    </label>
                ))}
            </div>
        </div>
    );
}
