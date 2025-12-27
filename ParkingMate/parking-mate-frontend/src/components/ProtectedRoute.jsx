import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

function ProtectedRoute({ children }) {
    const { isLoggedIn, loading } = useAuth();

    if (loading) {
        return (
            <div className="loading">
                <span className="spinner"></span>
                <span style={{ marginLeft: 'var(--spacing-3)' }}>로딩 중...</span>
            </div>
        );
    }

    if (!isLoggedIn) {
        return <Navigate to="/login" replace />;
    }

    return children;
}

export default ProtectedRoute;
