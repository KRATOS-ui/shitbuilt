import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api, { apiError, clearSession } from '../services/api.js';
import { useApp } from '../context/appContext.js';
import { formatDate } from '../utils/dates.js';

const PLAN_INFO = {
    FREE: { label: 'Básico (gratuito)', text: 'Calendario personal, eventos recurrentes y creación con IA.' },
    INDIVIDUAL: { label: 'Individual', text: 'Tu suscripción personal está vigente.' },
    ORGANIZATION: { label: 'Organización', text: 'Disfrutas los beneficios gracias a la suscripción BUSINESS de tu organización.' },
};

export default function Account() {
    const { me, refreshMe, showToast } = useApp();
    const navigate = useNavigate();
    const [name, setName] = useState(null);
    const [months, setMonths] = useState(1);
    const [busy, setBusy] = useState(false);
    const [confirmText, setConfirmText] = useState('');
    const [showDelete, setShowDelete] = useState(false);

    if (me === null) return <p className="muted">Cargando…</p>;

    const nameValue = name ?? me.name;
    const plan = PLAN_INFO[me.effectivePlan] || PLAN_INFO.FREE;

    const saveName = async (e) => {
        e.preventDefault();
        if (!nameValue.trim()) return;
        setBusy(true);
        try {
            await api.put('/users/me', { name: nameValue.trim() });
            showToast('Datos actualizados');
            setName(null);
            refreshMe();
        } catch (err) {
            showToast(apiError(err, 'No se pudo actualizar el nombre'), 'error');
        } finally {
            setBusy(false);
        }
    };

    const buy = async (e) => {
        e.preventDefault();
        if (!window.confirm(`Se simulará el pago de ${months} mes(es) de suscripción individual. ¿Continuar?`)) return;
        setBusy(true);
        try {
            await api.post('/subscriptions/individual', { months: Number(months) });
            showToast('Suscripción individual registrada');
            refreshMe();
        } catch (err) {
            showToast(apiError(err, 'No se pudo registrar la suscripción'), 'error');
        } finally {
            setBusy(false);
        }
    };

    const deleteAccount = async () => {
        if (confirmText.trim().toUpperCase() !== 'ELIMINAR') return;
        if (!window.confirm('Última confirmación: tu cuenta y todos tus datos se borrarán de forma definitiva. ¿Eliminar mi cuenta?')) return;
        setBusy(true);
        try {
            await api.delete('/users/me');
            clearSession();
            navigate('/login');
        } catch (err) {
            showToast(apiError(err, 'No se pudo eliminar la cuenta'), 'error');
            setBusy(false);
        }
    };

    const status = {
        ACTIVE: <><span className="badge badge-ok">Activa</span> hasta {formatDate(me.subscriptionEndDate)}</>,
        PAUSED: <><span className="badge badge-warn">Pausada</span> {me.pausedDaysRemaining} día(s) congelados; se reanudan al salir de la organización</>,
        INACTIVE: <span className="badge badge-off">Sin suscripción individual</span>,
    }[me.subscriptionStatus] || me.subscriptionStatus;

    return (
        <div className="page">
            <h2 className="page-title">Mi cuenta</h2>

            <div className="card-grid two">
                <form className="card" onSubmit={saveName}>
                    <h4>Mis datos</h4>
                    <label className="field-label">Nombre</label>
                    <input className="field-input" value={nameValue} onChange={(e) => setName(e.target.value)} required />
                    <label className="field-label">Correo</label>
                    <input className="field-input" value={me.email} disabled />
                    <button className="primary-btn" type="submit" disabled={busy || nameValue.trim() === me.name}>Guardar cambios</button>
                    <p className="muted" style={{ marginBottom: 0 }}>Puedes conocer, rectificar y suprimir tus datos (Ley 1581 de 2012). <Link to="/privacidad" target="_blank">Ver política</Link></p>
                </form>

                <div className="card">
                    <h4>Plan vigente</h4>
                    <div className="kv">
                        <span>Plan efectivo</span><strong><span className="badge badge-admin">{plan.label}</span></strong>
                        <span>Suscripción individual</span><strong>{status}</strong>
                    </div>
                    <p className="muted">{plan.text}</p>
                    {me.memberships?.length > 0 && (
                        <p className="muted">Organizaciones: {me.memberships.map((m) => `${m.organizationName} (${m.role === 'ADMIN' ? 'admin' : 'empleado'})`).join(', ')}.</p>
                    )}
                </div>
            </div>

            <form className="card" onSubmit={buy}>
                <h4>Comprar suscripción individual</h4>
                <div className="alert-info">💳 El pago es <strong>simulado</strong>: no se realiza ningún cobro real.</div>
                <p className="muted">Si perteneces a una organización, los días se suman a tu suscripción pausada y se reanudan cuando salgas de ella.</p>
                <div className="inline-field">
                    <label className="field-label" htmlFor="months">Meses</label>
                    <select id="months" className="field-input" style={{ width: '110px' }} value={months} onChange={(e) => setMonths(e.target.value)}>
                        {Array.from({ length: 12 }, (_, i) => i + 1).map((n) => <option key={n} value={n}>{n}</option>)}
                    </select>
                    <button className="primary-btn" type="submit" disabled={busy}>Comprar (simulado)</button>
                </div>
            </form>

            <div className="card danger-zone">
                <h4>Eliminar mi cuenta</h4>
                <p className="muted">Se borrarán tu cuenta, tus eventos y tu información asociada. Esta acción es definitiva.</p>
                {!showDelete ? (
                    <button className="delete-btn" onClick={() => setShowDelete(true)}>Quiero eliminar mi cuenta</button>
                ) : (
                    <div className="inline-field">
                        <input className="field-input" placeholder="Escribe ELIMINAR para confirmar" value={confirmText} onChange={(e) => setConfirmText(e.target.value)} style={{ maxWidth: '280px' }} />
                        <button className="delete-btn" disabled={busy || confirmText.trim().toUpperCase() !== 'ELIMINAR'} onClick={deleteAccount}>Eliminar definitivamente</button>
                        <button className="cancel-btn" onClick={() => { setShowDelete(false); setConfirmText(''); }}>Cancelar</button>
                    </div>
                )}
            </div>
        </div>
    );
}
