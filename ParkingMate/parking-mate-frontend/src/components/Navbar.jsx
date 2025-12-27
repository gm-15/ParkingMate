import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import './Navbar.css';

function Navbar() {
    const { isLoggedIn, logout } = useAuth();
    const location = useLocation();

    const isActive = (path) => location.pathname === path;

    return (
        <nav className="navbar">
            <div className="navbar-container">
                <Link to="/" className="navbar-brand">
                    🅿️ ParkingMate
                </Link>
                <div className="navbar-links">
                    <Link 
                        to="/" 
                        className={`navbar-link ${isActive('/') ? 'active' : ''}`}
                    >
                        홈
                    </Link>
                    {isLoggedIn ? (
                        <>
                            <Link 
                                to="/create-space" 
                                className={`navbar-link ${isActive('/create-space') ? 'active' : ''}`}
                            >
                                공간 등록
                            </Link>
                            <Link 
                                to="/mypage" 
                                className={`navbar-link ${isActive('/mypage') ? 'active' : ''}`}
                            >
                                마이페이지
                            </Link>
                            <button 
                                onClick={logout} 
                                className="btn btn-outline btn-sm"
                            >
                                로그아웃
                            </button>
                        </>
                    ) : (
                        <>
                            <Link 
                                to="/login" 
                                className={`navbar-link ${isActive('/login') ? 'active' : ''}`}
                            >
                                로그인
                            </Link>
                            <Link 
                                to="/signup" 
                                className="btn btn-primary btn-sm"
                            >
                                회원가입
                            </Link>
                        </>
                    )}
                </div>
            </div>
        </nav>
    );
}

export default Navbar;
