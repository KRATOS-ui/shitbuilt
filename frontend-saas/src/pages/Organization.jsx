import { useState } from 'react';
import api, { apiError } from '../services/api.js';
import { useApp } from '../context/appContext.js';
import NoOrgPanel from '../components/org/NoOrgPanel.jsx';
import AdminTeamPanel from '../components/org/AdminTeamPanel.jsx';
import AdminEventsPanel from '../components/org/AdminEventsPanel.jsx';
import OrgSubscriptionPanel from '../components/org/OrgSubscriptionPanel.jsx';
import { formatDate, formatDateTime } from '../utils/dates.js';

const TABS = [
    { key: 'equipo', label: '👥 Equipo y códigos' },
    { key: 'eventos', label: '📌 Eventos y asignación' },
    { key: 'suscripcion', label: '💳 Suscripción' },
];

export default function Organization() {
    const { me, memberships, showToast, refreshMe } = useApp();
    const [selectedId, setSelectedId] = useState(null);
    const [tab, setTab] = useState('equipo');
    const [leaving, setLeaving] = useState(false);

    if (me === null) return <p className="muted">Cargando…</p>;

    if (memberships.length === 0) {
        return (
            <div className="page">
                <h2 className="page-title">Organización</h2>
                <p className="muted">Aún no perteneces a ninguna organización. Crea una para coordinar a tu equipo o únete con un código de invitación.</p>
                <NoOrgPanel />
            </div>
        );
    }

    const current = memberships.find((m) => m.organizationId === selectedId) || memberships[0];
    const isAdmin = current.role === 'ADMIN';

    const leave = async () => {
        if (!window.confirm(`¿Seguro que quieres salir de «${current.organizationName}»? Si tenías una suscripción individual pausada, se reanudará.`)) return;
        setLeaving(true);
        try {
            await api.post(`/organizations/${current.organizationId}/leave`);
            showToast('Saliste de la organización');
            setSelectedId(null);
            refreshMe();
        } catch (err) {
            showToast(apiError(err, 'No se pudo salir de la organización'), 'error');
        } finally {
            setLeaving(false);
        }
    };

    return (
        <div className="page">
            <div className="page-head">
                <h2 className="page-title">Organización</h2>
                {memberships.length > 1 && (
                    <select className="field-input" style={{ maxWidth: '280px' }} value={current.organizationId} onChange={(e) => { setSelectedId(e.target.value); setTab('equipo'); }} aria-label="Seleccionar organización">
                        {memberships.map((m) => <option key={m.organizationId} value={m.organizationId}>{m.organizationName} ({m.role === 'ADMIN' ? 'admin' : 'empleado'})</option>)}
                    </select>
                )}
            </div>

            <div className="card">
                <div className="card-title-row">
                    <div>
                        <h3 style={{ margin: 0 }}>{current.organizationName}</h3>
                        <span className="muted">Miembro desde {formatDateTime(current.joinedAt)}</span>
                    </div>
                    <span className={`badge ${isAdmin ? 'badge-admin' : 'badge-ok'}`}>{isAdmin ? 'Administrador' : 'Empleado'}</span>
                </div>
                <div className="kv">
                    <span>Plan de la organización</span>
                    <strong>{current.plan === 'BUSINESS' ? `BUSINESS (vence ${formatDate(current.planEndDate)})` : 'Gratuito (FREE_TIER)'}</strong>
                </div>
                {!isAdmin && <p className="muted">Los eventos que te asigne tu administrador aparecen en tu calendario junto a los personales, y recibirás un correo por cada uno.</p>}
                <button className="delete-btn" onClick={leave} disabled={leaving}>{leaving ? 'Saliendo…' : 'Salir de la organización'}</button>
            </div>

            {isAdmin && (
                <>
                    <div className="tabs" role="tablist">
                        {TABS.map((t) => (
                            <button key={t.key} role="tab" aria-selected={tab === t.key} className={`tab ${tab === t.key ? 'active' : ''}`} onClick={() => setTab(t.key)}>{t.label}</button>
                        ))}
                    </div>
                    {tab === 'equipo' && <AdminTeamPanel key={`t-${current.organizationId}`} orgId={current.organizationId} />}
                    {tab === 'eventos' && <AdminEventsPanel key={`e-${current.organizationId}`} orgId={current.organizationId} />}
                    {tab === 'suscripcion' && <OrgSubscriptionPanel key={`s-${current.organizationId}`} membership={current} />}
                </>
            )}

            <details className="card more-orgs">
                <summary>Crear otra organización o unirme con un código</summary>
                <NoOrgPanel compact />
            </details>
        </div>
    );
}
