import { useEffect, useState } from 'react';
import api, { apiError } from '../services/api.js';
import { useApp } from '../context/appContext.js';

export default function ChecklistSection({ eventId }) {
    const { showToast } = useApp();
    const [items, setItems] = useState(null);
    const [text, setText] = useState('');

    useEffect(() => {
        let cancelled = false;
        api.get(`/events/${eventId}/checklist`)
            .then((res) => { if (!cancelled) setItems(res.data.data || []); })
            .catch(() => { if (!cancelled) setItems([]); });
        return () => { cancelled = true; };
    }, [eventId]);

    const add = async (e) => {
        e.preventDefault();
        if (!text.trim()) return;
        try {
            const res = await api.post(`/events/${eventId}/checklist`, { description: text.trim() });
            setItems((prev) => [...(prev || []), res.data.data]);
            setText('');
        } catch (err) {
            showToast(apiError(err, 'No se pudo agregar la tarea'), 'error');
        }
    };

    const toggle = async (item) => {
        try {
            const res = await api.patch(`/events/${eventId}/checklist/${item.id}`, { completed: !item.completed });
            setItems((prev) => prev.map((i) => (i.id === item.id ? res.data.data : i)));
        } catch (err) {
            showToast(apiError(err, 'No se pudo actualizar la tarea'), 'error');
        }
    };

    const remove = async (item) => {
        try {
            await api.delete(`/events/${eventId}/checklist/${item.id}`);
            setItems((prev) => prev.filter((i) => i.id !== item.id));
        } catch (err) {
            showToast(apiError(err, 'No se pudo eliminar la tarea'), 'error');
        }
    };

    const done = (items || []).filter((i) => i.completed).length;

    return (
        <div className="checklist">
            <strong className="checklist-title">Lista de verificación {items && items.length > 0 && <span className="muted">({done}/{items.length})</span>}</strong>
            {items === null ? <p className="muted">Cargando…</p> : (
                <ul className="checklist-list">
                    {items.length === 0 && <li className="muted">Sin tareas todavía.</li>}
                    {items.map((item) => (
                        <li key={item.id}>
                            <label className={item.completed ? 'done' : ''}>
                                <input type="checkbox" checked={Boolean(item.completed)} onChange={() => toggle(item)} />
                                <span>{item.description}</span>
                            </label>
                            <button type="button" className="icon-action-btn text-danger" title="Eliminar tarea" onClick={() => remove(item)}>🗑️</button>
                        </li>
                    ))}
                </ul>
            )}
            <form onSubmit={add} className="inline-field">
                <input className="modal-input" placeholder="Agregar tarea…" value={text} onChange={(e) => setText(e.target.value)} />
                <button type="submit" className="secondary-btn">Añadir</button>
            </form>
        </div>
    );
}
