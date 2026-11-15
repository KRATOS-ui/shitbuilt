import { useCallback, useEffect, useState } from 'react';
import api, { apiError } from '../../services/api.js';
import { useApp } from '../../context/appContext.js';
import { formatDateTime } from '../../utils/dates.js';

/** Miembros activos + códigos de invitación con límite de usos. Usar con key={orgId}. */
export default function AdminTeamPanel({ orgId }) {
    const { showToast } = useApp();
    const [members, setMembers] = useState(null);
    const [codes, setCodes] = useState(null);
    const [maxUses, setMaxUses] = useState(5);
    const [busy, setBusy] = useState(false);
    const [reloadKey, setReloadKey] = useState(0);

    useEffect(() => {
        let cancelled = false;
        Promise.all([api.get(`/organizations/${orgId}/members`), api.get(`/organizations/${orgId}/invite-codes`)])
            .then(([m, c]) => { if (!cancelled) { setMembers(m.data.data || []); setCodes(c.data.data || []); } })
            .catch((err) => { if (!cancelled) { showToast(apiError(err, 'No se pudo cargar el equipo'), 'error'); setMembers([]); setCodes([]); } });
        return () => { cancelled = true; };
    }, [orgId, reloadKey, showToast]);

    const generate = async (e) => {
        e.preventDefault();
        setBusy(true);
        try {
            const res = await api.post(`/organizations/${orgId}/invite-codes`, { maxUses: Math.max(1, parseInt(maxUses, 10) || 1) });
            showToast(`Código generado: ${res.data.data.code}`);
            setReloadKey((k) => k + 1);
        } catch (err) {
            showToast(apiError(err, 'No se pudo generar el código'), 'error');
        } finally {
            setBusy(false);
        }
    };

    const copy = useCallback(async (code) => {
        try {
            await navigator.clipboard.writeText(code);
            showToast('Código copiado al portapapeles');
        } catch {
            showToast(`Código: ${code}`, 'warning');
        }
    }, [showToast]);

    const removeMember = async (member) => {
        if (!window.confirm(`¿Retirar a ${member.name} de la organización? Su suscripción individual pausada se reanudará.`)) return;
        try {
            await api.delete(`/organizations/${orgId}/members/${member.userId}`);
            showToast(`${member.name} fue retirado de la organización`);
            setReloadKey((k) => k + 1);
        } catch (err) {
            showToast(apiError(err, 'No se pudo retirar al empleado'), 'error');
        }
    };

    return (
        <div className="stack">
            <div className="card">
                <h4>Códigos de invitación</h4>
                <p className="muted">Comparte un código con tus empleados. Cada código tiene un límite de usos y vence a los 7 días.</p>
                <form onSubmit={generate} className="inline-field">
                    <label className="field-label" htmlFor="maxUses">Usos máximos</label>
                    <input id="maxUses" type="number" min="1" className="field-input" style={{ width: '100px' }} value={maxUses} onChange={(e) => setMaxUses(e.target.value)} />
                    <button type="submit" className="primary-btn" disabled={busy}>Generar código</button>
                </form>
                {codes === null ? <p className="muted">Cargando…</p> : codes.length === 0 ? <p className="muted">Aún no has generado códigos.</p> : (
                    <div className="table-wrap">
                        <table className="table">
                            <thead><tr><th>Código</th><th>Usos</th><th>Vence</th><th>Estado</th><th></th></tr></thead>
                            <tbody>
                                {codes.map((c) => {
                                    const expired = c.expiresAt && new Date(c.expiresAt) < new Date();
                                    const exhausted = c.usesCount >= c.maxUses;
                                    return (
                                        <tr key={c.id}>
                                            <td><code>{c.code}</code></td>
                                            <td>{c.usesCount} / {c.maxUses}</td>
                                            <td>{formatDateTime(c.expiresAt)}</td>
                                            <td><span className={`badge ${expired || exhausted ? 'badge-off' : 'badge-ok'}`}>{expired ? 'Vencido' : exhausted ? 'Agotado' : 'Vigente'}</span></td>
                                            <td><button type="button" className="link-btn" onClick={() => copy(c.code)}>Copiar</button></td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>

            <div className="card">
                <h4>Miembros del equipo</h4>
                {members === null ? <p className="muted">Cargando…</p> : (
                    <div className="table-wrap">
                        <table className="table">
                            <thead><tr><th>Nombre</th><th>Correo</th><th>Rol</th><th>Ingreso</th><th></th></tr></thead>
                            <tbody>
                                {members.map((m) => (
                                    <tr key={m.userId}>
                                        <td>{m.name}</td>
                                        <td>{m.email}</td>
                                        <td><span className={`badge ${m.role === 'ADMIN' ? 'badge-admin' : 'badge-ok'}`}>{m.role === 'ADMIN' ? 'Administrador' : 'Empleado'}</span></td>
                                        <td>{formatDateTime(m.joinedAt)}</td>
                                        <td>{m.role !== 'ADMIN' && <button type="button" className="link-btn" onClick={() => removeMember(m)}>Retirar</button>}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>
        </div>
    );
}
