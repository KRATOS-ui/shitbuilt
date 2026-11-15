import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import api, { apiError, clearSession } from '../services/api.js';
import { AppContext } from '../context/appContext.js';
import logo from '../assets/SyncFlow.png';
import '../styles/Dashboard.css';
import '../styles/App.css';

const PLAN_LABELS = { FREE: 'Plan Básico', INDIVIDUAL: 'Plan Individual', ORGANIZATION: 'Plan Organización' };

export default function AppLayout() {
    const navigate = useNavigate();
    const [isSidebarOpen, setIsSidebarOpen] = useState(false);
    const [isDarkMode, setIsDarkMode] = useState(() => localStorage.getItem('theme') === 'dark');
    const [toast, setToast] = useState({ show: false, message: '', type: 'success' });
    const [me, setMe] = useState(null);
    const [memberships, setMemberships] = useState([]);
    const [reloadKey, setReloadKey] = useState(0);
    const toastTimer = useRef(null);

    const showToast = useCallback((message, type = 'success') => {
        setToast({ show: true, message, type });
        clearTimeout(toastTimer.current);
        toastTimer.current = setTimeout(() => setToast({ show: false, message: '', type: 'success' }), 3500);
    }, []);

    const refreshMe = useCallback(() => setReloadKey((k) => k + 1), []);

    useEffect(() => {
        let cancelled = false;
        Promise.all([api.get('/users/me'), api.get('/organizations/mine')])
            .then(([meRes, orgRes]) => {
                if (cancelled) return;
                setMe(meRes.data.data);
                setMemberships(orgRes.data.data || []);
            })
            .catch((err) => { if (!cancelled) showToast(apiError(err, 'No se pudo cargar tu perfil'), 'error'); });
        return () => { cancelled = true; };
    }, [reloadKey, showToast]);

    useEffect(() => () => clearTimeout(toastTimer.current), []);

    const toggleTheme = () => {
        setIsDarkMode((d) => {
            localStorage.setItem('theme', d ? 'light' : 'dark');
            return !d;
        });
    };

    const handleLogout = () => {
        clearSession();
        navigate('/login');
    };

    const ctx = useMemo(
        () => ({ me, memberships, refreshMe, showToast, isDarkMode }),
        [me, memberships, refreshMe, showToast, isDarkMode]
    );

    const navClass = ({ isActive }) => `nav-item ${isActive ? 'active' : ''}`;

    return (
        <AppContext.Provider value={ctx}>
            <div className={`layout ${isDarkMode ? 'dark-mode' : ''}`}>
                <div className={`toast-container ${toast.show ? 'show' : ''} toast-${toast.type}`} role="status">
                    {toast.message}
                </div>

                <aside className={`sidebar ${isSidebarOpen ? 'open' : 'closed'}`}>
                    <div className="sidebar-header" style={{ padding: '20px 15px', display: 'flex', alignItems: 'center', gap: '12px' }}>
                        <img src={logo} alt="SyncFlow Logo" style={{ width: '35px', height: '35px', borderRadius: '8px', objectFit: 'cover' }} />
                        {isSidebarOpen && <h2 className="brand-name" style={{ margin: 0, fontSize: '1.2rem' }}>SyncFlow Teams</h2>}
                    </div>

                    <nav className="sidebar-nav">
                        <NavLink to="/dashboard" className={navClass}>
                            <span className="icon">📅</span> {isSidebarOpen && 'Calendario'}
                        </NavLink>
                        <NavLink to="/organizacion" className={navClass}>
                            <span className="icon">🏢</span> {isSidebarOpen && 'Organización'}
                        </NavLink>
                        <NavLink to="/cuenta" className={navClass}>
                            <span className="icon">👤</span> {isSidebarOpen && 'Mi cuenta'}
                        </NavLink>
                    </nav>

                    <div className="sidebar-footer">
                        <button onClick={handleLogout} className="nav-item text-danger" style={{ color: '#ef4444' }}>
                            <span className="icon">🚪</span> {isSidebarOpen && 'Cerrar sesión'}
                        </button>
                        {isSidebarOpen && me && (
                            <div className="sidebar-plan" style={{ marginTop: '15px' }}>
                                <span className="plan-badge">{PLAN_LABELS[me.effectivePlan] || me.effectivePlan}</span>
                                <button className="upgrade-btn" onClick={() => navigate('/cuenta')}>Administrar plan</button>
                            </div>
                        )}
                    </div>
                </aside>

                <main className="main-area">
                    <header className="top-header">
                        <button onClick={() => setIsSidebarOpen((o) => !o)} className="toggle-btn" aria-label="Mostrar u ocultar menú">☰</button>
                        <div className="header-actions">
                            <span style={{ fontSize: '14px', color: isDarkMode ? '#94a3b8' : '#64748b', fontWeight: 500 }}>
                                {me ? `${me.name} · ${me.email}` : 'Cargando…'}
                            </span>
                            <button onClick={toggleTheme} className="theme-toggle" aria-label="Cambiar tema">
                                {isDarkMode ? '☀️' : '🌙'}
                            </button>
                        </div>
                    </header>

                    <div className="content-container">
                        <Outlet />
                    </div>
                </main>
            </div>
        </AppContext.Provider>
    );
}
