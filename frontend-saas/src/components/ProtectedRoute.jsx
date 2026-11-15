import { Navigate } from 'react-router-dom';
import { hasSession } from '../services/api.js';

export default function ProtectedRoute({ children }) {
    return hasSession() ? children : <Navigate to="/login" replace />;
}
