import { useState } from 'react';
import api, { apiError } from '../../services/api.js';
import { useApp } from '../../context/appContext.js';

/** Crear una organización o unirse con un código de invitación (CU-01). */
export default function NoOrgPanel({ compact = false }) {
    const { showToast, refreshMe } = useApp();
    const [orgName, setOrgName] = useState('');
    const [code, setCode] = useState('');
    const [busy, setBusy] = useState(false);

    const create = async (e) => {
        e.preventDefault();
        if (!orgName.trim()) return;
        setBusy(true);
        try {
            await api.post('/organizations', { name: orgName.trim() });
            showToast('Organización creada. Ahora eres su administrador.');
            setOrgName('');
            refreshMe();
        } catch (err) {
            showToast(apiError(err, 'No se pudo crear la organización'), 'error');
        } finally {
            setBusy(false);
        }
    };

    const join = async (e) => {
        e.preventDefault();
        if (!code.trim()) return;
        setBusy(true);
        try {
            const res = await api.post('/organizations/join', { code: code.trim() });
            showToast(`Te has unido a ${res.data.data?.organizationName || 'la organización'}`);
            setCode('');
            refreshMe();
        } catch (err) {
            showToast(apiError(err, 'Código de invitación inválido'), 'error');
        } finally {
            setBusy(false);
        }
    };

    return (
        <div className={compact ? 'card-grid' : 'card-grid two'}>
            <form className="card" onSubmit={create}>
                <h4>Crear nueva organización</h4>
                <p className="muted">Inicia un espacio de trabajo para tu equipo. Serás su administrador.</p>
                <label className="field-label">Nombre de la empresa</label>
                <input className="field-input" placeholder="Ej: Acme Corp" value={orgName} onChange={(e) => setOrgName(e.target.value)} required />
                <button className="primary-btn" type="submit" disabled={busy}>Crear y continuar</button>
            </form>
            <form className="card" onSubmit={join}>
                <h4>Unirme a un equipo</h4>
                <p className="muted">Ingresa el código que te compartió tu administrador. Si tienes una suscripción individual activa, se pausará sin perder días.</p>
                <label className="field-label">Código de invitación</label>
                <input className="field-input" placeholder="Ej: ORG-A1B2C3" value={code} onChange={(e) => setCode(e.target.value)} required />
                <button className="secondary-btn" type="submit" disabled={busy}>Unirme ahora</button>
            </form>
        </div>
    );
}
