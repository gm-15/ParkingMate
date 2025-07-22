import { useState } from 'react';
import axios from 'axios';
import { useNavigate } from 'react-router-dom';

function CreateSpacePage() {
    const [address, setAddress] = useState('');
    const [pricePerHour, setPricePerHour] = useState('');
    const [description, setDescription] = useState('');
    const navigate = useNavigate();

    const handleSubmit = async (event) => {
        event.preventDefault();

        try {
            // AuthContext가 모든 요청에 토큰을 포함시키므로 바로 API 호출
            await axios.post('http://localhost:8080/api/spaces', {
                address,
                pricePerHour: parseInt(pricePerHour, 10), // 문자열을 숫자로 변환
                description,
            });
            alert('주차 공간이 성공적으로 등록되었습니다.');
            navigate('/'); // 성공 후 홈으로 이동
        } catch (error) {
            alert('주차 공간 등록에 실패했습니다.');
            console.error('Create space error:', error);
        }
    };

    return (
        <div>
            <h1>새 주차 공간 등록</h1>
            <form onSubmit={handleSubmit}>
                <div>
                    <label>주소:</label>
                    <input
                        type="text"
                        value={address}
                        onChange={(e) => setAddress(e.target.value)}
                        required
                    />
                </div>
                <div>
                    <label>시간당 가격:</label>
                    <input
                        type="number"
                        value={pricePerHour}
                        onChange={(e) => setPricePerHour(e.target.value)}
                        required
                    />
                </div>
                <div>
                    <label>상세 설명:</label>
                    <textarea
                        value={description}
                        onChange={(e) => setDescription(e.target.value)}
                    />
                </div>
                <button type="submit">등록하기</button>
            </form>
        </div>
    );
}

export default CreateSpacePage;