import { Link } from 'react-router-dom';

export default function Privacy() {
    return (
        <div style={{ minHeight: '100vh', background: '#f8fafc', fontFamily: 'Inter, system-ui, sans-serif', color: '#0f172a', padding: '2rem 1rem' }}>
            <article style={{ maxWidth: '760px', margin: '0 auto', background: '#fff', border: '1px solid #e2e8f0', borderRadius: '16px', padding: '2.5rem', lineHeight: 1.65 }}>
                <h1 style={{ marginTop: 0 }}>Política de tratamiento de datos personales</h1>
                <p style={{ color: '#64748b' }}>Ley Estatutaria 1581 de 2012 (Colombia) y normas reglamentarias.</p>

                <h2>Responsable del tratamiento</h2>
                <p>El equipo del proyecto SyncFlow es el responsable del tratamiento de los datos personales recolectados a través de esta plataforma.</p>

                <h2>Datos que recolectamos</h2>
                <p>Nombre, correo electrónico, contraseña (almacenada únicamente cifrada con un algoritmo de hash seguro), los eventos de tu calendario, tu pertenencia a organizaciones y el estado de tu suscripción.</p>

                <h2>Finalidad</h2>
                <ul>
                    <li>Crear y administrar tu cuenta y tu calendario personal.</li>
                    <li>Coordinar eventos y horarios entre administradores y empleados de una organización.</li>
                    <li>Enviarte notificaciones por correo cuando un evento se asigne, cambie o se cancele.</li>
                    <li>Gestionar suscripciones individuales y de organización.</li>
                </ul>
                <p>Tus datos no se venden ni se comparten con terceros ajenos a la prestación del servicio. Los usuarios de una organización solo acceden a la información de su propia organización.</p>

                <h2>Tus derechos</h2>
                <p>Como titular puedes <strong>conocer</strong>, <strong>actualizar</strong>, <strong>rectificar</strong> y <strong>suprimir</strong> tus datos, así como revocar la autorización otorgada.</p>

                <h2>Cómo ejercerlos</h2>
                <p>Desde la sección <strong>«Mi cuenta»</strong> puedes consultar tus datos, corregir tu nombre y eliminar tu cuenta junto con la información asociada a ella. La supresión es definitiva.</p>

                <p style={{ marginTop: '2rem' }}><Link to="/register" style={{ color: '#4f46e5', fontWeight: 600 }}>← Volver al registro</Link></p>
            </article>
        </div>
    );
}
