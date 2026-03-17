import { useState, useEffect } from 'react';
import apiClient from '../api/axios';
import { Link } from 'react-router-dom';
import '../App.css';

function HomePage() {
    const [spaces, setSpaces] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [searchAddress, setSearchAddress] = useState('');

    useEffect(() => {
        fetchSpaces();
    }, []);

    const fetchSpaces = async (address = '') => {
        setLoading(true);
        setError('');
        try {
            const params = address ? { address } : {};
            const response = await apiClient.get('/spaces', { params });
            const data = response.data.data || response.data;
            setSpaces(Array.isArray(data) ? data : []);
        } catch (err) {
            setError('주차 공간 목록을 불러오는 데 실패했습니다.');
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    const handleSearch = (e) => {
        e.preventDefault();
        fetchSpaces(searchAddress);
    };

    const handleClearSearch = () => {
        setSearchAddress('');
        fetchSpaces('');
    };

    if (loading) {
        return (
            <div className="loading">
                <span className="spinner"></span>
                <span style={{ marginLeft: 'var(--spacing-3)' }}>주차 공간을 불러오는 중...</span>
            </div>
        );
    }

    return (
        <div>
            <div style={{ marginBottom: 'var(--spacing-8)' }}>
                <h1>주차 공간 찾기</h1>
                <p style={{ color: 'var(--color-gray-500)', marginBottom: 'var(--spacing-6)' }}>
                    원하는 위치의 주차 공간을 검색하고 예약하세요
                </p>

                <form onSubmit={handleSearch} style={{ maxWidth: '600px', display: 'flex', gap: 'var(--spacing-3)' }}>
                    <input
                        type="text"
                        className="form-input"
                        placeholder="주소로 검색 (예: 강남구, 홍대)"
                        value={searchAddress}
                        onChange={(e) => setSearchAddress(e.target.value)}
                        style={{ flex: 1 }}
                    />
                    <button type="submit" className="btn btn-primary">
                        검색
                    </button>
                    {searchAddress && (
                        <button 
                            type="button" 
                            className="btn btn-secondary"
                            onClick={handleClearSearch}
                        >
                            초기화
                        </button>
                    )}
                </form>
            </div>

            {error && (
                <div className="alert alert-error">
                    <span>⚠️</span>
                    <span>{error}</span>
                </div>
            )}

            {spaces.length === 0 ? (
                <div className="empty-state">
                    <div className="empty-state-icon">🅿️</div>
                    <h3>등록된 주차 공간이 없습니다</h3>
                    <p>첫 번째 주차 공간을 등록해보세요!</p>
                </div>
            ) : (
                <>
                    <div style={{ marginBottom: 'var(--spacing-4)', color: 'var(--color-gray-500)' }}>
                        총 {spaces.length}개의 주차 공간이 있습니다
                    </div>
                    <div className="grid grid-3">
                        {spaces.map(space => (
                            <Link 
                                to={`/spaces/${space.id}`} 
                                key={space.id}
                                style={{ textDecoration: 'none' }}
                            >
                                <div className="card">
                                    <div className="card-header">
                                        <h3 className="card-title" style={{ margin: 0 }}>
                                            {space.address}
                                        </h3>
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
                                            <p style={{ 
                                                color: 'var(--color-gray-600)', 
                                                marginBottom: 'var(--spacing-3)',
                                                display: '-webkit-box',
                                                WebkitLineClamp: 2,
                                                WebkitBoxOrient: 'vertical',
                                                overflow: 'hidden'
                                            }}>
                                                {space.description}
                                            </p>
                                        )}
                                        <p style={{ 
                                            color: 'var(--color-gray-500)', 
                                            fontSize: 'var(--font-size-sm)'
                                        }}>
                                            소유자: {space.ownerName}
                                        </p>
                                    </div>
                                    <div className="card-footer">
                                        <span style={{ 
                                            color: 'var(--color-primary)', 
                                            fontWeight: 500,
                                            fontSize: 'var(--font-size-sm)'
                                        }}>
                                            상세보기 →
                                        </span>
                                    </div>
                                </div>
                            </Link>
                        ))}
                    </div>
                </>
            )}
        </div>
    );
}

export default HomePage;
