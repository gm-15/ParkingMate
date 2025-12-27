import { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import axios from 'axios';
import { useAuth } from '../context/AuthContext';
import '../App.css';

function SpaceDetailPage() {
    const [space, setSpace] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const { id } = useParams();
    const navigate = useNavigate();
    const { isLoggedIn } = useAuth();

    const [startTime, setStartTime] = useState('');
    const [endTime, setEndTime] = useState('');
    const [bookingError, setBookingError] = useState('');
    const [bookingLoading, setBookingLoading] = useState(false);
    const [totalPrice, setTotalPrice] = useState(0);

    useEffect(() => {
        fetchSpaceDetails();
    }, [id]);

    useEffect(() => {
        calculatePrice();
    }, [startTime, endTime, space]);

    const fetchSpaceDetails = async () => {
        setLoading(true);
        setError('');
        try {
            const response = await axios.get(`http://localhost:8080/api/spaces/${id}`);
            const data = response.data.data || response.data;
            setSpace(data);
        } catch (err) {
            setError('주차 공간 정보를 불러오는 데 실패했습니다.');
        } finally {
            setLoading(false);
        }
    };

    const calculatePrice = () => {
        if (!startTime || !endTime || !space?.pricePerHour) {
            setTotalPrice(0);
            return;
        }

        const start = new Date(startTime);
        const end = new Date(endTime);
        const diffHours = Math.ceil((end - start) / (1000 * 60 * 60));
        
        if (diffHours > 0) {
            setTotalPrice(diffHours * space.pricePerHour);
        } else {
            setTotalPrice(0);
        }
    };

    const handleBookingSubmit = async (e) => {
        e.preventDefault();
        setBookingError('');

        if (!startTime || !endTime) {
            setBookingError('시작 시간과 종료 시간을 모두 선택해주세요.');
            return;
        }

        const start = new Date(startTime);
        const end = new Date(endTime);
        const now = new Date();

        if (start <= now) {
            setBookingError('시작 시간은 현재 시간 이후여야 합니다.');
            return;
        }

        if (end <= start) {
            setBookingError('종료 시간은 시작 시간 이후여야 합니다.');
            return;
        }

        setBookingLoading(true);

        try {
            const response = await axios.post('http://localhost:8080/api/bookings', {
                parkingSpaceId: parseInt(id),
                startTime: start.toISOString(),
                endTime: end.toISOString(),
            });

            const message = response.data?.message || '예약이 성공적으로 완료되었습니다.';
            navigate('/mypage', { 
                state: { message } 
            });
        } catch (err) {
            const errorMessage = err.response?.data?.message || '예약에 실패했습니다. 이미 예약된 시간일 수 있습니다.';
            setBookingError(errorMessage);
        } finally {
            setBookingLoading(false);
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

    if (error || !space) {
        return (
            <div>
                <div className="alert alert-error">
                    <span>⚠️</span>
                    <span>{error || '주차 공간을 찾을 수 없습니다.'}</span>
                </div>
                <Link to="/" className="btn btn-primary">
                    목록으로 돌아가기
                </Link>
            </div>
        );
    }

    return (
        <div style={{ maxWidth: '800px', margin: '0 auto' }}>
            <Link to="/" style={{ 
                color: 'var(--color-gray-500)', 
                marginBottom: 'var(--spacing-4)',
                display: 'inline-block'
            }}>
                ← 목록으로 돌아가기
            </Link>

            <div className="card" style={{ marginBottom: 'var(--spacing-6)' }}>
                <div className="card-header">
                    <h1 style={{ margin: 0 }}>{space.address}</h1>
                </div>
                <div className="card-body">
                    <div style={{ marginBottom: 'var(--spacing-4)' }}>
                        <div style={{ 
                            fontSize: 'var(--font-size-2xl)', 
                            fontWeight: 700,
                            color: 'var(--color-primary)',
                            marginBottom: 'var(--spacing-2)'
                        }}>
                            {space.pricePerHour?.toLocaleString()}원/시간
                        </div>
                    </div>
                    
                    {space.description && (
                        <div style={{ marginBottom: 'var(--spacing-4)' }}>
                            <h3 style={{ marginBottom: 'var(--spacing-2)' }}>상세 설명</h3>
                            <p style={{ color: 'var(--color-gray-600)', whiteSpace: 'pre-wrap' }}>
                                {space.description}
                            </p>
                        </div>
                    )}

                    <div>
                        <p style={{ color: 'var(--color-gray-500)' }}>
                            소유자: <strong>{space.ownerName}</strong>
                        </p>
                    </div>
                </div>
            </div>

            {isLoggedIn ? (
                <div className="card">
                    <h2 style={{ marginBottom: 'var(--spacing-4)' }}>예약하기</h2>
                    
                    {bookingError && (
                        <div className="alert alert-error" style={{ marginBottom: 'var(--spacing-4)' }}>
                            <span>⚠️</span>
                            <span>{bookingError}</span>
                        </div>
                    )}

                    <form onSubmit={handleBookingSubmit}>
                        <div className="form-group">
                            <label htmlFor="startTime" className="form-label required">
                                시작 시간
                            </label>
                            <input
                                id="startTime"
                                type="datetime-local"
                                className="form-input"
                                value={startTime}
                                onChange={(e) => setStartTime(e.target.value)}
                                required
                                disabled={bookingLoading}
                                min={new Date().toISOString().slice(0, 16)}
                            />
                        </div>

                        <div className="form-group">
                            <label htmlFor="endTime" className="form-label required">
                                종료 시간
                            </label>
                            <input
                                id="endTime"
                                type="datetime-local"
                                className="form-input"
                                value={endTime}
                                onChange={(e) => setEndTime(e.target.value)}
                                required
                                disabled={bookingLoading}
                                min={startTime || new Date().toISOString().slice(0, 16)}
                            />
                        </div>

                        {totalPrice > 0 && (
                            <div style={{ 
                                padding: 'var(--spacing-4)', 
                                backgroundColor: 'var(--color-gray-100)',
                                borderRadius: 'var(--border-radius)',
                                marginBottom: 'var(--spacing-4)'
                            }}>
                                <div style={{ 
                                    fontSize: 'var(--font-size-lg)', 
                                    fontWeight: 600,
                                    color: 'var(--color-primary)'
                                }}>
                                    예상 요금: {totalPrice.toLocaleString()}원
                                </div>
                            </div>
                        )}

                        <button
                            type="submit"
                            className="btn btn-primary"
                            style={{ width: '100%' }}
                            disabled={bookingLoading}
                        >
                            {bookingLoading ? (
                                <>
                                    <span className="spinner"></span>
                                    예약 중...
                                </>
                            ) : (
                                '예약하기'
                            )}
                        </button>
                    </form>
                </div>
            ) : (
                <div className="card">
                    <div className="alert alert-info">
                        <span>ℹ️</span>
                        <div>
                            <strong>예약을 하시려면 로그인이 필요합니다.</strong>
                            <div style={{ marginTop: 'var(--spacing-2)' }}>
                                <Link to="/login" className="btn btn-primary btn-sm">
                                    로그인하기
                                </Link>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

export default SpaceDetailPage;
