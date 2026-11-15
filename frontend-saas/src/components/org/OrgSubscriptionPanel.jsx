import { useState } from 'react';
import api, { apiError } from '../../services/api.js';
import { useApp } from '../../context/appContext.js';
import { formatDate } from '../../utils/dates.js';

/** Gestión de la suscripción de la organización (pago simulado). */
export default function OrgSubscriptionPanel({ membership }) {
    const { showToast, refreshMe } = useApp();
    const [months, setMonths] = useState(1);
    const [busy, setBusy] = useState(false);
    const isBusiness = membership.plan === 'BUSINESS';
    const expired = isBusiness && membership.planEndDate && new Date(`${membership.planEndDate}T23:59:59`) < new Date();

    const update = async (plan) => {
        const msg = plan === 'BUSINESS'
            ? `Se simulará el pago del plan BUSINESS por ${months} mes(es). ¿Continuar?`
            : '¿Volver al plan gratuito? Los empleados perderán los beneficios de la organización.';
        if (!window.confirm(msg)) return;
        setBusy(true);
        try {
            await api.put(`/organizations/${membership.organizationId}/subscription`, plan === 'BUSINESS' ? { plan, months: Number(months) } : { plan });
            showToast(plan === 'BUSINESS' ? 'Suscripción BUSINESS activada' : 'La organización volvió al plan gratuito');
            refreshMe();
        } catch (err) {
            showToast(apiError(err, 'No se pudo actualizar la suscripción'), 'error');
        } finally {
            setBusy(false);
        }
    };

    return (
        <div className="card">
            <h4>Suscripción de la organización</h4>
            <div className="kv">
                <span>Plan actual</span>
                <strong><span className={`badge ${isBusiness && !expired ? 'badge-ok' : 'badge-off'}`}>{isBusiness ? 'BUSINESS' : 'Gratuito (FREE_TIER)'}</span></strong>
                <span>Vence</span>
                <strong>{isBusiness ? `${formatDate(membership.planEndDate)}${expired ? ' (vencido)' : ''}` : '—'}</strong>
            </div>
            <p className="muted">Con el plan BUSINESS vigente, todos tus empleados disfrutan los beneficios de la organización sin pagar una suscripción individual.</p>
            <div className="alert-info">💳 El pago es <strong>simulado</strong>: no se realiza ningún cobro real (la pasarela de pagos es trabajo futuro).</div>
            <div className="inline-field" style={{ marginTop: '1rem' }}>
                <label className="field-label" htmlFor="orgMonths">Meses</label>
                <select id="orgMonths" className="field-input" style={{ width: '110px' }} value={months} onChange={(e) => setMonths(e.target.value)}>
                    {Array.from({ length: 12 }, (_, i) => i + 1).map((n) => <option key={n} value={n}>{n}</option>)}
                </select>
                <button className="primary-btn" disabled={busy} onClick={() => update('BUSINESS')}>{isBusiness ? 'Extender BUSINESS' : 'Contratar BUSINESS'}</button>
                {isBusiness && <button className="delete-btn" disabled={busy} onClick={() => update('FREE_TIER')}>Volver a gratuito</button>}
            </div>
        </div>
    );
}
