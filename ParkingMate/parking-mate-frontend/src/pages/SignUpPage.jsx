import { useState } from 'react';
import axios from 'axios';
import { useNavigate, Link } from 'react-router-dom';
import '../App.css';

function SignUpPage() {
    const [formData, setFormData] = useState({
        email: '',
        password: '',
        name: '',
    });
    const [errors, setErrors] = useState({});
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();

    const handleChange = (e) => {
        const { name, value } = e.target;
        setFormData(prev => ({ ...prev, [name]: value }));
        // 실시간 검증 피드백
        if (errors[name]) {
            setErrors(prev => ({ ...prev, [name]: '' }));
        }
    };

    const validate = () => {
        const newErrors = {};
        
        if (!formData.email) {
            newErrors.email = '이메일을 입력해주세요.';
        } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email)) {
            newErrors.email = '올바른 이메일 형식이 아닙니다.';
        }

        if (!formData.password) {
            newErrors.password = '비밀번호를 입력해주세요.';
        } else if (formData.password.length < 8) {
            newErrors.password = '비밀번호는 최소 8자 이상이어야 합니다.';
        }

        if (!formData.name) {
            newErrors.name = '이름을 입력해주세요.';
        } else if (formData.name.length < 2) {
            newErrors.name = '이름은 최소 2자 이상이어야 합니다.';
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');

        if (!validate()) {
            return;
        }

        setLoading(true);

        try {
            await axios.post('http://localhost:8080/api/users/signup', formData);
            navigate('/login', { 
                state: { message: '회원가입이 완료되었습니다. 로그인해주세요.' } 
            });
        } catch (err) {
            const errorMessage = err.response?.data?.message || '회원가입에 실패했습니다.';
            setError(errorMessage);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div style={{ maxWidth: '400px', margin: '0 auto' }}>
            <h1>회원가입</h1>
            <p style={{ color: 'var(--color-gray-500)', marginBottom: 'var(--spacing-6)' }}>
                ParkingMate에 가입하고 주차 공간을 공유해보세요
            </p>

            {error && (
                <div className="alert alert-error">
                    <span>⚠️</span>
                    <span>{error}</span>
                </div>
            )}

            <form onSubmit={handleSubmit}>
                <div className="form-group">
                    <label htmlFor="name" className="form-label required">
                        이름
                    </label>
                    <input
                        id="name"
                        name="name"
                        type="text"
                        className="form-input"
                        value={formData.name}
                        onChange={handleChange}
                        placeholder="홍길동"
                        required
                        disabled={loading}
                    />
                    {errors.name && <div className="form-error">{errors.name}</div>}
                </div>

                <div className="form-group">
                    <label htmlFor="email" className="form-label required">
                        이메일
                    </label>
                    <input
                        id="email"
                        name="email"
                        type="email"
                        className="form-input"
                        value={formData.email}
                        onChange={handleChange}
                        placeholder="example@email.com"
                        required
                        disabled={loading}
                    />
                    {errors.email && <div className="form-error">{errors.email}</div>}
                </div>

                <div className="form-group">
                    <label htmlFor="password" className="form-label required">
                        비밀번호
                    </label>
                    <input
                        id="password"
                        name="password"
                        type="password"
                        className="form-input"
                        value={formData.password}
                        onChange={handleChange}
                        placeholder="최소 8자 이상"
                        required
                        disabled={loading}
                        minLength={8}
                    />
                    {errors.password && <div className="form-error">{errors.password}</div>}
                    <div className="form-help">비밀번호는 최소 8자 이상이어야 합니다.</div>
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
                            가입 중...
                        </>
                    ) : (
                        '회원가입'
                    )}
                </button>
            </form>

            <p style={{ textAlign: 'center', marginTop: 'var(--spacing-6)', color: 'var(--color-gray-500)' }}>
                이미 계정이 있으신가요?{' '}
                <Link to="/login" style={{ fontWeight: 500 }}>
                    로그인
                </Link>
            </p>
        </div>
    );
}

export default SignUpPage;
