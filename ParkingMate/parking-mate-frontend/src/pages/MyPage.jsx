import { useState, useEffect } from 'react';
import axios from 'axios';

function MyPage() {
    const [bookings, setBookings] = useState([]);
    const [error, setError] = useState('');

    const fetchMyBookings = async () => {
        try {
            const response = await axios.get('http://localhost:8080/api/bookings/my');
            setBookings(response.data);
        } catch (err) {
            setError('예약 내역을 불러오는 데 실패했습니다.');
            console.error(err);
        }
    };

    useEffect(() => {
        fetchMyBookings();
    }, []);

    const handleCancelBooking = async (bookingId) => {
        if (window.confirm('정말로 이 예약을 취소하시겠습니까?')) {
            try {
                await axios.delete(`http://localhost:8080/api/bookings/${bookingId}`);
                alert('예약이 성공적으로 취소되었습니다.');
                fetchMyBookings();
            } catch (err) {
                alert(err.response?.data || '예약 취소에 실패했습니다.');
                console.error('Cancel booking error:', err);
            }
        }
    };

    if (error) return <div>{error}</div>;

    return (
        <div>
            <h1>나의 예약 내역</h1>
            {bookings.length > 0 ? (
                bookings.map(booking => (
                    <div key={booking.bookingId} className="card">
                        <h3>{booking.parkingSpaceAddress}</h3>
                        <p><strong>예약 시작:</strong> {new Date(booking.startTime).toLocaleString()}</p>
                        <p><strong>예약 종료:</strong> {new Date(booking.endTime).toLocaleString()}</p>
                        <p><strong>상태:</strong> {booking.status === 'RESERVED' ? '예약됨' : '취소됨'}</p>
                        {booking.status === 'RESERVED' && (
                            <button onClick={() => handleCancelBooking(booking.bookingId)}>예약 취소</button>
                        )}
                    </div>
                ))
            ) : (
                <p>예약 내역이 없습니다.</p>
            )}
        </div>
    );
}

export default MyPage;