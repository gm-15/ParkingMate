import { useState, useEffect } from 'react';
import apiClient from '../api/axios';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import '../App.css';

function MyPage() {
    const [bookings, setBookings] = useState([]);
    const [mySpaces, setMySpaces] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [activeTab, setActiveTab] = useState('bookings');
    const navigate = useNavigate();
    const location = useLocation();
    const { user } = useAuth();

    useEffect(() => {
        if (location.state?.message) {
            // 성공 메시지 표시 (선택사항)
        }
        fetchMyBookings();
        fetchMySpaces();
    }, []);

    const fetchMyBookings = async () => {
        try {
            const response = await apiClient.get('/bookings/my');
            const data = response.data.data || response.data;
            setBookings(Array.isArray(data) ? data : []);
        } catch (err) {
            setError('예약 내역을 불러오는 데 실패했습니다.');
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    const fetchMySpaces = async () => {
        try {
            const response = await apiClient.get('/spaces/my');
            const data = response.data.data || response.data;
            setMySpaces(Array.isArray(data) ? data : []);
        } catch (err) {
            console.error('내 주차 공간 조회 실패:', err);
        }
    };

    const handleCancelBooking = async (bookingId) => {
        if (!window.confirm('정말로 이 예약을 취소하시겠습니까?')) {
            return;
        }

        try {
            await apiClient.delete(`/bookings/${bookingId}`);
            fetchMyBookings();
        } catch (err) {
            const errorMessage = err.response?.data?.message || '예약 취소에 실패했습니다.';
            alert(errorMessage);
        }
    };

    const formatDateTime = (dateString) => {
        if (!dateString) return '';
        const date = new Date(dateString);
        return date.toLocaleString('ko-KR', {
            year: 'numeric',
            month: 'long',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
        });
    };

    const getStatusBadge = (status) => {
        if (status === 'RESERVED') {
            return <span style={{
                padding: 'var(--spacing-1) var(--spacing-3)',
                backgroundColor: 'var(--color-success-light)',
                color: '#065f46',
                borderRadius: 'var(--border-radius)',
                fontSize: 'var(--font-size-sm)',
                fontWeight: 500
            }}>예약됨</span>;
        } else {
            return <span style={{
                padding: 'var(--spacing-1) var(--spacing-3)',
                backgroundColor: 'var(--color-gray-300)',
                color: 'var(--color-gray-700)',
                borderRadius: 'var(--border-radius)',
                fontSize: 'var(--font-size-sm)',
                fontWeight: 500
            }}>취소됨</span>;
        }
    };

    if (loading) {
        return (
            <div className="loading">
                <span className="spinner"></span>
                <span style={{ marginLeft: 'var(--spacing-3)' }}>로딩 중...</span>
            </div>
        );
    }

    return (
        <div>
            <h1>마이페이지</h1>
            <p style={{ color: 'var(--color-gray-500)', marginBottom: 'var(--spacing-6)' }}>
                내 예약 내역과 등록한 주차 공간을 관리하세요
            </p>

            {/* 탭 네비게이션 */}
            <div style={{ 
                display: 'flex', 
                gap: 'var(--spacing-2)', 
                marginBottom: 'var(--spacing-6)',
                borderBottom: '1px solid var(--color-gray-200)'
            }}>
                <button
                    onClick={() => setActiveTab('bookings')}
                    className={`btn ${activeTab === 'bookings' ? 'btn-primary' : 'btn-outline'}`}
                    style={{ borderRadius: 'var(--border-radius) var(--border-radius) 0 0' }}
                >
                    내 예약 내역
                </button>
                <button
                    onClick={() => setActiveTab('spaces')}
                    className={`btn ${activeTab === 'spaces' ? 'btn-primary' : 'btn-outline'}`}
                    style={{ borderRadius: 'var(--border-radius) var(--border-radius) 0 0' }}
                >
                    내 주차 공간
                </button>
            </div>

            {error && (
                <div className="alert alert-error">
                    <span>⚠️</span>
                    <span>{error}</span>
                </div>
            )}

            {/* 예약 내역 탭 */}
            {activeTab === 'bookings' && (
                <div>
                    {bookings.length === 0 ? (
                        <div className="empty-state">
                            <div className="empty-state-icon">📅</div>
                            <h3>예약 내역이 없습니다</h3>
                            <p>주차 공간을 예약해보세요!</p>
                            <Link to="/" className="btn btn-primary" style={{ marginTop: 'var(--spacing-4)' }}>
                                주차 공간 찾기
                            </Link>
                        </div>
                    ) : (
                        <div className="grid grid-1">
                            {bookings.map(booking => (
                                <div key={booking.id || booking.bookingId} className="card">
                                    <div className="card-header">
                                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                                            <h3 style={{ margin: 0 }}>{booking.parkingSpaceAddress || booking.address}</h3>
                                            {getStatusBadge(booking.status)}
                                        </div>
                                    </div>
                                    <div className="card-body">
                                        <div style={{ marginBottom: 'var(--spacing-3)' }}>
                                            <div style={{ color: 'var(--color-gray-500)', fontSize: 'var(--font-size-sm)', marginBottom: 'var(--spacing-1)' }}>
                                                예약 시작
                                            </div>
                                            <div style={{ fontWeight: 500 }}>
                                                {formatDateTime(booking.startTime)}
                                            </div>
                                        </div>
                                        <div style={{ marginBottom: 'var(--spacing-3)' }}>
                                            <div style={{ color: 'var(--color-gray-500)', fontSize: 'var(--font-size-sm)', marginBottom: 'var(--spacing-1)' }}>
                                                예약 종료
                                            </div>
                                            <div style={{ fontWeight: 500 }}>
                                                {formatDateTime(booking.endTime)}
                                            </div>
                                        </div>
                                        {booking.totalPrice && (
                                            <div>
                                                <div style={{ color: 'var(--color-gray-500)', fontSize: 'var(--font-size-sm)', marginBottom: 'var(--spacing-1)' }}>
                                                    총 요금
                                                </div>
                                                <div style={{ 
                                                    fontSize: 'var(--font-size-xl)', 
                                                    fontWeight: 700,
                                                    color: 'var(--color-primary)'
                                                }}>
                                                    {booking.totalPrice?.toLocaleString()}원
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                    {booking.status === 'RESERVED' && (
                                        <div className="card-footer">
                                            <button
                                                onClick={() => handleCancelBooking(booking.id || booking.bookingId)}
                                                className="btn btn-danger btn-sm"
                                            >
                                                예약 취소
                                            </button>
                                        </div>
                                    )}
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            )}

            {/* 내 주차 공간 탭 */}
            {activeTab === 'spaces' && (
                <div>
                    <div style={{ marginBottom: 'var(--spacing-4)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <div style={{ color: 'var(--color-gray-500)' }}>
                            총 {mySpaces.length}개의 주차 공간
                        </div>
                        <Link to="/create-space" className="btn btn-primary btn-sm">
                            + 새 주차 공간 등록
                        </Link>
                    </div>

                    {mySpaces.length === 0 ? (
                        <div className="empty-state">
                            <div className="empty-state-icon">🅿️</div>
                            <h3>등록한 주차 공간이 없습니다</h3>
                            <p>주차 공간을 등록하고 수익을 창출해보세요!</p>
                            <Link to="/create-space" className="btn btn-primary" style={{ marginTop: 'var(--spacing-4)' }}>
                                주차 공간 등록하기
                            </Link>
                        </div>
                    ) : (
                        <div className="grid grid-1">
                            {mySpaces.map(space => (
                                <div key={space.id} className="card">
                                    <div className="card-header">
                                        <h3 style={{ margin: 0 }}>{space.address}</h3>
                                        <div style={{ 
                                            color: 'var(--color-primary)', 
                                            fontWeight: 600,
                                            fontSize: 'var(--font-size-lg)',
                                            marginTop: 'var(--spacing-2)'
                                        }}>
                                            {space.pricePerHour?.toLocaleString()}원/시간
                                        </div>
                                    </div>
                                    <div className="card-body">
                                        {space.description && (
                                            <p style={{ color: 'var(--color-gray-600)', marginBottom: 'var(--spacing-3)' }}>
                                                {space.description}
                                            </p>
                                        )}
                                    </div>
                                    <div className="card-footer">
                                        <Link 
                                            to={`/spaces/${space.id}`}
                                            className="btn btn-outline btn-sm"
                                        >
                                            상세보기
                                        </Link>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}

export default MyPage;
