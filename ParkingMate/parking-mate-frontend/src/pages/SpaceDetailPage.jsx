import { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import apiClient from '../api/axios';
import { useAuth } from '../context/AuthContext';
import '../App.css';

// datetime-local input용 로컬 시간 포맷 (UTC 변환 금지)
const toLocalInputFormat = (date) => {
    const pad = (n) => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
};

// 백엔드 LocalDateTime 파싱용 (타임존 없는 로컬 ISO 문자열)
const toLocalISOString = (dateInput) => {
    const d = new Date(dateInput);
    const pad = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`;
};

// 다음 정시(또는 30분 단위) 계산
const getNextHour = () => {
    const now = new Date();
    now.setMinutes(now.getMinutes() + 60);
    now.setMinutes(0, 0, 0);
    return now;
};

const addHours = (dateInput, hours) => {
    const d = new Date(dateInput);
    d.setHours(d.getHours() + hours);
    return d;
};

const DURATION_OPTIONS = [1, 2, 3, 4, 6, 8];

function SpaceDetailPage() {
    const [space, setSpace] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const { id } = useParams();
    const navigate = useNavigate();
    const { isLoggedIn } = useAuth();

    const defaultStart = getNextHour();
    const defaultEnd = addHours(defaultStart, 1);

    const [startTime, setStartTime] = useState(toLocalInputFormat(defaultStart));
    const [endTime, setEndTime] = useState(toLocalInputFormat(defaultEnd));
    const [selectedDuration, setSelectedDuration] = useState(1);
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
            const response = await apiClient.get(`/spaces/${id}`);
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
        setTotalPrice(diffHours > 0 ? diffHours * space.pricePerHour : 0);
    };

    const handleStartTimeChange = (e) => {
        const newStart = e.target.value;
        setStartTime(newStart);
        // 선택된 duration 유지하여 종료 시간 자동 업데이트
        const endDate = addHours(new Date(newStart), selectedDuration);
        setEndTime(toLocalInputFormat(endDate));
        setBookingError('');
    };

    const handleDurationSelect = (hours) => {
        setSelectedDuration(hours);
        if (startTime) {
            const endDate = addHours(new Date(startTime), hours);
            setEndTime(toLocalInputFormat(endDate));
        }
    };

    const handleEndTimeChange = (e) => {
        setEndTime(e.target.value);
        setSelectedDuration(null); // 직접 수정 시 duration 선택 해제
    };

    const handleBookingSubmit = async (e) => {
        e.preventDefault();
        setBookingError('');

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
            const response = await apiClient.post('/bookings', {
                parkingSpaceId: parseInt(id),
                startTime: toLocalISOString(startTime),
                endTime: toLocalISOString(endTime),
            });
            const message = response.data?.message || '예약이 성공적으로 완료되었습니다.';
            navigate('/mypage', { state: { message } });
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
                <Link to="/" className="btn btn-primary">목록으로 돌아가기</Link>
            </div>
        );
    }

    const nowInputMin = toLocalInputFormat(new Date(Date.now() + 60 * 1000));

    return (
        <div style={{ maxWidth: '800px', margin: '0 auto' }}>
            <Link to="/" style={{ color: 'var(--color-gray-500)', marginBottom: 'var(--spacing-4)', display: 'inline-block' }}>
                ← 목록으로 돌아가기
            </Link>

            {/* 공간 정보 카드 */}
            <div className="card" style={{ marginBottom: 'var(--spacing-6)' }}>
                <div className="card-header">
                    <h1 style={{ margin: 0 }}>📍 {space.address}</h1>
                </div>
                <div className="card-body">
                    <div style={{ fontSize: 'var(--font-size-2xl)', fontWeight: 700, color: 'var(--color-primary)', marginBottom: 'var(--spacing-2)' }}>
                        {space.pricePerHour?.toLocaleString()}원 / 시간
                    </div>
                    {space.description && (
                        <p style={{ color: 'var(--color-gray-600)', whiteSpace: 'pre-wrap', marginBottom: 'var(--spacing-2)' }}>
                            {space.description}
                        </p>
                    )}
                    <p style={{ color: 'var(--color-gray-500)', marginBottom: 0 }}>
                        소유자: <strong>{space.ownerName}</strong>
                    </p>
                </div>
            </div>

            {/* 예약 카드 */}
            {isLoggedIn ? (
                <div className="card">
                    <h2 style={{ marginBottom: 'var(--spacing-5)' }}>🕐 예약하기</h2>

                    {bookingError && (
                        <div className="alert alert-error" style={{ marginBottom: 'var(--spacing-4)' }}>
                            <span>⚠️</span>
                            <span>{bookingError}</span>
                        </div>
                    )}

                    <form onSubmit={handleBookingSubmit}>
                        {/* 시작 시간 */}
                        <div className="form-group">
                            <label htmlFor="startTime" className="form-label required">시작 시간</label>
                            <input
                                id="startTime"
                                type="datetime-local"
                                className="form-input"
                                value={startTime}
                                onChange={handleStartTimeChange}
                                min={nowInputMin}
                                required
                                disabled={bookingLoading}
                            />
                        </div>

                        {/* 이용 시간 빠른 선택 */}
                        <div className="form-group">
                            <label className="form-label">이용 시간</label>
                            <div style={{ display: 'flex', gap: 'var(--spacing-2)', flexWrap: 'wrap' }}>
                                {DURATION_OPTIONS.map((h) => (
                                    <button
                                        key={h}
                                        type="button"
                                        onClick={() => handleDurationSelect(h)}
                                        disabled={bookingLoading}
                                        style={{
                                            padding: '6px 16px',
                                            borderRadius: 'var(--border-radius)',
                                            border: '1px solid',
                                            borderColor: selectedDuration === h ? 'var(--color-primary)' : 'var(--color-gray-300)',
                                            backgroundColor: selectedDuration === h ? 'var(--color-primary)' : 'white',
                                            color: selectedDuration === h ? 'white' : 'var(--color-gray-700)',
                                            cursor: 'pointer',
                                            fontWeight: selectedDuration === h ? 600 : 400,
                                            transition: 'all 0.15s',
                                        }}
                                    >
                                        {h}시간
                                    </button>
                                ))}
                            </div>
                            <p className="form-help">버튼 선택 시 종료 시간이 자동으로 설정됩니다.</p>
                        </div>

                        {/* 종료 시간 */}
                        <div className="form-group">
                            <label htmlFor="endTime" className="form-label required">종료 시간</label>
                            <input
                                id="endTime"
                                type="datetime-local"
                                className="form-input"
                                value={endTime}
                                onChange={handleEndTimeChange}
                                min={startTime || nowInputMin}
                                required
                                disabled={bookingLoading}
                            />
                        </div>

                        {/* 예상 요금 */}
                        {totalPrice > 0 && (
                            <div style={{
                                padding: 'var(--spacing-4)',
                                backgroundColor: 'rgb(37 99 235 / 0.05)',
                                border: '1px solid rgb(37 99 235 / 0.2)',
                                borderRadius: 'var(--border-radius)',
                                marginBottom: 'var(--spacing-4)',
                                display: 'flex',
                                justifyContent: 'space-between',
                                alignItems: 'center',
                            }}>
                                <span style={{ color: 'var(--color-gray-600)' }}>
                                    {selectedDuration ? `${selectedDuration}시간 이용` : '예상 이용 시간'}
                                </span>
                                <span style={{ fontSize: 'var(--font-size-xl)', fontWeight: 700, color: 'var(--color-primary)' }}>
                                    {totalPrice.toLocaleString()}원
                                </span>
                            </div>
                        )}

                        <button
                            type="submit"
                            className="btn btn-primary"
                            style={{ width: '100%' }}
                            disabled={bookingLoading}
                        >
                            {bookingLoading ? (
                                <><span className="spinner"></span> 예약 중...</>
                            ) : (
                                '예약 확정하기'
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
                                <Link to="/login" className="btn btn-primary btn-sm">로그인하기</Link>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

export default SpaceDetailPage;
