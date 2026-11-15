import { useEffect, useState } from 'react';
import api, { apiError } from '../services/api.js';
import { useApp } from '../context/appContext.js';

export default function TimelineModal({ event, onClose }) {
    const { showToast, isDarkMode } = useApp();
    const [entries, setEntries] = useState([]);
    const [content, setContent] = useState('');
    const [saving, setSaving] = useState(false);
    const [reloadKey, setReloadKey] = useState(0);

    useEffect(() => {
        let cancelled = false;
        api.get(`/events/${event.baseId}/timeline`)
            .then((res) => { if (!cancelled) setEntries(res.data.data || []); })
            .catch(() => { if (!cancelled) setEntries([]); });
        return () => { cancelled = true; };
    }, [event.baseId, reloadKey]);

    const add = async () => {
        if (!content.trim()) return;
        setSaving(true);
        try {
            await api.post(`/events/${event.baseId}/timeline`, { content, entryType: 'COMMENT' });
            showToast('Evidencia agregada al contexto del evento');
            setContent('');
            setReloadKey((k) => k + 1);
        } catch (err) {
            showToast(apiError(err, 'No se pudo guardar la evidencia'), 'error');
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="modal-overlay">
            <div className="modal-container" style={{ maxWidth: '550px' }}>
                <div className="modal-header">
                    <h3 className="modal-title">Espacio de Trabajo: {event.title}</h3>
                    <button onClick={onClose} className="close-btn">✕</button>
                </div>
                <div className="modal-form">
                    <div style={{ maxHeight: '250px', overflowY: 'auto', marginBottom: '1.5rem', padding: '15px', backgroundColor: isDarkMode ? '#1e293b' : '#f8fafc', borderRadius: '8px', border: '1px solid', borderColor: isDarkMode ? '#334155' : '#e2e8f0' }}>
                        {entries.length === 0 ? (
                            <p style={{ color: '#64748b', fontSize: '14px', textAlign: 'center', margin: 0 }}>Aún no hay minutas ni evidencias registradas.</p>
                        ) : (
                            entries.map((entry) => (
                                <div key={entry.id} style={{ backgroundColor: isDarkMode ? '#0f172a' : 'white', padding: '12px', borderRadius: '6px', marginBottom: '10px', border: '1px solid', borderColor: isDarkMode ? '#334155' : '#cbd5e1' }}>
                                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', color: '#64748b', marginBottom: '8px' }}>
                                        <strong style={{ color: isDarkMode ? '#cbd5e1' : '#334155' }}>{entry.authorName || 'Usuario'}</strong>
                                        <span>{new Date(entry.createdAt).toLocaleString()}</span>
                                    </div>
                                    <p style={{ fontSize: '14px', margin: 0, color: isDarkMode ? '#f1f5f9' : '#334155', whiteSpace: 'pre-wrap' }}>{entry.content}</p>
                                </div>
                            ))
                        )}
                    </div>
                    <div className="form-group">
                        <label className="modal-label">Añadir acta, acuerdo o link asíncrono</label>
                        <textarea className="modal-input" style={{ minHeight: '100px', resize: 'vertical' }} placeholder="Escribe notas de la reunión o adjunta links de Drive..." value={content} onChange={(e) => setContent(e.target.value)} />
                    </div>
                    <div className="modal-actions-spaced" style={{ justifyContent: 'flex-end', marginTop: '1.5rem' }}>
                        <div className="action-buttons-group">
                            <button type="button" onClick={onClose} className="cancel-btn">Volver</button>
                            <button type="button" onClick={add} className="confirm-btn" disabled={saving}>{saving ? 'Guardando...' : 'Publicar en el Contexto'}</button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}
