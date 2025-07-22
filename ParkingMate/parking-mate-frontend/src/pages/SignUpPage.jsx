import { useState } from 'react';
import axios from 'axios';

function SignUpPage() {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [name, setName] = useState('');

    const handleSubmit = async (event) => {
        event.preventDefault(); // 폼의 기본 제출 동작을 막음

        try {
            const response = await axios.post('http://localhost:8080/api/users/signup', {
                email: email,
                password: password,
                name: name,
            });
            alert(response.data); // 성공 시, 백엔드에서 보낸 메시지를 알림창으로 표시
            // 성공 후 로그인 페이지로 이동하는 로직 추가 가능
        } catch (error) {
            alert(error.response.data); // 실패 시, 백엔드에서 보낸 에러 메시지를 표시
        }
    };

    return (
        <div>
            <h1>회원가입</h1>
            <form onSubmit={handleSubmit}>
                <div>
                    <label>이메일:</label>
                    <input
                        type="email"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        required
                    />
                </div>
                <div>
                    <label>비밀번호:</label>
                    <input
                        type="password"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        required
                    />
                </div>
                <div>
                    <label>이름:</label>
                    <input
                        type="text"
                        value={name}
                        onChange={(e) => setName(e.target.value)}
                        required
                    />
                </div>
                <button type="submit">가입하기</button>
            </form>
        </div>
    );
}

export default SignUpPage;