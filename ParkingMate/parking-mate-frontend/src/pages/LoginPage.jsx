import { useState } from 'react';
import axios from 'axios';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext'; // useAuth import

function LoginPage() {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const navigate = useNavigate();
    const { login } = useAuth(); // 방송국의 login 기능을 가져옴

    const handleSubmit = async (event) => {
        event.preventDefault();
        try {
            const response = await axios.post('http://localhost:8080/api/users/login', {
                email,
                password,
            });
            login(response.data.accessToken); // 방송국의 login 함수 호출
            alert('로그인 성공!');
            navigate('/mypage'); // 로그인 성공 후 마이페이지로 이동
        } catch (error) {
            alert('로그인에 실패했습니다.');
        }
    };

    return (
        // ... 폼 부분은 기존과 동일 ...
        <div>
            <h1>로그인</h1>
            <form onSubmit={handleSubmit}>
                <div>
                    <label>이메일:</label>
                    <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
                </div>
                <div>
                    <label>비밀번호:</label>
                    <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
                </div>
                <button type="submit">로그인하기</button>
            </form>
        </div>
    );
}

export default LoginPage;