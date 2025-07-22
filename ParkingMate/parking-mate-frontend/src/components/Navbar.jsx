import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

function Navbar() {
    const { isLoggedIn, logout } = useAuth();
    const navigate = useNavigate();

    const handleLogout = () => {
        logout();
        navigate('/');
    };

    return (
        <nav style={{ padding: '10px', borderBottom: '1px solid #ccc', marginBottom: '20px', display: 'flex', gap: '1rem', alignItems: 'center' }}>
            <Link to="/">홈</Link>
            
            {isLoggedIn ? (
                <>
                    <Link to="/create-space">주차 공간 등록</Link>
                    <Link to="/mypage">마이페이지</Link>
                    <button onClick={handleLogout}>로그아웃</button>
                </>
            ) : (
                <>
                    <Link to="/signup">회원가입</Link>
                    <Link to="/login">로그인</Link>
                </>
            )}
        </nav>
    );
}

export default Navbar;