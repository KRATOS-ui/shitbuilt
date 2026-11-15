import { useState } from 'react';
import api, { apiError } from '../services/api.js';
import { useApp } from '../context/appContext.js';
import EventFields from './EventFields.jsx';
import EmployeePicker from './EmployeePicker.jsx';
import { dateToInput, inputToApi } from '../utils/dates.js';
import { recurrenceToPayload, emptyRecurrence } from '../utils/recurrence.js';

/**
 * Crear / editar un evento. Si viene de la IA (isDraft) el evento NO se guarda hasta pulsar
 * "Confirmar y guardar" (regla de negocio del CU-03).
 *
 * Props: mode('create'|'edit'), eventId, initial, missingFields, isDraft, adminOrgs, fixedOrgId,
 *        onClose, onSaved, onRetry
 */
export default function EventEditorModal({
    mode = 'create', eventId, initial, missingFields = [], isDraft = false,
    adminOrgs = [], fixedOrgId = null, onClose, onSaved, onRetry,
}) {
    const { showToast } = useApp();
    const [values, setValues] = useState({
        title: initial?.title || '',
        description: initial?.description || '',
        startTime: initial?.startTime || '',
        endTime: initial?.endTime || '',
        recurrence: initial?.recurrence || emptyRecurrence(),
    });
    const [targetOrg, setTargetOrg] = useState(fixedOrgId || '');
    const [selectedUsers, setSelectedUsers] = useState([]);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    const [result, setResult] = useState(null);

    const patch = (p) => setValues((v) => ({ ...v, ...p }));
    const isEdit = mode === 'edit';
    const orgMode = !isEdit && Boolean(targetOrg);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');

        if (new Date(values.endTime) <= new Date(values.startTime)) {
            setError('La fecha de fin debe ser posterior a la de inicio.');
            return;
        }
        if (!isEdit && new Date(values.startTime) < new Date()) {
            setError('La hora de inicio ya ha pasado.');
            return;
        }
        if (orgMode && selectedUsers.length === 0) {
            setError('Selecciona al menos un empleado para asignar el evento.');
            return;
        }

        const payload = {
            title: values.title.trim(),
            description: values.description,
            startTime: inputToApi(values.startTime),
            endTime: inputToApi(values.endTime),
            recurrence: recurrenceToPayload(values.recurrence),
        };

        setSaving(true);
        try {
            if (isEdit) {
                await api.put(`/events/${eventId}`, payload);
                showToast('Evento actualizado correctamente');
                onSaved?.();
                onClose();
            } else if (orgMode) {
                const res = await api.post(`/organizations/${targetOrg}/events`, { ...payload, userIds: selectedUsers });
                const data = res.data.data;
                const conflicts = (data.assignments || []).filter((a) => a.success && a.hasConflict).length;
                showToast(conflicts ? `Evento asignado; ${conflicts} empleado(s) con conflicto de horario` : 'Evento asignado con éxito', conflicts ? 'warning' : 'success');
                setResult(data);
                onSaved?.();
            } else {
                const res = await api.post('/events', payload);
                showToast(res.data.data?.hasConflict ? 'Evento agendado, pero existe un solapamiento' : 'Evento agendado con éxito', res.data.data?.hasConflict ? 'warning' : 'success');
                onSaved?.();
                onClose();
            }
        } catch (err) {
            setError(apiError(err, 'Hubo un error al guardar el evento'));
        } finally {
            setSaving(false);
        }
    };

    // Resultado de una asignación organizacional (por empleado)
    if (result) {
        return (
            <div className="modal-overlay">
                <div className="modal-container">
                    <div className="modal-header">
                        <h3 className="modal-title">Resultado de la asignación</h3>
                        <button onClick={onClose} className="close-btn">✕</button>
                    </div>
                    <p className="muted">«{result.event?.title}» se creó y se asignó así:</p>
                    <ul className="result-list">
                        {(result.assignments || []).map((a) => (
                            <li key={a.userId} className={!a.success ? 'res-error' : a.hasConflict ? 'res-warn' : 'res-ok'}>
                                <strong>{a.userEmail || a.userId}</strong>
                                <span>
                                    {!a.success ? `✗ ${a.error || 'No se pudo asignar'}` : a.hasConflict ? '⚠ Asignado, pero se solapa con otro compromiso' : '✓ Asignado'}
                                </span>
                            </li>
                        ))}
                    </ul>
                    <div className="modal-actions">
                        <button type="button" className="confirm-btn" onClick={onClose}>Cerrar</button>
                    </div>
                </div>
            </div>
        );
    }

    const title = isEdit ? 'Modificar Evento' : isDraft ? 'Borrador generado por IA' : (fixedOrgId ? 'Nuevo evento organizacional' : 'Agendar Nueva Reunión');

    return (
        <div className="modal-overlay">
            <div className="modal-container">
                <div className="modal-header">
                    <h3 className="modal-title">{title}</h3>
                    <button onClick={onClose} className="close-btn">✕</button>
                </div>

                {isDraft && (
                    <div className="ai-banner">
                        🤖 Revisa y corrige los datos interpretados. <strong>Nada se guarda hasta que confirmes.</strong>
                        {missingFields.length > 0 && (
                            <div style={{ marginTop: '0.4rem' }}>Faltan por completar: <strong>{missingFields.map((f) => ({ title: 'título', startTime: 'inicio', endTime: 'fin' }[f] || f)).join(', ')}</strong>.</div>
                        )}
                    </div>
                )}

                <form onSubmit={handleSubmit} className="modal-form">
                    <EventFields values={values} onChange={patch} missing={missingFields} minStart={!isEdit ? dateToInput(new Date()) : undefined} />

                    {!isEdit && !fixedOrgId && adminOrgs.length > 0 && (
                        <div className="form-group">
                            <label className="modal-label">Guardar como</label>
                            <select className="modal-input" value={targetOrg} onChange={(e) => { setTargetOrg(e.target.value); setSelectedUsers([]); }}>
                                <option value="">Evento personal</option>
                                {adminOrgs.map((o) => (
                                    <option key={o.organizationId} value={o.organizationId}>Organización: {o.organizationName} (asignar a empleados)</option>
                                ))}
                            </select>
                        </div>
                    )}

                    {orgMode && <EmployeePicker key={targetOrg} orgId={targetOrg} selected={selectedUsers} onChange={setSelectedUsers} />}

                    {error && <div className="conflict-alert">{error}</div>}

                    <div className="modal-actions-spaced" style={{ justifyContent: 'flex-end' }}>
                        <div className="action-buttons-group">
                            {isDraft && onRetry && (
                                <button type="button" onClick={onRetry} className="cancel-btn">Reintentar</button>
                            )}
                            <button type="button" onClick={onClose} className="cancel-btn">{isDraft ? 'Descartar' : 'Cancelar'}</button>
                            <button type="submit" className="confirm-btn" disabled={saving}>
                                {saving ? 'Guardando...' : isEdit ? 'Actualizar Reunión' : isDraft ? 'Confirmar y guardar' : orgMode ? 'Crear y asignar' : 'Programar Reunión'}
                            </button>
                        </div>
                    </div>
                </form>
            </div>
        </div>
    );
}
