import { useState, useEffect } from 'react';
import apiClient from '../api/axios';
import { Link } from 'react-router-dom';
import '../App.css';

const RADIUS_OPTIONS = [1, 3, 5, 10];

function SpaceCard({ space }) {
    return (
        <Link to={`/spaces/${space.id}`} style={{ textDecoration: 'none' }}>
            <div className="card">
                <div className="card-header">
                    <h3 className="card-title" style={{ margin: 0 }}>{space.address}</h3>
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
                    <p style={{ color: 'var(--color-gray-500)', fontSize: 'var(--font-size-sm)' }}>
                        소유자: {space.ownerName}
                    </p>
                </div>
                <div className="card-footer">
                    <span style={{ color: 'var(--color-primary)', fontWeight: 500, fontSize: 'var(--font-size-sm)' }}>
                        상세보기 →
                    </span>
                </div>
            </div>
        </Link>
    );
}

function HomePage() {
    const [spaces, setSpaces] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    // 검색 모드: 'text' | 'nearby'
    const [searchMode, setSearchMode] = useState('text');
    const [searchAddress, setSearchAddress] = useState('');
    const [radiusKm, setRadiusKm] = useState(3);
    const [geoLoading, setGeoLoading] = useState(false);
    const [nearbyUsed, setNearbyUsed] = useState(false);

    useEffect(() => {
        fetchAllSpaces();
    }, []);

    const fetchAllSpaces = async () => {
        setLoading(true);
        setError('');
        setNearbyUsed(false);
        try {
            const response = await apiClient.get('/spaces');
            const data = response.data.data || response.data;
            setSpaces(Array.isArray(data) ? data : []);
        } catch (err) {
            setError('주차 공간 목록을 불러오는 데 실패했습니다.');
        } finally {
            setLoading(false);
        }
    };

    const handleTextSearch = async (e) => {
        e.preventDefault();
        setLoading(true);
        setError('');
        setNearbyUsed(false);
        try {
            const params = searchAddress ? { address: searchAddress } : {};
            const response = await apiClient.get('/spaces', { params });
            const data = response.data.data || response.data;
            setSpaces(Array.isArray(data) ? data : []);
        } catch (err) {
            setError('검색에 실패했습니다.');
        } finally {
            setLoading(false);
        }
    };

    const handleNearbySearch = () => {
        if (!navigator.geolocation) {
            setError('이 브라우저는 위치 기능을 지원하지 않습니다.');
            return;
        }
        setGeoLoading(true);
        setError('');
        navigator.geolocation.getCurrentPosition(
            async (pos) => {
                try {
                    setLoading(true);
                    const response = await apiClient.get('/spaces/nearby', {
                        params: {
                            latitude: pos.coords.latitude,
                            longitude: pos.coords.longitude,
                            radiusKm,
                        },
                    });
                    const data = response.data.data?.content || response.data.data || [];
                    setSpaces(Array.isArray(data) ? data : []);
                    setNearbyUsed(true);
                } catch (err) {
                    setError('위치 기반 검색에 실패했습니다.');
                } finally {
                    setLoading(false);
                    setGeoLoading(false);
                }
            },
            () => {
                setError('위치 정보를 가져올 수 없습니다. 브라우저 위치 권한을 허용해주세요.');
                setGeoLoading(false);
            }
        );
    };

    const handleClear = () => {
        setSearchAddress('');
        fetchAllSpaces();
    };

    return (
        <div>
            <div style={{ marginBottom: 'var(--spacing-8)' }}>
                <h1>주차 공간 찾기</h1>
                <p style={{ color: 'var(--color-gray-500)', marginBottom: 'var(--spacing-6)' }}>
                    원하는 위치의 주차 공간을 검색하고 예약하세요
                </p>

                {/* 검색 모드 탭 */}
                <div style={{ display: 'flex', gap: 0, marginBottom: 'var(--spacing-4)', borderBottom: '2px solid var(--color-gray-200)' }}>
                    {[
                        { key: 'text', label: '주소 검색' },
                        { key: 'nearby', label: '📍 내 주변 검색' },
                    ].map(tab => (
                        <button
                            key={tab.key}
                            type="button"
                            onClick={() => setSearchMode(tab.key)}
                            style={{
                                padding: 'var(--spacing-2) var(--spacing-5)',
                                border: 'none',
                                borderBottom: searchMode === tab.key ? '2px solid var(--color-primary)' : '2px solid transparent',
                                marginBottom: -2,
                                background: 'none',
                                cursor: 'pointer',
                                fontWeight: searchMode === tab.key ? 600 : 400,
                                color: searchMode === tab.key ? 'var(--color-primary)' : 'var(--color-gray-500)',
                                fontSize: 'var(--font-size-base)',
                                transition: 'all 0.15s',
                            }}
                        >
                            {tab.label}
                        </button>
                    ))}
                </div>

                {/* 주소 검색 */}
                {searchMode === 'text' && (
                    <form onSubmit={handleTextSearch} style={{ maxWidth: '600px', display: 'flex', gap: 'var(--spacing-3)' }}>
                        <input
                            type="text"
                            className="form-input"
                            placeholder="주소로 검색 (예: 강남구, 홍대)"
                            value={searchAddress}
                            onChange={(e) => setSearchAddress(e.target.value)}
                            style={{ flex: 1 }}
                        />
                        <button type="submit" className="btn btn-primary">검색</button>
                        {searchAddress && (
                            <button type="button" className="btn btn-secondary" onClick={handleClear}>초기화</button>
                        )}
                    </form>
                )}

                {/* 내 주변 검색 */}
                {searchMode === 'nearby' && (
                    <div style={{ maxWidth: '600px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--spacing-3)', flexWrap: 'wrap' }}>
                            <span style={{ color: 'var(--color-gray-600)', fontWeight: 500 }}>반경</span>
                            {RADIUS_OPTIONS.map(r => (
                                <button
                                    key={r}
                                    type="button"
                                    onClick={() => setRadiusKm(r)}
                                    style={{
                                        padding: '6px 16px',
                                        borderRadius: 'var(--border-radius)',
                                        border: '1px solid',
                                        borderColor: radiusKm === r ? 'var(--color-primary)' : 'var(--color-gray-300)',
                                        backgroundColor: radiusKm === r ? 'var(--color-primary)' : 'white',
                                        color: radiusKm === r ? 'white' : 'var(--color-gray-700)',
                                        cursor: 'pointer',
                                        fontWeight: radiusKm === r ? 600 : 400,
                                        transition: 'all 0.15s',
                                    }}
                                >
                                    {r}km
                                </button>
                            ))}
                            <button
                                type="button"
                                className="btn btn-primary"
                                onClick={handleNearbySearch}
                                disabled={geoLoading || loading}
                            >
                                {geoLoading ? (
                                    <><span className="spinner" style={{ width: 16, height: 16 }}></span> 위치 확인 중...</>
                                ) : (
                                    '내 주변 검색'
                                )}
                            </button>
                            {nearbyUsed && (
                                <button type="button" className="btn btn-secondary" onClick={handleClear}>
                                    전체 보기
                                </button>
                            )}
                        </div>
                        <p className="form-help" style={{ marginTop: 'var(--spacing-2)' }}>
                            브라우저 위치 권한 허용 후 검색합니다. MySQL R-Tree + Redis GEO 캐시로 처리됩니다.
                        </p>
                    </div>
                )}
            </div>

            {error && (
                <div className="alert alert-error">
                    <span>⚠️</span>
                    <span>{error}</span>
                </div>
            )}

            {loading ? (
                <div className="loading">
                    <span className="spinner"></span>
                    <span style={{ marginLeft: 'var(--spacing-3)' }}>주차 공간을 불러오는 중...</span>
                </div>
            ) : spaces.length === 0 ? (
                <div className="empty-state">
                    <div className="empty-state-icon">🅿️</div>
                    <h3>{nearbyUsed ? '주변에 등록된 주차 공간이 없습니다' : '등록된 주차 공간이 없습니다'}</h3>
                    <p>{nearbyUsed ? '반경을 넓혀서 다시 검색해보세요.' : '첫 번째 주차 공간을 등록해보세요!'}</p>
                </div>
            ) : (
                <>
                    <div style={{ marginBottom: 'var(--spacing-4)', color: 'var(--color-gray-500)' }}>
                        {nearbyUsed
                            ? `반경 ${radiusKm}km 내 ${spaces.length}개의 주차 공간`
                            : `총 ${spaces.length}개의 주차 공간`}
                    </div>
                    <div className="grid grid-3">
                        {spaces.map(space => (
                            <SpaceCard key={space.id} space={space} />
                        ))}
                    </div>
                </>
            )}
        </div>
    );
}

export default HomePage;
