import { useState } from 'react';
import axios from 'axios';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import '../App.css';

function LoginPage() {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();
    const { login } = useAuth();

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        setLoading(true);

        try {
            const response = await axios.post('http://localhost:8080/api/users/login', {
                email,
                password,
            });

            // API 응답 구조: ApiResponse<UserLoginResponseDto>
            const data = response.data.data || response.data;
            const token = data?.accessToken;
            if (token) {
                login(token);
                navigate('/mypage');
            } else {
                setError('로그인 응답을 처리할 수 없습니다.');
            }
        } catch (err) {
            const errorMessage = err.response?.data?.message || '로그인에 실패했습니다. 이메일과 비밀번호를 확인해주세요.';
            setError(errorMessage);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div style={{ maxWidth: '400px', margin: '0 auto' }}>
            <h1>로그인</h1>
            <p style={{ color: 'var(--color-gray-500)', marginBottom: 'var(--spacing-6)' }}>
                ParkingMate에 오신 것을 환영합니다
            </p>

            {error && (
                <div className="alert alert-error">
                    <span>⚠️</span>
                    <span>{error}</span>
                </div>
            )}

            <form onSubmit={handleSubmit}>
                <div className="form-group">
                    <label htmlFor="email" className="form-label required">
                        이메일
                    </label>
                    <input
                        id="email"
                        type="email"
                        className="form-input"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        placeholder="example@email.com"
                        required
                        disabled={loading}
                    />
                </div>

                <div className="form-group">
                    <label htmlFor="password" className="form-label required">
                        비밀번호
                    </label>
                    <input
                        id="password"
                        type="password"
                        className="form-input"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        placeholder="비밀번호를 입력하세요"
                        required
                        disabled={loading}
                        minLength={8}
                    />
                </div>

                <button
                    type="submit"
                    className="btn btn-primary"
                    style={{ width: '100%', marginTop: 'var(--spacing-4)' }}
                    disabled={loading}
                >
                    {loading ? (
                        <>
                            <span className="spinner"></span>
                            로그인 중...
                        </>
                    ) : (
                        '로그인'
                    )}
                </button>
            </form>

            <p style={{ textAlign: 'center', marginTop: 'var(--spacing-6)', color: 'var(--color-gray-500)' }}>
                계정이 없으신가요?{' '}
                <Link to="/signup" style={{ fontWeight: 500 }}>
                    회원가입
                </Link>
            </p>
        </div>
    );
}

export default LoginPage;
