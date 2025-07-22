import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { useAuth } from '../context/AuthContext'; // useAuth 추가

function SpaceDetailPage() {
    const [space, setSpace] = useState(null);
    const [error, setError] = useState('');
    const { id } = useParams();
    const navigate = useNavigate();

    // --- 예약 폼을 위한 상태 추가 ---
    const { isLoggedIn } = useAuth(); // 로그인 상태 확인
    const [startTime, setStartTime] = useState('');
    const [endTime, setEndTime] = useState('');

    useEffect(() => {
        const fetchSpaceDetails = async () => {
            try {
                const response = await axios.get(`http://localhost:8080/api/spaces/${id}`);
                setSpace(response.data);
            } catch (err) {
                setError('주차 공간 정보를 불러오는 데 실패했습니다.');
            }
        };
        fetchSpaceDetails();
    }, [id]);

    // --- 예약 버튼 클릭 시 실행될 함수 ---
    const handleBookingSubmit = async (event) => {
        event.preventDefault();
        if (!startTime || !endTime) {
            alert('시작 시간과 종료 시간을 모두 선택해주세요.');
            return;
        }
        try {
            await axios.post('http://localhost:8080/api/bookings', {
                parkingSpaceId: id,
                startTime,
                endTime,
            });
            alert('예약이 성공적으로 완료되었습니다.');
            navigate('/mypage'); // 성공 후 나의 예약 내역 페이지로 이동
        } catch (err) {
            alert(err.response?.data || '예약에 실패했습니다. 이미 예약된 시간일 수 있습니다.');
        }
    };


    if (error) return <div>{error}</div>;
    if (!space) return <div>로딩 중...</div>;

    return (
        <div>
            <h1>{space.address}</h1>
            <p><strong>시간당 요금:</strong> {space.pricePerHour}원</p>
            <p><strong>상세 설명:</strong> {space.description}</p>
            <p><strong>소유자:</strong> {space.ownerName}</p>
            <hr />

            {/* 로그인한 사용자에게만 예약 폼을 보여줍니다. */}
            {isLoggedIn ? (
                <form onSubmit={handleBookingSubmit}>
                    <h3>예약하기</h3>
                    <div>
                        <label>시작 시간: </label>
                        <input
                            type="datetime-local"
                            value={startTime}
                            onChange={(e) => setStartTime(e.target.value)}
                            required
                        />
                    </div>
                    <div>
                        <label>종료 시간: </label>
                        <input
                            type="datetime-local"
                            value={endTime}
                            onChange={(e) => setEndTime(e.target.value)}
                            required
                        />
                    </div>
                    <button type="submit">예약하기</button>
                </form>
            ) : (
                <p>예약을 하시려면 로그인이 필요합니다.</p>
            )}
        </div>
    );
}

export default SpaceDetailPage;